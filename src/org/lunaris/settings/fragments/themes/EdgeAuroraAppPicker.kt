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
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import com.android.settings.R
import java.util.concurrent.Executors
import kotlin.math.roundToInt

internal data class AuroraApp(val info: ApplicationInfo, val label: String, val user: UserHandle) {
    val isSystem: Boolean
        get() =
            info.flags and
                (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
}

internal object AuroraApps {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val icons = LruCache<String, Drawable.ConstantState>(64)

    fun load(context: Context, result: (List<AuroraApp>) -> Unit) {
        val appContext = context.applicationContext
        executor.execute {
            val currentUser = Process.myUserHandle()
            val profiles =
                runCatching {
                        appContext.getSystemService(UserManager::class.java)?.userProfiles.orEmpty()
                    }
                    .getOrDefault(emptyList())
            val apps =
                (listOf(currentUser) + profiles)
                    .distinct()
                    .flatMap { user ->
                        val pm =
                            runCatching {
                                    if (user == currentUser) appContext.packageManager
                                    else appContext.createContextAsUser(user, 0).packageManager
                                }
                                .getOrElse {
                                    Log.w("EdgeAuroraSettings", "Unable to access app profile", it)
                                    return@flatMap emptyList()
                                }
                        val installed =
                            runCatching { pm.getInstalledApplications(0) }
                                .getOrElse {
                                    Log.w("EdgeAuroraSettings", "Unable to load installed apps", it)
                                    emptyList()
                                }
                        installed.map { info ->
                            val label =
                                runCatching { info.loadLabel(pm).toString().trim() }
                                    .getOrDefault("")
                                    .ifBlank {
                                        info.nonLocalizedLabel?.toString()?.takeIf {
                                            it.isNotBlank()
                                        } ?: info.packageName
                                    }
                            AuroraApp(info, label, user)
                        }
                    }
                    .distinctBy { it.info.packageName }
                    .sortedWith(
                        compareBy<AuroraApp> { it.label.lowercase(java.util.Locale.getDefault()) }
                            .thenBy { it.info.packageName }
                    )
            mainHandler.post { result(apps) }
        }
    }

