package com.mishiranu.dashchan.chan.e444.enhance.widgets;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.widget.TextView;
import android.widget.Toast;
import chan.content.model.Post;
import chan.util.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.mishiranu.dashchan.chan.e444.E444ChanLocator;
import com.mishiranu.dashchan.chan.e444.E444JsonUtils;
import com.mishiranu.dashchan.chan.e444.E444Model;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceReflection;
import com.mishiranu.dashchan.chan.e444.enhance.TaskCallback;
import com.mishiranu.dashchan.chan.e444.enhance.tasks.TaskReadIcon;
import com.mishiranu.dashchan.chan.e444.enhance.tasks.TaskReadPost;
import com.mishiranu.dashchan.chan.e444.enhance.tasks.TaskSendReaction;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WidgetReaction {
    public static final int DEFAULT_ICON_SIZE_DP = 14;

    public enum SelectionMode {
        TOGGLE,
        SET
    }

    private static final Map<String, Bitmap> ICON_BITMAPS = new ConcurrentHashMap<>();
    private static final Map<String, List<WeakReference<TextView>>> ICON_WAITERS = new ConcurrentHashMap<>();
    private static final java.util.Set<String> ICON_LOADING =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private static final Map<String, String> TOGGLED_REACTION_BY_POST = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> BOARD_REACTION_ICONS_BY_BOARD = new ConcurrentHashMap<>();
    private static final String PREFS_NAME = "e444_reactions";
    private static final String PREFS_KEY_TOGGLED = "toggled";
    private static final Object TOGGLED_REACTIONS_LOCK = new Object();
    private static volatile boolean toggledReactionsLoaded;
    private static final int TAG_ICON_SIZE_DP = 0xE4442001;
    private static final int TAG_ICON_NAME = 0xE4442002;

    private final E444ChanLocator locator;
    private final String iconName;
    private final int count;
    private final String boardName;
    private final int postNumber;
    private final String postStateKey;

    public WidgetReaction(E444ChanLocator locator, String iconName, int count, String boardName, int postNumber) {
        this.locator = locator;
        this.iconName = iconName;
        this.count = count;
        this.boardName = boardName;
        this.postNumber = postNumber;
        this.postStateKey = boardName + ":" + postNumber;
    }

    public static void setBoardReactionIcons(String boardName, List<String> reactionIcons) {
        if (boardName == null) return;
        if (reactionIcons == null || reactionIcons.isEmpty()) {
            BOARD_REACTION_ICONS_BY_BOARD.remove(boardName);
            return;
        }
        BOARD_REACTION_ICONS_BY_BOARD.put(boardName, new ArrayList<>(reactionIcons));
    }

    public static List<String> getBoardReactionIcons(String boardName) {
        if (boardName == null) return Collections.emptyList();
        List<String> icons = BOARD_REACTION_ICONS_BY_BOARD.get(boardName);
        return icons != null ? new ArrayList<>(icons) : Collections.emptyList();
    }

    public int getCount() {
        return count;
    }

    public boolean isSelected(Context context) {
        ensureToggledReactionsLoaded(context);
        return iconName.equals(TOGGLED_REACTION_BY_POST.get(postStateKey));
    }

    public void bindIcon(TextView reactionView, int iconSizeDp) {
        reactionView.setTag(TAG_ICON_NAME, iconName);
        reactionView.setTag(TAG_ICON_SIZE_DP, iconSizeDp);
        Bitmap bitmap = ICON_BITMAPS.get(iconName);
        if (bitmap != null) {
            applyIconDrawable(reactionView, bitmap);
        } else {
            reactionView.setCompoundDrawables(null, null, null, null);
            requestIconLoad(reactionView);
        }
    }

    public void bindClick(
            Activity activity,
            TextView reactionView,
            SelectionMode mode,
            Runnable onReactionClick,
            Runnable onReactionSentSuccess,
            Runnable onSelectionChanged) {
        reactionView.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View view) {
                if (onReactionClick != null) {
                    onReactionClick.run();
                }
                sendReaction(activity, reactionView, mode, onReactionSentSuccess, onSelectionChanged);
            }
        });
    }

    private void sendReaction(
            Activity activity,
            TextView reactionView,
            SelectionMode mode,
            Runnable onReactionSentSuccess,
            Runnable onSelectionChanged) {
        final String targetBoardName = boardName;
        final int targetPostNumber = postNumber;
        if (StringUtils.isEmpty(targetBoardName) || targetPostNumber <= 0) {
            Toast.makeText(activity, "Unable to resolve reaction target", Toast.LENGTH_SHORT).show();
            return;
        }
        reactionView.setEnabled(false);
        EnhanceReflection.submitTask(
                locator,
                new TaskSendReaction(targetBoardName, targetPostNumber, iconName),
                new TaskCallback<Void>() {
                    @Override
                    public void accept(Void result) {
                        ensureToggledReactionsLoaded(activity.getApplicationContext());
                        updateReactionSelection(mode);
                        persistToggledReactions(activity.getApplicationContext());
                        if (onSelectionChanged != null) {
                            onSelectionChanged.run();
                        }
                        if (onReactionSentSuccess != null) {
                            onReactionSentSuccess.run();
                        }
                        reactionView.setEnabled(true);
                        refreshPostAfterReaction(activity, targetBoardName, targetPostNumber);
                    }
                },
                new TaskCallback<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        reactionView.setEnabled(true);
                        EnhanceReflection.showError(throwable);
                    }
                });
    }

    private void refreshPostAfterReaction(Activity activity, String targetBoardName, int targetPostNumber) {
        EnhanceReflection.submitTask(
                locator,
                new TaskReadPost(targetBoardName, targetPostNumber),
                new TaskCallback<Post>() {
                    @Override
                    public void accept(Post post) {
                        EnhanceReflection.requestThreadPostRebind(activity, targetBoardName, targetPostNumber);
                    }
                },
                new TaskCallback<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        EnhanceReflection.requestThreadPostRebind(activity, targetBoardName, targetPostNumber);
                    }
                });
    }

    private void updateReactionSelection(SelectionMode mode) {
        if (mode == SelectionMode.SET) {
            TOGGLED_REACTION_BY_POST.put(postStateKey, iconName);
            return;
        }
        String activeIcon = TOGGLED_REACTION_BY_POST.get(postStateKey);
        if (iconName.equals(activeIcon)) {
            TOGGLED_REACTION_BY_POST.remove(postStateKey);
        } else {
            TOGGLED_REACTION_BY_POST.put(postStateKey, iconName);
        }
    }

    private void requestIconLoad(TextView reactionView) {
        List<WeakReference<TextView>> waiters = ICON_WAITERS.get(iconName);
        if (waiters == null) {
            synchronized (ICON_WAITERS) {
                waiters = ICON_WAITERS.get(iconName);
                if (waiters == null) {
                    waiters = Collections.synchronizedList(new ArrayList<WeakReference<TextView>>());
                    ICON_WAITERS.put(iconName, waiters);
                }
            }
        }
        waiters.add(new WeakReference<>(reactionView));
        if (!ICON_LOADING.add(iconName)) {
            return;
        }
        EnhanceReflection.submitTask(
                locator,
                new TaskReadIcon(iconName),
                new TaskCallback<Bitmap>() {
                    @Override
                    public void accept(Bitmap bitmap) {
                        ICON_BITMAPS.put(iconName, bitmap);
                        deliverLoadedIcon(iconName, bitmap);
                    }
                },
                new TaskCallback<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        deliverLoadedIcon(iconName, null);
                    }
                });
    }

    private static void deliverLoadedIcon(String iconName, Bitmap bitmap) {
        List<WeakReference<TextView>> waiters = ICON_WAITERS.remove(iconName);
        ICON_LOADING.remove(iconName);
        if (waiters == null || bitmap == null) {
            return;
        }
        for (int i = 0; i < waiters.size(); i++) {
            TextView reactionView = waiters.get(i).get();
            if (reactionView == null) {
                continue;
            }
            Object boundIcon = reactionView.getTag(TAG_ICON_NAME);
            if (iconName.equals(boundIcon)) {
                applyIconDrawable(reactionView, bitmap);
            }
        }
    }

    private static void applyIconDrawable(TextView reactionView, Bitmap bitmap) {
        Drawable drawable = new BitmapDrawable(reactionView.getResources(), bitmap);
        Object iconSizeTag = reactionView.getTag(TAG_ICON_SIZE_DP);
        int iconSizeDp = iconSizeTag instanceof Integer ? (Integer) iconSizeTag : DEFAULT_ICON_SIZE_DP;
        int iconSizePx = dp(reactionView, iconSizeDp);
        drawable.setBounds(0, 0, iconSizePx, iconSizePx);
        reactionView.setCompoundDrawablePadding(dp(reactionView, 4));
        reactionView.setCompoundDrawables(drawable, null, null, null);
    }

    private static String createPostStateKey(String boardName, int postNumber) {
        return (boardName != null ? boardName : "") + ":" + postNumber;
    }

    private static void ensureToggledReactionsLoaded(Context context) {
        if (toggledReactionsLoaded || context == null) {
            return;
        }
        synchronized (TOGGLED_REACTIONS_LOCK) {
            if (toggledReactionsLoaded) {
                return;
            }
            SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String serialized = preferences.getString(PREFS_KEY_TOGGLED, null);
            if (!StringUtils.isEmpty(serialized)) {
                try {
                    Map<String, String> root =
                            E444JsonUtils.fromJson(serialized, new TypeReference<Map<String, String>>() {});
                    if (root != null) {
                        for (Map.Entry<String, String> entry : root.entrySet()) {
                            String postKey = entry.getKey();
                            String icon = StringUtils.nullIfEmpty(entry.getValue());
                            if (!StringUtils.isEmpty(postKey) && !StringUtils.isEmpty(icon)) {
                                TOGGLED_REACTION_BY_POST.put(postKey, icon);
                            }
                        }
                    }
                } catch (IOException ignored) {
                    TOGGLED_REACTION_BY_POST.clear();
                }
            }
            toggledReactionsLoaded = true;
        }
    }

    private static void persistToggledReactions(Context context) {
        if (context == null) {
            return;
        }
        synchronized (TOGGLED_REACTIONS_LOCK) {
            try {
                String serialized = E444JsonUtils.toJson(TOGGLED_REACTION_BY_POST);
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(PREFS_KEY_TOGGLED, serialized)
                        .apply();
            } catch (RuntimeException ignored) {
                // Ignore serialization failures.
            }
        }
    }

    private static int dp(TextView reactionView, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, reactionView.getResources().getDisplayMetrics()));
    }
}
