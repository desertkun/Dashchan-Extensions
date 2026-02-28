package com.mishiranu.dashchan.chan.e444.enhance;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import com.mishiranu.dashchan.chan.e444.E444ChanLocator;
import com.mishiranu.dashchan.chan.e444.enhance.controllers.HookPost;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class EnhanceReflection {
    private static final String TAG = "EnhanceReflection";
    public static final String URI_HANDLER_ACTIVITY = "chan.application.UriHandlerActivity";
    public static final String ACTION_HANDLE_URI = "chan.intent.action.HANDLE_URI";

    private static final String BUNDLE_KEY_CHAN_NAME = "chanName";
    private static final String METHOD_GET_CURRENT_FRAGMENT = "getCurrentFragment";
    private static final String METHOD_GET_PAGE = "getPage";
    private static final String METHOD_GET_CHAN_NAME = "getChanName";
    private static final String METHOD_GET_SUPPORT_FRAGMENT_MANAGER = "getSupportFragmentManager";
    private static final String METHOD_GET_PRIMARY_NAVIGATION_FRAGMENT = "getPrimaryNavigationFragment";
    private static final String METHOD_FIND_FRAGMENT_BY_ID = "findFragmentById";
    private static final String POSTS_PAGE_CLASS_NAME = "PostsPage";

    private EnhanceReflection() {}

    public static Context resolveApplicationContextReflective() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentApplication = activityThreadClass.getDeclaredMethod("currentApplication");
            currentApplication.setAccessible(true);
            Object application = currentApplication.invoke(null);
            if (application instanceof Context) {
                return ((Context) application).getApplicationContext();
            }
        } catch (Throwable t) {
            Log.d(TAG, "Cannot resolve application context reflectively", t);
        }
        return null;
    }

    public static void syncCurrentActivitiesNow() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThread = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThread.setAccessible(true);
            Object activityThread = currentActivityThread.invoke(null);
            if (activityThread == null) {
                return;
            }
            Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);
            Object activities = activitiesField.get(activityThread);
            if (!(activities instanceof Map)) {
                return;
            }
            for (Object activityRecord : ((Map<?, ?>) activities).values()) {
                if (activityRecord == null) {
                    continue;
                }
                try {
                    Object activityObject = readField(activityRecord, "activity");
                    if (activityObject instanceof Activity) {
                        final Activity activity = (Activity) activityObject;
                        boolean paused = false;
                        Object pausedValue = readField(activityRecord, "paused");
                        if (pausedValue instanceof Boolean) {
                            paused = (Boolean) pausedValue;
                        }
                        final boolean pausedState = paused;
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                EnhanceHookManager.syncActivity(activity, pausedState);
                            }
                        });
                    }
                } catch (Throwable ignored) {
                    // Ignore single-record failures.
                }
            }
        } catch (Throwable t) {
            Log.d(TAG, "Immediate activity sync probe failed", t);
        }
    }

    public static View getDecorViewSafe(Activity activity) {
        try {
            return activity.getWindow().getDecorView();
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isActivityDestroyed(Activity activity) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed();
    }

    public static boolean isActiveChan(Activity activity, String chanName) {
        String normalizedChanName = normalizeChanName(chanName);
        if (normalizedChanName == null) {
            return false;
        }
        String activeChanName = resolveActiveChanName(activity);
        if (activeChanName == null) {
            return true;
        }
        return normalizedChanName.equals(activeChanName);
    }

    public static ViewGroup resolvePostsCollectionView(Activity activity) {
        Object currentFragment = invokeNoArgs(activity, METHOD_GET_CURRENT_FRAGMENT);
        if (currentFragment == null) {
            return null;
        }
        Object recyclerView = readField(currentFragment, "recyclerView");
        if (recyclerView instanceof ViewGroup) {
            return (ViewGroup) recyclerView;
        }
        Object listPage = readField(currentFragment, "listPage");
        if (listPage == null) {
            return null;
        }
        recyclerView = invokeNoArgs(listPage, "getRecyclerView");
        return recyclerView instanceof ViewGroup ? (ViewGroup) recyclerView : null;
    }

    public static boolean isThreadPageActive(Activity activity) {
        Object page = resolveCurrentPage(activity);
        if (page == null) {
            return false;
        }
        String threadNumber = readStringField(page, "threadNumber");
        if (threadNumber == null) {
            threadNumber = castString(invokeNoArgs(page, "getThreadNumber"));
        }
        return threadNumber != null && !threadNumber.trim().isEmpty();
    }

    public static String resolveActiveBoardName(Activity activity) {
        Object page = resolveCurrentPage(activity);
        if (page == null) {
            return null;
        }
        String boardName = readStringField(page, "boardName");
        if (boardName == null) {
            boardName = castString(invokeNoArgs(page, "getBoardName"));
        }
        if (boardName == null) {
            return null;
        }
        boardName = boardName.trim();
        return boardName.isEmpty() ? null : boardName;
    }

    public static String resolveActiveThreadNumber(Activity activity) {
        Object page = resolveCurrentPage(activity);
        if (page == null) {
            return null;
        }
        String threadNumber = readStringField(page, "threadNumber");
        if (threadNumber == null) {
            threadNumber = castString(invokeNoArgs(page, "getThreadNumber"));
        }
        if (threadNumber == null) {
            return null;
        }
        threadNumber = threadNumber.trim();
        return threadNumber.isEmpty() ? null : threadNumber;
    }

    public static boolean requestThreadRefresh(Activity activity) {
        Object currentFragment = invokeNoArgs(activity, METHOD_GET_CURRENT_FRAGMENT);
        Object listPage = readField(currentFragment, "listPage");
        return invokeOnListPulled(listPage);
    }

    public static boolean requestThreadPostRebind(Activity activity, String boardName, int postNumber) {
        return HookPost.requestThreadPostRebind(activity, boardName, postNumber);
    }

    public static boolean replaceThreadPost(
            Activity activity, E444ChanLocator locator, String boardName, int postNumber, Object post) {
        return HookPost.replaceThreadPost(activity, locator, boardName, postNumber, post);
    }

    public static void showError(Throwable throwable) {
        Task.showError(throwable);
    }

    public static <Result> void submitTask(
            E444ChanLocator locator, EnhanceTask<Result> task, Consumer<Result> onSuccess) {
        submitTask(locator, task, onSuccess, EnhanceReflection::showError);
    }

    public static <Result> void submitTask(
            E444ChanLocator locator,
            EnhanceTask<Result> task,
            Consumer<Result> onSuccess,
            Consumer<Throwable> onError) {
        Task.submit(locator, task, onSuccess, onError);
    }

    public static Object readField(Object source, String fieldName) {
        if (source == null) {
            return null;
        }
        Class<?> current = source.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(source);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    public static Integer resolvePostNumberForPostChild(ViewGroup collectionView, View postRoot) {
        return HookPost.resolvePostNumberForPostChild(collectionView, postRoot);
    }

    private static String resolveActiveChanName(Activity activity) {
        String chanName = resolveChanNameFromCurrentFragment(activity);
        if (chanName != null) {
            return chanName;
        }
        Intent intent = activity.getIntent();
        if (isUriHandlerActivity(activity)) {
            return resolveChanNameFromIntent(intent);
        }
        return null;
    }

    private static Object resolveCurrentPage(Activity activity) {
        Object currentFragment = invokeNoArgs(activity, METHOD_GET_CURRENT_FRAGMENT);
        Object page = resolvePageFromPageFragment(currentFragment);
        if (page != null) {
            return page;
        }
        Object fragmentManager = invokeNoArgs(activity, METHOD_GET_SUPPORT_FRAGMENT_MANAGER);
        if (fragmentManager == null) {
            return null;
        }
        int contentFragmentId =
                activity.getResources().getIdentifier("content_fragment", "id", activity.getPackageName());
        if (contentFragmentId != 0) {
            Object contentFragment =
                    invoke(fragmentManager, METHOD_FIND_FRAGMENT_BY_ID, new Class<?>[] {int.class}, contentFragmentId);
            page = resolvePageFromPageFragment(contentFragment);
            if (page != null) {
                return page;
            }
        }
        Object primaryFragment = invokeNoArgs(fragmentManager, METHOD_GET_PRIMARY_NAVIGATION_FRAGMENT);
        return resolvePageFromPageFragment(primaryFragment);
    }

    private static boolean isPostsPage(Object page) {
        String simpleName = page.getClass().getSimpleName();
        if (POSTS_PAGE_CLASS_NAME.equals(simpleName)) {
            return true;
        }
        String className = page.getClass().getName();
        return className.endsWith("." + POSTS_PAGE_CLASS_NAME);
    }

    private static String resolveChanNameFromCurrentFragment(Activity activity) {
        Object currentFragment = invokeNoArgs(activity, METHOD_GET_CURRENT_FRAGMENT);
        String chanName = resolveChanNameFromPageFragment(currentFragment);
        if (chanName != null) {
            return chanName;
        }
        Object fragmentManager = invokeNoArgs(activity, METHOD_GET_SUPPORT_FRAGMENT_MANAGER);
        if (fragmentManager == null) {
            return null;
        }
        int contentFragmentId =
                activity.getResources().getIdentifier("content_fragment", "id", activity.getPackageName());
        if (contentFragmentId != 0) {
            Object contentFragment =
                    invoke(fragmentManager, METHOD_FIND_FRAGMENT_BY_ID, new Class<?>[] {int.class}, contentFragmentId);
            chanName = resolveChanNameFromPageFragment(contentFragment);
            if (chanName != null) {
                return chanName;
            }
        }
        Object primaryFragment = invokeNoArgs(fragmentManager, METHOD_GET_PRIMARY_NAVIGATION_FRAGMENT);
        return resolveChanNameFromPageFragment(primaryFragment);
    }

    private static String resolveChanNameFromPageFragment(Object fragment) {
        Object page = resolvePageFromPageFragment(fragment);
        if (page == null) {
            return null;
        }
        return resolveChanNameFromPage(page);
    }

    private static Object resolvePageFromPageFragment(Object fragment) {
        if (fragment == null) {
            return null;
        }
        Object page = invokeNoArgs(fragment, METHOD_GET_PAGE);
        if (page != null) {
            return page;
        }
        return readField(fragment, "listPage");
    }

    private static boolean isUriHandlerActivity(Activity activity) {
        String className = activity.getClass().getName();
        return URI_HANDLER_ACTIVITY.equals(className) || className.endsWith(".UriHandlerActivity");
    }

    private static String resolveChanNameFromIntent(Intent intent) {
        if (intent == null) {
            return null;
        }
        Bundle extras = intent.getExtras();
        return normalizeChanName(readStringFromBundle(extras, BUNDLE_KEY_CHAN_NAME));
    }

    private static String resolveChanNameFromPage(Object page) {
        if (page == null) {
            return null;
        }
        String chanName = normalizeChanName(readStringField(page, BUNDLE_KEY_CHAN_NAME));
        if (chanName != null) {
            return chanName;
        }
        return normalizeChanName(castString(invokeNoArgs(page, METHOD_GET_CHAN_NAME)));
    }

    private static String normalizeChanName(String chanName) {
        if (chanName == null) {
            return null;
        }
        String normalized = chanName.trim().toLowerCase(Locale.US);
        return normalized.isEmpty() ? null : normalized;
    }

    private static String readStringField(Object source, String fieldName) {
        Object value = readField(source, fieldName);
        return value instanceof String ? (String) value : null;
    }

    public static Object invokeNoArgs(Object source, String methodName) {
        return invoke(source, methodName, new Class<?>[0]);
    }

    private static boolean invokeOnListPulled(Object source) {
        if (source == null) {
            return false;
        }
        String methodName = "onListPulled";
        Class<?> current = source.getClass();
        while (current != null) {
            Method[] methods = current.getDeclaredMethods();
            for (Method method : methods) {
                if (methodName.equals(method.getName()) && method.getParameterTypes().length == 2) {
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    if (parameterTypes[0].isPrimitive() || parameterTypes[1].isPrimitive()) {
                        continue;
                    }
                    try {
                        method.setAccessible(true);
                        method.invoke(source, null, null);
                        return true;
                    } catch (Throwable t) {
                        return false;
                    }
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }

    public static Object invoke(Object source, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (source == null) {
            return null;
        }
        Class<?> current = source.getClass();
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return method.invoke(source, args);
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        try {
            Method method = source.getClass().getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(source, args);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String castString(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private static String readStringFromBundle(Bundle bundle, String key) {
        if (bundle == null) {
            return null;
        }
        Object value = bundle.get(key);
        return value instanceof String ? (String) value : null;
    }
}
