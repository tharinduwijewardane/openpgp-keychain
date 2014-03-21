/*
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

package org.sufficientlysecure.keychain;

import android.os.Environment;

import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.sufficientlysecure.keychain.service.remote.RegisteredAppsListActivity;
import org.sufficientlysecure.keychain.ui.DecryptActivity;
import org.sufficientlysecure.keychain.ui.EncryptActivity;
import org.sufficientlysecure.keychain.ui.ImportKeysActivity;
import org.sufficientlysecure.keychain.ui.KeyListActivity;

public final class Constants {

    public static final boolean DEBUG = BuildConfig.DEBUG;

    public static final String TAG = "Keychain";

    public static final String PACKAGE_NAME = "org.sufficientlysecure.keychain";

    // as defined in http://tools.ietf.org/html/rfc3156, section 7
    public static final String NFC_MIME = "application/pgp-keys";

    // used by QR Codes (Guardian Project, Monkeysphere compatiblity)
    public static final String FINGERPRINT_SCHEME = "openpgp4fpr";

    // Not BC due to the use of Spongy Castle for Android
    public static final String SC = BouncyCastleProvider.PROVIDER_NAME;
    public static final String BOUNCY_CASTLE_PROVIDER_NAME = SC;

    public static final String INTENT_PREFIX = PACKAGE_NAME + ".action.";

    public static final class Path {
        public static final String APP_DIR = Environment.getExternalStorageDirectory()
                + "/OpenPGP-Keychain";
        public static final String APP_DIR_FILE_SEC = APP_DIR + "/secexport.asc";
        public static final String APP_DIR_FILE_PUB = APP_DIR + "/pubexport.asc";
    }

    public static final class Pref {
        public static final String DEFAULT_ENCRYPTION_ALGORITHM = "defaultEncryptionAlgorithm";
        public static final String DEFAULT_HASH_ALGORITHM = "defaultHashAlgorithm";
        public static final String DEFAULT_ASCII_ARMOUR = "defaultAsciiArmour";
        public static final String DEFAULT_MESSAGE_COMPRESSION = "defaultMessageCompression";
        public static final String DEFAULT_FILE_COMPRESSION = "defaultFileCompression";
        public static final String PASS_PHRASE_CACHE_TTL = "passphraseCacheTtl";
        public static final String LANGUAGE = "language";
        public static final String FORCE_V3_SIGNATURES = "forceV3Signatures";
        public static final String KEY_SERVERS = "keyServers";
    }

    public static final class Defaults {
        public static final String KEY_SERVERS = "pool.sks-keyservers.net, subkeys.pgp.net, pgp.mit.edu";
    }

    public static final class DrawerItems {
        public static final Class KEY_LIST = KeyListActivity.class;
        public static final Class ENCRYPT = EncryptActivity.class;
        public static final Class DECRYPT = DecryptActivity.class;
        public static final Class IMPORT_KEYS = ImportKeysActivity.class;
        public static final Class REGISTERED_APPS_LIST = RegisteredAppsListActivity.class;
        public static final Class[] ARRAY = new Class[]{KEY_LIST, ENCRYPT, DECRYPT,
                                                IMPORT_KEYS, REGISTERED_APPS_LIST};
    }
}
