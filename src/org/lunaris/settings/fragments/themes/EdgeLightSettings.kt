/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.settings.fragments.themes

import android.content.Context
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import androidx.preference.Preference
import com.android.internal.graphics.EdgeLightRenderer
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settings.search.BaseSearchIndexProvider
import com.android.settingslib.search.SearchIndexable

@SearchIndexable
class EdgeLightSettings : SettingsPreferenceFragment() {
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { updateControls() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.edge_light_settings)
    }

    override fun onResume() {
        super.onResume()
        WATCHED_KEYS.forEach {
            requireContext().contentResolver.registerContentObserver(
                Settings.System.getUriFor(it), false, observer, UserHandle.USER_ALL,
            )
        }
        updateControls()
    }

    override fun onPause() {
        requireContext().contentResolver.unregisterContentObserver(observer)
        super.onPause()
    }

    private fun updateControls() {
        val resolver = requireContext().contentResolver
        val style = EdgeLightRenderer.normalizeStyle(Settings.System.getStringForUser(
            resolver, "edge_light_style", UserHandle.USER_CURRENT,
        ))
        val design = EdgeLightRenderer.isDesign(style)
        val mode = Settings.System.getStringForUser(
            resolver, "edge_light_color_mode", UserHandle.USER_CURRENT,
        )
        val effect = EdgeLightRenderer.normalizeEffect(Settings.System.getStringForUser(
            resolver, "edge_light_animation_effect", UserHandle.USER_CURRENT,
        ))
        fun show(key: String, visible: Boolean) {
            findPreference<Preference>(key)?.isVisible = visible
        }
        show("edge_light_stroke_width", EdgeLightRenderer.usesStrokeWidth(style))
        show("edge_light_animation_effect", !design)
        show("edge_light_animation_speed", design || effect != "none" || mode == "rainbow")
        show("edge_light_animation_direction", design)
        show("edge_light_design_length", design && style in LENGTH_STYLES)
        show("edge_light_design_size", design && style in SIZE_STYLES)
        show("edge_light_design_density", design && style != "side_lights")
        show("edge_light_custom_color", mode == "custom")
        val display = Settings.System.getIntForUser(
            resolver, "edge_light_display_mode", 0, UserHandle.USER_CURRENT,
        )
        show("edge_light_trigger_unlock_only", display == 1 || display == 2)
    }

    override fun getMetricsCategory(): Int = MetricsEvent.LUNARIS

    companion object {
        @JvmField val TAG = "EdgeLightSettings"
        private val WATCHED_KEYS = arrayOf(
            "edge_light_style", "edge_light_color_mode", "edge_light_display_mode",
            "edge_light_animation_effect",
        )
        private val LENGTH_STYLES = setOf(
            "chase_around", "mirror_rain", "corner_out", "fire_dance", "side_ripples",
            "flat_ripples", "flat_streaks", "side_streaks", "waves_around", "lights_around",
            "side_lights", "snow_particles", "bubble_particles", "dot_particles",
        )
        private val SIZE_STYLES = setOf(
            "fire_dance", "flat_bars", "side_bars", "bars_around", "flat_flutter",
            "side_flutter", "flat_waves", "side_waves", "waves_around", "strings_around",
            "side_braids", "side_lights", "serial_lights", "serials_around", "snow_particles",
            "bubble_particles", "dot_particles",
        )
        @JvmField
        val SEARCH_INDEX_DATA_PROVIDER: BaseSearchIndexProvider =
            object : BaseSearchIndexProvider(R.xml.edge_light_settings) {
                override fun getNonIndexableKeys(context: Context): List<String> =
                    super.getNonIndexableKeys(context)
            }
    }
}
