/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.settings.fragments.statusbar

import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.android.internal.util.lunaris.LunarisBarSettings
import com.android.settings.R
import com.android.settings.core.SubSettingLauncher

class LunarisBarCardsSettings : LunarisBarPreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.lunaris_bar_cards)
        requireActivity().title = preferenceScreen.title
        refreshPreferences()
    }

    override fun refreshPreferences() {
        val cards = editor.config.cards
        val parent = requireNotNull(findPreference<PreferenceGroup>("cards"))
        parent.removeAll()
        for (card in cards) {
            action(parent, card.id) { openCard(Bundle().apply { putString("card_id", card.id) }) }.apply {
                title = card.title
                summary = "${label(card.type)} · ${label(if (card.isEnabled) "on" else "off")}"
            }
        }
        findPreference<Preference>("cards_empty")?.isVisible = cards.isEmpty()
        findPreference<Preference>("add_card")?.apply {
            isEnabled = cards.size < LunarisBarSettings.MAX_CARDS
            setOnPreferenceClickListener {
                val types = LunarisBarSettings.Card.TYPES.toList()
                choose(label("add_card"), types.map(::label)) { index ->
                    if (types[index] == "widget") chooseWidget()
                    else openCard(Bundle().apply { putString("type", types[index]) })
                }
                true
            }
        }
    }

    private fun openCard(arguments: Bundle) {
        SubSettingLauncher(requireContext())
            .setDestination(LunarisBarCardSettings::class.java.name)
            .setArguments(arguments)
            .setTitleRes(R.string.lunaris_bar_edit_card)
            .setSourceMetricsCategory(getMetricsCategory())
            .launch()
    }

    private fun chooseWidget() {
        val providers = AppWidgetManager.getInstance(requireContext()).installedProviders.sortedBy {
            it.loadLabel(requireContext().packageManager).lowercase()
        }
        choose(label("widget"), providers.map { it.loadLabel(requireContext().packageManager) }) { index ->
            try {
                startActivity(Intent(LunarisBarSettings.WIDGET_PICKER_ACTION)
                    .setPackage("com.android.systemui")
                    .putExtra("provider", providers[index].provider.flattenToString()))
            } catch (_: ActivityNotFoundException) {
                changed(false)
            }
        }
    }
}
