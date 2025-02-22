package org.jetbrains.kotlinApp.backend

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
    val lastBuildDate: String? = null
)
