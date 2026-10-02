package com.memoria.mobile.di

import com.squareup.moshi.Moshi

/**
 * Single Moshi instance shared across the app. Avoids duplicate adapter caches
 * and reflection overhead that come from creating multiple builders.
 */
object MoshiProvider {
    val instance: Moshi = Moshi.Builder().build()
}
