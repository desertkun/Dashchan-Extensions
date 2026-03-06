package com.mishiranu.dashchan.chan.e444.enhance.widgets;

import android.app.Activity;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.flexbox.AlignItems;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.flexbox.JustifyContent;
import com.mishiranu.dashchan.chan.e444.E444ChanLocator;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceWidget;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceWidgetUtils;
import com.mishiranu.dashchan.chan.e444.enhance.controllers.HookPost;
import java.util.ArrayList;
import java.util.List;

public final class WidgetReactionsContextMenu implements EnhanceWidget {
    public static final String CONTAINER_TAG = "e444_context_menu_reactions_widget";
    private static final int TAG_REACTION_ITEM = 0xE4442201;
    private static final int ICON_SIZE_DP = WidgetReaction.DEFAULT_ICON_SIZE_DP * 2;
    private static final int CELL_WIDTH_DP = 48;
    private static final float MAX_SCROLL_HEIGHT_FRACTION = 0.45f;
    private final String boardName;
    private final int postNumber;
    private final String postStateKey;
    private final List<WidgetReaction> reactions;

    public WidgetReactionsContextMenu(E444ChanLocator locator, List<String> iconNames, String boardName, int postNumber) {
        this.boardName = boardName;
        this.postNumber = postNumber;
        this.postStateKey = EnhanceWidgetUtils.buildPostStateKey(boardName, postNumber);
        ArrayList<WidgetReaction> parsedReactions = new ArrayList<>(iconNames.size());
        for (String iconName : iconNames) {
            parsedReactions.add(new WidgetReaction(locator, iconName));
        }
        reactions = parsedReactions;
    }

    public boolean isEmpty() {
        return reactions.isEmpty();
    }

    @Override
    public String getContainerTag() {
        return CONTAINER_TAG;
    }

