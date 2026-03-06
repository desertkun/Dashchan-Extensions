package com.mishiranu.dashchan.chan.e444.enhance;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import com.mishiranu.dashchan.chan.e444.enhance.controllers.HookPost;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EnhanceShim {
    private static final String TAG = "EnhanceShim";
    private static final Object PROBE_LOCK = new Object();

    // These are intentionally optional and treated as best-effort only.
    private static final String[] CANDIDATE_SYMBOLS = {
        "com.mishiranu.dashchan.content.model.PostItem",
        "com.mishiranu.dashchan.ui.navigator.manager.UiManager",
        "com.mishiranu.dashchan.ui.navigator.page.PostsPage"
    };

    private static volatile ProbeState cachedProbeState;
    private static volatile boolean activityHookInstalled;

    private EnhanceShim() {}

    public static boolean ensureActivityHookInstalled() {
        return ensureActivityHookInstalled(null);
    }

    public static boolean ensureActivityHookInstalled(Context context) {
        if (activityHookInstalled) {
            EnhanceReflection.syncCurrentActivitiesNow();
            return true;
        }
        synchronized (PROBE_LOCK) {
            if (activityHookInstalled) {
                EnhanceReflection.syncCurrentActivitiesNow();
                return true;
            }
            Context appContext = context != null
                    ? context.getApplicationContext()
                    : EnhanceReflection.resolveApplicationContextReflective();
            if (!(appContext instanceof Application)) {
                Log.w(TAG, "Cannot install activity hook: app context is not Application");
                return false;
            }
            try {
                ((Application) appContext)
                        .registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                            @Override
                            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

                            @Override
                            public void onActivityStarted(Activity activity) {}

                            @Override
                            public void onActivityResumed(Activity activity) {
                                EnhanceHookManager.onActivityResumed(activity);
                            }

                            @Override
                            public void onActivityPaused(Activity activity) {
                                EnhanceHookManager.onActivityPaused(activity);
                            }

                            @Override
                            public void onActivityStopped(Activity activity) {}

                            @Override
                            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

                            @Override
                            public void onActivityDestroyed(Activity activity) {
                                EnhanceHookManager.onActivityDestroyed(activity);
                            }
                        });
                activityHookInstalled = true;
                EnhanceReflection.syncCurrentActivitiesNow();
                Log.i(TAG, "Activity hook installed");
                return true;
            } catch (Throwable t) {
                Log.w(TAG, "Cannot install activity hook", t);
                return false;
            }
        }
    }

    public static ProbeState probe(Context context) {
        ProbeState probeState = cachedProbeState;
        String packageName = context.getPackageName();
        String version = resolveVersion(context);
        if (probeState != null && probeState.matches(packageName, version)) {
            return probeState;
        }
        synchronized (PROBE_LOCK) {
            probeState = cachedProbeState;
            if (probeState != null && probeState.matches(packageName, version)) {
                return probeState;
            }
            probeState = performProbe(context, packageName, version);
            cachedProbeState = probeState;
            return probeState;
        }
    }

    private static ProbeState performProbe(Context context, String packageName, String version) {
        ArrayList<String> availableSymbols = new ArrayList<>();
        ArrayList<String> missingSymbols = new ArrayList<>();
        ClassLoader classLoader = context.getClassLoader();
        for (String symbol : CANDIDATE_SYMBOLS) {
            if (hasClass(classLoader, symbol)) {
                availableSymbols.add(symbol);
            } else {
                missingSymbols.add(symbol);
            }
        }
        ProbeState probeState =
                new ProbeState(packageName, version, !availableSymbols.isEmpty(), availableSymbols, missingSymbols);
        Log.i(
                TAG,
                "Probe completed: host=" + packageName + " version=" + version + " ready=" + probeState.ready
                        + " available=" + availableSymbols.size() + " missing=" + missingSymbols.size());
        return probeState;
    }

    @SuppressWarnings("deprecation")
    private static String resolveVersion(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            long versionCode;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                versionCode = packageInfo.getLongVersionCode();
            } else {
                versionCode = packageInfo.versionCode;
            }
            return packageInfo.versionName + "(" + versionCode + ")";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static boolean hasClass(ClassLoader classLoader, String className) {
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static final class ProbeState {
        public final String packageName;
        public final String version;
        public final boolean ready;
        public final List<String> availableSymbols;
        public final List<String> missingSymbols;

        private ProbeState(
                String packageName,
                String version,
                boolean ready,
                List<String> availableSymbols,
                List<String> missingSymbols) {
            this.packageName = packageName;
            this.version = version;
            this.ready = ready;
            this.availableSymbols = Collections.unmodifiableList(new ArrayList<>(availableSymbols));
            this.missingSymbols = Collections.unmodifiableList(new ArrayList<>(missingSymbols));
        }

        private boolean matches(String packageName, String version) {
            return this.packageName.equals(packageName) && this.version.equals(version);
        }
    }
}
