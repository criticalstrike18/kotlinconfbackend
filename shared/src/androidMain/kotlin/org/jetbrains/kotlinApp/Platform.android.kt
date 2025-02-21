package org.jetbrains.kotlinApp

import java.util.*

actual fun generateUserId(): String = "android-" + UUID.randomUUID().toString()
