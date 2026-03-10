package com.mishiranu.dashchan.chan.e444.enhance;

import android.app.Activity;

public interface EnhanceHook {
    void apply(Activity activity);

    void clear(Activity activity);
}
