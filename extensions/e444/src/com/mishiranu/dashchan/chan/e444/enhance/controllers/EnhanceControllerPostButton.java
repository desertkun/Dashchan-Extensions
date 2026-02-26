package com.mishiranu.dashchan.chan.e444.enhance.controllers;

import android.app.Activity;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.mishiranu.dashchan.chan.e444.enhance.DashEnhance;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceHostResolver;
import java.util.Locale;

public final class EnhanceControllerPostButton implements EnhanceController {
	private static final String POST_BUTTON_TAG = "e444_post_injected_button";
	private static final String POST_BUTTON_TEXT = "E444 ACTION";
	private static final String CLICK_FEEDBACK_TEXT = "e444 post button click";
	private static final EnhanceControllerPostButton INSTANCE = new EnhanceControllerPostButton();
	private static final EnhanceHostResolver.ChanComponentMatcher ENHANCE_MATCHER =
			new EnhanceHostResolver.ChanComponentMatcher() {
				@Override
				public boolean matches(Object component) {
					return component instanceof DashEnhance;
				}
			};

	private EnhanceControllerPostButton() {}

	public static EnhanceControllerPostButton getInstance() {
		return INSTANCE;
	}

	@Override
	public void apply(Activity activity) {
		if (activity.isFinishing() || EnhanceHostResolver.isActivityDestroyed(activity)) {
			clear(activity);
			return;
		}
		if (!EnhanceHostResolver.hasMatchingComponentForActiveChan(activity, ENHANCE_MATCHER)
				|| !EnhanceHostResolver.isThreadPageActive(activity)) {
			clear(activity);
			return;
		}
		injectButtonsIntoVisiblePosts(activity);
	}

	private static void injectButtonsIntoVisiblePosts(Activity activity) {
		View decorView = EnhanceHostResolver.getDecorViewSafe(activity);
		if (!(decorView instanceof ViewGroup)) {
			return;
		}
		injectButtonsRecursive(activity, (ViewGroup) decorView);
	}

	private static void injectButtonsRecursive(Activity activity, ViewGroup group) {
		if (isPostCollectionView(group)) {
			injectButtonsIntoRecyclerChildren(activity, group);
		}
		int childCount = group.getChildCount();
		for (int i = 0; i < childCount; i++) {
			View child = group.getChildAt(i);
			if (child instanceof ViewGroup) {
				injectButtonsRecursive(activity, (ViewGroup) child);
			}
		}
	}

	private static boolean isRecyclerView(View view) {
		String className = view.getClass().getName();
		return className.contains("RecyclerView");
	}

	private static boolean isListView(View view) {
		String className = view.getClass().getName();
		return className.contains("ListView");
	}

	private static boolean isCollectionView(View view) {
		return isRecyclerView(view) || isListView(view);
	}

	private static boolean isPostCollectionView(View view) {
		if (!isCollectionView(view)) {
			return false;
		}
		Object adapter = invokeNoArgs(view, "getAdapter");
		if (adapter == null) {
			return false;
		}
		String adapterName = adapter.getClass().getName().toLowerCase(Locale.US);
		return adapterName.contains("post");
	}

	private static void injectButtonsIntoRecyclerChildren(Activity activity, ViewGroup recyclerView) {
		int childCount = recyclerView.getChildCount();
		for (int i = 0; i < childCount; i++) {
			View child = recyclerView.getChildAt(i);
			if (child instanceof ViewGroup) {
				ensurePostButton(activity, (ViewGroup) child);
			}
		}
	}

	private static void ensurePostButton(Activity activity, ViewGroup postRoot) {
		if (postRoot.findViewWithTag(POST_BUTTON_TAG) != null) {
			return;
		}
		TextView button = new TextView(activity);
		button.setTag(POST_BUTTON_TAG);
		button.setText(POST_BUTTON_TEXT);
		button.setTextColor(0xffffffff);
		button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
		button.setBackgroundColor(0xcc1e6fa8);
		int paddingHorizontal = dp(activity, 8);
		int paddingVertical = dp(activity, 4);
		button.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
		button.setClickable(true);
		button.setFocusable(true);
		button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Toast.makeText(activity, CLICK_FEEDBACK_TEXT, Toast.LENGTH_SHORT).show();
			}
		});
		postRoot.addView(button, createLayoutParams(activity, postRoot));
	}

	private static ViewGroup.LayoutParams createLayoutParams(Activity activity, ViewGroup parent) {
		int margin = dp(activity, 6);
		if (parent instanceof FrameLayout) {
			FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
					ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			layoutParams.gravity = Gravity.END | Gravity.BOTTOM;
			layoutParams.rightMargin = margin;
			layoutParams.bottomMargin = margin;
			return layoutParams;
		}
		if (parent instanceof RelativeLayout) {
			RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
					ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			layoutParams.addRule(RelativeLayout.ALIGN_PARENT_END);
			layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
			layoutParams.rightMargin = margin;
			layoutParams.bottomMargin = margin;
			return layoutParams;
		}
		ViewGroup.MarginLayoutParams layoutParams = new ViewGroup.MarginLayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		layoutParams.rightMargin = margin;
		layoutParams.bottomMargin = margin;
		return layoutParams;
	}

	@Override
	public void clear(Activity activity) {
		View decorView = EnhanceHostResolver.getDecorViewSafe(activity);
		if (!(decorView instanceof ViewGroup)) {
			return;
		}
		removeInjectedButtonsRecursive((ViewGroup) decorView);
	}

	private static void removeInjectedButtonsRecursive(ViewGroup group) {
		for (int i = group.getChildCount() - 1; i >= 0; i--) {
			View child = group.getChildAt(i);
			if (POST_BUTTON_TAG.equals(child.getTag())) {
				group.removeViewAt(i);
				continue;
			}
			if (child instanceof ViewGroup) {
				removeInjectedButtonsRecursive((ViewGroup) child);
			}
		}
	}

	private static int dp(Activity activity, int value) {
		return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
				activity.getResources().getDisplayMetrics()));
	}

	private static Object invokeNoArgs(Object source, String methodName) {
		if (source == null) {
			return null;
		}
		try {
			java.lang.reflect.Method method = source.getClass().getMethod(methodName);
			method.setAccessible(true);
			return method.invoke(source);
		} catch (Throwable t) {
			return null;
		}
	}
}
