/*
 * Copyright (C) 2012-2013 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.sufficientlysecure.keychain.pgp;

import android.content.Context;
import org.spongycastle.bcpg.CompressionAlgorithmTags;
import org.spongycastle.bcpg.HashAlgorithmTags;
import org.spongycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.spongycastle.bcpg.sig.KeyFlags;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.jce.spec.ElGamalParameterSpec;
import org.spongycastle.openpgp.*;
import org.spongycastle.openpgp.PGPUtil;
import org.spongycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.spongycastle.openpgp.operator.PBESecretKeyEncryptor;
import org.spongycastle.openpgp.operator.PGPContentSignerBuilder;
import org.spongycastle.openpgp.operator.PGPDigestCalculator;
import org.spongycastle.openpgp.operator.jcajce.*;
import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.Id;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.pgp.exception.PgpGeneralException;
import org.sufficientlysecure.keychain.provider.ProviderHelper;
import org.sufficientlysecure.keychain.util.IterableIterator;
import org.sufficientlysecure.keychain.util.Log;
import org.sufficientlysecure.keychain.util.Primes;
import org.sufficientlysecure.keychain.util.ProgressDialogUpdater;

import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;

public class PgpKeyOperation {
    private Context mContext;
    private ProgressDialogUpdater mProgress;

    private static final int[] PREFERRED_SYMMETRIC_ALGORITHMS = new int[]{
            SymmetricKeyAlgorithmTags.AES_256, SymmetricKeyAlgorithmTags.AES_192,
            SymmetricKeyAlgorithmTags.AES_128, SymmetricKeyAlgorithmTags.CAST5,
            SymmetricKeyAlgorithmTags.TRIPLE_DES};
    private static final int[] PREFERRED_HASH_ALGORITHMS = new int[]{HashAlgorithmTags.SHA1,
            HashAlgorithmTags.SHA256, HashAlgorithmTags.RIPEMD160};
    private static final int[] PREFERRED_COMPRESSION_ALGORITHMS = new int[]{
            CompressionAlgorithmTags.ZLIB, CompressionAlgorithmTags.BZIP2,
            CompressionAlgorithmTags.ZIP};

    public PgpKeyOperation(Context context, ProgressDialogUpdater progress) {
        super();
        this.mContext = context;
        this.mProgress = progress;
    }

    public void updateProgress(int message, int current, int total) {
        if (mProgress != null) {
            mProgress.setProgress(message, current, total);
        }
    }

    public void updateProgress(int current, int total) {
        if (mProgress != null) {
            mProgress.setProgress(current, total);
        }
    }

    /**
     * Creates new secret key.
     *
     * @param algorithmChoice
     * @param keySize
     * @param passphrase
     * @param isMasterKey
     * @return
     * @throws NoSuchAlgorithmException
     * @throws PGPException
     * @throws NoSuchProviderException
     * @throws PgpGeneralException
     * @throws InvalidAlgorithmParameterException
     */

    // TODO: key flags?
    public PGPSecretKey createKey(int algorithmChoice, int keySize, String passphrase,
                                  boolean isMasterKey)
            throws NoSuchAlgorithmException, PGPException, NoSuchProviderException,
                   PgpGeneralException, InvalidAlgorithmParameterException {

        if (keySize < 512) {
            throw new PgpGeneralException(mContext.getString(R.string.error_key_size_minimum512bit));
        }

        if (passphrase == null) {
            passphrase = "";
        }

        int algorithm = 0;
        KeyPairGenerator keyGen = null;

        switch (algorithmChoice) {
            case Id.choice.algorithm.dsa: {
                keyGen = KeyPairGenerator.getInstance("DSA", Constants.BOUNCY_CASTLE_PROVIDER_NAME);
                keyGen.initialize(keySize, new SecureRandom());
                algorithm = PGPPublicKey.DSA;
                break;
            }

            case Id.choice.algorithm.elgamal: {
                if (isMasterKey) {
                    throw new PgpGeneralException(
                            mContext.getString(R.string.error_master_key_must_not_be_el_gamal));
                }
                keyGen = KeyPairGenerator.getInstance("ElGamal", Constants.BOUNCY_CASTLE_PROVIDER_NAME);
                BigInteger p = Primes.getBestPrime(keySize);
                BigInteger g = new BigInteger("2");

                ElGamalParameterSpec elParams = new ElGamalParameterSpec(p, g);

                keyGen.initialize(elParams);
                algorithm = PGPPublicKey.ELGAMAL_ENCRYPT;
                break;
            }

            case Id.choice.algorithm.rsa: {
                keyGen = KeyPairGenerator.getInstance("RSA", Constants.BOUNCY_CASTLE_PROVIDER_NAME);
                keyGen.initialize(keySize, new SecureRandom());

                algorithm = PGPPublicKey.RSA_GENERAL;
                break;
            }

            default: {
                throw new PgpGeneralException(
                        mContext.getString(R.string.error_unknown_algorithm_choice));
            }
        }

        // build new key pair
        PGPKeyPair keyPair = new JcaPGPKeyPair(algorithm, keyGen.generateKeyPair(), new Date());

        // define hashing and signing algos
        PGPDigestCalculator sha1Calc = new JcaPGPDigestCalculatorProviderBuilder().build().get(
                HashAlgorithmTags.SHA1);

        // Build key encrypter and decrypter based on passphrase
        PBESecretKeyEncryptor keyEncryptor = new JcePBESecretKeyEncryptorBuilder(
                PGPEncryptedData.CAST5, sha1Calc)
                .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME).build(passphrase.toCharArray());

        PGPSecretKey secKey = new PGPSecretKey(keyPair.getPrivateKey(), keyPair.getPublicKey(),
                sha1Calc, isMasterKey, keyEncryptor);

        return secKey;
    }

    public void changeSecretKeyPassphrase(PGPSecretKeyRing keyRing, String oldPassPhrase,
                                          String newPassPhrase) throws IOException, PGPException,
            NoSuchProviderException {

        updateProgress(R.string.progress_building_key, 0, 100);
        if (oldPassPhrase == null) {
            oldPassPhrase = "";
        }
        if (newPassPhrase == null) {
            newPassPhrase = "";
        }

        PGPSecretKeyRing newKeyRing = PGPSecretKeyRing.copyWithNewPassword(
                keyRing,
                new JcePBESecretKeyDecryptorBuilder(new JcaPGPDigestCalculatorProviderBuilder()
                        .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME).build()).setProvider(
                        Constants.BOUNCY_CASTLE_PROVIDER_NAME).build(oldPassPhrase.toCharArray()),
                new JcePBESecretKeyEncryptorBuilder(keyRing.getSecretKey()
                        .getKeyEncryptionAlgorithm()).build(newPassPhrase.toCharArray()));

        updateProgress(R.string.progress_saving_key_ring, 50, 100);

        ProviderHelper.saveKeyRing(mContext, newKeyRing);

        updateProgress(R.string.progress_done, 100, 100);

    }

    public void buildSecretKey(ArrayList<String> userIds, ArrayList<PGPSecretKey> keys,
                               ArrayList<Integer> keysUsages, ArrayList<GregorianCalendar> keysExpiryDates,
                               PGPPublicKey oldPublicKey, String oldPassPhrase,
                               String newPassPhrase) throws PgpGeneralException, NoSuchProviderException,
            PGPException, NoSuchAlgorithmException, SignatureException, IOException {

        Log.d(Constants.TAG, "userIds: " + userIds.toString());

        updateProgress(R.string.progress_building_key, 0, 100);

        if (oldPassPhrase == null) {
            oldPassPhrase = "";
        }
        if (newPassPhrase == null) {
            newPassPhrase = "";
        }

        updateProgress(R.string.progress_preparing_master_key, 10, 100);

        // prepare keyring generator with given master public and secret key
        PGPKeyRingGenerator keyGen;
        PGPPublicKey masterPublicKey; {

            String mainUserId = userIds.get(0);

            // prepare the master key pair
            PGPKeyPair masterKeyPair; {

                PGPSecretKey masterKey = keys.get(0);

                // this removes all userIds and certifications previously attached to the masterPublicKey
                PGPPublicKey tmpKey = masterKey.getPublicKey();
                masterPublicKey = new PGPPublicKey(tmpKey.getAlgorithm(),
                        tmpKey.getKey(new BouncyCastleProvider()), tmpKey.getCreationTime());

                // already done by code above:
                // PGPPublicKey masterPublicKey = masterKey.getPublicKey();
                // // Somehow, the PGPPublicKey already has an empty certification attached to it when the
                // // keyRing is generated the first time, we remove that when it exists, before adding the
                // new
                // // ones
                // PGPPublicKey masterPublicKeyRmCert = PGPPublicKey.removeCertification(masterPublicKey,
                // "");
                // if (masterPublicKeyRmCert != null) {
                // masterPublicKey = masterPublicKeyRmCert;
                // }

                PBESecretKeyDecryptor keyDecryptor = new JcePBESecretKeyDecryptorBuilder().setProvider(
                        Constants.BOUNCY_CASTLE_PROVIDER_NAME).build(oldPassPhrase.toCharArray());
                PGPPrivateKey masterPrivateKey = masterKey.extractPrivateKey(keyDecryptor);

                updateProgress(R.string.progress_certifying_master_key, 20, 100);

                // re-add old certificates, or create new ones for new uids
                for (String userId : userIds) {
                    // re-add certs for this uid, take a note if self-signed cert is in there
                    boolean foundSelfSign = false;
                    Iterator<PGPSignature> it = tmpKey.getSignaturesForID(userId);
                    if(it != null) for(PGPSignature sig : new IterableIterator<PGPSignature>(it)) {
                        if(sig.getKeyID() == masterPublicKey.getKeyID()) {
                            // already have a self sign? skip this other one, then.
                            // note: PGPKeyRingGenerator adds one cert for the main user id, which
                            // will lead to duplicates. unfortunately, if we add any other here
                            // first, that will change the main user id order...
                            if(foundSelfSign)
                                continue;
                            foundSelfSign = true;
                        }
                        Log.d(Constants.TAG, "adding old sig for " + userId + " from "
                                + PgpKeyHelper.convertKeyIdToHex(sig.getKeyID()));
                        masterPublicKey = PGPPublicKey.addCertification(masterPublicKey, userId, sig);
                    }

                    // there was an old self-signed certificate for this uid
                    if(foundSelfSign)
                        continue;

                    Log.d(Constants.TAG, "generating self-signed cert for " + userId);

                    PGPContentSignerBuilder signerBuilder = new JcaPGPContentSignerBuilder(
                            masterPublicKey.getAlgorithm(), HashAlgorithmTags.SHA1)
                            .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME);
                    PGPSignatureGenerator sGen = new PGPSignatureGenerator(signerBuilder);

                    sGen.init(PGPSignature.POSITIVE_CERTIFICATION, masterPrivateKey);

                    PGPSignature certification = sGen.generateCertification(userId, masterPublicKey);

                    masterPublicKey = PGPPublicKey.addCertification(masterPublicKey, userId, certification);
                }

                masterKeyPair = new PGPKeyPair(masterPublicKey, masterPrivateKey);
            }

            PGPSignatureSubpacketGenerator hashedPacketsGen;
            PGPSignatureSubpacketGenerator unhashedPacketsGen; {

                hashedPacketsGen = new PGPSignatureSubpacketGenerator();
                unhashedPacketsGen = new PGPSignatureSubpacketGenerator();

                int usageId = keysUsages.get(0);
                boolean canEncrypt =
                        (usageId == Id.choice.usage.encrypt_only || usageId == Id.choice.usage.sign_and_encrypt);

                int keyFlags = KeyFlags.CERTIFY_OTHER | KeyFlags.SIGN_DATA;
                if (canEncrypt) {
                    keyFlags |= KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE;
                }
                hashedPacketsGen.setKeyFlags(true, keyFlags);

                hashedPacketsGen.setPreferredSymmetricAlgorithms(true, PREFERRED_SYMMETRIC_ALGORITHMS);
                hashedPacketsGen.setPreferredHashAlgorithms(true, PREFERRED_HASH_ALGORITHMS);
                hashedPacketsGen.setPreferredCompressionAlgorithms(true, PREFERRED_COMPRESSION_ALGORITHMS);

                if (keysExpiryDates.get(0) != null) {
                    GregorianCalendar creationDate = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
                    creationDate.setTime(masterPublicKey.getCreationTime());
                    GregorianCalendar expiryDate = keysExpiryDates.get(0);
                    //note that the below, (a/c) - (b/c) is *not* the same as (a - b) /c
                    //here we purposefully ignore partial days in each date - long type has no fractional part!
                    long numDays =
                            (expiryDate.getTimeInMillis() / 86400000) - (creationDate.getTimeInMillis() / 86400000);
                    if (numDays <= 0) {
                        throw new PgpGeneralException(
                                mContext.getString(R.string.error_expiry_must_come_after_creation));
                    }
                    hashedPacketsGen.setKeyExpirationTime(false, numDays * 86400);
                } else {
                    //do this explicitly, although since we're rebuilding,
                    hashedPacketsGen.setKeyExpirationTime(false, 0);
                    //this happens anyway
                }
            }

            updateProgress(R.string.progress_building_master_key, 30, 100);

            // define hashing and signing algos
            PGPDigestCalculator sha1Calc = new JcaPGPDigestCalculatorProviderBuilder().build().get(
                    HashAlgorithmTags.SHA1);
            PGPContentSignerBuilder certificationSignerBuilder = new JcaPGPContentSignerBuilder(
                    masterKeyPair.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA1);

            // Build key encrypter based on passphrase
            PBESecretKeyEncryptor keyEncryptor = new JcePBESecretKeyEncryptorBuilder(
                    PGPEncryptedData.CAST5, sha1Calc)
                    .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME).build(
                            newPassPhrase.toCharArray());

            keyGen = new PGPKeyRingGenerator(PGPSignature.POSITIVE_CERTIFICATION,
                    masterKeyPair, mainUserId, sha1Calc, hashedPacketsGen.generate(),
                    unhashedPacketsGen.generate(), certificationSignerBuilder, keyEncryptor);

        }

        updateProgress(R.string.progress_adding_sub_keys, 40, 100);

        for (int i = 1; i < keys.size(); ++i) {
            updateProgress(40 + 40 * (i - 1) / (keys.size() - 1), 100);

            PGPSecretKey subKey = keys.get(i);
            PGPPublicKey subPublicKey = subKey.getPublicKey();

            PBESecretKeyDecryptor keyDecryptor = new JcePBESecretKeyDecryptorBuilder()
                    .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME).build(
                            oldPassPhrase.toCharArray());
            PGPPrivateKey subPrivateKey = subKey.extractPrivateKey(keyDecryptor);

            // TODO: now used without algorithm and creation time?! (APG 1)
            PGPKeyPair subKeyPair = new PGPKeyPair(subPublicKey, subPrivateKey);

            PGPSignatureSubpacketGenerator hashedPacketsGen = new PGPSignatureSubpacketGenerator();
            PGPSignatureSubpacketGenerator unhashedPacketsGen = new PGPSignatureSubpacketGenerator();

            int keyFlags = 0;

            int usageId = keysUsages.get(i);
            boolean canSign =
                    (usageId == Id.choice.usage.sign_only || usageId == Id.choice.usage.sign_and_encrypt);
            boolean canEncrypt =
                    (usageId == Id.choice.usage.encrypt_only || usageId == Id.choice.usage.sign_and_encrypt);
            if (canSign) {
                Date todayDate = new Date(); //both sig times the same
                keyFlags |= KeyFlags.SIGN_DATA;
                // cross-certify signing keys
                hashedPacketsGen.setSignatureCreationTime(false, todayDate); //set outer creation time
                PGPSignatureSubpacketGenerator subHashedPacketsGen = new PGPSignatureSubpacketGenerator();
                subHashedPacketsGen.setSignatureCreationTime(false, todayDate); //set inner creation time
                PGPContentSignerBuilder signerBuilder = new JcaPGPContentSignerBuilder(
                        subPublicKey.getAlgorithm(), PGPUtil.SHA1)
                        .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME);
                PGPSignatureGenerator sGen = new PGPSignatureGenerator(signerBuilder);
                sGen.init(PGPSignature.PRIMARYKEY_BINDING, subPrivateKey);
                sGen.setHashedSubpackets(subHashedPacketsGen.generate());
                PGPSignature certification = sGen.generateCertification(masterPublicKey,
                        subPublicKey);
                unhashedPacketsGen.setEmbeddedSignature(false, certification);
            }
            if (canEncrypt) {
                keyFlags |= KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE;
            }
            hashedPacketsGen.setKeyFlags(false, keyFlags);

            if (keysExpiryDates.get(i) != null) {
                GregorianCalendar creationDate = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
                creationDate.setTime(subPublicKey.getCreationTime());
                GregorianCalendar expiryDate = keysExpiryDates.get(i);
                //note that the below, (a/c) - (b/c) is *not* the same as (a - b) /c
                //here we purposefully ignore partial days in each date - long type has no fractional part!
                long numDays =
                        (expiryDate.getTimeInMillis() / 86400000) - (creationDate.getTimeInMillis() / 86400000);
                if (numDays <= 0) {
                    throw new PgpGeneralException
                            (mContext.getString(R.string.error_expiry_must_come_after_creation));
                }
                hashedPacketsGen.setKeyExpirationTime(false, numDays * 86400);
            } else {
                //do this explicitly, although since we're rebuilding,
                hashedPacketsGen.setKeyExpirationTime(false, 0);
                //this happens anyway
            }

            keyGen.addSubKey(subKeyPair, hashedPacketsGen.generate(), unhashedPacketsGen.generate());
        }

        PGPSecretKeyRing secretKeyRing = keyGen.generateSecretKeyRing();
        PGPPublicKeyRing publicKeyRing = keyGen.generatePublicKeyRing();

        updateProgress(R.string.progress_re_adding_certs, 80, 100);

        // re-add certificates from old public key
        // TODO: this only takes care of user id certificates, what about others?
        PGPPublicKey pubkey = publicKeyRing.getPublicKey();
        for(String uid : new IterableIterator<String>(pubkey.getUserIDs())) {
            for(PGPSignature sig : new IterableIterator<PGPSignature>(oldPublicKey.getSignaturesForID(uid), true)) {
                // but skip self certificates
                if(sig.getKeyID() == pubkey.getKeyID())
                    continue;
                pubkey = PGPPublicKey.addCertification(pubkey, uid, sig);
            }
        }
        publicKeyRing = PGPPublicKeyRing.insertPublicKey(publicKeyRing, pubkey);

        updateProgress(R.string.progress_saving_key_ring, 90, 100);

        /* additional handy debug info
        Log.d(Constants.TAG, " ------- in private key -------");
        for(String uid : new IterableIterator<String>(secretKeyRing.getPublicKey().getUserIDs())) {
            for(PGPSignature sig : new IterableIterator<PGPSignature>(secretKeyRing.getPublicKey().getSignaturesForID(uid))) {
                Log.d(Constants.TAG, "sig: " + PgpKeyHelper.convertKeyIdToHex(sig.getKeyID()) + " for " + uid);
            }
        }
        Log.d(Constants.TAG, " ------- in public key -------");
        for(String uid : new IterableIterator<String>(publicKeyRing.getPublicKey().getUserIDs())) {
            for(PGPSignature sig : new IterableIterator<PGPSignature>(publicKeyRing.getPublicKey().getSignaturesForID(uid))) {
                Log.d(Constants.TAG, "sig: " + PgpKeyHelper.convertKeyIdToHex(sig.getKeyID()) + " for " + uid);
            }
        }
        */

        ProviderHelper.saveKeyRing(mContext, secretKeyRing);
        ProviderHelper.saveKeyRing(mContext, publicKeyRing);

        updateProgress(R.string.progress_done, 100, 100);
    }

    /**
     * Certify the given pubkeyid with the given masterkeyid.
     *
     * @param masterKeyId Certifying key, must be available as secret key
     * @param pubKeyId ID of public key to certify
     * @param userIds User IDs to certify, must not be null or empty
     * @param passphrase Passphrase of the secret key
     * @return A keyring with added certifications
     */
    public PGPPublicKeyRing certifyKey(long masterKeyId, long pubKeyId, List<String> userIds, String passphrase)
            throws PgpGeneralException, NoSuchAlgorithmException, NoSuchProviderException,
            PGPException, SignatureException {
        if (passphrase == null) {
            throw new PgpGeneralException("Unable to obtain passphrase");
        } else {

            // create a signatureGenerator from the supplied masterKeyId and passphrase
            PGPSignatureGenerator signatureGenerator; {

                PGPSecretKey certificationKey = PgpKeyHelper.getCertificationKey(mContext, masterKeyId);
                if (certificationKey == null) {
                    throw new PgpGeneralException(mContext.getString(R.string.error_signature_failed));
                }

                PBESecretKeyDecryptor keyDecryptor = new JcePBESecretKeyDecryptorBuilder().setProvider(
                        Constants.BOUNCY_CASTLE_PROVIDER_NAME).build(passphrase.toCharArray());
                PGPPrivateKey signaturePrivateKey = certificationKey.extractPrivateKey(keyDecryptor);
                if (signaturePrivateKey == null) {
                    throw new PgpGeneralException(
                            mContext.getString(R.string.error_could_not_extract_private_key));
                }

                // TODO: SHA256 fixed?
                JcaPGPContentSignerBuilder contentSignerBuilder = new JcaPGPContentSignerBuilder(
                        certificationKey.getPublicKey().getAlgorithm(), PGPUtil.SHA256)
                        .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME);

                signatureGenerator = new PGPSignatureGenerator(contentSignerBuilder);
                signatureGenerator.init(PGPSignature.DEFAULT_CERTIFICATION, signaturePrivateKey);
            }

            { // supply signatureGenerator with a SubpacketVector
                PGPSignatureSubpacketGenerator spGen = new PGPSignatureSubpacketGenerator();
                PGPSignatureSubpacketVector packetVector = spGen.generate();
                signatureGenerator.setHashedSubpackets(packetVector);
            }

            // fetch public key ring, add the certification and return it
            PGPPublicKeyRing pubring = ProviderHelper
                    .getPGPPublicKeyRingByKeyId(mContext, pubKeyId);
            PGPPublicKey signedKey = pubring.getPublicKey(pubKeyId);
            for(String userId : new IterableIterator<String>(userIds.iterator())) {
                PGPSignature sig = signatureGenerator.generateCertification(userId, signedKey);
                signedKey = PGPPublicKey.addCertification(signedKey, userId, sig);
            }
            pubring = PGPPublicKeyRing.insertPublicKey(pubring, signedKey);

            return pubring;
        }
    }
}
