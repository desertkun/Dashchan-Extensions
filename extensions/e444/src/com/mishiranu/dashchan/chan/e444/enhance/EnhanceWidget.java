package com.mishiranu.dashchan.chan.e444.enhance;

import android.app.Activity;
import android.view.ViewGroup;

public interface EnhanceWidget {
    String getContainerTag();

    ViewGroup.LayoutParams createLayoutParams(Activity activity);

    void inject(Activity activity, ViewGroup postRoot);
}
