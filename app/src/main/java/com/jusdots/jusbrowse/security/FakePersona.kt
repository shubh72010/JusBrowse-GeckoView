package com.jusdots.jusbrowse.security

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class FakePersona(
    val id: String,
    val displayName: String,
    val userAgent: String,
    val platform: String,
    val platformString: String,
    val platformVersion: String = "14",
    val model: String = "Unknown",
    val mobile: Boolean = true,
    val locale: String,
    val languages: List<String> = listOf("en-US", "en"),
    val timezone: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val pixelRatio: Float,
    val dpi: Int = 480,
    val ramGB: Int,
    val cpuCores: Int,
    val videoCardVendor: String,
    val videoCardRenderer: String,
    val brands: List<BrandVersion>,
    val deviceManufacturer: String = "Generic",
    val deviceModel: String = "Generic Mobile",
    val androidVersionName: String = "14",
    val browserName: String = "Chrome",
    val browserVersion: String = "133.0.0.0",
    val isFlagship: Boolean = false,
    val noiseSeed: Long = 0L,
    val groupId: String = "default",
    val headers: Map<String, String> = emptyMap(),
    val flagEmoji: String = "🌐",
    val countryCode: String = "US"
) : Parcelable {

    @Parcelize
    data class BrandVersion(val brand: String, val version: String) : Parcelable

    fun getSecChUaHeader(): String {
        return brands.joinToString(", ") {
            "\"${it.brand}\";v=\"${it.version}\""
        }
    }
}
