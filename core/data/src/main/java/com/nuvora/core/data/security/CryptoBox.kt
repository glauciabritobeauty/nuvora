package com.nuvora.core.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cofre para links, usuários e senhas das listas.
 *
 * Uma chave mestra fica no cofre do próprio Android (Android Keystore) e nunca sai de lá.
 * Ela protege uma segunda chave, usada para cifrar os dados (AES-GCM); assim dá para cifrar
 * milhares de links de uma lista grande rapidamente. Nada é gravado em texto puro nem vai para logs.
 */
class CryptoBox(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("cofre", Context.MODE_PRIVATE)
    private val random = SecureRandom()
    private val dataKey: SecretKey by lazy(::loadOrCreateDataKey)

    fun encrypt(text: String): String {
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, dataKey, GCMParameterSpec(TAG_BITS, iv)) }
        return encode(iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
    }

    fun decrypt(value: String): String {
        val bytes = decode(value)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, dataKey, GCMParameterSpec(TAG_BITS, bytes, 0, IV_SIZE))
        }
        return String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
    }

    private fun loadOrCreateDataKey(): SecretKey {
        val master = masterKey()
        prefs.getString(KEY_WRAPPED, null)?.let { wrapped ->
            val bytes = decode(wrapped)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, master, GCMParameterSpec(TAG_BITS, bytes, 0, IV_SIZE))
            }
            return SecretKeySpec(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), "AES")
        }
        val raw = ByteArray(32).also(random::nextBytes)
        // A chave do Keystore escolhe o próprio vetor inicial (mais seguro); ele vai junto do resultado.
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, master) }
        prefs.edit().putString(KEY_WRAPPED, encode(cipher.iv + cipher.doFinal(raw))).apply()
        return SecretKeySpec(raw, "AES")
    }

    private fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(MASTER_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(MASTER_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val MASTER_ALIAS = "nuvora_mestra"
        const val KEY_WRAPPED = "chave_dados"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
