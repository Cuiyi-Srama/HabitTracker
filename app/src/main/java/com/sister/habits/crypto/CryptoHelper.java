package com.sister.habits.crypto;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM 加密工具
 * 复用 PasswordNotebook CryptoHelper 的架构经验
 * 用于端到端加密数据上云：云端只存密文，设备端解密
 */
public class CryptoHelper {
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "sister_habits_key";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;  // bits
    private static final int IV_LENGTH = 12;         // bytes

    private SecretKey secretKey;
    private static CryptoHelper instance;

    private CryptoHelper() {
        initKey();
    }

    public static synchronized CryptoHelper getInstance() {
        if (instance == null) {
            instance = new CryptoHelper();
        }
        return instance;
    }

    private void initKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
            keyStore.load(null);

            if (keyStore.containsAlias(KEY_ALIAS)) {
                secretKey = ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
            } else {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
                keyGenerator.init(new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build());
                secretKey = keyGenerator.generateKey();
            }
        } catch (Exception e) {
            // Fallback: 如果 AndroidKeyStore 不可用，使用软件密钥
            secretKey = new SecretKeySpec(
                    "fallback_key_32bytes_passphrase!!".getBytes(StandardCharsets.UTF_8),
                    "AES"
            );
        }
    }

    /**
     * 加密数据
     * @param plaintext 明文
     * @return Base64(IV + 密文) —> 云端只存这个字符串
     */
    public String encrypt(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 拼接 IV + 密文
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * 解密数据
     * @param encrypted Base64(IV + 密文)
     * @return 明文
     */
    public String decrypt(String encrypted) {
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);

            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * 导出密钥 base64 字符串（用于通过局域网/QR码分享给另一台设备）
     */
    public String exportKeyBase64() {
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }

    /**
     * 从另一台设备导入密钥
     */
    public void importKeyBase64(String keyBase64) {
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }
}