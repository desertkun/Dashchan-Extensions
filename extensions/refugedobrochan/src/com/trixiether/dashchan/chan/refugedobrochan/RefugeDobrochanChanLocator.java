package com.trixiether.dashchan.chan.refugedobrochan;

import android.net.Uri;

import java.util.regex.Pattern;

import chan.content.VichanChanLocator;

public class RefugeDobrochanChanLocator extends VichanChanLocator {

    public RefugeDobrochanChanLocator() {
        addChanHost("rf.dobrochan.net");
        setHttpsMode(HttpsMode.HTTPS_ONLY);
        DEFAULT_SEGMENT_PRESET = "vichan";
        THREAD_PATH = Pattern.compile("/vichan/\\w+/{1,2}res/(\\d+).*\\.html");
    }

    @Override
    public Uri createAntispamUri(String boardName, String threadNumber) {
        return threadNumber != null ? super.buildPath(DEFAULT_SEGMENT_PRESET, boardName, "res", threadNumber + ".html")
                : super.buildPath(DEFAULT_SEGMENT_PRESET, boardName);
    }

    @Override
    public Uri createPostUri(String boardName, String threadNumber, String postNumber) {
        return super.buildPath(DEFAULT_SEGMENT_PRESET, boardName, "res", threadNumber + ".html#q" + postNumber);
    }
}
