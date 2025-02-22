package org.jetbrains.kotlinApp

import io.ktor.util.date.GMTDate

fun GMTDate.isSameDay(other: GMTDate): Boolean {
    return this.dayOfMonth == other.dayOfMonth &&
            this.month == other.month &&
            this.year == other.year
}

fun GMTDate.isBeforeDay(other: GMTDate): Boolean {
    if (this.year != other.year) return this.year < other.year
    if (this.month != other.month) return this.month.ordinal < other.month.ordinal
    return this.dayOfMonth < other.dayOfMonth
}

fun GMTDate.addDays(days: Int): GMTDate {
    // Adding days in milliseconds
    return GMTDate(timestamp + (days * 24 * 60 * 60 * 1000))
}

//fun determineEventDay(sessionDate: GMTDate, currentDate: GMTDate): EventDay {
//    if (sessionDate.isBeforeDay(currentDate)) {
//        return EventDay.Passed
//    }
//
//    if (sessionDate.isSameDay(currentDate)) {
//        return EventDay.Today
//    }
//
//    if (sessionDate.isSameDay(currentDate.addDays(1))) {
//        return EventDay.Tomorrow
//    }
//
//    return EventDay.Later
//}