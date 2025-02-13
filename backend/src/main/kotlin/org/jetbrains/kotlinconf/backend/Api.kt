package org.jetbrains.kotlinconf.backend

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.util.date.GMTDate
import org.jetbrains.kotlinconf.ConferenceSessionRequest
import org.jetbrains.kotlinconf.FeedbackInfo
import org.jetbrains.kotlinconf.RoomResponse
import org.jetbrains.kotlinconf.SessionCategoriesRequest
import org.jetbrains.kotlinconf.SessionResponse
import org.jetbrains.kotlinconf.SessionSpeakerRequest
import org.jetbrains.kotlinconf.VoteInfo
import org.jetbrains.kotlinconf.Votes
import java.time.Clock
import java.time.LocalDateTime

internal fun Route.api(
    store: Store,
    imagesUrl: String,
    adminSecret: String
) {
    apiUsers(store)
    sessions()
    apiVote(store, adminSecret)
    apiSynchronize(store,adminSecret)
    apiTime(adminSecret)
    apiSessionizeImagesProxy(imagesUrl)
    sessionManagementApi(store,adminSecret)
    podcastRoutes(store, adminSecret)
}

/*
POST http://localhost:8080/sign
1238476512873162837
 */
private fun Route.apiUsers(database: Store) {
    post("sign") {
        val userUUID = call.receive<String>()
        val timestamp = LocalDateTime.now(Clock.systemUTC())
        val created = database.createUser(userUUID, timestamp)
        val code = if (created) HttpStatusCode.Created else HttpStatusCode.Conflict
        call.respond(code)
    }
}

/*
GET http://localhost:8080/vote
Accept: application/json
Authorization: Bearer 1238476512873162837
*/
private fun Route.apiVote(
    database: Store,
    adminSecret: String
) {
    route("vote") {
        get {
            val principal = call.validatePrincipal(database) ?: throw Unauthorized()
            // principal.token will be the same userUuid passed from the client
            val votes = database.getVotes(principal.token)
            call.respond(Votes(votes))
        }

        post {
            val principal = call.validatePrincipal(database) ?: throw Unauthorized()
            val vote = call.receive<VoteInfo>()
            val sessionId = vote.sessionId

            val session = getSessionizeData().sessions.firstOrNull { it.id == sessionId }
                ?: throw NotFound()

            val nowTime = now()
            val startVotesAt = session.startsAt
            val votingPeriodStarted = nowTime >= startVotesAt.timestamp

            if (!votingPeriodStarted) {
                return@post call.respond(comeBackLater)
            }

            val timestamp = LocalDateTime.now(Clock.systemUTC())
            database.changeVote(principal.token, sessionId, vote.score, timestamp)
            call.respond(HttpStatusCode.OK)
        }

        /**
         * Admin endpoints
         */
        get("all") {
            call.validateSecret(adminSecret)
            val votes = database.getAllVotes()
            call.respond(votes)
        }
    }

    route("feedback") {
        post {
            val principal = call.validatePrincipal(database) ?: throw Unauthorized()
            val feedback = call.receive<FeedbackInfo>()
            val timestamp = LocalDateTime.now(Clock.systemUTC())

            val result = database.setFeedback(
                principal.token, feedback.sessionId, feedback.value, timestamp
            )

            if (result) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }

        get("summary") {
            call.validateSecret(adminSecret)
            call.respond(database.getFeedbackSummary())
        }
    }
}

/*
GET http://localhost:8080/conference
Accept: application/json
Authorization: Bearer 1238476512873162837
*/
private fun Route.sessions() {
    get("conference") {
        // Notice: does not require validatePrincipal; open endpoint
        call.respond(getSessionizeData())
    }
}

/**
 * Admin endpoints
 * GET http://localhost:8080/time
 *
 * POST http://localhost:8080/time/1589568000000
 */
