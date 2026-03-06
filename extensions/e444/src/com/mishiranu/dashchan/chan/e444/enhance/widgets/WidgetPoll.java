package com.mishiranu.dashchan.chan.e444.enhance.widgets;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
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
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceReflection;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceWidget;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceWidgetUtils;
import com.mishiranu.dashchan.chan.e444.enhance.TaskCallback;
import com.mishiranu.dashchan.chan.e444.enhance.tasks.TaskSendPollVote;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private static final String PREFS_NAME = "e444_polls";
    private static final String PREFS_KEY_TOGGLED = "toggled_vote_index";
    private static final EnhanceWidgetUtils.PostSelectionStore<Integer> TOGGLED_VOTE_INDEX_BY_POST =
            new EnhanceWidgetUtils.PostSelectionStore<>(
                    PREFS_NAME,
                    PREFS_KEY_TOGGLED,
                    new TypeReference<Map<String, Integer>>() {},
                    value -> value != null && value >= 0 ? value : null);

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
        this.postStateKey = EnhanceWidgetUtils.buildPostStateKey(boardName, postNumber);
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
        layoutParams.leftMargin = EnhanceWidgetUtils.dp(activity, 4);
        layoutParams.rightMargin = EnhanceWidgetUtils.dp(activity, 12);
        layoutParams.topMargin = EnhanceWidgetUtils.dp(activity, 5);
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
        return EnhanceWidgetUtils.obtainOrCreateLinearContainer(activity, postRoot, CONTAINER_TAG);
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
        int accentColor = EnhanceWidgetUtils.resolveRequiredThemeColor(activity, "colorAccentSupport");

        FrameLayout pollBar = new FrameLayout(activity);
        pollBar.setClickable(true);
        pollBar.setFocusable(true);
        pollBar.setOnClickListener(v -> {});
        pollBar.setBackground(createPollBarBackground(activity, false));

        View fillView = new View(activity);
        GradientDrawable fillShape = new GradientDrawable();
        fillShape.setShape(GradientDrawable.RECTANGLE);
        fillShape.setCornerRadius(EnhanceWidgetUtils.dp(activity, 5));
        fillShape.setColor(EnhanceWidgetUtils.applyAlpha(accentColor, 0.24f));
        fillView.setBackground(fillShape);
        fillView.setPivotX(0f);
        FrameLayout.LayoutParams fillLayoutParams =
                new FrameLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        pollBar.addView(fillView, fillLayoutParams);

        LinearLayout contentLayout = new LinearLayout(activity);
        contentLayout.setOrientation(LinearLayout.HORIZONTAL);
        contentLayout.setGravity(Gravity.CENTER_VERTICAL);
        contentLayout.setPadding(
                EnhanceWidgetUtils.dp(activity, 8),
                EnhanceWidgetUtils.dp(activity, 6),
                EnhanceWidgetUtils.dp(activity, 8),
                EnhanceWidgetUtils.dp(activity, 6));
        pollBar.addView(
                contentLayout,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView answerView = new TextView(activity);
        answerView.setSingleLine(true);
        answerView.setEllipsize(TextUtils.TruncateAt.END);
        answerView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        LinearLayout.LayoutParams answerLayoutParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        answerLayoutParams.rightMargin = EnhanceWidgetUtils.dp(activity, 8);
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
        layoutParams.bottomMargin = EnhanceWidgetUtils.dp(activity, 5);
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
        answerView.setTextColor(EnhanceWidgetUtils.resolveRequiredThemeColor(activity, "colorTextPost"));
        resultView.setText(formatResult(percentage, pollItem.votes));
        resultView.setTextColor(EnhanceWidgetUtils.resolveRequiredThemeColor(activity, "colorTextMeta"));
        pollBar.setTag(TAG_VOTE_INDEX, voteIndex);

        String rowKey = boardName + ":" + postNumber + ":" + voteIndex;
        int targetLevel = calculateLevel(pollItem.votes, totalVotes);
        applyPollBarProgress(pollBar, rowKey, targetLevel);

        pollBar.setOnClickListener(v -> submitVote(activity, pollBar, voteIndex));
    }

    private static void applyPollBarProgress(FrameLayout pollBar, String rowKey, int targetLevel) {
        View fillView = (View) pollBar.getTag(TAG_FILL_VIEW);
        int barWidth = pollBar.getWidth();
        if (barWidth <= 0) {
            pollBar.post(() -> applyPollBarProgress(pollBar, rowKey, targetLevel));
            return;
        }

        Object oldRowKey = pollBar.getTag(TAG_ROW_KEY);
        Object runningAnimator = pollBar.getTag(TAG_WIDTH_ANIMATOR);
        Object runningTargetTag = pollBar.getTag(TAG_ANIM_TARGET_LEVEL);
        Integer runningTarget = runningTargetTag instanceof Integer ? (Integer) runningTargetTag : null;
        boolean sameRow = rowKey.equals(oldRowKey);
        int currentLevel = widthToLevel(getFillWidth(fillView), barWidth);
        int targetWidth = levelToWidth(targetLevel, barWidth);

        if (!sameRow) {
            cancelRunningWidthAnimation(pollBar);
            setFillWidth(fillView, targetWidth);
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
        if (!canAnimate) {
            cancelRunningWidthAnimation(pollBar);
            setFillWidth(fillView, targetWidth);
            pollBar.setTag(TAG_LAST_LEVEL, targetLevel);
            return;
        }
        int startWidth = levelToWidth(startLevelValue, barWidth);
        animateFillWidth(pollBar, fillView, startWidth, targetWidth, targetLevel);
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
                        EnhanceWidgetUtils.refreshPost(activity, locator, boardName, postNumber);
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
                int accentColor = EnhanceWidgetUtils.resolveRequiredThemeColor(activity, "colorAccentSupport");
                ((GradientDrawable) fillDrawable)
                        .setColor(EnhanceWidgetUtils.applyAlpha(accentColor, selected ? 0.36f : 0.24f));
            }
        }
    }

    private static boolean isSelected(Context context, String postStateKey, int voteIndex) {
        Integer selectedVoteIndex = TOGGLED_VOTE_INDEX_BY_POST.get(context, postStateKey);
        return selectedVoteIndex != null && selectedVoteIndex == voteIndex;
    }

    private static void updateSelection(Context context, String postStateKey, int voteIndex) {
        TOGGLED_VOTE_INDEX_BY_POST.put(context, postStateKey, voteIndex);
    }

    private static Drawable createPollBarBackground(Activity activity, boolean selected) {
        int accentColor = EnhanceWidgetUtils.resolveRequiredThemeColor(activity, "colorAccentSupport");
        int trackColor = EnhanceWidgetUtils.resolveRequiredThemeColor(activity, "colorWindowBackground");
        int strokeWidth = EnhanceWidgetUtils.dp(activity, 1);

        GradientDrawable trackShape = new GradientDrawable();
        trackShape.setShape(GradientDrawable.RECTANGLE);
        trackShape.setCornerRadius(EnhanceWidgetUtils.dp(activity, 5));
        trackShape.setColor(selected ? EnhanceWidgetUtils.applyAlpha(accentColor, 0.1f) : trackColor);
        trackShape.setStroke(strokeWidth, EnhanceWidgetUtils.applyAlpha(accentColor, selected ? 0.65f : 0.35f));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new RippleDrawable(
                    ColorStateList.valueOf(EnhanceWidgetUtils.applyAlpha(accentColor, selected ? 0.24f : 0.18f)),
                    trackShape,
                    null);
        }
        return trackShape;
    }

    private static void animateFillWidth(
            FrameLayout pollBar, View fillView, int startWidth, int targetWidth, int targetLevel) {
        cancelRunningWidthAnimation(pollBar);
        setFillWidth(fillView, startWidth);
        ValueAnimator animator = ValueAnimator.ofInt(startWidth, targetWidth);
        animator.setDuration(BAR_FILL_ANIMATION_DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> setFillWidth(fillView, (Integer) animation.getAnimatedValue()));
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (pollBar.getTag(TAG_WIDTH_ANIMATOR) == animation) {
                    pollBar.setTag(TAG_WIDTH_ANIMATOR, null);
                    pollBar.setTag(TAG_ANIM_TARGET_LEVEL, null);
                    pollBar.setTag(TAG_LAST_LEVEL, targetLevel);
                }
                setFillWidth(fillView, targetWidth);
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

    private static void setFillWidth(View fillView, int widthPx) {
        ViewGroup.LayoutParams layoutParams = fillView.getLayoutParams();
        if (layoutParams == null) {
            return;
        }
        int safeWidth = Math.max(0, widthPx);
        if (layoutParams.width != safeWidth) {
            layoutParams.width = safeWidth;
            fillView.requestLayout();
        }
    }

    private static int getFillWidth(View fillView) {
        ViewGroup.LayoutParams layoutParams = fillView.getLayoutParams();
        if (layoutParams != null && layoutParams.width >= 0) {
            return layoutParams.width;
        }
        return fillView.getWidth();
    }

    private static int levelToWidth(int level, int totalWidth) {
        float progress = Math.max(0f, Math.min(1f, level / (float) MAX_LEVEL));
        return Math.round(progress * Math.max(0, totalWidth));
    }

    private static int widthToLevel(int width, int totalWidth) {
        if (totalWidth <= 0) {
            return 0;
        }
        float progress = Math.max(0f, Math.min(1f, width / (float) totalWidth));
        return Math.round(progress * MAX_LEVEL);
    }

    private static int calculateLevel(int votes, int totalVotes) {
        float progress = totalVotes > 0 ? Math.max(0f, Math.min(1f, votes / (float) totalVotes)) : 0f;
        return Math.round(progress * MAX_LEVEL);
    }
}
