package com.wahana.swbplugin.updater

enum class BundleSource(val value: String) {
    BUILTIN("builtin"),
    OTA("ota");

    companion object {
        fun fromString(str: String?): BundleSource =
            if (str?.lowercase() == "ota") OTA else BUILTIN
    }
}

data class BundleState(
    val build: Int,
    val version: String,
    val path: String,
    val source: BundleSource
)

data class PendingUpdateState(
    val build: Int,
    val version: String,
    val path: String,
    val pendingAt: Long
)

enum class UpdateState(val value: String) {
    CHECKING("checking"),
    UP_TO_DATE("up-to-date"),
    AVAILABLE("available"),
    DOWNLOADING("downloading"),
    VERIFYING("verifying"),
    INSTALLING("installing"),
    APPLYING("applying"),
    APPLIED("applied"),
    ERROR("error")
}

data class UpdateStateEvent(
    val state: UpdateState,
    val message: String? = null,
    val build: Int? = null,
    val version: String? = null
)

class SwbUpdateException(
    message: String,
    val code: String = "UPDATE_FAILED",
    cause: Throwable? = null
) : Exception(message, cause)

