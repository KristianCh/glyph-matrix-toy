package com.nothinglondon.sdkdemo

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nothinglondon.sdkdemo.ui.theme.NothingAndroidSDKDemoTheme
import androidx.core.content.edit

class MainActivity : ComponentActivity() {
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(SETTINGS_PREFERENCES_NAME, MODE_PRIVATE)

        enableEdgeToEdge()
        setContent {
            NothingAndroidSDKDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column() {
                        Greeting(
                            name = "Settings",
                            modifier = Modifier.padding(innerPadding)
                        )
                        CheckboxBoolSetting("Show Audio Visualizer", AUDIO_VISUALIZER_ENABLED_SETTING_KEY, ::onChanged)
                    }
                }
            }
        }
    }


    @Composable
    fun Greeting(name: String, modifier: Modifier = Modifier) {
        Text(
            text = name,
            modifier = modifier
        )
    }

    @Preview(showBackground = true)
    @Composable
    fun GreetingPreview() {
        NothingAndroidSDKDemoTheme {
            Greeting("Android")
        }
    }

    @Composable
    fun CheckboxBoolSetting(text: String, valueKey: String, actionOnChanged: (Boolean, String) -> Unit) {
        val currentSavedState = sharedPreferences.getBoolean(valueKey, true)
        val (checkedState, onStateChange) = remember { mutableStateOf(currentSavedState) }
        val interactionSource = remember { MutableInteractionSource() }
        Row(
            Modifier.fillMaxWidth()
                .height(56.dp)
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

    fun onChanged(newState: Boolean, valueKey: String) {
        sharedPreferences.edit { putBoolean(valueKey, newState) }
    }


    private companion object {
        private const val SETTINGS_PREFERENCES_NAME = "SettingsPreferences"
        private const val AUDIO_VISUALIZER_ENABLED_SETTING_KEY = "AudioVisualizerEnabled"
    }
}