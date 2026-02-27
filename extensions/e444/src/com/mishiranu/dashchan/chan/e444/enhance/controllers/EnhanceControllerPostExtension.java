package com.mishiranu.dashchan.chan.e444.enhance.controllers;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import com.mishiranu.dashchan.chan.e444.enhance.DashEnhance;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceHostResolver;
import com.mishiranu.dashchan.chan.e444.enhance.widgets.EnhanceWidget;
import com.mishiranu.dashchan.chan.e444.enhance.widgets.MenuWidget;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EnhanceControllerPostExtension implements EnhanceController {
    private static final String E444_PACKAGE_PREFIX = "com.mishiranu.dashchan.chan.e444.";
    private static final EnhanceControllerPostExtension INSTANCE = new EnhanceControllerPostExtension();
    private static final PostWidgetStore<EnhanceWidget> postExtensionItemsStore = new PostWidgetStore<>();
    private static final EnhanceHostResolver.ChanComponentMatcher ENHANCE_MATCHER =
            new EnhanceHostResolver.ChanComponentMatcher() {
                @Override
                public boolean matches(Object component) {
                    return component != null
                            && component.getClass().getName().startsWith(E444_PACKAGE_PREFIX);
                }
            };

    private EnhanceControllerPostExtension() {}

    public static EnhanceControllerPostExtension getInstance() {
        return INSTANCE;
    }

    public static void clearWidgetsForPost(int postNumber) {
        postExtensionItemsStore.removeForPost(postNumber);
    }

    public static void addWidgetForPost(int postNumber, EnhanceWidget widget) {
        postExtensionItemsStore.addForPost(postNumber, widget);
    }

    public static List<EnhanceWidget> getWidgetsForPost(int postNumber) {
        return postExtensionItemsStore.getForPost(postNumber);
    }

    @Override
    public void apply(Activity activity) {
        if (activity.isFinishing() || EnhanceHostResolver.isActivityDestroyed(activity)) {
            clear(activity);
            return;
        }
        if (!EnhanceHostResolver.hasMatchingComponentForActiveChan(activity, ENHANCE_MATCHER)
                || !EnhanceHostResolver.isThreadPageActive(activity)) {
            clear(activity);
            return;
        }
        injectButtonsIntoVisiblePosts(activity);
    }

    private static void injectButtonsIntoVisiblePosts(Activity activity) {
        View decorView = EnhanceHostResolver.getDecorViewSafe(activity);
        if (!(decorView instanceof ViewGroup)) {
            return;
        }
        injectButtonsRecursive(activity, (ViewGroup) decorView);
    }

    private static void injectButtonsRecursive(Activity activity, ViewGroup group) {
        if (isPostCollectionView(group)) {
            injectButtonsIntoRecyclerChildren(activity, group);
        }
        int childCount = group.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = group.getChildAt(i);
            if (child instanceof ViewGroup) {
                injectButtonsRecursive(activity, (ViewGroup) child);
            }
        }
    }

    private static boolean isRecyclerView(View view) {
        String className = view.getClass().getName();
        return className.contains("RecyclerView");
    }

    private static boolean isListView(View view) {
        String className = view.getClass().getName();
        return className.contains("ListView");
    }

    private static boolean isCollectionView(View view) {
        return isRecyclerView(view) || isListView(view);
    }

    private static boolean isPostCollectionView(View view) {
        if (!isCollectionView(view)) {
            return false;
        }
        Object adapter = invokeNoArgs(view, "getAdapter");
        if (adapter == null) {
            return false;
        }
        String adapterName = adapter.getClass().getName().toLowerCase(Locale.US);
        return adapterName.contains("post");
    }

    private static void injectButtonsIntoRecyclerChildren(Activity activity, ViewGroup recyclerView) {
        int childCount = recyclerView.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = recyclerView.getChildAt(i);
            if (child instanceof ViewGroup) {
                ensurePostButtons(activity, recyclerView, (ViewGroup) child);
            }
        }
    }

    private static void ensurePostButtons(Activity activity, ViewGroup recyclerView, ViewGroup postRoot) {
        Integer postNumber = EnhanceHostResolver.resolvePostNumberForPostChild(recyclerView, postRoot);
        if (postNumber == null) {
            removePostMenuWidget(postRoot);
            return;
        }
        List<EnhanceWidget> widgets = getWidgetsForPost(postNumber);
        if (widgets.isEmpty()) {
            removePostMenuWidget(postRoot);
            return;
        }
        for (int i = 0; i < widgets.size(); i++) {
            widgets.get(i).bind(activity, postRoot);
        }
    }

    private static void removePostMenuWidget(ViewGroup postRoot) {
        View existingContainerView = postRoot.findViewWithTag(MenuWidget.CONTAINER_TAG);
        if (existingContainerView != null && existingContainerView.getParent() instanceof ViewGroup) {
            ((ViewGroup) existingContainerView.getParent()).removeView(existingContainerView);
        }
    }

    @Override
    public void clear(Activity activity) {
        View decorView = EnhanceHostResolver.getDecorViewSafe(activity);
        if (!(decorView instanceof ViewGroup)) {
            return;
        }
        removeInjectedButtonsRecursive((ViewGroup) decorView);
    }

    private static void removeInjectedButtonsRecursive(ViewGroup group) {
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            View child = group.getChildAt(i);
            if (MenuWidget.CONTAINER_TAG.equals(child.getTag())) {
                group.removeViewAt(i);
                continue;
            }
            if (child instanceof ViewGroup) {
                removeInjectedButtonsRecursive((ViewGroup) child);
            }
        }
    }

    private static Object invokeNoArgs(Object source, String methodName) {
        return invoke(source, methodName, new Class<?>[0]);
    }

    private static Object invoke(Object source, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (source == null) {
            return null;
        }
        Class<?> current = source.getClass();
        while (current != null) {
            try {
                java.lang.reflect.Method method = current.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return method.invoke(source, args);
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        try {
            java.lang.reflect.Method method = source.getClass().getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(source, args);
        } catch (Throwable t) {
            return null;
        }
    }

    private static final class PostWidgetStore<T> {
        private final Map<Integer, List<T>> byPostNumber = Collections.synchronizedMap(new HashMap<Integer, List<T>>());

        public List<T> getForPost(int postNumber) {
            return byPostNumber.getOrDefault(postNumber, Collections.emptyList());
        }

        public void removeForPost(int postNumber) {
            byPostNumber.remove(postNumber);
        }

        public void addForPost(int postNumber, T item) {
            List<T> items = byPostNumber.get(postNumber);
            if (items == null) {
                items = new ArrayList<>();
                byPostNumber.put(postNumber, items);
            }
            items.add(item);
        }
    }
}
