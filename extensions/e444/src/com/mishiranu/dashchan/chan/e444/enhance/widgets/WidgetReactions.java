package com.mishiranu.dashchan.chan.e444.enhance.widgets;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
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
import android.widget.Toast;
import chan.text.JsonSerial;
import chan.text.ParseException;
import chan.util.StringUtils;
import com.google.android.flexbox.AlignItems;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.flexbox.JustifyContent;
import com.mishiranu.dashchan.chan.e444.E444ChanLocator;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceReflection;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceWidget;
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
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import lombok.EqualsAndHashCode;
import org.json.JSONException;
import org.json.JSONObject;

public final class WidgetReactions implements EnhanceWidget {
    private static final String TAG = "WidgetReactions";
    public static final String CONTAINER_TAG = "e444_post_injected_reactions_container";
    private static final Map<LinearLayout, List<Reaction>> BOUND_REACTIONS =
            Collections.synchronizedMap(new WeakHashMap<LinearLayout, List<Reaction>>());
    private static final Map<String, Bitmap> ICON_BITMAPS = new ConcurrentHashMap<>();
    private static final Map<String, List<WeakReference<TextView>>> ICON_WAITERS = new ConcurrentHashMap<>();
    private static final java.util.Set<String> ICON_LOADING =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Map<String, String> TOGGLED_REACTION_BY_POST = new ConcurrentHashMap<>();
    private static final String PREFS_NAME = "e444_reactions";
    private static final String PREFS_KEY_TOGGLED = "toggled";
    private static final Object TOGGLED_REACTIONS_LOCK = new Object();
    private static volatile boolean toggledReactionsLoaded;
    private static final int ICON_SIZE_DP = 14;

    private final E444ChanLocator locator;
    private final List<Reaction> reactions;
    private String boardName;
    private int postNumber;
    private String postStateKey;

    @EqualsAndHashCode
    private static final class Reaction {
        public final String icon;
        public final int count;

        private Reaction(String icon, int count) {
            this.icon = icon;
            this.count = count;
        }

        private static Reaction parse(JsonSerial.Reader reader) throws IOException, ParseException {
            String icon = "";
            int count = 0;
            reader.startObject();
            while (!reader.endStruct()) {
                switch (reader.nextName()) {
                    case "icon": {
                        icon = reader.nextString();
                        break;
                    }
                    case "count": {
                        count = reader.nextInt();
                        break;
                    }
                    default: {
                        reader.skip();
                        break;
                    }
                }
            }
            return new Reaction(icon, count);
        }
    }

