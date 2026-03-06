package com.mishiranu.dashchan.chan.e444.enhance.widgets;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import chan.util.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.mishiranu.dashchan.chan.e444.E444ChanLocator;
import com.mishiranu.dashchan.chan.e444.E444JsonUtils;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceReflection;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceWidget;
import com.mishiranu.dashchan.chan.e444.enhance.TaskCallback;
import com.mishiranu.dashchan.chan.e444.enhance.tasks.TaskReadPost;
import com.mishiranu.dashchan.chan.e444.enhance.tasks.TaskSendPollVote;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WidgetPoll implements EnhanceWidget {
    public static final String CONTAINER_TAG = "e444_post_injected_poll_container";
    private static final int TAG_ANSWER_VIEW = 0xE4442401;
    private static final int TAG_RESULT_VIEW = 0xE4442402;
    private static final int TAG_FILL_VIEW = 0xE4442403;
    private static final int TAG_LAST_LEVEL = 0xE4442404;
    private static final int TAG_WIDTH_ANIMATOR = 0xE4442405;
    private static final int TAG_ROW_KEY = 0xE4442406;
    private static final int TAG_ANIM_TARGET_LEVEL = 0xE4442407;
    private static final int TAG_VOTE_INDEX = 0xE4442408;

    private static final long BAR_FILL_ANIMATION_DURATION_MS = 420L;
    private static final int MAX_LEVEL = 10000;
    private static final Map<String, Integer> TOGGLED_VOTE_INDEX_BY_POST = new ConcurrentHashMap<>();
    private static final String PREFS_NAME = "e444_polls";
    private static final String PREFS_KEY_TOGGLED = "toggled_vote_index";
    private static final Object TOGGLED_VOTES_LOCK = new Object();
    private static volatile boolean toggledVotesLoaded;

    private final E444ChanLocator locator;
    private final String boardName;
    private final int postNumber;
    private final String postStateKey;
    private final List<PollItem> pollItems;
    private final int totalVotes;

    private static final class PollItem {
        private final String answer;
        private final int votes;

        private PollItem(String answer, int votes) {
            this.answer = answer;
            this.votes = votes;
        }
    }

    public WidgetPoll(
            E444ChanLocator locator,
            String boardName,
            int postNumber,
            List<String> answers,
            List<Integer> pollResultsExact) {
        this.locator = locator;
        this.boardName = boardName;
        this.postNumber = postNumber;
        this.postStateKey = WidgetReaction.buildPostStateKey(boardName, postNumber);
        ArrayList<PollItem> parsedPollItems = new ArrayList<>();
        int parsedTotalVotes = 0;
        for (int i = 0; i < answers.size(); i++) {
            String answer = StringUtils.clearHtml(StringUtils.emptyIfNull(answers.get(i))).trim();
            int votes = pollResultsExact.get(i);
            parsedTotalVotes += votes;
            parsedPollItems.add(new PollItem(answer, votes));
        }
        this.pollItems = parsedPollItems;
        this.totalVotes = parsedTotalVotes;
    }

    public boolean isEmpty() {
        return pollItems.isEmpty();
    }

    @Override
    public String getContainerTag() {
        return CONTAINER_TAG;
    }

    @Override
    public ViewGroup.LayoutParams createLayoutParams(Activity activity) {
        LinearLayout.LayoutParams layoutParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.leftMargin = dp(activity, 4);
        layoutParams.rightMargin = dp(activity, 12);
        layoutParams.topMargin = dp(activity, 5);
        layoutParams.bottomMargin = 0;
        return layoutParams;
    }

    @Override
    public void inject(Activity activity, ViewGroup postRoot) {
        LinearLayout container = obtainOrCreateContainer(activity, postRoot);
        for (int i = 0; i < pollItems.size(); i++) {
            PollItem pollItem = pollItems.get(i);
            FrameLayout pollBar = obtainOrCreatePollBar(activity, container, i);
            bindPollBar(activity, pollBar, pollItem, totalVotes, i);
        }
        while (container.getChildCount() > pollItems.size()) {
            container.removeViewAt(container.getChildCount() - 1);
        }
        applyPollSelectionState(activity, container);
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

    private static FrameLayout obtainOrCreatePollBar(Activity activity, LinearLayout container, int index) {
        View existing = index < container.getChildCount() ? container.getChildAt(index) : null;
        if (existing instanceof FrameLayout
                && ((FrameLayout) existing).getTag(TAG_ANSWER_VIEW) instanceof TextView
                && ((FrameLayout) existing).getTag(TAG_RESULT_VIEW) instanceof TextView
                && ((FrameLayout) existing).getTag(TAG_FILL_VIEW) instanceof View) {
            return (FrameLayout) existing;
        }
        FrameLayout pollBar = createPollBar(activity);
        if (index < container.getChildCount()) {
            container.removeViewAt(index);
            container.addView(pollBar, index, createPollBarLayoutParams(activity));
        } else {
            container.addView(pollBar, createPollBarLayoutParams(activity));
        }
        return pollBar;
    }

    private static FrameLayout createPollBar(Activity activity) {
        int accentColor = resolveRequiredThemeColor(activity, "colorAccentSupport");

        FrameLayout pollBar = new FrameLayout(activity);
        pollBar.setClickable(true);
        pollBar.setFocusable(true);
        pollBar.setOnClickListener(v -> {});
        pollBar.setBackground(createPollBarBackground(activity, false));

        View fillView = new View(activity);
        GradientDrawable fillShape = new GradientDrawable();
        fillShape.setShape(GradientDrawable.RECTANGLE);
        fillShape.setCornerRadius(dp(activity, 5));
        fillShape.setColor(applyAlpha(accentColor, 0.24f));
        fillView.setBackground(fillShape);
        fillView.setPivotX(0f);
        FrameLayout.LayoutParams fillLayoutParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        pollBar.addView(fillView, fillLayoutParams);

        LinearLayout contentLayout = new LinearLayout(activity);
        contentLayout.setOrientation(LinearLayout.HORIZONTAL);
        contentLayout.setGravity(Gravity.CENTER_VERTICAL);
        contentLayout.setPadding(dp(activity, 8), dp(activity, 6), dp(activity, 8), dp(activity, 6));
        pollBar.addView(
                contentLayout,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView answerView = new TextView(activity);
        answerView.setSingleLine(true);
        answerView.setEllipsize(TextUtils.TruncateAt.END);
        answerView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        LinearLayout.LayoutParams answerLayoutParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        answerLayoutParams.rightMargin = dp(activity, 8);
        contentLayout.addView(answerView, answerLayoutParams);

        TextView resultView = new TextView(activity);
        resultView.setSingleLine(true);
        resultView.setGravity(Gravity.END);
        resultView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        contentLayout.addView(resultView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        pollBar.setTag(TAG_ANSWER_VIEW, answerView);
        pollBar.setTag(TAG_RESULT_VIEW, resultView);
        pollBar.setTag(TAG_FILL_VIEW, fillView);
        return pollBar;
    }

    private static LinearLayout.LayoutParams createPollBarLayoutParams(Activity activity) {
        LinearLayout.LayoutParams layoutParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.bottomMargin = dp(activity, 5);
        return layoutParams;
    }

    private void bindPollBar(
            Activity activity,
            FrameLayout pollBar,
            PollItem pollItem,
            int totalVotes,
            int voteIndex) {
        TextView answerView = (TextView) pollBar.getTag(TAG_ANSWER_VIEW);
        TextView resultView = (TextView) pollBar.getTag(TAG_RESULT_VIEW);
        int percentage = totalVotes > 0 ? Math.round(pollItem.votes * 100f / totalVotes) : 0;
        answerView.setText(pollItem.answer);
        answerView.setTextColor(resolveRequiredThemeColor(activity, "colorTextPost"));
        resultView.setText(formatResult(percentage, pollItem.votes));
        resultView.setTextColor(resolveRequiredThemeColor(activity, "colorTextMeta"));
        pollBar.setTag(TAG_VOTE_INDEX, voteIndex);

        String rowKey = boardName + ":" + postNumber + ":" + voteIndex;
        int targetLevel = calculateLevel(pollItem.votes, totalVotes);
        applyPollBarProgress(pollBar, rowKey, targetLevel);

        pollBar.setOnClickListener(v -> submitVote(activity, pollBar, voteIndex));
    }

    private static void applyPollBarProgress(FrameLayout pollBar, String rowKey, int targetLevel) {
        View fillView = (View) pollBar.getTag(TAG_FILL_VIEW);

        Object oldRowKey = pollBar.getTag(TAG_ROW_KEY);
        Object runningAnimator = pollBar.getTag(TAG_WIDTH_ANIMATOR);
        Object runningTargetTag = pollBar.getTag(TAG_ANIM_TARGET_LEVEL);
        Integer runningTarget = runningTargetTag instanceof Integer ? (Integer) runningTargetTag : null;
        boolean sameRow = rowKey.equals(oldRowKey);
        int currentLevel = scaleToLevel(fillView.getScaleX());

        if (!sameRow) {
            cancelRunningWidthAnimation(pollBar);
            fillView.setScaleX(levelToScaleX(targetLevel));
            pollBar.setTag(TAG_ROW_KEY, rowKey);
            pollBar.setTag(TAG_LAST_LEVEL, targetLevel);
            return;
        }

        if (sameRow && runningAnimator instanceof ValueAnimator && runningTarget != null && runningTarget == targetLevel) {
            return;
        }

        final int startLevelValue = currentLevel;
        final boolean canAnimate = startLevelValue != targetLevel;

        pollBar.setTag(TAG_ROW_KEY, rowKey);
        float targetScaleX = levelToScaleX(targetLevel);
        if (!canAnimate) {
            cancelRunningWidthAnimation(pollBar);
            fillView.setScaleX(targetScaleX);
            pollBar.setTag(TAG_LAST_LEVEL, targetLevel);
            return;
        }
        float startScaleX = levelToScaleX(startLevelValue);
        animateFillScale(pollBar, fillView, startScaleX, targetScaleX, targetLevel);
        pollBar.setTag(TAG_LAST_LEVEL, startLevelValue);
    }

    private void submitVote(Activity activity, FrameLayout pollBar, int voteIndex) {
        pollBar.setEnabled(false);
        EnhanceReflection.submitTask(
                locator,
                new TaskSendPollVote(boardName, postNumber, voteIndex),
                new TaskCallback<Void>() {
                    @Override
                    public void accept(Void result) {
                        updateSelection(activity.getApplicationContext(), postStateKey, voteIndex);
                        if (pollBar.getParent() instanceof LinearLayout) {
                            applyPollSelectionState(activity, (LinearLayout) pollBar.getParent());
                        }
                        refreshPostAfterVote(activity);
                        pollBar.setEnabled(true);
                    }
                },
                new TaskCallback<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        pollBar.setEnabled(true);
                        EnhanceReflection.showError(throwable);
                    }
                });
    }

    private void refreshPostAfterVote(Activity activity) {
        EnhanceReflection.submitTask(
                locator,
                new TaskReadPost(boardName, postNumber),
                new TaskCallback<chan.content.model.Post>() {
                    @Override
                    public void accept(chan.content.model.Post post) {
                        EnhanceReflection.syncCurrentActivitiesNow();
                        activity.getWindow()
                                .getDecorView()
                                .postDelayed(EnhanceReflection::syncCurrentActivitiesNow, 120L);
                    }
                },
                new TaskCallback<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {}
                });
    }

    private static String formatResult(int percentage, int votes) {
        return percentage + "% (" + votes + ")";
    }

    private void applyPollSelectionState(Activity activity, LinearLayout container) {
        Context context = activity.getApplicationContext();
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (!(child instanceof FrameLayout)) {
                continue;
            }
            FrameLayout pollBar = (FrameLayout) child;
            Object voteIndexTag = pollBar.getTag(TAG_VOTE_INDEX);
            if (!(voteIndexTag instanceof Integer)) {
                continue;
            }
            boolean selected = isSelected(context, postStateKey, (Integer) voteIndexTag);
            applyPollBarSelectionState(activity, pollBar, selected);
        }
    }

    private static void applyPollBarSelectionState(Activity activity, FrameLayout pollBar, boolean selected) {
        pollBar.setSelected(selected);
        pollBar.setBackground(createPollBarBackground(activity, selected));
        Object fillTag = pollBar.getTag(TAG_FILL_VIEW);
        if (fillTag instanceof View) {
            View fillView = (View) fillTag;
            Drawable fillDrawable = fillView.getBackground();
            if (fillDrawable instanceof GradientDrawable) {
                int accentColor = resolveRequiredThemeColor(activity, "colorAccentSupport");
                ((GradientDrawable) fillDrawable).setColor(applyAlpha(accentColor, selected ? 0.36f : 0.24f));
            }
        }
    }

    private static boolean isSelected(Context context, String postStateKey, int voteIndex) {
        ensureToggledVotesLoaded(context);
        Integer selectedVoteIndex = TOGGLED_VOTE_INDEX_BY_POST.get(postStateKey);
        return selectedVoteIndex != null && selectedVoteIndex == voteIndex;
    }

    private static void updateSelection(Context context, String postStateKey, int voteIndex) {
        ensureToggledVotesLoaded(context);
        TOGGLED_VOTE_INDEX_BY_POST.put(postStateKey, voteIndex);
        persistToggledVotes(context);
    }

    private static void ensureToggledVotesLoaded(Context context) {
        if (toggledVotesLoaded) {
            return;
        }
        synchronized (TOGGLED_VOTES_LOCK) {
            if (toggledVotesLoaded) {
                return;
            }
            SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String serialized = preferences.getString(PREFS_KEY_TOGGLED, null);
            if (!StringUtils.isEmpty(serialized)) {
                try {
                    Map<String, Integer> root =
                            E444JsonUtils.fromJson(serialized, new TypeReference<Map<String, Integer>>() {});
                    if (root != null) {
                        for (Map.Entry<String, Integer> entry : root.entrySet()) {
                            String postKey = entry.getKey();
                            Integer selectedVoteIndex = entry.getValue();
                            if (!StringUtils.isEmpty(postKey) && selectedVoteIndex != null && selectedVoteIndex >= 0) {
                                TOGGLED_VOTE_INDEX_BY_POST.put(postKey, selectedVoteIndex);
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            toggledVotesLoaded = true;
        }
    }

    private static void persistToggledVotes(Context context) {
        synchronized (TOGGLED_VOTES_LOCK) {
            String serialized = E444JsonUtils.toJson(TOGGLED_VOTE_INDEX_BY_POST);
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(PREFS_KEY_TOGGLED, serialized)
                    .apply();
        }
    }

    private static Drawable createPollBarBackground(Activity activity, boolean selected) {
        int accentColor = resolveRequiredThemeColor(activity, "colorAccentSupport");
        int trackColor = resolveRequiredThemeColor(activity, "colorWindowBackground");
        int strokeWidth = dp(activity, selected ? 2 : 1);
        int radius = dp(activity, 5);

        GradientDrawable trackShape = new GradientDrawable();
        trackShape.setShape(GradientDrawable.RECTANGLE);
        trackShape.setCornerRadius(radius);
        trackShape.setColor(selected ? applyAlpha(accentColor, 0.1f) : trackColor);
        trackShape.setStroke(strokeWidth, applyAlpha(accentColor, 0.35f));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new RippleDrawable(
                    ColorStateList.valueOf(applyAlpha(accentColor, selected ? 0.24f : 0.18f)),
                    trackShape,
                    null);
        }
        return trackShape;
    }

    private static void animateFillScale(
            FrameLayout pollBar, View fillView, float startScaleX, float targetScaleX, int targetLevel) {
        cancelRunningWidthAnimation(pollBar);
        fillView.setScaleX(startScaleX);
        ValueAnimator animator = ValueAnimator.ofFloat(startScaleX, targetScaleX);
        animator.setDuration(BAR_FILL_ANIMATION_DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> fillView.setScaleX((Float) animation.getAnimatedValue()));
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (pollBar.getTag(TAG_WIDTH_ANIMATOR) == animation) {
                    pollBar.setTag(TAG_WIDTH_ANIMATOR, null);
                    pollBar.setTag(TAG_ANIM_TARGET_LEVEL, null);
                    pollBar.setTag(TAG_LAST_LEVEL, targetLevel);
                }
                fillView.setScaleX(targetScaleX);
            }

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                if (pollBar.getTag(TAG_WIDTH_ANIMATOR) == animation) {
                    pollBar.setTag(TAG_WIDTH_ANIMATOR, null);
                    pollBar.setTag(TAG_ANIM_TARGET_LEVEL, null);
                }
            }
        });
        pollBar.setTag(TAG_WIDTH_ANIMATOR, animator);
        pollBar.setTag(TAG_ANIM_TARGET_LEVEL, targetLevel);
        animator.start();
    }

    private static void cancelRunningWidthAnimation(FrameLayout pollBar) {
        Object animatorTag = pollBar.getTag(TAG_WIDTH_ANIMATOR);
        if (animatorTag instanceof ValueAnimator) {
            ((ValueAnimator) animatorTag).cancel();
        }
        pollBar.setTag(TAG_WIDTH_ANIMATOR, null);
        pollBar.setTag(TAG_ANIM_TARGET_LEVEL, null);
    }

    private static float levelToScaleX(int level) {
        return Math.max(0f, Math.min(1f, level / (float) MAX_LEVEL));
    }

    private static int scaleToLevel(float scaleX) {
        return Math.round(Math.max(0f, Math.min(1f, scaleX)) * MAX_LEVEL);
    }

    private static int calculateLevel(int votes, int totalVotes) {
        float progress = totalVotes > 0 ? Math.max(0f, Math.min(1f, votes / (float) totalVotes)) : 0f;
        return Math.round(progress * MAX_LEVEL);
    }

    private static int resolveRequiredThemeColor(Activity activity, String hostAttrName) {
        int hostAttr = activity.getResources().getIdentifier(hostAttrName, "attr", activity.getPackageName());
        if (hostAttr == 0) {
            throw new IllegalStateException("Required theme attr is missing: " + hostAttrName);
        }
        return resolveRequiredAttrColor(activity, hostAttr);
    }

    private static int resolveRequiredAttrColor(Activity activity, int attr) {
        android.content.res.TypedArray typedArray = activity.obtainStyledAttributes(new int[] {attr});
        if (!typedArray.hasValue(0)) {
            typedArray.recycle();
            throw new IllegalStateException("Required attr has no value: " + attr);
        }
        ColorStateList colorStateList = typedArray.getColorStateList(0);
        typedArray.recycle();
        if (colorStateList == null) {
            throw new IllegalStateException("Required attr is not a color value: " + attr);
        }
        return colorStateList.getDefaultColor();
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
