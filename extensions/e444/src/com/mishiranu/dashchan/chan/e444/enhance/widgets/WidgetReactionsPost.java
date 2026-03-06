package com.mishiranu.dashchan.chan.e444.enhance.widgets;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import chan.util.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.mishiranu.dashchan.chan.e444.E444ChanLocator;
import com.mishiranu.dashchan.chan.e444.E444Model;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceWidgetUtils;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceWidget;
import com.google.android.flexbox.AlignItems;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.flexbox.JustifyContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class WidgetReactionsPost implements EnhanceWidget {
    public static final String CONTAINER_TAG = "e444_post_injected_reactions_container";
    private static final int TAG_REACTION_ITEM = 0xE4442101;
    private static final String PREFS_NAME = "e444_reactions";
    private static final String PREFS_KEY_TOGGLED = "toggled";
    private static final EnhanceWidgetUtils.PostSelectionStore<String> TOGGLED_REACTION_BY_POST =
            new EnhanceWidgetUtils.PostSelectionStore<>(
                    PREFS_NAME,
                    PREFS_KEY_TOGGLED,
                    new TypeReference<Map<String, String>>() {},
                    StringUtils::nullIfEmpty);
    private final String boardName;
    private final int postNumber;
    private final String postStateKey;
    private final List<ReactionItem> reactions = new ArrayList<>();

    public WidgetReactionsPost(List<E444Model.Reaction> reactionsJson, E444ChanLocator locator, String boardName, int postNumber) {
        this.boardName = boardName;
        this.postNumber = postNumber;
        this.postStateKey = EnhanceWidgetUtils.buildPostStateKey(boardName, postNumber);
        for (E444Model.Reaction reaction : reactionsJson) {
            reactions.add(new ReactionItem(new WidgetReaction(locator, reaction.icon), reaction.count));
        }
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
        int horizontal = EnhanceWidgetUtils.dp(activity, 4);
        int top = EnhanceWidgetUtils.dp(activity, 2);
        LinearLayout.LayoutParams layoutParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.leftMargin = horizontal;
        layoutParams.topMargin = top;
        layoutParams.rightMargin = horizontal;
        layoutParams.bottomMargin = 0;
        return layoutParams;
    }

    @Override
    public void inject(Activity activity, ViewGroup postRoot) {
        LinearLayout container = obtainOrCreateContainer(activity, postRoot);
        FlexboxLayout flexbox = obtainOrCreateFlexbox(activity, container);
        Runnable refreshSelectionState = () -> applyReactionSelectionState(activity, flexbox);
        for (int i = 0; i < reactions.size(); i++) {
            ReactionItem reactionItem = reactions.get(i);
            TextView bubble = obtainOrCreateBubble(activity, flexbox, i);
            bindReactionBubble(activity, bubble, reactionItem, refreshSelectionState);
        }
        while (flexbox.getChildCount() > reactions.size()) {
            flexbox.removeViewAt(flexbox.getChildCount() - 1);
        }
        applyReactionSelectionState(activity, flexbox);
    }

    private static LinearLayout obtainOrCreateContainer(Activity activity, ViewGroup postRoot) {
        return EnhanceWidgetUtils.obtainOrCreateLinearContainer(activity, postRoot, CONTAINER_TAG);
    }

    private static FlexboxLayout createFlexbox(Activity activity) {
        FlexboxLayout flexbox = new FlexboxLayout(activity);
        flexbox.setFlexDirection(FlexDirection.ROW);
        flexbox.setFlexWrap(FlexWrap.WRAP);
        flexbox.setJustifyContent(JustifyContent.FLEX_START);
        flexbox.setAlignItems(AlignItems.FLEX_START);
        return flexbox;
    }

    private static TextView createBubble(Activity activity) {
        return new TextView(activity);
    }

    private static FlexboxLayout obtainOrCreateFlexbox(Activity activity, LinearLayout container) {
        return EnhanceWidgetUtils.obtainOrCreateOnlyChild(
                container,
                FlexboxLayout.class,
                () -> createFlexbox(activity),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static TextView obtainOrCreateBubble(Activity activity, FlexboxLayout flexbox, int index) {
        return EnhanceWidgetUtils.obtainOrCreateChild(
                flexbox,
                index,
                TextView.class,
                () -> createBubble(activity),
                createBubbleLayoutParams(activity));
    }

    private static FlexboxLayout.LayoutParams createBubbleLayoutParams(Activity activity) {
        FlexboxLayout.LayoutParams layoutParams = new FlexboxLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.rightMargin = EnhanceWidgetUtils.dp(activity, 2);
        layoutParams.bottomMargin = EnhanceWidgetUtils.dp(activity, 2);
        return layoutParams;
    }

    private void bindReactionBubble(
            Activity activity,
            TextView bubble,
            ReactionItem reactionItem,
            Runnable refreshSelectionState) {
        bubble.setTag(TAG_REACTION_ITEM, reactionItem);
        bubble.setGravity(Gravity.CENTER_VERTICAL);
        bubble.setMinHeight(EnhanceWidgetUtils.dp(activity, 24));
        bubble.setMinWidth(0);
        bubble.setPadding(
                EnhanceWidgetUtils.dp(activity, 6),
                EnhanceWidgetUtils.dp(activity, 4),
                EnhanceWidgetUtils.dp(activity, 6),
                EnhanceWidgetUtils.dp(activity, 4));
        bubble.setTextSize(12f);
        bubble.setTextColor(resolveTextColor(activity));
        bubble.setText(Integer.toString(reactionItem.count));
        reactionItem.reaction.bindIcon(bubble, WidgetReaction.DEFAULT_ICON_SIZE_DP);
        reactionItem.reaction.bindClick(
                activity,
                bubble,
                boardName,
                postNumber,
                postStateKey,
                WidgetReaction.SelectionMode.TOGGLE,
                null,
                refreshSelectionState);
        applyReactionBubbleState(activity, bubble, reactionItem);
    }

    private void applyReactionSelectionState(Activity activity, FlexboxLayout flexbox) {
        for (int i = 0; i < flexbox.getChildCount(); i++) {
            View child = flexbox.getChildAt(i);
            if (!(child instanceof TextView)) {
                continue;
            }
            TextView bubble = (TextView) child;
            Object reactionTag = bubble.getTag(TAG_REACTION_ITEM);
            if (!(reactionTag instanceof ReactionItem)) {
                continue;
            }
            ReactionItem reactionItem = (ReactionItem) reactionTag;
            applyReactionBubbleState(activity, bubble, reactionItem);
        }
    }

    private void applyReactionBubbleState(Activity activity, TextView bubble, ReactionItem reactionItem) {
        bubble.setText(Integer.toString(reactionItem.count));
        boolean selected = isSelected(
                activity.getApplicationContext(), postStateKey, reactionItem.reaction.getIconName());
        bubble.setSelected(selected);
        bubble.setBackground(createBubbleBackground(activity, selected));
    }

    private static final class ReactionItem {
        private final WidgetReaction reaction;
        private final int count;

        private ReactionItem(WidgetReaction reaction, int count) {
            this.reaction = reaction;
            this.count = count;
        }
    }

    static boolean isSelected(Context context, String postStateKey, String iconName) {
        return iconName.equals(TOGGLED_REACTION_BY_POST.get(context, postStateKey));
    }

    static void updateSelection(
            Context context,
            String postStateKey,
            String iconName,
            WidgetReaction.SelectionMode mode) {
        String activeIcon = TOGGLED_REACTION_BY_POST.get(context, postStateKey);
        if (mode == WidgetReaction.SelectionMode.SET) {
            TOGGLED_REACTION_BY_POST.put(context, postStateKey, iconName);
        } else if (iconName.equals(activeIcon)) {
            TOGGLED_REACTION_BY_POST.remove(context, postStateKey);
        } else {
            TOGGLED_REACTION_BY_POST.put(context, postStateKey, iconName);
        }
    }

    private static Drawable createBubbleBackground(Activity activity, boolean active) {
        int backgroundColor =
                EnhanceWidgetUtils.resolveThemeColor(activity, "colorCardBackground", android.R.attr.colorBackground);
        int accentColor =
                EnhanceWidgetUtils.resolveThemeColor(activity, "colorAccentSupport", android.R.attr.colorAccent);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(EnhanceWidgetUtils.dp(activity, 12));
        shape.setColor(active ? EnhanceWidgetUtils.applyAlpha(accentColor, 0.22f) : backgroundColor);
        shape.setStroke(
                EnhanceWidgetUtils.dp(activity, 1),
                EnhanceWidgetUtils.applyAlpha(accentColor, active ? 0.65f : 0.35f));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new RippleDrawable(
                    ColorStateList.valueOf(EnhanceWidgetUtils.applyAlpha(accentColor, 0.18f)), shape, null);
        }
        return shape;
    }

    private static int resolveTextColor(Activity activity) {
        return EnhanceWidgetUtils.resolveThemeColor(activity, "colorTextMeta", android.R.attr.textColorPrimary);
    }
}
