package com.duel2048.shared.protocol

import kotlinx.serialization.Serializable

/** Published by GET /app/update. [url] is a path on the same host, such as /app/android-release.apk. */
@Serializable
data class AppRelease(
    val versionCode: Int = 0,
    val versionName: String = "",
    val url: String = "",
)
