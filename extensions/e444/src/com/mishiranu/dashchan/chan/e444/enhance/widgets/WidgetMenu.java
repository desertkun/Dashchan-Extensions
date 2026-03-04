package com.mishiranu.dashchan.chan.e444.enhance.widgets;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.flexbox.AlignItems;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.flexbox.JustifyContent;
import com.mishiranu.dashchan.chan.e444.E444ChanLocator;
import com.mishiranu.dashchan.chan.e444.E444Model;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceReflection;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceWidget;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import lombok.EqualsAndHashCode;

public final class WidgetMenu implements EnhanceWidget {
    public static final String CONTAINER_TAG = "e444_post_injected_menu_container";
    private static final Map<LinearLayout, List<MenuSection>> BOUND_SECTIONS =
            Collections.synchronizedMap(new WeakHashMap<LinearLayout, List<MenuSection>>());

    private final E444ChanLocator locator;
    private final List<MenuSection> sections;

    @EqualsAndHashCode
    private static final class MenuSection {
        public final String title;
        public final List<MenuLink> links;

        private MenuSection(String title, List<MenuLink> links) {
            this.title = title;
            this.links = links;
        }
    }

    @EqualsAndHashCode
    private static final class MenuLink {
        public final String label;
        public final String url;

        private MenuLink(String label, String url) {
            this.label = label;
            this.url = url;
        }
    }

    public WidgetMenu(List<E444Model.MenuSection> menuSections, E444ChanLocator locator) {
        this.locator = locator;
        ArrayList<MenuSection> parsedSections = new ArrayList<>();
        if (menuSections != null) {
            for (E444Model.MenuSection sectionJson : menuSections) {
                String sectionName = sectionJson != null && sectionJson.sectionName != null
                        ? sectionJson.sectionName.trim()
                        : "";
                ArrayList<MenuLink> sectionLinks = new ArrayList<>();
                if (sectionJson != null && sectionJson.links != null) {
                    for (E444Model.MenuLink linkJson : sectionJson.links) {
                        if (linkJson == null) {
                            continue;
                        }
                        String label = linkJson.label != null ? linkJson.label.trim() : "";
                        String url = linkJson.url != null ? linkJson.url.trim() : "";
                        sectionLinks.add(new MenuLink(label, url));
                    }
                }
                parsedSections.add(new MenuSection(sectionName, sectionLinks));
            }
        }
        this.sections = parsedSections;
    }

    public boolean isEmpty() {
        return sections.isEmpty();
    }

    @Override
    public String getContainerTag() {
        return CONTAINER_TAG;
    }

    @Override
    public ViewGroup.LayoutParams createLayoutParams(Activity activity) {
        int margin = dp(activity, 6);
        LinearLayout.LayoutParams layoutParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.leftMargin = margin;
        layoutParams.topMargin = margin;
        layoutParams.rightMargin = margin;
        layoutParams.bottomMargin = margin;
        return layoutParams;
    }

    @Override
    public void inject(Activity activity, ViewGroup postRoot) {
        View existingContainerView = postRoot.findViewWithTag(CONTAINER_TAG);
        if (existingContainerView == null) {
            LinearLayout container = createContainer(activity);
            postRoot.addView(container);
            existingContainerView = container;
        }
        LinearLayout container = (LinearLayout) existingContainerView;
        List<MenuSection> oldSections = BOUND_SECTIONS.get(container);
        if (oldSections != null && oldSections.equals(sections)) {
            return;
        }
        applySections(activity, container, sections);
        BOUND_SECTIONS.put(container, sections);
    }

