package com.mishiranu.dashchan.chan.e444.enhance.controllers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.mishiranu.dashchan.chan.e444.E444ChanLocator;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceHook;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceReflection;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceWidget;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public final class HookPost implements EnhanceHook {
    private static final String TAG = "HookPost";
    private static final HookPost INSTANCE = new HookPost();
    public static final int TAG_CONTEXT_MENU_CLOSE_ACTION = 0xE4441001;
    private static final String ROOT_TAG = "e444_post_extension_root";
    private static final String CONTEXT_MENU_WRAPPER_TAG = "e444_context_menu_wrapper";
    private static final String CONTEXT_MENU_EXTENSION_ROOT_TAG = "e444_context_menu_extension_root";
    private static final String PROXY_CACHE_DIR = "e444_proxy";
    private static final String[][] CONSTRUCTOR_PROFILES = {
        {
            "com.mishiranu.dashchan.ui.navigator.adapter.PostsAdapter$Callback",
            "callback",
            "java.lang.String",
            "chanName",
            "com.mishiranu.dashchan.ui.navigator.manager.UiManager",
            "uiManager",
            "com.mishiranu.dashchan.ui.posting.Replyable",
            "replyable",
            "com.mishiranu.dashchan.ui.navigator.manager.UiManager$PostStateProvider",
            "postStateProvider",
            "androidx.fragment.app.FragmentManager",
            "fragmentManager",
            "androidx.recyclerview.widget.RecyclerView",
            "collectionView",
            "java.util.Map",
            "postItemsMap",
            "com.mishiranu.dashchan.content.model.PostItem$HideState$Map",
            "hiddenPosts"
        },
        {
            "com.mishiranu.dashchan.ui.navigator.adapter.PostsAdapter$Callback",
            "callback",
            "java.lang.String",
            "chanName",
            "com.mishiranu.dashchan.ui.navigator.manager.UiManager",
            "uiManager",
            "com.mishiranu.dashchan.ui.posting.Replyable",
            "replyable",
            "com.mishiranu.dashchan.ui.navigator.manager.UiManager$PostStateProvider",
            "postStateProvider",
            "androidx.fragment.app.FragmentManager",
            "fragmentManager",
            "androidx.recyclerview.widget.RecyclerView",
            "collectionView",
            "java.util.Map",
            "postItemsMap"
        },
        {
            "com.mishiranu.dashchan.ui.navigator.adapter.PostsAdapter$Callback",
            "callback",
            "java.lang.String",
            "chanName",
            "com.mishiranu.dashchan.ui.navigator.manager.UiManager",
            "uiManager",
            "com.mishiranu.dashchan.ui.posting.Replyable",
            "replyable",
            "com.mishiranu.dashchan.ui.navigator.manager.UiManager$PostStateProvider",
            "postStateProvider",
            "androidx.recyclerview.widget.RecyclerView",
            "collectionView",
            "java.util.Map",
            "postItemsMap"
        }
    };
    private static final Map<String, List<EnhanceWidget>> widgetsByPostKey = new ConcurrentHashMap<>();
    private static final Map<String, List<EnhanceWidget>> contextMenuWidgetsByPostKey = new ConcurrentHashMap<>();
    private static final Map<ViewGroup, Object> wrappedAdaptersByCollection = new WeakHashMap<>();
    private static final Map<Object, Object> wrappedInteractionsByUiManager = new WeakHashMap<>();
    private static final Map<Object, Object> wrappedDialogsByUiManager = new WeakHashMap<>();

    private HookPost() {}

    public static HookPost getInstance() {
        return INSTANCE;
    }

    public static void prepareProxyCache(Context context) {
        resolveProxyBuilderClass();
        context.getDir(PROXY_CACHE_DIR, Context.MODE_PRIVATE).mkdirs();
    }

    public static void setWidgetsForPost(String boardName, int postNumber, List<EnhanceWidget> widgets) {
        String key = buildPostKey(boardName, postNumber);
        if (widgets.isEmpty()) {
            widgetsByPostKey.remove(key);
            return;
        }
        widgetsByPostKey.put(key, new ArrayList<>(widgets));
    }

    public static void setContextMenuWidgetsForPost(String boardName, int postNumber, List<EnhanceWidget> widgets) {
        String key = buildPostKey(boardName, postNumber);
        if (widgets == null || widgets.isEmpty()) {
            contextMenuWidgetsByPostKey.remove(key);
            return;
        }
        ArrayList<EnhanceWidget> sanitized = new ArrayList<>(widgets.size());
        for (int i = 0; i < widgets.size(); i++) {
            EnhanceWidget widget = widgets.get(i);
            if (widget != null) {
                sanitized.add(widget);
            }
        }
        if (sanitized.isEmpty()) {
            contextMenuWidgetsByPostKey.remove(key);
        } else {
            contextMenuWidgetsByPostKey.put(key, sanitized);
        }
    }

    @Override
    public void apply(Activity activity) {
        if (activity.isFinishing() || EnhanceReflection.isActivityDestroyed(activity)) return;
        if (!EnhanceReflection.isThreadPageActive(activity)) return;
        ViewGroup postsCollection = EnhanceReflection.resolvePostsCollectionView(activity);
        if (postsCollection == null) return;
        Object currentAdapter = EnhanceReflection.invokeNoArgs(postsCollection, "getAdapter");
        if (currentAdapter == null) return;
        contextHandleAdapterObserved(activity, currentAdapter);
        if (wrappedAdaptersByCollection.get(postsCollection) == currentAdapter) return;
        if (isAlreadyWrappedAdapter(currentAdapter)) {
            wrappedAdaptersByCollection.put(postsCollection, currentAdapter);
            return;
        }
        Object wrappedAdapter = wrapAndInstall(activity, postsCollection, currentAdapter);
        wrappedAdaptersByCollection.put(postsCollection, wrappedAdapter);
        contextHandleAdapterObserved(activity, wrappedAdapter);
    }

    @Override
    public void clear(Activity activity) {
        ViewGroup postsCollection = EnhanceReflection.resolvePostsCollectionView(activity);
        if (postsCollection == null) return;
        for (int i = 0; i < postsCollection.getChildCount(); i++) {
            View child = postsCollection.getChildAt(i);
            if (!(child instanceof ViewGroup)) continue;
            ViewGroup postRoot = (ViewGroup) child;
            inlineHandleOnRecycle(postRoot);
        }
    }

    private static void bindWidgets(Activity activity, ViewGroup collectionView, ViewGroup postRoot, int position) {
        ViewGroup root = ensureRoot(activity, postRoot);
        root.removeAllViews();
        Object adapter = EnhanceReflection.invokeNoArgs(collectionView, "getAdapter");
        Object postModel = EnhanceReflection.invoke(adapter, "getItem", new Class<?>[] {int.class}, position);
        Object boardValue = postModel != null ? EnhanceReflection.invokeNoArgs(postModel, "getBoardName") : null;
        Object postNumber = postModel != null ? EnhanceReflection.invokeNoArgs(postModel, "getPostNumber") : null;
        Object major = EnhanceReflection.readField(postNumber, "major");
        if (!(boardValue instanceof String) || !(major instanceof Number)) return;
        int number = ((Number) major).intValue();
        if (number <= 0) return;
        List<EnhanceWidget> widgets =
                widgetsByPostKey.getOrDefault(buildPostKey((String) boardValue, number), Collections.emptyList());
        for (int i = 0; i < widgets.size(); i++) {
            EnhanceWidget widget = widgets.get(i);
            widget.inject(activity, root);
            View container = root.findViewWithTag(widget.getContainerTag());
            if (container != null && container.getParent() == root)
                container.setLayoutParams(widget.createLayoutParams(activity));
        }
    }

    static void inlineHandleOnBind(Activity activity, ViewGroup collectionView, ViewGroup postRoot, int position) {
        bindWidgets(activity, collectionView, postRoot, position);
    }

    private static int bottomBarId(Activity activity) {
        return activity.getResources().getIdentifier("bottom_bar", "id", activity.getPackageName());
    }

    private static ViewGroup ensureRoot(Activity activity, ViewGroup postRoot) {
        ViewGroup contentRoot = resolveContentRoot(activity, postRoot);
        View existing = postRoot.findViewWithTag(ROOT_TAG);
        if (existing instanceof LinearLayout) {
            LinearLayout root = (LinearLayout) existing;
            ViewGroup parent = root.getParent() instanceof ViewGroup ? (ViewGroup) root.getParent() : null;
            int insertIndex = resolveInsertIndex(activity, contentRoot, root);
            if (parent != contentRoot || contentRoot.indexOfChild(root) != insertIndex) {
                if (parent != null) parent.removeView(root);
                contentRoot.addView(root, insertIndex, createRootLayoutParams());
            }
            return root;
        }
        LinearLayout root = new LinearLayout(activity);
        root.setTag(ROOT_TAG);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClickable(false);
        root.setFocusable(false);
        contentRoot.addView(root, resolveInsertIndex(activity, contentRoot, null), createRootLayoutParams());
        return root;
    }

    private static LinearLayout.LayoutParams createRootLayoutParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static ViewGroup resolveContentRoot(Activity activity, ViewGroup postRoot) {
        View anchor = resolveAnchorView(activity, postRoot);
        if (anchor != null && anchor.getParent() instanceof ViewGroup) return (ViewGroup) anchor.getParent();
        return postRoot;
    }

    private static int resolveInsertIndex(Activity activity, ViewGroup contentRoot, View existingRoot) {
        View anchor = resolveAnchorView(activity, contentRoot);
        if (anchor != null && anchor.getParent() == contentRoot) {
            int index = contentRoot.indexOfChild(anchor);
            if (existingRoot != null) {
                int existingIndex = contentRoot.indexOfChild(existingRoot);
                if (existingIndex >= 0 && existingIndex < index) index--;
            }
            return Math.max(0, index);
        }
        int index = contentRoot.getChildCount();
        if (existingRoot != null && existingRoot.getParent() == contentRoot) index--;
        return Math.max(0, index);
    }

    private static View resolveAnchorView(Activity activity, ViewGroup root) {
        int textSelectionPaddingId =
                activity.getResources().getIdentifier("text_selection_padding", "id", activity.getPackageName());
        if (textSelectionPaddingId != 0) {
            View view = root.findViewById(textSelectionPaddingId);
            if (view != null) return view;
        }
        int textBarPaddingId =
                activity.getResources().getIdentifier("text_bar_padding", "id", activity.getPackageName());
        if (textBarPaddingId != 0) {
            View view = root.findViewById(textBarPaddingId);
            if (view != null) return view;
        }
        int bottomBarId = bottomBarId(activity);
        return bottomBarId != 0 ? root.findViewById(bottomBarId) : null;
    }

    private static void clearRoot(ViewGroup postRoot) {
        View root = postRoot.findViewWithTag(ROOT_TAG);
        if (root instanceof ViewGroup) ((ViewGroup) root).removeAllViews();
    }

    static void inlineHandleOnRecycle(ViewGroup postRoot) {
        clearRoot(postRoot);
    }

    private static void contextHandleAdapterObserved(Activity activity, Object adapter) {
        maybeAttachContextMenuWidgets(activity, adapter);
    }

    private static void contextHandlePostLongClick(Object configurationSet, Object postItem) {
        if (maybeAttachContextMenuWidgetsFromConfigurationSet(configurationSet, postItem, null)) {
            return;
        }
        Activity activity = resolveActivityFromConfigurationSet(configurationSet);
        View decorView = activity != null ? EnhanceReflection.getDecorViewSafe(activity) : null;
        if (decorView == null) {
            return;
        }
        scheduleContextMenuAttachRetry(decorView, configurationSet, postItem, 6);
    }

    private static void contextHandlePostContextMenuDialog(
            Object configurationSet, Object postNumberObject, AlertDialog dialog, Activity knownActivity) {
        if (dialog == null) {
            Log.d(TAG, "contextHandlePostContextMenuDialog: skip dialog null");
            return;
        }
        Activity activity = knownActivity != null ? knownActivity : resolveActivity(dialog, configurationSet);
        if (activity == null || activity.isFinishing() || EnhanceReflection.isActivityDestroyed(activity)) {
            Log.d(TAG, "contextHandlePostContextMenuDialog: skip activity invalid");
            return;
        }
        Integer postNumber = parsePostNumber(postNumberObject);
        if (postNumber == null || postNumber <= 0) {
            Log.d(TAG, "contextHandlePostContextMenuDialog: skip post invalid " + postNumberObject);
            return;
        }
        String boardName = resolveBoardNameForContextMenu(configurationSet, postNumberObject, activity);
        if (boardName == null || boardName.isEmpty()) {
            Log.d(TAG, "contextHandlePostContextMenuDialog: skip board unresolved");
            return;
        }
        String postKey = buildPostKey(boardName, postNumber);
        List<EnhanceWidget> widgets = contextMenuWidgetsByPostKey.get(postKey);
        if (widgets == null || widgets.isEmpty()) {
            Log.d(TAG, "contextHandlePostContextMenuDialog: skip no widgets for post " + postKey);
            return;
        }
        if (attachContextMenuWidgetsView(activity, dialog, widgets)) {
            return;
        }
        View decorView = EnhanceReflection.getDecorViewSafe(activity);
        scheduleAttachToKnownDialog(decorView, activity, dialog, widgets, 8);
    }

    private static void maybeAttachContextMenuWidgets(Activity activity, Object adapter) {
        Object configurationSet = resolveConfigurationSetFromAdapter(adapter);
        maybeAttachContextMenuWidgetsFromConfigurationSet(configurationSet, null, activity);
    }

    private static boolean maybeAttachContextMenuWidgetsFromConfigurationSet(
            Object configurationSet, Object postItem, Activity knownActivity) {
        if (configurationSet == null) return false;
        Object stackInstance = EnhanceReflection.readField(configurationSet, "stackInstance");
        Object postContextMenu = EnhanceReflection.readField(stackInstance, "postContextMenu");
        Object dialogObject = EnhanceReflection.readField(postContextMenu, "second");
        if (!(dialogObject instanceof AlertDialog)) return false;
        AlertDialog dialog = (AlertDialog) dialogObject;
        if (!dialog.isShowing()) return false;
        String boardName = castString(postItem != null ? EnhanceReflection.invokeNoArgs(postItem, "getBoardName") : null);
        Integer postNumber =
                postItem != null ? parsePostNumber(EnhanceReflection.invokeNoArgs(postItem, "getPostNumber")) : null;
        if (postNumber == null) {
            postNumber = parsePostNumber(EnhanceReflection.readField(postContextMenu, "first"));
        }
        Activity activity = knownActivity != null ? knownActivity : resolveActivity(dialog, configurationSet);
        if ((boardName == null || boardName.isEmpty()) && activity != null) {
            boardName = EnhanceReflection.resolveActiveBoardName(activity);
        }
        if (postNumber == null || postNumber <= 0 || boardName == null || boardName.isEmpty()) return false;
        String postKey = buildPostKey(boardName, postNumber);
        List<EnhanceWidget> widgets = contextMenuWidgetsByPostKey.get(postKey);
        if (widgets == null || widgets.isEmpty()) return false;
        if (activity == null || activity.isFinishing() || EnhanceReflection.isActivityDestroyed(activity)) return false;
        return attachContextMenuWidgetsView(activity, dialog, widgets);
    }

    private static void scheduleContextMenuAttachRetry(
            View decorView, Object configurationSet, Object postItem, int retriesLeft) {
        if (retriesLeft <= 0 || decorView == null) return;
        decorView.postDelayed(
                () -> {
                    if (!maybeAttachContextMenuWidgetsFromConfigurationSet(configurationSet, postItem, null)) {
                        scheduleContextMenuAttachRetry(decorView, configurationSet, postItem, retriesLeft - 1);
                    }
                },
                16L);
    }

    private static void scheduleAttachToKnownDialog(
            View decorView,
            Activity activity,
            AlertDialog dialog,
            List<EnhanceWidget> widgets,
            int retriesLeft) {
        if (retriesLeft <= 0 || decorView == null) return;
        decorView.postDelayed(
                () -> {
                    if (!attachContextMenuWidgetsView(activity, dialog, widgets)) {
                        scheduleAttachToKnownDialog(decorView, activity, dialog, widgets, retriesLeft - 1);
                    }
                },
                16L);
    }

    private static boolean attachContextMenuWidgetsView(Activity activity, AlertDialog dialog, List<EnhanceWidget> widgets) {
        ViewGroup custom = findContextMenuContainer(dialog);
        if (custom == null) {
            return false;
        }
        LinearLayout wrapper = ensureContextMenuWrapper(dialog, custom);
        if (wrapper == null) {
            return false;
        }
        LinearLayout extensionRoot = ensureContextMenuExtensionRoot(activity, wrapper);
        if (extensionRoot == null) {
            return false;
        }
        extensionRoot.removeAllViews();
        extensionRoot.setTag(
                TAG_CONTEXT_MENU_CLOSE_ACTION,
                (Runnable) () -> {
                    if (dialog.isShowing()) {
                        dialog.dismiss();
                    }
                });
        for (int i = 0; i < widgets.size(); i++) {
            EnhanceWidget widget = widgets.get(i);
            widget.inject(activity, extensionRoot);
            View container = extensionRoot.findViewWithTag(widget.getContainerTag());
            if (container != null && container.getParent() == extensionRoot) {
                container.setLayoutParams(widget.createLayoutParams(activity));
            }
        }
        return extensionRoot.getChildCount() > 0;
    }

    private static ViewGroup findContextMenuContainer(AlertDialog dialog) {
        View custom = dialog.findViewById(android.R.id.custom);
        if (custom instanceof ViewGroup) return (ViewGroup) custom;
        View content = dialog.findViewById(android.R.id.content);
        return content instanceof ViewGroup ? (ViewGroup) content : null;
    }

    private static LinearLayout ensureContextMenuWrapper(AlertDialog dialog, ViewGroup customContainer) {
        if (customContainer.getChildCount() == 1
                && customContainer.getChildAt(0) instanceof LinearLayout
                && CONTEXT_MENU_WRAPPER_TAG.equals(customContainer.getChildAt(0).getTag())) {
            return (LinearLayout) customContainer.getChildAt(0);
        }
        if (customContainer.getChildCount() <= 0) return null;
        ArrayList<View> children = new ArrayList<>(customContainer.getChildCount());
        while (customContainer.getChildCount() > 0) {
            View child = customContainer.getChildAt(0);
            customContainer.removeViewAt(0);
            children.add(child);
        }
        LinearLayout wrapper = new LinearLayout(dialog.getContext());
        wrapper.setTag(CONTEXT_MENU_WRAPPER_TAG);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < children.size(); i++) {
            wrapper.addView(
                    children.get(i),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        customContainer.addView(wrapper);
        return wrapper;
    }

    private static LinearLayout ensureContextMenuExtensionRoot(Activity activity, LinearLayout wrapper) {
        View existing = wrapper.findViewWithTag(CONTEXT_MENU_EXTENSION_ROOT_TAG);
        if (existing instanceof LinearLayout) {
            return (LinearLayout) existing;
        }
        LinearLayout extensionRoot = new LinearLayout(activity);
        extensionRoot.setTag(CONTEXT_MENU_EXTENSION_ROOT_TAG);
        extensionRoot.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams layoutParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int horizontal = dp(activity, 16);
        layoutParams.leftMargin = horizontal;
        layoutParams.topMargin = dp(activity, 8);
        layoutParams.rightMargin = horizontal;
        layoutParams.bottomMargin = dp(activity, 10);
        wrapper.addView(extensionRoot, layoutParams);
        return extensionRoot;
    }

    private static Activity resolveActivity(AlertDialog dialog, Object configurationSet) {
        Activity ownerActivity = dialog.getOwnerActivity();
        if (ownerActivity != null) {
            return ownerActivity;
        }
        return resolveActivityFromConfigurationSet(configurationSet);
    }

    private static Activity resolveActivityFromConfigurationSet(Object configurationSet) {
        Object fragmentManager = EnhanceReflection.readField(configurationSet, "fragmentManager");
        Object fragments = EnhanceReflection.invokeNoArgs(fragmentManager, "getFragments");
        if (fragments instanceof List) {
            List<?> list = (List<?>) fragments;
            for (int i = 0; i < list.size(); i++) {
                Object fragment = list.get(i);
                Object fragmentActivity = EnhanceReflection.invokeNoArgs(fragment, "getActivity");
                if (fragmentActivity instanceof Activity) {
                    return (Activity) fragmentActivity;
                }
            }
        }
        return null;
    }

    private static String resolveBoardNameForContextMenu(
            Object configurationSet, Object postNumberObject, Activity activity) {
        Object postsProvider = EnhanceReflection.readField(configurationSet, "postsProvider");
        if (postsProvider != null && postNumberObject != null) {
            Object postItem =
                    EnhanceReflection.invoke(postsProvider, "findPostItem", new Class<?>[] {postNumberObject.getClass()}, postNumberObject);
            String boardName = castString(EnhanceReflection.invokeNoArgs(postItem, "getBoardName"));
            boardName = nullIfEmpty(boardName);
            if (boardName != null) return boardName;
        }
        return nullIfEmpty(EnhanceReflection.resolveActiveBoardName(activity));
    }

    private static void ensureInteractionHookInstalled(Activity activity, Object adapter) {
        Object uiManager = EnhanceReflection.readField(adapter, "uiManager");
        if (uiManager == null) return;
        Object interaction = EnhanceReflection.invokeNoArgs(uiManager, "interaction");
        if (interaction == null) return;
        if (wrappedInteractionsByUiManager.get(uiManager) == interaction) return;
        if (isAlreadyWrappedAdapter(interaction)) {
            wrappedInteractionsByUiManager.put(uiManager, interaction);
            return;
        }
        try {
            Object wrappedInteraction = wrapInteractionUnit(activity, interaction, uiManager);
            if (!writeField(uiManager, "interactionUnit", wrappedInteraction)) return;
            wrappedInteractionsByUiManager.put(uiManager, wrappedInteraction);
        } catch (Throwable ignored) {
            Log.w(TAG, "ensureInteractionHookInstalled: failed", ignored);
        }
    }

    private static void ensureDialogHookInstalled(Activity activity, Object adapter) {
        Object uiManager = EnhanceReflection.readField(adapter, "uiManager");
        if (uiManager == null) return;
        Object dialogUnit = EnhanceReflection.invokeNoArgs(uiManager, "dialog");
        if (dialogUnit == null) return;
        if (wrappedDialogsByUiManager.get(uiManager) == dialogUnit) return;
        if (isAlreadyWrappedAdapter(dialogUnit)) {
            wrappedDialogsByUiManager.put(uiManager, dialogUnit);
            return;
        }
        try {
            Object wrappedDialogUnit = wrapDialogUnit(activity, dialogUnit, uiManager);
            if (!writeField(uiManager, "dialogUnit", wrappedDialogUnit)) return;
            wrappedDialogsByUiManager.put(uiManager, wrappedDialogUnit);
        } catch (Throwable ignored) {
            Log.w(TAG, "ensureDialogHookInstalled: failed", ignored);
        }
    }

    private static Object wrapInteractionUnit(Activity activity, Object interaction, Object uiManager) {
        prepareProxyCache(activity);
        Class<?> proxyBuilderClass = resolveProxyBuilderClass();
        try {
            Object builder = proxyBuilderClass.getMethod("forClass", Class.class).invoke(null, interaction.getClass());
            Method dexCache = builder.getClass().getMethod("dexCache", File.class);
            builder = dexCache.invoke(builder, activity.getDir(PROXY_CACHE_DIR, Context.MODE_PRIVATE));
            Method callSuper = proxyBuilderClass.getMethod("callSuper", Object.class, Method.class, Object[].class);
            builder = builder.getClass()
                    .getMethod("handler", InvocationHandler.class)
                    .invoke(builder, createInteractionInvocationHandler(callSuper));
            Constructor<?> constructor = resolveInteractionConstructor(interaction.getClass(), uiManager);
            builder = builder.getClass()
                    .getMethod("constructorArgTypes", Class[].class)
                    .invoke(builder, new Object[] {constructor.getParameterTypes()});
            builder = builder.getClass()
                    .getMethod("constructorArgValues", Object[].class)
                    .invoke(builder, new Object[] {new Object[] {uiManager}});
            Object wrappedInteraction = builder.getClass().getMethod("build").invoke(builder);
            if (wrappedInteraction == null || wrappedInteraction == interaction) {
                throw new IllegalStateException("InteractionUnit wrapping returned original instance");
            }
            return wrappedInteraction;
        } catch (Throwable t) {
            throw new IllegalStateException("Unable to wrap InteractionUnit", t);
        }
    }

    private static Object wrapDialogUnit(Activity activity, Object dialogUnit, Object uiManager) {
        prepareProxyCache(activity);
        Class<?> proxyBuilderClass = resolveProxyBuilderClass();
        try {
            Object builder = proxyBuilderClass.getMethod("forClass", Class.class).invoke(null, dialogUnit.getClass());
            Method dexCache = builder.getClass().getMethod("dexCache", File.class);
            builder = dexCache.invoke(builder, activity.getDir(PROXY_CACHE_DIR, Context.MODE_PRIVATE));
            Method callSuper = proxyBuilderClass.getMethod("callSuper", Object.class, Method.class, Object[].class);
            builder = builder.getClass()
                    .getMethod("handler", InvocationHandler.class)
                    .invoke(builder, createDialogInvocationHandler(callSuper));
            Constructor<?> constructor = resolveDialogConstructor(dialogUnit.getClass(), uiManager);
            builder = builder.getClass()
                    .getMethod("constructorArgTypes", Class[].class)
                    .invoke(builder, new Object[] {constructor.getParameterTypes()});
            builder = builder.getClass()
                    .getMethod("constructorArgValues", Object[].class)
                    .invoke(builder, new Object[] {new Object[] {uiManager}});
            Object wrappedDialogUnit = builder.getClass().getMethod("build").invoke(builder);
            if (wrappedDialogUnit == null || wrappedDialogUnit == dialogUnit) {
                throw new IllegalStateException("DialogUnit wrapping returned original instance");
            }
            return wrappedDialogUnit;
        } catch (Throwable t) {
            throw new IllegalStateException("Unable to wrap DialogUnit", t);
        }
    }

    private static Constructor<?> resolveInteractionConstructor(Class<?> interactionClass, Object uiManager)
            throws NoSuchMethodException {
        Constructor<?>[] constructors = interactionClass.getDeclaredConstructors();
        for (int i = 0; i < constructors.length; i++) {
            Constructor<?> constructor = constructors[i];
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length == 1 && parameterTypes[0].isInstance(uiManager)) {
                constructor.setAccessible(true);
                return constructor;
            }
        }
        throw new NoSuchMethodException("No matching InteractionUnit constructor");
    }

    private static Constructor<?> resolveDialogConstructor(Class<?> dialogClass, Object uiManager)
            throws NoSuchMethodException {
        Constructor<?>[] constructors = dialogClass.getDeclaredConstructors();
        for (int i = 0; i < constructors.length; i++) {
            Constructor<?> constructor = constructors[i];
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length == 1 && parameterTypes[0].isInstance(uiManager)) {
                constructor.setAccessible(true);
                return constructor;
            }
        }
        throw new NoSuchMethodException("No matching DialogUnit constructor");
    }

    private static InvocationHandler createInteractionInvocationHandler(Method callSuper) {
        return (proxy, method, args) -> {
            Object result = callSuper.invoke(null, proxy, method, args);
            if ("handlePostContextMenu".equals(method.getName()) && args != null && args.length >= 2) {
                Object configurationSet = args[0];
                Object postItem = args[1];
                contextHandlePostLongClick(configurationSet, postItem);
            }
            return result;
        };
    }

    private static InvocationHandler createDialogInvocationHandler(Method callSuper) {
        return (proxy, method, args) -> {
            Object result = callSuper.invoke(null, proxy, method, args);
            if ("handlePostContextMenu".equals(method.getName()) && args != null && args.length >= 4) {
                boolean show = args[2] instanceof Boolean && (Boolean) args[2];
                if (show && args[3] instanceof AlertDialog) {
                    Object configurationSet = args[0];
                    Object postNumber = args[1];
                    AlertDialog dialog = (AlertDialog) args[3];
                    Log.d(TAG, "dialogHook:handlePostContextMenu show=true post=" + postNumber);
                    contextHandlePostContextMenuDialog(configurationSet, postNumber, dialog, null);
                }
            }
            return result;
        };
    }

    private static Object wrapAndInstall(Activity activity, ViewGroup collectionView, Object adapter) {
        Object wrappedAdapter = wrapAdapter(activity, collectionView, adapter);
        try {
            Class<?> recyclerAdapterClass = Class.forName(
                    "androidx.recyclerview.widget.RecyclerView$Adapter",
                    false,
                    collectionView.getClass().getClassLoader());
            Method setAdapter = collectionView.getClass().getMethod("setAdapter", recyclerAdapterClass);
            setAdapter.setAccessible(true);
            setAdapter.invoke(collectionView, wrappedAdapter);
        } catch (Throwable t) {
            throw new IllegalStateException("Unable to set wrapped PostsAdapter", t);
        }
        reinstallPostDecorations(activity, collectionView, wrappedAdapter);
        return wrappedAdapter;
    }

    private static Object wrapAdapter(Activity activity, ViewGroup collectionView, Object adapter) {
        try {
            prepareProxyCache(activity);
            Class<?> proxyBuilderClass = resolveProxyBuilderClass();
            ConstructorSpec constructorSpec =
                    resolveConstructorSpec(adapter.getClass(), readValues(collectionView, adapter));
            Object builder =
                    proxyBuilderClass.getMethod("forClass", Class.class).invoke(null, adapter.getClass());
            Method dexCache = builder.getClass().getMethod("dexCache", File.class);
            builder = dexCache.invoke(builder, activity.getDir(PROXY_CACHE_DIR, Context.MODE_PRIVATE));
            Method callSuper = proxyBuilderClass.getMethod("callSuper", Object.class, Method.class, Object[].class);
            builder = builder.getClass()
                    .getMethod("handler", InvocationHandler.class)
                    .invoke(builder, createInvocationHandler(callSuper, activity, collectionView));
            builder = builder.getClass()
                    .getMethod("constructorArgTypes", Class[].class)
                    .invoke(builder, new Object[] {constructorSpec.parameterTypes});
            builder = builder.getClass()
                    .getMethod("constructorArgValues", Object[].class)
                    .invoke(builder, new Object[] {constructorSpec.parameterValues});
            Object wrappedAdapter = builder.getClass().getMethod("build").invoke(builder);
            if (wrappedAdapter == null || wrappedAdapter == adapter)
                throw new IllegalStateException("PostsAdapter wrapping returned original adapter");
            return wrappedAdapter;
        } catch (Throwable t) {
            throw new IllegalStateException("Unable to wrap PostsAdapter", t);
        }
    }

    private static boolean isAlreadyWrappedAdapter(Object adapter) {
        Class<?> adapterClass = adapter.getClass();
        String className = adapterClass.getName();
        if (className.contains("_Proxy")) {
            return true;
        }
        return EnhanceReflection.readField(adapter, "$__handler") != null;
    }

    private static InvocationHandler createInvocationHandler(
            Method callSuper, Activity activity, ViewGroup collectionView) {
        return (proxy, method, args) -> {
            Object result = callSuper.invoke(null, proxy, method, args);
            String name = method.getName();
            if (!"onBindViewHolder".equals(name) && !"onViewRecycled".equals(name)) return result;
            if (args == null || args.length == 0) return result;
            Object itemView = EnhanceReflection.readField(args[0], "itemView");
            if (!(itemView instanceof ViewGroup)) return result;
            ViewGroup postRoot = (ViewGroup) itemView;
            if ("onBindViewHolder".equals(name)) {
                int position = args.length > 1 && args[1] instanceof Integer ? (Integer) args[1] : -1;
                inlineHandleOnBind(activity, collectionView, postRoot, position);
            } else {
                inlineHandleOnRecycle(postRoot);
            }
            return result;
        };
    }

    private static ConstructorSpec resolveConstructorSpec(Class<?> adapterClass, Map<String, Object> values) {
        ClassLoader classLoader = adapterClass.getClassLoader();
        for (int p = 0; p < CONSTRUCTOR_PROFILES.length; p++) {
            String[] profile = CONSTRUCTOR_PROFILES[p];
            Class<?>[] types = new Class<?>[profile.length / 2];
            Object[] args = new Object[types.length];
            boolean hasNull = false;
            try {
                for (int i = 0; i < types.length; i++) {
                    types[i] = Class.forName(profile[i * 2], false, classLoader);
                    args[i] = values.get(profile[i * 2 + 1]);
                    if (args[i] == null) {
                        hasNull = true;
                        break;
                    }
                }
                if (hasNull) continue;
                Constructor<?> constructor = adapterClass.getDeclaredConstructor(types);
                return new ConstructorSpec(constructor.getParameterTypes(), args);
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                // try next profile
            } catch (Throwable t) {
                throw new IllegalStateException(
                        "Unable to resolve constructor profile for " + adapterClass.getName(), t);
            }
        }
        throw new IllegalStateException("No compatible constructor found for " + adapterClass.getName());
    }

    private static Map<String, Object> readValues(ViewGroup collectionView, Object adapter) {
        HashMap<String, Object> values = new HashMap<>();
        values.put("collectionView", collectionView);
        Object configurationSet = EnhanceReflection.readField(adapter, "configurationSet");
        values.put(
                "callback",
                wrapPostClickCallback(
                        collectionView.getClass().getClassLoader(),
                        EnhanceReflection.readField(configurationSet, "clickCallback"),
                        collectionView));
        Object chanName = EnhanceReflection.readField(configurationSet, "chanName");
        values.put("chanName", chanName instanceof String ? chanName : null);
        values.put("uiManager", EnhanceReflection.readField(adapter, "uiManager"));
        values.put("replyable", EnhanceReflection.readField(configurationSet, "replyable"));
        values.put("postStateProvider", EnhanceReflection.readField(configurationSet, "postStateProvider"));
        values.put("fragmentManager", EnhanceReflection.readField(configurationSet, "fragmentManager"));
        values.put("postItemsMap", EnhanceReflection.readField(adapter, "postItemsMap"));
        values.put("hiddenPosts", EnhanceReflection.readField(adapter, "hiddenPosts"));
        return values;
    }

    private static Object wrapPostClickCallback(ClassLoader classLoader, Object callback, ViewGroup collectionView) {
        if (callback == null || Proxy.isProxyClass(callback.getClass())) return callback;
        try {
            Class<?> callbackClass = Class.forName(
                    "com.mishiranu.dashchan.ui.navigator.adapter.PostsAdapter$Callback", false, classLoader);
            if (!callbackClass.isInstance(callback)) return callback;
            return Proxy.newProxyInstance(
                    classLoader,
                    new Class<?>[] {callbackClass},
                    (proxy, method, args) -> {
                        Object result = method.invoke(callback, args);
                        String methodName = method.getName();
                        boolean handled = result instanceof Boolean && (Boolean) result;
                        boolean isDirectLongClick = "onItemLongClick".equals(methodName);
                        boolean isLongClickViaOnItemClick = "onItemClick".equals(methodName)
                                && args != null
                                && args.length >= 4
                                && args[3] instanceof Boolean
                                && (Boolean) args[3];
                        if (handled && (isDirectLongClick || isLongClickViaOnItemClick)) {
                            Object postItem;
                            if (isDirectLongClick) {
                                postItem = args != null && args.length > 0 ? args[0] : null;
                            } else {
                                postItem = args[2];
                            }
                            Object configurationSet = resolveConfigurationSetFromAdapter(collectionView);
                            contextHandlePostLongClick(configurationSet, postItem);
                        }
                        return result;
                    });
        } catch (Throwable t) {
            return callback;
        }
    }

    private static Object resolveConfigurationSetFromAdapter(ViewGroup collectionView) {
        Object adapter = EnhanceReflection.invokeNoArgs(collectionView, "getAdapter");
        return resolveConfigurationSetFromAdapter(adapter);
    }

    private static Object resolveConfigurationSetFromAdapter(Object adapter) {
        if (adapter == null) return null;
        Object configurationSet = EnhanceReflection.readField(adapter, "configurationSet");
        if (configurationSet != null) return configurationSet;
        return EnhanceReflection.invokeNoArgs(adapter, "getConfigurationSet");
    }

    private static boolean writeField(Object target, String fieldName, Object value) {
        if (target == null || fieldName == null) return false;
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return true;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }

    private static void reinstallPostDecorations(Activity activity, ViewGroup collectionView, Object adapter) {
        ClassLoader classLoader = collectionView.getClass().getClassLoader();
        try {
            Class<?> itemDecorationClass =
                    Class.forName("androidx.recyclerview.widget.RecyclerView$ItemDecoration", false, classLoader);
            Integer count = (Integer) EnhanceReflection.invokeNoArgs(collectionView, "getItemDecorationCount");
            if (count == null) throw new IllegalStateException("Unable to resolve RecyclerView item decoration count");
            for (int index = count - 1; index >= 0; index--) {
                Object decoration = EnhanceReflection.invoke(
                        collectionView, "getItemDecorationAt", new Class<?>[] {int.class}, index);
                if (decoration != null)
                    EnhanceReflection.invoke(
                            collectionView, "removeItemDecoration", new Class<?>[] {itemDecorationClass}, decoration);
            }
            int dividerPadding = (int) (12f * activity.getResources().getDisplayMetrics().density);
            Object dividerDecoration = createDividerDecoration(activity, adapter, dividerPadding, classLoader);
            EnhanceReflection.invoke(
                    collectionView, "addItemDecoration", new Class<?>[] {itemDecorationClass}, dividerDecoration);
            Object postDecoration = EnhanceReflection.invoke(
                    adapter,
                    "createPostItemDecoration",
                    new Class<?>[] {Context.class, int.class},
                    activity,
                    dividerPadding);
            if (postDecoration == null) throw new IllegalStateException("Unable to create post item decoration");
            EnhanceReflection.invoke(
                    collectionView, "addItemDecoration", new Class<?>[] {itemDecorationClass}, postDecoration);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("RecyclerView item decoration class is unavailable", e);
        }
    }

    private static Object createDividerDecoration(
            Activity activity, Object adapter, int dividerPadding, ClassLoader classLoader) {
        try {
            Class<?> dividerClass =
                    Class.forName("com.mishiranu.dashchan.widget.DividerItemDecoration", false, classLoader);
            Class<?> callbackClass =
                    Class.forName("com.mishiranu.dashchan.widget.DividerItemDecoration$Callback", false, classLoader);
            Class<?> configurationClass = Class.forName(
                    "com.mishiranu.dashchan.widget.DividerItemDecoration$Configuration", false, classLoader);
            Object callback =
                    Proxy.newProxyInstance(classLoader, new Class<?>[] {callbackClass}, (proxy, method, args) -> {
                        if (!"configure".equals(method.getName()) || args == null || args.length < 2) return null;
                        Object configuration = args[0];
                        int position = args[1] instanceof Integer ? (Integer) args[1] : -1;
                        if (position < 0) return configuration;
                        Object configured = EnhanceReflection.invoke(
                                adapter,
                                "configureDivider",
                                new Class<?>[] {configurationClass, int.class},
                                configuration,
                                position);
                        if (configured == null)
                            throw new IllegalStateException("PostsAdapter.configureDivider returned null");
                        EnhanceReflection.invoke(
                                configured,
                                "horizontal",
                                new Class<?>[] {int.class, int.class},
                                dividerPadding,
                                dividerPadding);
                        return configured;
                    });
            return dividerClass.getConstructor(Context.class, callbackClass).newInstance(activity, callback);
        } catch (Throwable t) {
            throw new IllegalStateException("Unable to create divider item decoration", t);
        }
    }

    @SuppressWarnings("unchecked")
    public static boolean requestThreadPostRebind(Activity activity, String boardName, int postNumber) {
        Log.d(TAG, "requestThreadPostRebind:start board=" + boardName + " post=" + postNumber);
        if (activity == null || boardName == null || postNumber <= 0) {
            Log.w(TAG, "requestThreadPostRebind:fail invalid-args");
            return false;
        }
        ViewGroup postsCollection = EnhanceReflection.resolvePostsCollectionView(activity);
        if (postsCollection == null) {
            Log.w(TAG, "requestThreadPostRebind:fail posts-collection-null");
            return false;
        }
        Object adapter = EnhanceReflection.invokeNoArgs(postsCollection, "getAdapter");
        if (adapter == null) {
            Log.w(TAG, "requestThreadPostRebind:fail adapter-null");
            return false;
        }
        Object postItemsMapObject = EnhanceReflection.readField(adapter, "postItemsMap");
        if (!(postItemsMapObject instanceof Map)) {
            Log.w(
                    TAG,
                    "requestThreadPostRebind:fail postItemsMap-unavailable class=" + describeClass(postItemsMapObject));
            return false;
        }
        Map<Object, Object> postItemsMap = (Map<Object, Object>) postItemsMapObject;
        Object mapKey = null;
        for (Map.Entry<Object, Object> entry : postItemsMap.entrySet()) {
            if (!isTargetPostNumber(entry.getKey(), postNumber)) {
                continue;
            }
            Object value = entry.getValue();
            String itemBoardName = castString(EnhanceReflection.invokeNoArgs(value, "getBoardName"));
            if (itemBoardName != null && !boardName.equals(itemBoardName)) {
                continue;
            }
            mapKey = entry.getKey();
            break;
        }
        if (mapKey == null) {
            Log.w(TAG, "requestThreadPostRebind:fail post-not-found-in-map size=" + postItemsMap.size());
            return false;
        }
        Integer position = findPostPosition(adapter, mapKey, boardName, postNumber);
        if (position == null || position < 0) {
            Log.w(TAG, "requestThreadPostRebind:fail adapter-position-not-found");
            return false;
        }
        Object emptyPayload = resolveSimpleViewHolderEmptyPayload(adapter.getClass().getClassLoader());
        if (emptyPayload == null) {
            Log.w(TAG, "requestThreadPostRebind:fail empty-payload-null");
            return false;
        }
        EnhanceReflection.invoke(adapter, "notifyItemChanged", new Class<?>[] {int.class, Object.class}, position, emptyPayload);
        Log.d(TAG, "requestThreadPostRebind:ok position=" + position + " adapter=" + describeClass(adapter));
        return true;
    }

    @SuppressWarnings("unchecked")
    public static boolean replaceThreadPost(
            Activity activity, E444ChanLocator locator, String boardName, int postNumber, Object post) {
        Log.d(TAG, "replaceThreadPost:start board=" + boardName + " post=" + postNumber);
        if (activity == null || locator == null || post == null || boardName == null || postNumber <= 0) {
            Log.w(TAG, "replaceThreadPost:fail invalid-args");
            return false;
        }
        ViewGroup postsCollection = EnhanceReflection.resolvePostsCollectionView(activity);
        if (postsCollection == null) {
            Log.w(TAG, "replaceThreadPost:fail posts-collection-null");
            return false;
        }
        Object adapter = EnhanceReflection.invokeNoArgs(postsCollection, "getAdapter");
        if (adapter == null) {
            Log.w(TAG, "replaceThreadPost:fail adapter-null");
            return false;
        }
        Object postItemsMapObject = EnhanceReflection.readField(adapter, "postItemsMap");
        if (!(postItemsMapObject instanceof Map)) {
            Log.w(TAG, "replaceThreadPost:fail postItemsMap-unavailable class=" + describeClass(postItemsMapObject));
            return false;
        }
        Map<Object, Object> postItemsMap = (Map<Object, Object>) postItemsMapObject;
        Object mapKey = null;
        Object oldPostItem = null;
        for (Map.Entry<Object, Object> entry : postItemsMap.entrySet()) {
            if (!isTargetPostNumber(entry.getKey(), postNumber)) {
                continue;
            }
            Object value = entry.getValue();
            String itemBoardName = castString(EnhanceReflection.invokeNoArgs(value, "getBoardName"));
            if (itemBoardName != null && !boardName.equals(itemBoardName)) {
                continue;
            }
            mapKey = entry.getKey();
            oldPostItem = value;
            break;
        }
        if (mapKey == null || oldPostItem == null) {
            Log.w(TAG, "replaceThreadPost:fail post-not-found-in-map size=" + postItemsMap.size());
            return false;
        }
        Integer position = findPostPosition(adapter, mapKey, boardName, postNumber);
        if (position == null || position < 0) {
            Log.w(TAG, "replaceThreadPost:fail adapter-position-not-found");
            return false;
        }
        ensurePostNumber(post, postNumber);
        Object dashchanPost = toDashchanPost(post);
        if (dashchanPost == null) {
            Log.w(TAG, "replaceThreadPost:fail dashchan-post-conversion");
            return false;
        }
        Object chan = EnhanceReflection.invokeNoArgs(locator, "get");
        if (chan == null) {
            Log.w(TAG, "replaceThreadPost:fail chan-null");
            return false;
        }
        String threadNumber =
                nullIfEmpty(castString(EnhanceReflection.invokeNoArgs(post, "getThreadNumberOrOriginalPostNumber")));
        if (threadNumber == null) {
            threadNumber = EnhanceReflection.resolveActiveThreadNumber(activity);
        }
        if (threadNumber == null) {
            threadNumber = castString(EnhanceReflection.invokeNoArgs(oldPostItem, "getThreadNumber"));
        }
        if (threadNumber == null) {
            Log.w(TAG, "replaceThreadPost:fail thread-number-null");
            return false;
        }
        Object originalPostNumber = parseOriginalPostNumber(
                mapKey.getClass(), castString(EnhanceReflection.invokeNoArgs(post, "getOriginalPostNumber")));
        originalPostNumber = originalPostNumber != null
                ? originalPostNumber
                : EnhanceReflection.invokeNoArgs(oldPostItem, "getOriginalPostNumber");
        Object newPostItem =
                createPostItem(oldPostItem.getClass(), dashchanPost, chan, boardName, threadNumber, originalPostNumber);
        if (newPostItem == null) {
            Log.w(TAG, "replaceThreadPost:fail create-post-item");
            return false;
        }
        postItemsMap.put(mapKey, newPostItem);
        Object emptyPayload = resolveSimpleViewHolderEmptyPayload(adapter.getClass().getClassLoader());
        if (emptyPayload == null) {
            Log.w(TAG, "replaceThreadPost:fail empty-payload-null");
            return false;
        }
        EnhanceReflection.invoke(adapter, "notifyItemChanged", new Class<?>[] {int.class, Object.class}, position, emptyPayload);
        Log.d(TAG, "replaceThreadPost:ok position=" + position + " adapter=" + describeClass(adapter));
        return true;
    }

    public static Integer resolvePostNumberForPostChild(ViewGroup collectionView, View postRoot) {
        if (collectionView == null || postRoot == null) {
            return null;
        }
        Integer position = castInt(
                EnhanceReflection.invoke(collectionView, "getChildAdapterPosition", new Class<?>[] {View.class}, postRoot));
        if (position == null || position < 0) {
            position = castInt(EnhanceReflection.invoke(collectionView, "getPositionForView", new Class<?>[] {View.class}, postRoot));
        }
        if (position == null || position < 0) {
            return null;
        }
        Object adapter = EnhanceReflection.invokeNoArgs(collectionView, "getAdapter");
        Object item = adapter != null ? EnhanceReflection.invoke(adapter, "getItem", new Class<?>[] {int.class}, position) : null;
        if (item == null && adapter != null) {
            Object items = EnhanceReflection.readField(adapter, "items");
            if (items instanceof List && position < ((List<?>) items).size()) {
                item = ((List<?>) items).get(position);
            }
        }
        return resolvePostNumberFromSource(item);
    }

    private static Integer resolvePostNumberFromSource(Object source) {
        if (source == null) {
            return null;
        }
        Integer postNumber = parsePostNumber(source);
        if (postNumber != null) return postNumber;
        postNumber = parsePostNumber(EnhanceReflection.invokeNoArgs(source, "getPostNumber"));
        if (postNumber != null) return postNumber;
        postNumber = parsePostNumber(EnhanceReflection.invokeNoArgs(source, "getPostNum"));
        if (postNumber != null) return postNumber;
        postNumber = parsePostNumber(EnhanceReflection.invokeNoArgs(source, "getNumber"));
        if (postNumber != null) return postNumber;
        postNumber = parsePostNumber(EnhanceReflection.invokeNoArgs(source, "getNum"));
        if (postNumber != null) return postNumber;
        Object post = EnhanceReflection.invokeNoArgs(source, "getPost");
        postNumber = parsePostNumber(EnhanceReflection.invokeNoArgs(post, "getPostNumber"));
        if (postNumber != null) return postNumber;
        postNumber = parsePostNumber(EnhanceReflection.readField(source, "postNumber"));
        if (postNumber != null) return postNumber;
        postNumber = parsePostNumber(EnhanceReflection.readField(source, "num"));
        if (postNumber != null) return postNumber;
        postNumber = parsePostNumber(EnhanceReflection.readField(source, "number"));
        if (postNumber != null) return postNumber;
        post = EnhanceReflection.readField(source, "post");
        postNumber = parsePostNumber(EnhanceReflection.invokeNoArgs(post, "getPostNumber"));
        if (postNumber != null) return postNumber;
        Object postItem = EnhanceReflection.readField(source, "postItem");
        postNumber = parsePostNumber(EnhanceReflection.invokeNoArgs(postItem, "getPostNumber"));
        if (postNumber != null) return postNumber;
        return parsePostNumber(String.valueOf(source));
    }

    private static Integer parsePostNumber(Object value) {
        if (value == null) return null;
        if (value instanceof Number) {
            int postNumber = ((Number) value).intValue();
            return postNumber > 0 ? postNumber : null;
        }
        String source = String.valueOf(value).trim();
        if (source.isEmpty()) return null;
        if (source.startsWith("#")) source = source.substring(1).trim();
        if (source.isEmpty()) return null;
        int dotIndex = source.indexOf('.');
        String intPart = dotIndex >= 0 ? source.substring(0, dotIndex) : source;
        if (intPart.isEmpty()) return null;
        for (int i = 0; i < intPart.length(); i++) {
            char c = intPart.charAt(i);
            if (!(c >= '0' && c <= '9')) return null;
        }
        try {
            int postNumber = Integer.parseInt(intPart);
            return postNumber > 0 ? postNumber : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isTargetPostNumber(Object postNumberObject, int postNumber) {
        Object major = EnhanceReflection.readField(postNumberObject, "major");
        if (major instanceof Number) {
            return ((Number) major).intValue() == postNumber;
        }
        Integer parsedPostNumber = parsePostNumber(postNumberObject);
        return parsedPostNumber != null && parsedPostNumber == postNumber;
    }

    private static Integer findPostPosition(Object adapter, Object mapKey, String boardName, int postNumber) {
        Integer byPostNumberMethod = castInt(
                EnhanceReflection.invoke(adapter, "positionOfPostNumber", new Class<?>[] {mapKey.getClass()}, mapKey));
        if (byPostNumberMethod != null && byPostNumberMethod >= 0) {
            return byPostNumberMethod;
        }
        Integer itemCount = castInt(EnhanceReflection.invokeNoArgs(adapter, "getItemCount"));
        if (itemCount == null || itemCount <= 0) {
            return null;
        }
        for (int position = 0; position < itemCount; position++) {
            Object postItem = EnhanceReflection.invoke(adapter, "getItem", new Class<?>[] {int.class}, position);
            if (postItem == null) continue;
            String itemBoardName = castString(EnhanceReflection.invokeNoArgs(postItem, "getBoardName"));
            if (itemBoardName != null && !boardName.equals(itemBoardName)) continue;
            Integer itemPostNumber = parsePostNumber(EnhanceReflection.invokeNoArgs(postItem, "getPostNumber"));
            if (itemPostNumber != null && itemPostNumber == postNumber) {
                return position;
            }
        }
        Log.w(TAG, "replaceThreadPost:find-position-failed itemCount=" + itemCount);
        return null;
    }

    private static Object toDashchanPost(Object post) {
        if (post == null) return null;
        if ("com.mishiranu.dashchan.content.model.Post".equals(post.getClass().getName())) {
            return post;
        }
        try {
            Class<?> singlePostClass = Class.forName("chan.content.model.SinglePost");
            Constructor<?>[] constructors = singlePostClass.getConstructors();
            Object singlePost = null;
            for (Constructor<?> constructor : constructors) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length == 1 && parameterTypes[0].isInstance(post)) {
                    constructor.setAccessible(true);
                    singlePost = constructor.newInstance(post);
                    break;
                }
            }
            if (singlePost == null) {
                Log.w(TAG, "replaceThreadPost:single-post-constructor-not-found postClass=" + describeClass(post));
                return null;
            }
            Object convertedPost = EnhanceReflection.readField(singlePost, "post");
            if (convertedPost != null
                    && "com.mishiranu.dashchan.content.model.Post".equals(convertedPost.getClass().getName())) {
                return convertedPost;
            }
            Log.w(TAG, "replaceThreadPost:single-post-field-returned " + describeClass(convertedPost));
        } catch (Throwable t) {
            Log.w(TAG, "replaceThreadPost:single-post-conversion-failed", t);
        }
        return null;
    }

    private static void ensurePostNumber(Object post, int fallbackPostNumber) {
        try {
            String postNumber = castString(EnhanceReflection.invokeNoArgs(post, "getPostNumber"));
            if (nullIfEmpty(postNumber) != null) return;
            EnhanceReflection.invoke(post, "setPostNumber", new Class<?>[] {String.class}, Integer.toString(fallbackPostNumber));
            String resolvedPostNumber = castString(EnhanceReflection.invokeNoArgs(post, "getPostNumber"));
            Log.d(TAG, "replaceThreadPost:ensure-post-number value=" + resolvedPostNumber);
        } catch (Throwable t) {
            Log.w(TAG, "replaceThreadPost:ensure-post-number-failed", t);
        }
    }

    private static Object parseOriginalPostNumber(Class<?> postNumberClass, String value) {
        value = nullIfEmpty(value);
        if (value == null || postNumberClass == null) return null;
        try {
            Method parseOrThrowMethod = postNumberClass.getMethod("parseOrThrow", String.class);
            parseOrThrowMethod.setAccessible(true);
            return parseOrThrowMethod.invoke(null, value);
        } catch (Throwable ignored) {
            // Try hardcoded class name.
        }
        try {
            Class<?> defaultPostNumberClass = Class.forName("com.mishiranu.dashchan.content.model.PostNumber");
            Method parseOrThrowMethod = defaultPostNumberClass.getMethod("parseOrThrow", String.class);
            parseOrThrowMethod.setAccessible(true);
            return parseOrThrowMethod.invoke(null, value);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object resolveSimpleViewHolderEmptyPayload(ClassLoader classLoader) {
        try {
            Class<?> simpleViewHolderClass =
                    Class.forName("com.mishiranu.dashchan.widget.SimpleViewHolder", false, classLoader);
            Field emptyPayloadField = simpleViewHolderClass.getField("EMPTY_PAYLOAD");
            emptyPayloadField.setAccessible(true);
            return emptyPayloadField.get(null);
        } catch (Throwable ignored) {
            Log.w(TAG, "replaceThreadPost:resolve-empty-payload-failed", ignored);
            return null;
        }
    }

    private static String describeClass(Object value) {
        return value != null ? value.getClass().getName() : "null";
    }

    private static Object createPostItem(
            Class<?> postItemClass,
            Object post,
            Object chan,
            String boardName,
            String threadNumber,
            Object originalPostNumber) {
        Method[] methods = postItemClass.getMethods();
        for (Method method : methods) {
            if (!"createPost".equals(method.getName())
                    || method.getParameterTypes().length != 5
                    || !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            try {
                return method.invoke(null, post, chan, boardName, threadNumber, originalPostNumber);
            } catch (IllegalArgumentException ignored) {
                // Try next matching createPost overload.
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static Integer castInt(Object value) {
        return value instanceof Integer ? (Integer) value : null;
    }

    private static String castString(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private static String nullIfEmpty(String value) {
        if (value == null) return null;
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static Class<?> resolveProxyBuilderClass() {
        try {
            return Class.forName("com.android.dx.stock.ProxyBuilder", false, HookPost.class.getClassLoader());
        } catch (Throwable t) {
            throw new IllegalStateException("DexMaker ProxyBuilder class is unavailable", t);
        }
    }

    private static String buildPostKey(String boardName, int postNumber) {
        return (boardName != null ? boardName : "") + "/" + postNumber;
    }

    private static final class ConstructorSpec {
        final Class<?>[] parameterTypes;
        final Object[] parameterValues;

        ConstructorSpec(Class<?>[] parameterTypes, Object[] parameterValues) {
            this.parameterTypes = parameterTypes;
            this.parameterValues = parameterValues;
        }
    }
}
