package com.mishiranu.dashchan.chan.e444.enhance.controllers;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceHook;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceReflection;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceWidget;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import lombok.EqualsAndHashCode;

public final class HookPost implements EnhanceHook {
    public static final int TAG_CONTEXT_MENU_CLOSE_ACTION = 0xE4443001;

    private static final int TAG_LONG_CLICK_WRAPPER = 0xE4443002;
    private static final int TAG_BOUND_POST_KEY = 0xE4443003;
    private static final int TAG_REPLIES_CLICK_WRAPPER = 0xE4443004;
    private static final int TAG_COMMENT_LINK_WRAPPER = 0xE4443005;
    private static final long CONTEXT_MENU_SCAN_DELAY_MS = 100L;
    private static final long CONTEXT_MENU_SCAN_TIMEOUT_MS = 2_000L;

    private static final String DIALOG_MENU_ADAPTER_CLASS = "com.mishiranu.dashchan.ui.DialogMenu$Adapter";
    private static final String CONTEXT_MENU_WRAPPER_TAG = "e444_context_menu_wrapper";
    private static final String CONTEXT_MENU_HOST_TAG = "e444_context_menu_host";
    private static final String RECYCLER_CHILD_ATTACH_LISTENER_CLASS =
            "androidx.recyclerview.widget.RecyclerView$OnChildAttachStateChangeListener";

    private static final HookPost INSTANCE = new HookPost();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Object APPLY_SYNC_LOCK = new Object();
    private static final Map<ViewGroup, Object> CHILD_ATTACH_LISTENERS = new WeakHashMap<>();

    private static final Object STORE_LOCK = new Object();
    private static final Map<PostKey, WidgetPayload> POST_WIDGETS = new HashMap<>();
    private static final Map<PostKey, WidgetPayload> CONTEXT_WIDGETS = new HashMap<>();
    private static final Set<String> KNOWN_POST_WIDGET_TAGS = new HashSet<>();
    private static final Set<String> KNOWN_CONTEXT_WIDGET_TAGS = new HashSet<>();
    private static final ThreadLocal<PostScope> CURRENT_POST_SCOPE = new ThreadLocal<>();

    private static volatile PostKey pendingContextMenuPostKey;
    private static volatile long pendingContextMenuTimestampMs;
    private static volatile boolean applySyncScheduled;

    private HookPost() {}

    public static HookPost getInstance() {
        return INSTANCE;
    }

    public static void enterPostScope(String chanName, String boardName, int postNumber) {
        Objects.requireNonNull(chanName, "chanName");
        Objects.requireNonNull(boardName, "boardName");
        CURRENT_POST_SCOPE.set(new PostScope(chanName, boardName, postNumber));
    }

    public static void exitPostScope() {
        CURRENT_POST_SCOPE.remove();
    }

    public static void setWidgetsForPost(List<EnhanceWidget> widgets) {
        Objects.requireNonNull(widgets, "widgets");
        PostKey postKey = requireScopedPostKey();
        WidgetPayload payload = new WidgetPayload(widgets);
        synchronized (STORE_LOCK) {
            POST_WIDGETS.put(postKey, payload);
            for (EnhanceWidget widget : payload.widgets) {
                KNOWN_POST_WIDGET_TAGS.add(widget.getContainerTag());
            }
        }
        scheduleApplySync();
    }

    public static void setContextMenuWidgetsForPost(List<EnhanceWidget> widgets) {
        Objects.requireNonNull(widgets, "widgets");
        PostKey postKey = requireScopedPostKey();
        WidgetPayload payload = new WidgetPayload(widgets);
        synchronized (STORE_LOCK) {
            CONTEXT_WIDGETS.put(postKey, payload);
            for (EnhanceWidget widget : payload.widgets) {
                KNOWN_CONTEXT_WIDGET_TAGS.add(widget.getContainerTag());
            }
        }
        scheduleApplySync();
    }

