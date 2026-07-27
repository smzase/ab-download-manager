package com.abdownloadmanager.desktop.nativemessaging

import com.abdownloadmanager.desktop.utils.AppInfo
import com.abdownloadmanager.desktop.utils.AppProperties
import com.abdownloadmanager.desktop.utils.isAppInstalled
import ir.amirab.util.createParentDirectories
import ir.amirab.util.deleteIfExists
import ir.amirab.util.desktop.WindowsRegistry
import ir.amirab.util.platform.Platform
import ir.amirab.util.writeText
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText

abstract class NativeMessagingManifestApplier(val json: Json) : KoinComponent {
    protected inline fun <reified T : Any> serialize(data: T): String {
        return json.encodeToString(data)
    }

    protected inline fun <reified T : Any> deserialize(string: String): T {
        return json.decodeFromString(string)
    }

    abstract fun updateManifests(manifests: NativeMessagingManifests)
    abstract fun removeManifests()

    companion object {
        fun getForCurrentPlatform(json: Json): NativeMessagingManifestApplier {
            if (!AppInfo.isAppInstalled()) {
                return NoOpNativeMessagingApplier(json)
            }
            return when (AppInfo.platform) {
                Platform.Desktop.Linux -> LinuxNativeMessagingManifestApplier(json)
                Platform.Desktop.MacOS -> MacosNativeMessagingManifestApplier(json)
                Platform.Desktop.Windows -> WindowsNativeMessagingManifestApplier(json)
                Platform.Android -> error("there is no native messaging for android so this code should never used in android")
            }
        }
    }
}

class WindowsNativeMessagingManifestApplier(json: Json) : NativeMessagingManifestApplier(json) {
    private val baseNativeMessagingDir get() = AppInfo.definedPaths.configDir / "native_messaging"
    private val firefoxManifestFile get() = baseNativeMessagingDir / "firefox" / "${AppInfo.packageName}.json"
    private val chromeManifestFile get() = baseNativeMessagingDir / "chrome" / "${AppInfo.packageName}.json"
    private val firefoxRegistryPath get() = "HKCU\\SOFTWARE\\Mozilla\\NativeMessagingHosts\\${AppInfo.packageName}"
    private val chromeRegistryPath get() = "HKCU\\SOFTWARE\\Google\\Chrome\\NativeMessagingHosts\\${AppInfo.packageName}"

    override fun updateManifests(
        manifests: NativeMessagingManifests
    ) {
        listOf(
            firefoxManifestFile,
            chromeManifestFile,
        ).forEach { it.createParentDirectories() }
        firefoxManifestFile.writeText(serialize(manifests.firefoxNativeMessagingManifest))
        WindowsRegistry.setValueInRegistry(
            path = firefoxRegistryPath,
            key = null,
            value = firefoxManifestFile.toString()
        )
        chromeManifestFile.writeText(serialize(manifests.chromeNativeMessagingManifest))
        WindowsRegistry.setValueInRegistry(
            path = chromeRegistryPath,
            key = null,
            value = chromeManifestFile.toString()
        )
    }

    override fun removeManifests() {
        firefoxManifestFile.deleteIfExists()
        WindowsRegistry.removePathInRegistry(
            path = firefoxRegistryPath,
        )
        chromeManifestFile.deleteIfExists()
        WindowsRegistry.removePathInRegistry(
            path = chromeRegistryPath,
        )
    }

}

class MacosNativeMessagingManifestApplier(
    json: Json
) : NativeMessagingManifestApplier(json) {
    private val firefoxNativeMessagingPath
        get() = Path(
            AppProperties.userDir, "Library/Application Support/Mozilla/NativeMessagingHosts",
            "${AppInfo.packageName}.json"
        )
    private val chromeNativeMessagingPath
        get() = Path(
            AppProperties.userDir, "Library/Application Support/Google/Chrome/NativeMessagingHosts",
            "${AppInfo.packageName}.json"
        )
    private val chromiumNativeMessagingPath
        get() = Path(
            AppProperties.userDir, "Library/Application Support/Chromium/NativeMessagingHosts",
            "${AppInfo.packageName}.json"
        )


    override fun updateManifests(manifests: NativeMessagingManifests) {
        listOf(
            firefoxNativeMessagingPath,
            chromeNativeMessagingPath,
            chromiumNativeMessagingPath
        ).forEach { it.createParentDirectories() }

        firefoxNativeMessagingPath.writeText(serialize(manifests.firefoxNativeMessagingManifest))
        val chromeManifestString = serialize(manifests.chromeNativeMessagingManifest)
        chromeNativeMessagingPath.writeText(chromeManifestString)
        chromiumNativeMessagingPath.writeText(chromeManifestString)
    }

    override fun removeManifests() {
        firefoxNativeMessagingPath.deleteIfExists()
        chromeNativeMessagingPath.deleteIfExists()
        chromiumNativeMessagingPath.deleteIfExists()
    }
}

class LinuxNativeMessagingManifestApplier(
    json: Json
) : NativeMessagingManifestApplier(
    json
) {
    private val firefoxNativeMessagingPath
        get() = Path(AppProperties.userDir, ".mozilla/native-messaging-hosts", "${AppInfo.packageName}.json")
    private val chromeNativeMessagingPath
        get() = Path(AppProperties.userDir, ".config/google-chrome/NativeMessagingHosts", "${AppInfo.packageName}.json")
    private val chromiumNativeMessagingPath
        get() = Path(AppProperties.userDir, ".config/chromium/NativeMessagingHosts", "${AppInfo.packageName}.json")

    override fun updateManifests(manifests: NativeMessagingManifests) {
        listOf(
            firefoxNativeMessagingPath,
            chromeNativeMessagingPath,
            chromiumNativeMessagingPath
        ).forEach { it.createParentDirectories() }

        firefoxNativeMessagingPath.writeText(serialize(manifests.firefoxNativeMessagingManifest))
        val chromeManifestString = serialize(manifests.chromeNativeMessagingManifest)
        chromeNativeMessagingPath.writeText(chromeManifestString)
        chromiumNativeMessagingPath.writeText(chromeManifestString)
    }

    override fun removeManifests() {
        firefoxNativeMessagingPath.deleteIfExists()
        chromeNativeMessagingPath.deleteIfExists()
        chromiumNativeMessagingPath.deleteIfExists()
    }
}

class NoOpNativeMessagingApplier(
    json: Json
) : NativeMessagingManifestApplier(
    json,
) {
    override fun updateManifests(manifests: NativeMessagingManifests) {
        //no-op
    }

    override fun removeManifests() {
        //no-op
    }
}
