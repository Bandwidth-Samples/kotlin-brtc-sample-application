package com.bandwidth.brtcsample.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneForwarded
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bandwidth.brtcsample.viewmodel.CallViewModel

@Composable
fun ConnectScreen(viewModel: CallViewModel) {
    var appeared by remember { mutableStateOf(false) }
    var isUrlFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.5f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "alpha"
    )

    LaunchedEffect(Unit) { appeared = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { focusManager.clearFocus() }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        Color(0x0F2196F3),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .then(if (!isUrlFocused) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (isUrlFocused) Arrangement.Center else Arrangement.Top
        ) {
            if (!isUrlFocused) {
                Spacer(Modifier.height(80.dp))

                // App icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(scale)
                        .alpha(alpha)
                        .shadow(16.dp, CircleShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF2196F3), Color(0xFF1976D2))
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Call,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Title
                Text(
                    "Bandwidth RTC",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alpha(alpha)
                )
                Text(
                    "Real-Time Communications",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(alpha)
                )

                Spacer(Modifier.height(36.dp))

                // Feature rows
                Column(
                    modifier = Modifier.alpha(alpha),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    FeatureRow(
                        icon = Icons.Filled.PhoneForwarded,
                        iconTint = Color(0xFF2196F3),
                        title = "WebRTC Calling",
                        description = "Make and receive calls over the internet with crystal-clear audio quality."
                    )
                    FeatureRow(
                        icon = Icons.Filled.History,
                        iconTint = Color(0xFFFF9800),
                        title = "Call History",
                        description = "Keep track of your recent calls with direction, duration, and timestamps."
                    )
                }

                Spacer(Modifier.height(36.dp))
            }

            // Server URL field
            OutlinedTextField(
                value = viewModel.serverURL,
                onValueChange = { viewModel.serverURL = it },
                label = { Text("Server URL") },
                placeholder = { Text("http://localhost:3000") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(alpha)
                    .onFocusChanged { isUrlFocused = it.isFocused },
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                supportingText = {
                    if (isUrlFocused) {
                        Text("Run `adb reverse tcp:3000 tcp:3000` to forward localhost to your device.")
                    }
                }
            )

            if (!isUrlFocused) {
                Spacer(Modifier.height(24.dp))

                // Connect button
                Button(
                    onClick = { viewModel.connect() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .alpha(alpha),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    )
                ) {
                    Icon(Icons.Filled.PhoneForwarded, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Connect", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }

                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconTint.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ConnectingScreen(viewModel: CallViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        Color(0x0F2196F3),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .shadow(16.dp, CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF2196F3), Color(0xFF1976D2))
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Call,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text("Bandwidth RTC", fontSize = 24.sp, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(8.dp))

            Text(
                viewModel.statusText,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))

            CircularProgressIndicator(color = Color(0xFF2196F3))
        }
    }
}
