/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.phone.vvm.omtp.sync;

import android.content.Context;
import android.provider.VoicemailContract;
import android.telecom.PhoneAccountHandle;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.phone.PhoneUtils;
import com.android.phone.settings.VisualVoicemailSettingsUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A singleton class designed to remember the active OMTP visual voicemail sources.
 */
public class OmtpVvmSourceManager {
    public static final String TAG = "OmtpVvmSourceManager";

    private static OmtpVvmSourceManager sInstance = new OmtpVvmSourceManager();

    private Context mContext;
    private SubscriptionManager mSubscriptionManager;
    private TelephonyManager mTelephonyManager;
    // Each phone account is associated with a phone state listener for updates to whether the
    // device is able to sync.
    private Map<PhoneAccountHandle, PhoneStateListener> mPhoneStateListenerMap;

    /**
     * Private constructor. Instance should only be acquired through getInstance().
     */
    private OmtpVvmSourceManager() {}

    public static OmtpVvmSourceManager getInstance(Context context) {
        sInstance.setup(context);
        return sInstance;
    }

    /**
     * Set the context and system services so they do not need to be retrieved every time.
     * @param context The context to get the subscription and telephony manager for.
     */
    private void setup(Context context) {
        if (mContext == null) {
            mContext = context;
            mSubscriptionManager = SubscriptionManager.from(context);
            mTelephonyManager = (TelephonyManager)
                    mContext.getSystemService(Context.TELEPHONY_SERVICE);
            mPhoneStateListenerMap = new HashMap<PhoneAccountHandle, PhoneStateListener>();
        }
    }

    /**
     * When a voicemail source is removed, we don't always know which one was removed. Check the
     * list of registered phone accounts against the active subscriptions list and remove the
     * inactive accounts.
     */
    public void removeInactiveSources() {
        Set<PhoneAccountHandle> sources = getOmtpVvmSources();
        for (PhoneAccountHandle source : sources) {
            if (!PhoneUtils.isPhoneAccountActive(mSubscriptionManager, source)) {
                VoicemailContract.Status.setStatus(mContext, source,
                        VoicemailContract.Status.CONFIGURATION_STATE_NOT_CONFIGURED,
                        VoicemailContract.Status.DATA_CHANNEL_STATE_NO_CONNECTION,
                        VoicemailContract.Status.NOTIFICATION_CHANNEL_STATE_NO_CONNECTION);
                removePhoneStateListener(source);
                VisualVoicemailSettingsUtil.setVisualVoicemailEnabled(mContext, source, false);
            }
        }
    }

    public void addPhoneStateListener(PhoneAccountHandle phoneAccount) {
        if (!mPhoneStateListenerMap.containsKey(phoneAccount)) {
            VvmPhoneStateListener phoneStateListener = new VvmPhoneStateListener(mContext,
                    PhoneUtils.makePstnPhoneAccountHandle(phoneAccount.getId()));
            mPhoneStateListenerMap.put(phoneAccount, phoneStateListener);
            mTelephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);
        }
    }

    public void removePhoneStateListener(PhoneAccountHandle phoneAccount) {
        PhoneStateListener phoneStateListener =
                mPhoneStateListenerMap.remove(phoneAccount);
        mTelephonyManager.listen(phoneStateListener, 0);
    }

    public Set<PhoneAccountHandle> getOmtpVvmSources() {
        return mPhoneStateListenerMap.keySet();
    }

    /**
     * Check if a certain account is registered.
     *
     * @param phoneAccount The account to look for.
     * @return {@code true} if the account is in the list of registered OMTP voicemail sync
     * accounts. {@code false} otherwise.
     */
    public boolean isVvmSourceRegistered(PhoneAccountHandle phoneAccount) {
        if (phoneAccount == null) {
            return false;
        }

        Set<PhoneAccountHandle> sources = getOmtpVvmSources();
        for (PhoneAccountHandle source : sources) {
            if (phoneAccount.equals(source)) {
                return true;
            }
        }
        return false;
    }
}
