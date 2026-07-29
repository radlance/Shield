package com.github.radlance.shield.subscription.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

interface SecretCipher {
    fun encrypt(value: String): String
    fun decrypt(value: String): String
}

class AndroidSecretCipher : SecretCipher {
    private val key: SecretKey
        get() {
            val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
                generateKey()
            }
        }

    @OptIn(ExperimentalEncodingApi::class)
    override fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val payload = cipher.iv + cipher.doFinal(value.toByteArray())
        return Base64.encode(payload)
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun decrypt(value: String): String {
        val payload = Base64.decode(value)
        require(payload.size > IV_LENGTH)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, payload.copyOfRange(0, IV_LENGTH)))
        return cipher.doFinal(payload.copyOfRange(IV_LENGTH, payload.size)).decodeToString()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "shield.subscription.storage.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }
}
