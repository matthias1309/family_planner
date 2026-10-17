package de.familyplanner.android.core.data.network

data class ServerConfig(
    val baseUrl: String,
) {
    init {
        require(baseUrl.endsWith("/")) { "baseUrl must end with '/'" }
    }
}
