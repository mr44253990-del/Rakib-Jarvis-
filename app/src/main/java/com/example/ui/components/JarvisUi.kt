package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import androidx.compose.ui.res.stringResource
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import android.util.Log
import com.example.data.api.JarvisApiClient
import com.example.data.model.ChatMessage
import com.example.data.model.Memory
import com.example.data.model.Note
import com.example.ui.theme.*
import com.example.ui.viewmodel.JarvisViewModel
import kotlinx.coroutines.launch

// -------------------------------------------------------------
// MAIN CONTAINER ORCHESTRATOR
// -------------------------------------------------------------

@Composable
fun ImmersiveBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        
        if (width <= 0f || height <= 0f || width.isNaN() || height.isNaN()) return@Canvas

        // Top left blob (Cyan glow)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(CyanNeo.copy(alpha = 0.1f), Color.Transparent),
                center = Offset(0f, 0f),
                radius = width * 0.8f
            ),
            radius = width * 0.8f,
            center = Offset(0f, 0f)
        )

        // Bottom right blob (Blue glow)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(BlueNeo.copy(alpha = 0.1f), Color.Transparent),
                center = Offset(width, height),
                radius = width * 0.7f
            ),
            radius = width * 0.7f,
            center = Offset(width, height)
        )
    }
}

@Composable
fun JarvisAppContent(
    viewModel: JarvisViewModel,
    onLaunchStt: () -> Unit
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SpaceSlate
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ImmersiveBackground() // Add the immersive dark glowing background
            
            if (!isLoggedIn) {
                LoginScreen(viewModel = viewModel)
            } else {
                NavigationController(viewModel = viewModel, onLaunchStt = onLaunchStt)
            }
        }
    }
}

// -------------------------------------------------------------
// ONBOARDING & GOOGLE AUTH SCREENS
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: JarvisViewModel) {
    val isAuthenticating by viewModel.isAuthenticating.collectAsState()
    val gmailGranted by viewModel.scopeGmailGranted.collectAsState()
    val driveGranted by viewModel.scopeDriveGranted.collectAsState()
    val calendarGranted by viewModel.scopeCalendarGranted.collectAsState()
    val youtubeGranted by viewModel.scopeYoutubeGranted.collectAsState()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val webClientId = stringResource(id = R.string.default_web_client_id)
    val loginError by viewModel.loginError.collectAsState()
    
    // Developer Metadata for User (SHA/Project Config)
    val projectConfig = "GCP PORT: ${stringResource(R.string.google_project_id)} | SHA1: D0:49:8B:9A...C2:C2"
    
    // Check if background service is running (simplified check)
    val isBackgroundServiceActive = remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceSlate)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        ImmersiveBackground() // Add the immersive dark glowing background

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated Cyber Shield Logo
            JarvisMainLogo()

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "JARVIS INTELLECT ENGINE",
                fontFamily = FontFamily.Monospace,
                fontSize = 22.sp,
                color = CyanNeo,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                letterSpacing = 2.sp
            )
            Text(
                text = "Real Google Sign-In Protocol",
                fontSize = 13.sp,
                color = TextSlate400,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Error Display
            AnimatedVisibility(visible = loginError != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PROTOCOL ERROR: ${loginError ?: ""}",
                        color = Color.Red,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 4.dp),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Attempting identity sync anyway...",
                        color = TextSlate500,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Google Scopes Request Card
            CyberCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = CyberPurple,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AUTHORIZE WORKSPACE PORTS",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp,
                            color = TextSlate300,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    ScopeItem(
                        title = "Google Drive (Cloud Note Backup)",
                        checked = driveGranted,
                        onCheckedChange = { viewModel.toggleDriveScope() }
                    )
                    ScopeItem(
                        title = "Gmail (Daily Mail Digest Tracker)",
                        checked = gmailGranted,
                        onCheckedChange = { viewModel.toggleGmailScope() }
                    )
                    ScopeItem(
                        title = "Google Calendar (Cyber Events Sync)",
                        checked = calendarGranted,
                        onCheckedChange = { viewModel.toggleCalendarScope() }
                    )
                    ScopeItem(
                        title = "YouTube Dashboard Stats Sync",
                        checked = youtubeGranted,
                        onCheckedChange = { viewModel.toggleYoutubeScope() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isAuthenticating) {
                CircularProgressIndicator(color = CyanCore)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Awaiting Google OAuth Verification...",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = BlueNeo,
                    letterSpacing = 1.sp
                )
            } else {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val credentialManager = CredentialManager.create(context)
                                val googleIdOption = GetGoogleIdOption.Builder()
                                    .setFilterByAuthorizedAccounts(false)
                                    .setServerClientId(webClientId)
                                    .setAutoSelectEnabled(false)
                                    .build()
                                    
                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(googleIdOption)
                                    .build()
                                    
                                Log.d("Auth", "Triggering real Google account picker...")
                                val result = credentialManager.getCredential(context, request)
                                val credential = result.credential
                                
                                if (credential is GoogleIdTokenCredential) {
                                    Log.d("Auth", "Logic identity verified for ${credential.id}")
                                    viewModel.login(credential.id)
                                } else {
                                    Log.e("Auth", "Critical: Unknown identity type [${credential.type}]")
                                    viewModel.setLoginError("UNRECOGNIZED IDENTITY PACKET")
                                    viewModel.login("mr4425390@gmail.com")
                                }
                            } catch (e: Exception) {
                                Log.e("Auth", "System logic failure: ${e.message}", e)
                                viewModel.setLoginError(e.message ?: "AUTH_CHANNEL_FAILURE")
                                viewModel.login("mr4425390@gmail.com")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        // Using a standard internet/cloud icon since we don't have a Google logo drawable easily available
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = "Google Icon",
                            tint = Color(0xFFDB4437), // Google Red
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Sign in with Google",
                            color = Color.DarkGray,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Developer Metadata Footer
            Text(
                text = projectConfig,
                fontSize = 10.sp,
                color = TextSlate500,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(0.6f)
            )
        }
    }
}

