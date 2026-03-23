package com.abdownloadmanager.desktop.utils

import com.abdownloadmanager.desktop.storage.AppSettingsStorage
import ir.amirab.downloader.connection.CfWorkerSettings
import ir.amirab.downloader.connection.CfWorkerSettingsProvider

class CfWorkerSettingsProviderFromSettings(
    private val appSettingsStorage: AppSettingsStorage
) : CfWorkerSettingsProvider {
    override fun getCfWorkerSettings(): CfWorkerSettings {
        return CfWorkerSettings(
            enabled = appSettingsStorage.cfWorkerEnabled.value,
            url = appSettingsStorage.cfWorkerUrl.value,
            secretKey = appSettingsStorage.cfWorkerSecretKey.value,
        )
    }
}
