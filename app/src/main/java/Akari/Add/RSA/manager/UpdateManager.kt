package Akari.Add.RSA.manager

import Akari.Add.RSA.BuildConfig
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val channel: String,
    val newVersion: String,
    val currentVersion: String,
    val title: String,
    val description: String,
    val downloadUrl: String,
    val pageUrl: String
)

sealed class UpdateCheckResult {
    object Idle : UpdateCheckResult()
    object Checking : UpdateCheckResult()
    data class UpToDate(val currentVersion: String, val channel: String) : UpdateCheckResult()
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    data class Error(val errorMsg: String) : UpdateCheckResult()
}

object UpdateManager {
    const val CHANNEL_RELEASE = "release"
    const val CHANNEL_ACTION = "action"

    private const val PREFS_NAME = "add_rsa_update_prefs"
    private const val KEY_CHANNEL = "update_channel"
    private const val GITHUB_REPO = "Akiyama-25/Add-RSA"

    fun getSelectedChannel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultChannel = if (BuildConfig.VERSION_NAME.startsWith("Action-")) {
            CHANNEL_ACTION
        } else {
            CHANNEL_RELEASE
        }
        return prefs.getString(KEY_CHANNEL, defaultChannel) ?: defaultChannel
    }

    fun setSelectedChannel(context: Context, channel: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CHANNEL, channel).apply()
    }

    suspend fun checkUpdate(channel: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            if (channel == CHANNEL_RELEASE) {
                checkReleaseUpdate()
            } else {
                checkActionUpdate()
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.localizedMessage ?: "Failed to check update")
        }
    }

    private fun checkReleaseUpdate(): UpdateCheckResult {
        val apiUrl = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
        val response = getJson(apiUrl) ?: return UpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME, CHANNEL_RELEASE)

        val tagName = response.optString("tag_name", "").trim()
        if (tagName.isBlank()) {
            return UpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME, CHANNEL_RELEASE)
        }

        val currentVersion = BuildConfig.VERSION_NAME
        val builtReleaseTag = BuildConfig.BUILT_LATEST_RELEASE_TAG

        // If current build is Action, compare remote release tag with the release tag cached at Action build time
        val isNewer = if (currentVersion.startsWith("Action-")) {
            tagName.isNotEmpty() && !tagName.equals(builtReleaseTag, ignoreCase = true)
        } else {
            tagName.isNotEmpty() && !tagName.equals(currentVersion, ignoreCase = true)
        }

        if (!isNewer) {
            return UpdateCheckResult.UpToDate(currentVersion, CHANNEL_RELEASE)
        }

        val title = response.optString("name", tagName)
        val body = response.optString("body", "")
        val htmlUrl = response.optString("html_url", "https://github.com/$GITHUB_REPO/releases")

        var downloadUrl = htmlUrl
        val assets = response.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i)
                val assetName = asset?.optString("name", "") ?: ""
                if (assetName.endsWith(".apk", ignoreCase = true)) {
                    downloadUrl = asset?.optString("browser_download_url", htmlUrl) ?: htmlUrl
                    break
                }
            }
        }

        return UpdateCheckResult.Available(
            UpdateInfo(
                channel = CHANNEL_RELEASE,
                newVersion = tagName,
                currentVersion = currentVersion,
                title = title,
                description = body,
                downloadUrl = downloadUrl,
                pageUrl = htmlUrl
            )
        )
    }

    private fun checkActionUpdate(): UpdateCheckResult {
        val apiUrl = "https://api.github.com/repos/$GITHUB_REPO/actions/runs?status=success&branch=main&per_page=5"
        val response = getJson(apiUrl) ?: return UpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME, CHANNEL_ACTION)

        val runs = response.optJSONArray("workflow_runs")
        if (runs == null || runs.length() == 0) {
            return UpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME, CHANNEL_ACTION)
        }

        var targetRun: JSONObject? = null
        for (i in 0 until runs.length()) {
            val run = runs.getJSONObject(i)
            val name = run.optString("name", "")
            if (name.contains("Build Signed APK", ignoreCase = true)) {
                targetRun = run
                break
            }
        }
        if (targetRun == null) {
            targetRun = runs.getJSONObject(0)
        }

        val runNumber = targetRun.optInt("run_number", 0)
        val currentRunNumber = BuildConfig.GITHUB_RUN_NUMBER.toIntOrNull() ?: 0

        val currentVersion = BuildConfig.VERSION_NAME
        val isNewer = if (currentRunNumber > 0) {
            runNumber > currentRunNumber
        } else {
            // Local / Release build switching to Action channel: consider newer if valid run exists
            runNumber > 0
        }

        if (!isNewer || runNumber == 0) {
            return UpdateCheckResult.UpToDate(currentVersion, CHANNEL_ACTION)
        }

        val newVersionName = "Action-$runNumber"
        val title = targetRun.optString("display_title", "Build Signed APK #$runNumber")
        val htmlUrl = targetRun.optString("html_url", "https://github.com/$GITHUB_REPO/actions")

        return UpdateCheckResult.Available(
            UpdateInfo(
                channel = CHANNEL_ACTION,
                newVersion = newVersionName,
                currentVersion = currentVersion,
                title = title,
                description = "CI Workflow Run #$runNumber",
                downloadUrl = htmlUrl,
                pageUrl = htmlUrl
            )
        )
    }

    private fun getJson(urlStr: String): JSONObject? {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "Add-RSA-Android-App")
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000

        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                return null
            }

            val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
            }
            reader.close()
            return JSONObject(sb.toString())
        } finally {
            conn.disconnect()
        }
    }
}