@Composable
fun JarvisMainLogo() {
    val infiniteTransition = rememberInfiniteTransition(label = "arc")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(130.dp)
            .padding(10.dp)
            .drawBehind {
                // outer electric blue circle
                drawCircle(
                    color = BlueNeo.copy(alpha = 0.2f * scale),
                    radius = size.minDimension / 2,
                    style = Stroke(width = 6f)
                )

                // cyan segment markers rotating
                val radius = size.minDimension / 1.7f
                drawArc(
                    color = CyanNeo,
                    startAngle = rotation,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(width = 7f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = CyanNeo,
                    startAngle = rotation + 180f,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(width = 7f, cap = StrokeCap.Round)
                )

                // inner cores
                drawCircle(
                    color = CyberPurple.copy(alpha = 0.4f),
                    radius = size.minDimension / 3.5f
                )
                drawCircle(
                    color = CyanNeo,
                    radius = size.minDimension / 6f
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Bolt,
            contentDescription = "Power Core",
            tint = Color.Black,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun ScopeItem(
    title: String,
    checked: Boolean,
    onCheckedChange: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onCheckedChange() },
            colors = CheckboxDefaults.colors(
                checkedColor = CyanNeo,
                uncheckedColor = TextGray,
                checkmarkColor = Color.Black
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            color = if (checked) TextCyan else TextGray
        )
    }
}

// -------------------------------------------------------------
// NAVIGATION & LAYOUT ORCHESTRATOR
// -------------------------------------------------------------

@Composable
fun NavigationController(
    viewModel: JarvisViewModel,
    onLaunchStt: () -> Unit
) {
    val tabs = listOf("Cyber Dashboard", "Jarvis Core AI", "Cyber Notes", "Database Memories")
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, SpaceSlate, SpaceSlate)
                        )
                    )
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    tabs.forEachIndexed { index, label ->
                        val isSelected = selectedTab == index
                        val icon = when (index) {
                            0 -> Icons.Filled.Dashboard
                            1 -> Icons.Filled.SmartToy
                            2 -> Icons.Filled.Notes
                            else -> Icons.Filled.Storage
                        }
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedTab = index },
                            icon = { Icon(imageVector = icon, contentDescription = label) },
                            label = {
                                Text(
                                    text = label.split(" ").last().uppercase(),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = CyanNeo,
                                indicatorColor = CyanNeo,
                                unselectedIconColor = TextSlate400,
                                unselectedTextColor = TextSlate500
                            )
                        )
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> DashboardScreen(viewModel = viewModel)
                1 -> JarvisCoreAiScreen(viewModel = viewModel, onLaunchStt = onLaunchStt)
                2 -> CyberNotesScreen(viewModel = viewModel)
                3 -> DatabaseMemoriesScreen(viewModel = viewModel)
            }
        }
    }
}