    @Override
    public ViewGroup.LayoutParams createLayoutParams(Activity activity) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void inject(Activity activity, ViewGroup postRoot) {
        Object closeAction = postRoot.getTag(HookPost.TAG_CONTEXT_MENU_CLOSE_ACTION);
        Runnable onReactionClick = closeAction instanceof Runnable ? (Runnable) closeAction : null;
        LinearLayout container = obtainOrCreateContainer(activity, postRoot);
        container.removeAllViews();
        ScrollView scrollView = createScrollView(activity);
        container.addView(
                scrollView,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FlexboxLayout flexbox = createFlexbox(activity);
        scrollView.addView(flexbox, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Runnable refreshSelectionState = () -> applyReactionSelectionState(activity, flexbox);
        for (WidgetReaction reaction : reactions) {
            TextView reactionView = createReactionView(activity);
            flexbox.addView(reactionView, createReactionLayoutParams(activity));
            bindReactionView(activity, reactionView, reaction, onReactionClick, refreshSelectionState);
        }
        applyReactionSelectionState(activity, flexbox);
        applyMaxScrollHeight(activity, scrollView, flexbox);
    }

    private static LinearLayout obtainOrCreateContainer(Activity activity, ViewGroup postRoot) {
        return EnhanceWidgetUtils.obtainOrCreateLinearContainer(
                activity,
                postRoot,
                CONTAINER_TAG,
                LinearLayout.VERTICAL,
                Gravity.CENTER_HORIZONTAL);
    }

    private static FlexboxLayout createFlexbox(Activity activity) {
        FlexboxLayout flexbox = new FlexboxLayout(activity);
        flexbox.setFlexDirection(FlexDirection.ROW);
        flexbox.setFlexWrap(FlexWrap.WRAP);
        flexbox.setJustifyContent(JustifyContent.CENTER);
        flexbox.setAlignItems(AlignItems.CENTER);
        return flexbox;
    }

    private static ScrollView createScrollView(Activity activity) {
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(true);
        scrollView.setHorizontalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scrollView.setPadding(0, 0, 0, EnhanceWidgetUtils.dp(activity, 8));
        return scrollView;
    }

    private static TextView createReactionView(Activity activity) {
        return new TextView(activity);
    }

    private static FlexboxLayout.LayoutParams createReactionLayoutParams(Activity activity) {
        FlexboxLayout.LayoutParams layoutParams =
                new FlexboxLayout.LayoutParams(
                        EnhanceWidgetUtils.dp(activity, CELL_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setFlexGrow(0f);
        layoutParams.setFlexShrink(0f);
        layoutParams.leftMargin = EnhanceWidgetUtils.dp(activity, 2);
        layoutParams.rightMargin = EnhanceWidgetUtils.dp(activity, 2);
        layoutParams.bottomMargin = EnhanceWidgetUtils.dp(activity, 2);
        return layoutParams;
    }

    private void bindReactionView(
            Activity activity,
            TextView reactionView,
            WidgetReaction reaction,
            Runnable onReactionClick,
            Runnable refreshSelectionState) {
        reactionView.setTag(TAG_REACTION_ITEM, reaction);
        reactionView.setGravity(Gravity.CENTER);
        int minSize = EnhanceWidgetUtils.dp(activity, ICON_SIZE_DP + 8);
        reactionView.setMinHeight(minSize);
        reactionView.setMinWidth(minSize);
        reactionView.setPadding(
                EnhanceWidgetUtils.dp(activity, 2),
                EnhanceWidgetUtils.dp(activity, 2),
                EnhanceWidgetUtils.dp(activity, 2),
                EnhanceWidgetUtils.dp(activity, 2));
        reactionView.setText("");
        reactionView.setBackground(null);
        reaction.bindIcon(reactionView, ICON_SIZE_DP);
        reaction.bindClick(
                activity,
                reactionView,
                boardName,
                postNumber,
                postStateKey,
                WidgetReaction.SelectionMode.TOGGLE,
                onReactionClick,
                refreshSelectionState);
        applyReactionViewState(activity, reactionView, reaction);
    }

    private void applyReactionSelectionState(Activity activity, FlexboxLayout flexbox) {
        for (int i = 0; i < flexbox.getChildCount(); i++) {
            View child = flexbox.getChildAt(i);
            if (!(child instanceof TextView)) {
                continue;
            }
            TextView reactionView = (TextView) child;
            Object reactionTag = reactionView.getTag(TAG_REACTION_ITEM);
            if (!(reactionTag instanceof WidgetReaction)) {
                continue;
            }
            WidgetReaction reaction = (WidgetReaction) reactionTag;
            applyReactionViewState(activity, reactionView, reaction);
        }
    }

    private void applyReactionViewState(Activity activity, TextView reactionView, WidgetReaction reaction) {
        reactionView.setSelected(WidgetReactionsPost.isSelected(
                activity.getApplicationContext(), postStateKey, reaction.getIconName()));
    }

    private static void applyMaxScrollHeight(Activity activity, ScrollView scrollView, FlexboxLayout flexbox) {
        final int displayHeight = activity.getResources().getDisplayMetrics().heightPixels;
        final int preferredMaxHeight =
                Math.max(EnhanceWidgetUtils.dp(activity, 96), Math.round(displayHeight * MAX_SCROLL_HEIGHT_FRACTION));
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                Rect visibleFrame = new Rect();
                scrollView.getWindowVisibleDisplayFrame(visibleFrame);
                int[] location = new int[2];
                scrollView.getLocationOnScreen(location);
                int availableVisibleHeight =
                        visibleFrame.bottom - location[1] - EnhanceWidgetUtils.dp(activity, 20);
                int maxHeight = availableVisibleHeight > 0
                        ? Math.max(
                                EnhanceWidgetUtils.dp(activity, 72),
                                Math.min(preferredMaxHeight, availableVisibleHeight))
                        : preferredMaxHeight;
                int contentHeight = flexbox.getMeasuredHeight();
                ViewGroup.LayoutParams layoutParams = scrollView.getLayoutParams();
                if (layoutParams == null) {
                    return;
                }
                int targetHeight = contentHeight > maxHeight ? maxHeight : ViewGroup.LayoutParams.WRAP_CONTENT;
                if (layoutParams.height != targetHeight) {
                    layoutParams.height = targetHeight;
                    scrollView.setLayoutParams(layoutParams);
                }
            }
        });
    }
}
