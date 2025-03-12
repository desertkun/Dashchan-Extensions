package com.trixiether.dashchan.chan.palanq;

import chan.content.FoolFuukaChanLocator;

public class PalanqWinChanLocator extends FoolFuukaChanLocator {
    public PalanqWinChanLocator() {
        addChanHost("archive.palanq.win");
        setHttpsMode(HttpsMode.HTTPS_ONLY);
    }
}
