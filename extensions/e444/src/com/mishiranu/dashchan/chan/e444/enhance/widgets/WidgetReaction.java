package com.mishiranu.dashchan.chan.e444.enhance.widgets;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.TextView;
import com.mishiranu.dashchan.chan.e444.E444ChanLocator;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceReflection;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceWidgetUtils;
import com.mishiranu.dashchan.chan.e444.enhance.TaskCallback;
import com.mishiranu.dashchan.chan.e444.enhance.tasks.TaskReadIcon;
import com.mishiranu.dashchan.chan.e444.enhance.tasks.TaskSendReaction;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
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

    private static final Map<String, List<String>> BOARD_REACTION_ICONS_BY_BOARD = new ConcurrentHashMap<>();
    private static final int TAG_ICON_SIZE_DP = 0xE4442001;
    private static final int TAG_ICON_NAME = 0xE4442002;

    private final E444ChanLocator locator;
    private final String iconName;

    public WidgetReaction(E444ChanLocator locator, String iconName) {
        this.locator = locator;
        this.iconName = iconName;
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

    public String getIconName() {
        return iconName;
    }

    public void sendReaction(
            Activity activity,
            String boardName,
            int postNumber,
            TaskCallback<Void> onSuccess,
            TaskCallback<Throwable> onError) {
        EnhanceReflection.submitTask(locator, new TaskSendReaction(boardName, postNumber, iconName), onSuccess, onError);
    }

    public void bindClick(
            Activity activity,
            TextView reactionView,
            String boardName,
            int postNumber,
            String postStateKey,
            SelectionMode selectionMode,
            Runnable onReactionClick,
            Runnable onSelectionChanged) {
        reactionView.setOnClickListener(v -> {
            if (onReactionClick != null) {
                onReactionClick.run();
            }
            reactionView.setEnabled(false);
            sendReaction(
                    activity,
                    boardName,
                    postNumber,
                    new TaskCallback<Void>() {
                        @Override
                        public void accept(Void result) {
                            WidgetReactionsPost.updateSelection(
                                    activity.getApplicationContext(),
                                    postStateKey,
                                    iconName,
                                    selectionMode);
                            if (onSelectionChanged != null) {
                                onSelectionChanged.run();
                            }
                            EnhanceWidgetUtils.refreshPost(activity, locator, boardName, postNumber);
                            reactionView.setEnabled(true);
                        }
                    },
                    new TaskCallback<Throwable>() {
                        @Override
                        public void accept(Throwable throwable) {
                            reactionView.setEnabled(true);
                            EnhanceReflection.showError(throwable);
                        }
                    });
        });
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
        int iconSizePx = EnhanceWidgetUtils.dp(reactionView, iconSizeDp);
        drawable.setBounds(0, 0, iconSizePx, iconSizePx);
        reactionView.setCompoundDrawablePadding(EnhanceWidgetUtils.dp(reactionView, 4));
        reactionView.setCompoundDrawables(drawable, null, null, null);
    }
}