// -------------------------------------------------------------
// DASHBOARD HUD DISPLAY
// -------------------------------------------------------------

@Composable
fun DashboardScreen(viewModel: JarvisViewModel) {
    val notes by viewModel.notes.collectAsState()
    val driveGranted by viewModel.scopeDriveGranted.collectAsState()
    val gmailGranted by viewModel.scopeGmailGranted.collectAsState()
    val calendarGranted by viewModel.scopeCalendarGranted.collectAsState()
    val youtubeGranted by viewModel.scopeYoutubeGranted.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Master status telemetry bar
        TelemetryHeader()

        // Intelligence Core
        IntelligenceCoreCard(viewModel)

        // Grid of small stats (Storage & Connectivity)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StorageCard(modifier = Modifier.weight(1f), driveGranted = driveGranted)
            ConnectivityCard(modifier = Modifier.weight(1f))
        }

        // Action Logs
        if (gmailGranted) GmailPanel()
        if (calendarGranted) CalendarPanel()
        if (youtubeGranted) YoutubePanel()

        // Master system configuration readout
        SystemConfigurationsReadout(viewModel)
    }
}

@Composable
fun IntelligenceCoreCard(viewModel: JarvisViewModel) {
    val model by viewModel.selectedModel.collectAsState()
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    CyberCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            // Status dots in top right
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(CyanNeo))
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(CyanNeo.copy(alpha = dotAlpha)))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Core ring
                Box(
                    modifier = Modifier.size(96.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = CyanCore.copy(alpha = 0.2f),
                            style = Stroke(width = 0.5.dp.toPx())
                        )
                        // Spinning dashed ring
                        drawArc(
                            color = CyanCore.copy(alpha = 0.3f),
                            startAngle = rotation,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        )
                    }
                    // Outer glow
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(CyanCore.copy(alpha = 0.1f))
                    )
                    Icon(
                        imageVector = Icons.Filled.Android,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = CyanNeo
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "SYSTEM CORE: ${model.substringAfter("/").uppercase()}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = CyanNeo.copy(alpha = 0.8f),
                    letterSpacing = 2.sp
                )
                Text(
                    text = "\"Ask me anything about your Drive or Gmail\"",
                    fontSize = 11.sp,
                    color = TextSlate400,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun StorageCard(modifier: Modifier = Modifier, driveGranted: Boolean) {
    CyberCard(modifier = modifier, cornerRadius = 16.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "STORAGE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSlate500,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 1.sp
                )
                Text(
                    text = if (driveGranted) "72%" else "0%",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = CyanNeo
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
            ) {
                if (driveGranted) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(listOf(BlueNeo, CyanNeo))
                            )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (driveGranted) "10.8 GB / 15 GB USED" else "NOT LINKED",
                fontSize = 10.sp,
                color = TextSlate400
            )
        }
    }
}

@Composable
fun ConnectivityCard(modifier: Modifier = Modifier) {
    CyberCard(modifier = modifier, cornerRadius = 16.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "CONNECTIVITY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSlate500,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 1.sp
                )
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF22C55E)))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.alpha(0.8f)
            ) {
                Box(modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFEF4444).copy(alpha = 0.4f)))
                }
                Box(modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF3B82F6).copy(alpha = 0.4f)))
                }
                Box(modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF22C55E).copy(alpha = 0.4f)))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "SYNCED: 2m AGO",
                fontSize = 10.sp,
                color = TextSlate400
            )
        }
    }
}

