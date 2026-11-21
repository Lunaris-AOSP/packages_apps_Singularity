/*
 * Copyright (C) 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.settings.fragments.notifications;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.server.notification.Flags;
import com.android.settings.core.BasePreferenceController;

import java.util.List;

public class LockScreenNotificationStyleController extends BasePreferenceController
        implements LifecycleEventObserver, Preference.OnPreferenceChangeListener {

    private static final String[] OBSERVED_SETTINGS = {
            Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS,
            Settings.Secure.LOCK_SCREEN_NOTIFICATION_MINIMALISM,
            Settings.Secure.LOCK_SCREEN_MOTO_COMPACT_NOTIFICATIONS,
            Settings.Secure.LOCK_SCREEN_STACKED_NOTIFICATIONS,
            Settings.Secure.LOCK_SCREEN_NOTIFICATIONS_BOTTOM_ANCHORED,
            Settings.Secure.LOCK_SCREEN_NOTIFICATIONS_ABOVE_CLOCK
    };

    private final ContentResolver mContentResolver;
    private Preference mPreference;
    private SwitchPreferenceCompat mMoto;
    private SwitchPreferenceCompat mStacked;
    private SwitchPreferenceCompat mBottomAligned;
    private SwitchPreferenceCompat mAboveClock;
    private Preference mStyleHint;
    private Preference mPosition;

    private final ContentObserver mObserver = new ContentObserver(
            new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            updateState(mPreference);
        }
    };

    public LockScreenNotificationStyleController(Context context, String preferenceKey) {
        super(context, preferenceKey);
        mContentResolver = context.getContentResolver();
    }

    @Override
    public int getAvailabilityStatus() {
        return getSecureInt(Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS, 0) == 1
                ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
        mMoto = screen.findPreference(Settings.Secure.LOCK_SCREEN_MOTO_COMPACT_NOTIFICATIONS);
        mStacked = screen.findPreference(Settings.Secure.LOCK_SCREEN_STACKED_NOTIFICATIONS);
        mBottomAligned = screen.findPreference(
                Settings.Secure.LOCK_SCREEN_NOTIFICATIONS_BOTTOM_ANCHORED);
        mAboveClock = screen.findPreference(Settings.Secure.LOCK_SCREEN_NOTIFICATIONS_ABOVE_CLOCK);
        mStyleHint = screen.findPreference("lock_screen_notification_style_hint");
        mPosition = screen.findPreference(Settings.System.LOCK_SCREEN_NOTIFICATIONS_ANCHOR_POSITION);
        for (SwitchPreferenceCompat preference : new SwitchPreferenceCompat[] {
                mMoto, mStacked, mBottomAligned, mAboveClock }) {
            preference.setOnPreferenceChangeListener(this);
        }
        updateState(mPreference);
    }

    @Override
    public void updateState(Preference preference) {
        if (mPreference == null) return;
        final boolean showNotifications = isAvailable();
        final boolean compact = isCompact();
        final boolean bottomAligned = getSecureInt(
                Settings.Secure.LOCK_SCREEN_NOTIFICATIONS_BOTTOM_ANCHORED, 0) == 1;

        mPreference.setVisible(showNotifications);
        mStyleHint.setVisible(showNotifications && Flags.notificationMinimalism());
        updateSwitch(mMoto, showNotifications && compact);
        updateSwitch(mStacked, showNotifications && !compact);
        updateSwitch(mBottomAligned, showNotifications);
        updateSwitch(mAboveClock, showNotifications);
        mPosition.setVisible(showNotifications && bottomAligned);
        mPosition.setEnabled(showNotifications && bottomAligned);
    }

    private void updateSwitch(SwitchPreferenceCompat preference, boolean available) {
        preference.setVisible(available);
        preference.setEnabled(available);
        preference.setChecked(getSecureInt(preference.getKey(), 0) == 1);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (!isAvailable() || !(newValue instanceof Boolean)) return false;
        if (preference == mMoto) {
            if (!isCompact()) return false;
        } else if (preference == mStacked) {
            if (isCompact()) return false;
        } else if (preference != mBottomAligned && preference != mAboveClock) {
            return false;
        }
        final boolean saved = Settings.Secure.putIntForUser(mContentResolver,
                preference.getKey(), (Boolean) newValue ? 1 : 0, UserHandle.USER_CURRENT);
        if (saved) updateState(mPreference);
        return saved;
    }

    @Override
    public void onStateChanged(LifecycleOwner owner, Lifecycle.Event event) {
        if (event == Lifecycle.Event.ON_RESUME) {
            for (String key : OBSERVED_SETTINGS) {
                mContentResolver.registerContentObserver(Settings.Secure.getUriFor(key),
                        false, mObserver, UserHandle.USER_CURRENT);
            }
            updateState(mPreference);
        } else if (event == Lifecycle.Event.ON_PAUSE) {
            mContentResolver.unregisterContentObserver(mObserver);
        }
    }

    @Override
    public void updateNonIndexableKeys(List<String> keys) {
        super.updateNonIndexableKeys(keys);
        if (!isAvailable()) {
            keys.add(Settings.Secure.LOCK_SCREEN_MOTO_COMPACT_NOTIFICATIONS);
            keys.add(Settings.Secure.LOCK_SCREEN_STACKED_NOTIFICATIONS);
            keys.add(Settings.Secure.LOCK_SCREEN_NOTIFICATIONS_BOTTOM_ANCHORED);
            keys.add(Settings.Secure.LOCK_SCREEN_NOTIFICATIONS_ABOVE_CLOCK);
        } else {
            keys.add(isCompact() ? Settings.Secure.LOCK_SCREEN_STACKED_NOTIFICATIONS
                    : Settings.Secure.LOCK_SCREEN_MOTO_COMPACT_NOTIFICATIONS);
        }
        if (!isAvailable() || getSecureInt(
                Settings.Secure.LOCK_SCREEN_NOTIFICATIONS_BOTTOM_ANCHORED, 0) != 1) {
            keys.add(Settings.System.LOCK_SCREEN_NOTIFICATIONS_ANCHOR_POSITION);
        }
    }

    private boolean isCompact() {
        return Flags.notificationMinimalism()
                && getSecureInt(Settings.Secure.LOCK_SCREEN_NOTIFICATION_MINIMALISM, 1) == 1;
    }

    private int getSecureInt(String key, int defaultValue) {
        return Settings.Secure.getIntForUser(mContentResolver, key, defaultValue,
                UserHandle.USER_CURRENT);
    }
}
