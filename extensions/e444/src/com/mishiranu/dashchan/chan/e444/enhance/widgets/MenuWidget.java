package com.mishiranu.dashchan.chan.e444.enhance.widgets;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import chan.text.JsonSerial;
import chan.text.ParseException;
import com.google.android.flexbox.AlignItems;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.flexbox.JustifyContent;
import com.mishiranu.dashchan.chan.e444.E444ChanLocator;
import com.mishiranu.dashchan.chan.e444.enhance.EnhanceHostResolver;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import lombok.EqualsAndHashCode;

public final class MenuWidget implements EnhanceWidget {
    public static final String CONTAINER_TAG = "e444_post_injected_buttons_container";
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

        private static MenuLink parse(JsonSerial.Reader reader) throws IOException, ParseException {
            String label = "";
            String url = "";
            reader.startObject();
            while (!reader.endStruct()) {
                switch (reader.nextName()) {
                    case "label": {
                        label = reader.nextString().trim();
                        break;
                    }
                    case "url": {
                        url = reader.nextString().trim();
                        break;
                    }
                    default: {
                        reader.skip();
                        break;
                    }
                }
            }
            return new MenuLink(label, url);
        }
    }

    public MenuWidget(JsonSerial.Reader reader, E444ChanLocator locator) throws IOException, ParseException {
        this.locator = locator;
        ArrayList<MenuSection> parsedSections = new ArrayList<>();
        reader.startArray();
        while (!reader.endStruct()) {
            String sectionName = "";
            ArrayList<MenuLink> sectionLinks = new ArrayList<>();
            reader.startObject();
            while (!reader.endStruct()) {
                switch (reader.nextName()) {
                    case "sectionName": {
                        sectionName = reader.nextString().trim();
                        break;
                    }
                    case "links": {
                        reader.startArray();
                        while (!reader.endStruct()) {
                            sectionLinks.add(MenuLink.parse(reader));
                        }
                        break;
                    }
                    default: {
                        reader.skip();
                        break;
                    }
                }
            }
            parsedSections.add(new MenuSection(sectionName, sectionLinks));
        }
        this.sections = parsedSections;
    }

    public boolean isEmpty() {
        return sections.isEmpty();
    }

    @Override
    public void bind(Activity activity, ViewGroup postRoot) {
        View existingContainerView = postRoot.findViewWithTag(CONTAINER_TAG);
        if (existingContainerView == null) {
            LinearLayout container = createContainer(activity);
            postRoot.addView(container, createLayoutParams(activity, postRoot));
            existingContainerView = container;
        }
        LinearLayout container = (LinearLayout) existingContainerView;
        List<MenuSection> oldSections = BOUND_SECTIONS.get(container);
        if (oldSections != null && oldSections.equals(sections)) {
            return;
        }
        rebuild(activity, container, sections);
        BOUND_SECTIONS.put(container, sections);
    }

    @Override
    public void clear(ViewGroup postRoot) {
        View existingContainerView = postRoot.findViewWithTag(CONTAINER_TAG);
        if (existingContainerView instanceof LinearLayout) {
            BOUND_SECTIONS.remove(existingContainerView);
        }
        if (existingContainerView != null && existingContainerView.getParent() instanceof ViewGroup) {
            ((ViewGroup) existingContainerView.getParent()).removeView(existingContainerView);
        }
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

    private void rebuild(Activity activity, LinearLayout container, List<MenuSection> sections) {
        container.removeAllViews();
        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            MenuSection section = sections.get(sectionIndex);
            TextView titleView = new TextView(activity);
            titleView.setText(section.title);
            LinearLayout.LayoutParams titleLayoutParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleLayoutParams.topMargin = dp(activity, 6);
            container.addView(titleView, titleLayoutParams);
            FlexboxLayout linksLayout = new FlexboxLayout(activity);
            linksLayout.setFlexDirection(FlexDirection.ROW);
            linksLayout.setFlexWrap(FlexWrap.WRAP);
            linksLayout.setJustifyContent(JustifyContent.FLEX_START);
            linksLayout.setAlignItems(AlignItems.FLEX_START);
            LinearLayout.LayoutParams linksLayoutParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            linksLayoutParams.topMargin = dp(activity, 3);
            container.addView(linksLayout, linksLayoutParams);
            for (int i = 0; i < section.links.size(); i++) {
                final MenuLink menuItem = section.links.get(i);
                Button button = new Button(activity);
                button.setText(menuItem.label);
                button.setTag(menuItem.url);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        performAction(activity, MenuWidget.this.locator, menuItem.url);
                    }
                });
                FlexboxLayout.LayoutParams buttonLayoutParams = new FlexboxLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                buttonLayoutParams.rightMargin = dp(activity, 4);
                buttonLayoutParams.bottomMargin = dp(activity, 4);
                linksLayout.addView(button, buttonLayoutParams);
            }
        }
    }

    private static void performAction(Activity activity, E444ChanLocator locator, String target) {
        Uri targetUri = normalizeTargetUri(locator, target);
        if (locator.isBoardUri(targetUri) || locator.isThreadUri(targetUri) || locator.isAttachmentUri(targetUri)) {
            if (tryStartActivity(
                    activity,
                    new Intent(EnhanceHostResolver.ACTION_HANDLE_URI, targetUri)
                            .setPackage(activity.getPackageName()))) {
                return;
            }
            if (tryStartActivity(
                    activity,
                    new Intent()
                            .setClassName(activity, EnhanceHostResolver.URI_HANDLER_ACTIVITY)
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

    private static ViewGroup.LayoutParams createLayoutParams(Activity activity, ViewGroup parent) {
        int margin = dp(activity, 6);
        FrameLayout.LayoutParams layoutParams =
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.END | Gravity.BOTTOM;
        layoutParams.leftMargin = margin;
        layoutParams.topMargin = margin;
        layoutParams.rightMargin = margin;
        layoutParams.bottomMargin = margin;
        return layoutParams;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, activity.getResources().getDisplayMetrics()));
    }
}
