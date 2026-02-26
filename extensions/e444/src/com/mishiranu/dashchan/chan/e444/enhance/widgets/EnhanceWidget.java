package com.mishiranu.dashchan.chan.e444.enhance.widgets;

import android.app.Activity;
import android.view.ViewGroup;

public interface EnhanceWidget {
    void bind(Activity activity, ViewGroup postRoot);

    void clear(ViewGroup postRoot);
}
