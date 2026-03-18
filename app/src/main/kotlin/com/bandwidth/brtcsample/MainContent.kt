package com.bandwidth.brtcsample

import android.Manifest
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bandwidth.brtcsample.ui.screen.CallScreen
import com.bandwidth.brtcsample.ui.screen.ConnectScreen
import com.bandwidth.brtcsample.ui.screen.ConnectingScreen
import com.bandwidth.brtcsample.viewmodel.CallViewModel
import com.bandwidth.brtcsample.viewmodel.ConnectionState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainContent(viewModel: CallViewModel = viewModel()) {
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    if (!micPermission.status.isGranted) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (micPermission.status.shouldShowRationale)
                    "Microphone access is required to make and receive calls. Please grant the permission."
                else
                    "Microphone permission is needed to use this app.",
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { micPermission.launchPermissionRequest() }) {
                Text("Grant Microphone Permission")
            }
        }
        return
    }

    AnimatedContent(
        targetState = viewModel.connectionState,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "main"
    ) { state ->
        when (state) {
            ConnectionState.DISCONNECTED -> ConnectScreen(viewModel)
            ConnectionState.CONNECTING -> ConnectingScreen(viewModel)
            ConnectionState.CONNECTED,
            ConnectionState.RINGING,
            ConnectionState.IN_CALL -> CallScreen(viewModel)
        }
    }

    if (viewModel.showError) {
        AlertDialog(
            onDismissRequest = { viewModel.showError = false },
            title = { Text("Error") },
            text = { Text(viewModel.errorMessage) },
            confirmButton = {
                TextButton(onClick = { viewModel.showError = false }) {
                    Text("OK")
                }
            }
        )
    }
}
