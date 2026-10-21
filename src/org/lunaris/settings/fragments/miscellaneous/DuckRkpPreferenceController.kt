/* SPDX-License-Identifier: Apache-2.0 */
package org.lunaris.settings.fragments.miscellaneous

import android.app.ActivityManager
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DuckRkpPreferenceController(
    private val fragment: SettingsPreferenceFragment,
    private val onKeyboxChanged: () -> Unit,
) {
    private var state: Bundle? = null
    private var changing = false
    private val owner = UserHandle.myUserId() == UserHandle.USER_SYSTEM
    private val toggle = fragment.findPreference<SwitchPreferenceCompat>("ts_rkp_enabled")
    private val provision = fragment.findPreference<Preference>("ts_rkp_provision")
    private val status = fragment.findPreference<Preference>("ts_rkp_status")

    val hasKeybox: Boolean
        get() = state?.getBoolean("has_keybox") == true

    val hasGeneratedKeybox: Boolean
        get() = hasKeybox && state?.getBoolean("imported") == false

    fun start() {
        toggle?.isEnabled = false
        provision?.isEnabled = false
        toggle?.setOnPreferenceChangeListener { _, value ->
            change { ActivityManager.getService().setDuckRkpEnabled(value as Boolean) }
            false
        }
        provision?.setOnPreferenceClickListener {
            change { ActivityManager.getService().provisionDuckRkp() }
            true
        }
        fragment.lifecycleScope.launch {
            fragment.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    refresh()
                    delay(1500)
                }
            }
        }
    }

    private fun change(action: () -> Unit) {
        if (changing) return
        changing = true
        render()
        fragment.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(action) }
            changing = false
            if (!fragment.isAdded) return@launch
            if (result.isFailure) {
                Toast.makeText(fragment.requireContext(), R.string.ts_rkp_action_failed,
                    Toast.LENGTH_LONG).show()
            }
            refresh()
        }
    }

    private suspend fun refresh() {
        if (!owner) {
            render()
            return
        }
        val next = withContext(Dispatchers.IO) {
            runCatching { ActivityManager.getService().duckRkpState }.getOrNull()
        }
        if (!fragment.isAdded) return
        val previous = state
        state = next
        render()
        if (next != null && previous != null && next.getBoolean("active")
                && !next.getBoolean("imported")
                && (next.getLong("revision") != previous.getLong("revision")
                    || !previous.getBoolean("active"))) {
            // The new keybox is saved before its revision changes. Stop cached attestation users.
            stopPlayPackages()
            if (!fragment.isAdded) return
        }
        if (next != null && (previous == null
                    || next.getLong("revision") != previous.getLong("revision")
                    || next.getBoolean("active") != previous.getBoolean("active")
                    || next.getBoolean("imported") != previous.getBoolean("imported"))) {
            onKeyboxChanged()
        }
    }

    private suspend fun stopPlayPackages() = withContext(Dispatchers.IO) {
        val activityManager = ActivityManager.getService()
        for (packageName in listOf("com.android.vending", "com.google.android.gms")) {
            runCatching {
                activityManager.forceStopPackage(packageName, UserHandle.USER_SYSTEM)
            }.onFailure {
                Log.w("DuckRkp", "Could not force-stop $packageName after keybox change", it)
            }
        }
    }

    private fun render() {
        val current = state
        val available = current?.getBoolean("available") == true
        val imported = current?.getBoolean("imported") == true
        val enabled = current?.getBoolean("enabled") == true
        val busy = current?.getBoolean("busy") == true
        toggle?.isChecked = enabled
        toggle?.isEnabled = owner && (available || enabled) && !imported && !changing
        provision?.isEnabled = owner && available && enabled && !imported && !busy && !changing
                && (current?.getLong("retry_after_ms") ?: 0L) == 0L
        status?.summary = when {
            !owner -> fragment.getString(R.string.ts_rkp_owner_only)
            current == null -> fragment.getString(R.string.ts_rkp_unavailable)
            imported -> fragment.getString(R.string.ts_rkp_imported)
            !available -> fragment.getString(R.string.ts_rkp_unavailable)
            !enabled -> fragment.getString(R.string.ts_rkp_disabled)
            !current.getString("error").isNullOrEmpty() -> error(current)
            busy -> fragment.getString(when (current.getString("stage")) {
                "fetching" -> R.string.ts_rkp_fetching
                "signing" -> R.string.ts_rkp_signing
                "validating", "saving" -> R.string.ts_rkp_validating
                else -> R.string.ts_rkp_hardware
            })
            current.getBoolean("active") -> fragment.getString(R.string.ts_rkp_ready,
                DateFormat.getDateTimeInstance().format(Date(current.getLong("expires_at"))))
            current.getLong("expires_at") in 1..System.currentTimeMillis() ->
                fragment.getString(R.string.ts_rkp_expired)
            else -> fragment.getString(R.string.ts_rkp_idle)
        }
    }

    private fun error(current: Bundle): String = when (current.getString("error")) {
        "hal_rejected" -> fragment.getString(R.string.ts_rkp_hal_rejected, current.getInt("detail"))
        "http_error" -> fragment.getString(R.string.ts_rkp_http_error, current.getInt("detail"))
        "hal_unsupported", "hal_unavailable", "missing_eek" ->
            fragment.getString(R.string.ts_rkp_unsupported)
        "network_error" -> fragment.getString(R.string.ts_rkp_network_error)
        "submission_unknown" -> fragment.getString(R.string.ts_rkp_submission_unknown)
        "certificate_invalid", "stored_key_invalid" -> fragment.getString(R.string.ts_rkp_invalid_chain)
        "timeout" -> fragment.getString(R.string.ts_rkp_timeout)
        "canceled" -> fragment.getString(R.string.ts_rkp_canceled)
        "save_failed" -> fragment.getString(R.string.ts_rkp_save_failed)
        else -> fragment.getString(R.string.ts_rkp_action_failed)
    }
}
