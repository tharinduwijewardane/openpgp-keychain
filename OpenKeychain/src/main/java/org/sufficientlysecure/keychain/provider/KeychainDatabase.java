/*
 * Copyright (C) 2012-2014 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.sufficientlysecure.keychain.provider;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import org.spongycastle.openpgp.PGPKeyRing;
import org.spongycastle.openpgp.PGPPublicKeyRing;
import org.spongycastle.openpgp.PGPSecretKeyRing;
import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.pgp.PgpConversionHelper;
import org.sufficientlysecure.keychain.provider.KeychainContract.ApiAppsColumns;
import org.sufficientlysecure.keychain.provider.KeychainContract.ApiAppsAccountsColumns;
import org.sufficientlysecure.keychain.provider.KeychainContract.KeyRingsColumns;
import org.sufficientlysecure.keychain.provider.KeychainContract.KeysColumns;
import org.sufficientlysecure.keychain.provider.KeychainContract.UserIdsColumns;
import org.sufficientlysecure.keychain.provider.KeychainContract.CertsColumns;
import org.sufficientlysecure.keychain.util.Log;

import java.io.IOException;

public class KeychainDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "openkeychain.db";
    private static final int DATABASE_VERSION = 1;
    static Boolean apgHack = false;

    public interface Tables {
        String KEY_RINGS_PUBLIC = "keyrings_public";
        String KEY_RINGS_SECRET = "keyrings_secret";
        String KEYS = "keys";
        String USER_IDS = "user_ids";
        String CERTS = "certs";
        String API_APPS = "api_apps";
        String API_ACCOUNTS = "api_accounts";
    }

    private static final String CREATE_KEYRINGS_PUBLIC =
            "CREATE TABLE IF NOT EXISTS keyrings_public ("
                + KeyRingsColumns.MASTER_KEY_ID + " INTEGER PRIMARY KEY,"
                + KeyRingsColumns.KEY_RING_DATA + " BLOB"
            + ")";

    private static final String CREATE_KEYRINGS_SECRET =
            "CREATE TABLE IF NOT EXISTS keyrings_secret ("
                    + KeyRingsColumns.MASTER_KEY_ID + " INTEGER PRIMARY KEY,"
                    + KeyRingsColumns.KEY_RING_DATA + " BLOB,"
                    + "FOREIGN KEY(" + KeyRingsColumns.MASTER_KEY_ID + ") "
                        + "REFERENCES keyrings_public(" + KeyRingsColumns.MASTER_KEY_ID + ") ON DELETE CASCADE"
            + ")";

    private static final String CREATE_KEYS =
            "CREATE TABLE IF NOT EXISTS " + Tables.KEYS + " ("
                + KeysColumns.MASTER_KEY_ID + " INTEGER, "
                + KeysColumns.RANK + " INTEGER, "

                + KeysColumns.KEY_ID + " INTEGER, "
                + KeysColumns.KEY_SIZE + " INTEGER, "
                + KeysColumns.ALGORITHM + " INTEGER, "
                + KeysColumns.FINGERPRINT + " BLOB, "

                + KeysColumns.CAN_CERTIFY + " BOOLEAN, "
                + KeysColumns.CAN_SIGN + " BOOLEAN, "
                + KeysColumns.CAN_ENCRYPT + " BOOLEAN, "
                + KeysColumns.IS_REVOKED + " BOOLEAN, "

                + KeysColumns.CREATION + " INTEGER, "
                + KeysColumns.EXPIRY + " INTEGER, "

                + "PRIMARY KEY(" + KeysColumns.MASTER_KEY_ID + ", " + KeysColumns.RANK + "),"
                + "FOREIGN KEY(" + KeysColumns.MASTER_KEY_ID + ") REFERENCES "
                    + Tables.KEY_RINGS_PUBLIC + "(" + KeyRingsColumns.MASTER_KEY_ID + ") ON DELETE CASCADE"
            + ")";

    private static final String CREATE_USER_IDS =
            "CREATE TABLE IF NOT EXISTS " + Tables.USER_IDS + "("
                + UserIdsColumns.MASTER_KEY_ID + " INTEGER, "
                + UserIdsColumns.USER_ID + " CHARMANDER, "

                + UserIdsColumns.IS_PRIMARY + " BOOLEAN, "
                + UserIdsColumns.IS_REVOKED + " BOOLEAN, "
                + UserIdsColumns.RANK+ " INTEGER, "

                + "PRIMARY KEY(" + UserIdsColumns.MASTER_KEY_ID + ", " + UserIdsColumns.USER_ID + "), "
                + "UNIQUE (" + UserIdsColumns.MASTER_KEY_ID + ", " + UserIdsColumns.RANK + "), "
                + "FOREIGN KEY(" + UserIdsColumns.MASTER_KEY_ID + ") REFERENCES "
                    + Tables.KEY_RINGS_PUBLIC + "(" + KeyRingsColumns.MASTER_KEY_ID + ") ON DELETE CASCADE"
            + ")";

    private static final String CREATE_CERTS =
            "CREATE TABLE IF NOT EXISTS " + Tables.CERTS + "("
                + CertsColumns.MASTER_KEY_ID + " INTEGER,"
                + CertsColumns.RANK + " INTEGER, " // rank of certified uid

                + CertsColumns.KEY_ID_CERTIFIER + " INTEGER, " // certifying key
                + CertsColumns.TYPE + " INTEGER, "
                + CertsColumns.VERIFIED + " INTEGER, "
                + CertsColumns.CREATION + " INTEGER, "

                + CertsColumns.DATA + " BLOB, "

                + "PRIMARY KEY(" + CertsColumns.MASTER_KEY_ID + ", " + CertsColumns.RANK + ", "
                    + CertsColumns.KEY_ID_CERTIFIER + "), "
                + "FOREIGN KEY(" + CertsColumns.MASTER_KEY_ID + ") REFERENCES "
                    + Tables.KEY_RINGS_PUBLIC + "(" + KeyRingsColumns.MASTER_KEY_ID + ") ON DELETE CASCADE,"
                + "FOREIGN KEY(" + CertsColumns.MASTER_KEY_ID + ", " + CertsColumns.RANK + ") REFERENCES "
                    + Tables.USER_IDS + "(" + UserIdsColumns.MASTER_KEY_ID + ", " + UserIdsColumns.RANK + ") ON DELETE CASCADE"
            + ")";

    private static final String CREATE_API_APPS = "CREATE TABLE IF NOT EXISTS " + Tables.API_APPS
            + " (" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + ApiAppsColumns.PACKAGE_NAME + " TEXT NOT NULL UNIQUE, "
            + ApiAppsColumns.PACKAGE_SIGNATURE + " BLOB)";

    private static final String CREATE_API_APPS_ACCOUNTS = "CREATE TABLE IF NOT EXISTS " + Tables.API_ACCOUNTS
            + " (" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + ApiAppsAccountsColumns.ACCOUNT_NAME + " TEXT NOT NULL, "
            + ApiAppsAccountsColumns.KEY_ID + " INT64, "
            + ApiAppsAccountsColumns.ENCRYPTION_ALGORITHM + " INTEGER, "
            + ApiAppsAccountsColumns.HASH_ALORITHM + " INTEGER, "
            + ApiAppsAccountsColumns.COMPRESSION + " INTEGER, "
            + ApiAppsAccountsColumns.PACKAGE_NAME + " TEXT NOT NULL, "
            + "UNIQUE(" + ApiAppsAccountsColumns.ACCOUNT_NAME + ", "
            + ApiAppsAccountsColumns.PACKAGE_NAME + "), "
            + "FOREIGN KEY(" + ApiAppsAccountsColumns.PACKAGE_NAME + ") REFERENCES "
            + Tables.API_APPS + "(" + ApiAppsColumns.PACKAGE_NAME + ") ON DELETE CASCADE)";

    KeychainDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);

        // make sure this is only done once, on the first instance!
        boolean iAmIt = false;
        synchronized(apgHack) {
            if(!apgHack) {
                iAmIt = true;
                apgHack = true;
            }
        }
        // if it's us, do the import
        if(iAmIt)
            checkAndImportApg(context);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.w(Constants.TAG, "Creating database...");

        db.execSQL(CREATE_KEYRINGS_PUBLIC);
        db.execSQL(CREATE_KEYRINGS_SECRET);
        db.execSQL(CREATE_KEYS);
        db.execSQL(CREATE_USER_IDS);
        db.execSQL(CREATE_CERTS);
        db.execSQL(CREATE_API_APPS);
        db.execSQL(CREATE_API_APPS_ACCOUNTS);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) {
            // Enable foreign key constraints
            db.execSQL("PRAGMA foreign_keys=ON;");
            // TODO remove, once we remove the "always migrate" debug stuff
            // db.execSQL("DROP TABLE user_ids;");
            db.execSQL(CREATE_USER_IDS);
            db.execSQL(CREATE_CERTS);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int old, int nu) {
        // don't care (this is version 1)
    }

    /** This method tries to import data from a provided database.
     *
     * The sole assumptions made on this db are that there is a key_rings table
     * with a key_ring_data, a master_key_id and a type column, the latter of
     * which should be 1 for secret keys and 0 for public keys.
     */
    public void checkAndImportApg(Context context) {

        boolean hasApgDb = false; {
            // It's the Java way =(
            String[] dbs = context.databaseList();
            for(String db : dbs) {
                if(db.equals("apg.db")) {
                    hasApgDb = true;
                    break;
                }
            }
        }

        if(!hasApgDb)
            return;

        Log.d(Constants.TAG, "apg.db exists! Importing...");

        SQLiteDatabase db = new SQLiteOpenHelper(context, "apg.db", null, 1) {
            @Override
            public void onCreate(SQLiteDatabase db) {
                // should never happen
                assert false;
            }
            @Override
            public void onDowngrade(SQLiteDatabase db, int old, int nu) {
                // don't care
            }
            @Override
            public void onUpgrade(SQLiteDatabase db, int old, int nu) {
                // don't care either
            }
        }.getReadableDatabase();

        // kill current!
        { // TODO don't kill current.
            Log.d(Constants.TAG, "Truncating db...");
            SQLiteDatabase d = getWritableDatabase();
            d.execSQL("DELETE FROM keyrings_public");
            d.close();
            Log.d(Constants.TAG, "Ok.");
        }

        Cursor c = null;
        try {
            // we insert in two steps: first, all public keys that have secret keys
            c = db.rawQuery("SELECT key_ring_data FROM key_rings WHERE type = 1 OR EXISTS ("
                    + " SELECT 1 FROM key_rings d2 WHERE key_rings.master_key_id = d2.master_key_id"
                    + " AND d2.type = 1) ORDER BY type ASC", null);
            Log.d(Constants.TAG, "Importing " + c.getCount() + " secret keyrings from apg.db...");
            for(int i = 0; i < c.getCount(); i++) {
                c.moveToPosition(i);
                byte[] data = c.getBlob(0);
                PGPKeyRing ring = PgpConversionHelper.BytesToPGPKeyRing(data);
                if(ring instanceof PGPPublicKeyRing)
                    ProviderHelper.saveKeyRing(context, (PGPPublicKeyRing) ring);
                else if(ring instanceof PGPSecretKeyRing)
                    ProviderHelper.saveKeyRing(context, (PGPSecretKeyRing) ring);
                else {
                    Log.e(Constants.TAG, "Unknown blob data type!");
                }
            }

            // afterwards, insert all keys, starting with public keys that have secret keys, then
            // secret keys, then all others. this order is necessary to ensure all certifications
            // are recognized properly.
            c = db.rawQuery("SELECT key_ring_data FROM key_rings ORDER BY (type = 0 AND EXISTS ("
                    + " SELECT 1 FROM key_rings d2 WHERE key_rings.master_key_id = d2.master_key_id AND"
                    + " d2.type = 1)) DESC, type DESC", null);
            // import from old database
            Log.d(Constants.TAG, "Importing " + c.getCount() + " keyrings from apg.db...");
            for(int i = 0; i < c.getCount(); i++) {
                c.moveToPosition(i);
                byte[] data = c.getBlob(0);
                PGPKeyRing ring = PgpConversionHelper.BytesToPGPKeyRing(data);
                if(ring instanceof PGPPublicKeyRing)
                    ProviderHelper.saveKeyRing(context, (PGPPublicKeyRing) ring);
                else if(ring instanceof PGPSecretKeyRing)
                    ProviderHelper.saveKeyRing(context, (PGPSecretKeyRing) ring);
                else {
                    Log.e(Constants.TAG, "Unknown blob data type!");
                }
            }

        } catch(IOException e) {
            Log.e(Constants.TAG, "Error importing apg db!", e);
            return;
        } finally {
            if(c != null)
                c.close();
            if(db != null)
                db.close();
        }

        // TODO delete old db, if we are sure this works
        // context.deleteDatabase("apg.db");
        Log.d(Constants.TAG, "All done, (not) deleting apg.db");


    }

}
