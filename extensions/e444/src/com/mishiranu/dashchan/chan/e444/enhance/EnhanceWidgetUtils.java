package com.mishiranu.dashchan.chan.e444.enhance;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import chan.util.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.mishiranu.dashchan.chan.e444.E444ChanLocator;
import com.mishiranu.dashchan.chan.e444.E444JsonUtils;
import com.mishiranu.dashchan.chan.e444.enhance.tasks.TaskReadPost;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EnhanceWidgetUtils {
    @FunctionalInterface
    public interface ViewFactory<T extends View> {
        T create();
    }

    @FunctionalInterface
    public interface ValueSanitizer<T> {
        T sanitize(T value);
    }

    public static final int DEFAULT_CONTAINER_ORIENTATION = LinearLayout.VERTICAL;
    public static final int DEFAULT_CONTAINER_GRAVITY = Gravity.START;

    private EnhanceWidgetUtils() {}

    public static int dp(Activity activity, int value) {
        return dp(activity.getResources(), value);
    }

    public static int dp(View view, int value) {
        return dp(view.getResources(), value);
    }

    public static int dp(Resources resources, int value) {
        return Math.round(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.getDisplayMetrics()));
    }

    public static int applyAlpha(int color, float alpha) {
        int baseAlpha = Math.round(255f * alpha);
        return (color & 0x00ffffff) | (baseAlpha << 24);
    }

    public static int resolveAttrColor(Activity activity, int attr, int fallback) {
        android.content.res.TypedArray typedArray = activity.obtainStyledAttributes(new int[] {attr});
        int color = typedArray.getColor(0, fallback);
        typedArray.recycle();
        return color;
    }

    public static int resolveThemeColor(Activity activity, String hostAttrName, int fallbackAttr) {
        return resolveThemeColor(activity, hostAttrName, fallbackAttr, Color.WHITE);
    }

    public static int resolveThemeColor(
            Activity activity, String hostAttrName, int fallbackAttr, int fallbackColor) {
        int fallback = resolveAttrColor(activity, fallbackAttr, fallbackColor);
        int hostAttr = activity.getResources().getIdentifier(hostAttrName, "attr", activity.getPackageName());
        if (hostAttr == 0) {
            return fallback;
        }
        return resolveAttrColor(activity, hostAttr, fallback);
    }

    public static int resolveRequiredThemeColor(Activity activity, String hostAttrName) {
        int hostAttr = activity.getResources().getIdentifier(hostAttrName, "attr", activity.getPackageName());
        if (hostAttr == 0) {
            throw new IllegalStateException("Required theme attr is missing: " + hostAttrName);
        }
        return resolveRequiredAttrColor(activity, hostAttr);
    }

    public static int resolveRequiredAttrColor(Activity activity, int attr) {
        android.content.res.TypedArray typedArray = activity.obtainStyledAttributes(new int[] {attr});
        if (!typedArray.hasValue(0)) {
            typedArray.recycle();
            throw new IllegalStateException("Required attr has no value: " + attr);
        }
        ColorStateList colorStateList = typedArray.getColorStateList(0);
        typedArray.recycle();
        if (colorStateList == null) {
            throw new IllegalStateException("Required attr is not a color value: " + attr);
        }
        return colorStateList.getDefaultColor();
    }

    public static LinearLayout obtainOrCreateLinearContainer(
            Activity activity, ViewGroup root, String containerTag) {
        return obtainOrCreateLinearContainer(
                activity,
                root,
                containerTag,
                DEFAULT_CONTAINER_ORIENTATION,
                DEFAULT_CONTAINER_GRAVITY);
    }

    public static LinearLayout obtainOrCreateLinearContainer(
            Activity activity, ViewGroup root, String containerTag, int orientation, int gravity) {
        View existingView = root.findViewWithTag(containerTag);
        if (existingView instanceof LinearLayout) {
            return (LinearLayout) existingView;
        }
        LinearLayout container = new LinearLayout(activity);
        container.setTag(containerTag);
        container.setOrientation(orientation);
        container.setGravity(gravity);
        container.setClickable(false);
        container.setFocusable(false);
        root.addView(container);
        return container;
    }

    public static <T extends View> T obtainOrCreateChild(
            ViewGroup parent,
            int index,
            Class<T> viewClass,
            ViewFactory<T> viewFactory,
            ViewGroup.LayoutParams layoutParams) {
        View existing = index < parent.getChildCount() ? parent.getChildAt(index) : null;
        if (viewClass.isInstance(existing)) {
            return viewClass.cast(existing);
        }
        T view = viewFactory.create();
        if (index < parent.getChildCount()) {
            parent.removeViewAt(index);
            if (layoutParams != null) {
                parent.addView(view, index, layoutParams);
            } else {
                parent.addView(view, index);
            }
        } else if (layoutParams != null) {
            parent.addView(view, layoutParams);
        } else {
            parent.addView(view);
        }
        return view;
    }

    public static <T extends View> T obtainOrCreateOnlyChild(
            ViewGroup parent, Class<T> viewClass, ViewFactory<T> viewFactory, ViewGroup.LayoutParams layoutParams) {
        if (parent.getChildCount() == 1) {
            View existing = parent.getChildAt(0);
            if (viewClass.isInstance(existing)) {
                return viewClass.cast(existing);
            }
        }
        parent.removeAllViews();
        T view = viewFactory.create();
        if (layoutParams != null) {
            parent.addView(view, layoutParams);
        } else {
            parent.addView(view);
        }
        return view;
    }

    public static String buildPostStateKey(String boardName, int postNumber) {
        return boardName + ":" + postNumber;
    }

    public static void refreshPost(Activity activity, E444ChanLocator locator, String boardName, int postNumber) {
        EnhanceReflection.submitTask(
                locator,
                new TaskReadPost(boardName, postNumber),
                new TaskCallback<chan.content.model.Post>() {
                    @Override
                    public void accept(chan.content.model.Post post) {
                        EnhanceReflection.syncCurrentActivitiesNow();
                        activity.getWindow()
                                .getDecorView()
                                .postDelayed(EnhanceReflection::syncCurrentActivitiesNow, 120L);
                    }
                },
                new TaskCallback<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {}
                });
    }

    public static final class PostSelectionStore<T> {
        private final String prefsName;
        private final String prefsKey;
        private final TypeReference<Map<String, T>> valueType;
        private final ValueSanitizer<T> sanitizer;
        private final Map<String, T> valuesByPost = new ConcurrentHashMap<>();
        private final Object lock = new Object();
        private volatile boolean loaded;

        public PostSelectionStore(
                String prefsName,
                String prefsKey,
                TypeReference<Map<String, T>> valueType,
                ValueSanitizer<T> sanitizer) {
            this.prefsName = prefsName;
            this.prefsKey = prefsKey;
            this.valueType = valueType;
            this.sanitizer = sanitizer;
        }

        public T get(Context context, String postStateKey) {
            ensureLoaded(context);
            return valuesByPost.get(postStateKey);
        }

        public void put(Context context, String postStateKey, T value) {
            if (StringUtils.isEmpty(postStateKey)) {
                return;
            }
            T sanitized = sanitize(value);
            if (sanitized == null) {
                remove(context, postStateKey);
                return;
            }
            ensureLoaded(context);
            synchronized (lock) {
                valuesByPost.put(postStateKey, sanitized);
                persistLocked(context.getApplicationContext());
            }
        }

        public void remove(Context context, String postStateKey) {
            if (StringUtils.isEmpty(postStateKey)) {
                return;
            }
            ensureLoaded(context);
            synchronized (lock) {
                valuesByPost.remove(postStateKey);
                persistLocked(context.getApplicationContext());
            }
        }

        private void ensureLoaded(Context context) {
            if (loaded) {
                return;
            }
            synchronized (lock) {
                if (loaded) {
                    return;
                }
                Context appContext = context.getApplicationContext();
                SharedPreferences preferences = appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
                String serialized = preferences.getString(prefsKey, null);
                if (!StringUtils.isEmpty(serialized)) {
                    try {
                        Map<String, T> root = E444JsonUtils.fromJson(serialized, valueType);
                        if (root != null) {
                            for (Map.Entry<String, T> entry : root.entrySet()) {
                                String postKey = StringUtils.nullIfEmpty(entry.getKey());
                                T value = sanitize(entry.getValue());
                                if (!StringUtils.isEmpty(postKey) && value != null) {
                                    valuesByPost.put(postKey, value);
                                }
                            }
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                loaded = true;
            }
        }

        private void persistLocked(Context context) {
            String serialized = E444JsonUtils.toJson(valuesByPost);
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    .edit()
                    .putString(prefsKey, serialized)
                    .apply();
        }

        private T sanitize(T value) {
            return sanitizer != null ? sanitizer.sanitize(value) : value;
        }
    }
}
