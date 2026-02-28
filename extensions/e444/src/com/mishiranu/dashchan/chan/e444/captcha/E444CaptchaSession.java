package com.mishiranu.dashchan.chan.e444.captcha;

import chan.http.HttpResponse;
import com.mishiranu.dashchan.chan.e444.E444ChanConfiguration;

public final class E444CaptchaSession {
    public static final String COOKIE_SESSION = "_ssid";

    private E444CaptchaSession() {}

    public static String get(E444ChanConfiguration configuration) {
        return configuration.getCookie(COOKIE_SESSION);
    }

    public static String updateAndStore(E444ChanConfiguration configuration, HttpResponse response, String session) {
        String newSession = response.getCookieValue(COOKIE_SESSION);
        if (newSession != null && !newSession.equals(session)) {
            configuration.storeCookie(COOKIE_SESSION, newSession, "Session");
            return newSession;
        }
        return session;
    }
}
