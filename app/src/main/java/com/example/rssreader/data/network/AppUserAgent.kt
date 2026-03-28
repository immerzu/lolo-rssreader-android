package com.example.rssreader.data.network

import com.example.rssreader.BuildConfig

object AppUserAgent {
    val value: String by lazy {
        "RSS-Reader/${BuildConfig.VERSION_NAME} (+Android)"
    }
}
