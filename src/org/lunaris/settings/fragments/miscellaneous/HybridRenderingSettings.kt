/*
 * Copyright (C) 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.settings.fragments.miscellaneous

import android.app.AlertDialog
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings.Global
import android.widget.Toast
import androidx.preference.Preference
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settings.search.BaseSearchIndexProvider
import com.android.settingslib.search.SearchIndexable
import org.lunaris.settings.fragments.themes.AuroraApp
import org.lunaris.settings.fragments.themes.AuroraAppPicker
import org.lunaris.settings.fragments.themes.AuroraApps

@SearchIndexable
class HybridRenderingSettings : SettingsPreferenceFragment() {
    private lateinit var appsPreference: Preference
    private var apps = emptyList<AuroraApp>()
    private var picker: AlertDialog? = null
    private var request = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.hybrid_rendering)
        appsPreference = findPreference<Preference>(Global.HYBRID_RENDERING_APPS)!!
        appsPreference.setOnPreferenceClickListener {
            pickApps()
            true
        }
    }

    private fun selected(resolver: ContentResolver) =
        Global.getString(resolver, Global.HYBRID_RENDERING_APPS).orEmpty()
            .split(':').filter(String::isNotEmpty).toSet()

    override fun onResume() {
        super.onResume()
        val context = requireContext()
        val generation = ++request
        appsPreference.isEnabled = false
        AuroraApps.load(context) { installed ->
            if (generation != request || !isResumed) return@load

            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val pm = context.packageManager
            val home = pm.getHomeActivities(mutableListOf())?.packageName
                ?: pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            val systemUi = ComponentName.unflattenFromString(
                context.getString(com.android.internal.R.string.config_systemUIServiceComponent)
            )?.packageName
            val reserved = setOf("android", home, systemUi)
            apps = installed.filterNot { it.info.packageName in reserved }
            updateSummary(selected(context.contentResolver))
            appsPreference.isEnabled = true
        }
    }

    private fun updateSummary(packages: Set<String>) {
        val labels = apps.filter { it.info.packageName in packages }.map { it.label }
        appsPreference.summary = if (labels.isEmpty()) {
            getString(R.string.no_apps_selected)
        } else {
            resources.getQuantityString(R.plurals.hybrid_rendering_selection, labels.size,
                labels.size, android.icu.text.ListFormatter.getInstance().format(labels))
        }
    }

    private fun pickApps() {
        val context = requireContext()
        val resolver = context.contentResolver
        val packages = selected(resolver)
        val initial = apps.map { it.info.packageName }.filterTo(mutableSetOf()) { it in packages }
        val ordered = apps.sortedByDescending { it.info.packageName in initial }
        picker = AuroraAppPicker.show(context, R.string.hybrid_rendering_apps, ordered, initial,
            showSystemInitially = apps.any { it.isSystem && it.info.packageName in initial }) {
            val updated = (selected(resolver) - (initial - it)) + (it - initial)
            if (Global.putString(resolver, Global.HYBRID_RENDERING_APPS,
                    updated.sorted().joinToString(":"))) {
                updateSummary(updated)
            } else {
                Toast.makeText(context, R.string.hybrid_rendering_save_failed, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    override fun onPause() {
        request++
        picker?.dismiss()
        picker = null
        super.onPause()
    }

    override fun getMetricsCategory() = MetricsEvent.LUNARIS

    companion object {
        @JvmField
        val SEARCH_INDEX_DATA_PROVIDER = BaseSearchIndexProvider(R.xml.hybrid_rendering)
    }
}
