/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.settings.fragments.statusbar

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import com.android.internal.logging.nano.MetricsProto
import com.android.internal.util.lunaris.LunarisBarSettings
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lunaris.settings.preferences.CustomSeekBarPreference
import org.lunaris.settings.preferences.colorpicker.ColorPickerPreference
import org.lunaris.settings.preferences.colorpicker.LunarisBarColorPickerPreference

/** Shared binding for the XML screens. All values belong to LunarisBarSettings, not SharedPreferences. */
abstract class LunarisBarPreferenceFragment : SettingsPreferenceFragment() {
    internal val editor by lazy { LunarisBarEditor(requireContext()) }
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = Runnable {
        if (isAdded) {
            if (!editor.isCurrentUser()) {
                onUserChanged()
                return@Runnable
            }
            editor.reload()
            refreshScreen()
        }
    }
    private val dynamicPreferences = mutableMapOf<String, Preference>()
    private var dialog: AlertDialog? = null
    private var appPickerJob: Job? = null
    private var observing = false
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = scheduleRefresh()
    }
    private val userReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onUserChanged()
    }

    override fun getMetricsCategory() = MetricsProto.MetricsEvent.LUNARIS

    override fun onResume() {
        super.onResume()
        if (!editor.isCurrentUser()) {
            onUserChanged()
            return
        }
        editor.reload()
        refreshScreen()
        for (key in LunarisBarSettings.systemDefaults().keys + LunarisBarSettings.KEY) {
            requireContext().contentResolver.registerContentObserver(
                Settings.System.getUriFor(key), false, observer, UserHandle.USER_ALL,
            )
        }
        requireContext().registerReceiver(
            userReceiver, IntentFilter(Intent.ACTION_USER_SWITCHED), Context.RECEIVER_EXPORTED,
        )
        observing = true
    }

    override fun onPause() {
        if (observing) {
            requireContext().contentResolver.unregisterContentObserver(observer)
            requireContext().unregisterReceiver(userReceiver)
            observing = false
        }
        appPickerJob?.cancel()
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    override fun onDestroyView() {
        handler.removeCallbacks(refresh)
        dismissDialogs()
        super.onDestroyView()
    }

    private fun dismissDialogs() {
        dialog?.dismiss()
        dialog = null
        dynamicPreferences.values.filterIsInstance<LunarisBarColorPickerPreference>()
            .forEach { it.dismissDialog() }
    }

    protected open fun onUserChanged() {
        dismissDialogs()
        finish()
    }

    protected abstract fun refreshPreferences()

    private fun refreshScreen() {
        refreshPreferences()
        val iterator = dynamicPreferences.values.iterator()
        while (iterator.hasNext()) {
            val preference = iterator.next()
            if (preference.parent == null) {
                (preference as? LunarisBarColorPickerPreference)?.dismissDialog()
                iterator.remove()
            }
        }
    }

    internal fun scheduleRefresh() {
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    internal fun changed(success: Boolean): Boolean {
        if (!success) Toast.makeText(requireContext(), R.string.lunaris_bar_save_error, Toast.LENGTH_SHORT).show()
        scheduleRefresh()
        return success
    }

    internal fun label(key: String): String = lunarisBarText(requireContext(), key)

    internal fun template(parent: PreferenceGroup, kind: String, key: String, title: String = key): Preference {
        // Dialog fragments hold their Preference instance. Keep that instance across
        // refreshes so an open or restored editor still updates the visible row.
        val preference = dynamicPreferences.getOrPut(key) {
            val templates = preferenceManager.inflateFromResource(
                requireContext(), R.xml.lunaris_bar_preference_templates, null,
            )
            requireNotNull(templates.findPreference<Preference>(kind)).also {
                templates.removePreference(it)
            }
        }
        // Reattaching a preference can dispatch its initial value. Never save that
        // default over the existing configuration or an unsaved card draft.
        preference.onPreferenceChangeListener = null
        preference.onPreferenceClickListener = null
        preference.key = key
        preference.title = label(title)
        preference.isPersistent = false
        preference.order = parent.preferenceCount
        parent.addPreference(preference)
        return preference
    }

    internal fun category(parent: PreferenceGroup, key: String, title: String): PreferenceGroup {
        // PreferenceCategory cannot contain another PreferenceCategory. Use its standard
        // heading style for the light/dark and component headings within a color category.
        object : Preference(requireContext(), null, androidx.preference.R.attr.preferenceCategoryStyle) {
            override fun onBindViewHolder(holder: PreferenceViewHolder) {
                super.onBindViewHolder(holder)
                holder.itemView.isAccessibilityHeading = true
            }
        }.also {
            it.key = key
            it.title = label(title)
            it.isSelectable = false
            it.order = parent.preferenceCount
            parent.addPreference(it)
        }
        return parent
    }

    internal fun bindOption(preference: Preference, option: LunarisBarOption, value: Int, save: (Int) -> Boolean) {
        preference.onPreferenceChangeListener = null
        preference.isPersistent = false
        when (preference) {
            is SwitchPreferenceCompat -> preference.isChecked = value != 0
            is ListPreference -> {
                preference.entries = option.choices.map(::label).toTypedArray()
                preference.entryValues = option.choices.indices.map(Int::toString).toTypedArray()
                preference.value = value.toString()
                val description = requireContext().resources.getIdentifier(
                    "lunaris_bar_${option.key}_description", "string", requireContext().packageName,
                )
                preference.summary = if (description == 0) preference.entry
                    else "${preference.entry}\n${getString(description)}"
            }
            is CustomSeekBarPreference -> {
                preference.setMax(option.max)
                preference.setMin(option.min)
                preference.setDefaultValue(option.default, false)
                preference.setValue(value)
            }
        }
        preference.setOnPreferenceChangeListener { _, next ->
            val number = when (next) {
                is Boolean -> if (next) 1 else 0
                is Int -> next
                else -> next.toString().toInt()
            }
            save(number)
        }
    }

    internal fun option(parent: PreferenceGroup, option: LunarisBarOption, value: Int = editor.read(option), save: (Int) -> Boolean = { changed(editor.set(option, it)) }): Preference {
        val preference = template(parent, option.kind, option.key)
        bindOption(preference, option, value, save)
        return preference
    }

    internal fun color(parent: PreferenceGroup, key: String, title: String, value: Int, fallback: Int, save: (Int) -> Boolean) {
        val preference = template(parent, "color", key, title) as ColorPickerPreference
        preference.setDefaultValue(fallback)
        preference.setNewPreviewColor(value)
        preference.setOnPreferenceChangeListener { _, next -> save(next as Int) }
    }

    internal fun action(parent: PreferenceGroup, key: String, title: String = key, click: () -> Unit): Preference =
        template(parent, "action", key, title).also {
            it.setOnPreferenceClickListener { click(); true }
        }

    internal fun textPreference(parent: PreferenceGroup, key: String, title: String, value: String, maxLength: Int = 8192, save: (String) -> Unit) {
        val preference = template(parent, "text", key, title) as EditTextPreference
        preference.text = value
        preference.summary = value
        preference.setOnBindEditTextListener { edit ->
            edit.filters = arrayOf(android.text.InputFilter.LengthFilter(maxLength))
        }
        preference.setOnPreferenceChangeListener { _, next ->
            save(next.toString().take(maxLength))
            scheduleRefresh()
            true
        }
    }

    internal fun showDialog(builder: AlertDialog.Builder): AlertDialog {
        dialog?.dismiss()
        return builder.create().also { dialog = it; it.show() }
    }

    internal fun choose(title: String, labels: List<String>, selected: Int = -1, select: (Int) -> Unit) {
        showDialog(AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), selected) { selectedDialog, index ->
                selectedDialog.dismiss()
                select(index)
            }
            .setNegativeButton(R.string.lunaris_bar_cancel, null))
    }

    internal fun chooseApp(select: (String, String) -> Unit) {
        val context = requireContext()
        val user = android.app.ActivityManager.getCurrentUser()
        appPickerJob?.cancel()
        appPickerJob = lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .map { info ->
                        info.loadLabel(pm).toString() to "app:" + ComponentName(
                            info.activityInfo.packageName, info.activityInfo.name,
                        ).flattenToString()
                    }.sortedBy { it.first.lowercase() }
            }
            if (!isResumed || user != android.app.ActivityManager.getCurrentUser()) return@launch
            val view = layoutInflater.inflate(R.layout.lunaris_bar_app_picker, null)
            val search = view.findViewById<EditText>(R.id.lunaris_bar_app_search)
            val list = view.findViewById<ListView>(R.id.lunaris_bar_app_list)
            var filtered = apps
            val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, apps.map { it.first }.toMutableList())
            list.adapter = adapter
            val picker = showDialog(AlertDialog.Builder(context)
                .setTitle(R.string.lunaris_bar_choose_app).setView(view)
                .setNegativeButton(R.string.lunaris_bar_cancel, null))
            search.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    filtered = apps.filter { it.first.contains(s?.toString().orEmpty(), true) }
                    adapter.clear()
                    adapter.addAll(filtered.map { it.first })
                }
                override fun afterTextChanged(s: Editable?) {}
            })
            list.setOnItemClickListener { _, _, position, _ ->
                picker.dismiss()
                select(filtered[position].first, filtered[position].second)
            }
        }
    }

    internal fun paletteColor(index: Int, foreground: Boolean): Int {
        if (index == 4 || index == 6) return if (foreground) Color.WHITE else Color.BLACK
        val attr = when (index) {
            2 -> if (foreground) com.google.android.material.R.attr.colorOnSecondaryContainer else com.google.android.material.R.attr.colorSecondaryContainer
            5 -> if (foreground) com.google.android.material.R.attr.colorOnPrimary else androidx.appcompat.R.attr.colorPrimary
            else -> if (foreground) com.google.android.material.R.attr.colorOnSurface else com.google.android.material.R.attr.colorSurface
        }
        val colors = requireContext().obtainStyledAttributes(intArrayOf(attr))
        return try { colors.getColor(0, if (foreground) Color.WHITE else Color.BLACK) } finally { colors.recycle() }
    }
}

internal fun lunarisBarText(context: Context, key: String): String {
    val base = key.removeSuffix("_day").removeSuffix("_night")
    val candidates = if (base.startsWith("feature_")) listOf(
        "card_icon_shape", "card_button_shape", "left_background", "left_foreground",
        "right_background", "right_foreground", "progress_background", "progress_foreground",
        "track_background", "track_foreground", "show_battery", "progress_palette", "track_palette",
        "left_palette", "right_palette", "left_content", "right_content", "mini_shape", "right_shape",
        "background", "foreground", "palette", "haptics",
    ).filter { base.endsWith("_$it") } else emptyList()
    for (candidate in listOf(key, base) + candidates) {
        val id = context.resources.getIdentifier("lunaris_bar_$candidate", "string", context.packageName)
        if (id != 0) return context.getString(id)
    }
    return base.replace('_', ' ')
}
