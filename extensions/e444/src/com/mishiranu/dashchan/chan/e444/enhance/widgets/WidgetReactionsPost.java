package com.mishiranu.dashchan.chan.e444.enhance.widgets;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
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
import chan.util.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.mishiranu.dashchan.chan.e444.E444ChanLocator;
import com.mishiranu.dashchan.chan.e444.E444JsonUtils;
import com.mishiranu.dashchan.chan.e444.E444Model;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceWidget;
import com.google.android.flexbox.AlignItems;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.flexbox.JustifyContent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WidgetReactionsPost implements EnhanceWidget {
    public static final String CONTAINER_TAG = "e444_post_injected_reactions_container";
    private static final int TAG_REACTION_ITEM = 0xE4442101;
    private static final Map<String, String> TOGGLED_REACTION_BY_POST = new ConcurrentHashMap<>();
    private static final String PREFS_NAME = "e444_reactions";
    private static final String PREFS_KEY_TOGGLED = "toggled";
    private static final Object TOGGLED_REACTIONS_LOCK = new Object();
    private static volatile boolean toggledReactionsLoaded;
    private final String boardName;
    private final int postNumber;
    private final String postStateKey;
    private final List<ReactionItem> reactions = new ArrayList<>();

    public WidgetReactionsPost(List<E444Model.Reaction> reactionsJson, E444ChanLocator locator, String boardName, int postNumber) {
        this.boardName = boardName;
        this.postNumber = postNumber;
        this.postStateKey = WidgetReaction.buildPostStateKey(boardName, postNumber);
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

    private static FlexboxLayout obtainOrCreateFlexbox(Activity activity, LinearLayout container) {
        View existing = container.getChildCount() > 0 ? container.getChildAt(0) : null;
        if (existing instanceof FlexboxLayout) {
            return (FlexboxLayout) existing;
        }
        container.removeAllViews();
        FlexboxLayout flexbox = createFlexbox(activity);
        container.addView(
                flexbox,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return flexbox;
    }

    private static TextView obtainOrCreateBubble(Activity activity, FlexboxLayout flexbox, int index) {
        View existing = index < flexbox.getChildCount() ? flexbox.getChildAt(index) : null;
        if (existing instanceof TextView) {
            return (TextView) existing;
        }
        TextView bubble = createBubble(activity);
        if (index < flexbox.getChildCount()) {
            flexbox.removeViewAt(index);
            flexbox.addView(bubble, index, createBubbleLayoutParams(activity));
        } else {
            flexbox.addView(bubble, createBubbleLayoutParams(activity));
        }
        return bubble;
    }

    private static FlexboxLayout.LayoutParams createBubbleLayoutParams(Activity activity) {
        FlexboxLayout.LayoutParams layoutParams = new FlexboxLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.rightMargin = dp(activity, 2);
        layoutParams.bottomMargin = dp(activity, 2);
        return layoutParams;
    }

    private void bindReactionBubble(
            Activity activity,
            TextView bubble,
            ReactionItem reactionItem,
            Runnable refreshSelectionState) {
        bubble.setTag(TAG_REACTION_ITEM, reactionItem);
        bubble.setGravity(Gravity.CENTER_VERTICAL);
        bubble.setMinHeight(dp(activity, 24));
        bubble.setMinWidth(0);
        bubble.setPadding(dp(activity, 6), dp(activity, 4), dp(activity, 6), dp(activity, 4));
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
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
        ensureToggledReactionsLoaded(context);
        return iconName.equals(TOGGLED_REACTION_BY_POST.get(postStateKey));
    }

    static void updateSelection(
            Context context,
            String postStateKey,
            String iconName,
            WidgetReaction.SelectionMode mode) {
        ensureToggledReactionsLoaded(context);
        String activeIcon = TOGGLED_REACTION_BY_POST.get(postStateKey);
        if (mode == WidgetReaction.SelectionMode.SET) {
            TOGGLED_REACTION_BY_POST.put(postStateKey, iconName);
        } else if (iconName.equals(activeIcon)) {
            TOGGLED_REACTION_BY_POST.remove(postStateKey);
        } else {
            TOGGLED_REACTION_BY_POST.put(postStateKey, iconName);
        }
        persistToggledReactions(context);
    }

    private static void ensureToggledReactionsLoaded(Context context) {
        if (toggledReactionsLoaded) {
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
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            toggledReactionsLoaded = true;
        }
    }

    private static void persistToggledReactions(Context context) {
        synchronized (TOGGLED_REACTIONS_LOCK) {
            String serialized = E444JsonUtils.toJson(TOGGLED_REACTION_BY_POST);
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(PREFS_KEY_TOGGLED, serialized)
                    .apply();
        }
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
