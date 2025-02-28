package org.jetbrains.kotlinApp.utils

import org.jetbrains.kotlinApp.ApplicationContext

expect fun getImportFilePath(context: ApplicationContext, filename: String): String?