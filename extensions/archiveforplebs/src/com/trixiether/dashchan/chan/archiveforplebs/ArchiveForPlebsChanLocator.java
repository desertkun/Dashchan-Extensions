package com.trixiether.dashchan.chan.archiveforplebs;

import chan.content.FoolFuukaChanLocator;

public class ArchiveForPlebsChanLocator extends FoolFuukaChanLocator {
    public ArchiveForPlebsChanLocator() {
        addChanHost("archive.4plebs.org");
        setHttpsMode(HttpsMode.HTTPS_ONLY);
    }
}
