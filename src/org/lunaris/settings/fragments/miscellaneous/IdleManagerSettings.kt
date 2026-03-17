/*
 * SPDX-FileCopyrightText: 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.settings.fragments.miscellaneous

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import com.android.settings.R
import com.android.settingslib.spa.framework.theme.SettingsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

internal enum class IdlePolicy {
    BALANCED,
    AGGRESSIVE,
    CUSTOM;

    val defaultMinutes: Int
        get() = when (this) {
            BALANCED -> 60
            AGGRESSIVE -> 15
            CUSTOM -> 30
        }

    companion object {
        fun fromString(value: String): IdlePolicy =
            entries.firstOrNull { it.name == value } ?: BALANCED
    }
}

private val CRITICAL_SYSTEM_PACKAGES = setOf(
    "android",
    "com.android.systemui",
    "com.android.phone",
    "com.android.providers.telephony",
    "com.android.server.telecom"
)

private data class IdleAppItem(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val isSystem: Boolean
)

private data class IdleAppConfigUi(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val isSystem: Boolean,
    val policy: IdlePolicy,
    val customTimeoutMinutes: Int
)

private data class KillStats(val count: Int, val lastKillMs: Long)

private fun readEnabled(context: Context): Boolean =
    Settings.Secure.getInt(context.contentResolver, Settings.Secure.IDLE_MANAGER, 1) == 1

private fun writeEnabled(context: Context, enabled: Boolean) {
    Settings.Secure.putInt(
        context.contentResolver,
        Settings.Secure.IDLE_MANAGER,
        if (enabled) 1 else 0
    )
}

private fun readAppConfigs(context: Context): LinkedHashMap<String, IdleAppConfigUi> {
    val result = linkedMapOf<String, IdleAppConfigUi>()
    val json = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.IDLE_MANAGER_APPS
    ) ?: return result
    if (json.isBlank()) return result
    try {
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val pkg = obj.getString("package")
            val pol = IdlePolicy.fromString(obj.optString("policy", "BALANCED"))
            val mins = obj.optInt("timeout_minutes", pol.defaultMinutes)
            result[pkg] = IdleAppConfigUi(
                packageName = pkg,
                label = pkg,
                icon = android.graphics.drawable.ColorDrawable(0),
                isSystem = false,
                policy = pol,
                customTimeoutMinutes = mins
            )
        }
    } catch (_: Exception) {}
    return result
}

private fun writeAppConfigs(context: Context, configs: Map<String, IdleAppConfigUi>) {
    val arr = JSONArray()
    configs.values.forEach { cfg ->
        arr.put(JSONObject().apply {
            put("package", cfg.packageName)
            put("policy", cfg.policy.name)
            put("timeout_minutes", cfg.customTimeoutMinutes)
        })
    }
    Settings.Secure.putString(
        context.contentResolver,
        Settings.Secure.IDLE_MANAGER_APPS,
        arr.toString()
    )
}

private fun readKillStats(context: Context, packageName: String): KillStats {
    val json = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.IDLE_MANAGER_KILL_STATS
    ) ?: return KillStats(0, 0L)
    if (json.isBlank()) return KillStats(0, 0L)
    return try {
        val entry = JSONObject(json).optJSONObject(packageName)
            ?: return KillStats(0, 0L)
        KillStats(
            count = entry.optInt("count", 0),
            lastKillMs = entry.optLong("last_kill", 0L)
        )
    } catch (_: Exception) {
        KillStats(0, 0L)
    }
}

private fun formatLastKilled(lastKillMs: Long): String? {
    if (lastKillMs == 0L) return null
    val elapsed = System.currentTimeMillis() - lastKillMs
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
    val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
    val days = TimeUnit.MILLISECONDS.toDays(elapsed)
    return when {
        minutes < 1  -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}

class IdleManagerSettings : Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().title = getString(R.string.idle_manager_title)
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        return androidx.compose.ui.platform.ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy
                    .DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                SettingsTheme {
                    IdleManagerContent(requireContext())
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IdleManagerContent(context: Context) {
    val pm = context.packageManager
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var allApps by remember { mutableStateOf(listOf<IdleAppItem>()) }
    var configuredApps by remember { mutableStateOf(linkedMapOf<String, IdleAppConfigUi>()) }
    var globalEnabled by remember { mutableStateOf(true) }

    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<IdleAppConfigUi?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    fun mergeConfigs(
        raw: LinkedHashMap<String, IdleAppConfigUi>,
        apps: List<IdleAppItem>
    ): LinkedHashMap<String, IdleAppConfigUi> {
        val merged = linkedMapOf<String, IdleAppConfigUi>()
        raw.forEach { (pkg, cfg) ->
            val app = apps.find { it.packageName == pkg } ?: return@forEach
            merged[pkg] = cfg.copy(label = app.label, icon = app.icon, isSystem = app.isSystem)
        }
        return merged
    }

    fun loadConfigs() {
        globalEnabled = readEnabled(context)
        configuredApps = mergeConfigs(readAppConfigs(context), allApps)
    }

    fun persist(updated: LinkedHashMap<String, IdleAppConfigUi>) {
        configuredApps = updated
        writeAppConfigs(context, updated)
    }

    fun addOrUpdate(pkg: String, policy: IdlePolicy, customMinutes: Int, appItem: IdleAppItem) {
        val updated = linkedMapOf<String, IdleAppConfigUi>().apply {
            putAll(configuredApps)
            put(
                pkg, IdleAppConfigUi(
                    packageName = pkg,
                    label = appItem.label,
                    icon = appItem.icon,
                    isSystem = appItem.isSystem,
                    policy = policy,
                    customTimeoutMinutes = customMinutes
                )
            )
        }
        persist(updated)
    }

    fun remove(pkg: String) {
        persist(linkedMapOf<String, IdleAppConfigUi>().apply {
            putAll(configuredApps.filter { it.key != pkg })
        })
    }

    fun clearAll() = persist(linkedMapOf())

    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.IO) {
            pm.getInstalledPackages(PackageManager.MATCH_ANY_USER)
                .mapNotNull { pkg ->
                    val ai = pkg.applicationInfo ?: return@mapNotNull null
                    IdleAppItem(
                        packageName = pkg.packageName,
                        label = ai.loadLabel(pm).toString(),
                        icon = ai.loadIcon(pm),
                        isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                                      || (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase(Locale.getDefault()) }
        }
        loadConfigs()
    }

    if (showAddDialog) {
        AddAppDialog(
            allApps = allApps,
            configuredPackages = configuredApps.keys,
            onDismiss = { showAddDialog = false },
            onAppAdded = { app, policy, customMinutes ->
                addOrUpdate(app.packageName, policy, customMinutes, app)
                showAddDialog = false
            }
        )
    }

    showEditDialog?.let { editTarget ->
        EditAppDialog(
            config = editTarget,
            onDismiss = { showEditDialog = null },
            onSave = { policy, customMinutes ->
                addOrUpdate(
                    editTarget.packageName, policy, customMinutes,
                    IdleAppItem(
                        editTarget.packageName, editTarget.label,
                        editTarget.icon, editTarget.isSystem
                    )
                )
                showEditDialog = null
            }
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            icon = {
                Icon(
                    Icons.Default.Warning, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.idle_manager_clear_all)) },
            text = { Text(stringResource(R.string.idle_manager_clear_all_confirm)) },
            confirmButton = {
                Button(
                    onClick = { clearAll(); showClearAllConfirm = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.idle_manager_clear_all)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(containerColor = Color.Transparent) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceBright
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier         = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.BatteryAlert, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.idle_manager_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (globalEnabled)
                                    stringResource(R.string.idle_manager_app_count, configuredApps.size)
                                else
                                    stringResource(R.string.idle_manager_disabled),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = globalEnabled,
                        onCheckedChange = { newValue ->
                            scope.launch {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            globalEnabled = newValue
                            writeEnabled(context, newValue)
                        },
                        thumbContent = {
                            Crossfade(
                                targetState = globalEnabled,
                                animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                                label = "switch_icon"
                            ) { checked ->
                                if (checked)
                                    Icon(
                                        Icons.Rounded.Check, contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                else
                                    Icon(
                                        Icons.Rounded.Close, contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = globalEnabled,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                            expandVertically(
                                MaterialTheme.motionScheme.defaultSpatialSpec(),
                                expandFrom = Alignment.Top
                            ),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                            shrinkVertically(
                                MaterialTheme.motionScheme.fastSpatialSpec(),
                                shrinkTowards = Alignment.Top
                            )
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Default.Add, contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.idle_manager_add_apps),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        OutlinedButton(
                            onClick = { showClearAllConfirm = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                Icons.Default.Delete, contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.idle_manager_clear_all),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (configuredApps.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.idle_manager_configured_apps),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )
                        Column {
                            configuredApps.values.forEach { appCfg ->
                                AnimatedVisibility(
                                    visible = true,
                                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                                                expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                                                shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec())
                                ) {
                                    Column {
                                        AppConfigCard(
                                            config = appCfg,
                                            context = context,
                                            onRemove = { remove(appCfg.packageName) },
                                            onEdit = { showEditDialog = appCfg },
                                            onLongPress = { showEditDialog = appCfg }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                    } else {
                        EmptyState()
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.PowerSettingsNew, contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.idle_manager_no_apps_configured),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.idle_manager_no_apps_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppConfigCard(
    config: IdleAppConfigUi,
    context: Context,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
    onLongPress: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var killStats by remember { mutableStateOf(KillStats(0, 0L)) }

    val iconBitmap = remember(config.packageName) { config.icon.toBitmap(96, 96).asImageBitmap() }
    val isCritical = config.isSystem && CRITICAL_SYSTEM_PACKAGES.contains(config.packageName)
    val policyColor = policyColor(config.policy)

    LaunchedEffect(config.packageName) {
        killStats = withContext(Dispatchers.IO) { readKillStats(context, config.packageName) }
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            icon  = {
                Icon(
                    Icons.Default.Warning, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.idle_manager_remove_title)) },
            text = {
                Text(stringResource(R.string.idle_manager_remove_confirm, config.label))
            },
            confirmButton = {
                Button(
                    onClick = { showRemoveConfirm = false; onRemove() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
            .combinedClickable(onClick = { expanded = !expanded }, onLongClick = onLongPress),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    )
                    if (config.isSystem) {
                        Badge(
                            modifier = Modifier.align(Alignment.BottomEnd),
                            containerColor = if (isCritical) MaterialTheme.colorScheme.error
                                             else MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ) { Text("SYS", style = MaterialTheme.typography.labelSmall) }
                    }
                    if (killStats.count > 0) {
                        Badge(
                            modifier = Modifier.align(Alignment.TopEnd),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Text(
                                text = if (killStats.count > 99) "99+" else killStats.count.toString(),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = config.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = config.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    formatLastKilled(killStats.lastKillMs)?.let { timeStr ->
                        Text(
                            text = stringResource(R.string.idle_manager_last_killed, timeStr),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            maxLines = 1
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(policyColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = policyLabel(config.policy, config.customTimeoutMinutes),
                        style = MaterialTheme.typography.labelSmall,
                        color = policyColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))

                if (isCritical) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning, contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.idle_manager_critical_system_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        DetailRow(
                            label = stringResource(R.string.idle_manager_policy_label),
                            value = policyLabel(config.policy, config.customTimeoutMinutes),
                            color = policyColor
                        )
                        if (config.policy == IdlePolicy.CUSTOM) {
                            DetailRow(
                                label = stringResource(R.string.idle_manager_timeout_label),
                                value = "${config.customTimeoutMinutes} min",
                                color = policyColor
                            )
                        }
                        if (killStats.count > 0) {
                            DetailRow(
                                label = stringResource(R.string.idle_manager_kill_count_label),
                                value = killStats.count.toString(),
                                color = MaterialTheme.colorScheme.secondary
                            )
                            formatLastKilled(killStats.lastKillMs)?.let { timeStr ->
                                DetailRow(
                                    label = stringResource(R.string.idle_manager_last_killed_label),
                                    value = timeStr,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text  = stringResource(R.string.idle_manager_longpress_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showRemoveConfirm = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Delete, contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.remove))
                }
                FilledTonalButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Default.Edit, contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.edit))
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun policyLabel(policy: IdlePolicy, customMinutes: Int): String = when (policy) {
    IdlePolicy.BALANCED -> stringResource(R.string.idle_manager_policy_balanced)
    IdlePolicy.AGGRESSIVE -> stringResource(R.string.idle_manager_policy_aggressive)
    IdlePolicy.CUSTOM -> "$customMinutes min"
}

@Composable
private fun policyColor(policy: IdlePolicy): Color = when (policy) {
    IdlePolicy.BALANCED -> MaterialTheme.colorScheme.tertiary
    IdlePolicy.AGGRESSIVE -> MaterialTheme.colorScheme.error
    IdlePolicy.CUSTOM -> MaterialTheme.colorScheme.primary
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAppDialog(
    allApps: List<IdleAppItem>,
    configuredPackages: Set<String>,
    onDismiss: () -> Unit,
    onAppAdded: (IdleAppItem, IdlePolicy, Int) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var selectedApps by remember { mutableStateOf(setOf<IdleAppItem>()) }
    var showPolicy by remember { mutableStateOf(false) }

    val filtered = allApps.filter { app ->
        if (configuredPackages.contains(app.packageName)) return@filter false
        if (!showSystemApps && app.isSystem) return@filter false
        if (searchQuery.isBlank()) return@filter true
        app.label.contains(searchQuery, true) || app.packageName.contains(searchQuery, true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showPolicy) {
                    TextButton(onClick = { showPolicy = false }) {
                        Text(stringResource(R.string.idle_manager_back_to_apps))
                    }
                } else {
                    Text(stringResource(R.string.idle_manager_add_apps))
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text    = {
                                    Text(
                                        if (showSystemApps)
                                            stringResource(R.string.hide_system_apps)
                                        else
                                            stringResource(R.string.show_system_apps)
                                    )
                                },
                                onClick = { showSystemApps = !showSystemApps; showMenu = false }
                            )
                        }
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!showPolicy) {
                    androidx.compose.material3.OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.search_apps)) }
                    )
                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isBlank())
                                    stringResource(R.string.idle_manager_no_apps_available)
                                else
                                    stringResource(R.string.idle_manager_no_apps_found, searchQuery),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            filtered.forEach { app ->
                                val icon = remember(app.packageName) {
                                    app.icon.toBitmap(96, 96).asImageBitmap()
                                }
                                val isSelected = selectedApps.any { it.packageName == app.packageName }
                                val isCritical = app.isSystem &&
                                    CRITICAL_SYSTEM_PACKAGES.contains(app.packageName)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedApps = if (selectedApps.any { it.packageName == app.packageName })
                                                selectedApps.filter { it.packageName != app.packageName }.toSet()
                                            else
                                                selectedApps + app
                                        }
                                        .background(
                                            if (isSelected)
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                            else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box {
                                        Image(
                                            bitmap = icon,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        if (app.isSystem) {
                                            Badge(
                                                modifier = Modifier.align(Alignment.BottomEnd),
                                                containerColor = if (isCritical)
                                                    MaterialTheme.colorScheme.error
                                                else
                                                    MaterialTheme.colorScheme.tertiary
                                            ) {}
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                app.label,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isCritical) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            app.packageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check, contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (selectedApps.isNotEmpty()) {
                        Button(
                            onClick  = { showPolicy = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.idle_manager_select_policy_count, selectedApps.size))
                        }
                    }
                } else {
                    PolicySelector(onSelected = { policy, customMins ->
                        selectedApps.forEach { app -> onAppAdded(app, policy, customMins) }
                    })
                }
            }
        },
        confirmButton = {
            if (!showPolicy) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
        dismissButton = null
    )
}

@Composable
private fun EditAppDialog(
    config: IdleAppConfigUi,
    onDismiss: () -> Unit,
    onSave: (IdlePolicy, Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.idle_manager_edit_policy, config.label)) },
        text = { PolicySelector(onSelected = { policy, mins -> onSave(policy, mins) }) },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun PolicySelector(onSelected: (IdlePolicy, Int) -> Unit) {
    var selected by remember { mutableStateOf<IdlePolicy?>(null) }
    var customMinutes by remember { mutableFloatStateOf(30f) }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.idle_manager_select_policy),
            style = MaterialTheme.typography.labelMedium
        )

        PolicyOption(
            policy = IdlePolicy.BALANCED,
            selected = selected == IdlePolicy.BALANCED,
            title = stringResource(R.string.idle_manager_policy_balanced),
            subtitle = stringResource(R.string.idle_manager_policy_balanced_desc),
            color = MaterialTheme.colorScheme.tertiary,
            onClick = {
                selected = IdlePolicy.BALANCED
                onSelected(IdlePolicy.BALANCED, IdlePolicy.BALANCED.defaultMinutes)
            }
        )

        PolicyOption(
            policy = IdlePolicy.AGGRESSIVE,
            selected = selected == IdlePolicy.AGGRESSIVE,
            title = stringResource(R.string.idle_manager_policy_aggressive),
            subtitle = stringResource(R.string.idle_manager_policy_aggressive_desc),
            color = MaterialTheme.colorScheme.error,
            onClick = {
                selected = IdlePolicy.AGGRESSIVE
                onSelected(IdlePolicy.AGGRESSIVE, IdlePolicy.AGGRESSIVE.defaultMinutes)
            }
        )

        PolicyOption(
            policy = IdlePolicy.CUSTOM,
            selected = selected == IdlePolicy.CUSTOM,
            title = stringResource(R.string.idle_manager_policy_custom),
            subtitle = stringResource(R.string.idle_manager_policy_custom_desc),
            color = MaterialTheme.colorScheme.primary,
            onClick = { selected = IdlePolicy.CUSTOM }
        )

        AnimatedVisibility(visible = selected == IdlePolicy.CUSTOM) {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = stringResource(
                        R.string.idle_manager_timeout_minutes,
                        customMinutes.toInt()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = customMinutes,
                    onValueChange = { customMinutes = it },
                    valueRange = 5f..240f,
                    steps = 46,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { onSelected(IdlePolicy.CUSTOM, customMinutes.toInt()) },
                        enabled = customMinutes > 0
                    ) { Text(stringResource(R.string.idle_manager_apply_custom)) }
                }
            }
        }
    }
}

@Composable
private fun PolicyOption(
    policy: IdlePolicy,
    selected: Boolean,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = if (selected) color.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) color else Color.Unspecified
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.Check, contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
