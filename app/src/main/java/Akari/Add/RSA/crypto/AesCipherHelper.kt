package Akari.Add.RSA.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object AesCipherHelper {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEYSTORE_ALIAS = "AddRsaMasterKey"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12

    /**
     * Decrypts AES-256-GCM ciphertext using the given raw 32-byte key and 12-byte IV.
     */
    fun decryptWithKey(ciphertext: ByteArray, keyBytes: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        return cipher.doFinal(ciphertext)
    }

    /**
     * Encrypts plaintext using the given raw 32-byte key. Generates a random 12-byte IV.
     */
    fun encryptWithKey(plaintext: ByteArray, keyBytes: ByteArray): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertext = cipher.doFinal(plaintext)
        return Pair(iv, ciphertext)
    }

    /**
     * Gets or creates an AES-256 Key in Android Keystore for encrypting custom stored RSA keys.
     */
    @Synchronized
    private fun getOrCreateKeystoreKey(context: Context): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                (keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
            } else {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (_: Exception) {
            // Fallback for environments where Android Keystore may be emulated or restricted
            getOrCreateFallbackKey(context)
        }
    }

    private fun getOrCreateFallbackKey(context: Context): SecretKey {
        val keyFile = File(context.filesDir, ".master_key")
        val keyBytes: ByteArray
        if (keyFile.exists() && keyFile.length() == 32L) {
            keyBytes = keyFile.readBytes()
        } else {
            keyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            keyFile.parentFile?.mkdirs()
            keyFile.writeBytes(keyBytes)
        }
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts data for internal storage using the device's Master AES-256 key.
     * Returns: 12 bytes IV + ciphertext (with GCM tag).
     */
    fun encryptStorageData(context: Context, plaintext: ByteArray): ByteArray {
        val secretKey = getOrCreateKeystoreKey(context)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        val result = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(ciphertext, 0, result, iv.size, ciphertext.size)
        return result
    }

    /**
     * Decrypts data from internal storage using the device's Master AES-256 key.
     */
    fun decryptStorageData(context: Context, encryptedBlob: ByteArray): ByteArray {
        require(encryptedBlob.size > IV_LENGTH) { "Invalid encrypted storage payload" }
        val iv = encryptedBlob.copyOfRange(0, IV_LENGTH)
        val ciphertext = encryptedBlob.copyOfRange(IV_LENGTH, encryptedBlob.size)

        val secretKey = getOrCreateKeystoreKey(context)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        return cipher.doFinal(ciphertext)
    }
}
