/*
 * Copyright (C) 2014 Dominik Schürmann <dominik@dominikschuermann.de>
 * Copyright (C) 2011 Senecaso
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

package org.sufficientlysecure.keychain.ui;

import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.beardedhen.androidbootstrap.BootstrapButton;
import org.spongycastle.openpgp.PGPPublicKeyRing;
import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.helper.Preferences;
import org.sufficientlysecure.keychain.pgp.PgpKeyHelper;
import org.sufficientlysecure.keychain.provider.KeychainContract.KeyRings;
import org.sufficientlysecure.keychain.provider.KeychainContract.UserIds;
import org.sufficientlysecure.keychain.provider.ProviderHelper;
import org.sufficientlysecure.keychain.service.KeychainIntentService;
import org.sufficientlysecure.keychain.service.KeychainIntentServiceHandler;
import org.sufficientlysecure.keychain.service.PassphraseCacheService;
import org.sufficientlysecure.keychain.ui.adapter.ViewKeyUserIdsAdapter;
import org.sufficientlysecure.keychain.ui.dialog.PassphraseDialogFragment;
import org.sufficientlysecure.keychain.util.Log;

import java.util.ArrayList;

/**
 * Signs the specified public key with the specified secret master key
 */
public class CertifyKeyActivity extends ActionBarActivity implements
        SelectSecretKeyLayoutFragment.SelectSecretKeyCallback, LoaderManager.LoaderCallbacks<Cursor> {
    private BootstrapButton mSignButton;
    private CheckBox mUploadKeyCheckbox;
    private Spinner mSelectKeyserverSpinner;

    private SelectSecretKeyLayoutFragment mSelectKeyFragment;

    private Uri mDataUri;
    private long mPubKeyId = 0;
    private long mMasterKeyId = 0;

    private ListView mUserIds;
    private ViewKeyUserIdsAdapter mUserIdsAdapter;

    private static final int LOADER_ID_KEYRING = 0;
    private static final int LOADER_ID_USER_IDS = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.certify_key_activity);

        final ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setHomeButtonEnabled(false);

        mSelectKeyFragment = (SelectSecretKeyLayoutFragment) getSupportFragmentManager()
                .findFragmentById(R.id.sign_key_select_key_fragment);
        mSelectKeyFragment.setCallback(this);
        mSelectKeyFragment.setFilterCertify(true);

        mSelectKeyserverSpinner = (Spinner) findViewById(R.id.sign_key_keyserver);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, Preferences.getPreferences(this)
                .getKeyServers());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSelectKeyserverSpinner.setAdapter(adapter);

        mUploadKeyCheckbox = (CheckBox) findViewById(R.id.sign_key_upload_checkbox);
        if (!mUploadKeyCheckbox.isChecked()) {
            mSelectKeyserverSpinner.setEnabled(false);
        } else {
            mSelectKeyserverSpinner.setEnabled(true);
        }

        mUploadKeyCheckbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!isChecked) {
                    mSelectKeyserverSpinner.setEnabled(false);
                } else {
                    mSelectKeyserverSpinner.setEnabled(true);
                }
            }
        });

        mSignButton = (BootstrapButton) findViewById(R.id.sign_key_sign_button);
        mSignButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mPubKeyId != 0) {
                    if (mMasterKeyId == 0) {
                        mSelectKeyFragment.setError(getString(R.string.select_key_to_sign));
                    } else {
                        initiateSigning();
                    }
                }
            }
        });

        mDataUri = getIntent().getData();
        if (mDataUri == null) {
            Log.e(Constants.TAG, "Intent data missing. Should be Uri of key!");
            finish();
            return;
        }
        Log.e(Constants.TAG, "uri: " + mDataUri);

        mUserIds = (ListView) findViewById(R.id.user_ids);

        mUserIdsAdapter = new ViewKeyUserIdsAdapter(this, null, 0, true);
        mUserIds.setAdapter(mUserIdsAdapter);
        mUserIds.setOnItemClickListener(mUserIdsAdapter);

        getSupportLoaderManager().initLoader(LOADER_ID_KEYRING, null, this);
        getSupportLoaderManager().initLoader(LOADER_ID_USER_IDS, null, this);

    }

    static final String USER_IDS_SELECTION = UserIds.IS_REVOKED + " = 0";

    static final String[] KEYRING_PROJECTION =
            new String[] {
                    KeyRings._ID,
                    KeyRings.MASTER_KEY_ID,
                    KeyRings.FINGERPRINT,
                    KeyRings.USER_ID,
            };
    static final int INDEX_MASTER_KEY_ID = 1;
    static final int INDEX_FINGERPRINT = 2;
    static final int INDEX_USER_ID = 3;

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        switch(id) {
            case LOADER_ID_KEYRING: {
                Uri uri = KeyRings.buildUnifiedKeyRingUri(mDataUri);
                return new CursorLoader(this, uri, KEYRING_PROJECTION, null, null, null);
            }
            case LOADER_ID_USER_IDS: {
                Uri uri = UserIds.buildUserIdsUri(mDataUri);
                return new CursorLoader(this, uri,
                        ViewKeyUserIdsAdapter.USER_IDS_PROJECTION, USER_IDS_SELECTION, null, null);
            }
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        switch(loader.getId()) {
            case LOADER_ID_KEYRING:
                // the first key here is our master key
                if (data.moveToFirst()) {
                    // TODO: put findViewById in onCreate!
                    mPubKeyId = data.getLong(INDEX_MASTER_KEY_ID);
                    String keyIdStr = PgpKeyHelper.convertKeyIdToHexShort(mPubKeyId);
                    ((TextView) findViewById(R.id.key_id)).setText(keyIdStr);

                    String mainUserId = data.getString(INDEX_USER_ID);
                    ((TextView) findViewById(R.id.main_user_id)).setText(mainUserId);

                    byte[] fingerprintBlob = data.getBlob(INDEX_FINGERPRINT);
                    String fingerprint = PgpKeyHelper.convertFingerprintToHex(fingerprintBlob);
                    ((TextView) findViewById(R.id.fingerprint))
                        .setText(PgpKeyHelper.colorizeFingerprint(fingerprint));
                }
                break;
            case LOADER_ID_USER_IDS:
                mUserIdsAdapter.swapCursor(data);
                break;
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        switch(loader.getId()) {
            case LOADER_ID_USER_IDS:
                mUserIdsAdapter.swapCursor(null);
                break;
        }
    }

    /**
     * handles the UI bits of the signing process on the UI thread
     */
    private void initiateSigning() {
        PGPPublicKeyRing pubring = ProviderHelper.getPGPPublicKeyRing(this, mPubKeyId);
        if (pubring != null) {
            // if we have already signed this key, dont bother doing it again
            boolean alreadySigned = false;

            /* todo: reconsider this at a later point when certs are in the db
            @SuppressWarnings("unchecked")
            Iterator<PGPSignature> itr = pubring.getPublicKey(mPubKeyId).getSignatures();
            while (itr.hasNext()) {
                PGPSignature sig = itr.next();
                if (sig.getKeyID() == mMasterKeyId) {
                    alreadySigned = true;
                    break;
                }
            }
            */

            if (!alreadySigned) {
                /*
                 * get the user's passphrase for this key (if required)
                 */
                String passphrase = PassphraseCacheService.getCachedPassphrase(this, mMasterKeyId);
                if (passphrase == null) {
                    PassphraseDialogFragment.show(this, mMasterKeyId,
                        new Handler() {
                            @Override
                            public void handleMessage(Message message) {
                                if (message.what == PassphraseDialogFragment.MESSAGE_OKAY) {
                                    startSigning();
                                }
                            }
                        });
                    // bail out; need to wait until the user has entered the passphrase before trying again
                    return;
                } else {
                    startSigning();
                }
            } else {
                Toast.makeText(this, R.string.key_has_already_been_signed, Toast.LENGTH_SHORT)
                        .show();

                setResult(RESULT_CANCELED);
                finish();
            }
        }
    }

    /**
     * kicks off the actual signing process on a background thread
     */
    private void startSigning() {

        // Bail out if there is not at least one user id selected
        ArrayList<String> userIds = mUserIdsAdapter.getSelectedUserIds();
        if (userIds.isEmpty()) {
            Toast.makeText(CertifyKeyActivity.this, "No User IDs to sign selected!",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Send all information needed to service to sign key in other thread
        Intent intent = new Intent(this, KeychainIntentService.class);

        intent.setAction(KeychainIntentService.ACTION_CERTIFY_KEYRING);

        // fill values for this action
        Bundle data = new Bundle();

        data.putLong(KeychainIntentService.CERTIFY_KEY_MASTER_KEY_ID, mMasterKeyId);
        data.putLong(KeychainIntentService.CERTIFY_KEY_PUB_KEY_ID, mPubKeyId);
        data.putStringArrayList(KeychainIntentService.CERTIFY_KEY_UIDS, userIds);

        intent.putExtra(KeychainIntentService.EXTRA_DATA, data);

        // Message is received after signing is done in KeychainIntentService
        KeychainIntentServiceHandler saveHandler = new KeychainIntentServiceHandler(this,
                getString(R.string.progress_signing), ProgressDialog.STYLE_SPINNER) {
            public void handleMessage(Message message) {
                // handle messages by standard KeychainIntentServiceHandler first
                super.handleMessage(message);

                if (message.arg1 == KeychainIntentServiceHandler.MESSAGE_OKAY) {

                    Toast.makeText(CertifyKeyActivity.this, R.string.key_sign_success,
                            Toast.LENGTH_SHORT).show();

                    // check if we need to send the key to the server or not
                    if (mUploadKeyCheckbox.isChecked()) {
                        // upload the newly signed key to the keyserver
                        uploadKey();
                    } else {
                        setResult(RESULT_OK);
                        finish();
                    }
                }
            }
        };

        // Create a new Messenger for the communication back
        Messenger messenger = new Messenger(saveHandler);
        intent.putExtra(KeychainIntentService.EXTRA_MESSENGER, messenger);

        // show progress dialog
        saveHandler.showProgressDialog(this);

        // start service with intent
        startService(intent);
    }

    private void uploadKey() {
        // Send all information needed to service to upload key in other thread
        Intent intent = new Intent(this, KeychainIntentService.class);

        intent.setAction(KeychainIntentService.ACTION_UPLOAD_KEYRING);

        // set data uri as path to keyring
        intent.setData(mDataUri);

        // fill values for this action
        Bundle data = new Bundle();

        Spinner keyServer = (Spinner) findViewById(R.id.sign_key_keyserver);
        String server = (String) keyServer.getSelectedItem();
        data.putString(KeychainIntentService.UPLOAD_KEY_SERVER, server);

        intent.putExtra(KeychainIntentService.EXTRA_DATA, data);

        // Message is received after uploading is done in KeychainIntentService
        KeychainIntentServiceHandler saveHandler = new KeychainIntentServiceHandler(this,
                getString(R.string.progress_exporting), ProgressDialog.STYLE_HORIZONTAL) {
            public void handleMessage(Message message) {
                // handle messages by standard KeychainIntentServiceHandler first
                super.handleMessage(message);

                if (message.arg1 == KeychainIntentServiceHandler.MESSAGE_OKAY) {
                    Toast.makeText(CertifyKeyActivity.this, R.string.key_send_success,
                            Toast.LENGTH_SHORT).show();

                    setResult(RESULT_OK);
                    finish();
                }
            }
        };

        // Create a new Messenger for the communication back
        Messenger messenger = new Messenger(saveHandler);
        intent.putExtra(KeychainIntentService.EXTRA_MESSENGER, messenger);

        // show progress dialog
        saveHandler.showProgressDialog(this);

        // start service with intent
        startService(intent);
    }

    /**
     * callback from select key fragment
     */
    @Override
    public void onKeySelected(long secretKeyId) {
        mMasterKeyId = secretKeyId;
    }
}
