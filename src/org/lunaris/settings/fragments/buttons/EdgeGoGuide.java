/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 */
package org.lunaris.settings.fragments.buttons;

import android.content.Context;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;

import com.android.internal.util.lunaris.EdgeGoConfig;
import com.android.settings.R;
import com.android.settings.core.SubSettingLauncher;
import com.android.settingslib.search.SearchIndexableRaw;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class EdgeGoGuide {
    private static final String[] BASICS = {"zones", "taps", "swipes", "holds", "extended", "cancel"};
    private final EdgeGoSettings fragment;

    EdgeGoGuide(EdgeGoSettings fragment) { this.fragment = fragment; }

    static boolean known(String topic) {
        return Arrays.asList(BASICS).contains(topic) || Arrays.asList(EdgeGoSettings.SECTIONS).contains(topic)
                || topic.equals("actions");
    }

    void show(PreferenceGroup screen, String topic) {
        Context context = fragment.requireContext();
        if (topic.isEmpty() || !known(topic)) {
            Preference intro = new Preference(context);
            intro.setKey("edgego_guide_intro");
            intro.setTitle(R.string.edgego_guide_welcome);
            intro.setSummary(R.string.edgego_guide_intro);
            intro.setSelectable(false);
            screen.addPreference(intro);
            PreferenceCategory gestures = category(screen, "edgego_guide_gestures", R.string.edgego_guide_gestures);
            for (String id : BASICS) topic(gestures, id);
            PreferenceCategory features = category(screen, "edgego_guide_features", R.string.edgego_guide_features);
            for (String id : EdgeGoSettings.SECTIONS) topic(features, id);
            topic(screen, "actions");
            return;
        }
        EdgeGoGuidePreference preview = new EdgeGoGuidePreference(context, fragment.configuration(), topic,
                title(context, topic), resource(context, topic, "summary"));
        fragment.setGuidePreview(preview);
        screen.addPreference(preview);
        Preference explanation = new Preference(context);
        explanation.setKey("edgego_guide_explanation");
        explanation.setTitle(R.string.edgego_guide_how_to);
        explanation.setSummary(resource(context, topic, "body"));
        explanation.setSelectable(false);
        screen.addPreference(explanation);
        if (topic.equals("actions")) {
            PreferenceCategory actions = category(screen, "edgego_guide_action_list", R.string.edgego_guide_actions_title);
            for (String action : EdgeGoConfig.ACTIONS) {
                Preference entry = new Preference(context);
                entry.setKey("edgego_guide_action_" + action);
                entry.setTitle(EdgeGoActionPicker.label(context, action));
                int description = context.getResources().getIdentifier("edgego_guide_action_" + action,
                        "string", context.getPackageName());
                entry.setSummary(description == 0 ? R.string.edgego_guide_action_default : description);
                entry.setSelectable(false);
                actions.addPreference(entry);
            }
        } else {
            Preference settings = new Preference(context);
            settings.setKey("edgego_guide_settings");
            settings.setTitle(R.string.edgego_guide_open_settings);
            settings.setOnPreferenceClickListener(preference -> {
                boolean basic = Arrays.asList(BASICS).contains(topic);
                Bundle args = new Bundle();
                args.putString("section", basic ? "" : topic);
                args.putString("zone", basic ? "right_z1" : "");
                new SubSettingLauncher(context).setDestination(EdgeGoSettings.class.getName()).setArguments(args)
                        .setSourceMetricsCategory(fragment.getMetricsCategory())
                        .setTitleText(basic ? context.getString(R.string.edgego_zone_right_z1_title) : title(context, topic)).launch();
                return true;
            });
            screen.addPreference(settings);
        }
    }

    private void topic(PreferenceGroup group, String topic) {
        Context context = fragment.requireContext();
        Preference entry = new Preference(context);
        entry.setKey("edgego_guide_" + topic);
        entry.setTitle(title(context, topic));
        entry.setSummary(resource(context, topic, "summary"));
        entry.setOnPreferenceClickListener(preference -> {
            Bundle args = new Bundle();
            args.putString("section", "guide_" + topic);
            new SubSettingLauncher(context).setDestination(EdgeGoSettings.class.getName()).setArguments(args)
                    .setSourceMetricsCategory(fragment.getMetricsCategory()).setTitleText(title(context, topic)).launch();
            return true;
        });
        group.addPreference(entry);
    }

    private PreferenceCategory category(PreferenceGroup screen, String key, int title) {
        PreferenceCategory category = new PreferenceCategory(fragment.requireContext());
        category.setKey(key);
        category.setTitle(title);
        screen.addPreference(category);
        return category;
    }

    static String title(Context context, String topic) {
        String prefix = Arrays.asList(EdgeGoSettings.SECTIONS).contains(topic) ? "edgego_section_" : "edgego_guide_";
        int id = context.getResources().getIdentifier(prefix + topic + "_title", "string", context.getPackageName());
        return context.getString(id == 0 ? R.string.edgego_help : id);
    }

    private static String resource(Context context, String topic, String suffix) {
        int id = context.getResources().getIdentifier("edgego_guide_" + topic + "_" + suffix,
                "string", context.getPackageName());
        return context.getString(id == 0 ? R.string.edgego_guide_intro : id);
    }

    static void addSearchRows(Context context, List<SearchIndexableRaw> rows) {
        List<String> topics = new ArrayList<>(Arrays.asList(BASICS));
        topics.addAll(Arrays.asList(EdgeGoSettings.SECTIONS));
        topics.add("actions");
        for (String topic : topics) {
            SearchIndexableRaw row = new SearchIndexableRaw(context);
            row.title = title(context, topic);
            row.summaryOn = resource(context, topic, "summary");
            row.screenTitle = context.getString(R.string.edgego_help);
            row.key = "edgego_guide_" + topic;
            row.className = EdgeGoSettings.class.getName();
            row.keywords = "EdgeGo Gesture guide " + row.title;
            rows.add(row);
        }
    }
}