    @Override
    public void apply(Activity activity) {
        if (!EnhanceReflection.isThreadPageActive(activity)) {
            return;
        }
        int bottomBarId = activity.getResources().getIdentifier("bottom_bar", "id", activity.getPackageName());
        int repliesButtonId =
                activity.getResources().getIdentifier("bottom_bar_replies", "id", activity.getPackageName());
        int commentId = activity.getResources().getIdentifier("comment", "id", activity.getPackageName());
        int textBarPaddingId =
                activity.getResources().getIdentifier("text_bar_padding", "id", activity.getPackageName());
        Set<String> postWidgetTags = snapshotKnownWidgetTags(KNOWN_POST_WIDGET_TAGS);

        ViewGroup primaryCollectionView = EnhanceReflection.resolvePostsCollectionView(activity);
        Set<ViewGroup> postCollections = collectPostCollections(primaryCollectionView, bottomBarId, textBarPaddingId);
        if (postCollections.isEmpty()) {
            throw new IllegalStateException("Posts collection view is not available");
        }
        for (ViewGroup collectionView : postCollections) {
            ensureChildAttachListener(
                    activity,
                    collectionView,
                    bottomBarId,
                    repliesButtonId,
                    commentId,
                    textBarPaddingId);
            for (int i = 0; i < collectionView.getChildCount(); i++) {
                View child = collectionView.getChildAt(i);
                if (!(child instanceof ViewGroup)) {
                    continue;
                }
                injectPostWidgetsForVisibleChild(
                        activity,
                        collectionView,
                        (ViewGroup) child,
                        bottomBarId,
                        repliesButtonId,
                        commentId,
                        textBarPaddingId,
                        postWidgetTags);
            }
        }

        injectPendingContextMenuWidgets(activity);
    }

    @Override
    public void clear(Activity activity) {
        Set<String> postWidgetTags = snapshotKnownWidgetTags(KNOWN_POST_WIDGET_TAGS);
        if (postWidgetTags.isEmpty()) {
            return;
        }
        for (View root : getWindowRootViews()) {
            removeWidgetContainersRecursive(root, postWidgetTags, Collections.<String>emptySet());
        }
    }

    private static void injectPendingContextMenuWidgets(Activity activity) {
        PostKey postKey = pendingContextMenuPostKey;
        if (postKey == null) {
            return;
        }
        if (SystemClock.uptimeMillis() - pendingContextMenuTimestampMs > CONTEXT_MENU_SCAN_TIMEOUT_MS) {
            pendingContextMenuPostKey = null;
            return;
        }

        WidgetPayload payload = resolveWidgetPayload(postKey, CONTEXT_WIDGETS);
        if (payload == null) {
            pendingContextMenuPostKey = null;
            pendingContextMenuTimestampMs = 0L;
            return;
        }

        View dialogMenuRecyclerView = findDialogMenuRecyclerView();
        if (dialogMenuRecyclerView == null) {
            return;
        }

        ViewGroup contextMenuHost = ensureContextMenuHost(activity, dialogMenuRecyclerView);
        contextMenuHost.setTag(TAG_CONTEXT_MENU_CLOSE_ACTION, (Runnable) () -> {
            View rootView = dialogMenuRecyclerView.getRootView();
            rootView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
            rootView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
        });

        Set<String> contextWidgetTags = snapshotKnownWidgetTags(KNOWN_CONTEXT_WIDGET_TAGS);
        applyWidgetPayload(activity, contextMenuHost, payload, null, contextWidgetTags);
        pendingContextMenuPostKey = null;
        pendingContextMenuTimestampMs = 0L;
    }

