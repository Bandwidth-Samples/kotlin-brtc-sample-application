package com.bandwidth.brtcsample

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bandwidth.brtcsample.ui.screen.CallScreen
import com.bandwidth.brtcsample.ui.screen.ConnectScreen
import com.bandwidth.brtcsample.ui.screen.ConnectingScreen
import com.bandwidth.brtcsample.viewmodel.CallViewModel
import com.bandwidth.brtcsample.viewmodel.ConnectionState

@Composable
fun MainContent(viewModel: CallViewModel = viewModel()) {
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
