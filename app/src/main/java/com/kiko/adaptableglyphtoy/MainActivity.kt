package com.kiko.adaptableglyphtoy

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import android.Manifest
import android.app.Activity
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.kiko.adaptableglyphtoy.animation.NotificationItem
import com.kiko.adaptableglyphtoy.animation.NotificationListener
import com.kiko.adaptableglyphtoy.animation.SettingsConstants.AUDIO_VISUALIZER_ENABLED_SETTING_KEY
import com.kiko.adaptableglyphtoy.animation.SettingsConstants.AUDIO_VISUALIZER_ROTATION_SETTING_KEY
import com.kiko.adaptableglyphtoy.animation.SettingsConstants.NOTIFICATION_SCROLL_INCLUDE_BODY_SETTING_KEY
import com.kiko.adaptableglyphtoy.animation.SettingsConstants.NOTIFICATION_SCROLL_REPEAT_TIME_SETTING_KEY
import com.kiko.adaptableglyphtoy.animation.SettingsConstants.PRIMARY_TOY_SETTING_KEY
import com.kiko.adaptableglyphtoy.animation.SettingsConstants.SETTINGS_PREFERENCES_NAME
import com.kiko.adaptableglyphtoy.animation.SettingsConstants.SHOW_MEDIA_SCROLL_SETTING_KEY
import com.kiko.adaptableglyphtoy.animation.SettingsConstants.SHOW_NOTIFICATION_RING_SETTING_KEY
import com.kiko.adaptableglyphtoy.animation.SettingsConstants.SHOW_NOTIFICATION_SCROLL_SETTING_KEY
import com.kiko.adaptableglyphtoy.ui.theme.NothingAndroidSDKDemoTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    lateinit var sharedPreferences: SharedPreferences
    val notificationPermissionsGranted: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val audioPermissionsGranted: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override fun onStart() {
        super.onStart()
    }

    fun isNotificationServiceEnabled(context: Context): Boolean {
        val packageName = context.packageName
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val hasPerm = flat.split(":").any { it.contains(packageName) }
        notificationPermissionsGranted.value = hasPerm
        return hasPerm
    }

    fun hasAudioPermission(context: Context): Boolean {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        audioPermissionsGranted.value = hasPerm
        return hasPerm
    }

    fun requestNotificationAccess(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        context.startActivity(intent)
    }

    fun shouldShowAudioRationale(activity: Activity): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.RECORD_AUDIO
        )
    }

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startService(Intent(this, MainActivity::class.java))
            audioPermissionsGranted.value = true
        } else {
            // Show explanation to user
        }
    }

    override fun onResume() {
        super.onResume()

        audioPermissionsGranted.value = hasAudioPermission(this)
        notificationPermissionsGranted.value = isNotificationServiceEnabled(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!hasAudioPermission(this)) {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        sharedPreferences = getSharedPreferences(SETTINGS_PREFERENCES_NAME, MODE_PRIVATE)

        enableEdgeToEdge()
        setContent {
            NothingAndroidSDKDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                    ) {
                    Text(
                        text = "Settings",
                        modifier = Modifier.padding(innerPadding),
                        style = MaterialTheme.typography.titleLarge,
                    )
                        Text("Primary Toy", style = MaterialTheme.typography.titleMedium)
                        PrimaryToySelectRadioButton()
                        Text(
                            "Audio Visualizer Settings",
                            style = MaterialTheme.typography.titleMedium
                        )
                        val audioPermGranted by audioPermissionsGranted.collectAsState()
                        if (audioPermGranted) {
                            var showAudioVisualizer by remember { mutableStateOf(sharedPreferences.getBoolean(AUDIO_VISUALIZER_ENABLED_SETTING_KEY, false)) }
                            SwitchSetting(
                                "Show Audio Visualizer",
                                AUDIO_VISUALIZER_ENABLED_SETTING_KEY,
                            ) { newValue, key ->
                                showAudioVisualizer = newValue
                                onBooleanValueChanged(newValue, key)
                            }

                            AnimatedVisibility(showAudioVisualizer) {
                                Column {
                                    Text(
                                        text = "Rotation Type",
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(start = 16.dp),
                                    )
                                    AudioVisualizerRotationTypeSelectRadioButton()
                                }
                            }
                        }
                        else {
                            Button(onClick = {
                                if (shouldShowAudioRationale(this@MainActivity)) { requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO) }
                                else { openAppSettings(this@MainActivity) }
                            }) {
                                Text(text = "Enabled permission", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        val notifPermGranted by notificationPermissionsGranted.collectAsState()
                        if (notifPermGranted) {
                            SwitchSetting(
                                "Show Active Media Scrolling Text",
                                SHOW_MEDIA_SCROLL_SETTING_KEY,
                                ::onBooleanValueChanged
                            )

                        }
                        else {
                            Button(onClick = {
                                requestNotificationAccess(this@MainActivity)
                            }) {
                                Text(text = "Enabled permission for app in settings", style = MaterialTheme.typography.bodyLarge)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "Notification Settings",
                            style = MaterialTheme.typography.titleMedium
                        )


                        if (notifPermGranted) {
                            val notifications by NotificationListener.notifications.collectAsState()
                            Text(
                                text = "    Currently active notifications: ${notifications.size}",
                                style = MaterialTheme.typography.bodyLarge
                            )

                            SwitchSetting(
                                "Show Notification Ring",
                                SHOW_NOTIFICATION_RING_SETTING_KEY,
                                ::onBooleanValueChanged
                            )

                            var showNotificationScroll by remember { mutableStateOf(sharedPreferences.getBoolean(SHOW_NOTIFICATION_SCROLL_SETTING_KEY, false)) }
                            SwitchSetting(
                                "Show Scrolling Notification Text",
                                SHOW_NOTIFICATION_SCROLL_SETTING_KEY,
                            ) { newValue, key ->
                                showNotificationScroll = newValue
                                onBooleanValueChanged(newValue, key)
                            }

                            AnimatedVisibility(showNotificationScroll) {
                                Column {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    var sliderValue by remember {
                                        mutableFloatStateOf(
                                            sharedPreferences.getInt(
                                                NOTIFICATION_SCROLL_REPEAT_TIME_SETTING_KEY,
                                                0
                                            ).toFloat()
                                        )
                                    }
                                    Text(
                                        text = "Repeat Scroll every 5-60 seconds (0 for no repeat): ${sliderValue.roundToInt()}s",
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(start = 16.dp),
                                    )
                                    Slider(
                                        value = sliderValue,
                                        onValueChange = { newValue ->
                                            sliderValue = newValue
                                            onIntValueChanged(
                                                newValue.roundToInt(),
                                                NOTIFICATION_SCROLL_REPEAT_TIME_SETTING_KEY
                                            )
                                        },
                                        valueRange = 0f..60f,
                                        steps = 11,
                                        modifier = Modifier.padding(32.dp, 0.dp)
                                    )

                                    SwitchSetting(
                                        "Include Notification Body in Scrolling Notification",
                                        NOTIFICATION_SCROLL_INCLUDE_BODY_SETTING_KEY,
                                        ::onBooleanValueChanged
                                    )

                                    Spacer(modifier = Modifier.height(32.dp))
                                    /*LazyColumn {
                                        items(notifications) { notif ->
                                            NotificationRow(notif)
                                        }
                                    }*/
                                }
                            }
                        }
                        else {
                            Button(onClick = {
                                requestNotificationAccess(this@MainActivity)
                            }) {
                                Text(text = "Enabled permission for app in settings", style = MaterialTheme.typography.bodyLarge)
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
            Text(text = item.title ?: "", style = MaterialTheme.typography.bodyLarge)
            Text(text = item.text ?: "", style = MaterialTheme.typography.bodyMedium)
        }
    }

    @Composable
    fun SwitchSetting(text: String, valueKey: String, actionOnChanged: (Boolean, String) -> Unit) {
        val (checkedState, onStateChange) = remember { mutableStateOf(sharedPreferences.getBoolean(valueKey, false)) }
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
            Switch(
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
        SelectRadioButton(options, texts, PRIMARY_TOY_SETTING_KEY)
    }

    @Preview
    @Composable
    fun AudioVisualizerRotationTypeSelectRadioButton() {
        val options = listOf(0, 1, 2)
        val texts = listOf("Axis", "Full", "None")
        SelectRadioButton(options, texts, AUDIO_VISUALIZER_ROTATION_SETTING_KEY)
    }

    @Composable
    fun SelectRadioButton(options: List<Int>, texts: List<String>, valueKey: String) {
        val selectedOption = remember { mutableStateOf(sharedPreferences.getInt(valueKey, 0)) }
        Column {
            options.forEach { option ->
                Row (
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedOption.value == option,
                            onClick = {
                                selectedOption.value = option
                                onIntValueChanged(selectedOption.value, valueKey)
                            }
                        )
                        .padding(horizontal = 16.dp)
                ) {
                    RadioButton(
                        selected = selectedOption.value == option,
                        onClick = {
                            selectedOption.value = option
                            onIntValueChanged(selectedOption.value, valueKey)
                        }
                    )
                    Text(texts[option],
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 16.dp),)
                }
            }
        }
    }

    fun onBooleanValueChanged(newState: Boolean, valueKey: String) {
        sharedPreferences.edit { putBoolean(valueKey, newState) }
    }

    fun onIntValueChanged(newValue: Int, valueKey: String) {
        sharedPreferences.edit { putInt(valueKey, newValue) }
    }
}