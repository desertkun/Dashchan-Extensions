package com.mishiranu.dashchan.chan.e444.enhance;

import android.app.Activity;
import android.view.View;
import android.view.ViewTreeObserver;
import com.mishiranu.dashchan.chan.e444.enhance.controllers.HookPost;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class EnhanceHookManager {
    private static final String TARGET_CHAN_NAME = "e444";
    private static final List<EnhanceHook> CONTROLLERS =
            Collections.unmodifiableList(Arrays.asList(HookPost.getInstance()));
    private static final Map<Activity, ActivityStateObserver> activityStateObservers = new WeakHashMap<>();

    private static final class ActivityStateObserver {
        final WeakReference<Activity> activityReference;
        final ViewTreeObserver.OnPreDrawListener preDrawListener;
        boolean applyRequested = true;

        ActivityStateObserver(
                WeakReference<Activity> activityReference,
                ViewTreeObserver.OnPreDrawListener preDrawListener) {
            this.activityReference = activityReference;
            this.preDrawListener = preDrawListener;
        }

        void requestApply() {
            applyRequested = true;
        }

        boolean onPreDraw() {
            Activity observedActivity = activityReference.get();
            if (observedActivity == null
                    || observedActivity.isFinishing()
                    || EnhanceReflection.isActivityDestroyed(observedActivity)) {
                return true;
            }
            if (!applyRequested) {
                return true;
            }
            applyRequested = false;
            applyAll(observedActivity);
            return true;
        }
    }

    private EnhanceHookManager() {}

    static void onActivityResumed(Activity activity) {
        if (!isTargetChanActive(activity)) {
            detachActivityStateObserver(activity);
            clearAll(activity);
            return;
        }
        attachActivityStateObserver(activity);
        requestApply(activity);
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
        if (paused || !isTargetChanActive(activity)) {
            detachActivityStateObserver(activity);
            clearAll(activity);
        } else {
            attachActivityStateObserver(activity);
            requestApply(activity);
            applyAll(activity);
        }
    }

    private static void attachActivityStateObserver(Activity activity) {
        detachActivityStateObserver(activity);
        View decorView = EnhanceReflection.getDecorViewSafe(activity);
        if (decorView == null) {
            return;
        }
        WeakReference<Activity> activityReference = new WeakReference<>(activity);
        ActivityStateObserver[] observerHolder = new ActivityStateObserver[1];
        ViewTreeObserver.OnPreDrawListener preDrawListener = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                ActivityStateObserver observer = observerHolder[0];
                return observer == null || observer.onPreDraw();
            }
        };
        ActivityStateObserver observer = new ActivityStateObserver(activityReference, preDrawListener);
        observerHolder[0] = observer;
        ViewTreeObserver viewTreeObserver = decorView.getViewTreeObserver();
        if (!viewTreeObserver.isAlive()) {
            return;
        }
        viewTreeObserver.addOnPreDrawListener(preDrawListener);
        activityStateObservers.put(activity, observer);
    }

    private static void detachActivityStateObserver(Activity activity) {
        ActivityStateObserver stateObserver = activityStateObservers.remove(activity);
        if (stateObserver == null) {
            return;
        }
        View decorView = EnhanceReflection.getDecorViewSafe(activity);
        if (decorView == null) {
            return;
        }
        ViewTreeObserver viewTreeObserver = decorView.getViewTreeObserver();
        if (!viewTreeObserver.isAlive()) {
            return;
        }
        viewTreeObserver.removeOnPreDrawListener(stateObserver.preDrawListener);
    }

    private static void requestApply(Activity activity) {
        if (!isTargetChanActive(activity)) {
            clearAll(activity);
            return;
        }
        ActivityStateObserver observer = activityStateObservers.get(activity);
        if (observer != null) {
            observer.requestApply();
        } else {
            applyAll(activity);
        }
    }

    private static void applyAll(Activity activity) {
        if (!isTargetChanActive(activity)) {
            clearAll(activity);
            return;
        }
        for (EnhanceHook controller : CONTROLLERS) {
            try {
                controller.apply(activity);
            } catch (Throwable t) {
                throw new RuntimeException(
                        "Controller apply failed: " + controller.getClass().getName(), t);
            }
        }
    }

    private static void clearAll(Activity activity) {
        for (EnhanceHook controller : CONTROLLERS) {
            try {
                controller.clear(activity);
            } catch (Throwable t) {
                throw new RuntimeException(
                        "Controller clear failed: " + controller.getClass().getName(), t);
            }
        }
    }

    private static boolean isTargetChanActive(Activity activity) {
        return EnhanceReflection.isActiveChan(activity, TARGET_CHAN_NAME);
    }
}
