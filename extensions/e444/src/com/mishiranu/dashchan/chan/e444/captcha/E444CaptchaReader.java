package com.mishiranu.dashchan.chan.e444.captcha;

import android.graphics.Bitmap;
import android.net.Uri;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import chan.http.UrlEncodedEntity;
import chan.util.CommonUtils;
import com.mishiranu.dashchan.chan.e444.E444ChanConfiguration;
import com.mishiranu.dashchan.chan.e444.E444ChanLocator;
import com.mishiranu.dashchan.chan.e444.E444ChanPerformer;
import org.json.JSONException;
import org.json.JSONObject;

public final class E444CaptchaReader {
	private static final int SLIDE_CHOICES_COUNT = 7;
	private static final String RECAPTCHA_API_KEY = "6LdclbwqAAAAALruA-J-PUYZaI2c1WnqbXfCL1Sj";
	private static final String RECAPTCHA_REFERER = "https://ech.bz";
	public static final String CAPTCHA_DATA_KEY_TYPE = "captchaType";

	@FunctionalInterface
	public interface SlideCaptchaChooser {
		Integer choose(Bitmap[] images) throws HttpException;
	}

	private E444CaptchaReader() {}

	public static boolean isRecaptchaType(String captchaType) {
		return E444ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2.equals(captchaType)
				|| E444ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2_INVISIBLE.equals(captchaType);
	}

	private static String createSlideCaptchaChallenge(String key, String point, String session) {
		try {
			JSONObject jsonObject = new JSONObject();
			if (key != null) {
				jsonObject.put("key", key);
			}
			if (point != null) {
				jsonObject.put("point", point);
			}
			if (session != null) {
				jsonObject.put("session", session);
			}
			return jsonObject.toString();
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	private static boolean checkSlideCaptcha(E444ChanPerformer performer, ChanPerformer.ReadCaptchaData data,
			String session, String key, String value, int x, int y) throws HttpException, InvalidResponseException {
		E444ChanLocator locator = E444ChanLocator.get(performer);
		Uri uri = locator.buildQuery("api/captcha/slide/check", "v", value);
		UrlEncodedEntity entity = new UrlEncodedEntity("point", x + "," + y, "key", key);
		HttpResponse response = new HttpRequest(uri, data).setPostMethod(entity)
				.addCookie(E444CaptchaSession.COOKIE_SESSION, session).perform();
		E444CaptchaSession.updateAndStore(E444ChanConfiguration.get(performer), response, session);
		try {
			JSONObject jsonObject = new JSONObject(response.readString());
			return jsonObject.optInt("code", -1) == 0;
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
	}

	public static ChanPerformer.ReadCaptchaResult onReadCaptcha(E444ChanPerformer performer,
			ChanPerformer.ReadCaptchaData data, SlideCaptchaChooser slideCaptchaChooser)
			throws HttpException, InvalidResponseException {
		String captchaType = data.captchaType;
		E444ChanLocator locator = E444ChanLocator.get(performer);
		ChanPerformer.ReadCaptchaResult result;
		if (isRecaptchaType(captchaType)) {
			ChanPerformer.CaptchaData captchaData = new ChanPerformer.CaptchaData();
			captchaData.put(ChanPerformer.CaptchaData.REFERER, RECAPTCHA_REFERER);
			captchaData.put(ChanPerformer.CaptchaData.API_KEY, RECAPTCHA_API_KEY);
			captchaData.put(CAPTCHA_DATA_KEY_TYPE, captchaType);
			result = new ChanPerformer.ReadCaptchaResult(ChanPerformer.CaptchaState.CAPTCHA, captchaData)
					.setValidity(E444ChanConfiguration.Captcha.Validity.IN_BOARD_SEPARATELY);
		} else if (E444ChanConfiguration.CAPTCHA_TYPE_SLIDER.equals(captchaType)) {
			if (data.mayShowLoadButton) {
				return new ChanPerformer.ReadCaptchaResult(ChanPerformer.CaptchaState.NEED_LOAD, null);
			}
			E444ChanConfiguration configuration = E444ChanConfiguration.get(performer);
			String session = E444CaptchaSession.get(configuration);
			String value = Long.toString(System.currentTimeMillis());
			Uri uri = locator.buildQuery("api/captcha/slide/id", "v", value);
			HttpResponse response = new HttpRequest(uri, data)
					.addCookie(E444CaptchaSession.COOKIE_SESSION, session).perform();
			session = E444CaptchaSession.updateAndStore(configuration, response, session);
			String key;
			Bitmap image;
			Bitmap tile;
			int tileY;
			try {
				JSONObject jsonObject = new JSONObject(response.readString());
				if (jsonObject.optInt("code", -1) != 0) {
					throw new InvalidResponseException();
				}
				key = jsonObject.getString("captcha_key");
				image = E444SlideCaptchaUtils.decodeBase64Bitmap(jsonObject.getString("image_base64"));
				tile = E444SlideCaptchaUtils.decodeBase64Bitmap(jsonObject.getString("tile_base64"));
				tileY = jsonObject.optInt("tile_y", 0);
			} catch (JSONException e) {
				throw new InvalidResponseException(e);
			}
			tileY = Math.max(0, Math.min(tileY, Math.max(0, image.getHeight() - tile.getHeight())));
			Integer selectedX;
			try {
				selectedX = E444SlideCaptchaUtils.chooseSlideCaptchaX(image, tile, tileY, SLIDE_CHOICES_COUNT,
						slideCaptchaChooser::choose);
			} finally {
				image.recycle();
				tile.recycle();
			}
			if (selectedX == null) {
				return new ChanPerformer.ReadCaptchaResult(ChanPerformer.CaptchaState.NEED_LOAD, null);
			}
			if (!checkSlideCaptcha(performer, data, session, key, value, selectedX, tileY)) {
				return new ChanPerformer.ReadCaptchaResult(ChanPerformer.CaptchaState.NEED_LOAD, null);
			}
			ChanPerformer.CaptchaData captchaData = new ChanPerformer.CaptchaData();
			captchaData.put(ChanPerformer.CaptchaData.CHALLENGE,
					createSlideCaptchaChallenge(key, selectedX + "," + tileY, session));
			captchaData.put(CAPTCHA_DATA_KEY_TYPE, E444ChanConfiguration.CAPTCHA_TYPE_SLIDER);
			result = new ChanPerformer.ReadCaptchaResult(ChanPerformer.CaptchaState.SKIP, captchaData);
		} else {
			throw new IllegalStateException();
		}
		if (!CommonUtils.equals(data.captchaType, captchaType)) {
			result.setCaptchaType(captchaType);
		}
		return result;
	}
}