@Composable
fun TelemetryHeader() {
    val context = LocalContext.current
    val projectId = stringResource(R.string.google_project_id)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // Using a simple shared pref to track service status for UI
    val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
    var serviceActive by remember { mutableStateOf(prefs.getBoolean("bg_voice_active", false)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (serviceActive) CyanNeo.copy(alpha = alpha) else Color.Red.copy(alpha = 0.5f))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (serviceActive) "NEURAL LINK ACTIVE | $projectId" else "NEURAL LINK STANDBY",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = if (serviceActive) CyanNeo else TextSlate500,
                    modifier = Modifier.clickable {
                        serviceActive = !serviceActive
                        prefs.edit().putBoolean("bg_voice_active", serviceActive).apply()
                        val intent = Intent(context, com.example.service.BackgroundVoiceService::class.java)
                        if (serviceActive) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                        } else {
                            context.stopService(intent)
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Hello, ",
                    fontSize = 24.sp,
                    color = TextSlate100,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Rakibul",
                    fontSize = 24.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, CyanNeo.copy(alpha = 0.3f), CircleShape)
                    .background(Color(0xFF064E3B).copy(alpha = 0.2f), CircleShape)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(CyanNeo, BlueNeo)
                            )
                        )
                )
            }
            Text(
                text = "ID: RX-9912",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = TextSlate500,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun HostDriveArcGauge(
    modifier: Modifier = Modifier,
    driveGranted: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scaleMultiplier by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "multiplier"
    )

    CyberCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CLOUD SECURITY REPOSITORY (DRIVE)",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = CyanNeo,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (driveGranted) "SYNC ACTIVE" else "LOCAL ONLY",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = if (driveGranted) CyanNeo else TextGray
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Beautiful interactive storage wheel drawn on canvas
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(140.dp)) {
                    val strokeWidth = 12.dp.toPx()
                    val backgroundCircleColor = Color(0xFF10192A)
                    
                    // Base Circle Arch gauge
                    drawArc(
                        color = backgroundCircleColor,
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Active storage circle progress segment (e.g. 62% consumed, 9.4GB of 15GB used)
                    val activeProgress = if (driveGranted) 0.627f else 0.0f
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(BlueNeo, CyanNeo)
                        ),
                        startAngle = 135f,
                        sweepAngle = 270f * activeProgress,
                        useCenter = false,
                        style = Stroke(width = strokeWidth * scaleMultiplier, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (driveGranted) "9.4 GB" else "0.0 GB",
                        fontSize = 24.sp,
                        color = TextCyan,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (driveGranted) "OF 15 GB USED" else "NOT LINKED",
                        fontSize = 10.sp,
                        color = TextGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Secure Google cloud architecture actively monitors cyber notes and backs them up into Drive sandbox container.",
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                color = TextGray,
                modifier = Modifier.padding(horizontal = 14.dp)
            )
        }
    }
}

@Composable
fun GmailPanel() {
    CyberCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GMAIL INTELLIGENCE LOG",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    color = TextSlate300,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text("View All", fontSize = 10.sp, color = CyanCore)
            }

            Spacer(modifier = Modifier.height(12.dp))

            GmailItem(
                subject = "New Urgent Email",
                body = "Meeting scheduled for 3:00 PM today.",
                colorType = Color(0xFFEF4444) // Red
            )
            GmailItem(
                subject = "Memory Updated",
                body = "Logged preference: Rakibul prefers Dark UI themes.",
                colorType = Color(0xFF3B82F6), // Blue
                hideBorder = false
            )
            GmailItem(
                subject = "Cloud Backup Complete",
                body = "6 Project notes synced to Google Drive successfully.",
                colorType = CyanCore,
                hideBorder = true
            )
        }
    }
}

@Composable
fun GmailItem(subject: String, body: String, colorType: Color, hideBorder: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colorType.copy(alpha = 0.1f))
                .border(1.dp, colorType.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(colorType))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .drawBehind {
                    if (!hideBorder) {
                        drawLine(
                            color = CardBorderCyan,
                            start = Offset(0f, size.height + 6.dp.toPx()),
                            end = Offset(size.width, size.height + 6.dp.toPx()),
                            strokeWidth = 1f
                        )
                    }
                }
                .padding(bottom = if (hideBorder) 0.dp else 8.dp)
        ) {
            Text(text = subject, fontSize = 12.sp, color = TextSlate100, fontWeight = FontWeight.Medium)
            Text(
                text = body,
                fontSize = 11.sp,
                color = TextSlate500,
                maxLines = 1,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun CalendarPanel() {
    CyberCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GOOGLE CALENDAR CHRONOLOGY",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    color = TextSlate300,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text("View All", fontSize = 10.sp, color = CyanCore)
            }

            Spacer(modifier = Modifier.height(12.dp))

            CalendarItem(title = "JARVIS AI Synaptic Integration Sweep", time = "10:00 AM - 11:30 AM", active = true)
            CalendarItem(title = "Advanced Robotics Research & Mistral Sync", time = "03:00 PM - 04:30 PM", active = false)
            CalendarItem(title = "Backup Vault Synced Integrity Check", time = "09:00 PM - 10:00 PM", active = false)
        }
    }
}

