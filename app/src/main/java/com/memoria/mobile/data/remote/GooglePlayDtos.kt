package com.memoria.mobile.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GooglePlayConfig(val configured: Boolean = false, val packageName: String = "", val productId: String = "")

@JsonClass(generateAdapter = true)
data class GooglePlayPurchaseRequest(val purchaseToken: String)

@JsonClass(generateAdapter = true)
data class GooglePlayEntitlement(val active: Boolean = false, val plan: String = "", val expiryTime: String? = null)
