@file:OptIn(ExperimentalSerializationApi::class)

package org.jetbrains.kotlinApp

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.protobuf.protobuf
import io.ktor.util.date.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.kotlinApp.utils.appLogger

val HTTP_CLIENT = HttpClient()

/**
 * Adapter to handle backend API and manage auth information.
 */
class APIClient(
    private val apiUrl: String
) : Closeable {
    var userUuid: String? = null // <--- CHANGED (renamed userId -> userUuid)

    private val client = HTTP_CLIENT.config {
        install(ContentNegotiation) {
            json()
            protobuf()
        }

        install(Logging) {
            level = LogLevel.HEADERS
            logger = appLogger()
        }

        HttpResponseValidator {
            validateResponse {
                when (it.status) {
                    COMEBACK_LATER_STATUS -> throw TooEarlyVote()
                    TOO_LATE_STATUS -> throw TooLateVote()
                    HttpStatusCode.Conflict -> return@validateResponse
                    HttpStatusCode.Unauthorized -> throw Unauthorized()
                }
            }
        }

        install(HttpRequestRetry) {
            maxRetries = Int.MAX_VALUE
            delay {
                kotlinx.coroutines.delay(it)
            }
            constantDelay(10 * 1000L)
            retryOnException(retryOnTimeout = true)
        }

        install(DefaultRequest) {
            url.takeFrom(apiUrl)
        }
    }

    /**
     * @return status of request.
     */
    suspend fun sign(): Boolean {
        val uuid = userUuid ?: return false // <--- CHANGED variable name

        val response = client.post {
            apiUrl("sign")
            setBody(uuid) // <--- passing the UUID in the request body
        }
        return response.status.isSuccess()
    }

    /**
     * Get [ConferenceData] info
     */
    suspend fun downloadConferenceData(): Conference = client.get {
        url.path("conference")
    }.body()

    suspend fun getSessionData(): List<SessionInfo> {
        if (userUuid == null) return emptyList() // <--- CHANGED
        return client.get {
            apiUrl("get/sessions")
        }.body<List<SessionInfo>>()
    }

    suspend fun getRoomData(): List<RoomTable> {
        if (userUuid == null) return emptyList()
        return client.get {
            apiUrl("get/rooms")
        }.body()
    }

    suspend fun getSpeakerData(): List<SpeakerInfo> {
        if (userUuid == null) return emptyList()
        return client.get {
            apiUrl("get/speakers")
        }.body()
    }

    suspend fun getCategoryData(): List<CategoriesTable> {
        if (userUuid == null) return emptyList()
        return client.get {
            apiUrl("get/categories")
        }.body()
    }

    suspend fun getSessionSpeakersData(): Map<String, List<String>> {
        if (userUuid == null) return emptyMap()
        return client.get {
            apiUrl("get/session-speakers")
        }.body()
    }

    suspend fun getSessionCategoriesData(): Map<String, List<Int>> {
        if (userUuid == null) return emptyMap()
        return client.get {
            apiUrl("get/session-categories")
        }.body()
    }

    suspend fun sendSessionData(request: ConferenceSessionRequest): SessionResponse {
        return try {
            val response = client.post {
                apiUrl("send/sessions")
                json()
                setBody(request)
            }
            response.body()
        } catch (e: Exception) {
            SessionResponse(
                success = false,
                sessionId = null,
                message = e.message
            )
        }
    }

    suspend fun sendRoomData(room: RoomTableReq): RoomResponse {
        return try {
            val response = client.post {
                apiUrl("send/rooms")
                json()
                setBody(room)
            }
            response.body()
        } catch (e: Exception) {
            RoomResponse(
                success = false,
                roomId = null,
                message = e.message
            )
        }
    }

    suspend fun sendSessionSpeaker(sessionId: String, speakerId: String): Boolean {
        if (userUuid == null) return false

        try {
            client.post {
                apiUrl("send/session-speaker")
                json()
                setBody(SessionSpeakerRequest(sessionId, speakerId))
            }
            return true
        } catch (e: Exception) {
            println("Failed to sync session-speaker: ${e.message}")
            return false
        }
    }

    /**
     * Vote for session.
     */
    suspend fun vote(sessionId: String, score: Score?): Boolean {
        if (userUuid == null) return false

        client.post {
            apiUrl("vote")
            json()
            setBody(VoteInfo(sessionId, score))
        }
        return true
    }

    /**
     * Send feedback
     */
    suspend fun sendFeedback(sessionId: String, feedback: String): Boolean {
        if (userUuid == null) return false

        client.post {
            apiUrl("feedback")
            json()
            setBody(FeedbackInfo(sessionId, feedback))
        }
        return true
    }

    /**
     * List my votes.
     */
    suspend fun myVotes(): List<VoteInfo> {
        if (userUuid == null) return emptyList()
        return client.get {
            apiUrl("vote")
        }.body<Votes>().votes
    }

    suspend fun getPodcastsData(): List<ChannelFullData> {
        if (userUuid == null) return emptyList()
        return client.get {
            apiUrl("/podcast/all")
            accept(ContentType.Application.ProtoBuf)
        }.body()

    }

    suspend fun sendPodcastRequest(title: String, author: String, rssLink: String): Boolean {
        if (userUuid == null) return false

        client.post {
            apiUrl("/podcast/sendRequest")
            json()
            setBody(PodcastQueryInfo(title,author,rssLink))
        }
        return true
    }

    /**
     * Get server time.
     */
    suspend fun getServerTime(): GMTDate = client.get {
        apiUrl("time")
    }.bodyAsText().let { response -> GMTDate(response.toLong()) }

    private fun HttpRequestBuilder.json() {
        contentType(ContentType.Application.Json)
    }

    private fun HttpRequestBuilder.apiUrl(path: String) {
        if (userUuid != null) {
            // <--- CHANGED: we pass Bearer <uuid> for all validated requests
            header(HttpHeaders.Authorization, "Bearer $userUuid")
        }
        header(HttpHeaders.CacheControl, "no-cache")
        url {
            encodedPath = path
        }
    }

    override fun close() {
        client.close()
    }
}

