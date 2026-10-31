package com.willg.superear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.willg.superear.ui.theme.SuperEarTheme

class MainActivity : ComponentActivity() {

    private lateinit var prefs: PreferencesManager

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* handled reactively by re-reading permission state in Compose */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)

        setContent {
            SuperEarTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SuperEarScreen(
                        prefs = prefs,
                        onRequestPermissions = { permissions ->
                            requestPermissions.launch(permissions)
                        }
                    )
                }
            }
        }
    }
}

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.RECORD_AUDIO)
    }

private fun hasAllPermissions(context: android.content.Context): Boolean =
    requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

@Composable
fun SuperEarScreen(
    prefs: PreferencesManager,
    onRequestPermissions: (Array<String>) -> Unit
) {
    val context = LocalContext.current
    val isRunning by AudioAmplifierService.isRunningState.collectAsState()

    var showDisclaimer by remember { mutableStateOf(!prefs.disclaimerAccepted) }
    var showHeadphoneWarning by remember { mutableStateOf(false) }
    var showPermissionDeniedHint by remember { mutableStateOf(false) }
    var gainPercent by remember { mutableIntStateOf(prefs.gainPercent) }
    var noiseReduction by remember { mutableStateOf(prefs.noiseReductionEnabled) }
    var headsetConnected by remember { mutableStateOf(HeadphoneUtil.isHeadsetConnected(context)) }

    // Keep headset-connected state live so the Start button enables itself
    // the instant the user plugs in headphones, without needing to re-open
    // the screen.
    DisposableEffect(Unit) {
        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                headsetConnected = HeadphoneUtil.isHeadsetConnected(context)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                headsetConnected = HeadphoneUtil.isHeadsetConnected(context)
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }

    fun startService() {
        val intent = Intent(context, AudioAmplifierService::class.java)
            .setAction(AudioAmplifierService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopService() {
        context.startService(
            Intent(context, AudioAmplifierService::class.java)
                .setAction(AudioAmplifierService.ACTION_STOP)
        )
    }

    fun onStartStopClicked() {
        if (isRunning) {
            stopService()
            return
        }
        if (!hasAllPermissions(context)) {
            onRequestPermissions(requiredPermissions())
            showPermissionDeniedHint = true
            return
        }
        if (!headsetConnected) {
            showHeadphoneWarning = true
            return
        }
        startService()
    }

    Scaffold(
        bottomBar = { BannerAd() }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(24.dp))

            if (!headsetConnected) {
                HeadphoneWarningCard()
                Spacer(Modifier.height(16.dp))
            }

            val statusText = if (isRunning) {
                stringResource(R.string.listening_status_active)
            } else {
                stringResource(R.string.listening_status_inactive)
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleLarge,
                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                }
            )

            Spacer(Modifier.height(16.dp))

            val startStopLabel = if (isRunning) {
                stringResource(R.string.stop_listening)
            } else {
                stringResource(R.string.start_listening)
            }
            Button(
                onClick = { onStartStopClicked() },
                modifier = Modifier
                    .size(width = 220.dp, height = 56.dp) // comfortably exceeds 48dp min touch target
                    .semantics {
                        contentDescription = String.format(
                            context.getString(R.string.cd_start_stop_button), startStopLabel
                        )
                    }
            ) {
                Text(startStopLabel, style = MaterialTheme.typography.labelLarge)
            }

            if (showPermissionDeniedHint && !hasAllPermissions(context)) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.mic_permission_denied),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.open_settings))
                }
            }

            Spacer(Modifier.height(32.dp))

            GainControl(
                gainPercent = gainPercent,
                onGainChange = { newValue ->
                    gainPercent = newValue
                    prefs.gainPercent = newValue
                    if (isRunning) {
                        context.startService(
                            Intent(context, AudioAmplifierService::class.java)
                                .setAction(AudioAmplifierService.ACTION_SET_GAIN)
                                .putExtra(AudioAmplifierService.EXTRA_GAIN_PERCENT, newValue)
                        )
                    }
                }
            )

            Spacer(Modifier.height(24.dp))

            NoiseReductionControl(
                enabled = noiseReduction,
                onToggle = { newValue ->
                    noiseReduction = newValue
                    prefs.noiseReductionEnabled = newValue
                    if (isRunning) {
                        context.startService(
                            Intent(context, AudioAmplifierService::class.java)
                                .setAction(AudioAmplifierService.ACTION_SET_NOISE_REDUCTION)
                                .putExtra(AudioAmplifierService.EXTRA_NOISE_REDUCTION, newValue)
                        )
                    }
                }
            )

            Spacer(Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.privacy_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }

    if (showDisclaimer) {
        DisclaimerDialog(onAccept = {
            prefs.disclaimerAccepted = true
            showDisclaimer = false
        })
    }

    if (showHeadphoneWarning) {
        AlertDialog(
            onDismissRequest = { showHeadphoneWarning = false },
            title = { Text(stringResource(R.string.headphones_required_title)) },
            text = { Text(stringResource(R.string.headphones_required_body)) },
            confirmButton = {
                Button(onClick = { showHeadphoneWarning = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
}

@Composable
private fun HeadphoneWarningCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null, // decorative; text below conveys the meaning
                tint = MaterialTheme.colorScheme.error
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = stringResource(R.string.headphones_required_title),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.headphones_required_body),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun GainControl(gainPercent: Int, onGainChange: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.gain_label),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "${gainPercent}%",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
        val context = LocalContext.current
        Slider(
            value = gainPercent.toFloat(),
            onValueChange = { onGainChange((it / 10).toInt() * 10) },
            valueRange = 100f..600f,
            steps = 49, // 10% increments across the 100-600 range
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = String.format(
                        context.getString(R.string.gain_value_desc), gainPercent
                    )
                }
        )
    }
}

@Composable
private fun NoiseReductionControl(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Reduce background noise"
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.padding(end = 12.dp)) {
            Text(
                text = stringResource(R.string.noise_reduction_label),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.noise_reduction_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun DisclaimerDialog(onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* must be explicitly accepted */ },
        title = { Text(stringResource(R.string.disclaimer_title)) },
        text = { Text(stringResource(R.string.disclaimer_body)) },
        confirmButton = {
            Button(onClick = onAccept) {
                Text(stringResource(R.string.disclaimer_accept))
            }
        }
    )
}