    public WidgetReactions(JsonSerial.Reader reader, E444ChanLocator locator) throws IOException, ParseException {
        this.locator = locator;
        ArrayList<Reaction> parsedReactions = new ArrayList<>();
        reader.startArray();
        while (!reader.endStruct()) {
            parsedReactions.add(Reaction.parse(reader));
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
        int horizontal = dp(activity, 4);
        LinearLayout.LayoutParams layoutParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.leftMargin = horizontal;
        layoutParams.topMargin = dp(activity, 2);
        layoutParams.rightMargin = horizontal;
        layoutParams.bottomMargin = 0;
        return layoutParams;
    }

    public void setPostContext(String boardName, int postNumber) {
        this.boardName = boardName;
        this.postNumber = postNumber;
        postStateKey = createPostStateKey(boardName, postNumber);
    }

    @Override
    public void inject(Activity activity, ViewGroup postRoot) {
        ensureToggledReactionsLoaded(activity.getApplicationContext());
        LinearLayout reactionsContainer = obtainOrMoveContainer(activity, postRoot);
        List<Reaction> oldReactions = BOUND_REACTIONS.get(reactionsContainer);
        if (oldReactions != null && oldReactions.equals(reactions)) {
            ensureIconsRequested(activity, reactionsContainer);
            return;
        }
        applyReactions(activity, reactionsContainer);
        BOUND_REACTIONS.put(reactionsContainer, reactions);
    }

    private void applyReactions(Activity activity, LinearLayout reactionsContainer) {
        FlexboxLayout flexbox = obtainFlexbox(activity, reactionsContainer);
        for (int i = 0; i < reactions.size(); i++) {
            final Reaction reaction = reactions.get(i);
            TextView bubble = obtainOrCreateBubble(activity, flexbox, i);
            configureBubble(activity, bubble, reaction);
        }
        while (flexbox.getChildCount() > reactions.size()) {
            flexbox.removeViewAt(flexbox.getChildCount() - 1);
        }
    }

    private static FlexboxLayout obtainFlexbox(Activity activity, LinearLayout reactionsContainer) {
        if (reactionsContainer.getChildCount() == 1 && reactionsContainer.getChildAt(0) instanceof FlexboxLayout) {
            return (FlexboxLayout) reactionsContainer.getChildAt(0);
        }
        reactionsContainer.removeAllViews();
        FlexboxLayout flexbox = new FlexboxLayout(activity);
        flexbox.setFlexDirection(FlexDirection.ROW);
        flexbox.setFlexWrap(FlexWrap.WRAP);
        flexbox.setJustifyContent(JustifyContent.FLEX_START);
        flexbox.setAlignItems(AlignItems.FLEX_START);
        reactionsContainer.addView(
                flexbox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return flexbox;
    }

    private TextView obtainOrCreateBubble(Activity activity, FlexboxLayout flexbox, int index) {
        View existing = index < flexbox.getChildCount() ? flexbox.getChildAt(index) : null;
        if (existing instanceof TextView) {
            return (TextView) existing;
        }
        TextView bubble = new TextView(activity);
        if (index < flexbox.getChildCount()) {
            flexbox.removeViewAt(index);
            flexbox.addView(bubble, index, createBubbleLayoutParams(activity));
        } else {
            flexbox.addView(bubble, createBubbleLayoutParams(activity));
        }
        return bubble;
    }

    private static FlexboxLayout.LayoutParams createBubbleLayoutParams(Activity activity) {
        FlexboxLayout.LayoutParams buttonLayoutParams = new FlexboxLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonLayoutParams.rightMargin = dp(activity, 2);
        buttonLayoutParams.bottomMargin = dp(activity, 2);
        return buttonLayoutParams;
    }

    private void configureBubble(Activity activity, TextView bubble, Reaction reaction) {
        bubble.setGravity(Gravity.CENTER_VERTICAL);
        bubble.setMinHeight(dp(activity, 24));
        bubble.setPadding(dp(activity, 6), dp(activity, 4), dp(activity, 6), dp(activity, 4));
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        bubble.setTextColor(resolveTextColor(activity));
        bubble.setText(Integer.toString(reaction.count));
        bindIcon(bubble, reaction.icon);
        applyBubbleState(activity, bubble, reaction.icon);
        bubble.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                performReaction(activity, bubble, reaction.icon);
            }
        });
    }

    private void performReaction(final Activity activity, final TextView bubble, final String iconName) {
        final String targetBoardName = boardName;
        final int targetPostNumber = postNumber;
        if (StringUtils.isEmpty(targetBoardName) || targetPostNumber <= 0) {
            Toast.makeText(activity, "Unable to resolve reaction target", Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        bubble.setEnabled(false);
        EnhanceReflection.submitTask(
                locator,
                new TaskSendReaction(targetBoardName, targetPostNumber, iconName),
                result -> {
                    ensureToggledReactionsLoaded(activity.getApplicationContext());
                    if (postStateKey != null) {
                        String activeIcon = TOGGLED_REACTION_BY_POST.get(postStateKey);
                        if (iconName.equals(activeIcon)) {
                            TOGGLED_REACTION_BY_POST.remove(postStateKey);
                        } else {
                            TOGGLED_REACTION_BY_POST.put(postStateKey, iconName);
                        }
                        persistToggledReactions(activity.getApplicationContext());
                    }
                    applyBubbleStatesForContainer(activity, bubble);
                    EnhanceReflection.submitTask(
                            locator,
                            new TaskReadPost(targetBoardName, targetPostNumber),
                            post -> {
                                bubble.setEnabled(true);
                                if (!EnhanceReflection.requestThreadPostRebind(
                                        activity, targetBoardName, targetPostNumber)) {
                                    EnhanceReflection.showError(
                                            new IllegalStateException("Unable to apply single post refresh"));
                                }
                            },
                            throwable -> {
                                bubble.setEnabled(true);
                                EnhanceReflection.showError(throwable);
                            });
                },
                throwable -> {
                    bubble.setEnabled(true);
                    EnhanceReflection.showError(throwable);
                });
    }

    private void bindIcon(TextView bubble, String iconName) {
        bubble.setTag(iconName);
        Bitmap bitmap = ICON_BITMAPS.get(iconName);
        if (bitmap != null) {
            applyBubbleIcon(bubble, bitmap);
        } else {
            bubble.setCompoundDrawables(null, null, null, null);
            requestIconLoad(bubble, iconName);
        }
    }

    private void requestIconLoad(TextView bubble, String iconName) {
        ICON_WAITERS
                .computeIfAbsent(
                        iconName, key -> Collections.synchronizedList(new ArrayList<WeakReference<TextView>>()))
                .add(new WeakReference<>(bubble));
        if (!ICON_LOADING.add(iconName)) {
            return;
        }
        EnhanceReflection.submitTask(
                locator,
                new TaskReadIcon(iconName),
                bitmap -> {
                    ICON_BITMAPS.put(iconName, bitmap);
                    deliverLoadedIcon(iconName, bitmap);
                },
                throwable -> deliverLoadedIcon(iconName, null));
    }

    private static void deliverLoadedIcon(String iconName, Bitmap bitmap) {
        List<WeakReference<TextView>> waiters = ICON_WAITERS.remove(iconName);
        ICON_LOADING.remove(iconName);
        if (waiters == null || bitmap == null) {
            return;
        }
        for (int i = 0; i < waiters.size(); i++) {
            TextView bubble = waiters.get(i).get();
            if (bubble != null && iconName.equals(bubble.getTag())) {
                applyBubbleIcon(bubble, bitmap);
            }
        }
    }

    private static void applyBubbleIcon(TextView bubble, Bitmap bitmap) {
        Drawable drawable = new BitmapDrawable(bubble.getResources(), bitmap);
        int size = dp(bubble, ICON_SIZE_DP);
        drawable.setBounds(0, 0, size, size);
        bubble.setCompoundDrawablePadding(dp(bubble, 4));
        bubble.setCompoundDrawables(drawable, null, null, null);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, activity.getResources().getDisplayMetrics()));
    }

    private static int dp(View view, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, view.getResources().getDisplayMetrics()));
    }

    private void ensureIconsRequested(Activity activity, LinearLayout reactionsContainer) {
        if (reactionsContainer.getChildCount() != 1 || !(reactionsContainer.getChildAt(0) instanceof FlexboxLayout)) {
            return;
        }
        FlexboxLayout flexbox = (FlexboxLayout) reactionsContainer.getChildAt(0);
        for (int i = 0; i < flexbox.getChildCount(); i++) {
            View child = flexbox.getChildAt(i);
            if (!(child instanceof TextView)) {
                continue;
            }
            TextView bubble = (TextView) child;
            Object tag = bubble.getTag();
            if (!(tag instanceof String)) {
                continue;
            }
            String iconName = (String) tag;
            applyBubbleState(activity, bubble, iconName);
            Bitmap bitmap = ICON_BITMAPS.get(iconName);
            if (bitmap != null) {
                if (bubble.getCompoundDrawables()[0] == null) {
                    applyBubbleIcon(bubble, bitmap);
                }
            } else if (bubble.getCompoundDrawables()[0] == null) {
                requestIconLoad(bubble, iconName);
            }
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

    private void applyBubbleState(Activity activity, TextView bubble, String iconName) {
        boolean active = isReactionToggled(iconName);
        bubble.setBackground(createBubbleBackground(activity, active));
        bubble.setSelected(active);
    }

    private void applyBubbleStatesForContainer(Activity activity, TextView bubble) {
        View parent = (View) bubble.getParent();
        if (!(parent instanceof FlexboxLayout)) {
            Object tag = bubble.getTag();
            if (tag instanceof String) {
                applyBubbleState(activity, bubble, (String) tag);
            }
            return;
        }
        FlexboxLayout flexbox = (FlexboxLayout) parent;
        for (int i = 0; i < flexbox.getChildCount(); i++) {
            View child = flexbox.getChildAt(i);
            if (!(child instanceof TextView)) {
                continue;
            }
            TextView reactionBubble = (TextView) child;
            Object tag = reactionBubble.getTag();
            if (tag instanceof String) {
                applyBubbleState(activity, reactionBubble, (String) tag);
            }
        }
    }

    private boolean isReactionToggled(String iconName) {
        String toggledIcon = postStateKey != null ? TOGGLED_REACTION_BY_POST.get(postStateKey) : null;
        return iconName.equals(toggledIcon);
    }

    private static String createPostStateKey(String boardName, int postNumber) {
        return boardName + ":" + postNumber;
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
                    JSONObject root = new JSONObject(serialized);
                    Iterator<String> keys = root.keys();
                    while (keys.hasNext()) {
                        String postKey = keys.next();
                        String icon = root.optString(postKey, null);
                        if (!StringUtils.isEmpty(icon)) {
                            TOGGLED_REACTION_BY_POST.put(postKey, icon);
                        }
                    }
                } catch (JSONException ignored) {
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
            JSONObject root = new JSONObject();
            for (Map.Entry<String, String> entry : TOGGLED_REACTION_BY_POST.entrySet()) {
                try {
                    root.put(entry.getKey(), entry.getValue());
                } catch (JSONException ignored) {
                    // Skip invalid key/value pair.
                }
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(PREFS_KEY_TOGGLED, root.toString())
                    .apply();
        }
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

    private static LinearLayout createReactionsContainer(Activity activity) {
        LinearLayout container = new LinearLayout(activity);
        container.setTag(CONTAINER_TAG);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.START);
        container.setClickable(false);
        container.setFocusable(false);
        return container;
    }

    private static LinearLayout obtainOrMoveContainer(Activity activity, ViewGroup postRoot) {
        View existingContainerView = postRoot.findViewWithTag(CONTAINER_TAG);
        LinearLayout container = existingContainerView instanceof LinearLayout
                ? (LinearLayout) existingContainerView
                : createReactionsContainer(activity);
        ViewGroup currentParent = container.getParent() instanceof ViewGroup ? (ViewGroup) container.getParent() : null;
        if (currentParent == postRoot) {
            return container;
        }
        if (currentParent != null) {
            currentParent.removeView(container);
        }
        postRoot.addView(container);
        return container;
    }

}