    private static LinearLayout createContainer(Activity activity) {
        LinearLayout container = new LinearLayout(activity);
        container.setTag(CONTAINER_TAG);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.START);
        container.setClickable(false);
        container.setFocusable(false);
        return container;
    }

    private void applySections(Activity activity, LinearLayout container, List<MenuSection> sections) {
        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            MenuSection section = sections.get(sectionIndex);
            int titleIndex = sectionIndex * 2;
            TextView titleView = obtainOrCreateTitleView(activity, container, titleIndex);
            titleView.setText(section.title);
            FlexboxLayout linksLayout = obtainOrCreateLinksLayout(activity, container, titleIndex + 1);
            for (int i = 0; i < section.links.size(); i++) {
                final MenuLink menuItem = section.links.get(i);
                Button button = obtainOrCreateButton(activity, linksLayout, i);
                button.setText(menuItem.label);
                button.setTag(menuItem.url);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        performAction(activity, WidgetMenu.this.locator, menuItem.url);
                    }
                });
            }
            while (linksLayout.getChildCount() > section.links.size()) {
                linksLayout.removeViewAt(linksLayout.getChildCount() - 1);
            }
        }
        int expectedChildrenCount = sections.size() * 2;
        while (container.getChildCount() > expectedChildrenCount) {
            container.removeViewAt(container.getChildCount() - 1);
        }
    }

    private static TextView obtainOrCreateTitleView(Activity activity, LinearLayout container, int index) {
        View existing = index < container.getChildCount() ? container.getChildAt(index) : null;
        if (existing instanceof TextView) {
            return (TextView) existing;
        }
        TextView titleView = new TextView(activity);
        if (index < container.getChildCount()) {
            container.removeViewAt(index);
            container.addView(titleView, index, createTitleLayoutParams(activity));
        } else {
            container.addView(titleView, createTitleLayoutParams(activity));
        }
        return titleView;
    }

    private static FlexboxLayout obtainOrCreateLinksLayout(Activity activity, LinearLayout container, int index) {
        View existing = index < container.getChildCount() ? container.getChildAt(index) : null;
        if (existing instanceof FlexboxLayout) {
            return (FlexboxLayout) existing;
        }
        FlexboxLayout linksLayout = createLinksLayout(activity);
        if (index < container.getChildCount()) {
            container.removeViewAt(index);
            container.addView(linksLayout, index, createLinksLayoutParams(activity));
        } else {
            container.addView(linksLayout, createLinksLayoutParams(activity));
        }
        return linksLayout;
    }

    private static Button obtainOrCreateButton(Activity activity, FlexboxLayout linksLayout, int index) {
        View existing = index < linksLayout.getChildCount() ? linksLayout.getChildAt(index) : null;
        if (existing instanceof Button) {
            return (Button) existing;
        }
        Button button = new Button(activity);
        if (index < linksLayout.getChildCount()) {
            linksLayout.removeViewAt(index);
            linksLayout.addView(button, index, createButtonLayoutParams(activity));
        } else {
            linksLayout.addView(button, createButtonLayoutParams(activity));
        }
        return button;
    }

    private static LinearLayout.LayoutParams createTitleLayoutParams(Activity activity) {
        LinearLayout.LayoutParams titleLayoutParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLayoutParams.topMargin = dp(activity, 6);
        return titleLayoutParams;
    }

    private static FlexboxLayout createLinksLayout(Activity activity) {
        FlexboxLayout linksLayout = new FlexboxLayout(activity);
        linksLayout.setFlexDirection(FlexDirection.ROW);
        linksLayout.setFlexWrap(FlexWrap.WRAP);
        linksLayout.setJustifyContent(JustifyContent.FLEX_START);
        linksLayout.setAlignItems(AlignItems.FLEX_START);
        return linksLayout;
    }

    private static LinearLayout.LayoutParams createLinksLayoutParams(Activity activity) {
        LinearLayout.LayoutParams linksLayoutParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        linksLayoutParams.topMargin = dp(activity, 3);
        return linksLayoutParams;
    }

    private static FlexboxLayout.LayoutParams createButtonLayoutParams(Activity activity) {
        FlexboxLayout.LayoutParams buttonLayoutParams = new FlexboxLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonLayoutParams.rightMargin = dp(activity, 4);
        buttonLayoutParams.bottomMargin = dp(activity, 4);
        return buttonLayoutParams;
    }

    private static void performAction(Activity activity, E444ChanLocator locator, String target) {
        Uri targetUri = normalizeTargetUri(locator, target);
        if (locator.isBoardUri(targetUri) || locator.isThreadUri(targetUri) || locator.isAttachmentUri(targetUri)) {
            if (tryStartActivity(
                    activity,
                    new Intent(EnhanceReflection.ACTION_HANDLE_URI, targetUri).setPackage(activity.getPackageName()))) {
                return;
            }
            if (tryStartActivity(
                    activity,
                    new Intent()
                            .setClassName(activity, EnhanceReflection.URI_HANDLER_ACTIVITY)
                            .setData(targetUri))) {
                return;
            }
        }
        if (tryStartActivity(activity, new Intent(Intent.ACTION_VIEW, targetUri))) {
            return;
        }
        Toast.makeText(activity, "Unable to open " + target, Toast.LENGTH_SHORT).show();
    }

    private static Uri normalizeTargetUri(E444ChanLocator locator, String target) {
        if (target.startsWith("/")) {
            return Uri.parse(URI.create(locator.buildPath("").toString())
                    .resolve(target)
                    .normalize()
                    .toString());
        }
        return Uri.parse(target);
    }

    private static boolean tryStartActivity(Activity activity, Intent intent) {
        try {
            if (intent.resolveActivity(activity.getPackageManager()) == null) {
                return false;
            }
            activity.startActivity(intent);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static int dp(Activity activity, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, activity.getResources().getDisplayMetrics()));
    }
}