private fun Route.apiTime(adminSecret: String) {
    get("time") {
        call.respond(now())
    }
    post("time/{timestamp}") {
        call.validateSecret(adminSecret)

        val timestamp = call.parameters["timestamp"] ?: error("No time")
        val time = if (timestamp == "null") {
            null
        } else {
            GMTDate(timestamp.toLong())
        }

        updateTime(time)
        call.respond(HttpStatusCode.OK)
    }
}

/*
POST http://localhost:8080/sessionizeSync
*/
private fun Route.apiSynchronize(
    database: Store,
    adminSecret: String
) {
    post("sessionizeSync") {
        call.validateSecret(adminSecret)
        synchronizeWithSessionize(database)
        call.respond(HttpStatusCode.OK)
    }
}

/*
GET http://localhost:8080/sessionize/image/{imageId}
Authorization: Bearer 1238476512873162837
*/
private fun Route.apiSessionizeImagesProxy(imagesUrl: String) {

    get("sessionize/image/{imageId}") {
        call.respond(fetchSessionizeImage(imagesUrl, call.parameters["imageId"] ?: error("No imageId")))
    }
}

private fun ApplicationCall.validateSecret(adminSecret: String) {
    val principal = principal<KotlinConfPrincipal>()
    if (principal?.token != adminSecret) {
        throw Unauthorized()
    }
}

private suspend fun ApplicationCall.validatePrincipal(database: Store): KotlinConfPrincipal? {
    val principal = principal<KotlinConfPrincipal>() ?: return null
    if (!database.validateUser(principal.token)) return null
    return principal
}

