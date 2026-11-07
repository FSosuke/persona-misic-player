package com.fsosuke.personamusic.youtube

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class YtAccount(val name: String, val handle: String?, val photoUrl: String?)

/**
 * Keeps the YouTube Music login. The cookie grants full account access, so it is encrypted with a
 * key that never leaves the Android Keystore and is only ever sent to music.youtube.com.
 */
class YtAccountManager(context: Context, private val innerTube: InnerTube) {

    private val prefs = context.getSharedPreferences("yt_account", Context.MODE_PRIVATE)
    private val box = SecretBox("persona_yt_cookie")

    @Volatile
    var cookie: String? = prefs.getString(KEY_COOKIE, null)?.let { runCatching { box.decrypt(it) }.getOrNull() }
        private set

    private val _account = MutableStateFlow(
        if (cookie == null) null else prefs.getString(KEY_NAME, null)?.let {
            YtAccount(it, prefs.getString(KEY_HANDLE, null), prefs.getString(KEY_PHOTO, null))
        }
    )
    val account: StateFlow<YtAccount?> = _account.asStateFlow()

    /** Validates [cookie] against the account menu and stores it on success. */
    suspend fun signIn(cookie: String): YtAccount {
        if (!InnerTube.hasAuthCookie(cookie)) throw IOException("No login cookie (SAPISID) found")
        val menu = innerTube.post("account/account_menu", cookie, JSONObject())
        val header = findFirst(menu, "activeAccountHeaderRenderer")
            ?: throw IOException("Login not recognised – please sign in again")
        val account = YtAccount(
            name = header.obj("accountName").runsText() ?: "YouTube Music",
            handle = header.obj("channelHandle").runsText(),
            photoUrl = bestThumbnail(header.arr("accountPhoto", "thumbnails")),
        )
        prefs.edit()
            .putString(KEY_COOKIE, box.encrypt(cookie))
            .putString(KEY_NAME, account.name)
            .putString(KEY_HANDLE, account.handle)
            .putString(KEY_PHOTO, account.photoUrl)
            .apply()
        this.cookie = cookie
        _account.value = account
        return account
    }

    fun signOut() {
        prefs.edit().clear().apply()
        cookie = null
        _account.value = null
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }

    private companion object {
        const val KEY_COOKIE = "cookie"
        const val KEY_NAME = "name"
        const val KEY_HANDLE = "handle"
        const val KEY_PHOTO = "photo"
    }
}

/** AES-GCM with a non-exportable Android Keystore key. */
internal class SecretBox(private val alias: String) {

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.doFinal(plain.toByteArray())
        return encode(cipher.iv) + ":" + encode(data)
    }

    fun decrypt(stored: String): String {
        val (iv, data) = stored.split(":").let { decode(it[0]) to decode(it[1]) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(data))
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)
}
