package com.fsosuke.personamusic.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.fsosuke.personamusic.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class AppUpdate(val version: String, val notes: String, val apkUrl: String, val pageUrl: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AppUpdate) : UpdateState
    data class Downloading(val update: AppUpdate, val progress: Float) : UpdateState
    data class Ready(val update: AppUpdate, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Checks the GitHub releases of this repo and installs newer APKs ("New mission"). */
class UpdateManager(
    private val context: Context,
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersion: String = BuildConfig.VERSION_NAME

    /** Automatic check, at most every 6 hours. */
    fun checkIfDue() {
        if (System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0) > CHECK_INTERVAL_MS) check(silent = true)
    }

    fun check(silent: Boolean = false) {
        val current = _state.value
        if (current is UpdateState.Checking || current is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        scope.launch {
            val result = runCatching { fetchLatest() }
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            _state.value = result.fold(
                onSuccess = { update ->
                    when {
                        update == null -> UpdateState.UpToDate
                        silent && update.version == prefs.getString(KEY_DISMISSED, null) -> UpdateState.Idle
                        else -> UpdateState.Available(update)
                    }
                },
                onFailure = { if (silent) UpdateState.Idle else UpdateState.Failed(it.localizedMessage ?: it.javaClass.simpleName) },
            )
        }
    }

    /** Hides the banner for this version until the user checks manually. */
    fun dismiss(update: AppUpdate) {
        prefs.edit().putString(KEY_DISMISSED, update.version).apply()
        _state.value = UpdateState.Idle
    }

    fun download(update: AppUpdate) {
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Downloading(update, 0f)
        scope.launch {
            _state.value = runCatching { downloadApk(update) }.fold(
                onSuccess = { UpdateState.Ready(update, it) },
                onFailure = { UpdateState.Failed(it.localizedMessage ?: it.javaClass.simpleName) },
            )
        }
    }

    /** Hands the APK to the system installer (asks for "install unknown apps" first if needed). */
    fun install(activityContext: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            activityContext.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        activityContext.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private suspend fun fetchLatest(): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) throw IOException("GitHub antwortet mit HTTP ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            val version = json.optString("tag_name").removePrefix("v")
            val assets = json.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0))
                .mapNotNull { assets?.optJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?.optString("browser_download_url")
                ?: return@withContext null
            if (!isNewer(version, currentVersion)) return@withContext null
            AppUpdate(version, json.optString("body"), apk, json.optString("html_url"))
        }
    }

    private suspend fun downloadApk(update: AppUpdate): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "persona-music-${update.version}.apk")
        client.newCall(Request.Builder().url(update.apkUrl).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download fehlgeschlagen (HTTP ${response.code})")
            val body = response.body ?: throw IOException("Leere Antwort")
            val total = body.contentLength().takeIf { it > 0 }
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buffer).also { read = it } >= 0) {
                        output.write(buffer, 0, read)
                        done += read
                        if (total != null) _state.value = UpdateState.Downloading(update, done.toFloat() / total)
                    }
                }
            }
        }
        target
    }

    companion object {
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_DISMISSED = "dismissed_version"
        private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

        /** Numeric comparison of dotted versions: 0.10.0 > 0.9.3. */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val b = current.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