    private static void injectPostWidgetsForVisibleChild(
            Activity activity,
            ViewGroup collectionView,
            ViewGroup postRoot,
            int bottomBarId,
            int repliesButtonId,
            int commentId,
            int textBarPaddingId,
            Set<String> postWidgetTags) {
        View bottomBar = postRoot.findViewById(bottomBarId);
        if (bottomBar == null) {
            removeWidgetContainers(postRoot, postWidgetTags, new HashSet<String>());
            return;
        }
        View textBarPadding = postRoot.findViewById(textBarPaddingId);
        if (textBarPadding == null) {
            throw new IllegalStateException("text_bar_padding was not found");
        }
        if (!(textBarPadding.getParent() instanceof ViewGroup)) {
            throw new IllegalStateException("text_bar_padding parent is not a ViewGroup");
        }
        ViewGroup postWidgetHost = (ViewGroup) textBarPadding.getParent();

        PostIdentity identity = resolvePostIdentity(collectionView, postRoot);
        identity.postRoot.setTag(TAG_BOUND_POST_KEY, identity.postKey);
        installLongClickWrapper(activity, identity.postRoot);
        installRepliesClickWrapper(identity.postRoot, repliesButtonId);
        installCommentLinkWrapper(identity.postRoot, commentId);
        WidgetPayload payload = resolveWidgetPayload(identity.postKey, POST_WIDGETS);
        if (postWidgetHost != identity.postRoot) {
            removeWidgetContainers(identity.postRoot, postWidgetTags, new HashSet<String>());
        }
        applyWidgetPayload(activity, postWidgetHost, payload, textBarPadding, postWidgetTags);
    }

    private static void installLongClickWrapper(Activity activity, ViewGroup postRoot) {
        if (postRoot.getTag(TAG_LONG_CLICK_WRAPPER) != null) {
            return;
        }
        Object listenerInfo = EnhanceReflection.readField(postRoot, "mListenerInfo");
        View.OnLongClickListener delegate =
                (View.OnLongClickListener) EnhanceReflection.readField(listenerInfo, "mOnLongClickListener");
        PostLongClickWrapper wrapper = new PostLongClickWrapper(activity, delegate);
        postRoot.setTag(TAG_LONG_CLICK_WRAPPER, wrapper);
        postRoot.setOnLongClickListener(wrapper);
    }

    private static void installRepliesClickWrapper(ViewGroup postRoot, int repliesButtonId) {
        View repliesButton = postRoot.findViewById(repliesButtonId);
        if (repliesButton == null || repliesButton.getTag(TAG_REPLIES_CLICK_WRAPPER) != null) {
            return;
        }
        Object listenerInfo = EnhanceReflection.readField(repliesButton, "mListenerInfo");
        View.OnClickListener delegate =
                (View.OnClickListener) EnhanceReflection.readField(listenerInfo, "mOnClickListener");
        PostClickWrapper wrapper = new PostClickWrapper(delegate);
        repliesButton.setTag(TAG_REPLIES_CLICK_WRAPPER, wrapper);
        repliesButton.setOnClickListener(wrapper);
    }

