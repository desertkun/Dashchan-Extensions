package com.mishiranu.dashchan.chan.e444.enhance;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;
import com.mishiranu.dashchan.chan.e444.enhance.controllers.EnhanceController;
import com.mishiranu.dashchan.chan.e444.enhance.controllers.EnhanceControllerBadge;
import com.mishiranu.dashchan.chan.e444.enhance.controllers.EnhanceControllerPostExtension;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class EnhanceControllersManager {
    private static final List<EnhanceController> CONTROLLERS = Collections.unmodifiableList(
            Arrays.asList(EnhanceControllerBadge.getInstance(), EnhanceControllerPostExtension.getInstance()));
    private static final Map<Activity, ActivityStateObserver> activityStateObservers =
            Collections.synchronizedMap(new WeakHashMap<Activity, ActivityStateObserver>());

    private static final class ActivityStateObserver {
        final ViewTreeObserver.OnGlobalLayoutListener layoutListener;
        final ViewTreeObserver.OnScrollChangedListener scrollChangedListener;

        ActivityStateObserver(
                ViewTreeObserver.OnGlobalLayoutListener layoutListener,
                ViewTreeObserver.OnScrollChangedListener scrollChangedListener) {
            this.layoutListener = layoutListener;
            this.scrollChangedListener = scrollChangedListener;
        }
    }

    private EnhanceControllersManager() {}

    static void onActivityResumed(Activity activity) {
        attachActivityStateObserver(activity);
        applyAll(activity);
    }

    static void onActivityPaused(Activity activity) {
        detachActivityStateObserver(activity);
        clearAll(activity);
    }

    static void onActivityDestroyed(Activity activity) {
        detachActivityStateObserver(activity);
        clearAll(activity);
    }

    static void syncActivity(Activity activity, boolean paused) {
        if (paused) {
            detachActivityStateObserver(activity);
            clearAll(activity);
        } else {
            attachActivityStateObserver(activity);
            applyAll(activity);
        }
    }

    private static void attachActivityStateObserver(Activity activity) {
        detachActivityStateObserver(activity);
        View decorView = EnhanceHostResolver.getDecorViewSafe(activity);
        if (decorView == null) {
            return;
        }
        WeakReference<Activity> activityReference = new WeakReference<>(activity);
        ViewTreeObserver.OnGlobalLayoutListener layoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                Activity observedActivity = activityReference.get();
                if (observedActivity == null
                        || observedActivity.isFinishing()
                        || EnhanceHostResolver.isActivityDestroyed(observedActivity)) {
                    return;
                }
                applyAll(observedActivity);
            }
        };
        ViewTreeObserver.OnScrollChangedListener scrollChangedListener =
                new ViewTreeObserver.OnScrollChangedListener() {
                    @Override
                    public void onScrollChanged() {
                        Activity observedActivity = activityReference.get();
                        if (observedActivity == null
                                || observedActivity.isFinishing()
                                || EnhanceHostResolver.isActivityDestroyed(observedActivity)) {
                            return;
                        }
                        applyAll(observedActivity);
                    }
                };
        ViewTreeObserver viewTreeObserver = decorView.getViewTreeObserver();
        if (!viewTreeObserver.isAlive()) {
            return;
        }
        viewTreeObserver.addOnGlobalLayoutListener(layoutListener);
        viewTreeObserver.addOnScrollChangedListener(scrollChangedListener);
        activityStateObservers.put(activity, new ActivityStateObserver(layoutListener, scrollChangedListener));
    }

    private static void detachActivityStateObserver(Activity activity) {
        ActivityStateObserver stateObserver = activityStateObservers.remove(activity);
        if (stateObserver == null) {
            return;
        }
        View decorView = EnhanceHostResolver.getDecorViewSafe(activity);
        if (decorView == null) {
            return;
        }
        ViewTreeObserver viewTreeObserver = decorView.getViewTreeObserver();
        if (!viewTreeObserver.isAlive()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            viewTreeObserver.removeOnGlobalLayoutListener(stateObserver.layoutListener);
        } else {
            viewTreeObserver.removeGlobalOnLayoutListener(stateObserver.layoutListener);
        }
        viewTreeObserver.removeOnScrollChangedListener(stateObserver.scrollChangedListener);
    }

    private static void applyAll(Activity activity) {
        for (EnhanceController controller : CONTROLLERS) {
            try {
                controller.apply(activity);
            } catch (Throwable ignored) {
                // Keep other controllers active even if one fails.
            }
        }
    }

    private static void clearAll(Activity activity) {
        for (EnhanceController controller : CONTROLLERS) {
            try {
                controller.clear(activity);
            } catch (Throwable ignored) {
                // Keep other controllers active even if one fails.
            }
        }
    }
}
