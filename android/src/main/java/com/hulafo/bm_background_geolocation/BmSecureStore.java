package com.hulafo.bm_background_geolocation;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AES-GCM encryption with a non-exportable key kept in the Android Keystore. */
final class BmSecureStore {

    private static final String ALIAS = "bm_geolocation_secret_key";
    private static final String PROVIDER = "AndroidKeyStore";
    private static final int IV_LEN = 12;

    private BmSecureStore() {}

    private static synchronized SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(PROVIDER);
        ks.load(null);
        if (ks.containsAlias(ALIAS)) {
            return (SecretKey) ks.getKey(ALIAS, null);
        }
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER);
        kg.init(
            new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        );
        return kg.generateKey();
    }

    /** @return base64(iv || ciphertext), or null if encryption is impossible (never falls back to plaintext). */
    static String encrypt(String plain) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = c.getIV();
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    /** @return plaintext, or null if the data cannot be decrypted (e.g. restored from a backup onto another device). */
    static String decrypt(String encoded) {
        try {
            byte[] in = Base64.decode(encoded, Base64.NO_WRAP);
            if (in.length <= IV_LEN) return null;
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, in, 0, IV_LEN));
            return new String(c.doFinal(in, IV_LEN, in.length - IV_LEN), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