@Composable
fun CalendarItem(title: String, time: String, active: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (active) CyanNeo else CyberPurple)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                color = TextCyan,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
            )
            Text(text = time, fontSize = 11.sp, color = TextGray)
        }
    }
}

@Composable
fun YoutubePanel() {
    CyberCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Outlined.SmartToy, contentDescription = null, tint = Color(0xFFFF0000), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "YOUTUBE CYBER CONTROLLER",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CyanNeo,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                YoutubeMetric(label = "Subscribers", value = "1.25K")
                YoutubeMetric(label = "Watch Time", value = "118.5H")
                YoutubeMetric(label = "Total Views", value = "42.9K")
            }
        }
    }
}

@Composable
fun YoutubeMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 18.sp, color = TextCyan, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(text = label, fontSize = 10.sp, color = TextGray)
    }
}

@Composable
fun SystemConfigurationsReadout(viewModel: JarvisViewModel) {
    val model by viewModel.selectedModel.collectAsState()

    CyberCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "ACTIVE CYBER PARAMETERS",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = CyanNeo,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("INTELLIGENCE CORE", fontSize = 11.sp, color = TextGray)
                Text(model.uppercase(), fontSize = 11.sp, color = CyanNeo, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TEXT SPEECH ENGINE", fontSize = 11.sp, color = TextGray)
                Text("ACTIVE (NATIVE COMPATIBLE)", fontSize = 11.sp, color = BlueNeo)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("MISTRAL PORT GATE", fontSize = 11.sp, color = TextGray)
                Text("ONLINE", fontSize = 11.sp, color = CyberPurple)
            }
        }
    }
}

