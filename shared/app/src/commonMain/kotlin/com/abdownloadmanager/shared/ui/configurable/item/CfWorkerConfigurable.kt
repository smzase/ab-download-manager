package com.abdownloadmanager.shared.ui.configurable.item

import com.abdownloadmanager.shared.ui.configurable.Configurable
import ir.amirab.util.compose.StringSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class CfWorkerSettings(
    val enabled: Boolean = false,
    val url: String = "",
    val secretKey: String = "",
)

class CfWorkerConfigurable(
    title: StringSource,
    description: StringSource,
    backedBy: MutableStateFlow<CfWorkerSettings>,
    describe: (CfWorkerSettings) -> StringSource,
    validate: (CfWorkerSettings) -> Boolean = { true },
    enabled: StateFlow<Boolean> = DefaultEnabledValue,
    visible: StateFlow<Boolean> = DefaultVisibleValue,
) : Configurable<CfWorkerSettings>(
    title = title,
    description = description,
    backedBy = backedBy,
    validate = validate,
    describe = describe,
    enabled = enabled,
    visible = visible,
) {
    object Key : Configurable.Key

    override fun getKey() = Key
}
