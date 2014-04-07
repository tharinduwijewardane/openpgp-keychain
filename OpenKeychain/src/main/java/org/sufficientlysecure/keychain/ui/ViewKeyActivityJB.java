/*
 * Copyright (C) 2013-2014 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.keychain.ui;

import android.annotation.TargetApi;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateNdefMessageCallback;
import android.nfc.NfcAdapter.OnNdefPushCompleteCallback;
import android.nfc.NfcEvent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

import com.devspark.appmsg.AppMsg;

import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.provider.ProviderHelper;
import org.sufficientlysecure.keychain.util.Log;

import java.io.IOException;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class ViewKeyActivityJB extends ViewKeyActivity implements CreateNdefMessageCallback,
        OnNdefPushCompleteCallback {

    private NfcAdapter mNfcAdapter;
    private byte[] mSharedKeyringBytes;
    private static final int NFC_SENT = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initNfc();
    }

    /**
     * NFC: Initialize NFC sharing if OS and device supports it
     */
    private void initNfc() {
        // check if NFC Beam is supported (>= Android 4.1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // Check for available NFC Adapter
            mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
            if (mNfcAdapter != null) {
                // init nfc
                // Register callback to set NDEF message
                mNfcAdapter.setNdefPushMessageCallback(this, this);
                // Register callback to listen for message-sent success
                mNfcAdapter.setOnNdefPushCompleteCallback(this, this);
            }
        }
    }

    /**
     * NFC: Implementation for the CreateNdefMessageCallback interface
     */
    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        /**
         * When a device receives a push with an AAR in it, the application specified in the AAR is
         * guaranteed to run. The AAR overrides the tag dispatch system. You can add it back in to
         * guarantee that this activity starts when receiving a beamed message. For now, this code
         * uses the tag dispatch system.
         */
        try {
            // get public keyring as byte array
            mSharedKeyringBytes = ProviderHelper.getPGPKeyRing(this, mDataUri).getEncoded();

            NdefMessage msg = new NdefMessage(NdefRecord.createMime(Constants.NFC_MIME,
                    mSharedKeyringBytes), NdefRecord.createApplicationRecord(Constants.PACKAGE_NAME));
            return msg;
        } catch(IOException e) {
            Log.e(Constants.TAG, "Error parsing keyring", e);
            return null;
        }
    }

    /**
     * NFC: Implementation for the OnNdefPushCompleteCallback interface
     */
    @Override
    public void onNdefPushComplete(NfcEvent arg0) {
        // A handler is needed to send messages to the activity when this
        // callback occurs, because it happens from a binder thread
        mNfcHandler.obtainMessage(NFC_SENT).sendToTarget();
    }

    /**
     * NFC: This handler receives a message from onNdefPushComplete
     */
    private final Handler mNfcHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case NFC_SENT:
                    AppMsg.makeText(ViewKeyActivityJB.this, R.string.nfc_successfull,
                            AppMsg.STYLE_INFO).show();
                    break;
            }
        }
    };

}
