package com.nothinglondon.sdkdemo

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.nothinglondon.sdkdemo.demos.animation.NotificationItem
import com.nothinglondon.sdkdemo.demos.animation.NotificationListener
import com.nothinglondon.sdkdemo.demos.animation.SettingsConstants.AUDIO_VISUALIZER_ENABLED_SETTING_KEY
import com.nothinglondon.sdkdemo.demos.animation.SettingsConstants.PRIMARY_TOY_SETTING_KEY
import com.nothinglondon.sdkdemo.demos.animation.SettingsConstants.SETTINGS_PREFERENCES_NAME
import com.nothinglondon.sdkdemo.ui.theme.NothingAndroidSDKDemoTheme

class MainActivity : ComponentActivity() {
    lateinit var sharedPreferences: SharedPreferences

    override fun onStart() {
        super.onStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(SETTINGS_PREFERENCES_NAME, MODE_PRIVATE)

        enableEdgeToEdge()
        setContent {
            NothingAndroidSDKDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val notifications by NotificationListener.notifications.collectAsState()
                    Column() {
                        Text(
                            text = "Settings",
                            modifier = Modifier.padding(innerPadding),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text("Primary Toy")
                        PrimaryToySelectRadioButton()
                        Text("Audio Visualizer Settings")
                        CheckboxBoolSetting("Show Audio Visualizer", AUDIO_VISUALIZER_ENABLED_SETTING_KEY, ::onValueChanged)

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Notifications", style = MaterialTheme.typography.titleMedium)

                        LazyColumn {
                            items(notifications) { notif ->
                                NotificationRow(notif)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun NotificationRow(item: NotificationItem?) {
        if (item == null) return
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)) {

            Text(
                text = "${item.title ?: ""}",
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = item.text ?: "",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    @Composable
    fun CheckboxBoolSetting(text: String, valueKey: String, actionOnChanged: (Boolean, String) -> Unit) {
        val (checkedState, onStateChange) = remember { mutableStateOf(sharedPreferences.getBoolean(valueKey, true)) }
        val interactionSource = remember { MutableInteractionSource() }
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checkedState,
                    onValueChange =
                        {
                            onStateChange(!checkedState)
                            actionOnChanged(!checkedState, valueKey)
                        },
                    role = Role.Checkbox,
                    indication = null,
                    interactionSource = interactionSource,
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = checkedState,
                interactionSource = interactionSource,
                onCheckedChange =
                    {
                        onStateChange(it)
                        actionOnChanged(it, valueKey)
                    },
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }

    @Preview
    @Composable
    fun PrimaryToySelectRadioButton() {
        val options = listOf(0, 1)
        val texts = listOf("Clock", "Game of Life")
        val selectedOption = remember { mutableStateOf(sharedPreferences.getInt(PRIMARY_TOY_SETTING_KEY, 0)) }
        Column {
            options.forEach { option ->
                Row (
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedOption.value == option,
                            onClick = {
                                selectedOption.value = option
                                onPrimaryToySelected(selectedOption.value)
                            }
                        )
                        .padding(horizontal = 16.dp)
                ) {
                    RadioButton(
                        selected = selectedOption.value == option,
                        onClick = {
                            selectedOption.value = option
                            onPrimaryToySelected(selectedOption.value)
                        }
                    )
                    Text(texts[option],
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 16.dp),)
                }
            }
        }
    }

    fun onValueChanged(newState: Boolean, valueKey: String) {
        sharedPreferences.edit { putBoolean(valueKey, newState) }
    }

    fun onPrimaryToySelected(newToy: Int) {
        sharedPreferences.edit { putInt(PRIMARY_TOY_SETTING_KEY, newToy) }
    }
}