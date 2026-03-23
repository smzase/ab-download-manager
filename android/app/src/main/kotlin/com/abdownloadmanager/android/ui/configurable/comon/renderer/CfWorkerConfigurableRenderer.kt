package com.abdownloadmanager.android.ui.configurable.comon.renderer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.android.ui.configurable.ConfigTemplate
import com.abdownloadmanager.android.ui.configurable.ConfigurableSheet
import com.abdownloadmanager.android.ui.configurable.NextIcon
import com.abdownloadmanager.android.ui.configurable.TitleAndDescription
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.ui.configurable.ConfigurableRenderer
import com.abdownloadmanager.shared.ui.configurable.ConfigurableUiProps
import com.abdownloadmanager.shared.ui.configurable.isConfigEnabled
import com.abdownloadmanager.shared.ui.configurable.item.CfWorkerConfigurable
import com.abdownloadmanager.shared.ui.configurable.item.CfWorkerSettings
import com.abdownloadmanager.shared.ui.widget.ActionButton
import com.abdownloadmanager.shared.ui.widget.CheckBox
import com.abdownloadmanager.shared.ui.widget.MyTextField
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.util.ui.theme.mySpacings
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.compose.resources.myStringResource

object CfWorkerConfigurableRenderer : ConfigurableRenderer<CfWorkerConfigurable> {
    @Composable
    override fun RenderConfigurable(configurable: CfWorkerConfigurable, configurableUiProps: ConfigurableUiProps) {
        RenderCfWorkerConfig(configurable, configurableUiProps)
    }

    @Composable
    fun RenderCfWorkerConfig(cfg: CfWorkerConfigurable, configurableUiProps: ConfigurableUiProps) {
        val value by cfg.stateFlow.collectAsState()
        val setValue = cfg::set
        val enabled = isConfigEnabled()
        var cfWorkerState by remember {
            mutableStateOf(null as CfWorkerEditState?)
        }
        val dismiss = {
            cfWorkerState = null
        }
        ConfigTemplate(
            modifier = configurableUiProps.modifier
                .clickable(
                    onClick = {
                        cfWorkerState = CfWorkerEditState(
                            settings = value,
                            setSettings = {
                                setValue(it)
                                dismiss()
                            }
                        )
                    }
                )
                .padding(configurableUiProps.itemPaddingValues),
            title = {
                TitleAndDescription(cfg, true)
            },
            value = {
                NextIcon()
            }
        )
        cfWorkerState?.let {
            CfWorkerEditDialog(it, onDismiss = dismiss)
        }
    }

    @Stable
    private class CfWorkerEditState(
        private val settings: CfWorkerSettings,
        private val setSettings: (CfWorkerSettings) -> Unit,
    ) {
        var enabled = mutableStateOf(settings.enabled)
        var url = mutableStateOf(settings.url)
        var secretKey = mutableStateOf(settings.secretKey)

        val canSave: Boolean by derivedStateOf {
            if (!enabled.value) true
            else url.value.isNotBlank() && secretKey.value.isNotBlank()
        }

        fun save() {
            if (!canSave) return
            setSettings(
                CfWorkerSettings(
                    enabled = enabled.value,
                    url = url.value.trim(),
                    secretKey = secretKey.value.trim(),
                )
            )
        }
    }

    @Composable
    private fun CfWorkerEditDialog(
        state: CfWorkerEditState?,
        onDismiss: () -> Unit,
    ) {
        val headerTitle = Res.string.settings_cf_worker.asStringSource()
        ConfigurableSheet(
            title = headerTitle,
            onDismiss = onDismiss,
            isOpened = state != null,
            content = {
                state?.let { state ->
                    val (enabled, setEnabled) = state.enabled
                    val (url, setUrl) = state.url
                    val (secretKey, setSecretKey) = state.secretKey

                    Column {
                        DialogConfigItem(
                            title = {
                                Row(
                                    modifier = Modifier.clickable { setEnabled(!enabled) }
                                ) {
                                    CheckBox(
                                        value = enabled,
                                        onValueChange = setEnabled,
                                        size = 16.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(myStringResource(Res.string.enabled))
                                }
                            },
                            value = {}
                        )

                        Spacer(Modifier.height(mySpacings.mediumSpace))

                        DialogConfigItem(
                            title = { Text(myStringResource(Res.string.settings_cf_worker_url)) },
                            value = {
                                MyTextField(
                                    text = url,
                                    onTextChange = setUrl,
                                    placeholder = "https://your-worker.workers.dev",
                                    enabled = enabled,
                                )
                            }
                        )

                        Spacer(Modifier.height(mySpacings.mediumSpace))

                        DialogConfigItem(
                            title = { Text(myStringResource(Res.string.settings_cf_worker_secret_key)) },
                            value = {
                                MyTextField(
                                    text = secretKey,
                                    onTextChange = setSecretKey,
                                    placeholder = myStringResource(Res.string.settings_cf_worker_secret_key_placeholder),
                                    enabled = enabled,
                                )
                            }
                        )

                        Spacer(Modifier.height(mySpacings.mediumSpace))

                        Row {
                            val btnModifier = Modifier.weight(1f)
                            ActionButton(
                                myStringResource(Res.string.change),
                                enabled = state.canSave,
                                modifier = btnModifier,
                                onClick = { state.save() }
                            )
                            Spacer(Modifier.width(mySpacings.mediumSpace))
                            ActionButton(
                                myStringResource(Res.string.cancel),
                                modifier = btnModifier,
                                onClick = onDismiss
                            )
                        }
                    }
                }
            }
        )
    }

    @Composable
    private fun DialogConfigItem(
        title: @Composable ColumnScope.() -> Unit,
        value: @Composable ColumnScope.() -> Unit,
    ) {
        Column {
            Column {
                title()
                Spacer(Modifier.height(8.dp))
                value()
            }
        }
    }
}
