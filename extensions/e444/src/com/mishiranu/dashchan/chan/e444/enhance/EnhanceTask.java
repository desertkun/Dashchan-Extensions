package com.mishiranu.dashchan.chan.e444.enhance;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import chan.http.HttpRequest;
import com.mishiranu.dashchan.chan.e444.E444ChanLocator;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.Executor;

public interface EnhanceTask<Result> {
    Result run(E444ChanLocator locator, HttpRequest.Preset preset) throws Exception;
}

final class Task {
    private static final String TAG = "EnhanceTask";
    private static final String CLICKABLE_TOAST_CLASS = "com.mishiranu.dashchan.widget.ClickableToast";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Executor DASHCHAN_EXECUTOR = resolveDashchanExecutor();

    private Task() {}

    static <Result> void submit(
            E444ChanLocator locator,
            EnhanceTask<Result> task,
            TaskCallback<Result> onSuccess,
            TaskCallback<Throwable> onError) {
        DASHCHAN_EXECUTOR.execute(() -> {
            try {
                ClassLoader classLoader = locator.getClass().getClassLoader();
                Object holder = createTaskHolder(locator, classLoader);
                Object holderUse = holder.getClass().getMethod("use").invoke(holder);
                Result result;
                try {
                    HttpRequest.Preset preset = createTaskPreset(holder);
                    result = task.run(locator, preset);
                } finally {
                    holderUse.getClass().getMethod("close").invoke(holderUse);
                }
                MAIN_HANDLER.post(() -> onSuccess.accept(result));
            } catch (Throwable throwable) {
                MAIN_HANDLER.post(() -> onError.accept(throwable));
            }
        });
    }

    static void showError(Throwable throwable) {
        Object errorItem = null;
        Throwable current = throwable;
        while (current != null && errorItem == null) {
            errorItem = EnhanceReflection.invokeNoArgs(current, "getErrorItemAndHandle");
            current = current.getCause();
        }
        if (errorItem != null && showClickableToast(errorItem)) {
            return;
        }
        if (throwable != null && throwable.getMessage() != null && showClickableToast(throwable.getMessage())) {
            return;
        }
        Log.e(TAG, "Failed to show error", throwable);
    }

    private static Object createTaskHolder(E444ChanLocator locator, ClassLoader classLoader) throws Exception {
        Class<?> locatorClass = Class.forName("chan.content.ChanLocator", false, classLoader);
        Method getMethod = locatorClass.getMethod("get");
        Object chan = getMethod.invoke(locator);
        Class<?> holderClass = Class.forName("chan.http.HttpHolder", false, classLoader);
        return holderClass.getConstructor(chan.getClass()).newInstance(chan);
    }

    private static HttpRequest.Preset createTaskPreset(Object holder) {
        return (HttpRequest.Preset) Proxy.newProxyInstance(
                HttpRequest.Preset.class.getClassLoader(),
                new Class<?>[] {HttpRequest.Preset.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getHolder":
                            return holder;
                        case "toString":
                            return "EnhanceReflectionTaskPreset";
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return args != null && args.length == 1 && proxy == args[0];
                        default:
                            return null;
                    }
                });
    }

    private static Executor resolveDashchanExecutor() {
        try {
            Class<?> concurrentUtilsClass = Class.forName("com.mishiranu.dashchan.util.ConcurrentUtils");
            Field field = concurrentUtilsClass.getDeclaredField("PARALLEL_EXECUTOR");
            field.setAccessible(true);
            return (Executor) field.get(null);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to resolve Dashchan executor", e);
        }
    }

    private static boolean showClickableToast(Object value) {
        if (value == null) return false;
        try {
            Class<?> clickableToastClass = Class.forName(CLICKABLE_TOAST_CLASS);
            Method[] methods = clickableToastClass.getMethods();
            for (Method method : methods) {
                if (!"show".equals(method.getName())) continue;
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 1 || !parameterTypes[0].isInstance(value)) continue;
                method.invoke(null, value);
                return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
}
