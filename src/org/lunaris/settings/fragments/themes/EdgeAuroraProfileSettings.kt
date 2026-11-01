/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lunaris.settings.fragments.themes

import android.app.AlertDialog
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.internal.util.lunaris.EdgeAuroraConfig
import com.android.internal.util.lunaris.EdgeAuroraConfig.Profile
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settingslib.widget.LayoutPreference
import kotlin.math.roundToInt
import org.json.JSONArray
import org.lunaris.settings.preferences.colorpicker.ColorPickerPreference

class EdgeAuroraProfileSettings : SettingsPreferenceFragment() {
    private lateinit var profile: Profile
    private var binding = false
    private lateinit var boundProfile: Profile
    private var observing = false
    private var app: AuroraApp? = null
    private var apps = emptyList<AuroraApp>()
    private var appLoadGeneration = 0
    private var appDialog: AlertDialog? = null
    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (isAdded && ::profile.isInitialized) reload()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.edge_aurora_profile)
        val id = arguments?.getString("profile_id")
        val saved = readProfiles().find { it.id == id }
        if (saved == null) {
            finish()
            return
        }
        profile = saved.copy()
        boundProfile = profile.copy()
        activity?.title = profile.name.ifBlank { getString(R.string.edge_aurora_title) }
        findPreference<SwitchPreferenceCompat>("profile_enabled")!!.setOnPreferenceChangeListener {
            _,
            value ->
            profile.enabled = value as Boolean
            save()
            true
        }
        findPreference<EditTextPreference>("name")!!.apply {
            isVisible = profile.isGroup
            setOnPreferenceChangeListener { _, value ->
                val name = value.toString().trim().take(80)
                if (name.isBlank()) false
                else {
                    profile.name = name
                    save()
                    activity?.title = name
                    true
                }
            }
        }
        findPreference<Preference>("members")!!.apply {
            isVisible = profile.isGroup
            isEnabled = false
            setOnPreferenceClickListener {
                appDialog?.dismiss()
                appDialog =
                    AuroraAppPicker.show(
                        requireContext(),
                        R.string.edge_aurora_members,
                        apps,
                        profile.members.toSet(),
                    ) { selected ->
                        profile.members.clear()
                        profile.members.addAll(selected)
                        save()
                    }
                true
            }
        }
        findPreference<ListPreference>("edge")!!.setOnPreferenceChangeListener { _, value ->
            profile.edge = EdgeAuroraConfig.Edge.valueOf(value.toString())
            save()
            true
        }
        findPreference<ListPreference>("motion")!!.setOnPreferenceChangeListener { _, value ->
            profile.motion = EdgeAuroraConfig.Motion.valueOf(value.toString())
            save()
            true
        }
        findPreference<ListPreference>("palette")!!.setOnPreferenceChangeListener { _, value ->
            profile.color = Color.parseColor(value.toString())
            save()
            true
        }
        findPreference<ColorPickerPreference>("color")!!.setOnPreferenceChangeListener { _, value ->
            if (!binding) {
                profile.color = value as Int
                rememberColor(profile.color)
                save()
            }
            true
        }
        findPreference<ListPreference>("recent_colors")!!.setOnPreferenceChangeListener { _, value
            ->
            profile.color = value.toString().toInt()
            save()
            true
        }
        listOf("position", "length", "thickness", "bloom").forEach { key ->
            findPreference<EdgeAuroraSliderPreference>(key)!!.setOnPreferenceChangeListener {
                _,
                value ->
                if (!binding) {
                    val number = (value as Int).toFloat()
                    when (key) {
                        "position" -> profile.position = number / 100f
                        "length" -> profile.length = number / 100f
                        "thickness" -> profile.thickness = number
                        "bloom" -> profile.bloom = number
                    }
                    save()
                }
                true
            }
        }
        findPreference<LayoutPreference>("preview")!!
            .findViewById<EdgeAuroraPreviewView>(R.id.edge_aurora_preview)
            .onProfileMoved =
            {
                profile.edge = it.edge
                profile.position = it.position
                save()
            }
        findPreference<Preference>("try")!!.setOnPreferenceClickListener {
            Settings.System.putStringForUser(
                requireContext().contentResolver,
                EdgeAuroraConfig.PREVIEW,
                "${System.currentTimeMillis()}|${profile.id}",
                UserHandle.myUserId(),
            )
            true
        }
        findPreference<Preference>("notification_settings")!!.apply {
            isVisible = !profile.isGroup
            setOnPreferenceClickListener {
                app?.let { selected ->
                    requireContext()
                        .startActivityAsUser(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, profile.id)
                                .putExtra(Settings.EXTRA_APP_UID, selected.info.uid),
                            selected.user,
                        )
                }
                true
            }
        }
        findPreference<Preference>("remove")!!.setOnPreferenceClickListener {
            val items = readProfiles().filter { it.id != profile.id }
            Settings.System.putStringForUser(
                requireContext().contentResolver,
                EdgeAuroraConfig.PROFILES,
                EdgeAuroraConfig.encodeProfiles(items),
                UserHandle.myUserId(),
            )
            finish()
            true
        }
        findPreference<Preference>("notification_settings")!!.isEnabled = false
        bind()
    }

    override fun onResume() {
        super.onResume()
        if (!::profile.isInitialized) return
        requireContext()
            .contentResolver
            .registerContentObserver(
                Settings.System.getUriFor(EdgeAuroraConfig.PROFILES),
                false,
                observer,
                UserHandle.myUserId(),
            )
        observing = true
        reload()
        val generation = ++appLoadGeneration
        AuroraApps.load(requireContext()) { loaded ->
            if (isAdded && generation == appLoadGeneration) {
                apps = loaded
                app = apps.find { it.info.packageName == profile.id }
                findPreference<Preference>("members")!!.isEnabled = true
                findPreference<Preference>("notification_settings")!!.isEnabled = app != null
                bind()
                app?.let { selected ->
                    AuroraApps.icon(requireContext(), selected) { icon ->
                        if (isAdded && generation == appLoadGeneration)
                            findPreference<Preference>("app_info")!!.icon = icon
                    }
                }
            }
        }
    }

    override fun onPause() {
        appLoadGeneration++
        appDialog?.dismiss()
        appDialog = null
        if (observing) requireContext().contentResolver.unregisterContentObserver(observer)
        observing = false
        super.onPause()
    }

    private fun reload() {
        val latest = readProfiles().find { it.id == profile.id }
        if (latest == null) finish()
        else {
            profile = latest.copy()
            bind()
        }
    }

    private fun readProfiles() =
        EdgeAuroraConfig.readProfiles(
            Settings.System.getStringForUser(
                requireContext().contentResolver,
                EdgeAuroraConfig.PROFILES,
                UserHandle.myUserId(),
            )
        )

    private fun save() {
        val items = readProfiles().toMutableList()
        val latest = items.find { it.id == profile.id } ?: return
        if (profile.name != boundProfile.name) latest.name = profile.name
        if (profile.enabled != boundProfile.enabled) latest.enabled = profile.enabled
        if (profile.color != boundProfile.color) latest.color = profile.color
        if (profile.edge != boundProfile.edge) latest.edge = profile.edge
        if (profile.motion != boundProfile.motion) latest.motion = profile.motion
        if (profile.position != boundProfile.position) latest.position = profile.position
        if (profile.length != boundProfile.length) latest.length = profile.length
        if (profile.thickness != boundProfile.thickness) latest.thickness = profile.thickness
        if (profile.bloom != boundProfile.bloom) latest.bloom = profile.bloom
        if (profile.members != boundProfile.members) {
            latest.members.clear()
            latest.members.addAll(profile.members)
        }
        latest.clamp()
        val owned = if (latest.isGroup) latest.members.toSet() else setOf(latest.id)
        items.removeAll { it.id != latest.id && !it.isGroup && it.id in owned }
        items.filter { it.isGroup && it.id != latest.id }.forEach { it.members.removeAll(owned) }
        val encoded = EdgeAuroraConfig.encodeProfiles(items)
        if (
            encoded.length > EdgeAuroraConfig.MAX_PROFILE_LENGTH ||
                !Settings.System.putStringForUser(
                    requireContext().contentResolver,
                    EdgeAuroraConfig.PROFILES,
                    encoded,
                    UserHandle.myUserId(),
                )
        ) {
            Toast.makeText(requireContext(), R.string.edge_aurora_limit, Toast.LENGTH_SHORT).show()
            reload()
            return
        }
        profile = latest.copy()
        bind()
    }

    private fun bind() {
        boundProfile = profile.copy()
        binding = true
        findPreference<SwitchPreferenceCompat>("profile_enabled")!!.isChecked = profile.enabled
        findPreference<EditTextPreference>("name")!!.apply {
            text = profile.name
            summary = profile.name
        }
        findPreference<Preference>("members")!!.summary =
            getString(R.string.edge_aurora_members_count, profile.members.size)
        val title =
            if (profile.isGroup) profile.name.ifBlank { getString(R.string.edge_aurora_new_group) }
            else app?.label ?: profile.name.ifBlank { getString(R.string.edge_aurora_title) }
        activity?.title = title
        findPreference<Preference>("app_info")!!.apply {
            this.title = title
            summary =
                if (profile.isGroup)
                    getString(R.string.edge_aurora_members_count, profile.members.size)
                else profile.id
            if (icon == null)
                icon =
                    if (profile.isGroup)
                        requireContext().getDrawable(android.R.drawable.ic_menu_myplaces)
                    else requireContext().packageManager.defaultActivityIcon
        }
        findPreference<ListPreference>("edge")!!.value = profile.edge.name
        findPreference<ListPreference>("motion")!!.apply {
            value = profile.motion.name
            summary =
                resources
                    .getStringArray(R.array.edge_aurora_motion_descriptions)[profile.motion.ordinal]
        }
        findPreference<ListPreference>("palette")!!.value =
            String.format("#%06X", profile.color and 0xffffff)
        findPreference<ColorPickerPreference>("color")!!.setNewPreviewColor(profile.color)
        findPreference<ListPreference>("recent_colors")!!.apply {
            val colors = recentColors()
            isVisible = colors.isNotEmpty()
            entries = colors.map { String.format("#%06X", it and 0xffffff) }.toTypedArray()
            entryValues = colors.map { it.toString() }.toTypedArray()
            value = profile.color.toString()
        }
        findPreference<EdgeAuroraSliderPreference>("position")!!.apply {
            setMax(((1f - profile.length) * 100).roundToInt())
            bindValue((profile.position * 100).roundToInt())
        }
        findPreference<EdgeAuroraSliderPreference>("length")!!.bindValue(
            (profile.length * 100).roundToInt()
        )
        findPreference<EdgeAuroraSliderPreference>("thickness")!!.bindValue(
            profile.thickness.roundToInt()
        )
        findPreference<EdgeAuroraSliderPreference>("bloom")!!.bindValue(profile.bloom.roundToInt())
        findPreference<LayoutPreference>("preview")!!
            .findViewById<EdgeAuroraPreviewView>(R.id.edge_aurora_preview)
            .profiles = listOf(profile)
        binding = false
    }

    private fun recentColors(): List<Int> =
        runCatching {
                val array =
                    JSONArray(
                        Settings.System.getStringForUser(
                            requireContext().contentResolver,
                            EdgeAuroraConfig.RECENT_COLORS,
                            UserHandle.myUserId(),
                        ) ?: "[]"
                    )
                (0 until minOf(array.length(), 5)).map { array.getInt(it) }
            }
            .getOrDefault(emptyList())

    private fun rememberColor(color: Int) {
        val colors = (listOf(color) + recentColors()).distinct().take(5)
        Settings.System.putStringForUser(
            requireContext().contentResolver,
            EdgeAuroraConfig.RECENT_COLORS,
            JSONArray(colors).toString(),
            UserHandle.myUserId(),
        )
    }

    override fun getMetricsCategory() = MetricsEvent.LUNARIS
}
