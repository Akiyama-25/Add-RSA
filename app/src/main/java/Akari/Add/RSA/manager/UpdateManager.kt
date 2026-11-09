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
import java.time.Instant

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
        val defaultChannel = if (BuildConfig.VERSION_NAME.startsWith("Action-", ignoreCase = true)) {
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

    private fun parseReleaseNumber(tag: String): Int {
        return Regex("""\d+""").find(tag)?.value?.toIntOrNull() ?: 0
    }

    private fun parseActionRunNumber(versionName: String): Int {
        if (!versionName.startsWith("Action-", ignoreCase = true)) return 0
        return versionName.removePrefix("Action-").removePrefix("action-").toIntOrNull() ?: 0
    }

    private fun parseIsoTimestamp(isoString: String): Long {
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (e: Exception) {
            0L
        }
    }

    private fun getLatestReleaseTagWebFallback(): String? {
        return try {
            val url = URL("https://github.com/$GITHUB_REPO/releases/latest")
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Add-RSA-Android-App")
            conn.requestMethod = "GET"
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: return null
                location.substringAfterLast("/").trim()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun checkReleaseUpdate(): UpdateCheckResult {
        var tagName = ""
        var title = ""
        var body = ""
        var htmlUrl = "https://github.com/$GITHUB_REPO/releases"
        var downloadUrl = ""

        // 1. Try official GitHub REST API first
        val apiUrl = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
        val response = getJson(apiUrl)
        if (response != null) {
            tagName = response.optString("tag_name", "").trim()
            title = response.optString("name", tagName)
            body = response.optString("body", "")
            htmlUrl = response.optString("html_url", htmlUrl)
            val assets = response.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i)
                    val assetName = asset?.optString("name", "") ?: ""
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset?.optString("browser_download_url", "") ?: ""
                        break
                    }
                }
            }
        }

        // 2. If API was rate limited or empty, seamlessly fallback to Web Redirect
        if (tagName.isBlank()) {
            val fallbackTag = getLatestReleaseTagWebFallback()
            if (!fallbackTag.isNullOrBlank()) {
                tagName = fallbackTag
                title = "Add RSA $tagName"
                htmlUrl = "https://github.com/$GITHUB_REPO/releases/tag/$tagName"
            }
        }

        if (tagName.isBlank()) {
            return UpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME, CHANNEL_RELEASE)
        }

        if (downloadUrl.isBlank()) {
            downloadUrl = "https://github.com/$GITHUB_REPO/releases/download/$tagName/Add-RSA-$tagName.apk"
        }

        val currentVersion = BuildConfig.VERSION_NAME
        val builtReleaseTag = BuildConfig.BUILT_LATEST_RELEASE_TAG

        val remoteNum = parseReleaseNumber(tagName)
        val currentBaseNum = if (currentVersion.startsWith("Action-", ignoreCase = true)) {
            parseReleaseNumber(builtReleaseTag)
        } else {
            parseReleaseNumber(currentVersion)
        }

        val isNewer = when {
            remoteNum > 0 && currentBaseNum > 0 -> remoteNum > currentBaseNum
            currentVersion.startsWith("Action-", ignoreCase = true) -> {
                !tagName.equals(builtReleaseTag, ignoreCase = true)
            }
            else -> {
                !tagName.equals(currentVersion, ignoreCase = true)
            }
        }

        if (!isNewer) {
            return UpdateCheckResult.UpToDate(currentVersion, CHANNEL_RELEASE)
        }

        return UpdateCheckResult.Available(
            UpdateInfo(
                channel = CHANNEL_RELEASE,
                newVersion = tagName,
                currentVersion = currentVersion,
                title = title.ifBlank { "Add RSA $tagName" },
                description = body.ifBlank { "Release $tagName" },
                downloadUrl = downloadUrl,
                pageUrl = htmlUrl
            )
        )
    }

    fun checkActionUpdate(): UpdateCheckResult {
        val currentVersion = BuildConfig.VERSION_NAME
        val apiUrl = "https://api.github.com/repos/$GITHUB_REPO/actions/runs?status=success&branch=main&per_page=5"
        val response = getJson(apiUrl)

        if (response == null) {
            // If Actions API is rate-limited, check if a newer formal release exists
            if (currentVersion.startsWith("Action-", ignoreCase = true)) {
                val releaseCheck = checkReleaseUpdate()
                if (releaseCheck is UpdateCheckResult.Available) {
                    return releaseCheck
                }
            }
            return UpdateCheckResult.UpToDate(currentVersion, CHANNEL_ACTION)
        }

        val runs = response.optJSONArray("workflow_runs")
        if (runs == null || runs.length() == 0) {
            return UpdateCheckResult.UpToDate(currentVersion, CHANNEL_ACTION)
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

        val remoteRunNumber = targetRun.optInt("run_number", 0)
        val currentRunNumber = parseActionRunNumber(currentVersion)
        val runCreatedAtStr = targetRun.optString("created_at", "")
        val runCreatedTime = parseIsoTimestamp(runCreatedAtStr)
        val buildTime = BuildConfig.BUILD_TIME_MILLIS
        val remoteHeadSha = targetRun.optString("head_sha", "")

        val isNewer = if (currentVersion.startsWith("Action-", ignoreCase = true)) {
            // Currently on Action build: compare action run numbers or commit time
            if (currentRunNumber > 0 && remoteRunNumber > 0) {
                remoteRunNumber > currentRunNumber
            } else if (runCreatedTime > 0 && buildTime > 0) {
                runCreatedTime > buildTime
            } else {
                false
            }
        } else {
            // Currently on Release build: an Action build is ONLY newer if it was created AFTER this Release!
            val isSameCommit = remoteHeadSha.isNotBlank() && remoteHeadSha.startsWith(BuildConfig.GIT_COMMIT_SHA)
            if (isSameCommit) {
                false // Same commit as the release, cannot be an update
            } else if (runCreatedTime > 0 && buildTime > 0) {
                // Must be committed/created strictly AFTER the release build time
                runCreatedTime > buildTime
            } else {
                false
            }
        }

        if (!isNewer || remoteRunNumber == 0) {
            // Cross-channel detection:
            // If currently on an older Action build, and no newer Action build is found,
            // check whether a newer formal Release has been published!
            if (currentVersion.startsWith("Action-", ignoreCase = true)) {
                val releaseCheck = checkReleaseUpdate()
                if (releaseCheck is UpdateCheckResult.Available) {
                    return releaseCheck
                }
            }
            return UpdateCheckResult.UpToDate(currentVersion, CHANNEL_ACTION)
        }

        val newVersionName = "Action-$remoteRunNumber"
        val title = targetRun.optString("display_title", "Build Signed APK #$remoteRunNumber")
        val htmlUrl = targetRun.optString("html_url", "https://github.com/$GITHUB_REPO/actions")

        return UpdateCheckResult.Available(
            UpdateInfo(
                channel = CHANNEL_ACTION,
                newVersion = newVersionName,
                currentVersion = currentVersion,
                title = title,
                description = "CI Workflow Run #$remoteRunNumber",
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
