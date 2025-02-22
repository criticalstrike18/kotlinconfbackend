package org.jetbrains.kotlinApp

import java.util.UUID

actual fun generateUserId(): String = "desktop-" + UUID.randomUUID().toString()