    fun icon(context: Context, app: AuroraApp, result: (Drawable) -> Unit) {
        val appContext = context.applicationContext
        val key = "${app.info.uid}:${app.info.packageName}"
        icons.get(key)?.let {
            result(it.newDrawable(context.resources).mutate())
            return
        }
        executor.execute {
            val drawable =
                runCatching {
                        val pm =
                            if (app.user == Process.myUserHandle()) appContext.packageManager
                            else appContext.createContextAsUser(app.user, 0).packageManager
                        val source = pm.getUserBadgedIcon(app.info.loadIcon(pm), app.user)
                        val size =
                            (40 * appContext.resources.displayMetrics.density)
                                .roundToInt()
                                .coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                        source.setBounds(0, 0, size, size)
                        source.draw(Canvas(bitmap))
                        BitmapDrawable(appContext.resources, bitmap)
                    }
                    .getOrElse { appContext.packageManager.defaultActivityIcon }
            drawable.constantState?.let { icons.put(key, it) }
            mainHandler.post {
                result(
                    drawable.constantState?.newDrawable(appContext.resources)?.mutate() ?: drawable
                )
            }
        }
    }
}

internal object AuroraAppPicker {
    fun show(
        context: Context,
        title: Int,
        apps: List<AuroraApp>,
        selected: Set<String>? = null,
        onSelected: (Set<String>) -> Unit,
    ): AlertDialog {
        val multiple = selected != null
        val selection = selected.orEmpty().toMutableSet()
        val density = context.resources.displayMetrics.density
        fun px(value: Int) = (value * density).roundToInt()
        val colors =
            context.obtainStyledAttributes(
                intArrayOf(
                    android.R.attr.textColorPrimary,
                    android.R.attr.textColorSecondary,
                    android.R.attr.colorControlHighlight,
                )
            )
        val primary = colors.getColor(0, Color.WHITE)
        val secondary = colors.getColor(1, Color.GRAY)
        val surface = colors.getColor(2, 0x1fffffff)
        colors.recycle()
        val container =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(px(16), px(4), px(16), 0)
                isFocusableInTouchMode = true
            }
        val searchBar =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPaddingRelative(px(16), 0, px(4), 0)
                background =
                    GradientDrawable().apply {
                        setColor(surface)
                        cornerRadius = px(28).toFloat()
                    }
            }
        searchBar.addView(
            ImageView(context).apply {
                setImageResource(android.R.drawable.ic_menu_search)
                imageTintList = ColorStateList.valueOf(secondary)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LinearLayout.LayoutParams(px(20), px(20)),
        )
        val search =
            EditText(context).apply {
                setHint(R.string.edge_aurora_search)
                background = null
                setPaddingRelative(px(12), px(8), 0, px(8))
                minHeight = px(56)
                textSize = 16f
                setTextColor(primary)
                setHintTextColor(secondary)
                isSingleLine = true
                inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            }
        searchBar.addView(
            search,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        val clearSearch =
            ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                imageTintList = ColorStateList.valueOf(secondary)
                contentDescription = context.getString(R.string.common_clear_search)
                val background = TypedValue()
                if (
                    context.theme.resolveAttribute(
                        android.R.attr.selectableItemBackgroundBorderless,
                        background,
                        true,
                    )
                ) {
                    setBackgroundResource(background.resourceId)
                }
                visibility = View.INVISIBLE
                setOnClickListener { search.text.clear() }
            }
        searchBar.addView(clearSearch, LinearLayout.LayoutParams(px(48), px(48)))
        val showSystem =
            Switch(context).apply {
                setText(R.string.show_system_apps)
                setTextColor(primary)
                textSize = 14f
                minHeight = px(56)
                switchPadding = px(12)
                setPaddingRelative(px(12), 0, px(12), 0)
                isChecked = false
            }
        val count =
            TextView(context).apply {
                textSize = 12f
                setTextColor(secondary)
                setPaddingRelative(px(12), 0, px(12), px(8))
            }
        val list =
            ListView(context).apply {
                divider = null
                dividerHeight = 0
                setSelector(android.R.color.transparent)
                clipToPadding = false
            }
        val empty =
            TextView(context).apply {
                setText(R.string.edge_aurora_no_apps)
                setTextColor(secondary)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(px(24), px(24), px(24), px(24))
            }
        var query = ""
        var filtered = apps.filter { !it.isSystem }
        val duplicateLabels = apps.groupingBy { it.label }.eachCount().filterValues { it > 1 }.keys
        fun updateCount() {
            val available =
                context.resources.getQuantityString(
                    R.plurals.edge_aurora_picker_app_count,
                    filtered.size,
                    filtered.size,
                )
            count.text =
                if (multiple)
                    context.getString(
                        R.string.edge_aurora_picker_selected,
                        available,
                        selection.size,
                    )
                else available
        }
        val adapter =
            object : BaseAdapter() {
                override fun getCount() = filtered.size

                override fun getItem(position: Int) = filtered[position]

                override fun getItemId(position: Int) = position.toLong()

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val row =
                        convertView
                            ?: LayoutInflater.from(context)
                                .inflate(R.layout.edge_aurora_app_item, parent, false)
                    val app = getItem(position)
                    row.findViewById<TextView>(android.R.id.title).text = app.label
                    row.findViewById<TextView>(android.R.id.summary).apply {
                        text = app.info.packageName
                        visibility =
                            if (
                                showSystem.isChecked ||
                                    app.label in duplicateLabels ||
                                    (query.isNotEmpty() && !app.label.contains(query, true))
                            )
                                View.VISIBLE
                            else View.GONE
                    }
                    row.findViewById<CheckBox>(android.R.id.checkbox).apply {
                        visibility = if (multiple) View.VISIBLE else View.GONE
                        isChecked = app.info.packageName in selection
                    }
                    row.isActivated = multiple && app.info.packageName in selection
                    row.accessibilityDelegate =
                        if (multiple)
                            object : View.AccessibilityDelegate() {
                                override fun onInitializeAccessibilityNodeInfo(
                                    host: View,
                                    info: AccessibilityNodeInfo,
                                ) {
                                    super.onInitializeAccessibilityNodeInfo(host, info)
                                    info.className = CheckBox::class.java.name
                                    info.isCheckable = true
                                    info.isChecked = app.info.packageName in selection
                                }
                            }
                        else null
                    val icon = row.findViewById<ImageView>(android.R.id.icon)
                    val key = "${app.info.uid}:${app.info.packageName}"
                    icon.tag = key
                    icon.setImageDrawable(context.packageManager.defaultActivityIcon)
                    AuroraApps.icon(context, app) { if (icon.tag == key) icon.setImageDrawable(it) }
                    return row
                }
            }
        list.adapter = adapter
        container.addView(searchBar)
        container.addView(showSystem)
        container.addView(count)
        val results =
            FrameLayout(context).apply {
                addView(
                    list,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                addView(
                    empty,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        container.addView(
            results,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                minOf(px(360), context.resources.displayMetrics.heightPixels / 2),
                1f,
            ),
        )
        list.emptyView = empty
        fun updateFilter() {
            query = search.text.toString().trim()
            filtered =
                apps.filter {
                    (showSystem.isChecked || !it.isSystem) &&
                        (it.label.contains(query, true) ||
                            it.info.packageName.contains(query, true))
                }
            clearSearch.visibility = if (search.text.isEmpty()) View.INVISIBLE else View.VISIBLE
            empty.setText(
                if (query.isEmpty() && !showSystem.isChecked) R.string.edge_aurora_no_user_apps
                else R.string.edge_aurora_no_apps
            )
            adapter.notifyDataSetChanged()
            list.setSelection(0)
            updateCount()
        }
        val builder =
            AlertDialog.Builder(context)
                .setTitle(title)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
        if (multiple)
            builder.setPositiveButton(android.R.string.ok) { _, _ -> onSelected(selection.toSet()) }
        val dialog = builder.create()
        list.setOnItemClickListener { _, _, position, _ ->
            val app = adapter.getItem(position)
            if (multiple) {
                if (!selection.add(app.info.packageName)) selection.remove(app.info.packageName)
                adapter.notifyDataSetChanged()
                updateCount()
            } else {
                dialog.dismiss()
                onSelected(setOf(app.info.packageName))
            }
        }
        search.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {}

                override fun afterTextChanged(s: Editable?) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    updateFilter()
                }
            }
        )
        showSystem.setOnCheckedChangeListener { _, _ -> updateFilter() }
        updateFilter()
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
        dialog.show()
        dialog.window?.setLayout(
            minOf(px(560), context.resources.displayMetrics.widthPixels - px(32)),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        container.requestFocus()
        return dialog
    }
}
