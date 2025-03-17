package com.trixiether.dashchan.chan.archiveofsins;

import chan.content.FoolFuukaChanLocator;

public class ArchiveOfSinsChanLocator extends FoolFuukaChanLocator {
    public ArchiveOfSinsChanLocator() {
        addChanHost("archiveofsins.com");
        setHttpsMode(HttpsMode.HTTPS_ONLY);
    }
}
