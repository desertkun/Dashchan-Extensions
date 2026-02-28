package com.mishiranu.dashchan.chan.e444.captcha;

import chan.content.ChanPerformer;
import chan.http.MultipartEntity;
import chan.util.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;

public final class E444CaptchaSender {
    public static final class CaptchaPostData {
        private final String captchaType;
        private final String captchaId;
        private final String captchaValue;
        private final String recaptchaResponse;
        private final String session;

        private CaptchaPostData(
                String captchaType, String captchaId, String captchaValue, String recaptchaResponse, String session) {
            this.captchaType = captchaType;
            this.captchaId = captchaId;
            this.captchaValue = captchaValue;
            this.recaptchaResponse = recaptchaResponse;
            this.session = session;
        }

        public String getSession() {
            return session;
        }

        private boolean isRecaptcha() {
            return E444CaptchaReader.isRecaptchaType(captchaType);
        }

        public void addToEntity(MultipartEntity entity) {
            entity.add("captcha_id", captchaId);
            entity.add("captcha_value", captchaValue);
            entity.add("captcha_type", isRecaptcha() ? "recaptcha" : "captcha");
            if (isRecaptcha()) {
                entity.add("g-recaptcha-response", recaptchaResponse);
            }
        }
    }

    private E444CaptchaSender() {}

    public static CaptchaPostData buildCaptchaPostData(ChanPerformer.SendPostData data, String session) {
        String captchaType = data.captchaType;
        String captchaId = "";
        String captchaValue = "";
        String recaptchaResponse = "";
        if (data.captchaData != null) {
            String dataCaptchaType =
                    StringUtils.nullIfEmpty(data.captchaData.get(E444CaptchaReader.CAPTCHA_DATA_KEY_TYPE));
            if (dataCaptchaType != null) {
                captchaType = dataCaptchaType;
            }
            String challenge = data.captchaData.get(ChanPerformer.CaptchaData.CHALLENGE);
            String input = StringUtils.emptyIfNull(data.captchaData.get(ChanPerformer.CaptchaData.INPUT));
            if (E444CaptchaReader.isRecaptchaType(captchaType)) {
                recaptchaResponse = input;
                captchaValue = input;
            } else {
                String id = null;
                String key = null;
                String point = null;
                if (!StringUtils.isEmpty(challenge)) {
                    JSONObject jsonObject;
                    try {
                        jsonObject = new JSONObject(challenge);
                        id = StringUtils.nullIfEmpty(jsonObject.optString("id"));
                        key = StringUtils.nullIfEmpty(jsonObject.optString("key"));
                        point = StringUtils.nullIfEmpty(jsonObject.optString("point"));
                        String captchaSession = StringUtils.nullIfEmpty(jsonObject.optString("session"));
                        if (captchaSession != null) {
                            session = captchaSession;
                        }
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (key != null && point != null) {
                    captchaId = key;
                    captchaValue = point;
                } else if (id != null || !StringUtils.isEmpty(input)) {
                    captchaId = StringUtils.emptyIfNull(id);
                    captchaValue = input;
                }
            }
        }
        return new CaptchaPostData(captchaType, captchaId, captchaValue, recaptchaResponse, session);
    }
}
