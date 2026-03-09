package com.mishiranu.dashchan.chan.e444.enhance;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import com.mishiranu.dashchan.chan.e444.E444ChanLocator;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public final class EnhanceReflection {
    private static final String TAG = "EnhanceReflection";
    public static final String URI_HANDLER_ACTIVITY = "chan.application.UriHandlerActivity";
    public static final String ACTION_HANDLE_URI = "chan.intent.action.HANDLE_URI";

    private static final String METHOD_GET_CURRENT_FRAGMENT = "getCurrentFragment";
    private static final String METHOD_GET_PAGE = "getPage";
    private static final String METHOD_GET_SUPPORT_FRAGMENT_MANAGER = "getSupportFragmentManager";
    private static final String METHOD_GET_PRIMARY_NAVIGATION_FRAGMENT = "getPrimaryNavigationFragment";
    private static final String METHOD_FIND_FRAGMENT_BY_ID = "findFragmentById";

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
                Object activityObject = readField(activityRecord, "activity");
                if (!(activityObject instanceof Activity)) {
                    continue;
                }
                final Activity activity = (Activity) activityObject;
                Object pausedValue = readField(activityRecord, "paused");
                final boolean pausedState = pausedValue instanceof Boolean && (Boolean) pausedValue;
                activity.runOnUiThread(() -> EnhanceHookManager.syncActivity(activity, pausedState));
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

    public static ViewGroup resolvePostsCollectionView(Activity activity) {
        Object currentFragment = resolveCurrentFragment(activity);
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
        Object threadNumberValue = readField(page, "threadNumber");
        String threadNumber = threadNumberValue instanceof String
                ? (String) threadNumberValue
                : castString(invokeNoArgs(page, "getThreadNumber"));
        return threadNumber != null && !threadNumber.trim().isEmpty();
    }

    public static Object resolveCurrentPageToken(Activity activity) {
        return resolveCurrentPage(activity);
    }

    public static void showError(Throwable throwable) {
        Task.showError(throwable);
    }

    public static <Result> void submitTask(
            E444ChanLocator locator, EnhanceTask<Result> task, TaskCallback<Result> onSuccess) {
        submitTask(locator, task, onSuccess, EnhanceReflection::showError);
    }

    public static <Result> void submitTask(
            E444ChanLocator locator,
            EnhanceTask<Result> task,
            TaskCallback<Result> onSuccess,
            TaskCallback<Throwable> onError) {
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

    public static Object invokeNoArgs(Object source, String methodName) {
        return invoke(source, methodName, new Class<?>[0]);
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
        return null;
    }

    private static Object resolveCurrentPage(Activity activity) {
        Object currentFragment = resolveCurrentFragment(activity);
        if (currentFragment == null) {
            return null;
        }
        Object page = invokeNoArgs(currentFragment, METHOD_GET_PAGE);
        return page != null ? page : readField(currentFragment, "listPage");
    }

    private static Object resolveCurrentFragment(Activity activity) {
        Object currentFragment = invokeNoArgs(activity, METHOD_GET_CURRENT_FRAGMENT);
        if (currentFragment != null) {
            return currentFragment;
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
            if (contentFragment != null) {
                return contentFragment;
            }
        }
        return invokeNoArgs(fragmentManager, METHOD_GET_PRIMARY_NAVIGATION_FRAGMENT);
    }

    private static String castString(Object value) {
        return value instanceof String ? (String) value : null;
    }
}
