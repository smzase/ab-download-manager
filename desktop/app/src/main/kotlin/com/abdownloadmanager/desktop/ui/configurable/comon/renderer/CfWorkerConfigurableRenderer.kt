package com.abdownloadmanager.desktop.ui.configurable.comon.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.abdownloadmanager.desktop.ui.configurable.ConfigTemplate
import com.abdownloadmanager.desktop.ui.configurable.TitleAndDescription
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
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myShapes
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
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
        ConfigTemplate(
            modifier = configurableUiProps.modifier.padding(configurableUiProps.itemPaddingValues),
            title = {
                TitleAndDescription(cfg, true)
            },
            value = {
                RenderChangeCfWorkerConfig(
                    settings = value,
                    setSettings = { setValue(it) }
                )
            },
        )
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
    fun RenderChangeCfWorkerConfig(
        settings: CfWorkerSettings,
        setSettings: (CfWorkerSettings) -> Unit,
    ) {
        var showDialog by remember { mutableStateOf(false) }
        ActionButton(
            myStringResource(Res.string.change),
            onClick = { showDialog = true },
        )
        if (showDialog) {
            val dismiss = { showDialog = false }
            val state = remember(setSettings) {
                CfWorkerEditState(
                    settings = settings,
                    setSettings = {
                        setSettings(it)
                        dismiss()
                    }
                )
            }
            CfWorkerEditDialog(state, onDismiss = dismiss)
        }
    }

    @Composable
    private fun CfWorkerEditDialog(
        state: CfWorkerEditState,
        onDismiss: () -> Unit,
    ) {
        Dialog(onDismissRequest = onDismiss) {
            val shape = myShapes.defaultRounded
            Column(
                modifier = Modifier
                    .clip(shape)
                    .border(2.dp, myColors.onBackground / 10, shape)
                    .background(myColors.surface)
                    .padding(16.dp)
                    .width(450.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        myStringResource(Res.string.settings_cf_worker),
                        fontSize = myTextSizes.lg,
                        fontWeight = FontWeight.Bold,
                    )
                    MyIcon(
                        MyIcons.windowClose,
                        myStringResource(Res.string.close),
                        Modifier
                            .clip(CircleShape)
                            .clickable { onDismiss() }
                            .padding(12.dp)
                            .size(12.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                
                val (enabled, setEnabled) = state.enabled
                val (url, setUrl) = state.url
                val (secretKey, setSecretKey) = state.secretKey

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
                
                Spacer(Modifier.height(12.dp))
                
                DialogConfigItem(
                    title = { Text(myStringResource(Res.string.settings_cf_worker_url)) },
                    value = {
                        MyTextField(
                            text = url,
                            onTextChange = setUrl,
                            placeholder = "https://your-worker.workers.dev",
                            modifier = Modifier.fillMaxWidth(),
                            enabled = enabled,
                        )
                    }
                )
                
                Spacer(Modifier.height(12.dp))
                
                DialogConfigItem(
                    title = { Text(myStringResource(Res.string.settings_cf_worker_secret_key)) },
                    value = {
                        MyTextField(
                            text = secretKey,
                            onTextChange = setSecretKey,
                            placeholder = myStringResource(Res.string.settings_cf_worker_secret_key_placeholder),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = enabled,
                        )
                    }
                )
                
                Spacer(Modifier.height(16.dp))
                
                Row(
                    Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionButton(
                        myStringResource(Res.string.change),
                        enabled = state.canSave,
                        onClick = { state.save() }
                    )
                    Spacer(Modifier.width(8.dp))
                    ActionButton(myStringResource(Res.string.cancel), onClick = onDismiss)
                }
            }
        }
    }

    @Composable
    private fun DialogConfigItem(
        title: @Composable ColumnScope.() -> Unit,
        value: @Composable ColumnScope.() -> Unit,
    ) {
        Column {
            Column(Modifier.height(IntrinsicSize.Max)) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start,
                ) {
                    title()
                }
                Spacer(Modifier.height(8.dp))
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.End,
                ) {
                    value()
                }
            }
        }
    }
}
