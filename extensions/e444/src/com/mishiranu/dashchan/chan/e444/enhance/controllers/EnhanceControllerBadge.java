package com.mishiranu.dashchan.chan.e444.enhance.controllers;

import android.app.Activity;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.mishiranu.dashchan.chan.e444.enhance.DashEnhance;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceHostResolver;

public final class EnhanceControllerBadge implements EnhanceController {
	private static final String ACTIVITY_BADGE_TAG = "e444_activity_hook_badge";
	private static final String ACTIVITY_BADGE_TEXT = "E444 INJECTED";
	private static final EnhanceControllerBadge INSTANCE = new EnhanceControllerBadge();
	private static final EnhanceHostResolver.ChanComponentMatcher BADGE_MATCHER =
			new EnhanceHostResolver.ChanComponentMatcher() {
				@Override
				public boolean matches(Object component) {
					return component instanceof DashEnhance && ((DashEnhance) component).showBadge();
				}
	};
	private static volatile boolean installToastShown;

	private EnhanceControllerBadge() {}

	public static EnhanceControllerBadge getInstance() {
		return INSTANCE;
	}

	@Override
	public void apply(Activity activity) {
		if (activity.isFinishing() || EnhanceHostResolver.isActivityDestroyed(activity)) {
			clear(activity);
			return;
		}
		if (isBadgeEnabledForActiveChan(activity)) {
			tryInjectActivityBadge(activity);
		} else {
			clear(activity);
		}
	}

	private static boolean isBadgeEnabledForActiveChan(Activity activity) {
		return EnhanceHostResolver.hasMatchingComponentForActiveChan(activity, BADGE_MATCHER);
	}

	private static void tryInjectActivityBadge(Activity activity) {
		View decorView = EnhanceHostResolver.getDecorViewSafe(activity);
		if (!(decorView instanceof ViewGroup)) {
			return;
		}
		ViewGroup rootView = (ViewGroup) decorView;
		if (rootView.findViewWithTag(ACTIVITY_BADGE_TAG) != null) {
			return;
		}
		TextView badge = new TextView(activity);
		badge.setTag(ACTIVITY_BADGE_TAG);
		badge.setText(ACTIVITY_BADGE_TEXT);
		badge.setTextColor(0xffffffff);
		badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
		badge.setBackgroundColor(0xccb00020);
		int paddingHorizontal = dp(activity, 10);
		int paddingVertical = dp(activity, 4);
		badge.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
		badge.setClickable(false);
		badge.setFocusable(false);
		FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		layoutParams.gravity = Gravity.START | Gravity.TOP;
		int margin = dp(activity, 8);
		layoutParams.leftMargin = margin;
		layoutParams.topMargin = margin;
		rootView.addView(badge, layoutParams);
		if (!installToastShown) {
			installToastShown = true;
			Toast.makeText(activity, "e444 activity hook active", Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	public void clear(Activity activity) {
		View decorView = EnhanceHostResolver.getDecorViewSafe(activity);
		if (!(decorView instanceof ViewGroup)) {
			return;
		}
		View badge = ((ViewGroup) decorView).findViewWithTag(ACTIVITY_BADGE_TAG);
		if (badge == null) {
			return;
		}
		if (badge.getParent() instanceof ViewGroup) {
			((ViewGroup) badge.getParent()).removeView(badge);
		}
	}

	private static int dp(Activity activity, int value) {
		return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
				activity.getResources().getDisplayMetrics()));
	}
}