    private static void installCommentLinkWrapper(ViewGroup postRoot, int commentId) {
        View commentView = postRoot.findViewById(commentId);
        if (commentView == null || commentView.getTag(TAG_COMMENT_LINK_WRAPPER) != null) {
            return;
        }
        Object delegate = EnhanceReflection.readField(commentView, "linkListener");
        Object configuration = EnhanceReflection.readField(commentView, "linkConfiguration");
        if (delegate == null || configuration == null) {
            throw new IllegalStateException("Comment link listener is not initialized");
        }
        try {
            Class<?> linkListenerClass =
                    Class.forName("com.mishiranu.dashchan.widget.CommentTextView$LinkListener");
            Class<?> linkConfigurationClass =
                    Class.forName("com.mishiranu.dashchan.widget.CommentTextView$LinkConfiguration");
            InvocationHandler handler = (proxy, method, args) -> {
                Object result = method.invoke(delegate, args);
                if ("onLinkClick".equals(method.getName())) {
                    scheduleApplySync();
                    MAIN_HANDLER.postDelayed(HookPost::scheduleApplySync, 100L);
                }
                return result;
            };
            Object wrapper = Proxy.newProxyInstance(
                    linkListenerClass.getClassLoader(), new Class<?>[] {linkListenerClass}, handler);
            EnhanceReflection.invoke(
                    commentView,
                    "setLinkListener",
                    new Class<?>[] {linkListenerClass, linkConfigurationClass},
                    wrapper,
                    configuration);
            commentView.setTag(TAG_COMMENT_LINK_WRAPPER, wrapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void ensureChildAttachListener(
            Activity activity,
            ViewGroup collectionView,
            int bottomBarId,
            int repliesButtonId,
            int commentId,
            int textBarPaddingId) {
        synchronized (CHILD_ATTACH_LISTENERS) {
            if (CHILD_ATTACH_LISTENERS.containsKey(collectionView)) {
                return;
            }
        }

        try {
            Class<?> listenerClass = Class.forName(RECYCLER_CHILD_ATTACH_LISTENER_CLASS);
            InvocationHandler handler = (proxy, method, args) -> {
                String methodName = method.getName();
                if ("onChildViewAttachedToWindow".equals(methodName)
                        && args != null
                        && args.length == 1
                        && args[0] instanceof ViewGroup) {
                    ViewGroup child = (ViewGroup) args[0];
                    injectPostWidgetsForVisibleChild(
                            activity,
                            collectionView,
                            child,
                            bottomBarId,
                            repliesButtonId,
                            commentId,
                            textBarPaddingId,
                            snapshotKnownWidgetTags(KNOWN_POST_WIDGET_TAGS));
                }
                return null;
            };
            Object listener = Proxy.newProxyInstance(
                    listenerClass.getClassLoader(), new Class<?>[] {listenerClass}, handler);
            EnhanceReflection.invoke(
                    collectionView,
                    "addOnChildAttachStateChangeListener",
                    new Class<?>[] {listenerClass},
                    listener);
            synchronized (CHILD_ATTACH_LISTENERS) {
                CHILD_ATTACH_LISTENERS.put(collectionView, listener);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Set<ViewGroup> collectPostCollections(
            ViewGroup primaryCollectionView,
            int bottomBarId,
            int textBarPaddingId) {
        LinkedHashSet<ViewGroup> collections = new LinkedHashSet<>();
        if (primaryCollectionView != null) {
            collections.add(primaryCollectionView);
        }
        List<View> roots = getWindowRootViews();
        for (View root : roots) {
            collectPostCollectionsFromView(root, collections, bottomBarId, textBarPaddingId);
        }
        return collections;
    }

    private static void collectPostCollectionsFromView(
            View view,
            Set<ViewGroup> collections,
            int bottomBarId,
            int textBarPaddingId) {
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup viewGroup = (ViewGroup) view;
        if (isPostCollectionView(viewGroup, bottomBarId, textBarPaddingId)) {
            collections.add(viewGroup);
            return;
        }
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            collectPostCollectionsFromView(viewGroup.getChildAt(i), collections, bottomBarId, textBarPaddingId);
        }
    }

    private static boolean isPostCollectionView(ViewGroup candidate, int bottomBarId, int textBarPaddingId) {
        for (int i = 0; i < candidate.getChildCount(); i++) {
            View child = candidate.getChildAt(i);
            if (!(child instanceof ViewGroup)) {
                continue;
            }
            if (child.findViewById(bottomBarId) == null || child.findViewById(textBarPaddingId) == null) {
                continue;
            }
            Object holder = EnhanceReflection.invoke(
                    candidate, "getChildViewHolder", new Class<?>[] {View.class}, child);
            if (holder == null) {
                continue;
            }
            Object postItem = EnhanceReflection.invokeNoArgs(holder, "getPostItem");
            Object configurationSet = EnhanceReflection.invokeNoArgs(holder, "getConfigurationSet");
            if (postItem != null && configurationSet != null) {
                return true;
            }
        }
        return false;
    }

    private static ViewGroup ensureContextMenuHost(Activity activity, View dialogMenuRecyclerView) {
        ViewGroup parent = (ViewGroup) dialogMenuRecyclerView.getParent();
        if (CONTEXT_MENU_WRAPPER_TAG.equals(parent.getTag())) {
            ViewGroup contextMenuHost = (ViewGroup) parent.findViewWithTag(CONTEXT_MENU_HOST_TAG);
            if (contextMenuHost == null) {
                throw new IllegalStateException("Context menu host is missing");
            }
            enforceContextMenuHostOrder(parent, dialogMenuRecyclerView, contextMenuHost);
            return contextMenuHost;
        }

        int recyclerIndex = parent.indexOfChild(dialogMenuRecyclerView);
        ViewGroup.LayoutParams recyclerLayoutParams = dialogMenuRecyclerView.getLayoutParams();

        parent.removeViewAt(recyclerIndex);

        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setTag(CONTEXT_MENU_WRAPPER_TAG);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutParams(recyclerLayoutParams);

        LinearLayout contextMenuHost = new LinearLayout(activity);
        contextMenuHost.setTag(CONTEXT_MENU_HOST_TAG);
        contextMenuHost.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(
                dialogMenuRecyclerView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrapper.addView(
                contextMenuHost,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        parent.addView(wrapper, recyclerIndex);
        return contextMenuHost;
    }

    private static void enforceContextMenuHostOrder(
            ViewGroup wrapper,
            View dialogMenuRecyclerView,
            ViewGroup contextMenuHost) {
        if (dialogMenuRecyclerView.getParent() != wrapper) {
            throw new IllegalStateException("Context menu recycler is detached from wrapper");
        }
        if (contextMenuHost.getParent() != wrapper) {
            throw new IllegalStateException("Context menu host is detached from wrapper");
        }

        int recyclerIndex = wrapper.indexOfChild(dialogMenuRecyclerView);
        int hostIndex = wrapper.indexOfChild(contextMenuHost);
        if (recyclerIndex == -1 || hostIndex == -1) {
            throw new IllegalStateException("Context menu wrapper children are missing");
        }

        if (recyclerIndex > hostIndex) {
            ViewGroup.LayoutParams recyclerLayoutParams = dialogMenuRecyclerView.getLayoutParams();
            wrapper.removeViewAt(recyclerIndex);
            wrapper.addView(dialogMenuRecyclerView, hostIndex, recyclerLayoutParams);
            hostIndex = wrapper.indexOfChild(contextMenuHost);
        }

        int expectedHostIndex = wrapper.getChildCount() - 1;
        if (hostIndex != expectedHostIndex) {
            ViewGroup.LayoutParams hostLayoutParams = contextMenuHost.getLayoutParams();
            wrapper.removeViewAt(hostIndex);
            wrapper.addView(contextMenuHost, wrapper.getChildCount(), hostLayoutParams);
        }
    }

    private static View findDialogMenuRecyclerView() {
        List<View> roots = getWindowRootViews();
        for (int i = roots.size() - 1; i >= 0; i--) {
            View recyclerView = findDialogMenuRecyclerView(roots.get(i));
            if (recyclerView != null) {
                return recyclerView;
            }
        }
        return null;
    }

    private static View findDialogMenuRecyclerView(View view) {
        Object adapter = EnhanceReflection.invokeNoArgs(view, "getAdapter");
        if (adapter != null && DIALOG_MENU_ADAPTER_CLASS.equals(adapter.getClass().getName())) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View recyclerView = findDialogMenuRecyclerView(viewGroup.getChildAt(i));
                if (recyclerView != null) {
                    return recyclerView;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<View> getWindowRootViews() {
        try {
            Class<?> windowManagerGlobalClass = Class.forName("android.view.WindowManagerGlobal");
            Method getInstanceMethod = windowManagerGlobalClass.getMethod("getInstance");
            Object windowManagerGlobal = getInstanceMethod.invoke(null);
            Object views = EnhanceReflection.readField(windowManagerGlobal, "mViews");
            if (!(views instanceof List)) {
                throw new IllegalStateException("WindowManagerGlobal.mViews is not a List");
            }
            return (List<View>) views;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static PostIdentity resolvePostIdentity(ViewGroup collectionView, View postChildView) {
        View postRootView = postChildView.getParent() == collectionView
                ? postChildView
                : resolveDirectChild(collectionView, postChildView);
        Object viewHolder = EnhanceReflection.invoke(
                collectionView, "getChildViewHolder", new Class<?>[] {View.class}, postRootView);

        Object postItem = EnhanceReflection.invokeNoArgs(viewHolder, "getPostItem");
        Object configurationSet = EnhanceReflection.invokeNoArgs(viewHolder, "getConfigurationSet");
        String chanName = (String) EnhanceReflection.readField(configurationSet, "chanName");
        String boardName = (String) EnhanceReflection.invokeNoArgs(postItem, "getBoardName");

        Object postNumberObject = EnhanceReflection.invokeNoArgs(postItem, "getPostNumber");
        int postNumber = (Integer) EnhanceReflection.readField(postNumberObject, "major");

        return new PostIdentity(
                new PostKey(chanName, boardName, postNumber),
                (ViewGroup) postRootView);
    }

    private static View resolveDirectChild(ViewGroup parent, View child) {
        View directChild = child;
        while (true) {
            ViewParent currentParent = directChild.getParent();
            if (currentParent == parent) {
                return directChild;
            }
            if (!(currentParent instanceof View)) {
                throw new IllegalStateException("View is not attached under target parent");
            }
            directChild = (View) currentParent;
        }
    }

    private static WidgetPayload resolveWidgetPayload(PostKey postKey, Map<PostKey, WidgetPayload> payloads) {
        synchronized (STORE_LOCK) {
            return payloads.get(postKey);
        }
    }

    private static void applyWidgetPayload(
            Activity activity,
            ViewGroup root,
            WidgetPayload payload,
            View anchorView,
            Set<String> knownWidgetTags) {
        List<EnhanceWidget> widgets = payload != null ? payload.widgets : new ArrayList<>();

        HashSet<String> activeTags = new HashSet<>();
        for (EnhanceWidget widget : widgets) {
            activeTags.add(widget.getContainerTag());
        }

        removeWidgetContainers(root, knownWidgetTags, activeTags);

        for (EnhanceWidget widget : widgets) {
            ensureContainer(activity, root, widget, anchorView);
            widget.inject(activity, root);
        }
    }

    private static void removeWidgetContainers(ViewGroup root, Set<String> knownTags, Set<String> activeTags) {
        for (String tag : knownTags) {
            if (activeTags.contains(tag)) {
                continue;
            }
            View container = root.findViewWithTag(tag);
            if (container != null && container.getParent() == root) {
                root.removeView(container);
            }
        }
    }

    private static void removeWidgetContainersRecursive(View root, Set<String> knownTags, Set<String> activeTags) {
        if (!(root instanceof ViewGroup)) {
            return;
        }
        ViewGroup viewGroup = (ViewGroup) root;
        removeWidgetContainers(viewGroup, knownTags, activeTags);
        for (int i = viewGroup.getChildCount() - 1; i >= 0; i--) {
            removeWidgetContainersRecursive(viewGroup.getChildAt(i), knownTags, activeTags);
        }
    }

    private static LinearLayout ensureContainer(
            Activity activity, ViewGroup root, EnhanceWidget widget, View anchorView) {
        String containerTag = widget.getContainerTag();
        View existing = root.findViewWithTag(containerTag);
        LinearLayout container;

        if (existing == null) {
            container = new LinearLayout(activity);
            container.setTag(containerTag);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setClickable(false);
            container.setFocusable(false);
            int index = resolveInsertionIndex(root, anchorView);
            root.addView(container, index, widget.createLayoutParams(activity));
        } else {
            container = (LinearLayout) existing;
            if (anchorView != null && container.getParent() == root) {
                int currentIndex = root.indexOfChild(container);
                int targetIndex = resolveInsertionIndex(root, anchorView);
                if (currentIndex != targetIndex) {
                    root.removeViewAt(currentIndex);
                    root.addView(container, resolveInsertionIndex(root, anchorView));
                }
            }
            container.setLayoutParams(widget.createLayoutParams(activity));
        }

        return container;
    }

    private static int resolveInsertionIndex(ViewGroup root, View anchorView) {
        if (anchorView == null) {
            return root.getChildCount();
        }
        View anchorDirectChild = anchorView.getParent() == root
                ? anchorView
                : resolveDirectChild(root, anchorView);
        int index = root.indexOfChild(anchorDirectChild);
        if (index < 0) {
            throw new IllegalStateException("Anchor view is not attached under target root");
        }
        return index;
    }

    private static Set<String> snapshotKnownWidgetTags(Set<String> source) {
        synchronized (STORE_LOCK) {
            return new HashSet<>(source);
        }
    }

    private static PostKey requireScopedPostKey() {
        PostScope postScope = CURRENT_POST_SCOPE.get();
        if (postScope == null) {
            throw new IllegalStateException("setWidgetsForPost called outside post scope");
        }
        return new PostKey(postScope.chanName, postScope.boardName, postScope.postNumber);
    }

    private static void scheduleContextMenuInjection(Activity activity) {
        MAIN_HANDLER.postDelayed(() -> {
            if (activity.isFinishing() || EnhanceReflection.isActivityDestroyed(activity)) {
                return;
            }
            injectPendingContextMenuWidgets(activity);
        }, CONTEXT_MENU_SCAN_DELAY_MS);
    }

    private static void scheduleApplySync() {
        synchronized (APPLY_SYNC_LOCK) {
            if (applySyncScheduled) {
                return;
            }
            applySyncScheduled = true;
        }
        MAIN_HANDLER.post(() -> {
            synchronized (APPLY_SYNC_LOCK) {
                applySyncScheduled = false;
            }
            EnhanceReflection.syncCurrentActivitiesNow();
        });
    }

    private static final class PostLongClickWrapper implements View.OnLongClickListener {
        private final Activity activity;
        private final View.OnLongClickListener delegate;

        private PostLongClickWrapper(
                Activity activity,
                View.OnLongClickListener delegate) {
            this.activity = activity;
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public boolean onLongClick(View v) {
            PostKey postKey = (PostKey) v.getTag(TAG_BOUND_POST_KEY);
            boolean handled = delegate.onLongClick(v);
            if (handled) {
                pendingContextMenuPostKey = postKey;
                pendingContextMenuTimestampMs = SystemClock.uptimeMillis();
                scheduleContextMenuInjection(activity);
            }
            return handled;
        }
    }

    private static final class PostClickWrapper implements View.OnClickListener {
        private final View.OnClickListener delegate;

        private PostClickWrapper(View.OnClickListener delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public void onClick(View v) {
            delegate.onClick(v);
            scheduleApplySync();
            MAIN_HANDLER.postDelayed(HookPost::scheduleApplySync, 100L);
        }
    }

    @EqualsAndHashCode
    private static final class PostKey {
        private final String chanName;
        private final String boardName;
        private final int postNumber;

        private PostKey(String chanName, String boardName, int postNumber) {
            this.chanName = chanName;
            this.boardName = boardName;
            this.postNumber = postNumber;
        }
    }

    private static final class PostScope {
        private final String chanName;
        private final String boardName;
        private final int postNumber;

        private PostScope(String chanName, String boardName, int postNumber) {
            this.chanName = chanName;
            this.boardName = boardName;
            this.postNumber = postNumber;
        }
    }

    private static final class PostIdentity {
        private final PostKey postKey;
        private final ViewGroup postRoot;

        private PostIdentity(PostKey postKey, ViewGroup postRoot) {
            this.postKey = postKey;
            this.postRoot = postRoot;
        }
    }

    private static final class WidgetPayload {
        private final ArrayList<EnhanceWidget> widgets;

        private WidgetPayload(List<EnhanceWidget> widgets) {
            this.widgets = new ArrayList<>(widgets);
        }
    }
}
