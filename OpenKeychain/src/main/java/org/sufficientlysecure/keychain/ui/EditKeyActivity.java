/*
 * Copyright (C) 2012-2014 Dominik Schürmann <dominik@dominikschuermann.de>
 * Copyright (C) 2010 Thialfihar <thi@thialfihar.org>
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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;

import com.beardedhen.androidbootstrap.BootstrapButton;
import com.devspark.appmsg.AppMsg;

import org.spongycastle.openpgp.PGPSecretKey;
import org.spongycastle.openpgp.PGPSecretKeyRing;
import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.Id;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.helper.ActionBarHelper;
import org.sufficientlysecure.keychain.helper.ExportHelper;
import org.sufficientlysecure.keychain.pgp.PgpConversionHelper;
import org.sufficientlysecure.keychain.pgp.PgpKeyHelper;
import org.sufficientlysecure.keychain.pgp.exception.PgpGeneralException;
import org.sufficientlysecure.keychain.provider.KeychainContract;
import org.sufficientlysecure.keychain.provider.ProviderHelper;
import org.sufficientlysecure.keychain.service.KeychainIntentService;
import org.sufficientlysecure.keychain.service.KeychainIntentServiceHandler;
import org.sufficientlysecure.keychain.service.PassphraseCacheService;
import org.sufficientlysecure.keychain.service.SaveKeyringParcel;
import org.sufficientlysecure.keychain.ui.dialog.PassphraseDialogFragment;
import org.sufficientlysecure.keychain.ui.dialog.SetPassphraseDialogFragment;
import org.sufficientlysecure.keychain.ui.widget.Editor;
import org.sufficientlysecure.keychain.ui.widget.Editor.EditorListener;
import org.sufficientlysecure.keychain.ui.widget.KeyEditor;
import org.sufficientlysecure.keychain.ui.widget.SectionView;
import org.sufficientlysecure.keychain.ui.widget.UserIdEditor;
import org.sufficientlysecure.keychain.util.IterableIterator;
import org.sufficientlysecure.keychain.util.Log;

import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Vector;

public class EditKeyActivity extends ActionBarActivity implements EditorListener {

    // Actions for internal use only:
    public static final String ACTION_CREATE_KEY = Constants.INTENT_PREFIX + "CREATE_KEY";
    public static final String ACTION_EDIT_KEY = Constants.INTENT_PREFIX + "EDIT_KEY";

    // possible extra keys
    public static final String EXTRA_USER_IDS = "user_ids";
    public static final String EXTRA_NO_PASSPHRASE = "no_passphrase";
    public static final String EXTRA_GENERATE_DEFAULT_KEYS = "generate_default_keys";

    // EDIT
    private Uri mDataUri;

    private PGPSecretKeyRing mKeyRing = null;

    private SectionView mUserIdsView;
    private SectionView mKeysView;

    private String mCurrentPassphrase = null;
    private String mNewPassphrase = null;
    private String mSavedNewPassphrase = null;
    private boolean mIsPassphraseSet;
    private boolean mNeedsSaving;
    private boolean mIsBrandNewKeyring = false;

    private BootstrapButton mChangePassphrase;

    private CheckBox mNoPassphrase;

    Vector<String> mUserIds;
    Vector<PGPSecretKey> mKeys;
    Vector<Integer> mKeysUsages;
    boolean mMasterCanSign = true;

    ExportHelper mExportHelper;

    public boolean needsSaving() {
        mNeedsSaving = (mUserIdsView == null) ? false : mUserIdsView.needsSaving();
        mNeedsSaving |= (mKeysView == null) ? false : mKeysView.needsSaving();
        mNeedsSaving |= hasPassphraseChanged();
        mNeedsSaving |= mIsBrandNewKeyring;
        return mNeedsSaving;
    }


    public void somethingChanged() {
        ActivityCompat.invalidateOptionsMenu(this);
    }

    public void onDeleted(Editor e, boolean wasNewItem) {
        somethingChanged();
    }

    public void onEdited() {
        somethingChanged();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mExportHelper = new ExportHelper(this);

        // Inflate a "Done"/"Cancel" custom action bar view
        ActionBarHelper.setTwoButtonView(getSupportActionBar(),
                R.string.btn_save, R.drawable.ic_action_save,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Save
                        saveClicked();
                    }
                }, R.string.menu_key_edit_cancel, R.drawable.ic_action_cancel,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Cancel
                        cancelClicked();
                    }
                }
        );

        mUserIds = new Vector<String>();
        mKeys = new Vector<PGPSecretKey>();
        mKeysUsages = new Vector<Integer>();

        // Catch Intents opened from other apps
        Intent intent = getIntent();
        String action = intent.getAction();
        if (ACTION_CREATE_KEY.equals(action)) {
            handleActionCreateKey(intent);
        } else if (ACTION_EDIT_KEY.equals(action)) {
            handleActionEditKey(intent);
        }
    }

    /**
     * Handle intent action to create new key
     *
     * @param intent
     */
    private void handleActionCreateKey(Intent intent) {
        Bundle extras = intent.getExtras();

        mCurrentPassphrase = "";
        mIsBrandNewKeyring = true;

        if (extras != null) {
            // if userId is given, prefill the fields
            if (extras.containsKey(EXTRA_USER_IDS)) {
                Log.d(Constants.TAG, "UserIds are given!");
                mUserIds.add(extras.getString(EXTRA_USER_IDS));
            }

            // if no passphrase is given
            if (extras.containsKey(EXTRA_NO_PASSPHRASE)) {
                boolean noPassphrase = extras.getBoolean(EXTRA_NO_PASSPHRASE);
                if (noPassphrase) {
                    // check "no passphrase" checkbox and remove button
                    mNoPassphrase.setChecked(true);
                    mChangePassphrase.setVisibility(View.GONE);
                }
            }

            // generate key
            if (extras.containsKey(EXTRA_GENERATE_DEFAULT_KEYS)) {
                boolean generateDefaultKeys = extras.getBoolean(EXTRA_GENERATE_DEFAULT_KEYS);
                if (generateDefaultKeys) {

                    // Send all information needed to service generate keys in other thread
                    final Intent serviceIntent = new Intent(this, KeychainIntentService.class);
                    serviceIntent.setAction(KeychainIntentService.ACTION_GENERATE_DEFAULT_RSA_KEYS);

                    // fill values for this action
                    Bundle data = new Bundle();
                    data.putString(KeychainIntentService.GENERATE_KEY_SYMMETRIC_PASSPHRASE,
                            mCurrentPassphrase);

                    serviceIntent.putExtra(KeychainIntentService.EXTRA_DATA, data);

                    // Message is received after generating is done in KeychainIntentService
                    KeychainIntentServiceHandler saveHandler = new KeychainIntentServiceHandler(
                            this, getResources().getQuantityString(R.plurals.progress_generating, 1),
                            ProgressDialog.STYLE_HORIZONTAL, true,

                            new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    // Stop key generation on cancel
                                    stopService(serviceIntent);
                                    EditKeyActivity.this.setResult(Activity.RESULT_CANCELED);
                                    EditKeyActivity.this.finish();
                                }
                            }) {

                        @Override
                        public void handleMessage(Message message) {
                            // handle messages by standard KeychainIntentServiceHandler first
                            super.handleMessage(message);

                            if (message.arg1 == KeychainIntentServiceHandler.MESSAGE_OKAY) {
                                // get new key from data bundle returned from service
                                Bundle data = message.getData();

                                ArrayList<PGPSecretKey> newKeys =
                                        PgpConversionHelper.BytesToPGPSecretKeyList(data
                                                .getByteArray(KeychainIntentService.RESULT_NEW_KEY));

                                ArrayList<Integer> keyUsageFlags = data.getIntegerArrayList(
                                        KeychainIntentService.RESULT_KEY_USAGES);

                                if (newKeys.size() == keyUsageFlags.size()) {
                                    for (int i = 0; i < newKeys.size(); ++i) {
                                        mKeys.add(newKeys.get(i));
                                        mKeysUsages.add(keyUsageFlags.get(i));
                                    }
                                }

                                buildLayout(true);
                            }
                        }
                    };

                    // Create a new Messenger for the communication back
                    Messenger messenger = new Messenger(saveHandler);
                    serviceIntent.putExtra(KeychainIntentService.EXTRA_MESSENGER, messenger);

                    saveHandler.showProgressDialog(this);

                    // start service with intent
                    startService(serviceIntent);
                }
            }
        } else {
            buildLayout(false);
        }
    }

    /**
     * Handle intent action to edit existing key
     *
     * @param intent
     */
    private void handleActionEditKey(Intent intent) {
        mDataUri = intent.getData();
        if (mDataUri == null) {
            Log.e(Constants.TAG, "Intent data missing. Should be Uri of key!");
            finish();
        } else {
            Log.d(Constants.TAG, "uri: " + mDataUri);

            // get master key id using row id
            long masterKeyId = ProviderHelper.getMasterKeyId(this, mDataUri);
            finallyEdit(masterKeyId);
        }
    }

    @SuppressWarnings("unchecked")
    private void finallyEdit(final long masterKeyId) {
        if (masterKeyId != 0) {
            PGPSecretKey masterKey = null;
            mKeyRing = ProviderHelper.getPGPSecretKeyRing(this, masterKeyId);
            if (mKeyRing != null) {
                masterKey = mKeyRing.getSecretKey();
                mMasterCanSign = PgpKeyHelper.isCertificationKey(mKeyRing.getSecretKey());
                for (PGPSecretKey key : new IterableIterator<PGPSecretKey>(mKeyRing.getSecretKeys())) {
                    mKeys.add(key);
                    mKeysUsages.add(-1); // get usage when view is created
                }
            } else {
                Log.e(Constants.TAG, "Keyring not found with masterKeyId: " + masterKeyId);
                AppMsg.makeText(this, R.string.error_no_secret_key_found, AppMsg.STYLE_ALERT).show();
                // TODO
            }
            if (masterKey != null) {
                boolean isSet = false;
                for (String userId : new IterableIterator<String>(masterKey.getUserIDs())) {
                    Log.d(Constants.TAG, "Added userId " + userId);
                    if (!isSet) {
                        isSet = true;
                        String[] parts = PgpKeyHelper.splitUserId(userId);
                        if (parts[0] != null) {
                            setTitle(parts[0]);
                        }
                    }
                    mUserIds.add(userId);
                }
            }
        }

        mCurrentPassphrase = "";
        buildLayout(false);

        mIsPassphraseSet = PassphraseCacheService.hasPassphrase(this, masterKeyId);
        if (!mIsPassphraseSet) {
            // check "no passphrase" checkbox and remove button
            mNoPassphrase.setChecked(true);
            mChangePassphrase.setVisibility(View.GONE);
        }
    }

    /**
     * Shows the dialog to set a new passphrase
     */
    private void showSetPassphraseDialog() {
        // Message is received after passphrase is cached
        Handler returnHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                if (message.what == SetPassphraseDialogFragment.MESSAGE_OKAY) {
                    Bundle data = message.getData();

                    // set new returned passphrase!
                    mNewPassphrase = data
                            .getString(SetPassphraseDialogFragment.MESSAGE_NEW_PASSPHRASE);

                    updatePassphraseButtonText();
                    somethingChanged();
                }
            }
        };

        // Create a new Messenger for the communication back
        Messenger messenger = new Messenger(returnHandler);

        // set title based on isPassphraseSet()
        int title;
        if (isPassphraseSet()) {
            title = R.string.title_change_passphrase;
        } else {
            title = R.string.title_set_passphrase;
        }

        SetPassphraseDialogFragment setPassphraseDialog = SetPassphraseDialogFragment.newInstance(
                messenger, title);

        setPassphraseDialog.show(getSupportFragmentManager(), "setPassphraseDialog");
    }

    /**
     * Build layout based on mUserId, mKeys and mKeysUsages Vectors. It creates Views for every user
     * id and key.
     *
     * @param newKeys
     */
    private void buildLayout(boolean newKeys) {
        setContentView(R.layout.edit_key_activity);

        // find views
        mChangePassphrase = (BootstrapButton) findViewById(R.id.edit_key_btn_change_passphrase);
        mNoPassphrase = (CheckBox) findViewById(R.id.edit_key_no_passphrase);
        // Build layout based on given userIds and keys

        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        LinearLayout container = (LinearLayout) findViewById(R.id.edit_key_container);
        if (mIsPassphraseSet) {
            mChangePassphrase.setText(getString(R.string.btn_change_passphrase));
        }
        mUserIdsView = (SectionView) inflater.inflate(R.layout.edit_key_section, container, false);
        mUserIdsView.setType(Id.type.user_id);
        mUserIdsView.setCanBeEdited(mMasterCanSign);
        mUserIdsView.setUserIds(mUserIds);
        mUserIdsView.setEditorListener(this);
        container.addView(mUserIdsView);
        mKeysView = (SectionView) inflater.inflate(R.layout.edit_key_section, container, false);
        mKeysView.setType(Id.type.key);
        mKeysView.setCanBeEdited(mMasterCanSign);
        mKeysView.setKeys(mKeys, mKeysUsages, newKeys);
        mKeysView.setEditorListener(this);
        container.addView(mKeysView);

        updatePassphraseButtonText();

        mChangePassphrase.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                showSetPassphraseDialog();
            }
        });

        // disable passphrase when no passphrase checkbox is checked!
        mNoPassphrase.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // remove passphrase
                    mSavedNewPassphrase = mNewPassphrase;
                    mNewPassphrase = "";
                    mChangePassphrase.setVisibility(View.GONE);
                } else {
                    mNewPassphrase = mSavedNewPassphrase;
                    mChangePassphrase.setVisibility(View.VISIBLE);
                }
                somethingChanged();
            }
        });
    }

    private long getMasterKeyId() {
        if (mKeysView.getEditors().getChildCount() == 0) {
            return 0;
        }
        return ((KeyEditor) mKeysView.getEditors().getChildAt(0)).getValue().getKeyID();
    }

    public boolean isPassphraseSet() {
        if (mNoPassphrase.isChecked()) {
            return true;
        } else if ((mIsPassphraseSet)
                || (mNewPassphrase != null && !mNewPassphrase.equals(""))) {
            return true;
        } else {
            return false;
        }
    }

    public boolean hasPassphraseChanged() {
        if (mNoPassphrase != null) {
            if (mNoPassphrase.isChecked()) {
                return mIsPassphraseSet;
            } else {
                return (mNewPassphrase != null && !mNewPassphrase.equals(""));
            }
        } else {
            return false;
        }
    }

    private void saveClicked() {
        final long masterKeyId = getMasterKeyId();
        if (needsSaving()) { //make sure, as some versions don't support invalidateOptionsMenu
            try {
                if (!isPassphraseSet()) {
                    throw new PgpGeneralException(this.getString(R.string.set_a_passphrase));
                }

                String passphrase;
                if (mIsPassphraseSet) {
                    passphrase = PassphraseCacheService.getCachedPassphrase(this, masterKeyId);
                } else {
                    passphrase = "";
                }
                if (passphrase == null) {
                    PassphraseDialogFragment.show(this, masterKeyId,
                            new Handler() {
                                @Override
                                public void handleMessage(Message message) {
                                    if (message.what == PassphraseDialogFragment.MESSAGE_OKAY) {
                                        mCurrentPassphrase = PassphraseCacheService.getCachedPassphrase(
                                                EditKeyActivity.this, masterKeyId);
                                        checkEmptyIDsWanted();
                                    }
                                }
                            });
                } else {
                    mCurrentPassphrase = passphrase;
                    checkEmptyIDsWanted();
                }
            } catch (PgpGeneralException e) {
                AppMsg.makeText(this, getString(R.string.error_message, e.getMessage()),
                        AppMsg.STYLE_ALERT).show();
            }
        } else {
            AppMsg.makeText(this, R.string.error_change_something_first, AppMsg.STYLE_ALERT).show();
        }
    }

    private void checkEmptyIDsWanted() {
        try {
            ArrayList<String> userIDs = getUserIds(mUserIdsView);
            List<Boolean> newIDs = mUserIdsView.getNewIDFlags();
            ArrayList<String> originalIDs = mUserIdsView.getOriginalIDs();
            int curID = 0;
            for (String userID : userIDs) {
                if (userID.equals("") && (!userID.equals(originalIDs.get(curID)) || newIDs.get(curID))) {
                    AlertDialog.Builder alert = new AlertDialog.Builder(
                            EditKeyActivity.this);

                    alert.setIcon(R.drawable.ic_dialog_alert_holo_light);
                    alert.setTitle(R.string.warning);
                    alert.setMessage(EditKeyActivity.this.getString(R.string.ask_empty_id_ok));

                    alert.setPositiveButton(EditKeyActivity.this.getString(android.R.string.yes),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();
                                    finallySaveClicked();
                                }
                            }
                    );
                    alert.setNegativeButton(this.getString(android.R.string.no),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();
                                }
                            }
                    );
                    alert.setCancelable(false);
                    alert.create().show();
                    return;
                }
                curID++;
            }
        } catch (PgpGeneralException e) {
            Log.e(Constants.TAG, getString(R.string.error_message, e.getMessage()));
            AppMsg.makeText(this, getString(R.string.error_message, e.getMessage()), AppMsg.STYLE_ALERT).show();
        }
        finallySaveClicked();
    }

    private boolean[] toPrimitiveArray(final List<Boolean> booleanList) {
        final boolean[] primitives = new boolean[booleanList.size()];
        int index = 0;
        for (Boolean object : booleanList) {
            primitives[index++] = object;
        }
        return primitives;
    }

    private void finallySaveClicked() {
        try {
            // Send all information needed to service to edit key in other thread
            Intent intent = new Intent(this, KeychainIntentService.class);

            intent.setAction(KeychainIntentService.ACTION_SAVE_KEYRING);

            SaveKeyringParcel saveParams = new SaveKeyringParcel();
            saveParams.userIDs = getUserIds(mUserIdsView);
            saveParams.originalIDs = mUserIdsView.getOriginalIDs();
            saveParams.deletedIDs = mUserIdsView.getDeletedIDs();
            saveParams.newIDs = toPrimitiveArray(mUserIdsView.getNewIDFlags());
            saveParams.primaryIDChanged = mUserIdsView.primaryChanged();
            saveParams.moddedKeys = toPrimitiveArray(mKeysView.getNeedsSavingArray());
            saveParams.deletedKeys = mKeysView.getDeletedKeys();
            saveParams.keysExpiryDates = getKeysExpiryDates(mKeysView);
            saveParams.keysUsages = getKeysUsages(mKeysView);
            saveParams.newPassphrase = mNewPassphrase;
            saveParams.oldPassphrase = mCurrentPassphrase;
            saveParams.newKeys = toPrimitiveArray(mKeysView.getNewKeysArray());
            saveParams.keys = getKeys(mKeysView);
            saveParams.originalPrimaryID = mUserIdsView.getOriginalPrimaryID();


            // fill values for this action
            Bundle data = new Bundle();
            data.putBoolean(KeychainIntentService.SAVE_KEYRING_CAN_SIGN, mMasterCanSign);
            data.putParcelable(KeychainIntentService.SAVE_KEYRING_PARCEL, saveParams);

            intent.putExtra(KeychainIntentService.EXTRA_DATA, data);

            // Message is received after saving is done in KeychainIntentService
            KeychainIntentServiceHandler saveHandler = new KeychainIntentServiceHandler(this,
                    getString(R.string.progress_saving), ProgressDialog.STYLE_HORIZONTAL) {
                public void handleMessage(Message message) {
                    // handle messages by standard KeychainIntentServiceHandler first
                    super.handleMessage(message);

                    if (message.arg1 == KeychainIntentServiceHandler.MESSAGE_OKAY) {
                        Intent data = new Intent();

                        // return uri pointing to new created key
                        Uri uri = KeychainContract.KeyRings.buildGenericKeyRingUri(
                                String.valueOf(getMasterKeyId()));
                        data.setData(uri);

                        setResult(RESULT_OK, data);
                        finish();
                    }
                }
            };

            // Create a new Messenger for the communication back
            Messenger messenger = new Messenger(saveHandler);
            intent.putExtra(KeychainIntentService.EXTRA_MESSENGER, messenger);

            saveHandler.showProgressDialog(this);

            // start service with intent
            startService(intent);
        } catch (PgpGeneralException e) {
            Log.e(Constants.TAG, getString(R.string.error_message, e.getMessage()));
            AppMsg.makeText(this, getString(R.string.error_message, e.getMessage()),
                    AppMsg.STYLE_ALERT).show();
        }
    }

    private void cancelClicked() {
        if (needsSaving()) { //ask if we want to save
            AlertDialog.Builder alert = new AlertDialog.Builder(
                    EditKeyActivity.this);

            alert.setIcon(R.drawable.ic_dialog_alert_holo_light);
            alert.setTitle(R.string.warning);
            alert.setMessage(EditKeyActivity.this.getString(R.string.ask_save_changed_key));

            alert.setPositiveButton(EditKeyActivity.this.getString(android.R.string.yes),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                            saveClicked();
                        }
                    });
            alert.setNegativeButton(this.getString(android.R.string.no),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                            setResult(RESULT_CANCELED);
                            finish();
                        }
                    });
            alert.setCancelable(false);
            alert.create().show();
        } else {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    /**
     * Returns user ids from the SectionView
     *
     * @param userIdsView
     * @return
     */
    private ArrayList<String> getUserIds(SectionView userIdsView) throws PgpGeneralException {
        ArrayList<String> userIds = new ArrayList<String>();

        ViewGroup userIdEditors = userIdsView.getEditors();

        boolean gotMainUserId = false;
        for (int i = 0; i < userIdEditors.getChildCount(); ++i) {
            UserIdEditor editor = (UserIdEditor) userIdEditors.getChildAt(i);
            String userId;
            userId = editor.getValue();

            if (editor.isMainUserId()) {
                userIds.add(0, userId);
                gotMainUserId = true;
            } else {
                userIds.add(userId);
            }
        }

        if (userIds.size() == 0) {
            throw new PgpGeneralException(getString(R.string.error_key_needs_a_user_id));
        }

        if (!gotMainUserId) {
            throw new PgpGeneralException(getString(R.string.error_main_user_id_must_not_be_empty));
        }

        return userIds;
    }

    /**
     * Returns keys from the SectionView
     *
     * @param keysView
     * @return
     */
    private ArrayList<PGPSecretKey> getKeys(SectionView keysView) throws PgpGeneralException {
        ArrayList<PGPSecretKey> keys = new ArrayList<PGPSecretKey>();

        ViewGroup keyEditors = keysView.getEditors();

        if (keyEditors.getChildCount() == 0) {
            throw new PgpGeneralException(getString(R.string.error_key_needs_master_key));
        }

        for (int i = 0; i < keyEditors.getChildCount(); ++i) {
            KeyEditor editor = (KeyEditor) keyEditors.getChildAt(i);
            keys.add(editor.getValue());
        }

        return keys;
    }

    /**
     * Returns usage selections of keys from the SectionView
     *
     * @param keysView
     * @return
     */
    private ArrayList<Integer> getKeysUsages(SectionView keysView) throws PgpGeneralException {
        ArrayList<Integer> keysUsages = new ArrayList<Integer>();

        ViewGroup keyEditors = keysView.getEditors();

        if (keyEditors.getChildCount() == 0) {
            throw new PgpGeneralException(getString(R.string.error_key_needs_master_key));
        }

        for (int i = 0; i < keyEditors.getChildCount(); ++i) {
            KeyEditor editor = (KeyEditor) keyEditors.getChildAt(i);
            keysUsages.add(editor.getUsage());
        }

        return keysUsages;
    }

    private ArrayList<GregorianCalendar> getKeysExpiryDates(SectionView keysView) throws PgpGeneralException {
        ArrayList<GregorianCalendar> keysExpiryDates = new ArrayList<GregorianCalendar>();

        ViewGroup keyEditors = keysView.getEditors();

        if (keyEditors.getChildCount() == 0) {
            throw new PgpGeneralException(getString(R.string.error_key_needs_master_key));
        }

        for (int i = 0; i < keyEditors.getChildCount(); ++i) {
            KeyEditor editor = (KeyEditor) keyEditors.getChildAt(i);
            keysExpiryDates.add(editor.getExpiryDate());
        }

        return keysExpiryDates;
    }

    private void updatePassphraseButtonText() {
        mChangePassphrase.setText(isPassphraseSet() ? getString(R.string.btn_change_passphrase)
                : getString(R.string.btn_set_passphrase));
    }

}
