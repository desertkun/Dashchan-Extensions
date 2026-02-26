package com.mishiranu.dashchan.chan.e444.enhance;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public final class EnhanceHostResolver {
	private static final String HOST_CHAN_CLASS = "chan.content.Chan";
	private static final String METHOD_CHAN_GET = "get";
	private static final String BUNDLE_KEY_CHAN_NAME = "chanName";
	private static final String METHOD_GET_CURRENT_FRAGMENT = "getCurrentFragment";
	private static final String METHOD_GET_PAGE = "getPage";
	private static final String METHOD_GET_CHAN_NAME = "getChanName";
	private static final String METHOD_GET_SUPPORT_FRAGMENT_MANAGER = "getSupportFragmentManager";
	private static final String METHOD_GET_PRIMARY_NAVIGATION_FRAGMENT = "getPrimaryNavigationFragment";
	private static final String METHOD_FIND_FRAGMENT_BY_ID = "findFragmentById";
	private static final String POSTS_PAGE_CLASS_NAME = "PostsPage";

	public interface ChanComponentMatcher {
		boolean matches(Object component);
	}

	private EnhanceHostResolver() {}

	public static View getDecorViewSafe(Activity activity) {
		try {
			return activity.getWindow().getDecorView();
		} catch (Throwable t) {
			return null;
		}
	}

	public static boolean isActivityDestroyed(Activity activity) {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed();
	}

	public static boolean hasMatchingComponentForActiveChan(Activity activity, ChanComponentMatcher matcher) {
		if (activity == null || matcher == null) {
			return false;
		}
		String activeChanName = resolveActiveChanName(activity);
		if (activeChanName == null) {
			return false;
		}
		Object chan = resolveHostChan(activity.getClassLoader(), activeChanName);
		if (chan == null) {
			return false;
		}
		if (normalizeChanName(readStringField(chan, "name")) == null) {
			return false;
		}
		return matcher.matches(readField(chan, "configuration"))
				|| matcher.matches(readField(chan, "performer"))
				|| matcher.matches(readField(chan, "locator"))
				|| matcher.matches(readField(chan, "markup"));
	}

	public static boolean isPostsPageActive(Activity activity) {
		Object page = resolveCurrentPage(activity);
		if (page == null) {
			return false;
		}
		String simpleName = page.getClass().getSimpleName();
		if (POSTS_PAGE_CLASS_NAME.equals(simpleName)) {
			return true;
		}
		String className = page.getClass().getName();
		return className.endsWith("." + POSTS_PAGE_CLASS_NAME);
	}

	public static boolean isThreadPageActive(Activity activity) {
		Object page = resolveCurrentPage(activity);
		if (page == null) {
			return false;
		}
		String threadNumber = readStringField(page, "threadNumber");
		if (threadNumber == null) {
			threadNumber = castString(invokeNoArgs(page, "getThreadNumber"));
		}
		return threadNumber != null && !threadNumber.trim().isEmpty();
	}

	public static Object readField(Object source, String fieldName) {
		if (source == null) {
			return null;
		}
		Class<?> current = source.getClass();
		while (current != null) {
			try {
				Field field = current.getDeclaredField(fieldName);
				field.setAccessible(true);
				return field.get(source);
			} catch (NoSuchFieldException e) {
				current = current.getSuperclass();
			} catch (Throwable t) {
				return null;
			}
		}
		return null;
	}

	private static String resolveActiveChanName(Activity activity) {
		String chanName = resolveChanNameFromCurrentFragment(activity);
		if (chanName != null) {
			return chanName;
		}
		Intent intent = activity.getIntent();
		if (isUriHandlerActivity(activity)) {
			return resolveChanNameFromIntent(intent);
		}
		return null;
	}

	private static Object resolveCurrentPage(Activity activity) {
		Object currentFragment = invokeNoArgs(activity, METHOD_GET_CURRENT_FRAGMENT);
		Object page = resolvePageFromFragment(currentFragment);
		if (page != null) {
			return page;
		}
		Object fragmentManager = invokeNoArgs(activity, METHOD_GET_SUPPORT_FRAGMENT_MANAGER);
		if (fragmentManager == null) {
			return null;
		}
		int contentFragmentId = activity.getResources().getIdentifier("content_fragment", "id",
				activity.getPackageName());
		if (contentFragmentId != 0) {
			Object contentFragment = invoke(fragmentManager, METHOD_FIND_FRAGMENT_BY_ID,
					new Class<?>[] {int.class}, contentFragmentId);
			page = resolvePageFromFragment(contentFragment);
			if (page != null) {
				return page;
			}
		}
		Object primaryFragment = invokeNoArgs(fragmentManager, METHOD_GET_PRIMARY_NAVIGATION_FRAGMENT);
		return resolvePageFromFragment(primaryFragment);
	}

	private static Object resolveHostChan(ClassLoader classLoader, String chanName) {
		try {
			Class<?> chanClass = Class.forName(HOST_CHAN_CLASS, false, classLoader);
			Method method = chanClass.getDeclaredMethod(METHOD_CHAN_GET, String.class);
			method.setAccessible(true);
			return method.invoke(null, chanName);
		} catch (Throwable t) {
			return null;
		}
	}

	private static String resolveChanNameFromCurrentFragment(Activity activity) {
		Object currentFragment = invokeNoArgs(activity, METHOD_GET_CURRENT_FRAGMENT);
		String chanName = resolveChanNameFromPageFragment(currentFragment);
		if (chanName != null) {
			return chanName;
		}
		Object fragmentManager = invokeNoArgs(activity, METHOD_GET_SUPPORT_FRAGMENT_MANAGER);
		if (fragmentManager == null) {
			return null;
		}
		int contentFragmentId = activity.getResources().getIdentifier("content_fragment", "id",
				activity.getPackageName());
		if (contentFragmentId != 0) {
			Object contentFragment = invoke(fragmentManager, METHOD_FIND_FRAGMENT_BY_ID,
					new Class<?>[] {int.class}, contentFragmentId);
			chanName = resolveChanNameFromPageFragment(contentFragment);
			if (chanName != null) {
				return chanName;
			}
		}
		Object primaryFragment = invokeNoArgs(fragmentManager, METHOD_GET_PRIMARY_NAVIGATION_FRAGMENT);
		return resolveChanNameFromPageFragment(primaryFragment);
	}

	private static String resolveChanNameFromPageFragment(Object fragment) {
		Object page = resolvePageFromFragment(fragment);
		if (page == null) {
			return null;
		}
		return resolveChanNameFromPage(page);
	}

	private static Object resolvePageFromFragment(Object fragment) {
		if (fragment == null) {
			return null;
		}
		return invokeNoArgs(fragment, METHOD_GET_PAGE);
	}

	private static boolean isUriHandlerActivity(Activity activity) {
		String className = activity.getClass().getName();
		return "chan.application.UriHandlerActivity".equals(className) || className.endsWith(".UriHandlerActivity");
	}

	private static String resolveChanNameFromIntent(Intent intent) {
		if (intent == null) {
			return null;
		}
		Bundle extras = intent.getExtras();
		return normalizeChanName(readStringFromBundle(extras, BUNDLE_KEY_CHAN_NAME));
	}

	private static String resolveChanNameFromPage(Object page) {
		if (page == null) {
			return null;
		}
		String chanName = normalizeChanName(readStringField(page, BUNDLE_KEY_CHAN_NAME));
		if (chanName != null) {
			return chanName;
		}
		return normalizeChanName(castString(invokeNoArgs(page, METHOD_GET_CHAN_NAME)));
	}

	private static String normalizeChanName(String chanName) {
		if (chanName == null) {
			return null;
		}
		String normalized = chanName.trim().toLowerCase(Locale.US);
		return normalized.isEmpty() ? null : normalized;
	}

	private static String readStringField(Object source, String fieldName) {
		Object value = readField(source, fieldName);
		return value instanceof String ? (String) value : null;
	}

	private static Object invokeNoArgs(Object source, String methodName) {
		return invoke(source, methodName, new Class<?>[0]);
	}

	private static Object invoke(Object source, String methodName, Class<?>[] parameterTypes, Object... args) {
		if (source == null) {
			return null;
		}
		Class<?> current = source.getClass();
		while (current != null) {
			try {
				Method method = current.getDeclaredMethod(methodName, parameterTypes);
				method.setAccessible(true);
				return method.invoke(source, args);
			} catch (NoSuchMethodException e) {
				current = current.getSuperclass();
			} catch (Throwable t) {
				return null;
			}
		}
		try {
			Method method = source.getClass().getMethod(methodName, parameterTypes);
			method.setAccessible(true);
			return method.invoke(source, args);
		} catch (Throwable t) {
			return null;
		}
	}

	private static String castString(Object value) {
		return value instanceof String ? (String) value : null;
	}

	private static String readStringFromBundle(Bundle bundle, String key) {
		if (bundle == null) {
			return null;
		}
		Object value = bundle.get(key);
		return value instanceof String ? (String) value : null;
	}
}
