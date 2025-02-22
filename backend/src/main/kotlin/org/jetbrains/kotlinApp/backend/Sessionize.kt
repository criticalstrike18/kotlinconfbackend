package org.jetbrains.kotlinApp.backend

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import org.jetbrains.kotlinApp.Conference
import java.util.concurrent.TimeUnit


@Volatile
private var conference: Conference? = null
val comeBackLater = HttpStatusCode(477, "Come Back Later")
const val GMT_TIME_OFFSET = 2 * 60 * 60 * 1000

@OptIn(DelicateCoroutinesApi::class)
internal fun Application.launchSyncJob(
    database: Store,
    sessionInterval: Long
) {
    log.info("Synchronizing each $sessionInterval minutes with database")
    GlobalScope.launch {
        while (true) {
            log.trace("Synchronizing to Database…")
            synchronizeWithSessionize(database)
            log.trace("Finished loading data from Database.")
            delay(TimeUnit.MINUTES.toMillis(sessionInterval))
        }
    }
}

internal suspend fun synchronizeWithSessionize(
    database: Store,
) {
    conference = database.getConferenceData()
}

suspend fun fetchSessionizeImage(
    imagesUrl: String,
    imageId: String
): ByteArray {
    return client.get("$imagesUrl/$imageId").body<ByteArray>()
}

fun getSessionizeData(): Conference = conference ?: throw ServiceUnavailable()

fun GMTDate.toKotlinInstant(): Instant = Instant.fromEpochMilliseconds(timestamp)

fun Instant.toGMTDate(): GMTDate = GMTDate(timestamp = toEpochMilliseconds())
