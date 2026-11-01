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
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.internal.util.lunaris.EdgeAuroraConfig
import com.android.internal.util.lunaris.EdgeAuroraConfig.Profile
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settings.core.SubSettingLauncher
import com.android.settings.search.BaseSearchIndexProvider
import com.android.settingslib.search.SearchIndexable
import com.android.settingslib.widget.LayoutPreference
import java.util.UUID
import org.json.JSONArray

@SearchIndexable
class EdgeAuroraSettings : SettingsPreferenceFragment() {
    private val main = Handler(Looper.getMainLooper())
    private var apps = emptyList<AuroraApp>()
    private var appsLoaded = false
    private var appLoadGeneration = 0
    private var appDialog: AlertDialog? = null
    private var observing = false
    private val observer =
        object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean) {
                if (isAdded) {
                    refreshProfiles()
                    refreshQuietApps()
                    refreshOpenAction()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.edge_aurora_settings)
        findPreference<Preference>("add_app")!!.setOnPreferenceClickListener {
            pickApp()
            true
        }
        findPreference<Preference>("add_group")!!.setOnPreferenceClickListener {
            addProfile("group:${UUID.randomUUID()}", getString(R.string.edge_aurora_new_group))
            true
        }
        findPreference<Preference>(EdgeAuroraConfig.QUIET_APPS)!!.setOnPreferenceClickListener {
            val selected =
                EdgeAuroraConfig.readPackages(
                    Settings.System.getStringForUser(
                        requireContext().contentResolver,
                        EdgeAuroraConfig.QUIET_APPS,
                        UserHandle.myUserId(),
                    )
                )
            appDialog?.dismiss()
            appDialog =
                AuroraAppPicker.show(requireContext(), R.string.edge_aurora_quiet, apps, selected) {
                    if (
                        !Settings.System.putStringForUser(
                            requireContext().contentResolver,
                            EdgeAuroraConfig.QUIET_APPS,
                            JSONArray(it.toList()).toString(),
                            UserHandle.myUserId(),
                        )
                    )
                        Toast.makeText(
                                requireContext(),
                                R.string.edge_aurora_save_failed,
                                Toast.LENGTH_SHORT,
                            )
                            .show()
                    refreshQuietApps()
                }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        requireContext()
            .contentResolver
            .registerContentObserver(
                Settings.System.getUriFor(EdgeAuroraConfig.PROFILES),
                false,
                observer,
                UserHandle.myUserId(),
            )
        requireContext()
            .contentResolver
            .registerContentObserver(
                Settings.System.getUriFor(EdgeAuroraConfig.QUIET_APPS),
                false,
                observer,
                UserHandle.myUserId(),
            )
        requireContext()
            .contentResolver
            .registerContentObserver(
                Settings.System.getUriFor(EdgeAuroraConfig.OPEN_ACTION),
                false,
                observer,
                UserHandle.myUserId(),
            )
        observing = true
        refreshProfiles()
        refreshQuietApps()
        refreshOpenAction()
        loadApps()
    }

    private fun loadApps() {
        val generation = ++appLoadGeneration
        findPreference<Preference>("add_app")!!.isEnabled = appsLoaded
        findPreference<Preference>(EdgeAuroraConfig.QUIET_APPS)!!.isEnabled = appsLoaded
        AuroraApps.load(requireContext()) { loaded ->
            if (isAdded && generation == appLoadGeneration) {
                apps = loaded
                appsLoaded = true
                findPreference<Preference>("add_app")!!.isEnabled = true
                findPreference<Preference>(EdgeAuroraConfig.QUIET_APPS)!!.isEnabled = true
                refreshProfiles()
                refreshQuietApps()
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

    private fun refreshOpenAction() {
        val action =
            Settings.System.getIntForUser(
                    requireContext().contentResolver,
                    EdgeAuroraConfig.OPEN_ACTION,
                    EdgeAuroraConfig.OPEN_ON_TAP,
                    UserHandle.myUserId(),
                )
                .coerceIn(EdgeAuroraConfig.OPEN_ON_TAP, EdgeAuroraConfig.OPEN_ON_HOLD)
        findPreference<ListPreference>(EdgeAuroraConfig.OPEN_ACTION)!!.value = action.toString()
    }

    private fun refreshQuietApps() {
        val selected =
            EdgeAuroraConfig.readPackages(
                Settings.System.getStringForUser(
                    requireContext().contentResolver,
                    EdgeAuroraConfig.QUIET_APPS,
                    UserHandle.myUserId(),
                )
            )
        findPreference<Preference>(EdgeAuroraConfig.QUIET_APPS)!!.summary =
            if (selected.isEmpty()) getString(R.string.edge_aurora_quiet_summary)
            else getString(R.string.edge_aurora_members_count, selected.size)
    }

    private fun profiles() =
        EdgeAuroraConfig.readProfiles(
            Settings.System.getStringForUser(
                requireContext().contentResolver,
                EdgeAuroraConfig.PROFILES,
                UserHandle.myUserId(),
            )
        )

    private fun refreshProfiles() {
        val items = profiles()
        findPreference<LayoutPreference>("preview")!!
            .findViewById<EdgeAuroraPreviewView>(R.id.edge_aurora_preview)
            .profiles = items
        val category = findPreference<PreferenceCategory>("profiles")!!
        category.removeAll()
        if (!appsLoaded) {
            category.addPreference(
                Preference(requireContext()).apply {
                    setTitle(R.string.edge_aurora_loading)
                    isSelectable = false
                }
            )
            return
        }
        val edges = resources.getStringArray(R.array.edge_aurora_edges)
        val motions = resources.getStringArray(R.array.edge_aurora_motions)
        val byPackage = apps.associateBy { it.info.packageName }
        items.forEach { profile ->
            val app = byPackage[profile.id]
            val preference =
                Preference(requireContext()).apply {
                    key = profile.id
                    title =
                        if (profile.isGroup)
                            profile.name.ifBlank { getString(R.string.edge_aurora_new_group) }
                        else
                            app?.label
                                ?: profile.name.ifBlank {
                                    getString(R.string.edge_aurora_unavailable_app)
                                }
                    summary =
                        getString(
                            R.string.edge_aurora_profile_summary,
                            getString(
                                if (profile.enabled) R.string.edge_aurora_on
                                else R.string.edge_aurora_off
                            ),
                            edges[profile.edge.ordinal],
                            motions[profile.motion.ordinal],
                        ) +
                            if (profile.isGroup)
                                "\n" +
                                    getString(
                                        R.string.edge_aurora_members_count,
                                        profile.members.size,
                                    )
                            else if (app == null) "\n" + profile.id else ""
                    icon =
                        if (profile.isGroup)
                            requireContext().getDrawable(android.R.drawable.ic_menu_myplaces)
                        else requireContext().packageManager.defaultActivityIcon
                    setOnPreferenceClickListener {
                        edit(profile.id)
                        true
                    }
                }
            category.addPreference(preference)
            if (app != null) AuroraApps.icon(requireContext(), app) { preference.icon = it }
        }
    }

    private fun edit(id: String) {
        SubSettingLauncher(requireContext())
            .setDestination(EdgeAuroraProfileSettings::class.java.name)
            .setArguments(Bundle().apply { putString("profile_id", id) })
            .setTitleRes(R.string.edge_aurora_title)
            .setSourceMetricsCategory(metricsCategory)
            .launch()
    }

    private fun addProfile(id: String, name: String = "") {
        val items = profiles().toMutableList()
        if (!id.startsWith("group:")) items.filter { it.isGroup }.forEach { it.members.remove(id) }
        if (items.any { it.id == id }) {
            if (saveProfiles(items)) edit(id)
            return
        }
        val profile =
            Profile(id).apply {
                this.name = name
                color = EdgeAuroraConfig.PALETTE[items.size % EdgeAuroraConfig.PALETTE.size]
                val right = items.count { it.edge == EdgeAuroraConfig.Edge.RIGHT }
                val left = items.count { it.edge == EdgeAuroraConfig.Edge.LEFT }
                edge =
                    if (right < 3) EdgeAuroraConfig.Edge.RIGHT
                    else if (left < 3) EdgeAuroraConfig.Edge.LEFT else EdgeAuroraConfig.Edge.TOP
                position =
                    if (edge == EdgeAuroraConfig.Edge.RIGHT) .16f + right * .22f
                    else if (edge == EdgeAuroraConfig.Edge.LEFT) .16f + left * .22f else .2f
                length = .18f
                clamp()
            }
        items.add(profile)
        if (saveProfiles(items)) edit(id)
    }

    private fun saveProfiles(items: List<Profile>): Boolean {
        val encoded = EdgeAuroraConfig.encodeProfiles(items)
        if (encoded.length > EdgeAuroraConfig.MAX_PROFILE_LENGTH) {
            Toast.makeText(requireContext(), R.string.edge_aurora_limit, Toast.LENGTH_SHORT).show()
            return false
        }
        return Settings.System.putStringForUser(
            requireContext().contentResolver,
            EdgeAuroraConfig.PROFILES,
            encoded,
            UserHandle.myUserId(),
        )
    }

    private fun pickApp() {
        appDialog?.dismiss()
        appDialog =
            AuroraAppPicker.show(requireContext(), R.string.edge_aurora_add_app, apps) {
                val packageName = it.first()
                addProfile(
                    packageName,
                    apps.find { app -> app.info.packageName == packageName }?.label.orEmpty(),
                )
            }
    }

    override fun getMetricsCategory() = MetricsEvent.LUNARIS

    companion object {
        @JvmField
        val SEARCH_INDEX_DATA_PROVIDER = BaseSearchIndexProvider(R.xml.edge_aurora_settings)
    }
}