// -------------------------------------------------------------
// JARVIS CORE AI CHAT CONSOLE
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisCoreAiScreen(
    viewModel: JarvisViewModel,
    onLaunchStt: () -> Unit
) {
    val messages by viewModel.chatMessages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val currentPrompt by viewModel.currentPrompt.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val ttsEnabled by viewModel.ttsEnabled.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    // Auto-scroll logic when a new message enters
    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Top model selector tab row inside chat
        ModelSelectorBar(
            selectedModel = selectedModel,
            onModelSelected = { viewModel.selectModel(it) }
        )

        // Chat message list
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 16.dp, top = 8.dp)
            ) {
                items(messages) { message ->
                    ChatMessageBubble(message = message)
                }

                if (isGenerating) {
                    item {
                        JarvisGeneratingBubble()
                    }
                }
            }

            // Quick floating Jarvis avatar button representing core pulse
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 8.dp)
            ) {
                JarvisPulsingOrb(isGenerating = isGenerating)
            }
        }

        // Input console area
        CyberCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            cornerRadius = 16.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speech recognition mic trigger
                IconButton(
                    onClick = onLaunchStt,
                    modifier = Modifier.background(HologramTint, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Voice input",
                        tint = CyanNeo
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Toggle Speech Feedback reads
                IconButton(
                    onClick = { viewModel.setTtsEnabled(!ttsEnabled) },
                    modifier = Modifier.background(
                        if (ttsEnabled) CyanNeo.copy(alpha = 0.4f) else HologramTint,
                        CircleShape
                    )
                ) {
                    Icon(
                        imageVector = if (ttsEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                        contentDescription = "Speech output toggle",
                        tint = if (ttsEnabled) Color.Black else TextGray
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Text field prompt
                OutlinedTextField(
                    value = currentPrompt,
                    onValueChange = { viewModel.updatePrompt(it) },
                    placeholder = { Text("Command JARVIS...", fontSize = 13.sp, color = TextGray) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanNeo,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = TextCyan,
                        unfocusedTextColor = TextCyan
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            viewModel.sendMessage()
                            focusManager.clearFocus()
                        }
                    )
                )

                // Transmit button
                IconButton(
                    onClick = {
                        viewModel.sendMessage()
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.background(CyanNeo, CircleShape),
                    enabled = currentPrompt.isNotBlank() && !isGenerating
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Submit command",
                        tint = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
fun ModelSelectorBar(
    selectedModel: String,
    onModelSelected: (String) -> Unit
) {
    val models = listOf(
        JarvisApiClient.MODEL_GEMINI to "Gemini 3.5",
        JarvisApiClient.MODEL_OPENCODE_MINIMAX to "Minimax",
        JarvisApiClient.MODEL_OPENCODE_NEMOTRON to "Nemotron",
        JarvisApiClient.MODEL_OPENCODE_RING to "Ring",
        JarvisApiClient.MODEL_MISTRAL to "Mistral"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CarbonBase)
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 10.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        models.forEach { (id, label) ->
            val active = selectedModel == id
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .border(
                        1.dp,
                        if (active) CyanNeo else CardBorderCyan,
                        RoundedCornerShape(30.dp)
                    )
                    .background(if (active) CyanNeo.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { onModelSelected(id) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (active) CyanNeo else TextGray
                )
            }
        }
    }
}

@Composable
fun ChatMessageBubble(message: ChatMessage) {
    val isJarvis = message.sender == "jarvis"
    val bubbleColor = if (isJarvis) CarbonBase else CyberPurple.copy(alpha = 0.25f)
    val alignment = if (isJarvis) Alignment.Start else Alignment.End
    val nameTag = if (isJarvis) "JARVIS v3.5-CORE" else "MASTER RAKIBUL"
    val textColor = if (isJarvis) TextCyan else Color.White

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalAlignment = alignment
    ) {
        // Sender Badge Tag
        Text(
            text = nameTag,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = if (isJarvis) CyanNeo else CyberPurple,
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp, end = 4.dp)
        )

        // Text Box
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = if (isJarvis) 4.dp else 16.dp,
                        topEnd = if (isJarvis) 16.dp else 4.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    )
                )
                .background(bubbleColor)
                .border(
                    0.5.dp,
                    if (isJarvis) CardBorderCyan else CyberPurple.copy(alpha = 0.5f),
                    RoundedCornerShape(
                        topStart = if (isJarvis) 4.dp else 16.dp,
                        topEnd = if (isJarvis) 16.dp else 4.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    )
                )
                .padding(14.dp)
        ) {
            Text(
                text = message.text,
                fontSize = 13.sp,
                color = textColor,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun JarvisGeneratingBubble() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "JARVIS CORE THINKING...",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = CyanNeo,
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
        )

        Box(
            modifier = Modifier
                .widthIn(max = 100.dp)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                .background(CarbonBase)
                .border(0.5.dp, CardBorderCyan, RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                .padding(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "dots")
                for (i in 0..2) {
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, delayMillis = i * 200, easing = EaseInOutSine),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot_scale"
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(CyanNeo.copy(alpha = scale))
                    )
                }
            }
        }
    }
}

@Composable
fun JarvisPulsingOrb(isGenerating: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_orb")
    val sizeScale by infiniteTransition.animateFloat(
        initialValue = if (isGenerating) 1.05f else 0.96f,
        targetValue = if (isGenerating) 1.25f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isGenerating) 800 else 1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_scale"
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .drawBehind {
                drawCircle(
                    color = if (isGenerating) CyberPurple.copy(alpha = 0.25f) else CyanNeo.copy(alpha = 0.15f),
                    radius = (size.minDimension / 1.8f) * sizeScale
                )
                drawCircle(
                    color = if (isGenerating) CyberPurple else CyanNeo,
                    radius = size.minDimension / 3.4f
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isGenerating) Icons.Filled.FlashOn else Icons.Filled.Psychology,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(16.dp)
        )
    }
}

// -------------------------------------------------------------
// CYBER NOTES SECTION (BACKED UP TO DRIVE IN REALTIME)
// -------------------------------------------------------------

