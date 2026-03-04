package com.mishiranu.dashchan.chan.e444.enhance.widgets;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.mishiranu.dashchan.chan.e444.E444ChanLocator;
import com.mishiranu.dashchan.chan.e444.E444Model;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceWidget;
import com.google.android.flexbox.AlignItems;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.flexbox.JustifyContent;
import java.util.ArrayList;
import java.util.List;

public final class WidgetReactionsPost implements EnhanceWidget {
    public static final String CONTAINER_TAG = "e444_post_injected_reactions_container";
    private static final int TAG_REACTION_ITEM = 0xE4442101;
    private final List<WidgetReaction> reactions = new ArrayList<>();

    public WidgetReactionsPost(List<E444Model.Reaction> reactionsJson, E444ChanLocator locator, String boardName, int postNumber) {
        for (E444Model.Reaction reaction : reactionsJson) {
            reactions.add(new WidgetReaction(locator, reaction.icon, reaction.count, boardName, postNumber));
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
        int horizontal = Math.round(4f * activity.getResources().getDisplayMetrics().density);
        int top = Math.round(2f * activity.getResources().getDisplayMetrics().density);
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
        container.removeAllViews();
        FlexboxLayout flexbox = createFlexbox(activity);
        container.addView(
                flexbox,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Runnable refreshSelectionState = () -> applyReactionSelectionState(activity, flexbox);
        for (WidgetReaction reaction : reactions) {
            TextView bubble = createBubble(activity);
            flexbox.addView(bubble, createBubbleLayoutParams(activity));
            bindReactionBubble(activity, bubble, reaction, refreshSelectionState);
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
        flexbox.setAlignItems(AlignItems.FLEX_START);
        return flexbox;
    }

    private static TextView createBubble(Activity activity) {
        return new TextView(activity);
    }

    private static FlexboxLayout.LayoutParams createBubbleLayoutParams(Activity activity) {
        FlexboxLayout.LayoutParams layoutParams = new FlexboxLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.rightMargin = dp(activity, 2);
        layoutParams.bottomMargin = dp(activity, 2);
        return layoutParams;
    }

    private static void bindReactionBubble(
            Activity activity,
            TextView bubble,
            WidgetReaction reaction,
            Runnable refreshSelectionState) {
        bubble.setTag(TAG_REACTION_ITEM, reaction);
        bubble.setGravity(Gravity.CENTER_VERTICAL);
        bubble.setMinHeight(dp(activity, 24));
        bubble.setMinWidth(0);
        bubble.setPadding(dp(activity, 6), dp(activity, 4), dp(activity, 6), dp(activity, 4));
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        bubble.setTextColor(resolveTextColor(activity));
        bubble.setText(Integer.toString(reaction.getCount()));
        reaction.bindIcon(bubble, WidgetReaction.DEFAULT_ICON_SIZE_DP);
        reaction.bindClick(
                activity,
                bubble,
                WidgetReaction.SelectionMode.TOGGLE,
                null,
                null,
                refreshSelectionState);
        applyReactionBubbleState(activity, bubble, reaction);
    }

    private static void applyReactionSelectionState(Activity activity, FlexboxLayout flexbox) {
        for (int i = 0; i < flexbox.getChildCount(); i++) {
            View child = flexbox.getChildAt(i);
            if (!(child instanceof TextView)) {
                continue;
            }
            TextView bubble = (TextView) child;
            Object reactionTag = bubble.getTag(TAG_REACTION_ITEM);
            if (!(reactionTag instanceof WidgetReaction)) {
                continue;
            }
            WidgetReaction reaction = (WidgetReaction) reactionTag;
            applyReactionBubbleState(activity, bubble, reaction);
        }
    }

    private static void applyReactionBubbleState(Activity activity, TextView bubble, WidgetReaction reaction) {
        boolean selected = reaction.isSelected(activity.getApplicationContext());
        bubble.setSelected(selected);
        bubble.setBackground(createBubbleBackground(activity, selected));
    }

    private static Drawable createBubbleBackground(Activity activity, boolean active) {
        int backgroundColor = resolveThemeColor(activity, "colorCardBackground", android.R.attr.colorBackground);
        int accentColor = resolveThemeColor(activity, "colorAccentSupport", android.R.attr.colorAccent);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(activity, 12));
        shape.setColor(active ? applyAlpha(accentColor, 0.22f) : backgroundColor);
        shape.setStroke(dp(activity, 1), applyAlpha(accentColor, active ? 0.65f : 0.35f));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new RippleDrawable(ColorStateList.valueOf(applyAlpha(accentColor, 0.18f)), shape, null);
        }
        return shape;
    }

    private static int resolveTextColor(Activity activity) {
        return resolveThemeColor(activity, "colorTextMeta", android.R.attr.textColorPrimary);
    }

    private static int resolveThemeColor(Activity activity, String hostAttrName, int fallbackAttr) {
        int fallback = resolveAttrColor(activity, fallbackAttr, Color.WHITE);
        int hostAttr = activity.getResources().getIdentifier(hostAttrName, "attr", activity.getPackageName());
        if (hostAttr == 0) {
            return fallback;
        }
        return resolveAttrColor(activity, hostAttr, fallback);
    }

    private static int resolveAttrColor(Activity activity, int attr, int fallback) {
        android.content.res.TypedArray typedArray = activity.obtainStyledAttributes(new int[] {attr});
        int color = typedArray.getColor(0, fallback);
        typedArray.recycle();
        return color;
    }

    private static int applyAlpha(int color, float alpha) {
        int baseAlpha = Math.round(255f * alpha);
        return (color & 0x00ffffff) | (baseAlpha << 24);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, activity.getResources().getDisplayMetrics()));
    }
}
