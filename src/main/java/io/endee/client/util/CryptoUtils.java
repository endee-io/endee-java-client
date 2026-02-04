package io.endee.client.util;

import io.endee.client.exception.EndeeException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Cryptographic utilities for Endee-DB.
 * Provides compression/decompression and AES encryption/decryption for metadata.
 */
public final class CryptoUtils {

    private static final int AES_KEY_SIZE = 32; // 256 bits
    private static final int IV_SIZE = 16; // 128 bits
    private static final int BLOCK_SIZE = 16;

    private CryptoUtils() {}

    /**
     * Gets checksum from key by converting last two hex characters to integer.
     *
     * @param key the key string
     * @return checksum value or -1 if key is null
     */
    public static int getChecksum(String key) {
        if (key == null || key.length() < 2) {
            return -1;
        }
        try {
            String lastTwo = key.substring(key.length() - 2);
            return Integer.parseInt(lastTwo, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Compresses a map to deflated JSON bytes, optionally encrypting with AES.
     *
     * @param data the map to compress
     * @param key  optional hex key for AES encryption (64 hex chars = 256 bits)
     * @return compressed (and optionally encrypted) bytes
     */
    public static byte[] jsonZip(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return new byte[0];
        }
        try {
            String json = JsonUtils.toJson(data);
            byte[] compressed = deflateCompress(json.getBytes(StandardCharsets.UTF_8));

            return compressed;
        } catch (Exception e) {
            throw new EndeeException("Failed to compress metadata", e);
        }
    }

    /**
     * Decompresses deflated JSON bytes to a map.
     *
     * @param data the compressed bytes
     * @return the decompressed map
     */
    public static Map<String, Object> jsonUnzip(byte[] data) {
        return jsonUnzip(data, null);
    }

    /**
     * Decompresses deflated JSON bytes to a map, optionally decrypting with AES.
     *
     * @param data the compressed (and optionally encrypted) bytes
     * @param key  optional hex key for AES decryption (64 hex chars = 256 bits)
     * @return the decompressed map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> jsonUnzip(byte[] data, String key) {
        if (data == null || data.length == 0) {
            return Map.of();
        }
        try {
            byte[] buffer = data;

            // If key is provided, decrypt first
            if (key != null && !key.isEmpty()) {
                buffer = aesDecrypt(buffer, key);
            }

            byte[] decompressed = deflateDecompress(buffer);
            String json = new String(decompressed, StandardCharsets.UTF_8);
            return JsonUtils.fromJson(json, Map.class);
        } catch (Exception e) {
            // Return empty map on failure (matches TypeScript behavior)
            return Map.of();
        }
    }

    /**
     * Encrypts data using AES-256-CBC.
     *
     * @param data   the data to encrypt
     * @param keyHex a 256-bit hex key (64 hex characters)
     * @return IV + ciphertext (IV is prepended to the ciphertext)
     */
    public static byte[] aesEncrypt(byte[] data, String keyHex) {
        try {
            byte[] key = hexToBytes(keyHex);

            if (key.length != AES_KEY_SIZE) {
                throw new IllegalArgumentException("Key must be 256 bits (64 hex characters)");
            }

            // Generate random IV
            byte[] iv = new byte[IV_SIZE];
            new SecureRandom().nextBytes(iv);

            // Pad data with PKCS7
            byte[] paddedData = pkcs7Pad(data);

            // Create cipher and encrypt
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

            byte[] ciphertext = cipher.doFinal(paddedData);

            // Return IV + ciphertext
            byte[] result = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);

            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new EndeeException("AES encryption failed", e);
        }
    }

    /**
     * Decrypts data using AES-256-CBC.
     *
     * @param data   the encrypted data (IV + ciphertext)
     * @param keyHex a 256-bit hex key (64 hex characters)
     * @return the decrypted data
     */
    public static byte[] aesDecrypt(byte[] data, String keyHex) {
        try {
            byte[] key = hexToBytes(keyHex);

            if (key.length != AES_KEY_SIZE) {
                throw new IllegalArgumentException("Key must be 256 bits (64 hex characters)");
            }

            if (data.length < IV_SIZE) {
                throw new IllegalArgumentException("Data too short to contain IV");
            }

            // Extract IV and ciphertext
            byte[] iv = Arrays.copyOfRange(data, 0, IV_SIZE);
            byte[] ciphertext = Arrays.copyOfRange(data, IV_SIZE, data.length);

            // Create cipher and decrypt
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            byte[] paddedData = cipher.doFinal(ciphertext);

            // Remove PKCS7 padding
            return pkcs7Unpad(paddedData);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new EndeeException("AES decryption failed", e);
        }
    }

    /**
     * Compresses data using DEFLATE algorithm (raw, no headers).
     */
    private static byte[] deflateCompress(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();

        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
        byte[] buffer = new byte[1024];

        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            bos.write(buffer, 0, count);
        }

        deflater.end();
        return bos.toByteArray();
    }

    /**
     * Decompresses DEFLATE compressed data.
     */
    private static byte[] deflateDecompress(byte[] data) throws Exception {
        Inflater inflater = new Inflater();
        inflater.setInput(data);

        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
        byte[] buffer = new byte[1024];

        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            if (count == 0 && inflater.needsInput()) {
                break;
            }
            bos.write(buffer, 0, count);
        }

        inflater.end();
        return bos.toByteArray();
    }

    /**
     * Adds PKCS7 padding to data.
     */
    private static byte[] pkcs7Pad(byte[] data) {
        int paddingLength = BLOCK_SIZE - (data.length % BLOCK_SIZE);
        byte[] padded = new byte[data.length + paddingLength];
        System.arraycopy(data, 0, padded, 0, data.length);
        Arrays.fill(padded, data.length, padded.length, (byte) paddingLength);
        return padded;
    }

    /**
     * Removes PKCS7 padding from data.
     */
    private static byte[] pkcs7Unpad(byte[] data) {
        if (data.length == 0) {
            return data;
        }
        int paddingLength = data[data.length - 1] & 0xFF;
        if (paddingLength > BLOCK_SIZE || paddingLength > data.length) {
            return data; // Invalid padding, return as-is
        }
        return Arrays.copyOfRange(data, 0, data.length - paddingLength);
    }

    /**
     * Converts a hex string to byte array.
     */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