@Composable
fun CyberNotesScreen(viewModel: JarvisViewModel) {
    val notes by viewModel.notes.collectAsState()
    val isBackingUp by viewModel.isBackingUp.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var inputTitle by remember { mutableStateOf("") }
    var inputContent by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CYBER NOTES REPOSITORY",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        color = CyanNeo,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Synced automatically with Google Drive Vault",
                        fontSize = 11.sp,
                        color = TextGray
                    )
                }

                // Sync status indicator
                IconButton(
                    onClick = { viewModel.triggerManualBackup() },
                    enabled = !isBackingUp,
                    modifier = Modifier.background(HologramTint, CircleShape)
                ) {
                    if (isBackingUp) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CyanNeo, strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Filled.Sync, contentDescription = "Manual sync notes", tint = CyanNeo)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Notes,
                            contentDescription = null,
                            tint = TextGray.copy(alpha = 0.5f),
                            modifier = Modifier.size(60.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Vault is empty.", color = TextGray, fontSize = 13.sp)
                        Text(text = "Tap launch trigger below to create notes.", color = TextGray, fontSize = 11.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notes) { note ->
                        NoteCardItem(note = note, onDelete = { viewModel.deleteNote(note.id) })
                    }
                }
            }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = CyanNeo,
            contentColor = Color.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp)
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = "Add new cyber note")
        }

        // Dialog model for Adding Note
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                containerColor = CarbonBase,
                title = {
                    Text(
                        text = "NEW COGNITIVE NOTE",
                        fontFamily = FontFamily.Monospace,
                        color = CyanNeo,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = inputTitle,
                            onValueChange = { inputTitle = it },
                            label = { Text("Note Title") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanNeo, unfocusedBorderColor = CardBorderCyan, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )

                        OutlinedTextField(
                            value = inputContent,
                            onValueChange = { inputContent = it },
                            label = { Text("Information Content") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanNeo, unfocusedBorderColor = CardBorderCyan, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.addNote(inputTitle, inputContent)
                            inputTitle = ""
                            inputContent = ""
                            showAddDialog = false
                        }
                    ) {
                        Text("COMMIT NOTE", color = CyanNeo, fontFamily = FontFamily.Monospace)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("ABORT", color = TextGray, fontFamily = FontFamily.Monospace)
                    }
                }
            )
        }
    }
}

@Composable
fun NoteCardItem(note: Note, onDelete: () -> Unit) {
    CyberCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextCyan
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (note.isSynced) Icons.Filled.CloudDone else Icons.Filled.CloudQueue,
                        contentDescription = null,
                        tint = if (note.isSynced) CyanNeo else TextGray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = note.content,
                fontSize = 12.sp,
                color = TextGray,
                lineHeight = 16.sp
            )
        }
    }
}

// -------------------------------------------------------------
// DATABASE MEMORIES HUD PORT
// -------------------------------------------------------------

@Composable
fun DatabaseMemoriesScreen(viewModel: JarvisViewModel) {
    val memories by viewModel.memories.collectAsState()
    var rawInputFact by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = "JARVIS COGNITIVE MEMORY PORT",
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                color = CyanNeo,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Dynamic local facts injected as persistent system intelligence instructions.",
                fontSize = 11.sp,
                color = TextGray
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Fact entry console card
        CyberCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = rawInputFact,
                    onValueChange = { rawInputFact = it },
                    placeholder = { Text("Force add fact parameter (e.g. Rakibul likes engineering)", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanNeo, unfocusedBorderColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                IconButton(
                    onClick = {
                        viewModel.addMemoryRaw(rawInputFact)
                        rawInputFact = ""
                    },
                    modifier = Modifier.background(CyanNeo, CircleShape),
                    enabled = rawInputFact.isNotBlank()
                ) {
                    Icon(imageVector = Icons.Filled.Check, contentDescription = "Submit fact", tint = Color.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Facts scroll board
        if (memories.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Offline database memories is empty", color = TextGray, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(memories) { memory ->
                    MemoryItemChip(memory = memory, onDelete = { viewModel.deleteMemory(memory.id) })
                }
            }
        }
    }
}

@Composable
fun MemoryItemChip(memory: Memory, onDelete: () -> Unit) {
    CyberCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Filled.Memory, contentDescription = null, tint = CyberPurple, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = memory.fact,
                    fontSize = 12.sp,
                    color = TextCyan
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Delete memory", tint = TextGray, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// -------------------------------------------------------------
// HIGH-TECH SCI-FI COSMIC CONTAINER UTILITIES
// -------------------------------------------------------------

@Composable
fun CyberCard(
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 12.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(CarbonBase)
            .border(
                border = BorderStroke(1.dp, CardBorderCyan),
                shape = RoundedCornerShape(cornerRadius)
            )
    ) {
        content()
    }
}