private fun Route.sessionManagementApi(database: Store, adminSecret: String) {
    // Protected endpoint: require validatePrincipal
    get("get/sessions") {
        call.validatePrincipal(database) ?: throw Unauthorized()
        try {
            val sessionsData = database.getAllSessions()
            call.respond(sessionsData)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "Failed to fetch sessions: ${e.message}")
        }
    }

    get("get/categories") {
        call.validatePrincipal(database) ?: throw Unauthorized()
        try {
            val categoriesData = database.getAllCategories()
            call.respond(categoriesData)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "Failed to fetch categories: ${e.message}")
        }
    }

    get("get/rooms") {
        // Validate that the user is authorized via the Bearer token.
        call.validatePrincipal(database) ?: throw Unauthorized()

        try {
            val roomsData = database.getAllRooms()
            call.respond(roomsData)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "Failed to fetch rooms: ${e.message}")
        }
    }

    get("get/speakers") {
        call.validatePrincipal(database) ?: throw Unauthorized()
        try {
            val speakersData = database.getAllSpeakers()
            call.respond(speakersData)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "Failed to fetch speakers: ${e.message}")
        }
    }

    get("get/session-speakers") {
        call.validatePrincipal(database) ?: throw Unauthorized()
        try {
            val sessions = database.getAllSessions()
            val sessionSpeakers = sessions.associate { session ->
                session.id to database.getSessionSpeakers(session.id)
            }
            call.respond(sessionSpeakers)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "Failed to fetch session-speaker relationships: ${e.message}")
        }
    }

    get("get/session-categories") {
        call.validatePrincipal(database) ?: throw Unauthorized()
        try {
            val sessions = database.getAllSessions()
            val sessionCategories = sessions.associate { session ->
                session.id to database.getSessionCategories(session.id)
            }
            call.respond(sessionCategories)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "Failed to fetch session-category relationships: ${e.message}")
        }
    }

    post("send/sessions") {
        call.validatePrincipal(database) ?: throw Unauthorized()
        try {
            val session = call.receive<ConferenceSessionRequest>()
            val sessionId = database.addSession(session)  // Returns the generated UUID
            call.respond(
                SessionResponse(
                success = true,
                sessionId = sessionId,
                message = "Session added successfully"
            )
            )
        } catch (e: Exception) {
            call.respond(
                SessionResponse(
                success = false,
                sessionId = null,
                message = e.message
            )
            )
        }
    }

    post("send/rooms") {
        call.validatePrincipal(database) ?: throw Unauthorized()
        try {
            val room = call.receive<ConferenceRoomRequest>()
            val roomId = database.addRoom(room)  // Will modify this to return the ID
            call.respond(
                RoomResponse(
                success = true,
                roomId = roomId,
                message = "Room added successfully"
            )
            )
        } catch (e: Exception) {
            call.respond(
                RoomResponse(
                success = false,
                roomId = null,
                message = e.message
            )
            )
        }
    }

    post("send/session-speaker") {
        call.validatePrincipal(database) ?: throw Unauthorized()
        try {
            val sessionSpeaker = call.receive<SessionSpeakerRequest>()

            // Verify that both session and speaker exist
            database.getSessionById(sessionSpeaker.sessionId)
                ?: throw NotFoundException("Session not found")
            database.getSpeakerById(sessionSpeaker.speakerId)
                ?: throw NotFoundException("Speaker not found")

            // Add the relationship to the database
            database.addSessionSpeaker(
                sessionId = sessionSpeaker.sessionId,
                speakerId = sessionSpeaker.speakerId
            )

            call.respond(HttpStatusCode.Created, "Session-speaker relationship added successfully")
        } catch (e: NotFoundException) {
            call.respond(HttpStatusCode.NotFound, e.message ?: "Resource not found")
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "Failed to add session-speaker relationship: ${e.message}")
        }
    }

    post("send/session-categories") {
        call.validatePrincipal(database) ?: throw Unauthorized()
        try {
            val sessionCategories = call.receive<SessionCategoriesRequest>()

            // Verify that both session and speaker exist
            database.getSessionById(sessionCategories.sessionId)
                ?: throw NotFoundException("Session not found")
            database.getCategoryById(sessionCategories.categoryId)
                ?: throw NotFoundException("Category not found")

            // Add the relationship to the database
            database.addSessionCategory(
                sessionId = sessionCategories.sessionId,
                categoryId = sessionCategories.categoryId
            )

            call.respond(HttpStatusCode.Created, "Session-speaker relationship added successfully")
        } catch (e: NotFoundException) {
            call.respond(HttpStatusCode.NotFound, e.message ?: "Resource not found")
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "Failed to add session-speaker relationship: ${e.message}")
        }
    }


    post("admin/session") {
        call.validateSecret(adminSecret)
        try {
            val session = call.receive<ConferenceSessionRequest>()
            database.addSession(session)
            call.respond(HttpStatusCode.Created, "Session added successfully")
        } catch (e: Exception) {
            error(e)
        }
    }

    // Admin-only routes
    post("admin/speakers") {
        call.validateSecret(adminSecret)
        try {
            val speaker = call.receive<ConferenceSpeakerRequest>()
            database.addSpeaker(speaker)
            call.respond(HttpStatusCode.Created, "Speaker added successfully")
        } catch (e: Exception) {
            error(e)
        }
    }

    post("admin/categories") {
        call.validateSecret(adminSecret)
        try {
            val category = call.receive<ConferenceCategoriesRequest>()
            database.addCategory(category)
            call.respond(HttpStatusCode.Created, "Category added successfully")
        } catch (e: Exception) {
            error(e)
        }
    }
}

private fun Route.podcastRoutes(database: Store, adminSecret: String) {
    post("/podcast/import") {
        try {
            val importRequest = call.receive<PodcastImportRequest>()
            val channelId = database.storePodcastData(importRequest)
            call.respond(
                mapOf(
                    "status" to "ok",
                    "channel_id" to channelId
                )
            )
        } catch (e: Exception) {
            // Log the exception fully
            e.printStackTrace() // or log.error("Error storing data", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                "Failed to store podcast data: ${e.message}"
            )
        }
    }

    get("/podcast/all") {
        try {
            val data = database.getAllPodcastData()
            call.respond(HttpStatusCode.OK, data)
        } catch (e: Exception) {
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, "Failed to fetch data: ${e.message}")
        }
    }
}


