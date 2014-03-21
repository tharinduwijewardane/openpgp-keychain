/*
 * Copyright (C) 2012 Dominik Schürmann <dominik@dominikschuermann.de>
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

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AnimationUtils;
import android.widget.*;
import com.beardedhen.androidbootstrap.BootstrapButton;
import com.devspark.appmsg.AppMsg;
import org.openintents.openpgp.OpenPgpSignatureResult;
import org.spongycastle.openpgp.PGPPublicKeyRing;
import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.Id;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.compatibility.ClipboardReflection;
import org.sufficientlysecure.keychain.helper.ActionBarHelper;
import org.sufficientlysecure.keychain.helper.FileHelper;
import org.sufficientlysecure.keychain.pgp.PgpDecryptVerify;
import org.sufficientlysecure.keychain.pgp.PgpDecryptVerifyResult;
import org.sufficientlysecure.keychain.pgp.PgpHelper;
import org.sufficientlysecure.keychain.pgp.PgpKeyHelper;
import org.sufficientlysecure.keychain.pgp.exception.NoAsymmetricEncryptionException;
import org.sufficientlysecure.keychain.pgp.exception.PgpGeneralException;
import org.sufficientlysecure.keychain.provider.ProviderHelper;
import org.sufficientlysecure.keychain.service.KeychainIntentService;
import org.sufficientlysecure.keychain.service.KeychainIntentServiceHandler;
import org.sufficientlysecure.keychain.service.PassphraseCacheService;
import org.sufficientlysecure.keychain.ui.dialog.DeleteFileDialogFragment;
import org.sufficientlysecure.keychain.ui.dialog.FileDialogFragment;
import org.sufficientlysecure.keychain.ui.dialog.PassphraseDialogFragment;
import org.sufficientlysecure.keychain.util.Log;

import java.io.*;
import java.util.regex.Matcher;

@SuppressLint("NewApi")
public class DecryptActivity extends DrawerActivity {

    /* Intents */
    // without permission
    public static final String ACTION_DECRYPT = Constants.INTENT_PREFIX + "DECRYPT";

    /* EXTRA keys for input */
    public static final String EXTRA_TEXT = "text";

    private static final int RESULT_CODE_LOOKUP_KEY = 0x00007006;
    private static final int RESULT_CODE_FILE = 0x00007003;

    private long mSignatureKeyId = 0;

    private boolean mReturnResult = false;

    // TODO: replace signed only checks with something more intelligent
    // PgpDecryptVerify should handle all automatically!!!
    private boolean mSignedOnly = false;
    private boolean mAssumeSymmetricEncryption = false;

    private EditText mMessage = null;
    private RelativeLayout mSignatureLayout = null;
    private ImageView mSignatureStatusImage = null;
    private TextView mUserId = null;
    private TextView mUserIdRest = null;

    private ViewFlipper mSource = null;
    private TextView mSourceLabel = null;
    private ImageView mSourcePrevious = null;
    private ImageView mSourceNext = null;

    private int mDecryptTarget;

    private EditText mFilename = null;
    private CheckBox mDeleteAfter = null;
    private BootstrapButton mBrowse = null;
    private BootstrapButton mLookupKey = null;

    private String mInputFilename = null;
    private String mOutputFilename = null;

    private Uri mContentUri = null;
    private boolean mReturnBinary = false;

    private long mSecretKeyId = Id.key.none;

    private FileDialogFragment mFileDialog;

    private boolean mDecryptImmediately = false;

    private BootstrapButton mDecryptButton;

    private void initView() {
        mSource = (ViewFlipper) findViewById(R.id.source);
        mSourceLabel = (TextView) findViewById(R.id.sourceLabel);
        mSourcePrevious = (ImageView) findViewById(R.id.sourcePrevious);
        mSourceNext = (ImageView) findViewById(R.id.sourceNext);

        mSourcePrevious.setClickable(true);
        mSourcePrevious.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mSource.setInAnimation(AnimationUtils.loadAnimation(DecryptActivity.this,
                        R.anim.push_right_in));
                mSource.setOutAnimation(AnimationUtils.loadAnimation(DecryptActivity.this,
                        R.anim.push_right_out));
                mSource.showPrevious();
                updateSource();
            }
        });

        mSourceNext.setClickable(true);
        OnClickListener nextSourceClickListener = new OnClickListener() {
            public void onClick(View v) {
                mSource.setInAnimation(AnimationUtils.loadAnimation(DecryptActivity.this,
                        R.anim.push_left_in));
                mSource.setOutAnimation(AnimationUtils.loadAnimation(DecryptActivity.this,
                        R.anim.push_left_out));
                mSource.showNext();
                updateSource();
            }
        };
        mSourceNext.setOnClickListener(nextSourceClickListener);

        mSourceLabel.setClickable(true);
        mSourceLabel.setOnClickListener(nextSourceClickListener);

        mMessage = (EditText) findViewById(R.id.message);
        mSignatureLayout = (RelativeLayout) findViewById(R.id.signature);
        mSignatureStatusImage = (ImageView) findViewById(R.id.ic_signature_status);
        mUserId = (TextView) findViewById(R.id.mainUserId);
        mUserIdRest = (TextView) findViewById(R.id.mainUserIdRest);

        // measure the height of the source_file view and set the message view's min height to that,
        // so it fills mSource fully... bit of a hack.
        View tmp = findViewById(R.id.sourceFile);
        tmp.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int height = tmp.getMeasuredHeight();
        mMessage.setMinimumHeight(height);

        mFilename = (EditText) findViewById(R.id.filename);
        mBrowse = (BootstrapButton) findViewById(R.id.btn_browse);
        mBrowse.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                FileHelper.openFile(DecryptActivity.this, mFilename.getText().toString(), "*/*",
                        RESULT_CODE_FILE);
            }
        });

        mLookupKey = (BootstrapButton) findViewById(R.id.lookup_key);
        mLookupKey.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                lookupUnknownKey(mSignatureKeyId);
            }
        });

        mDeleteAfter = (CheckBox) findViewById(R.id.deleteAfterDecryption);

        // default: message source
        mSource.setInAnimation(null);
        mSource.setOutAnimation(null);
        while (mSource.getCurrentView().getId() != R.id.sourceMessage) {
            mSource.showNext();
        }

        mDecryptButton = (BootstrapButton) findViewById(R.id.action_decrypt);
        mDecryptButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                decryptClicked();
            }
        });
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.decrypt_activity);

        // set actionbar without home button if called from another app
        ActionBarHelper.setBackButton(this);

        initView();

        setupDrawerNavigation(savedInstanceState);

        // Handle intent actions
        handleActions(getIntent());

        if (mSource.getCurrentView().getId() == R.id.sourceMessage
                && mMessage.getText().length() == 0) {

            CharSequence clipboardText = ClipboardReflection.getClipboardText(this);

            String data = "";
            if (clipboardText != null) {
                Matcher matcher = PgpHelper.PGP_MESSAGE.matcher(clipboardText);
                if (!matcher.matches()) {
                    matcher = PgpHelper.PGP_SIGNED_MESSAGE.matcher(clipboardText);
                }
                if (matcher.matches()) {
                    data = matcher.group(1);
                    mMessage.setText(data);
                    AppMsg.makeText(this, R.string.using_clipboard_content, AppMsg.STYLE_INFO)
                            .show();
                }
            }
        }

        mSignatureLayout.setVisibility(View.GONE);
        mSignatureLayout.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (mSignatureKeyId == 0) {
                    return;
                }
                PGPPublicKeyRing key = ProviderHelper.getPGPPublicKeyRingByKeyId(
                        DecryptActivity.this, mSignatureKeyId);
                if (key != null) {
                    Intent intent = new Intent(DecryptActivity.this, ImportKeysActivity.class);
                    intent.setAction(ImportKeysActivity.ACTION_IMPORT_KEY_FROM_KEYSERVER);
                    intent.putExtra(ImportKeysActivity.EXTRA_KEY_ID, mSignatureKeyId);
                    startActivity(intent);
                }
            }
        });

        if (mReturnResult) {
            mSourcePrevious.setClickable(false);
            mSourcePrevious.setEnabled(false);
            mSourcePrevious.setVisibility(View.INVISIBLE);

            mSourceNext.setClickable(false);
            mSourceNext.setEnabled(false);
            mSourceNext.setVisibility(View.INVISIBLE);

            mSourceLabel.setClickable(false);
            mSourceLabel.setEnabled(false);
        }

        updateSource();

        if (mDecryptImmediately
                || (mSource.getCurrentView().getId() == R.id.sourceMessage && (mMessage.getText()
                .length() > 0 || mContentUri != null))) {
            decryptClicked();
        }
    }

    /**
     * Handles all actions with this intent
     *
     * @param intent
     */
    private void handleActions(Intent intent) {
        String action = intent.getAction();
        Bundle extras = intent.getExtras();
        String type = intent.getType();
        Uri uri = intent.getData();

        if (extras == null) {
            extras = new Bundle();
        }

        /*
         * Android's Action
         */
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            // When sending to Keychain Decrypt via share menu
            if ("text/plain".equals(type)) {
                // Plain text
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sharedText != null) {
                    // handle like normal text decryption, override action and extras to later
                    // executeServiceMethod ACTION_DECRYPT in main actions
                    extras.putString(EXTRA_TEXT, sharedText);
                    action = ACTION_DECRYPT;
                }
            } else {
                // Binary via content provider (could also be files)
                // override uri to get stream from send
                uri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
                action = ACTION_DECRYPT;
            }
        } else if (Intent.ACTION_VIEW.equals(action)) {
            // Android's Action when opening file associated to Keychain (see AndroidManifest.xml)

            // override action
            action = ACTION_DECRYPT;
        }

        String textData = extras.getString(EXTRA_TEXT);

        /**
         * Main Actions
         */
        if (ACTION_DECRYPT.equals(action) && textData != null) {
            Log.d(Constants.TAG, "textData null, matching text ...");
            Matcher matcher = PgpHelper.PGP_MESSAGE.matcher(textData);
            if (matcher.matches()) {
                Log.d(Constants.TAG, "PGP_MESSAGE matched");
                textData = matcher.group(1);
                // replace non breakable spaces
                textData = textData.replaceAll("\\xa0", " ");
                mMessage.setText(textData);
            } else {
                matcher = PgpHelper.PGP_SIGNED_MESSAGE.matcher(textData);
                if (matcher.matches()) {
                    Log.d(Constants.TAG, "PGP_SIGNED_MESSAGE matched");
                    textData = matcher.group(1);
                    // replace non breakable spaces
                    textData = textData.replaceAll("\\xa0", " ");
                    mMessage.setText(textData);
                } else {
                    Log.d(Constants.TAG, "Nothing matched!");
                }
            }
        } else if (ACTION_DECRYPT.equals(action) && uri != null) {
            // get file path from uri
            String path = FileHelper.getPath(this, uri);

            if (path != null) {
                mInputFilename = path;
                mFilename.setText(mInputFilename);
                guessOutputFilename();
                mSource.setInAnimation(null);
                mSource.setOutAnimation(null);
                while (mSource.getCurrentView().getId() != R.id.sourceFile) {
                    mSource.showNext();
                }
            } else {
                Log.e(Constants.TAG,
                "Direct binary data without actual file in filesystem is not supported. Please use the Remote Service API!");
                Toast.makeText(this, R.string.error_only_files_are_supported, Toast.LENGTH_LONG)
                        .show();
                // end activity
                finish();
            }
        } else {
            Log.e(Constants.TAG,
                    "Include the extra 'text' or an Uri with setData() in your Intent!");
        }
    }

    private void guessOutputFilename() {
        mInputFilename = mFilename.getText().toString();
        File file = new File(mInputFilename);
        String filename = file.getName();
        if (filename.endsWith(".asc") || filename.endsWith(".gpg") || filename.endsWith(".pgp")) {
            filename = filename.substring(0, filename.length() - 4);
        }
        mOutputFilename = Constants.Path.APP_DIR + "/" + filename;
    }

    private void updateSource() {
        switch (mSource.getCurrentView().getId()) {
            case R.id.sourceFile: {
                mSourceLabel.setText(R.string.label_file);
                mDecryptButton.setText(getString(R.string.btn_decrypt));
                break;
            }

            case R.id.sourceMessage: {
                mSourceLabel.setText(R.string.label_message);
                mDecryptButton.setText(getString(R.string.btn_decrypt));
                break;
            }

            default: {
                break;
            }
        }
    }

    private void decryptClicked() {
        if (mSource.getCurrentView().getId() == R.id.sourceFile) {
            mDecryptTarget = Id.target.file;
        } else {
            mDecryptTarget = Id.target.message;
        }
        initiateDecryption();
    }

    private void initiateDecryption() {
        if (mDecryptTarget == Id.target.file) {
            String currentFilename = mFilename.getText().toString();
            if (mInputFilename == null || !mInputFilename.equals(currentFilename)) {
                guessOutputFilename();
            }

            if (mInputFilename.equals("")) {
                AppMsg.makeText(this, R.string.no_file_selected, AppMsg.STYLE_ALERT).show();
                return;
            }

            if (mInputFilename.startsWith("file")) {
                File file = new File(mInputFilename);
                if (!file.exists() || !file.isFile()) {
                    AppMsg.makeText(
                            this,
                            getString(R.string.error_message,
                                    getString(R.string.error_file_not_found)), AppMsg.STYLE_ALERT)
                            .show();
                    return;
                }
            }
        }

        if (mDecryptTarget == Id.target.message) {
            String messageData = mMessage.getText().toString();
            Matcher matcher = PgpHelper.PGP_SIGNED_MESSAGE.matcher(messageData);
            if (matcher.matches()) {
                mSignedOnly = true;
                decryptStart();
                return;
            }
        }

        // else treat it as an decrypted message/file
        mSignedOnly = false;

        getDecryptionKeyFromInputStream();

        // if we need a symmetric passphrase or a passphrase to use a secret key ask for it
        if (mSecretKeyId == Id.key.symmetric
                || PassphraseCacheService.getCachedPassphrase(this, mSecretKeyId) == null) {
            showPassphraseDialog();
        } else {
            if (mDecryptTarget == Id.target.file) {
                askForOutputFilename();
            } else { // mDecryptTarget == Id.target.message
                decryptStart();
            }
        }
    }

    /**
     * Shows passphrase dialog to cache a new passphrase the user enters for using it later for
     * encryption. Based on mSecretKeyId it asks for a passphrase to open a private key or it asks
     * for a symmetric passphrase
     */
    private void showPassphraseDialog() {
        // Message is received after passphrase is cached
        Handler returnHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                if (message.what == PassphraseDialogFragment.MESSAGE_OKAY) {
                    if (mDecryptTarget == Id.target.file) {
                        askForOutputFilename();
                    } else {
                        decryptStart();
                    }
                }
            }
        };

        // Create a new Messenger for the communication back
        Messenger messenger = new Messenger(returnHandler);

        try {
            PassphraseDialogFragment passphraseDialog = PassphraseDialogFragment.newInstance(this,
                    messenger, mSecretKeyId);

            passphraseDialog.show(getSupportFragmentManager(), "passphraseDialog");
        } catch (PgpGeneralException e) {
            Log.d(Constants.TAG, "No passphrase for this secret key, encrypt directly!");
            // send message to handler to start encryption directly
            returnHandler.sendEmptyMessage(PassphraseDialogFragment.MESSAGE_OKAY);
        }
    }

    /**
     * TODO: Rework function, remove global variables
     */
    private void getDecryptionKeyFromInputStream() {
        InputStream inStream = null;
        if (mContentUri != null) {
            try {
                inStream = getContentResolver().openInputStream(mContentUri);
            } catch (FileNotFoundException e) {
                Log.e(Constants.TAG, "File not found!", e);
                AppMsg.makeText(this, getString(R.string.error_file_not_found, e.getMessage()),
                        AppMsg.STYLE_ALERT).show();
            }
        } else if (mDecryptTarget == Id.target.file) {
            // check if storage is ready
            if (!FileHelper.isStorageMounted(mInputFilename)) {
                AppMsg.makeText(this, getString(R.string.error_external_storage_not_ready),
                        AppMsg.STYLE_ALERT).show();
                return;
            }

            try {
                inStream = new BufferedInputStream(new FileInputStream(mInputFilename));
            } catch (FileNotFoundException e) {
                Log.e(Constants.TAG, "File not found!", e);
                AppMsg.makeText(this, getString(R.string.error_file_not_found, e.getMessage()),
                        AppMsg.STYLE_ALERT).show();
            } finally {
                try {
                    if (inStream != null) {
                        inStream.close();
                    }
                } catch (Exception e) {
                }
            }
        } else {
            inStream = new ByteArrayInputStream(mMessage.getText().toString().getBytes());
        }

        // get decryption key for this inStream
        try {
            try {
                if (inStream.markSupported()) {
                    inStream.mark(200); // should probably set this to the max size of two pgpF
                    // objects, if it even needs to be anything other than 0.
                }
                mSecretKeyId = PgpHelper.getDecryptionKeyId(this, inStream);
                if (mSecretKeyId == Id.key.none) {
                    throw new PgpGeneralException(getString(R.string.error_no_secret_key_found));
                }
                mAssumeSymmetricEncryption = false;
            } catch (NoAsymmetricEncryptionException e) {
                if (inStream.markSupported()) {
                    inStream.reset();
                }
                mSecretKeyId = Id.key.symmetric;
                if (!PgpDecryptVerify.hasSymmetricEncryption(this, inStream)) {
                    throw new PgpGeneralException(
                            getString(R.string.error_no_known_encryption_found));
                }
                mAssumeSymmetricEncryption = true;
            }
        } catch (Exception e) {
            AppMsg.makeText(this, getString(R.string.error_message, e.getMessage()),
                    AppMsg.STYLE_ALERT).show();
        }
    }

    private void replyClicked() {
        Intent intent = new Intent(this, EncryptActivity.class);
        intent.setAction(EncryptActivity.ACTION_ENCRYPT);
        String data = mMessage.getText().toString();
        data = data.replaceAll("(?m)^", "> ");
        data = "\n\n" + data;
        intent.putExtra(EncryptActivity.EXTRA_TEXT, data);
        intent.putExtra(EncryptActivity.EXTRA_SIGNATURE_KEY_ID, mSecretKeyId);
        intent.putExtra(EncryptActivity.EXTRA_ENCRYPTION_KEY_IDS, new long[]{mSignatureKeyId});
        startActivity(intent);
    }

    private void askForOutputFilename() {
        // Message is received after passphrase is cached
        Handler returnHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                if (message.what == FileDialogFragment.MESSAGE_OKAY) {
                    Bundle data = message.getData();
                    mOutputFilename = data.getString(FileDialogFragment.MESSAGE_DATA_FILENAME);
                    decryptStart();
                }
            }
        };

        // Create a new Messenger for the communication back
        Messenger messenger = new Messenger(returnHandler);

        mFileDialog = FileDialogFragment.newInstance(messenger,
                getString(R.string.title_decrypt_to_file),
                getString(R.string.specify_file_to_decrypt_to), mOutputFilename, null);

        mFileDialog.show(getSupportFragmentManager(), "fileDialog");
    }

    private void lookupUnknownKey(long unknownKeyId) {
        Intent intent = new Intent(this, ImportKeysActivity.class);
        intent.setAction(ImportKeysActivity.ACTION_IMPORT_KEY_FROM_KEYSERVER);
        intent.putExtra(ImportKeysActivity.EXTRA_KEY_ID, unknownKeyId);
        startActivityForResult(intent, RESULT_CODE_LOOKUP_KEY);
    }

    private void decryptStart() {
        Log.d(Constants.TAG, "decryptStart");

        // Send all information needed to service to decrypt in other thread
        Intent intent = new Intent(this, KeychainIntentService.class);

        // fill values for this action
        Bundle data = new Bundle();

        intent.setAction(KeychainIntentService.ACTION_DECRYPT_VERIFY);

        // choose action based on input: decrypt stream, file or bytes
        if (mContentUri != null) {
            data.putInt(KeychainIntentService.TARGET, KeychainIntentService.TARGET_STREAM);

            data.putParcelable(KeychainIntentService.ENCRYPT_PROVIDER_URI, mContentUri);
        } else if (mDecryptTarget == Id.target.file) {
            data.putInt(KeychainIntentService.TARGET, KeychainIntentService.TARGET_URI);

            Log.d(Constants.TAG, "mInputFilename=" + mInputFilename + ", mOutputFilename="
                    + mOutputFilename);

            data.putString(KeychainIntentService.ENCRYPT_INPUT_FILE, mInputFilename);
            data.putString(KeychainIntentService.ENCRYPT_OUTPUT_FILE, mOutputFilename);
        } else {
            data.putInt(KeychainIntentService.TARGET, KeychainIntentService.TARGET_BYTES);

            String message = mMessage.getText().toString();
            data.putByteArray(KeychainIntentService.DECRYPT_CIPHERTEXT_BYTES, message.getBytes());
        }

        data.putLong(KeychainIntentService.ENCRYPT_SECRET_KEY_ID, mSecretKeyId);

        data.putBoolean(KeychainIntentService.DECRYPT_RETURN_BYTES, mReturnBinary);
        data.putBoolean(KeychainIntentService.DECRYPT_ASSUME_SYMMETRIC, mAssumeSymmetricEncryption);

        intent.putExtra(KeychainIntentService.EXTRA_DATA, data);

        // Message is received after encrypting is done in ApgService
        KeychainIntentServiceHandler saveHandler = new KeychainIntentServiceHandler(this,
                getString(R.string.progress_decrypting), ProgressDialog.STYLE_HORIZONTAL) {
            public void handleMessage(Message message) {
                // handle messages by standard ApgHandler first
                super.handleMessage(message);

                if (message.arg1 == KeychainIntentServiceHandler.MESSAGE_OKAY) {
                    // get returned data bundle
                    Bundle returnData = message.getData();

                    mSignatureKeyId = 0;
                    mSignatureLayout.setVisibility(View.GONE);

                    AppMsg.makeText(DecryptActivity.this, R.string.decryption_successful,
                            AppMsg.STYLE_INFO).show();
                    if (mReturnResult) {
                        Intent intent = new Intent();
                        intent.putExtras(returnData);
                        setResult(RESULT_OK, intent);
                        finish();
                        return;
                    }

                    switch (mDecryptTarget) {
                        case Id.target.message:
                            String decryptedMessage = returnData
                                    .getString(KeychainIntentService.RESULT_DECRYPTED_STRING);
                            mMessage.setText(decryptedMessage);
                            mMessage.setHorizontallyScrolling(false);

                            break;

                        case Id.target.file:
                            if (mDeleteAfter.isChecked()) {
                                // Create and show dialog to delete original file
                                DeleteFileDialogFragment deleteFileDialog = DeleteFileDialogFragment
                                        .newInstance(mInputFilename);
                                deleteFileDialog.show(getSupportFragmentManager(), "deleteDialog");
                            }
                            break;

                        default:
                            // shouldn't happen
                            break;

                    }

                    PgpDecryptVerifyResult decryptVerifyResult =
                            returnData.getParcelable(KeychainIntentService.RESULT_DECRYPT_VERIFY_RESULT);

                    OpenPgpSignatureResult signatureResult = decryptVerifyResult.getSignatureResult();

                    if (signatureResult != null) {

                        String userId = signatureResult.getUserId();
                        mSignatureKeyId = signatureResult.getKeyId();
                        mUserIdRest.setText("id: "
                                + PgpKeyHelper.convertKeyIdToHex(mSignatureKeyId));
                        if (userId == null) {
                            userId = getResources().getString(R.string.user_id_no_name);
                        }
                        String chunks[] = userId.split(" <", 2);
                        userId = chunks[0];
                        if (chunks.length > 1) {
                            mUserIdRest.setText("<" + chunks[1]);
                        }
                        mUserId.setText(userId);

                        switch (signatureResult.getStatus()) {
                            case OpenPgpSignatureResult.SIGNATURE_SUCCESS_UNCERTIFIED: {
                                mSignatureStatusImage.setImageResource(R.drawable.overlay_ok);
                                mLookupKey.setVisibility(View.GONE);
                                break;
                            }

                            // TODO!
//                            case OpenPgpSignatureResult.SIGNATURE_SUCCESS_CERTIFIED: {
//                                break;
//                            }

                            case OpenPgpSignatureResult.SIGNATURE_UNKNOWN_PUB_KEY: {
                                mSignatureStatusImage.setImageResource(R.drawable.overlay_error);
                                mLookupKey.setVisibility(View.VISIBLE);
                                AppMsg.makeText(DecryptActivity.this,
                                        R.string.unknown_signature,
                                        AppMsg.STYLE_ALERT).show();
                                break;
                            }

                            default: {
                                mSignatureStatusImage.setImageResource(R.drawable.overlay_error);
                                mLookupKey.setVisibility(View.GONE);
                                break;
                            }
                        }
                        mSignatureLayout.setVisibility(View.VISIBLE);
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case RESULT_CODE_FILE: {
                if (resultCode == RESULT_OK && data != null) {
                    try {
                        String path = FileHelper.getPath(this, data.getData());
                        Log.d(Constants.TAG, "path=" + path);

                        mFilename.setText(path);
                    } catch (NullPointerException e) {
                        Log.e(Constants.TAG, "Nullpointer while retrieving path!");
                    }
                }
                return;
            }

            // this request is returned after LookupUnknownKeyDialogFragment started
            // ImportKeysActivity and user looked uo key
            case RESULT_CODE_LOOKUP_KEY: {
                Log.d(Constants.TAG, "Returning from Lookup Key...");
                if (resultCode == RESULT_OK) {
                    // decrypt again
                    decryptStart();
                }
                return;
            }

            default: {
                super.onActivityResult(requestCode, resultCode, data);

                break;
            }
        }
    }

}
