package com.mishiranu.dashchan.chan.e444.enhance.widgets;

import android.app.Activity;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.flexbox.AlignItems;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.flexbox.JustifyContent;
import com.mishiranu.dashchan.chan.e444.E444ChanLocator;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceWidget;
import com.mishiranu.dashchan.chan.e444.enhance.controllers.HookPost;
import java.util.ArrayList;
import java.util.List;

public final class WidgetReactionsContextMenu implements EnhanceWidget {
    public static final String CONTAINER_TAG = "e444_context_menu_reactions_widget";
    private static final int TAG_REACTION_ITEM = 0xE4442201;
    private static final int ICON_SIZE_DP = WidgetReaction.DEFAULT_ICON_SIZE_DP * 2;
    private static final int CELL_WIDTH_DP = 48;
    private final List<WidgetReaction> reactions;

    public WidgetReactionsContextMenu(E444ChanLocator locator, List<String> iconNames) {
        ArrayList<WidgetReaction> parsedReactions = new ArrayList<>(iconNames.size());
        for (String iconName : iconNames) {
            parsedReactions.add(WidgetReaction.fromIconName(locator, iconName));
        }
        reactions = parsedReactions;
    }

    public boolean isEmpty() {
        return reactions.isEmpty();
    }

    public void setPostContext(String boardName, int postNumber) {
        for (WidgetReaction reaction : reactions) {
            reaction.setPostContext(boardName, postNumber);
        }
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
        FlexboxLayout flexbox = createFlexbox(activity);
        container.addView(
                flexbox,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Runnable refreshSelectionState = () -> applyReactionSelectionState(activity, flexbox);
        for (WidgetReaction reaction : reactions) {
            TextView reactionView = createReactionView(activity);
            flexbox.addView(reactionView, createReactionLayoutParams(activity));
            bindReactionView(activity, reactionView, reaction, onReactionClick, refreshSelectionState);
        }
        applyReactionSelectionState(activity, flexbox);
    }

    private static LinearLayout obtainOrCreateContainer(Activity activity, ViewGroup postRoot) {
        View existingView = postRoot.findViewWithTag(CONTAINER_TAG);
        if (existingView instanceof LinearLayout) {
            return (LinearLayout) existingView;
        }
        LinearLayout container = new LinearLayout(activity);
        container.setTag(CONTAINER_TAG);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.START);
        container.setClickable(false);
        container.setFocusable(false);
        postRoot.addView(container);
        return container;
    }

    private static FlexboxLayout createFlexbox(Activity activity) {
        FlexboxLayout flexbox = new FlexboxLayout(activity);
        flexbox.setFlexDirection(FlexDirection.ROW);
        flexbox.setFlexWrap(FlexWrap.WRAP);
        flexbox.setJustifyContent(JustifyContent.FLEX_START);
        flexbox.setAlignItems(AlignItems.STRETCH);
        return flexbox;
    }

    private static TextView createReactionView(Activity activity) {
        return new TextView(activity);
    }

    private static FlexboxLayout.LayoutParams createReactionLayoutParams(Activity activity) {
        FlexboxLayout.LayoutParams layoutParams =
                new FlexboxLayout.LayoutParams(dp(activity, CELL_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setFlexGrow(1f);
        layoutParams.setFlexShrink(0f);
        layoutParams.bottomMargin = dp(activity, 2);
        return layoutParams;
    }

    private static void bindReactionView(
            Activity activity,
            TextView reactionView,
            WidgetReaction reaction,
            Runnable onReactionClick,
            Runnable refreshSelectionState) {
        reactionView.setTag(TAG_REACTION_ITEM, reaction);
        reactionView.setGravity(Gravity.CENTER);
        int minSize = dp(activity, ICON_SIZE_DP + 8);
        reactionView.setMinHeight(minSize);
        reactionView.setMinWidth(minSize);
        reactionView.setPadding(dp(activity, 2), dp(activity, 2), dp(activity, 2), dp(activity, 2));
        reactionView.setText("");
        reactionView.setBackground(null);
        reaction.bindIcon(reactionView, ICON_SIZE_DP);
        reaction.bindClick(
                activity,
                reactionView,
                WidgetReaction.SelectionMode.SET,
                onReactionClick,
                null,
                refreshSelectionState);
        applyReactionViewState(activity, reactionView, reaction);
    }

    private static void applyReactionSelectionState(Activity activity, FlexboxLayout flexbox) {
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

    private static void applyReactionViewState(Activity activity, TextView reactionView, WidgetReaction reaction) {
        reactionView.setSelected(reaction.isSelected(activity.getApplicationContext()));
    }

    private static int dp(Activity activity, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, activity.getResources().getDisplayMetrics()));
    }
}
