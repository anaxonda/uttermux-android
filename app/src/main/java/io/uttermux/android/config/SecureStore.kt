package io.uttermux.android.config

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.concurrent.ConcurrentHashMap

class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("secure", Context.MODE_PRIVATE)
    private val alias = "uttermux-provider-keys"
    private val values = ConcurrentHashMap<String,String>()
    @Volatile private var cachedKey:SecretKey?=null
    private fun key(): SecretKey {
        cachedKey?.let { return it }
        return synchronized(this) {
            cachedKey ?: run {
                val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                val resolved = (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                    init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
                }.generateKey()
                cachedKey = resolved
                resolved
            }
        }
    }
    fun put(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
        prefs.edit().putString(name, encoded).apply();values[name]=value
    }
    fun get(name: String): String {
        values[name]?.let { return it }
        val raw = prefs.getString(name, null) ?: return "".also { values[name] = it }
        return runCatching {
            val all = Base64.decode(raw, Base64.NO_WRAP); val iv = all.copyOfRange(0, 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
            String(cipher.doFinal(all.copyOfRange(12, all.size)))
        }.getOrDefault("").also { values[name] = it }
    }
}
