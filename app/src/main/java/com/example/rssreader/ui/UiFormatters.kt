package com.example.rssreader.ui

import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatRelativeTime(timestamp: Long?): String {
    if (timestamp == null) {
        return "nie"
    }

    return DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}

fun formatFeedUpdatedAt(timestamp: Long?): String {
    if (timestamp == null) {
        return "nie"
    }

    val formatter = SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
