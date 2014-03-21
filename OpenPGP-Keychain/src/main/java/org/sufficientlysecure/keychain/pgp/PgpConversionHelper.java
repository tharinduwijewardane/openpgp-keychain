/*
 * Copyright (C) 2012-2013 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.sufficientlysecure.keychain.pgp;

import org.spongycastle.openpgp.PGPKeyRing;
import org.spongycastle.openpgp.PGPObjectFactory;
import org.spongycastle.openpgp.PGPSecretKey;
import org.spongycastle.openpgp.PGPSecretKeyRing;
import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;


public class PgpConversionHelper {

    /**
     * Convert from byte[] to PGPKeyRing
     *
     * @param keysBytes
     * @return
     */
    public static PGPKeyRing BytesToPGPKeyRing(byte[] keysBytes) {
        PGPObjectFactory factory = new PGPObjectFactory(keysBytes);
        PGPKeyRing keyRing = null;
        try {
            if ((keyRing = (PGPKeyRing) factory.nextObject()) == null) {
                Log.e(Constants.TAG, "No keys given!");
            }
        } catch (IOException e) {
            Log.e(Constants.TAG, "Error while converting to PGPKeyRing!", e);
        }

        return keyRing;
    }

    /**
     * Convert from byte[] to ArrayList<PGPSecretKey>
     *
     * @param keysBytes
     * @return
     */
    public static ArrayList<PGPSecretKey> BytesToPGPSecretKeyList(byte[] keysBytes) {
        PGPSecretKeyRing keyRing = (PGPSecretKeyRing) BytesToPGPKeyRing(keysBytes);
        ArrayList<PGPSecretKey> keys = new ArrayList<PGPSecretKey>();

        @SuppressWarnings("unchecked")
        Iterator<PGPSecretKey> itr = keyRing.getSecretKeys();
        while (itr.hasNext()) {
            keys.add(itr.next());
        }

        return keys;
    }

    /**
     * Convert from byte[] to PGPSecretKey
     * <p/>
     * Singles keys are encoded as keyRings with one single key in it by Bouncy Castle
     *
     * @param keyBytes
     * @return
     */
    public static PGPSecretKey BytesToPGPSecretKey(byte[] keyBytes) {
        PGPObjectFactory factory = new PGPObjectFactory(keyBytes);
        Object obj = null;
        try {
            obj = factory.nextObject();
        } catch (IOException e) {
            Log.e(Constants.TAG, "Error while converting to PGPSecretKey!", e);
        }
        PGPSecretKey secKey = null;
        if (obj instanceof PGPSecretKey) {
            if ((secKey = (PGPSecretKey) obj) == null) {
                Log.e(Constants.TAG, "No keys given!");
            }
        } else if (obj instanceof PGPSecretKeyRing) { //master keys are sent as keyrings
            PGPSecretKeyRing keyRing = null;
            if ((keyRing = (PGPSecretKeyRing) obj) == null) {
                Log.e(Constants.TAG, "No keys given!");
            }
            secKey = keyRing.getSecretKey();
        }

        return secKey;
    }

    /**
     * Convert from ArrayList<PGPSecretKey> to byte[]
     *
     * @param keys
     * @return
     */
    public static byte[] PGPSecretKeyArrayListToBytes(ArrayList<PGPSecretKey> keys) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        for (PGPSecretKey key : keys) {
            try {
                key.encode(os);
            } catch (IOException e) {
                Log.e(Constants.TAG, "Error while converting ArrayList<PGPSecretKey> to byte[]!", e);
            }
        }

        return os.toByteArray();
    }

    /**
     * Convert from PGPSecretKey to byte[]
     *
     * @param key
     * @return
     */
    public static byte[] PGPSecretKeyToBytes(PGPSecretKey key) {
        try {
            return key.getEncoded();
        } catch (IOException e) {
            Log.e(Constants.TAG, "Encoding failed", e);

            return null;
        }
    }

    /**
     * Convert from PGPSecretKeyRing to byte[]
     *
     * @param keyRing
     * @return
     */
    public static byte[] PGPSecretKeyRingToBytes(PGPSecretKeyRing keyRing) {
        try {
            return keyRing.getEncoded();
        } catch (IOException e) {
            Log.e(Constants.TAG, "Encoding failed", e);

            return null;
        }
    }

}
