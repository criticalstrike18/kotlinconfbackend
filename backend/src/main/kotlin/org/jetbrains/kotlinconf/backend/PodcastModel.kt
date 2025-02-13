package org.jetbrains.kotlinconf.backend

import kotlinx.serialization.Serializable

@Serializable
data class ChannelData(
    val title: String,
    val link: String,
    val description: String,
    val copyright: String? = null,
    val language: String? = null,
    val author: String? = null,
    val ownerEmail: String? = null,
    val ownerName: String? = null,
    val imageUrl: String? = null,
    // We'll store lastBuildDate as a string or as a Kotlin Instant?
    // Example: "2025-01-23T15:36:55Z"
    val lastBuildDate: String? = null
)
