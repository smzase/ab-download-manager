package ir.amirab.downloader.connection

data class CfWorkerSettings(
    val enabled: Boolean,
    val url: String,
    val secretKey: String,
)

interface CfWorkerSettingsProvider {
    fun getCfWorkerSettings(): CfWorkerSettings
}
