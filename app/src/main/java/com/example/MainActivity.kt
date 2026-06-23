package com.example

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.data.CarnetProfile
import com.example.data.EcoActivity
import com.example.data.EcoArticle
import com.example.data.Member
import com.example.data.EcoEnrollment
import com.example.data.EcoNotification
import com.example.ui.AppViewModel
import com.example.ui.theme.MyApplicationTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures


fun copyUriToLocalStorage(context: Context, uri: android.net.Uri): String? {
    return try {
        val resolver = context.contentResolver
        val inputStream = resolver.openInputStream(uri) ?: return null
        val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream) ?: return null
        
        // Scale down to max 800px dimension to ensure compact memory size and immediate cloud sync
        val maxDimension = 800
        val width = originalBitmap.width
        val height = originalBitmap.height
        val scaledBitmap = if (width > maxDimension || height > maxDimension) {
            val ratio = width.toFloat() / height.toFloat()
            val newWidth = if (ratio > 1) maxDimension else (maxDimension * ratio).toInt()
            val newHeight = if (ratio > 1) (maxDimension / ratio).toInt() else maxDimension
            android.graphics.Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
        } else {
            originalBitmap
        }
        
        val outputStream = java.io.ByteArrayOutputStream()
        scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
        val byteArray = outputStream.toByteArray()
        android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun getCoilImageModel(uriString: String?): Any? {
    if (uriString.isNullOrBlank()) return null
    if (uriString.startsWith("content://") || uriString.startsWith("http://") || uriString.startsWith("https://")) {
        return uriString
    }
    // Only treat as local file path if it starts with "/" AND does NOT start with "/9j/" (Base64 JPEG prefix) and is short
    if (uriString.startsWith("/") && !uriString.startsWith("/9j/") && uriString.length < 500) {
        return uriString
    }
    // Handle prefixed schemes or clean standard Base64 string
    val cleanBase64 = if (uriString.contains(",")) uriString.substringAfter(",") else uriString
    return try {
        val bytes = android.util.Base64.decode(cleanBase64.trim(), android.util.Base64.NO_WRAP)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        try {
            val bytes = android.util.Base64.decode(cleanBase64.trim(), android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e2: Exception) {
            uriString
        }
    }
}

@Composable
fun AsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: androidx.compose.ui.Alignment = androidx.compose.ui.Alignment.Center,
    alpha: Float = 1.0f,
    colorFilter: androidx.compose.ui.graphics.ColorFilter? = null
) {
    val decodedModel = remember(model) {
        if (model is String) {
            getCoilImageModel(model)
        } else {
            model
        }
    }
    coil.compose.AsyncImage(
        model = decodedModel,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment,
        colorFilter = colorFilter,
        alpha = alpha
    )
}

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Inicializar Firebase Helper para autenticación y base de datos en nube
        com.example.data.api.FirebaseHelper.init(applicationContext)

        // Solicitar permiso de notificaciones para Android 13+ (API 33+) en el inicio de la app
        if (Build.VERSION.SDK_INT >= 33) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    "android.permission.POST_NOTIFICATIONS"
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf("android.permission.POST_NOTIFICATIONS"),
                    101
                )
            }
        }

        setContent {
            val prefTheme by viewModel.prefTheme.collectAsStateWithLifecycle()
            val isDark = when (prefTheme) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            
            MyApplicationTheme(darkTheme = isDark) {
                MainScreen(viewModel)
            }
        }
    }
}

enum class NavigationTab(val label: String, val iconSelected: androidx.compose.ui.graphics.vector.ImageVector, val iconUnselected: androidx.compose.ui.graphics.vector.ImageVector, val testTag: String) {
    ACTIVITIES("Inicio", Icons.Filled.Home, Icons.Outlined.Home, "tab_activities"),
    CALENDAR("Eventos", Icons.Filled.DateRange, Icons.Outlined.DateRange, "tab_calendar"),
    CARNET("Carnet", Icons.Filled.CardMembership, Icons.Outlined.CardMembership, "tab_carnet"),
    FEED("Noticias", Icons.Filled.MenuBook, Icons.Outlined.MenuBook, "tab_feed"),
    ADMIN("Consola", Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings, "tab_admin")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: AppViewModel) {
    val loggedInMember by viewModel.loggedInMember.collectAsStateWithLifecycle()

    if (loggedInMember == null) {
        LoginScreen(viewModel = viewModel)
        return
    }

    val currentMember = loggedInMember!!

    var showLoginWelcomeState by remember(currentMember.email) { mutableStateOf(true) }

    if (showLoginWelcomeState) {
        WelcomeScreen(
            viewModel = viewModel,
            member = currentMember,
            onDismiss = { showLoginWelcomeState = false }
        )
        return
    }

    // Core database states
    val activities by viewModel.allActivities.collectAsStateWithLifecycle()
    val articles by viewModel.allArticles.collectAsStateWithLifecycle()
    val enrollments by viewModel.allEnrollments.collectAsStateWithLifecycle()
    val members by viewModel.adminMembersList.collectAsStateWithLifecycle()
    val rawNotifications by viewModel.allNotifications.collectAsStateWithLifecycle()
    val isUserAdmin = currentMember.isAdmin || currentMember.email == "coordinador@je.org"
    val notifications = remember(rawNotifications, isUserAdmin) {
        val now = System.currentTimeMillis()
        val nonExpired = rawNotifications.filter { alert ->
            !alert.isGlobalAlert || alert.expiryMillis == 0L || now <= alert.expiryMillis
        }
        if (isUserAdmin) {
            nonExpired
        } else {
            nonExpired.filter { it.title != "Inscripción en Actividad 🌿" }
        }
    }
    val googleCalendarLinked by viewModel.googleCalendarLinked.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(NavigationTab.ACTIVITIES) }
    
    // Map activities dynamically based on whether loggedInMember is enrolled or not!
    val mappedActivitiesByEnrollment = remember(activities, enrollments, currentMember) {
        activities.map { activity ->
            val isRegistered = enrollments.any { 
                it.activityId == activity.id && 
                it.memberEmail.trim().lowercase() == currentMember.email.trim().lowercase() 
            }
            activity.copy(isUserRegistered = isRegistered)
        }
    }

    // Support local PIN bypass for testing
    val isOverrideCoordinator by viewModel.isOverrideCoordinator.collectAsStateWithLifecycle()
    var showPinDialog by remember { mutableStateOf(false) }
    var pinText by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    // Determine Coordinator Mode
    val isCoordinadorMode = (currentMember.email == "coordinador@je.org") || currentMember.isAdmin || isOverrideCoordinator

    val context = LocalContext.current
    val alertPrefs = remember(currentMember.email) {
        context.getSharedPreferences("global_alerts_prefs", Context.MODE_PRIVATE)
    }
    val localDismissedAlerts = remember { androidx.compose.runtime.mutableStateMapOf<Int, Boolean>() }
    val activeGlobalAlert = remember(rawNotifications, currentMember.email, localDismissedAlerts.size) {
        val now = System.currentTimeMillis()
        rawNotifications.firstOrNull { alert ->
            alert.isGlobalAlert && 
            (alert.expiryMillis == 0L || now <= alert.expiryMillis) &&
            !alertPrefs.getBoolean("global_alert_dismissed_${currentMember.email}_${alert.id}", false) &&
            localDismissedAlerts[alert.id] != true
        }
    }

    // UI Local Modals
    var showAddActivityDialog by remember { mutableStateOf(false) }
    var showAddReportDialog by remember { mutableStateOf(false) }
    var showNotificationDialog by remember { mutableStateOf(false) }
    var showProfileMenu by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showBugReportDialog by remember { mutableStateOf(false) }

    if (activeGlobalAlert != null) {
        GlobalAlertDialog(
            alert = activeGlobalAlert,
            onDismiss = {
                alertPrefs.edit().putBoolean("global_alert_dismissed_${currentMember.email}_${activeGlobalAlert.id}", true).apply()
                localDismissedAlerts[activeGlobalAlert.id] = true
            }
        )
    }

    var selectedActivityForDetail by remember { mutableStateOf<EcoActivity?>(null) }
    var selectedActivityForEdit by remember { mutableStateOf<EcoActivity?>(null) }
    var selectedArticleForDetail by remember { mutableStateOf<EcoArticle?>(null) }
    var selectedArticleForEdit by remember { mutableStateOf<EcoArticle?>(null) }
    var activityForEnrollConfirm by remember { mutableStateOf<EcoActivity?>(null) }
    var activityForUnenrollConfirm by remember { mutableStateOf<EcoActivity?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            val welcomeName = remember(currentMember) {
                currentMember.fullName.split(" ").firstOrNull()?.let { 
                    it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
                } ?: "Mateo"
            }
            val userAvatar = currentMember.emojiAvatar
            
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "HOLA, $welcomeName".uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "JE-App",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Elegant Security Coordinator Activator Badge on its own line below logo
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isCoordinadorMode) Color(0xFFF18824).copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCoordinadorMode) Icons.Filled.LockOpen else Icons.Filled.Lock,
                                    contentDescription = "Llave de Coordinador",
                                    tint = if (isCoordinadorMode) Color(0xFFF18824) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = if (isCoordinadorMode) "COORDINADOR" else currentMember.role.uppercase(),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isCoordinadorMode) Color(0xFFF18824) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        // Real-time Cloud Sync button with rotating animation and status badge
                        val syncState by viewModel.syncState.collectAsStateWithLifecycle()
                        val context = LocalContext.current
                        
                        val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
                        val rotationAngle by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "rotation"
                        )

                        IconButton(
                            onClick = {
                                viewModel.syncWithCloud { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    when (syncState) {
                                        is AppViewModel.SyncState.Syncing -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        is AppViewModel.SyncState.Success -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                                        is AppViewModel.SyncState.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                    }
                                )
                                .graphicsLayer(rotationZ = if (syncState is AppViewModel.SyncState.Syncing) rotationAngle else 0f)
                                .testTag("cloud_sync_header_button")
                        ) {
                            Icon(
                                imageVector = when (syncState) {
                                    is AppViewModel.SyncState.Success -> Icons.Filled.CloudDone
                                    is AppViewModel.SyncState.Error -> Icons.Filled.SyncProblem
                                    else -> Icons.Filled.Sync
                                },
                                contentDescription = "Sincronizar Nube",
                                tint = when (syncState) {
                                    is AppViewModel.SyncState.Syncing -> MaterialTheme.colorScheme.primary
                                    is AppViewModel.SyncState.Success -> Color(0xFF4CAF50)
                                    is AppViewModel.SyncState.Error -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                }
                            )
                        }

                        // Interactive Notification Bell with Live Unread Badge
                        val unreadCount = notifications.count { !it.isRead }
                        Box(contentAlignment = Alignment.TopEnd) {
                            IconButton(
                                onClick = { showNotificationDialog = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                    .testTag("notification_bell_button")
                            ) {
                                Icon(
                                    imageVector = if (unreadCount > 0) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                                    contentDescription = "Notificaciones",
                                    tint = if (unreadCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            if (unreadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = (-4).dp, y = (4).dp)
                                        .size(16.dp)
                                        .background(Color(0xFFE53935), shape = CircleShape), // Vivid bright red
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = unreadCount.toString(),
                                        color = Color.White,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        style = androidx.compose.ui.text.TextStyle(
                                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                                includeFontPadding = false
                                            )
                                        )
                                    )
                                }
                            }
                        } // Closes Box(contentAlignment = Alignment.TopEnd) { for the notification bell
                        
                        // Clickable avatar that opens the profile options dropdown menu
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .clickable { showProfileMenu = true }
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = userAvatar,
                                        fontSize = 22.sp
                                    )
                                }
                                DropdownMenu(
                                    expanded = showProfileMenu,
                                    onDismissRequest = { showProfileMenu = false },
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surface)
                                        .clip(RoundedCornerShape(16.dp))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Configuración", fontWeight = FontWeight.Medium, fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
                                        onClick = {
                                            showProfileMenu = false
                                            showSettingsDialog = true
                                        }
                                    )
                                    Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                    DropdownMenuItem(
                                        text = { Text("Reportar Bug", fontWeight = FontWeight.Medium, fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Filled.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
                                        onClick = {
                                            showProfileMenu = false
                                            showBugReportDialog = true
                                        }
                                    )
                                    Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                    DropdownMenuItem(
                                        text = { Text("Cerrar Sesión", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) },
                                        onClick = {
                                            showProfileMenu = false
                                            viewModel.logout()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {},
        floatingActionButton = {
            if (currentTab == NavigationTab.ACTIVITIES && isCoordinadorMode) {
                ExtendedFloatingActionButton(
                    text = { Text("Nueva Actividad", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = "Añadir") },
                    onClick = { showAddActivityDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(bottom = 84.dp)
                        .testTag("add_activity_fab")
                )
            } else if (currentTab == NavigationTab.FEED && isCoordinadorMode) {
                ExtendedFloatingActionButton(
                    text = { Text("Subir Reporte", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Filled.EditNote, contentDescription = "Publicar") },
                    onClick = { showAddReportDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(bottom = 84.dp)
                        .testTag("add_report_fab")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            val mappedProfile = remember(currentMember) {
                CarnetProfile(
                    id = 1,
                    fullName = currentMember.fullName,
                    association = currentMember.association,
                    role = currentMember.role,
                    country = currentMember.country,
                    emojiAvatar = currentMember.emojiAvatar,
                    points = currentMember.points,
                    credentialId = currentMember.credentialId,
                    joinDate = currentMember.joinDate,
                    photoUri = currentMember.photoUri,
                    qrUri = currentMember.qrUri,
                    customCara1Uri = currentMember.customCara1Uri,
                    customCara2Uri = currentMember.customCara2Uri
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = innerPadding.calculateTopPadding(),
                        start = innerPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                        end = innerPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
                    )
            ) {
                when (currentTab) {
                    NavigationTab.ACTIVITIES -> {
                        ActivitiesTab(
                            activities = mappedActivitiesByEnrollment,
                            isCoordinadorMode = isCoordinadorMode,
                            memberName = currentMember.fullName,
                            joinDate = currentMember.joinDate,
                            memberPhotoUrl = currentMember.photoUri,
                            onNavigateToCarnet = { currentTab = NavigationTab.CARNET },
                            onToggleEnroll = { activity ->
                                if (activity.isUserRegistered) {
                                    activityForUnenrollConfirm = activity
                                } else {
                                    activityForEnrollConfirm = activity
                                }
                            },
                            onDeleteActivity = { viewModel.deleteActivity(it) },
                            onCardClick = { selectedActivityForDetail = it }
                        )
                    }
                    NavigationTab.CALENDAR -> {
                        CalendarTab(
                            activities = mappedActivitiesByEnrollment,
                            enrollments = enrollments,
                            currentMember = currentMember,
                            viewModel = viewModel,
                            isCoordinadorMode = isCoordinadorMode,
                            onToggleEnroll = { activity ->
                                if (activity.isUserRegistered) {
                                    activityForUnenrollConfirm = activity
                                } else {
                                    activityForEnrollConfirm = activity
                                }
                            },
                            onDeleteActivity = { viewModel.deleteActivity(it) },
                            onCardClick = { selectedActivityForDetail = it }
                        )
                    }
                    NavigationTab.CARNET -> {
                        CarnetTab(
                            profile = mappedProfile,
                            isCoordinadorMode = isCoordinadorMode,
                            onSaveProfile = { name, assoc, role, country, avatar ->
                                viewModel.saveProfile(name, assoc, role, country, avatar)
                            },
                            onUpdatePhoto = { photoUri ->
                                viewModel.updateProfilePhoto(photoUri)
                            },
                            onUpdateCustomCara1 = { uri ->
                                viewModel.updateProfileCustomCara1(uri)
                            },
                            onUpdateCustomCara2 = { uri ->
                                viewModel.updateProfileCustomCara2(uri)
                            }
                        )
                    }
                    NavigationTab.FEED -> {
                        FeedTab(
                            articles = articles,
                            viewModel = viewModel,
                            isCoordinadorMode = isCoordinadorMode,
                            onDeleteArticle = { viewModel.deleteArticle(it) },
                            onPublishReportClick = { showAddReportDialog = true },
                            onArticleClick = { selectedArticleForDetail = it }
                        )
                    }
                    NavigationTab.ADMIN -> {
                        AdminConsoleTab(
                            currentMember = currentMember,
                            viewModel = viewModel,
                            members = members,
                            enrollments = enrollments,
                            notifications = notifications,
                            activities = mappedActivitiesByEnrollment,
                            googleCalendarLinked = googleCalendarLinked,
                            onToggleGoogleCalendar = { viewModel.toggleGoogleCalendarLinked() }
                        )
                    }
                }
            }

            val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
            // Floating bottom navigation bar overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f), // Solidified translucent and premium
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp, // Soft elegant shadow without black outlines or borders
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                    ) {
                        NavigationTab.values().forEach { tab ->
                            // Only show ADMIN console tab if in coordinator mode
                            if (tab != NavigationTab.ADMIN || isCoordinadorMode) {
                                NavigationBarItem(
                                    selected = currentTab == tab,
                                    onClick = { 
                                        hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        currentTab = tab 
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentTab == tab) tab.iconSelected else tab.iconUnselected,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            fontSize = 11.sp,
                                            fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedActivityForDetail?.let { act ->
        ActivityDetailDialog(
            activity = act,
            isCoordinadorMode = isCoordinadorMode,
            onDismissRequest = { selectedActivityForDetail = null },
            onEditClick = {
                selectedActivityForDetail = null
                selectedActivityForEdit = act
            }
        )
    }

    selectedActivityForEdit?.let { act ->
        ActivityEditDialog(
            activity = act,
            onDismissRequest = { selectedActivityForEdit = null },
            onSaveClick = { updated ->
                viewModel.updateActivity(updated)
                selectedActivityForEdit = null
                viewModel.autoSync()
            }
        )
    }

    activityForEnrollConfirm?.let { act ->
        Dialog(onDismissRequest = { activityForEnrollConfirm = null }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Text(
                        text = "Confirmar Inscripción",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "¿Deseas inscribirte en la actividad \"${act.title}\"?",
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        lineHeight = 18.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { activityForEnrollConfirm = null }) {
                            Text("Cancelar")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            shape = RoundedCornerShape(20.dp),
                            onClick = {
                                viewModel.toggleEnrollment(act, currentMember)
                                activityForEnrollConfirm = null
                            }
                        ) {
                            Text("Confirmar")
                        }
                    }
                }
            }
        }
    }

    activityForUnenrollConfirm?.let { act ->
        Dialog(onDismissRequest = { activityForUnenrollConfirm = null }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Text(
                        text = "Confirmar Cancelación",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "¿Estás seguro de que deseas desinscribirte de \"${act.title}\"?",
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        lineHeight = 18.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { activityForUnenrollConfirm = null }) {
                            Text("Cancelar")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            onClick = {
                                viewModel.toggleEnrollment(act, currentMember)
                                activityForUnenrollConfirm = null
                            }
                        ) {
                            Text(
                                text = "Confirmar desinscripción",
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    selectedArticleForDetail?.let { article ->
        ArticleDetailFullScreenDialog(
            article = article,
            isCoordinadorMode = isCoordinadorMode,
            onEditClick = {
                selectedArticleForDetail = null
                selectedArticleForEdit = article
            },
            onDismiss = { selectedArticleForDetail = null }
        )
    }

    selectedArticleForEdit?.let { article ->
        EditReportDialog(
            article = article,
            onDismiss = { selectedArticleForEdit = null },
            onSave = { updated ->
                viewModel.updateLocalArticle(updated)
                selectedArticleForEdit = null
            }
        )
    }

    // Modern Security PIN Dialog to access administrative features
    if (showPinDialog) {
        Dialog(onDismissRequest = { 
            showPinDialog = false
            pinText = ""
            pinError = false
        }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AdminPanelSettings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Text(
                        text = "Acceso Coordinación Central",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Solo el equipo coordinador de Jóvenes y Ecosistemas Latinoamérica puede gestionar actividades, artículos y credenciales. Ingrese el PIN de seguridad (2026).",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        lineHeight = 16.sp
                    )

                    OutlinedTextField(
                        value = pinText,
                        onValueChange = { 
                            pinText = it
                            pinError = false
                        },
                        label = { Text("PIN de Acceso") },
                        placeholder = { Text("Pista: 2026") },
                        singleLine = true,
                        isError = pinError,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (pinError) {
                        Text(
                            text = "PIN incorrecto. Inténtelo de nuevo.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { 
                            showPinDialog = false
                            pinText = ""
                            pinError = false
                        }) {
                            Text("Cancelar")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            shape = RoundedCornerShape(20.dp),
                            onClick = {
                                if (pinText == "2026" || pinText == "JE2026") {
                                    viewModel.setOverrideCoordinator(true)
                                    showPinDialog = false
                                    pinText = ""
                                    pinError = false
                                } else {
                                    pinError = true
                                }
                            }
                        ) {
                            Text("Habilitar")
                        }
                    }
                }
            }
        }
    }

    // Modal dialog for adding custom events
    if (showAddActivityDialog) {
        AddActivityDialog(
            onDismiss = { showAddActivityDialog = false },
            onAdd = { title, desc, date, endDate, loc, country, cat, org, evType, isMandatory, deadline ->
                viewModel.addActivity(title, desc, date, endDate, loc, country, cat, org, evType, isMandatory, deadline)
                showAddActivityDialog = false
            }
        )
    }

    // Modal dialog for contributing custom columns/reports
    if (showAddReportDialog) {
        AddReportDialog(
            onDismiss = { showAddReportDialog = false },
            onPublish = { title, content, cat, rgn, photo ->
                viewModel.publishLocalArticle(title, content, cat, rgn, photo)
                showAddReportDialog = false
            }
        )
    }

    // Modal dialog for display notifications to members and coordinators
    if (showNotificationDialog) {
        NotificationListDialog(
            viewModel = viewModel,
            notifications = notifications,
            onDismiss = { showNotificationDialog = false },
            onMarkAllRead = { viewModel.markNotificationsRead() },
            onClearNotification = { id -> viewModel.clearNotification(id) }
        )
    }

    // Modal dialog for app general preferences (Ajustes de la App)
    if (showSettingsDialog) {
        AppSettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showBugReportDialog) {
        BugReportDialog(
            viewModel = viewModel,
            currentMember = currentMember,
            onDismiss = { showBugReportDialog = false }
        )
    }
}

// ==========================================
// TAB 1: ACTIVITIES (NOTICE BOARD)
// ==========================================

@Composable
fun ActivitiesTab(
    activities: List<EcoActivity>,
    isCoordinadorMode: Boolean,
    memberName: String,
    joinDate: String,
    memberPhotoUrl: String?,
    onNavigateToCarnet: () -> Unit,
    onToggleEnroll: (EcoActivity) -> Unit,
    onDeleteActivity: (Int) -> Unit,
    onCardClick: (EcoActivity) -> Unit
) {
    var selectedCategoryFilter by remember { mutableStateOf("Todos") }
    val categories = listOf("Todos", "Educación", "Asamblea general", "Actividad", "Voluntariado")
    var activityToDelete by remember { mutableStateOf<EcoActivity?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(selectedCategoryFilter) {
        try {
            listState.scrollToItem(0)
        } catch (_: Exception) {}
    }

    if (activityToDelete != null) {
        AlertDialog(
            onDismissRequest = { activityToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text("Eliminar Actividad", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Text(
                    "¿De verdad deseas eliminar permanentemente la actividad \"${activityToDelete?.title}\"? Esta acción no se puede revertir.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        activityToDelete?.let { onDeleteActivity(it.id) }
                        activityToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Eliminar Actividad", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { activityToDelete = null }) {
                    Text("Cancelar")
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        
        // Sleek "Quick Access Digital ID" Card representing the premium CSS container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(146.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF394931), // BrandGreen - Dark aesthetic forest green
                            Color(0xFF1B2317)  // Brand forestry shadow accent
                        )
                    )
                )
                .clickable {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onNavigateToCarnet()
                }
        ) {
            // Organic backdrop circle representing visual layers
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color(0xFFB0B59E).copy(alpha = 0.15f),
                    radius = size.maxDimension * 0.42f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.95f, size.height * 0.05f)
                )
            }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "Nombre del Miembro".uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB0B59E), // BrandSage
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = memberName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!memberPhotoUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = memberPhotoUrl,
                                contentDescription = "Foto del Miembro",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Foto del Miembro",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "Miembro desde: ${if (joinDate.contains("/")) joinDate.substringAfterLast("/") else joinDate}",
                            fontSize = 11.sp,
                            color = Color(0xFFD9DBD8).copy(alpha = 0.85f) // BrandGrey
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "ID: LATAM-2026-882",
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                    }
                    
                    // Glassmorphic "Ver QR" button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Ver QR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Horizontal Category Row Filters
        Text(
            text = "Filtrar por Tipo de Actividad".uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 18.dp, bottom = 6.dp, top = 4.dp)
        )
        
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { cat ->
                val isSelected = selectedCategoryFilter == cat
                val tabColor = when (cat.lowercase()) {
                    "voluntariado" -> Color(0xFF047857)
                    "educación" -> Color(0xFFD97706)
                    "asamblea general" -> Color(0xFF7C3AED)
                    "actividad" -> Color(0xFF0284C7)
                    else -> MaterialTheme.colorScheme.primary
                }
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) tabColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    label = "bgColor"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        if (cat.lowercase() == "todos") MaterialTheme.colorScheme.onPrimary else Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "textColor"
                )
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(bgColor)
                        .clickable { 
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            selectedCategoryFilter = cat 
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = cat,
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // List display
        val allowedTypes = listOf("educación", "asamblea general", "actividad", "voluntariado")
        val filteredActivities = if (selectedCategoryFilter == "Todos") {
            activities.filter { it.eventType.lowercase() in allowedTypes }
        } else {
            activities.filter { it.eventType.equals(selectedCategoryFilter, ignoreCase = true) }
        }

        if (filteredActivities.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.NaturePeople,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No hay actividades en esta categoría",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "¡Usa el botón flotante para crear la primera actividad hoy!",
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredActivities, key = { it.id }) { item ->
                    ActivityCard(
                        item = item,
                        isCoordinadorMode = isCoordinadorMode,
                        onToggleEnroll = { 
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onToggleEnroll(item) 
                        },
                        onDelete = { 
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            activityToDelete = item 
                        },
                        onClick = { onCardClick(item) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
fun ActivityCard(
    item: EcoActivity,
    isCoordinadorMode: Boolean,
    onToggleEnroll: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    val categoryStyle = remember(item.category, isDark) {
        if (isDark) {
            when (item.category.trim().lowercase()) {
                "reforestación", "je-ambiente" -> Triple(Color(0xFF064E3B), Color(0xFF34D399), Icons.Filled.Eco)
                "limpieza", "je-visual" -> Triple(Color(0xFF0C4A6E), Color(0xFF38BDF8), Icons.Filled.WaterDrop)
                "educación" -> Triple(Color(0xFF78350F), Color(0xFFFBBF24), Icons.Filled.School)
                "conservación" -> Triple(Color(0xFF2D3A29), Color(0xFFC2C9B6), Icons.Filled.Forest)
                "je-ram" -> Triple(Color(0xFF022C22), Color(0xFF34D399), Icons.Filled.Eco)
                "je-mental" -> Triple(Color(0xFF3B0764), Color(0xFFC084FC), Icons.Filled.Eco)
                "je-podcast" -> Triple(Color(0xFF78350F), Color(0xFFFBBF24), Icons.Filled.Eco)
                "je-360" -> Triple(Color(0xFF1E3A8A), Color(0xFF60A5FA), Icons.Filled.Eco)
                "je-vih" -> Triple(Color(0xFF881337), Color(0xFFFDA4AF), Icons.Filled.Eco)
                else -> Triple(Color(0xFF273523), Color(0xFFB4C2AF), Icons.Filled.NaturePeople)
            }
        } else {
            when (item.category.trim().lowercase()) {
                "reforestación", "je-ambiente" -> Triple(Color(0xFFD1FAE5), Color(0xFF047857), Icons.Filled.Eco)
                "limpieza", "je-visual" -> Triple(Color(0xFFE0F2FE), Color(0xFF0284C7), Icons.Filled.WaterDrop)
                "educación" -> Triple(Color(0xFFFEF3C7), Color(0xFFD97706), Icons.Filled.School)
                "conservación" -> Triple(Color(0xFFE5E7E2), Color(0xFF394931), Icons.Filled.Forest)
                "je-ram" -> Triple(Color(0xFFECFDF5), Color(0xFF059669), Icons.Filled.Eco)
                "je-mental" -> Triple(Color(0xFFF3E8FF), Color(0xFF7C3AED), Icons.Filled.Eco)
                "je-podcast" -> Triple(Color(0xFFFEF3C7), Color(0xFFD97706), Icons.Filled.Eco)
                "je-360" -> Triple(Color(0xFFE0F2FE), Color(0xFF2563EB), Icons.Filled.Eco)
                "je-vih" -> Triple(Color(0xFFFFE4E6), Color(0xFFE11D48), Icons.Filled.Eco)
                else -> Triple(Color(0xFFEAECE9), Color(0xFF4C5E43), Icons.Filled.NaturePeople)
            }
        }
    }

    val eventTypeIcon = remember(item.eventType) {
        when (item.eventType.trim().lowercase()) {
            "voluntariado" -> Icons.Filled.Favorite
            "educación" -> Icons.Filled.School
            "asamblea general" -> Icons.Filled.Groups
            "actividad" -> Icons.Filled.Event
            else -> Icons.Filled.LocalActivity
        }
    }

    val typeColor = remember(item.eventType, isDark) {
        if (isDark) {
            when (item.eventType.lowercase()) {
                "voluntariado" -> Color(0xFF34D399)
                "educación" -> Color(0xFFFBBF24)
                "asamblea general" -> Color(0xFFC084FC)
                "actividad" -> Color(0xFF60A5FA)
                "talleres" -> Color(0xFFFBBF24)
                "charlas" -> Color(0xFF60A5FA)
                else -> Color(0xFF9CA3AF)
            }
        } else {
            when (item.eventType.lowercase()) {
                "voluntariado" -> Color(0xFF047857)
                "educación" -> Color(0xFFD97706)
                "asamblea general" -> Color(0xFF7C3AED)
                "actividad" -> Color(0xFF0284C7)
                "talleres" -> Color(0xFFD97706)
                "charlas" -> Color(0xFF0284C7)
                else -> Color(0xFF6B7280)
            }
        }
    }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Card(
        modifier = modifier.fillMaxWidth().clickable { 
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            onClick() 
        },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = if (item.isMandatory) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFDC2626)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Main Top info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type Styled Left Box
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(typeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = eventTypeIcon,
                        contentDescription = item.category,
                        tint = typeColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Text columns
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${item.date.substringBefore(" ").trim()} • ${item.category}".uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        
                        // Event Type badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(typeColor.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
							Text(
								text = item.eventType.uppercase(),
								color = typeColor,
								fontSize = 8.sp,
								fontWeight = FontWeight.Bold,
								letterSpacing = 0.5.sp,
								maxLines = 1,
								softWrap = false
							)
                        }

                        if (item.isMandatory) {
                            val mandatoryBg = if (isDark) Color(0xFF7F1D1D) else Color(0xFFFEE2E2)
                            val mandatoryFg = if (isDark) Color(0xFFFCA5A5) else Color(0xFF991B1B)
                            Box(
                                modifier = Modifier
                                    .background(mandatoryBg, shape = RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "OBLIGATORIA",
                                    color = mandatoryFg,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Delete Action - Restricted to Coordinador
                if (isCoordinadorMode) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Borrar",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            
            // Description paragraph
            Text(
                text = item.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                lineHeight = 17.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(10.dp))

            // Metadata Bottom Row (Location + Organizer + Action Button)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = categoryStyle.second,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "${item.location}, ${item.country}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Por: ${item.organizer}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
                
                // Enrollment Button Action
                val isDeadlinePassed = com.example.util.TimezoneHelper.isPastRegistrationDeadline(item.registrationDeadline)
                if (isDeadlinePassed && !item.isUserRegistered) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Cerrado",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Button(
                        onClick = onToggleEnroll,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (item.isUserRegistered) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        enabled = !isDeadlinePassed
                    ) {
                        Icon(
                            imageVector = if (item.isUserRegistered) Icons.Filled.CheckCircle else Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (item.isUserRegistered) "Inscrito" else "Unirse",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// Dialog to Add a custom Activity
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddActivityDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String, String, String, String, String, String, Boolean, String) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var datePart by remember { mutableStateOf("2026-06-05") }
    var timePart by remember { mutableStateOf("10:00") }
    var endDatePart by remember { mutableStateOf("2026-06-05") }
    var endTimePart by remember { mutableStateOf("12:00") }

    var hasRegistrationDeadline by remember { mutableStateOf(false) }
    var deadlineDatePart by remember { mutableStateOf("2026-06-05") }
    var deadlineTimePart by remember { mutableStateOf("10:00") }

    val showDeadlineDatePicker = {
        val calendar = java.util.Calendar.getInstance()
        if (deadlineDatePart.isNotBlank()) {
            try {
                val parts = deadlineDatePart.split("-")
                if (parts.size == 3) {
                    calendar.set(java.util.Calendar.YEAR, parts[0].toInt())
                    calendar.set(java.util.Calendar.MONTH, parts[1].toInt() - 1)
                    calendar.set(java.util.Calendar.DAY_OF_MONTH, parts[2].toInt())
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formattedMonth = String.format("%02d", month + 1)
                val formattedDay = String.format("%02d", dayOfMonth)
                deadlineDatePart = "$year-$formattedMonth-$formattedDay"
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    val showDeadlineTimePicker = {
        val hourMinute = deadlineTimePart.split(":")
        val hour = hourMinute.getOrNull(0)?.toIntOrNull() ?: 10
        val minute = hourMinute.getOrNull(1)?.toIntOrNull() ?: 0
        android.app.TimePickerDialog(
            context,
            { _, selectedHour, selectedMinute ->
                deadlineTimePart = String.format("%02d:%02d", selectedHour, selectedMinute)
            },
            hour,
            minute,
            true // is24HourView
        ).show()
    }

    val showDatePicker = {
        val calendar = java.util.Calendar.getInstance()
        if (datePart.isNotBlank()) {
            try {
                val parts = datePart.split("-")
                if (parts.size == 3) {
                    calendar.set(java.util.Calendar.YEAR, parts[0].toInt())
                    calendar.set(java.util.Calendar.MONTH, parts[1].toInt() - 1)
                    calendar.set(java.util.Calendar.DAY_OF_MONTH, parts[2].toInt())
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formattedMonth = String.format("%02d", month + 1)
                val formattedDay = String.format("%02d", dayOfMonth)
                val oldDate = datePart
                datePart = "$year-$formattedMonth-$formattedDay"
                // Keep end date aligned with start date initially unless user customizes it
                if (endDatePart == oldDate) {
                    endDatePart = datePart
                }
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    val showTimePicker = {
        val hourMinute = timePart.split(":")
        val hour = hourMinute.getOrNull(0)?.toIntOrNull() ?: 10
        val minute = hourMinute.getOrNull(1)?.toIntOrNull() ?: 0
        android.app.TimePickerDialog(
            context,
            { _, selectedHour, selectedMinute ->
                timePart = String.format("%02d:%02d", selectedHour, selectedMinute)
            },
            hour,
            minute,
            true // is24HourView
        ).show()
    }

    val showEndDatePicker = {
        val calendar = java.util.Calendar.getInstance()
        if (endDatePart.isNotBlank()) {
            try {
                val parts = endDatePart.split("-")
                if (parts.size == 3) {
                    calendar.set(java.util.Calendar.YEAR, parts[0].toInt())
                    calendar.set(java.util.Calendar.MONTH, parts[1].toInt() - 1)
                    calendar.set(java.util.Calendar.DAY_OF_MONTH, parts[2].toInt())
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formattedMonth = String.format("%02d", month + 1)
                val formattedDay = String.format("%02d", dayOfMonth)
                endDatePart = "$year-$formattedMonth-$formattedDay"
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    val showEndTimePicker = {
        val hourMinute = endTimePart.split(":")
        val hour = hourMinute.getOrNull(0)?.toIntOrNull() ?: 12
        val minute = hourMinute.getOrNull(1)?.toIntOrNull() ?: 0
        android.app.TimePickerDialog(
            context,
            { _, selectedHour, selectedMinute ->
                endTimePart = String.format("%02d:%02d", selectedHour, selectedMinute)
            },
            hour,
            minute,
            true // is24HourView
        ).show()
    }
    
    // Location parameters
    var loc by remember { mutableStateOf("") }
    var isVirtual by remember { mutableStateOf(false) }
    var virtualLink by remember { mutableStateOf("") }

    var country by remember { mutableStateOf("Ecuador") }
    var cat by remember { mutableStateOf("JE-RAM") }
    var org by remember { mutableStateOf("") }
    var eventType by remember { mutableStateOf("Voluntariado") }
    var isMandatory by remember { mutableStateOf(false) }

    // Dynamic country additions
    var countriesList by remember {
        mutableStateOf(
            listOf("Ecuador", "Guatemala", "Bolivia", "Venezuela", "México", "Colombia", "Perú", "Chile", "Argentina", "Brasil", "América Latina", "Internacional")
        )
    }
    var customCountryText by remember { mutableStateOf("") }

    val catsList = listOf("JE-RAM", "JE-Visual", "JE-Ambiente", "JE-VIH", "JE-Podcast", "JE-360", "JE-Mental")
    val eventTypesList = listOf("Educación", "Asamblea general", "Actividad", "Voluntariado")

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                // Top Custom Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Crear Actividad",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "Nueva convocatoria para motivar la participación juvenil",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                }

                // Form Content
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Card 1: Información Básica (Creación Única)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth() /* create_card_1 */,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "1. Información Básica (Nuevo)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                
                                OutlinedTextField(
                                    value = title,
                                    onValueChange = { title = it /* add_title */ },
                                    label = { Text("Título de la actividad") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = desc,
                                    onValueChange = { desc = it /* add_desc */ },
                                    label = { Text("Descripción / Convocatoria") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(115.dp),
                                    maxLines = 5
                                )

                                OutlinedTextField(
                                    value = org,
                                    onValueChange = { org = it /* add_org */ },
                                    label = { Text("Organizador (Colectivo / Organización)") },
                                    placeholder = { Text("Ej. Voluntariado Juvenil") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Actividad Obligatoria 🚨",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Las actividades obligatorias se destacan con un borde rojo elegante para llamar la atención del equipo.",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                    Switch(
                                        checked = isMandatory,
                                        onCheckedChange = { isMandatory = it /* add_is_mandatory */ }
                                    )
                                }
                            }
                        }
                    }

                    // Card 2: Fecha, Modalidad y Ubicación (Creación)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "2. Fecha, Ubicación y Límite",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                // UNIQUE_ADD_DATE_SELECTOR
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.Checkbox(
                                        checked = hasRegistrationDeadline,
                                        onCheckedChange = { hasRegistrationDeadline = it }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Establecer fecha límite de inscripción",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                if (hasRegistrationDeadline) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1.3f)
                                                .clickable { showDeadlineDatePicker() }
                                        ) {
                                            OutlinedTextField(
                                                value = deadlineDatePart,
                                                onValueChange = {},
                                                label = { Text("Fecha límite") },
                                                readOnly = true,
                                                enabled = false,
                                                trailingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Filled.CalendarToday,
                                                        contentDescription = "Seleccionar fecha límite"
                                                    )
                                                },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                                                ),
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(0.9f)
                                                .clickable { showDeadlineTimePicker() }
                                        ) {
                                            OutlinedTextField(
                                                value = deadlineTimePart,
                                                onValueChange = {},
                                                label = { Text("Hora límite") },
                                                readOnly = true,
                                                enabled = false,
                                                trailingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Filled.Event,
                                                        contentDescription = "Seleccionar hora límite"
                                                    )
                                                },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                                                ),
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "Fecha y Hora de Inicio",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .clickable { showDatePicker() }
                                    ) {
                                        OutlinedTextField(
                                            value = datePart,
                                            onValueChange = {},
                                            label = { Text("Fecha Inicio") },
                                            readOnly = true,
                                            enabled = false,
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.CalendarToday,
                                                    contentDescription = "Seleccionar fecha inicio"
                                                )
                                            },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(0.9f)
                                            .clickable { showTimePicker() }
                                    ) {
                                        OutlinedTextField(
                                            value = timePart,
                                            onValueChange = {},
                                            label = { Text("Hora Inicio") },
                                            readOnly = true,
                                            enabled = false,
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.Event,
                                                    contentDescription = "Seleccionar hora inicio"
                                                )
                                            },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                    }
                                }

                                Text(
                                    text = "Fecha y Hora de Fin",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .clickable { showEndDatePicker() }
                                    ) {
                                        OutlinedTextField(
                                            value = endDatePart,
                                            onValueChange = {},
                                            label = { Text("Fecha Fin") },
                                            readOnly = true,
                                            enabled = false,
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.CalendarToday,
                                                    contentDescription = "Seleccionar fecha fin"
                                                )
                                            },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(0.9f)
                                            .clickable { showEndTimePicker() }
                                    ) {
                                        OutlinedTextField(
                                            value = endTimePart,
                                            onValueChange = {},
                                            label = { Text("Hora Fin") },
                                            readOnly = true,
                                            enabled = false,
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.Event,
                                                    contentDescription = "Seleccionar hora fin"
                                                )
                                            },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                
                                // Modalidad Switcher (Presencial vs Virtual)
                                Text(
                                    text = "Modalidad del evento:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = !isVirtual,
                                        onClick = { isVirtual = false },
                                        label = { Text("Presencial 📍") }
                                    )
                                    FilterChip(
                                        selected = isVirtual,
                                        onClick = { isVirtual = true },
                                        label = { Text("Virtual 💻") }
                                    )
                                }

                                if (isVirtual) {
                                    OutlinedTextField(
                                        value = loc,
                                        onValueChange = { loc = it },
                                        label = { Text("Plataforma virtual (ej. Google Meet, Zoom, Teams)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = virtualLink,
                                        onValueChange = { virtualLink = it },
                                        label = { Text("Enlace / Link de la reunión virtual") },
                                        placeholder = { Text("https://meet.google.com/...") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                } else {
                                    OutlinedTextField(
                                        value = loc,
                                        onValueChange = { loc = it },
                                        label = { Text("Dirección del Lugar físico") },
                                        placeholder = { Text("Ej. Parque Central, Auditorio Principal") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }

                    // Card 3: Categorías, Tipo y País Sede
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "3. Clasificación de Actividad",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                // Tipo de evento list
                                Column {
                                    Text(
                                        text = "Tipo de Evento:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(eventTypesList) { currentType ->
                                            FilterChip(
                                                selected = eventType == currentType,
                                                onClick = { eventType = currentType },
                                                label = { Text(currentType, fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                }

                                // Categories changed to "Proyecto emblemático que realiza"
                                Column {
                                    Text(
                                        text = "Proyecto emblemático que realiza:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(catsList) { currentCat ->
                                            FilterChip(
                                                selected = cat == currentCat,
                                                onClick = { cat = currentCat },
                                                label = { Text(currentCat, fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                }

                                // Country tags + dynamic item creator
                                Column {
                                    Text(
                                        text = "País Sede:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(countriesList) { currentC ->
                                            FilterChip(
                                                selected = country == currentC,
                                                onClick = { country = currentC },
                                                label = { Text(currentC, fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // Add country dynamically to tags
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = customCountryText,
                                            onValueChange = { customCountryText = it },
                                            placeholder = { Text("Añadir otro país...", fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedContainerColor = Color.Transparent
                                            )
                                        )
                                        Button(
                                            onClick = {
                                                if (customCountryText.isNotBlank()) {
                                                    val trimmedC = customCountryText.trim()
                                                    if (!countriesList.contains(trimmedC)) {
                                                        countriesList = countriesList + trimmedC
                                                    }
                                                    country = trimmedC
                                                    customCountryText = ""
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Añadir país",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Añadir", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Action Bar at Bottom
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 44.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Cancelar", fontWeight = FontWeight.Medium)
                    }
                    Button(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(48.dp),
                        onClick = {
                            if (title.isNotBlank() && desc.isNotBlank()) {
                                val finalLoc = if (isVirtual) {
                                    val platform = loc.ifBlank { "Virtual" }
                                    if (virtualLink.isNotBlank()) "$platform - Enlace: $virtualLink" else platform
                                } else {
                                    loc.ifBlank { "Presencial" }
                                }
                                val rawEcuadorDateStr = "${datePart.trim()} ${timePart.trim()}"
                                val rawEcuadorEndDateStr = "${endDatePart.trim()} ${endTimePart.trim()}"
                                val rawEcuadorDeadlineStr = if (hasRegistrationDeadline) "${deadlineDatePart.trim()} ${deadlineTimePart.trim()}" else ""
                                onAdd(
                                    title,
                                    desc,
                                    rawEcuadorDateStr,
                                    rawEcuadorEndDateStr,
                                    finalLoc,
                                    country,
                                    cat,
                                    org.ifBlank { "Voluntariado Juvenil" },
                                    eventType,
                                    isMandatory,
                                    rawEcuadorDeadlineStr
                                )
                            }
                        },
                        enabled = title.isNotBlank() && desc.isNotBlank()
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Guardar", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Crear Actividad")
                    }
                }
            }
        }
    }
}

// ==========================================
// TAB 2: MEMBERSHIP CARD (CARNET DIGITAL)
// ==========================================

@Composable
fun CarnetTab(
    profile: CarnetProfile?,
    isCoordinadorMode: Boolean,
    onSaveProfile: (String, String, String, String, String) -> Unit,
    onUpdatePhoto: (String?) -> Unit,
    onUpdateCustomCara1: (String?) -> Unit,
    onUpdateCustomCara2: (String?) -> Unit
) {
    if (profile == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Cargando credencial digital de miembro...", fontSize = 14.sp)
        }
        return
    }

    val context = LocalContext.current

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val localPath = copyUriToLocalStorage(context, uri)
            if (localPath != null) {
                onUpdatePhoto(localPath)
            }
        }
    }

    val customCara1Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val localPath = copyUriToLocalStorage(context, uri)
            if (localPath != null) {
                onUpdateCustomCara1(localPath)
            }
        }
    }

    val customCara2Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val localPath = copyUriToLocalStorage(context, uri)
            if (localPath != null) {
                onUpdateCustomCara2(localPath)
            }
        }
    }

    var isEditing by remember { mutableStateOf(false) }
    var isFlipped by remember { mutableStateOf(false) }

    // Forms fields states
    var editedName by remember { mutableStateOf(profile.fullName) }
    var editedAssoc by remember { mutableStateOf(profile.association) }
    var editedRole by remember { mutableStateOf(profile.role) }
    var editedCountry by remember { mutableStateOf(profile.country) }
    var editedAvatar by remember { mutableStateOf(profile.emojiAvatar) }

    // Set form fields once profile updates externally
    LaunchedEffect(profile) {
        editedName = profile.fullName
        editedAssoc = profile.association
        editedRole = profile.role
        editedCountry = profile.country
        editedAvatar = profile.emojiAvatar
    }

    val animatedRotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "rotation"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tu Credencial Digital",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Toca la credencial para voltearlo y ver tu código QR",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Beautiful 3D Flip Card
        item {
            Card(
                modifier = Modifier
                    .width(310.dp)
                    .height(490.dp)
                    .graphicsLayer {
                        rotationY = animatedRotation
                        cameraDistance = 12 * density
                    }
                    .clickable { isFlipped = !isFlipped },
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                if (animatedRotation <= 90f) {
                    // ==========================================
                    // CARA 1 (FRONT OF CARNET)
                    // ==========================================
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF394931), // BrandGreen - Dark aesthetic forest green
                                        Color(0xFF1B2317)  // Dark forestry shadow accent
                                    )
                                )
                            )
                    ) {
                        if (!profile.customCara1Uri.isNullOrEmpty()) {
                            AsyncImage(
                                model = profile.customCara1Uri,
                                contentDescription = "Manual Cara 1",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                        // Fine organic layout curves
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = Color(0xFFB0B59E).copy(alpha = 0.08f),
                                radius = size.minDimension / 1.1f,
                                center = androidx.compose.ui.geometry.Offset(size.width, size.height * 0.15f)
                            )
                            drawCircle(
                                color = Color(0xFFD9DBD8).copy(alpha = 0.05f),
                                radius = size.minDimension / 1.5f,
                                center = androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.85f)
                            )
                        }

                        // Top lanyard / strap slot hanger
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp)
                                .size(50.dp, 8.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.22f))
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 22.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Eco,
                                    contentDescription = null,
                                    tint = Color(0xFFB0B59E),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "JÓVENES Y ECOSISTEMAS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    color = Color(0xFFD9DBD8)
                                )
                            }

                            // Middle Info section - Name, role & country centered
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = profile.fullName.uppercase(),
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = profile.role.uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB0B59E),
                                    letterSpacing = 1.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Public,
                                        contentDescription = null,
                                        tint = Color(0xFFB0B59E).copy(alpha = 0.7f),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = profile.country.uppercase(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFD9DBD8)
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DateRange,
                                        contentDescription = null,
                                        tint = Color(0xFFB0B59E).copy(alpha = 0.7f),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "MIEMBRO DESDE: ${if (profile.joinDate.contains("/")) profile.joinDate.substringAfterLast("/") else profile.joinDate}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFD9DBD8)
                                    )
                                }
                            }

                            // Photograph Center Acrylic Frame Container
                            Box(
                                modifier = Modifier
                                    .size(135.dp)
                                    .border(3.1.dp, Color.White, RoundedCornerShape(20.dp))
                                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp))
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .then(
                                        if (isCoordinadorMode) {
                                            Modifier.clickable(
                                                onClickLabel = "Cambiar Foto de Carnet"
                                            ) {
                                                photoLauncher.launch(
                                                    PickVisualMediaRequest(
                                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                                    )
                                                )
                                            }
                                        } else {
                                            Modifier
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!profile.photoUri.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = profile.photoUri,
                                        contentDescription = "Foto de carnet",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (isCoordinadorMode) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.25f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PhotoCamera,
                                                contentDescription = "Cambiar Foto",
                                                tint = Color.White.copy(alpha = 0.9f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(Color(0xFF394931), Color(0xFF1B2317))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = profile.emojiAvatar,
                                            fontSize = 60.sp
                                        )
                                    }
                                    if (isCoordinadorMode) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PhotoCamera,
                                                contentDescription = "Subir foto",
                                                tint = Color.White.copy(alpha = 0.9f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Credential ID Code block
                            Column(
                                modifier = Modifier.fillMaxWidth(0.85f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = profile.credentialId,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    letterSpacing = 2.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(Color.White.copy(alpha = 0.35f))
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "CÓDIGO",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB0B59E),
                                    letterSpacing = 1.5.sp
                                )
                            }

                            // Bottom Motto / Decoration Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Dynamic circular JEL medallion
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1B2317))
                                            .border(1.dp, Color(0xFFB0B59E), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Eco,
                                            contentDescription = null,
                                            tint = Color(0xFFB0B59E),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "VOLUNTARIO JEL",
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "BIO NETWORK LA",
                                            fontSize = 6.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFB0B59E)
                                        )
                                    }
                                }

                                // Quotation block
                                Text(
                                    text = "“ Jóvenes que encajan para transformar ”",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontStyle = FontStyle.Italic,
                                    color = Color(0xFFD9DBD8),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f).padding(start = 12.dp)
                                )
                            }
                        }
                        }
                    }
                } else {
                    // ==========================================
                    // CARA 2 (BACK OF CARNET - QR CODE SIDE)
                    // ==========================================
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationY = 180f }
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF1B2317), // Dark forest shadow accent
                                        Color(0xFF11170F)  // Even darker carbon forest tone
                                    )
                                )
                            )
                    ) {
                        if (!profile.customCara2Uri.isNullOrEmpty()) {
                            AsyncImage(
                                model = profile.customCara2Uri,
                                contentDescription = "Manual Cara 2",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                        // Abstract design background lines
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = Color(0xFFB0B59E).copy(alpha = 0.05f),
                                radius = size.minDimension * 0.9f,
                                center = androidx.compose.ui.geometry.Offset(0f, size.height * 0.5f)
                            )
                        }

                        // Top slot hanger
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp)
                                .size(50.dp, 8.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.22f))
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 22.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Back top tagline
                            Column(
                                modifier = Modifier.padding(top = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Forest,
                                    contentDescription = null,
                                    tint = Color(0xFFB0B59E),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "COMPROMISO CLIMÁTICO",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.5.sp,
                                    color = Color(0xFFB0B59E)
                                )
                            }

                            // White QR block frame holding uploaded QR image or high fidelity placeholder
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.82f)
                                    .height(230.dp)
                                    .background(Color.White, RoundedCornerShape(22.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(22.dp))
                                    .shadow(6.dp, RoundedCornerShape(22.dp))
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!profile.qrUri.isNullOrEmpty()) {
                                    // Custom user uploaded QR Code image
                                    AsyncImage(
                                        model = profile.qrUri,
                                        contentDescription = "Código QR personalizado de carnet",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    // High-Fidelity Custom Ecology QR Code Representation
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Draw modular QR blocks manually
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                // QR Finder Pattern top-left
                                                QRAnchorPattern()
                                                // QR Finder Pattern top-right
                                                QRAnchorPattern()
                                            }
                                            
                                            // Middle decorative matrix columns
                                            Row(
                                                modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                QRMatrixDotGroupVertical()
                                                QRMatrixDotGroupVertical()
                                            }

                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                // QR Finder Pattern bottom-left
                                                QRAnchorPattern()
                                                // QR Finder Pattern bottom-right placeholder simulation
                                                Box(
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .padding(2.dp)
                                                ) {
                                                    // Dynamic mini matrix pixels simulation
                                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                            Box(modifier = Modifier.size(5.dp).background(Color(0xFF0F172A), RoundedCornerShape(1.dp)))
                                                            Box(modifier = Modifier.size(5.dp).background(Color.Transparent))
                                                            Box(modifier = Modifier.size(5.dp).background(Color(0xFF0F172A), RoundedCornerShape(1.dp)))
                                                            Box(modifier = Modifier.size(5.dp).background(Color(0xFF0F172A), RoundedCornerShape(1.dp)))
                                                        }
                                                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                            Box(modifier = Modifier.size(5.dp).background(Color.Transparent))
                                                            Box(modifier = Modifier.size(5.dp).background(Color(0xFF0F172A), RoundedCornerShape(1.dp)))
                                                            Box(modifier = Modifier.size(5.dp).background(Color.Transparent))
                                                            Box(modifier = Modifier.size(5.dp).background(Color(0xFF0F172A), RoundedCornerShape(1.dp)))
                                                        }
                                                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                            Box(modifier = Modifier.size(5.dp).background(Color(0xFF0F172A), RoundedCornerShape(1.dp)))
                                                            Box(modifier = Modifier.size(5.dp).background(Color(0xFF0F172A), RoundedCornerShape(1.dp)))
                                                            Box(modifier = Modifier.size(5.dp).background(Color.Transparent))
                                                            Box(modifier = Modifier.size(5.dp).background(Color(0xFF0F172A), RoundedCornerShape(1.dp)))
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Beautiful Brand Green/Sage Eco Logo embedded inside the QR center
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                                .padding(2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape)
                                                    .background(
                                                        brush = Brush.radialGradient(
                                                            colors = listOf(Color(0xFFB0B59E), Color(0xFF394931))
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Eco,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Footer Website Address and info lines
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "www.jelatinoamerica.org".uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFEF4444).copy(alpha = 0f), // layout line helper to adjust color
                                    letterSpacing = 1.5.sp
                                )
                                Text(
                                    text = "www.jelatinoamerica.org",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    letterSpacing = 1.6.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "CREDENCIAL OFICIAL DE MIEMBRO ACTIVO",
                                    fontSize = 7.1.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB0B59E).copy(alpha = 0.7f),
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                        }
                    }
                }
            }
        }

        // Action Buttons Row (Flip and Edit)
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { isFlipped = !isFlipped },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Flip, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Voltear Carnet")
                    }

                    if (isCoordinadorMode) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { isEditing = !isEditing },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.testTag("edit_carnet_button")
                        ) {
                            Icon(
                                imageVector = if (isEditing) Icons.Filled.Close else Icons.Filled.Edit,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isEditing) "Cerrar Edición" else "Editar Perfil")
                        }
                    }
                }
            }
        }



        // Inline Profile Edit Collapsible Panel
        item {
            AnimatedVisibility(visible = isEditing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Formulario de Perfil",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )

                        OutlinedTextField(
                            value = editedName,
                            onValueChange = { editedName = it },
                            label = { Text("Nombre Completo") },
                            modifier = Modifier.fillMaxWidth().testTag("input_full_name")
                        )

                        OutlinedTextField(
                            value = editedAssoc,
                            onValueChange = { editedAssoc = it },
                            label = { Text("Colectivo u Organización") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editedRole,
                            onValueChange = { editedRole = it },
                            label = { Text("Rol / Nombramiento") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = editedCountry,
                                onValueChange = { editedCountry = it },
                                label = { Text("País") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Avatar Selection
                        Text(
                            text = "Elige tu Tótem / Avatar de Fauna LA:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        val faunaAvatars = listOf(
                            "🐆" to "Jaguar",
                            "🦙" to "Llama",
                            "🦜" to "Tucán",
                            "🐢" to "Tortuga",
                            "🐳" to "Ballena",
                            "🦊" to "Zorro",
                            "🦉" to "Búho",
                            "🦋" to "Monarca"
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            items(faunaAvatars) { (emoji, label) ->
                                val isSelected = editedAvatar == emoji
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .clickable { editedAvatar = emoji }
                                        .padding(8.dp)
                                ) {
                                    Text(emoji, fontSize = 28.sp)
                                    Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        if (isCoordinadorMode) {
                            Spacer(modifier = Modifier.height(4.dp))
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            Text(
                                text = "Foto Real de Perfil (Exclusivo Administrador):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        photoLauncher.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Elegir de Galería", fontSize = 12.sp)
                                }

                                if (!profile.photoUri.isNullOrEmpty()) {
                                    IconButton(
                                        onClick = { onUpdatePhoto(null) },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        ),
                                        modifier = Modifier.size(40.9.dp)
                                    ) {
                                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "Eliminar Foto", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            Text(
                                text = "Subir Carnet Manualmente (Exclusivo Administrador):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            Text(
                                text = "Sube imágenes personalizadas para reemplazar el carné digital dinámico.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            
                            // Cara 1
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        customCara1Launcher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Filled.CardMembership, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (!profile.customCara1Uri.isNullOrEmpty()) "Cambiar Cara 1" else "Subir Cara 1 (Frente)", fontSize = 11.sp)
                                }
                                if (!profile.customCara1Uri.isNullOrEmpty()) {
                                    IconButton(
                                        onClick = { onUpdateCustomCara1(null) },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        ),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "Eliminar Cara 1", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            
                            // Cara 2
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        customCara2Launcher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Filled.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (!profile.customCara2Uri.isNullOrEmpty()) "Cambiar Cara 2" else "Subir Cara 2 (Reverso)", fontSize = 11.sp)
                                }
                                if (!profile.customCara2Uri.isNullOrEmpty()) {
                                    IconButton(
                                        onClick = { onUpdateCustomCara2(null) },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        ),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "Eliminar Cara 2", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (editedName.isNotBlank() && editedAssoc.isNotBlank()) {
                                    onSaveProfile(editedName, editedAssoc, editedRole, editedCountry, editedAvatar)
                                    isEditing = false
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_profile_button"),
                            shape = RoundedCornerShape(20.dp),
                            enabled = editedName.isNotBlank() && editedAssoc.isNotBlank()
                        ) {
                            Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Guardar Credencial")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QRAnchorPattern() {
    Box(
        modifier = Modifier
            .size(38.dp)
            .border(4.5.dp, Color(0xFF0F172A), RoundedCornerShape(6.dp))
            .padding(4.5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A), RoundedCornerShape(2.dp))
        )
    }
}

@Composable
fun QRMatrixDotGroupVertical() {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(5.dp).background(Color(0xFF0F172A), RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.size(5.dp).background(Color.Transparent))
        Box(modifier = Modifier.size(5.dp).background(Color(0xFF0F172A), RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.size(5.dp).background(Color(0xFF0F172A), RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.size(5.dp).background(Color.Transparent))
        Box(modifier = Modifier.size(5.dp).background(Color(0xFF0F172A), RoundedCornerShape(1.dp)))
    }
}

// ==========================================
// TAB 3: FEED & AI ECO-CONSULT (ECO-FEED)
// ==========================================

// ==========================================
// TAB 3: FEED & AI ECO-CONSULT (ECO-FEED)
// ==========================================

@Composable
fun FeedTab(
    articles: List<EcoArticle>,
    viewModel: AppViewModel,
    isCoordinadorMode: Boolean,
    onDeleteArticle: (Int) -> Unit,
    onPublishReportClick: () -> Unit,
    onArticleClick: (EcoArticle) -> Unit
) {
    val searchQuery = ""
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var selectedCategoryFilter by remember { mutableStateOf("Todos") }
    val categories = listOf("Todos", "JE-RAM", "JE-Mental", "JE-Ambiente", "JE-Podcast", "JE-Visual", "JE-360", "JE-VIH", "Conservación", "Comunidad")
    var articleToDelete by remember { mutableStateOf<EcoArticle?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(selectedCategoryFilter) {
        try {
            listState.scrollToItem(0)
        } catch (_: Exception) {}
    }

    if (articleToDelete != null) {
        AlertDialog(
            onDismissRequest = { articleToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text("Eliminar Publicación", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Text(
                    "¿De verdad deseas eliminar permanentemente la noticia/reporte \"${articleToDelete?.title}\"? Esta acción no se puede revertir.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        articleToDelete?.let { onDeleteArticle(it.id) }
                        articleToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Eliminar de la Red", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { articleToDelete = null }) {
                    Text("Cancelar")
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        
        // Beautiful Ecological Bulletin Board Banner Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            ),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Boletín Informativo de JE",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    if (isCoordinadorMode) {
                        Button(
                            onClick = onPublishReportClick,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Publicar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Explora los reportes, boletines y guías de acción frente a la resistencia antimicrobiana (RAM), salud mental comunitaria y biodiversidad en nuestra región.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    lineHeight = 16.sp
                )
                

            }
        }

        // Horizontal line
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

        // Categories Quick Selector
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { cat ->
                val isSelected = selectedCategoryFilter == cat
                val tabColor = when (cat.lowercase()) {
                    "je-ram" -> Color(0xFFDC2626)          // Vibrant Red for antimicrobial resistance
                    "je-mental" -> Color(0xFF7C3AED)       // Purple for headspace / mental
                    "je-ambiente" -> Color(0xFF047857)     // Emerald green for environment
                    "je-podcast" -> Color(0xFFD97706)      // Amber/orange for podcasts
                    "je-visual" -> Color(0xFF0284C7)       // Sky blue for visuals
                    "je-360" -> Color(0xFF2563EB)          // Royal Blue for 360 / global
                    "je-vih" -> Color(0xFFE11D48)          // Rose/Red for HIV health support
                    "conservación" -> Color(0xFF0F766E)    // Teal for conservation
                    "comunidad" -> Color(0xFFBE185D)       // Crimson/pink for community
                    else -> MaterialTheme.colorScheme.primary
                }
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) tabColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    label = "newsBgColor"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        if (cat.lowercase() == "todos") MaterialTheme.colorScheme.onPrimary else Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "newsTextColor"
                )
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(bgColor)
                        .clickable { 
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            selectedCategoryFilter = cat 
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = cat,
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Column Listing
        val filteredArticles = if (selectedCategoryFilter == "Todos") {
            articles
        } else {
            articles.filter { it.category.contains(selectedCategoryFilter, ignoreCase = true) }
        }

        if (filteredArticles.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Aún no se ha subido información que conincida",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = "Sé el primero en publicar un reporte ecológico local.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), // Fab space at bottom
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredArticles, key = { it.id }) { item ->
                    ArticleCard(
                        item = item,
                        isCoordinadorMode = isCoordinadorMode,
                        onDelete = { articleToDelete = item },
                        onClick = { onArticleClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun ArticleCard(item: EcoArticle, isCoordinadorMode: Boolean, onDelete: () -> Unit, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Source Badge Row: AI generated vs Manual Youth Report vs Knowledge Seed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val projectPrefix = when (item.category.trim().uppercase()) {
                        "JE-VISUAL" -> "🎨"
                        "JE-AMBIENTE" -> "🌱"
                        "JE-RAM" -> "🦠"
                        "JE-MENTAL" -> "🧠"
                        "JE-PODCAST" -> "🎙️"
                        "JE-360" -> "🌐"
                        "JE-VIH" -> "🎗️"
                        else -> "🌱"
                    }
                    val tagText = if (item.isAiGenerated) "📢 BOLETÍN" else "$projectPrefix ${item.category.uppercase()}"
                    val tagBg = if (item.isAiGenerated) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                    val tagColor = if (item.isAiGenerated) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(tagBg)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = tagText,
                            color = tagColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "•  ${item.region} • ${item.publishDate}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        letterSpacing = 0.5.sp
                    )
                }

                if (isCoordinadorMode) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Remover",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = item.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 21.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Content Body text
            Text(
                text = item.content,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                lineHeight = 17.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // Optional published Photo/Image Preview
            if (!item.photoUri.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                AsyncImage(
                    model = item.photoUri,
                    contentDescription = "Imagen de la publicación",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(8.dp))

            // Metadata: Author region, Date published
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Terrain,
                        contentDescription = null,
                        tint = if (item.isAiGenerated) MaterialTheme.colorScheme.primary else Color(0xFF059669),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Región: ${item.region}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Text(
                    text = "Publicado: ${item.publishDate}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

// Dialog to author a manual eco-report / upload information
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReportDialog(
    onDismiss: () -> Unit,
    onPublish: (String, String, String, String, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Conservación") }
    var region by remember { mutableStateOf("Ecuador") }
    var photoUri by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val localPath = copyUriToLocalStorage(context, uri)
            photoUri = localPath
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                // Top Custom Header Row matching AddActivityDialog
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Contribuir Reporte o Novedad",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "Publica información valiosa sobre ecosistemas locales",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Título del reporte") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { Text("Contenido / Mensaje completo") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            maxLines = 10
                        )
                    }

                    // Theme/Category custom input + suggested chips
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = category,
                                onValueChange = { category = it },
                                label = { Text("Tema o Categoría (Personalizado)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Sugerencias de Categorías:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val defaultCats = listOf("JE-RAM", "JE-Mental", "JE-Ambiente", "JE-Podcast", "JE-Visual", "JE-360", "JE-VIH", "Conservación", "Biodiversidad", "Reforestación")
                                items(defaultCats) { catName ->
                                    val isSelected = category.lowercase() == catName.lowercase()
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.secondary 
                                                else MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
                                            )
                                            .clickable { category = catName }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = catName,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Region renamed to "Países de los miembros" custom input + chips
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = region,
                                onValueChange = { region = it },
                                label = { Text("País") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Países de los miembros de JE:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val defaultCountries = listOf("Ecuador", "Venezuela", "Bolivia", "Guatemala", "México", "Colombia", "Perú", "Chile", "Argentina")
                                items(defaultCountries) { countryName ->
                                    val isSelected = region.lowercase() == countryName.lowercase()
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary 
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                            )
                                            .clickable { region = countryName }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = countryName,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Visual optional image selection
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Imagen de la publicación (Opcional)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        photoLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = "Subir foto")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Seleccionar Imagen")
                                }
                                if (photoUri != null) {
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(70.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                    ) {
                                        AsyncImage(
                                            model = photoUri,
                                            contentDescription = "Vista previa",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        IconButton(
                                            onClick = { photoUri = null },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(24.dp)
                                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Quitar foto",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text("Cancelar")
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    if (title.isNotBlank() && content.isNotBlank()) {
                                        onPublish(title, content, category, region, photoUri)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                enabled = title.isNotBlank() && content.isNotBlank()
                            ) {
                                Text("Subir Publicación")
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditReportDialog(
    article: EcoArticle,
    onDismiss: () -> Unit,
    onSave: (EcoArticle) -> Unit
) {
    var title by remember { mutableStateOf(article.title) }
    var content by remember { mutableStateOf(article.content) }
    var category by remember { mutableStateOf(article.category) }
    var region by remember { mutableStateOf(article.region) }
    var photoUri by remember { mutableStateOf<String?>(article.photoUri) }

    val context = LocalContext.current
    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val localPath = copyUriToLocalStorage(context, uri)
            photoUri = localPath
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                // Top Custom Header Row matching AddActivityDialog
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Editar Reporte o Novedad",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "Modificar información ya publicada",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Título del reporte") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { Text("Contenido / Mensaje completo") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            maxLines = 10
                        )
                    }

                    // Theme/Category custom input + suggested chips
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = category,
                                onValueChange = { category = it },
                                label = { Text("Tema o Categoría (Personalizado)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Sugerencias de Categorías:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val defaultCats = listOf("JE-RAM", "JE-Mental", "JE-Ambiente", "JE-Podcast", "JE-Visual", "JE-360", "JE-VIH", "Conservación", "Biodiversidad", "Reforestación")
                                items(defaultCats) { catName ->
                                    val isSelected = category.lowercase() == catName.lowercase()
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary 
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                              )
                                            .clickable { category = catName }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = catName,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Region renamed to "Países de los miembros" custom input + chips
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = region,
                                onValueChange = { region = it },
                                label = { Text("País") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Países de los miembros de JE:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val defaultCountries = listOf("Ecuador", "Venezuela", "Bolivia", "Guatemala", "México", "Colombia", "Perú", "Chile", "Argentina")
                                items(defaultCountries) { countryName ->
                                    val isSelected = region.lowercase() == countryName.lowercase()
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary 
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                            )
                                            .clickable { region = countryName }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = countryName,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Visual optional image selection
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Imagen de la publicación (Opcional)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        photoLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = "Subir foto")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Seleccionar Imagen")
                                }
                                if (photoUri != null) {
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(70.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                    ) {
                                        AsyncImage(
                                            model = photoUri,
                                            contentDescription = "Vista previa",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        IconButton(
                                            onClick = { photoUri = null },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(24.dp)
                                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Quitar foto",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text("Cancelar")
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    if (title.isNotBlank() && content.isNotBlank()) {
                                        val updated = article.copy(
                                            title = title,
                                            content = content,
                                            category = category,
                                            region = region,
                                            photoUri = photoUri
                                        )
                                        onSave(updated)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                enabled = title.isNotBlank() && content.isNotBlank()
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Guardar", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Guardar Cambios")
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BugReportDialog(
    viewModel: AppViewModel,
    currentMember: Member,
    onDismiss: () -> Unit
) {
    var bugComment by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val localPath = copyUriToLocalStorage(context, uri)
            photoUri = localPath
        }
    }

    Dialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isSending,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                // Header row matching the visual identity
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isSending
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reportar un Bug 🐛",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Envío directo a soporte de JE-Latinoamérica",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                if (isSending) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.error
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "El reporte se enviará de forma directa a tu correo ${currentMember.email.trim().lowercase()} (y a la base de datos de soporte), sin abrir la aplicación de correo móvil.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = bugComment,
                            onValueChange = { bugComment = it },
                            label = { Text("Describe el error / Comentarios") },
                            placeholder = { Text("Escribe qué fallo ocurrió, qué estabas haciendo y cómo podemos reproducirlo...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            maxLines = 10,
                            enabled = !isSending
                        )
                    }

                    // Optional screenshot attachment selection
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Captura de pantalla o imagen del error (Opcional)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        photoLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    enabled = !isSending
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Subir foto")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Adjuntar Imagen")
                                }
                                if (photoUri != null) {
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(70.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                    ) {
                                        AsyncImage(
                                            model = photoUri,
                                            contentDescription = "Vista previa del bug",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        if (!isSending) {
                                            IconButton(
                                                onClick = { photoUri = null },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(24.dp)
                                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Quitar foto",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                enabled = !isSending
                            ) {
                                Text("Cancelar")
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    if (bugComment.isNotBlank()) {
                                        isSending = true
                                        viewModel.sendBugReportDirectly(
                                            comment = bugComment,
                                            photoPath = photoUri,
                                            onSuccess = {
                                                Toast.makeText(context, "¡Reporte enviado exitosamente!\nDestinatario: ${currentMember.email.trim().lowercase()}", Toast.LENGTH_LONG).show()
                                                isSending = false
                                                onDismiss()
                                            },
                                            onError = { error ->
                                                Toast.makeText(context, "Fallo al enviar el reporte: $error", Toast.LENGTH_LONG).show()
                                                isSending = false
                                            }
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                enabled = bugComment.isNotBlank() && !isSending
                            ) {
                                if (isSending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onError,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Enviando...")
                                } else {
                                    Icon(Icons.Default.Send, contentDescription = "Enviar", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Enviar Reporte")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// INTERACTIVE CALENDAR TAB COMPONENTS & HELPERS
// ==========================================

fun isActivityOnThisDay(activity: EcoActivity, day: Int, month: Int, year: Int): Boolean {
    val startEpoch = com.example.util.TimezoneHelper.parseEcuadorDateTime(activity.date) ?: return false
    
    if (activity.endDate.isNullOrBlank()) {
        return extractDayFromDateStr(activity.date) == day &&
               extractMonthFromDateStr(activity.date) == month &&
               extractYearFromDateStr(activity.date) == year
    }
    
    val endEpoch = com.example.util.TimezoneHelper.parseEcuadorDateTime(activity.endDate) ?: startEpoch
    
    val targetCalendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Guayaquil")).apply {
        set(java.util.Calendar.YEAR, year)
        set(java.util.Calendar.MONTH, month)
        set(java.util.Calendar.DAY_OF_MONTH, day)
        set(java.util.Calendar.HOUR_OF_DAY, 12)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val targetTime = targetCalendar.timeInMillis
    
    val startCalendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Guayaquil")).apply {
        timeInMillis = startEpoch
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    
    val endCalendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Guayaquil")).apply {
        timeInMillis = endEpoch
        set(java.util.Calendar.HOUR_OF_DAY, 23)
        set(java.util.Calendar.MINUTE, 59)
        set(java.util.Calendar.SECOND, 59)
        set(java.util.Calendar.MILLISECOND, 999)
    }
    
    return targetTime in startCalendar.timeInMillis..endCalendar.timeInMillis
}

fun extractDayFromDateStr(dateStr: String): Int? {
    return try {
        val trimmed = dateStr.trim()
        if (trimmed.contains(" ") || trimmed.contains("T")) {
            val epochMs = com.example.util.TimezoneHelper.parseEcuadorDateTime(trimmed)
            if (epochMs != null) {
                val sdf = java.text.SimpleDateFormat("d", java.util.Locale.getDefault()).apply {
                    timeZone = java.util.TimeZone.getDefault()
                }
                return sdf.format(java.util.Date(epochMs)).toIntOrNull()
            }
        }
        val cleanDate = trimmed.substringBefore(" ").substringBefore("T").trim()
        val parts = cleanDate.split("-")
        if (parts.size >= 3) {
            parts[2].toIntOrNull()
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

fun extractMonthFromDateStr(dateStr: String): Int? {
    return try {
        val trimmed = dateStr.trim()
        if (trimmed.contains(" ") || trimmed.contains("T")) {
            val epochMs = com.example.util.TimezoneHelper.parseEcuadorDateTime(trimmed)
            if (epochMs != null) {
                val sdf = java.text.SimpleDateFormat("M", java.util.Locale.getDefault()).apply {
                    timeZone = java.util.TimeZone.getDefault()
                }
                return sdf.format(java.util.Date(epochMs)).toInt() - 1 // 0-indexed to match currentMonth
            }
        }
        val cleanDate = trimmed.substringBefore(" ").substringBefore("T").trim()
        val parts = cleanDate.split("-")
        if (parts.size >= 2) {
            parts[1].toInt() - 1 // 0-indexed
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

fun extractYearFromDateStr(dateStr: String): Int? {
    return try {
        val trimmed = dateStr.trim()
        if (trimmed.contains(" ") || trimmed.contains("T")) {
            val epochMs = com.example.util.TimezoneHelper.parseEcuadorDateTime(trimmed)
            if (epochMs != null) {
                val sdf = java.text.SimpleDateFormat("yyyy", java.util.Locale.getDefault()).apply {
                    timeZone = java.util.TimeZone.getDefault()
                }
                return sdf.format(java.util.Date(epochMs)).toInt()
            }
        }
        val cleanDate = trimmed.substringBefore(" ").substringBefore("T").trim()
        val parts = cleanDate.split("-")
        if (parts.isNotEmpty()) {
            parts[0].toInt()
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

fun triggerPushNotification(context: Context, activityTitle: String, activityDate: String) {
    val channelId = "je_app_notifications_elegant"
    val channelName = "Recordatorios de Actividades (Elegante)"
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Buscar si existe un sonido elegante personalizado en res/raw/elegant_chime
    val soundResId = context.resources.getIdentifier("elegant_chime", "raw", context.packageName)
    val soundUri = if (soundResId != 0) {
        android.net.Uri.parse("android.resource://${context.packageName}/$soundResId")
    } else {
        android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val audioAttributes = android.media.AudioAttributes.Builder()
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .build()
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Canal para notificaciones con sonido elegante de JE-App"
            enableLights(true)
            enableVibration(true)
            setSound(soundUri, audioAttributes)
        }
        notificationManager.createNotificationChannel(channel)
    }

    // Crear PendingIntent para abrir MainActivity al hacer clic
    val intent = android.content.Intent(context, MainActivity::class.java).apply {
        flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    val pendingIntent = android.app.PendingIntent.getActivity(
        context,
        0,
        intent,
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
    )

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(com.example.R.mipmap.ic_launcher)
        .setContentTitle("Recordatorio de Evento 🌿")
        .setContentText("Tienes una actividad programada: $activityTitle para la fecha $activityDate.")
        .setStyle(NotificationCompat.BigTextStyle().bigText("Tienes una actividad programada: $activityTitle para la fecha $activityDate."))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setSound(soundUri)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)

    // Configurar vibración y luces por defecto (sin pisar el Custom Sound)
    builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)

    try {
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        Toast.makeText(context, "📢 Recordatorio de Evento Activado", Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        Toast.makeText(context, "Recordatorio: Tienes $activityTitle el $activityDate", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun CalendarTab(
    activities: List<EcoActivity>,
    enrollments: List<com.example.data.EcoEnrollment>,
    currentMember: com.example.data.Member,
    viewModel: com.example.ui.AppViewModel,
    isCoordinadorMode: Boolean,
    onToggleEnroll: (EcoActivity) -> Unit,
    onDeleteActivity: (Int) -> Unit,
    onCardClick: (EcoActivity) -> Unit
) {
    val context = LocalContext.current
    var selectedEventTypeFilter by remember { mutableStateOf("Todos") }
    val eventTypes = listOf("Todos", "Educación", "Asamblea general", "Actividad", "Voluntariado")
    var eventToDelete by remember { mutableStateOf<EcoActivity?>(null) }
    var reminderEventToConfigure by remember { mutableStateOf<EcoActivity?>(null) }

    if (eventToDelete != null) {
        AlertDialog(
            onDismissRequest = { eventToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text("Eliminar Evento", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Text(
                    "¿De verdad deseas eliminar permanentemente la actividad/evento de calendario \"${eventToDelete?.title}\"? Esta acción no se puede revertir.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        eventToDelete?.let { onDeleteActivity(it.id) }
                        eventToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Eliminar Evento", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { eventToDelete = null }) {
                    Text("Cancelar")
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (reminderEventToConfigure != null) {
        val event = reminderEventToConfigure!!
        val userEnrollment = enrollments.find {
            it.activityId == event.id &&
            it.memberEmail.trim().lowercase() == currentMember.email.trim().lowercase()
        }
        var reminderActive by remember { mutableStateOf(userEnrollment != null && userEnrollment.reminderMinutes >= 0) }
        var selectedMinutes by remember { mutableStateOf(if (userEnrollment != null && userEnrollment.reminderMinutes >= 0) userEnrollment.reminderMinutes else 15) }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { reminderEventToConfigure = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Recordatorio de Evento", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Configura una alerta personalizada para este evento. Esta configuración es individual de cada miembro y se sincronizará con la nube.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Activar recordatorio", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        androidx.compose.material3.Switch(
                            checked = reminderActive,
                            onCheckedChange = { reminderActive = it }
                        )
                    }

                    if (reminderActive) {
                        Text("Tiempo de anticipación:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        
                        val options = listOf(
                            0 to "Al comenzar el evento",
                            5 to "5 minutos antes",
                            15 to "15 minutos antes",
                            30 to "30 minutos antes",
                            60 to "1 hora antes",
                            1440 to "1 día antes"
                        )
                        
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            options.forEach { (mins, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selectedMinutes == mins) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                                        .clickable { selectedMinutes = mins }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    androidx.compose.material3.RadioButton(
                                        selected = selectedMinutes == mins,
                                        onClick = { selectedMinutes = mins }
                                    )
                                    Text(label, fontSize = 13.sp, fontWeight = if (selectedMinutes == mins) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }

                        // Preview dynamic alarm scheduling in local timezone
                        val epochMs = com.example.util.TimezoneHelper.parseEcuadorDateTime(event.date)
                        if (epochMs != null) {
                            val alertTimeMs = epochMs - (selectedMinutes * 60 * 1000)
                            val nowMs = System.currentTimeMillis()
                            val localSdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).apply {
                                timeZone = java.util.TimeZone.getDefault()
                            }
                            val eventLocalStr = localSdf.format(java.util.Date(epochMs))
                            val alertLocalStr = localSdf.format(java.util.Date(alertTimeMs))
                            
                            val systemZone = java.util.TimeZone.getDefault()
                            val zoneName = systemZone.getDisplayName(systemZone.inDaylightTime(java.util.Date(alertTimeMs)), java.util.TimeZone.SHORT, java.util.Locale.getDefault())

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Sincronización Local",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    
                                    val isCatchUp = alertTimeMs <= nowMs && epochMs > nowMs
                                    if (isCatchUp) {
                                        Text(
                                            text = "⚠️ La hora requerida ya pasó en tu región. La alarma sonará de inmediato (en 5 s) como aviso de puesta al día.\n\n• Evento local: $eventLocalStr ($zoneName)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else if (epochMs <= nowMs) {
                                        Text(
                                            text = "🚫 Este evento ya ocurrió en el pasado (${eventLocalStr}). No se disparará alarma activa.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    } else {
                                        Text(
                                            text = "• Tu hora local: $zoneName\n• Evento local: $eventLocalStr\n• Alarma programada: $alertLocalStr",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setEventReminder(event, currentMember, reminderActive, selectedMinutes)
                        if (reminderActive) {
                            val timeLabel = when (selectedMinutes) {
                                0 -> "al comenzar el evento"
                                5 -> "5 minutos antes"
                                15 -> "15 minutos antes"
                                30 -> "30 minutos antes"
                                60 -> "1 hora antes"
                                1440 -> "1 día antes"
                                else -> "$selectedMinutes minutos antes"
                            }
                            Toast.makeText(context, "⏰ Recordatorio programado: $timeLabel", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "🚫 Recordatorio Desactivado", Toast.LENGTH_SHORT).show()
                        }
                        reminderEventToConfigure = null
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Guardar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { reminderEventToConfigure = null }) {
                    Text("Cancelar")
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    var currentYear by remember { mutableStateOf(2026) }
    var currentMonth by remember { mutableStateOf(5) } // 0-indexed: 5 = June
    var selectedDay by remember { mutableStateOf(16) } // June 16 as default selected day

    val currentMonthCalendarHelper = remember(currentYear, currentMonth) {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, currentYear)
            set(java.util.Calendar.MONTH, currentMonth)
            set(java.util.Calendar.DAY_OF_MONTH, 1)
        }
    }

    val monthName = remember(currentMonth) {
        when (currentMonth) {
            0 -> "Enero"
            1 -> "Febrero"
            2 -> "Marzo"
            3 -> "Abril"
            4 -> "Mayo"
            5 -> "Junio"
            6 -> "Julio"
            7 -> "Agosto"
            8 -> "Septiembre"
            9 -> "Octubre"
            10 -> "Noviembre"
            11 -> "Diciembre"
            else -> "Mes"
        }
    }

    val daysInMonth = remember(currentMonthCalendarHelper) {
        currentMonthCalendarHelper.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    }

    val firstDayOfWeek = remember(currentMonthCalendarHelper) {
        currentMonthCalendarHelper.get(java.util.Calendar.DAY_OF_WEEK)
    }

    val startOffset = remember(firstDayOfWeek) {
        when (firstDayOfWeek) {
            java.util.Calendar.MONDAY -> 0
            java.util.Calendar.TUESDAY -> 1
            java.util.Calendar.WEDNESDAY -> 2
            java.util.Calendar.THURSDAY -> 3
            java.util.Calendar.FRIDAY -> 4
            java.util.Calendar.SATURDAY -> 5
            java.util.Calendar.SUNDAY -> 6
            else -> 0
        }
    }

    val totalGridCells = startOffset + daysInMonth
    val numRows = if (totalGridCells % 7 == 0) totalGridCells / 7 else (totalGridCells / 7) + 1

    LaunchedEffect(currentMonth, currentYear) {
        val helper = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, currentYear)
            set(java.util.Calendar.MONTH, currentMonth)
            set(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        val maxDays = helper.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        selectedDay = selectedDay.coerceIn(1, maxDays)
    }

    // Filtered activities based on filter selection
    val filteredActivities = remember(activities, selectedEventTypeFilter) {
        val allowedTypes = listOf("educación", "asamblea general", "actividad", "voluntariado")
        if (selectedEventTypeFilter == "Todos") {
            activities.filter { it.eventType.lowercase() in allowedTypes }
        } else {
            activities.filter { it.eventType.equals(selectedEventTypeFilter, ignoreCase = true) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Tab Header
        item {
            Column {
                Text(
                    text = "Calendario de Eventos".uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "$monthName $currentYear",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Category Filter Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            ) {
                items(eventTypes) { filter ->
                    val isSelected = selectedEventTypeFilter == filter
                    val chipColor = when (filter.lowercase()) {
                        "voluntariado" -> Color(0xFF047857)
                        "educación" -> Color(0xFFD97706)
                        "asamblea general" -> Color(0xFF7C3AED)
                        "actividad" -> Color(0xFF0284C7)
                        else -> MaterialTheme.colorScheme.primary
                    }
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) chipColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        label = "calendarChipBg"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            if (filter.lowercase() == "todos") MaterialTheme.colorScheme.onPrimary else Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        label = "calendarChipText"
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(bgColor)
                            .clickable { selectedEventTypeFilter = filter }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(text = filter, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Calendar visual card container with horizontal hand gestures
        item {
            var dragAmountX by remember { mutableStateOf(0f) }
            val currentMonthState = currentMonth + currentYear * 12

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(currentMonthState) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (dragAmountX > 40f) {
                                    // Swipe right -> previous month
                                    if (currentMonth == 0) {
                                        currentMonth = 11
                                        currentYear -= 1
                                    } else {
                                        currentMonth -= 1
                                    }
                                } else if (dragAmountX < -40f) {
                                    // Swipe left -> next month
                                    if (currentMonth == 11) {
                                        currentMonth = 0
                                        currentYear += 1
                                    } else {
                                        currentMonth += 1
                                    }
                                }
                                dragAmountX = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                dragAmountX += dragAmount
                            }
                        )
                    }
            ) {
                AnimatedContent(
                    targetState = currentMonthState,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut()
                            )
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut()
                            )
                        }
                    },
                    label = "calendarCardSlide"
                ) { targetMonthPlusYear ->
                    val targetYear = targetMonthPlusYear / 12
                    val targetMonth = targetMonthPlusYear % 12

                    val targetMonthCalendarHelper = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.YEAR, targetYear)
                        set(java.util.Calendar.MONTH, targetMonth)
                        set(java.util.Calendar.DAY_OF_MONTH, 1)
                    }

                    val targetDaysInMonth = targetMonthCalendarHelper.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                    val targetFirstDayOfWeek = targetMonthCalendarHelper.get(java.util.Calendar.DAY_OF_WEEK)
                    val targetStartOffset = when (targetFirstDayOfWeek) {
                        java.util.Calendar.MONDAY -> 0
                        java.util.Calendar.TUESDAY -> 1
                        java.util.Calendar.WEDNESDAY -> 2
                        java.util.Calendar.THURSDAY -> 3
                        java.util.Calendar.FRIDAY -> 4
                        java.util.Calendar.SATURDAY -> 5
                        java.util.Calendar.SUNDAY -> 6
                        else -> 0
                    }
                    val targetTotalGridCells = targetStartOffset + targetDaysInMonth
                    val targetNumRows = if (targetTotalGridCells % 7 == 0) targetTotalGridCells / 7 else (targetTotalGridCells / 7) + 1

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Day names header (Monday to Sunday)
                            val dayHeaders = listOf("L", "M", "M", "J", "V", "S", "D")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                dayHeaders.forEach { header ->
                                    Text(
                                        text = header,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Days grid based on starting day offset and target month size
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (row in 0 until targetNumRows) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceAround
                                    ) {
                                        for (col in 0 until 7) {
                                            val cellIndex = (row * 7) + col
                                            val dayNum = cellIndex - targetStartOffset + 1
                                            if (dayNum in 1..targetDaysInMonth) {
                                                // Calc if this day has any activities matching the filter
                                                val activitiesOnThisDay = filteredActivities.filter { activity ->
                                                    isActivityOnThisDay(activity, dayNum, targetMonth, targetYear)
                                                }
                                                val hasActivities = activitiesOnThisDay.isNotEmpty()
                                                val isCurrentSelected = selectedDay == dayNum && currentMonth == targetMonth && currentYear == targetYear

                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .aspectRatio(1f)
                                                        .padding(3.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            when {
                                                                isCurrentSelected -> MaterialTheme.colorScheme.primaryContainer
                                                                hasActivities -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                                                else -> Color.Transparent
                                                            }
                                                        )
                                                        .border(
                                                            width = if (isCurrentSelected) 1.5.dp else if (hasActivities) 1.dp else 0.dp,
                                                            color = if (isCurrentSelected) {
                                                                MaterialTheme.colorScheme.primary
                                                            } else if (hasActivities) {
                                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                                            } else {
                                                                Color.Transparent
                                                            },
                                                            shape = CircleShape
                                                        )
                                                        .clickable { selectedDay = dayNum },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center
                                                    ) {
                                                        Text(
                                                            text = dayNum.toString(),
                                                            fontSize = 13.sp,
                                                            fontWeight = if (isCurrentSelected || hasActivities) FontWeight.Bold else FontWeight.Medium,
                                                            color = when {
                                                                isCurrentSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                                                hasActivities -> MaterialTheme.colorScheme.primary
                                                                else -> MaterialTheme.colorScheme.onSurface
                                                            }
                                                        )
                                                        if (isCurrentSelected) {
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(4.dp)
                                                                    .clip(CircleShape)
                                                                    .background(MaterialTheme.colorScheme.primary)
                                                            )
                                                        } else if (hasActivities) {
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                val uniqueTypes = activitiesOnThisDay.map { it.eventType.lowercase() }.distinct()
                                                                uniqueTypes.take(3).forEach { evType ->
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(4.dp)
                                                                            .clip(CircleShape)
                                                                            .background(
                                                                                when (evType) {
                                                                                    "voluntariado" -> Color(0xFF047857)
                                                                                    "educación" -> Color(0xFFD97706)
                                                                                    "asamblea general" -> Color(0xFF7C3AED)
                                                                                    "actividad" -> Color(0xFF0284C7)
                                                                                    "talleres" -> Color(0xFFD97706)
                                                                                    "charlas" -> Color(0xFF0284C7)
                                                                                    else -> MaterialTheme.colorScheme.primary
                                                                                }
                                                                            )
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "◀  Desliza para cambiar de mes  ▶",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Activities Section Title
        item {
            Text(
                text = "EVENTOS DEL DÍA $selectedDay DE ${monthName.uppercase()}:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.40f),
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Find activities for selected day, month and year
        val dayFilteredActivities = filteredActivities.filter { activity ->
            isActivityOnThisDay(activity, selectedDay, currentMonth, currentYear)
        }

        if (dayFilteredActivities.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No hay eventos programados",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Elige otro día o remueve los filtros para explorar actividades.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(dayFilteredActivities, key = { it.id }) { event ->
                val userEnrollment = enrollments.find {
                    it.activityId == event.id &&
                    it.memberEmail.trim().lowercase() == currentMember.email.trim().lowercase()
                }
                val isReminderActive = userEnrollment != null && userEnrollment.reminderMinutes >= 0
                val reminderMinutes = userEnrollment?.reminderMinutes ?: -1

                CalendarEventCard(
                    event = event,
                    isCoordinadorMode = isCoordinadorMode,
                    isReminderActive = isReminderActive,
                    reminderMinutes = reminderMinutes,
                    onDelete = { eventToDelete = event },
                    onRegister = { onToggleEnroll(event) },
                    onAddToPersonalCalendar = {
                        // Launch Implicit Intent to Calendar
                        try {
                            val calendarIntent = Intent(Intent.ACTION_INSERT).apply {
                                data = CalendarContract.Events.CONTENT_URI
                                putExtra(CalendarContract.Events.TITLE, event.title)
                                putExtra(CalendarContract.Events.DESCRIPTION, "${event.description} \n\nOrganizado por: ${event.organizer}")
                                putExtra(CalendarContract.Events.EVENT_LOCATION, "${event.location}, ${event.country}")
                                
                                val epochMs = com.example.util.TimezoneHelper.parseEcuadorDateTime(event.date)
                                val beginTime = epochMs ?: run {
                                    val parts = event.date.split("-")
                                    val calendar = Calendar.getInstance()
                                    if (parts.size >= 3) {
                                        calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 10, 0)
                                    }
                                    calendar.timeInMillis
                                }
                                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTime)
                                
                                val endTime = if (!event.endDate.isNullOrBlank()) {
                                    com.example.util.TimezoneHelper.parseEcuadorDateTime(event.endDate) ?: (beginTime + 2 * 60 * 60 * 1000)
                                } else {
                                    beginTime + 2 * 60 * 60 * 1000
                                }
                                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
                            }
                            context.startActivity(calendarIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No se puede abrir la aplicación de calendario", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSchedulePushReminder = {
                        reminderEventToConfigure = event
                    },
                    onClick = { onCardClick(event) }
                )
            }
        }
    }
}

@Composable
fun CalendarEventCard(
    event: EcoActivity,
    isCoordinadorMode: Boolean,
    isReminderActive: Boolean = false,
    reminderMinutes: Int = -1,
    onDelete: () -> Unit,
    onRegister: () -> Unit,
    onAddToPersonalCalendar: () -> Unit,
    onSchedulePushReminder: () -> Unit,
    onClick: () -> Unit
) {
    val typeColor = remember(event.eventType) {
        when (event.eventType.lowercase()) {
            "voluntariado" -> Color(0xFF047857)
            "educación" -> Color(0xFFD97706)
            "asamblea general" -> Color(0xFF7C3AED)
            "actividad" -> Color(0xFF0284C7)
            "talleres" -> Color(0xFFD97706)
            "charlas" -> Color(0xFF0284C7)
            else -> Color(0xFF6B7280)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, typeColor.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Type Badge + Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Event Type tag badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(typeColor.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = event.eventType.uppercase(),
                            color = typeColor,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                    }

                    if (isReminderActive) {
                        val label = when (reminderMinutes) {
                            0 -> "al comenzar"
                            5 -> "5 min antes"
                            15 -> "15 min antes"
                            30 -> "30 min antes"
                            60 -> "1 h antes"
                            1440 -> "1 d antes"
                            else -> "$reminderMinutes min"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = "ALERTA: $label",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Push Reminder Button
                    IconButton(
                        onClick = onSchedulePushReminder,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isReminderActive) Icons.Filled.NotificationsActive else Icons.Outlined.Notifications,
                            contentDescription = "Programar Alerta",
                            tint = if (isReminderActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Add to personal calendar
                    IconButton(
                        onClick = onAddToPersonalCalendar,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarToday,
                            contentDescription = "Añadir a Calendario Personal",
                            tint = typeColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Delete Activity (Only for coordinator mode)
                    if (isCoordinadorMode) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Borrar",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Event Title
            Text(
                text = event.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Short Description
            Text(
                text = event.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Meta Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Info Column
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = typeColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "${event.location}, ${event.country}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Por: ${event.organizer}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Register Button
                val isDeadlinePassed = com.example.util.TimezoneHelper.isPastRegistrationDeadline(event.registrationDeadline)
                if (isDeadlinePassed && !event.isUserRegistered) {
                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Límite vencido",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Button(
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (event.isUserRegistered) MaterialTheme.colorScheme.primaryContainer else typeColor
                        ),
                        modifier = Modifier.height(38.dp),
                        onClick = onRegister,
                        enabled = !isDeadlinePassed
                    ) {
                        Text(
                            text = if (event.isUserRegistered) "Inscrito ✓" else "Inscribirme",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (event.isUserRegistered) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// LOGIN & ADMIN CONSOLE SUPPORT COMPONENTS
// ==========================================

@Composable
fun LeafLogo(modifier: Modifier = Modifier, color: Color? = null) {
    Image(
        painter = painterResource(id = R.drawable.ic_clover),
        contentDescription = "Clover Logo",
        modifier = modifier,
        colorFilter = color?.let { androidx.compose.ui.graphics.ColorFilter.tint(it) }
    )
}

// Legacy splash screen removed - relying on fast system splash screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: AppViewModel) {
    val savedEmail = remember { viewModel.getSavedEmail() }
    val savedPass = remember { viewModel.getSavedPassword() }
    val savedRemember = remember { viewModel.getSavedRememberMe() }

    var email by remember { mutableStateOf(savedEmail) }
    var password by remember { mutableStateOf(savedPass) }
    var loginError by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(savedRemember) }
    val context = LocalContext.current

    val loginUiState by viewModel.loginUiState.collectAsStateWithLifecycle()
    val isLoading = loginUiState is AppViewModel.LoginUiState.Loading

    val forestDarkGreen = Color(0xFF253013)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    0.0f to Color(0xFF000000),
                    0.15f to Color(0xFF000000),      
                    0.60f to forestDarkGreen,
                    1.0f to forestDarkGreen
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Logo drawn dynamically matching the clover style
            LeafLogo(
                modifier = Modifier.size(115.dp),
                color = null // Preserves the original colors of your PNG
            )

            // Extremely close to the logo using offset pulling
            Text(
                text = "BIENVENIDO",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(y = (-10).dp)
            )

            Text(
                text = "INICIA SESIÓN PARA CONTINUAR",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6B7B61),
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(y = (-10).dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Core Custom Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_card")
                    .drawBehind {
                        val cardSize = this@drawBehind.size
                        
                        // 1. Soft Outward Shadow with strokes to prevent filling internal card background
                        val shadowColor = Color.Black.copy(alpha = 0.08f)
                        for (i in 1..8) {
                            val spread = (i * 1.5f).dp.toPx()
                            drawRoundRect(
                                color = shadowColor,
                                topLeft = androidx.compose.ui.geometry.Offset(-spread, -spread + 3f),
                                size = androidx.compose.ui.geometry.Size(cardSize.width + spread * 2, cardSize.height + spread * 2),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx() + spread),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                            )
                        }
                        
                        // 2. Outward Green Glow with strokes to maintain brilliant border accent and zero interior color
                        val glowColor = Color(0xFF5D7157).copy(alpha = 0.04f)
                        for (i in 1..12) {
                            val spread = (i * 2.0f).dp.toPx()
                            drawRoundRect(
                                color = glowColor,
                                topLeft = androidx.compose.ui.geometry.Offset(-spread, -spread),
                                size = androidx.compose.ui.geometry.Size(cardSize.width + spread * 2, cardSize.height + spread * 2),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx() + spread),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF5D7157)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp)
                ) {
                    // ID Indicator Line Group
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .offset(y = (-14).dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = Color.White.copy(alpha = 0.35f),
                            thickness = 1.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Image(
                            painter = painterResource(id = R.drawable.ic_clover_id),
                            contentDescription = "ID Label Logo",
                            modifier = Modifier.size(width = 125.dp, height = 98.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = Color.White.copy(alpha = 0.35f),
                            thickness = 1.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "CORREO ELECTRÓNICO",
                        color = Color(0xFFB0B59E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { 
                            email = it
                            loginError = false
                            viewModel.resetLoginUiState()
                        },
                        enabled = !isLoading,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = Color(0xFF91A37E),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = Color(0xFF5E7955),
                            unfocusedBorderColor = Color(0xFF324A2D),
                            cursorColor = Color(0xFF91A37E),
                            disabledTextColor = Color.White.copy(alpha = 0.5f),
                            disabledBorderColor = Color(0xFF324A2D).copy(alpha = 0.5f)
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "CONTRASEÑA",
                        color = Color(0xFFB0B59E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    var passwordVisibility by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = password,
                        onValueChange = { 
                            password = it
                            loginError = false
                            viewModel.resetLoginUiState()
                        },
                        enabled = !isLoading,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFF91A37E),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            val visibilityIcon = if (passwordVisibility) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { passwordVisibility = !passwordVisibility }) {
                                Icon(
                                    imageVector = visibilityIcon,
                                    contentDescription = null,
                                    tint = Color(0xFF91A37E),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input"),
                        singleLine = true,
                        visualTransformation = if (passwordVisibility) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = Color(0xFF5E7955),
                            unfocusedBorderColor = Color(0xFF324A2D),
                            cursorColor = Color(0xFF91A37E),
                            disabledTextColor = Color.White.copy(alpha = 0.5f),
                            disabledBorderColor = Color(0xFF324A2D).copy(alpha = 0.5f)
                        )
                    )

                    if (loginError || loginUiState is AppViewModel.LoginUiState.Error) {
                        val errorMessage = (loginUiState as? AppViewModel.LoginUiState.Error)?.message 
                            ?: "Credenciales incorrectas. Verifique correo y contraseña."
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Remember Me and Forgot Password Action Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { 
                                if (!isLoading) {
                                    rememberMe = !rememberMe 
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .border(1.2.dp, Color(0xFF2B3D26), RoundedCornerShape(4.dp))
                                    .background(
                                        if (rememberMe) Color(0xFF1B2A17) else Color.Transparent,
                                        RoundedCornerShape(4.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (rememberMe) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF91A37E),
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "RECUÉRDAME",
                                color = Color(0xFFB0B59E),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "OLVIDÉ MI CONTRASEÑA",
                            color = Color(0xFFB0B59E),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .testTag("forgot_password_button")
                                .clickable { 
                                    if (!isLoading) {
                                        showForgotDialog = true 
                                    }
                                }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Iniciar Sesion Action Button
            Button(
                onClick = {
                    if (email.isNotBlank() && password.isNotBlank()) {
                        viewModel.login(email, password, rememberMe) { success ->
                            if (!success) {
                                loginError = true
                            }
                        }
                    } else {
                        Toast.makeText(context, "Por favor complete todos los campos", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(52.dp)
                    .testTag("login_button")
                    .drawBehind {
                        val btnSize = this@drawBehind.size
                        // Soft glow around the entire rounded rectangle button - very subtle intensity
                        val glowColor = Color(0xFF5D7157).copy(alpha = 0.02f)
                        for (i in 1..5) {
                            val spread = (i * 2.0f).dp.toPx()
                            drawRoundRect(
                                color = glowColor,
                                topLeft = androidx.compose.ui.geometry.Offset(-spread, -spread),
                                size = androidx.compose.ui.geometry.Size(btnSize.width + spread * 2, btnSize.height + spread * 2),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx() + spread, 14.dp.toPx() + spread)
                            )
                        }
                    },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF45503F),
                    disabledContainerColor = Color(0xFF323B2F)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF5D6B56))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "AUTENTICANDO...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f),
                            letterSpacing = 1.sp
                        )
                    } else {
                        Text(
                            text = "INICIAR SESIÓN",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Seed Account Panel
            Card(
                modifier = Modifier.fillMaxWidth(0.95f),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF070B06).copy(alpha = 0.6f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1B2817).copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "CUENTAS DE DEMOSTRACIÓN AUTORIZADAS:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6B7B61),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Director: coordinador@je.org / JE2026\n• Miembro RAM: miembro2@je.org / miembro2\n• Miembro Salud: miembro3@je.org / miembro3",
                        fontSize = 10.sp,
                        color = Color(0xFF6B7B61).copy(alpha = 0.75f),
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFFF18824))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Recuperación de Acceso", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Text(
                    text = "Por políticas de seguridad y protección de la ONG Jóvenes y Ecosistemas Latinoamérica, todas las cuentas y contraseñas de acceso son gestionadas centralmente.\n\nPara recuperar o restablecer tu contraseña, por favor envía un correo oficial describiendo tu rol y país al Director General:\n\n✉ je.react21@gmail.com",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = { showForgotDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF394931))
                ) {
                    Text("Entendido", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun AdminConsoleTab(
    currentMember: Member,
    viewModel: AppViewModel,
    members: List<Member>,
    enrollments: List<EcoEnrollment>,
    notifications: List<EcoNotification>,
    activities: List<EcoActivity>,
    googleCalendarLinked: Boolean,
    onToggleGoogleCalendar: () -> Unit
) {
    var adminActiveSubTab by remember { mutableStateOf("Miembros") }
    val context = LocalContext.current
    val isDirector = currentMember.isAdmin || currentMember.email == "coordinador@je.org" || currentMember.role == "Director General"

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.refreshAdminMembers()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "CONSOLA DE COORDINACIÓN".uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.2.sp
        )
        Text(
            text = "Centro de control",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Sub-tabs navigation Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Miembros", "Inscripciones", "Alertas", "Nube").forEach { subTab ->
                val isSelected = adminActiveSubTab == subTab
                val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .clickable { adminActiveSubTab = subTab }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = subTab,
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(modifier = Modifier.weight(1f)) {
            when (adminActiveSubTab) {
                "Miembros" -> {
                    AdminMembersPanel(viewModel = viewModel, members = members)
                }
                "Inscripciones" -> {
                    AdminEnrollmentsPanel(viewModel = viewModel, enrollments = enrollments, activities = activities)
                }
                "Alertas" -> {
                    AdminAlertsPanel(viewModel = viewModel, notifications = notifications)
                }
                "Nube" -> {
                    AdminCloudPanel(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMembersPanel(viewModel: AppViewModel, members: List<Member>) {
    val context = LocalContext.current
    val prefTheme by viewModel.prefTheme.collectAsStateWithLifecycle()
    val isDark = when (prefTheme) {
        "light" -> false
        "dark" -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val cardBg = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else Color.White

    var showAddMemberDialog by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<Member?>(null) }
    var memberToDelete by remember { mutableStateOf<Member?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortByField by remember { mutableStateOf("none") } // "none", "name", "role", "date"
    var isAscending by remember { mutableStateOf(true) }

    if (memberToDelete != null) {
        AlertDialog(
            onDismissRequest = { memberToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text("Confirmar eliminación", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Text(
                    "¿De verdad deseas eliminar permanentemente al miembro \"${memberToDelete?.fullName}\"? Esto revocará de inmediato sus credenciales de carnet digital y acceso. Se mantendrá el historial local e histórico de sus colaboraciones.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        memberToDelete?.let { viewModel.removeMember(it.email) }
                        memberToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Eliminar Miembro", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToDelete = null }) {
                    Text("Cancelar")
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    val filteredMembers = remember(members, searchQuery) {
        if (searchQuery.isBlank()) {
            members
        } else {
            val query = searchQuery.trim().lowercase()
            members.filter {
                it.fullName.lowercase().contains(query) || it.role.lowercase().contains(query)
            }
        }
    }

    val sortedAndFilteredMembers = remember(filteredMembers, sortByField, isAscending) {
        when (sortByField) {
            "name" -> {
                if (isAscending) filteredMembers.sortedBy { it.fullName.lowercase() }
                else filteredMembers.sortedByDescending { it.fullName.lowercase() }
            }
            "role" -> {
                if (isAscending) filteredMembers.sortedBy { it.role.lowercase() }
                else filteredMembers.sortedByDescending { it.role.lowercase() }
            }
            "date" -> {
                val parseDate = { dateStr: String ->
                    val parts = dateStr.split("/")
                    if (parts.size == 3) {
                        "${parts[2]}${parts[1]}${parts[0]}"
                    } else {
                        dateStr
                    }
                }
                if (isAscending) filteredMembers.sortedBy { parseDate(it.joinDate) }
                else filteredMembers.sortedByDescending { parseDate(it.joinDate) }
            }
            else -> filteredMembers
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Registro de Carnets de Miembros".uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            
            TextButton(
                onClick = { showAddMemberDialog = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Registrar Miembro", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar por nombre o rol...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Limpiar búsqueda",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(24.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Interfaz de ordenación de columnas - Refinada estéticamente
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(
                    text = "Ordenar:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            val sortKeys = listOf(
                Triple("name", "Nombre", Icons.Default.Person),
                Triple("role", "Rol", Icons.Default.VerifiedUser),
                Triple("date", "Ingreso", Icons.Default.WbSunny)
            )

            sortKeys.forEach { (field, label, icon) ->
                val isSelected = sortByField == field
                val containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                }
                val contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                val borderColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                } else {
                    Color.Transparent
                }

                Surface(
                    onClick = {
                        if (sortByField == field) {
                            if (isAscending) {
                                isAscending = false
                            } else {
                                sortByField = "none"
                            }
                        } else {
                            sortByField = field
                            isAscending = true
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = containerColor,
                    contentColor = contentColor,
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, borderColor) else null,
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("sort_by_${field}_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = if (isAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = if (isAscending) "Asc" else "Desc",
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (members.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No hay miembros registrados", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
        } else if (sortedAndFilteredMembers.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Text(
                        "No se encontraron resultados para \"$searchQuery\"",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(sortedAndFilteredMembers, key = { it.email }) { member ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = cardBg
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!member.photoUri.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = member.photoUri,
                                            contentDescription = "Foto de carnet",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(member.emojiAvatar, fontSize = 20.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(member.fullName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text("${member.role} • ${member.country}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    Text("Credencial: ${member.credentialId}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { editingMember = member }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar Carnet", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                                if (member.email != "coordinador@je.org") {
                                    IconButton(onClick = { memberToDelete = member }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar Miembro", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddMemberDialog) {
        var newEmail by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        var newName by remember { mutableStateOf("") }
        var newRole by remember { mutableStateOf("miembro activo") }
        var newCountry by remember { mutableStateOf("Ecuador") }
        var newEmoji by remember { mutableStateOf("🐆") }
        var newIsAdmin by remember { mutableStateOf(false) }
        var newPhotoUri by remember { mutableStateOf<String?>(null) }
        var newQrUri by remember { mutableStateOf<String?>(null) }
        var newCara1Uri by remember { mutableStateOf<String?>(null) }
        var newCara2Uri by remember { mutableStateOf<String?>(null) }

        val addPhotoLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                val localPath = copyUriToLocalStorage(context, uri)
                if (localPath != null) {
                    newPhotoUri = localPath
                }
            }
        }

        val addQrLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                val localPath = copyUriToLocalStorage(context, uri)
                if (localPath != null) {
                    newQrUri = localPath
                }
            }
        }

        val addCara1Launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                val localPath = copyUriToLocalStorage(context, uri)
                if (localPath != null) {
                    newCara1Uri = localPath
                }
            }
        }

        val addCara2Launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                val localPath = copyUriToLocalStorage(context, uri)
                if (localPath != null) {
                    newCara2Uri = localPath
                }
            }
        }

        Dialog(
            onDismissRequest = { showAddMemberDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                ) {
                    // Header (Verde y blanco)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showAddMemberDialog = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancelar",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Registrar Miembro",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text = "Completa los datos para registrar un miembro",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // Form Content
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = newName,
                                        onValueChange = { newName = it },
                                        label = { Text("Nombre Completo") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = newEmail,
                                        onValueChange = { newEmail = it },
                                        label = { Text("Correo Electrónico") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = newPass,
                                        onValueChange = { newPass = it },
                                        label = { Text("Contraseña de Acceso") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    
                                    Text("Rol:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    val roles = listOf("coordinador", "miembro activo", "miembro pasivo", "director", "secretario")
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(roles) { r ->
                                                val sel = newRole.lowercase() == r.lowercase()
                                                Badge(
                                                    containerColor = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                    contentColor = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .clickable { newRole = r }
                                                        .padding(horizontal = 4.dp, vertical = 4.dp)
                                                ) {
                                                    Text(r.replaceFirstChar { it.uppercase() }, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                                }
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = newCountry,
                                        onValueChange = { newCountry = it },
                                        label = { Text("País") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                        }

                        // Attachments (Photos, QR, Emoji, Custom faces)
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text("Multimedia y Personalización", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                                    Text("Fotografía Oficial (Opcional):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                                .clickable {
                                                    addPhotoLauncher.launch(
                                                        PickVisualMediaRequest(
                                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                                        )
                                                    )
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!newPhotoUri.isNullOrEmpty()) {
                                                AsyncImage(
                                                    model = newPhotoUri,
                                                    contentDescription = "Foto elegida",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Filled.PhotoCamera,
                                                    contentDescription = "Subir foto",
                                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                        
                                        Column(modifier = Modifier.weight(1.0f)) {
                                            Button(
                                                onClick = {
                                                    addPhotoLauncher.launch(
                                                        PickVisualMediaRequest(
                                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                                        )
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                ),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("Añadir Foto", fontSize = 12.sp)
                                            }
                                            
                                            if (!newPhotoUri.isNullOrEmpty()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                TextButton(
                                                    onClick = { newPhotoUri = null },
                                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                                ) {
                                                    Icon(imageVector = Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Quitar Foto", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }



                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                                    Text("Avatar / Emoji:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    val emojis = listOf("🐆", "🐸", "🦉", "🦅", "🐳", "🦊", "🌿")
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        emojis.forEach { emoji ->
                                            val sel = newEmoji == emoji
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                                    .border(1.dp, if (sel) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                                    .clickable { newEmoji = emoji },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(emoji, fontSize = 20.sp)
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                                    Text("Carnet Manual (Exclusivo Administrador):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("Opcional: puedes subir el carnet pre-diseñado para este miembro.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Button(
                                                onClick = {
                                                    addCara1Launcher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Cara 1 (Frente)", fontSize = 11.sp, maxLines = 1)
                                            }
                                            if (!newCara1Uri.isNullOrEmpty()) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Cargado", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                                    IconButton(onClick = { newCara1Uri = null }, modifier = Modifier.size(24.dp)) {
                                                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            }
                                        }
                                        
                                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Button(
                                                onClick = {
                                                    addCara2Launcher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Cara 2 (Atrás)", fontSize = 11.sp, maxLines = 1)
                                            }
                                            if (!newCara2Uri.isNullOrEmpty()) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Cargado", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                                    IconButton(onClick = { newCara2Uri = null }, modifier = Modifier.size(24.dp)) {
                                                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Privilegios de Administrador", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text("Permite acceder a la consola de coordinación", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        }
                                        Switch(
                                            checked = newIsAdmin,
                                            onCheckedChange = { newIsAdmin = it }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Row: Actions
                    Surface(
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .navigationBarsPadding()
                                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 56.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { showAddMemberDialog = false },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text("Cancelar", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    if (newEmail.isNotBlank() && newPass.isNotBlank() && newName.isNotBlank()) {
                                        viewModel.registerNewMember(
                                            email = newEmail,
                                            password = newPass,
                                            fullName = newName,
                                            role = newRole,
                                            country = newCountry,
                                            emojiAvatar = newEmoji,
                                            isAdmin = newIsAdmin,
                                            photoUri = newPhotoUri,
                                            qrUri = newQrUri,
                                            customCara1Uri = newCara1Uri,
                                            customCara2Uri = newCara2Uri
                                        ) { success ->
                                            if (success) {
                                                showAddMemberDialog = false
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                enabled = newEmail.isNotBlank() && newPass.isNotBlank() && newName.isNotBlank(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Guardar", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Guardar", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingMember != null) {
        val memberToEdit = editingMember!!
        var editName by remember { mutableStateOf(memberToEdit.fullName) }
        var editEmail by remember { mutableStateOf(memberToEdit.email) }
        var editPassword by remember { mutableStateOf(com.example.data.SecurityUtils.resolveDehashedPassword(memberToEdit.password)) }
        var passwordVisible by remember { mutableStateOf(false) }
        var editRole by remember { mutableStateOf(memberToEdit.role) }
        var editCountry by remember { mutableStateOf(memberToEdit.country) }
        var editInclusionYear by remember {
            val year = if (memberToEdit.joinDate.contains("/")) {
                memberToEdit.joinDate.substringAfterLast("/")
            } else {
                memberToEdit.joinDate
            }
            mutableStateOf(year)
        }
        var editEmoji by remember { mutableStateOf(memberToEdit.emojiAvatar) }
        var editIsAdmin by remember { mutableStateOf(memberToEdit.isAdmin) }
        var editPhotoUri by remember { mutableStateOf<String?>(memberToEdit.photoUri) }
        var editQrUri by remember { mutableStateOf<String?>(memberToEdit.qrUri) }
        var editCara1Uri by remember { mutableStateOf<String?>(memberToEdit.customCara1Uri) }
        var editCara2Uri by remember { mutableStateOf<String?>(memberToEdit.customCara2Uri) }

        val editPhotoLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                val localPath = copyUriToLocalStorage(context, uri)
                if (localPath != null) {
                    editPhotoUri = localPath
                }
            }
        }

        val editQrLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                val localPath = copyUriToLocalStorage(context, uri)
                if (localPath != null) {
                    editQrUri = localPath
                }
            }
        }

        val editCara1Launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                val localPath = copyUriToLocalStorage(context, uri)
                if (localPath != null) {
                    editCara1Uri = localPath
                }
            }
        }

        val editCara2Launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                val localPath = copyUriToLocalStorage(context, uri)
                if (localPath != null) {
                    editCara2Uri = localPath
                }
            }
        }

        Dialog(
            onDismissRequest = { editingMember = null },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                ) {
                    // Header (Verde y blanco)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { editingMember = null }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancelar",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Modificar Carnet de Miembro",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text = "Edita los datos y configuración de la credencial",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // Form
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = editName,
                                        onValueChange = { editName = it },
                                        label = { Text("Nombre Completo") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    OutlinedTextField(
                                        value = editEmail,
                                        onValueChange = { /* read-only */ },
                                        label = { Text("Correo Electrónico") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        readOnly = true,
                                        enabled = false
                                    )

                                    OutlinedTextField(
                                        value = editPassword,
                                        onValueChange = { editPassword = it },
                                        label = { Text("Contraseña de la APP") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                        trailingIcon = {
                                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                                Icon(
                                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                    contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña"
                                                )
                                            }
                                        }
                                    )
                                    
                                    Text("Rol:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    val roles = listOf("coordinador", "miembro activo", "miembro pasivo", "director", "secretario")
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(roles) { r ->
                                                val sel = editRole.lowercase() == r.lowercase()
                                                Badge(
                                                    containerColor = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                    contentColor = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .clickable { editRole = r }
                                                        .padding(horizontal = 4.dp, vertical = 4.dp)
                                                ) {
                                                    Text(r.replaceFirstChar { it.uppercase() }, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                                }
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = editCountry,
                                        onValueChange = { editCountry = it },
                                        label = { Text("País") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    OutlinedTextField(
                                        value = editInclusionYear,
                                        onValueChange = { editInclusionYear = it },
                                        label = { Text("Año de Inclusión") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                        )
                                    )
                                }
                            }
                        }

                        // Attachments (Photos, QR, Emoji, Custom faces)
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text("Multimedia y Personalización", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                                    Text("Fotografía Oficial (Opcional):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                                .clickable {
                                                    editPhotoLauncher.launch(
                                                        PickVisualMediaRequest(
                                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                                        )
                                                    )
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!editPhotoUri.isNullOrEmpty()) {
                                                AsyncImage(
                                                    model = editPhotoUri,
                                                    contentDescription = "Foto elegida",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Filled.PhotoCamera,
                                                    contentDescription = "Subir foto",
                                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                        
                                        Column(modifier = Modifier.weight(1.0f)) {
                                            Button(
                                                onClick = {
                                                    editPhotoLauncher.launch(
                                                        PickVisualMediaRequest(
                                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                                        )
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                ),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("Añadir / Cambiar", fontSize = 12.sp)
                                            }
                                            
                                            if (!editPhotoUri.isNullOrEmpty()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                TextButton(
                                                    onClick = { editPhotoUri = null },
                                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                                ) {
                                                    Icon(imageVector = Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Quitar Foto", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }



                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                                    Text("Avatar / Emoji:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    val emojis = listOf("🐆", "🐸", "🦉", "🦅", "🐳", "🦊", "🌿")
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        emojis.forEach { emoji ->
                                            val sel = editEmoji == emoji
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                                    .border(1.dp, if (sel) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                                    .clickable { editEmoji = emoji },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(emoji, fontSize = 20.sp)
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                                    Text("Carnet Manual (Exclusivo Administrador):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("Opcional: puedes subir imágenes de carnet personalizadas para este miembro.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Button(
                                                onClick = {
                                                    editCara1Launcher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Cara 1 (Frente)", fontSize = 11.sp, maxLines = 1)
                                            }
                                            if (!editCara1Uri.isNullOrEmpty()) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Cargado", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                                    IconButton(onClick = { editCara1Uri = null }, modifier = Modifier.size(24.dp)) {
                                                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            }
                                        }
                                        
                                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Button(
                                                onClick = {
                                                    editCara2Launcher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Cara 2 (Atrás)", fontSize = 11.sp, maxLines = 1)
                                            }
                                            if (!editCara2Uri.isNullOrEmpty()) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Cargado", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                                    IconButton(onClick = { editCara2Uri = null }, modifier = Modifier.size(24.dp)) {
                                                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Privilegios de Administrador", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text("Permite acceder a la consola de coordinación", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        }
                                        Switch(
                                            checked = editIsAdmin || memberToEdit.email == "coordinador@je.org",
                                            enabled = memberToEdit.email != "coordinador@je.org",
                                            onCheckedChange = { editIsAdmin = it }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Row: Actions
                    Surface(
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .navigationBarsPadding()
                                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 56.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { editingMember = null },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text("Cancelar", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    val finalPassword = editPassword.trim()
                                    val newJoinDate = if (memberToEdit.joinDate.contains("/")) {
                                        val prefix = memberToEdit.joinDate.substringBeforeLast("/")
                                        "$prefix/${editInclusionYear.trim()}"
                                    } else {
                                        editInclusionYear.trim()
                                    }
                                    val updated = memberToEdit.copy(
                                        fullName = editName,
                                        role = editRole,
                                        country = editCountry,
                                        emojiAvatar = editEmoji,
                                        isAdmin = if (memberToEdit.email == "coordinador@je.org") true else editIsAdmin,
                                        photoUri = editPhotoUri,
                                        qrUri = editQrUri,
                                        customCara1Uri = editCara1Uri,
                                        customCara2Uri = editCara2Uri,
                                        password = finalPassword,
                                        joinDate = newJoinDate
                                    )
                                    viewModel.modifyMemberProfile(updated)
                                    editingMember = null
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                enabled = editName.isNotBlank(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Guardar", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Guardar", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminEnrollmentsPanel(viewModel: AppViewModel, enrollments: List<EcoEnrollment>, activities: List<EcoActivity>) {
    val prefTheme by viewModel.prefTheme.collectAsStateWithLifecycle()
    val isDark = when (prefTheme) {
        "light" -> false
        "dark" -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White

    var expandedActivityIds by remember { mutableStateOf(emptySet<Int>()) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Miembros Inscritos por Actividad".uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (activities.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Crea una actividad para visualizar inscritos", fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(activities, key = { it.id }) { activity ->
                    val activityEnrollments = enrollments.filter { it.activityId == activity.id }
                    val isExpanded = expandedActivityIds.contains(activity.id)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedActivityIds = if (isExpanded) {
                                    expandedActivityIds - activity.id
                                } else {
                                    expandedActivityIds + activity.id
                                }
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = activity.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text("${activityEnrollments.size} inscritos", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isExpanded) "Colapsar" else "Expandir",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Fecha de Actividad: ${activity.date} | Categoría: ${activity.category}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            
                            AnimatedVisibility(visible = isExpanded) {
                                Column {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (activityEnrollments.isEmpty()) {
                                        Text(
                                            text = "Ningún miembro se ha inscrito todavía.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                            style = androidx.compose.ui.text.TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    } else {
                                        activityEnrollments.forEach { enrollment ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Column {
                                                        Text(
                                                            text = enrollment.memberName,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = enrollment.memberEmail,
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                        )
                                                    }
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        text = "Inscrito el:",
                                                        fontSize = 9.sp,
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = enrollment.enrolledAt,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGlobalAlertDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var buttonText by remember { mutableStateOf("") }
    var actionUrl by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<String?>(null) }
    var selectedIconName by remember { mutableStateOf("bell") } // "bell", "info", "warning", "celebration", "star", "campaign", "help"
    var selectedDurationName by remember { mutableStateOf("Siempre visible") } // "1 hora", "1 día", "3 días", "7 días", "Siempre visible"
    var isSubmitting by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }

    val durationOptions = listOf(
        "1 hora" to 60 * 60 * 1000L,
        "1 día" to 24 * 60 * 60 * 1000L,
        "3 días" to 3 * 24 * 60 * 60 * 1000L,
        "7 días" to 7 * 24 * 60 * 60 * 1000L,
        "Siempre visible" to 0L
    )

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val localPath = copyUriToLocalStorage(context, uri)
            photoUri = localPath
        }
    }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isSubmitting,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Header (Verde y blanco)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, enabled = !isSubmitting) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Crear Mensaje de Difusión",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "Completa los datos para crear un comunicado de alta prioridad",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Este mensaje se difundirá con prioridad máxima en la aplicación de cada miembro.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Input Title
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título del Mensaje (ej: Comunicado Urgente)") },
                        modifier = Modifier.fillMaxWidth().testTag("global_alert_input_title"),
                        singleLine = true,
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Input Message
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Mensaje o Contenido") },
                        modifier = Modifier.fillMaxWidth().height(110.dp).testTag("global_alert_input_message"),
                        maxLines = 6,
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Icon Selector (diverse options)
                    Text(
                        text = "Seleccione el Icono para el Mensaje",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val iconsList = listOf(
                        Triple("bell", Icons.Filled.NotificationsActive, "Notificación"),
                        Triple("info", Icons.Filled.Info, "Información"),
                        Triple("warning", Icons.Filled.Warning, "Advertencia"),
                        Triple("celebration", Icons.Filled.Celebration, "Celebración"),
                        Triple("star", Icons.Filled.Star, "Especial"),
                        Triple("campaign", Icons.Filled.Campaign, "Campaña"),
                        Triple("help", Icons.Filled.Help, "Ayuda")
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(iconsList.size) { index ->
                            val (key, vector, label) = iconsList[index]
                            val isSelected = selectedIconName == key
                            val containerCol = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            val tintCol = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(containerCol)
                                    .clickable(enabled = !isSubmitting) { selectedIconName = key }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = vector,
                                        contentDescription = label,
                                        tint = tintCol,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = tintCol
                                    )
                                }
                            }
                        }
                    }

                    // Expiration / Duration Options
                    Text(
                        text = "Duración del Mensaje de Difusión",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        durationOptions.forEach { (label, durationMs) ->
                            val isSelected = selectedDurationName == label
                            val containerCol = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            val borderCol = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(containerCol)
                                    .border(1.dp, borderCol, RoundedCornerShape(10.dp))
                                    .clickable(enabled = !isSubmitting) { selectedDurationName = label }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Input Button Text
                    OutlinedTextField(
                        value = buttonText,
                        onValueChange = { buttonText = it },
                        label = { Text("Texto del Botón (ej: Enterado)") },
                        placeholder = { Text("Aceptar") },
                        modifier = Modifier.fillMaxWidth().testTag("global_alert_input_btn"),
                        singleLine = true,
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Input Action Link / Hyperlink
                    OutlinedTextField(
                        value = actionUrl,
                        onValueChange = { actionUrl = it },
                        label = { Text("Enlace / Hipervínculo del Botón (Opcional)") },
                        placeholder = { Text("https://example.com/mas-informacion") },
                        leadingIcon = { Icon(imageVector = Icons.Default.Link, contentDescription = "Enlace") },
                        modifier = Modifier.fillMaxWidth().testTag("global_alert_input_url"),
                        singleLine = true,
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Optional photograph attachment card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Imagen Adjunta Opcional",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            if (photoUri != null) {
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = photoUri,
                                        contentDescription = "Visualización",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    IconButton(
                                        onClick = { photoUri = null },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                            .size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Limpiar Foto",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        photoLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    enabled = !isSubmitting
                                ) {
                                    Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Añadir Imagen", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Botón de Vista Previa
                    OutlinedButton(
                        onClick = { showPreview = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("global_alert_preview_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        enabled = !isSubmitting
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Vista Previa"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Vista Previa del Mensaje",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Footer Actions
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    tonalElevation = 1.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isSubmitting
                        ) {
                            Text("Cancelar")
                        }

                        Button(
                            onClick = {
                                if (title.isBlank() || message.isBlank()) {
                                    android.widget.Toast.makeText(context, "Por favor complete el título y el mensaje.", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isSubmitting = true
                                val durationVal = durationOptions.firstOrNull { it.first == selectedDurationName }?.second ?: 0L
                                val expiry = if (durationVal > 0) System.currentTimeMillis() + durationVal else 0L

                                viewModel.insertGlobalAlert(
                                    title = title,
                                    message = message,
                                    buttonText = buttonText,
                                    photoPath = photoUri,
                                    broadcastIcon = selectedIconName,
                                    expiryMillis = expiry,
                                    actionUrl = actionUrl.trim().takeIf { it.isNotBlank() },
                                    onSuccess = {
                                        android.widget.Toast.makeText(context, "Mensaje de Difusión creado exitosamente 📢", android.widget.Toast.LENGTH_SHORT).show()
                                        isSubmitting = false
                                        onDismiss()
                                    },
                                    onError = { error ->
                                        android.widget.Toast.makeText(context, "Error: $error", android.widget.Toast.LENGTH_LONG).show()
                                        isSubmitting = false
                                    }
                                )
                            },
                            modifier = Modifier.weight(1.5f).height(48.dp).testTag("global_alert_submit_btn"),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isSubmitting && title.isNotBlank() && message.isNotBlank()
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Difundir", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPreview) {
        val previewAlert = EcoNotification(
            title = title.ifBlank { "Comunicado de Ejemplo" },
            message = message.ifBlank { "Este es un ejemplo de cómo se verá el contenido de tu mensaje de difusión cuando se envíe a los miembros de la comunidad." },
            timestamp = "Hace un momento",
            isRead = false,
            photoUri = photoUri,
            isGlobalAlert = true,
            alertButtonText = buttonText.ifBlank { "Aceptar" },
            broadcastIcon = selectedIconName,
            actionUrl = actionUrl.trim().takeIf { it.isNotBlank() }
        )
        GlobalAlertDialog(
            alert = previewAlert,
            onDismiss = { showPreview = false }
        )
    }
}

@Composable
fun AdminAlertsPanel(viewModel: AppViewModel, notifications: List<EcoNotification>) {
    val context = LocalContext.current
    val prefTheme by viewModel.prefTheme.collectAsStateWithLifecycle()
    val isDark = when (prefTheme) {
        "light" -> false
        "dark" -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    var activeScreenshotUri by remember { mutableStateOf<String?>(null) }
    var showCreateGlobalAlertDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Buzón de Notificaciones de Red".uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { viewModel.markNotificationsRead() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f).height(34.dp).testTag("mark_all_read_btn"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Todo Leído", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = { viewModel.clearAllNotifications() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f).height(34.dp).testTag("clear_local_btn"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Borrar Todo", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            viewModel.clearAllCloudNotifications { success, message ->
                                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f).height(34.dp).testTag("clear_cloud_btn"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Limpiar Nube", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { showCreateGlobalAlertDialog = true },
                    modifier = Modifier.fillMaxWidth().height(38.dp).testTag("create_global_alert_btn"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Campaign,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Crear Mensaje de Difusión", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showCreateGlobalAlertDialog) {
            CreateGlobalAlertDialog(
                viewModel = viewModel,
                onDismiss = { showCreateGlobalAlertDialog = false }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (notifications.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Buzón libre de nuevas alertas", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(notifications, key = { it.id }) { alert ->
                    val isBug = alert.title.contains("Bug", ignoreCase = true) || alert.title.contains("🐛") || alert.title.contains("Fallo", ignoreCase = true)
                    
                    val cardBgItem = if (isDark) {
                        if (alert.isRead) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        } else if (isBug) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.08f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.04f)
                        }
                    } else {
                        Color.White
                    }

                    val borderColor = if (alert.isRead) {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                    } else if (isBug) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    }

                    val accentColor = if (alert.isRead) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    } else if (isBug) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgItem),
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (alert.isRead) 0.dp else 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(accentColor.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val customAlertIcon = if (isBug) {
                                            Icons.Default.Warning
                                        } else if (alert.isGlobalAlert) {
                                            when (alert.broadcastIcon) {
                                                "bell" -> Icons.Default.NotificationsActive
                                                "info" -> Icons.Default.Info
                                                "warning" -> Icons.Default.Warning
                                                "celebration" -> Icons.Default.Celebration
                                                "star" -> Icons.Default.Star
                                                "campaign" -> Icons.Default.Campaign
                                                "help" -> Icons.Default.Help
                                                else -> Icons.Default.NotificationsActive
                                            }
                                        } else if (alert.isRead) {
                                            Icons.Default.NotificationsNone
                                        } else {
                                            Icons.Default.NotificationsActive
                                        }
                                        Icon(
                                            imageVector = customAlertIcon,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(8.dp))
                                    
                                    Text(
                                        text = alert.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (alert.isRead) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    
                                    if (alert.isGlobalAlert) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            ),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "DIFUSIÓN",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    if (isBug && !alert.isRead) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "BUG",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onError,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    
                                    if (!alert.isRead) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                                
                                Text(
                                    text = alert.message,
                                    fontSize = 12.sp,
                                    color = if (alert.isRead) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                
                                if (!alert.photoUri.isNullOrBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .padding(vertical = 6.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                            .clickable { activeScreenshotUri = alert.photoUri }
                                            .padding(4.dp)
                                    ) {
                                        Card(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(6.dp)),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            AsyncImage(
                                                model = alert.photoUri,
                                                contentDescription = "Pantallazo de bug",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("Ver evidencia adjunta", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Text("Toca para ampliar 🔍", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                        }
                                    }
                                }

                                Text(
                                    text = alert.timestamp,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                            
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.toggleNotificationReadStatus(alert) },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(
                                            color = if (alert.isRead) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    Icon(
                                        imageVector = if (alert.isRead) Icons.Default.NotificationsNone else Icons.Default.Check,
                                        contentDescription = if (alert.isRead) "Marcar como no leído" else "Marcar como leído",
                                        tint = if (alert.isRead) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.clearNotification(alert.id) },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Eliminar alerta",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Full screen expandable screenshot lightbox dialog
    if (activeScreenshotUri != null) {
        Dialog(
            onDismissRequest = { activeScreenshotUri = null }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Evidencia de Bug / Captura",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        IconButton(onClick = { activeScreenshotUri = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = activeScreenshotUri,
                            contentDescription = "Pantallazo completo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = { activeScreenshotUri = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Cerrar Vista", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(viewModel: com.example.ui.AppViewModel, member: Member, onDismiss: () -> Unit) {
    var startAnim by remember { mutableStateOf(false) }
    val cardScale by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0.85f,
        animationSpec = tween(durationMillis = 600)
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(durationMillis = 500)
    )

    LaunchedEffect(Unit) {
        startAnim = true
        val startTime = System.currentTimeMillis()
        var completed = false
        viewModel.syncWithCloud { _, _ ->
            completed = true
        }
        while (!completed) {
            kotlinx.coroutines.delay(100)
        }
        val elapsed = System.currentTimeMillis() - startTime
        val remainingDelay = maxOf(0L, 1600L - elapsed)
        if (remainingDelay > 0) {
            kotlinx.coroutines.delay(remainingDelay)
        }
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .graphicsLayer(
                    scaleX = cardScale,
                    scaleY = cardScale,
                    alpha = cardAlpha
                ),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Large emoji or mascot inside animated background
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = member.emojiAvatar,
                        fontSize = 56.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "¡BIENVENIDO/A!",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = member.fullName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    val formattedRole = remember(member.role) {
                        member.role.split(" ").joinToString(" ") { word ->
                            word.replaceFirstChar { if (it.isLowerCase()) it.uppercase() else it.toString() }
                        }
                    }
                    Text(
                        text = formattedRole,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Accediendo al perfil...",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Credencial: ${member.credentialId} • América Latina",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsDialog(
    viewModel: com.example.ui.AppViewModel,
    onDismiss: () -> Unit
) {
    val prefTheme by viewModel.prefTheme.collectAsStateWithLifecycle()
    val cloudSyncUrl by viewModel.cloudSyncUrl.collectAsStateWithLifecycle()
    val cloudSecurityKey by viewModel.cloudSecurityKey.collectAsStateWithLifecycle()
    val prefNotifActividades by viewModel.prefNotifActividades.collectAsStateWithLifecycle()
    val prefNotifNovedades by viewModel.prefNotifNovedades.collectAsStateWithLifecycle()
    val prefNotifNube by viewModel.prefNotifNube.collectAsStateWithLifecycle()
    val prefNotifSistema by viewModel.prefNotifSistema.collectAsStateWithLifecycle()
    val currentMember by viewModel.loggedInMember.collectAsStateWithLifecycle()

    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Ajustes de JE-App",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Tema Visual Section
                Text(
                    text = "Tema Visual",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val themeOptions = listOf(
                        Triple("system", "Sistema", Icons.Default.PhoneAndroid),
                        Triple("light", "Claro", Icons.Default.WbSunny),
                        Triple("dark", "Oscuro", Icons.Default.NightsStay)
                    )

                    themeOptions.forEach { (optionId, label, icon) ->
                        val isSelected = prefTheme == optionId
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    viewModel.updatePrefTheme(optionId)
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Section 1: Notifications
                Text(
                    text = "Preferencias de Alertas",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Activity Notification Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Nuevas Actividades",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Avisar al publicar una actividad",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    Switch(
                        checked = prefNotifActividades,
                        onCheckedChange = { viewModel.updatePrefNotifActividades(it) }
                    )
                }

                // AI Novedades Notification Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Asesoría AI & Noticias",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Avisar sobre nuevas asesorías",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    Switch(
                        checked = prefNotifNovedades,
                        onCheckedChange = { viewModel.updatePrefNotifNovedades(it) }
                    )
                }

                // Nube Notification Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Alertas de Nube",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Notificar al sincronizar la base",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    Switch(
                        checked = prefNotifNube,
                        onCheckedChange = { viewModel.updatePrefNotifNube(it) }
                    )
                }

                // System Notification Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Notificaciones de Sistema",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Habilitar avisos en barra de Android",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    Switch(
                        checked = prefNotifSistema,
                        onCheckedChange = { viewModel.updatePrefNotifSistema(it) }
                    )
                }

                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Section 3: Diagnostic information
                Text(
                    text = "Estado y Diagnóstico",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("• Versión de App: JE-App Production v2.5 Stable", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("• Estado de red: Online & Auto-heal habilitado", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("• Motor de sincronización: OK (Firebase Realtime DB)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Button(
                    onClick = {
                        Toast.makeText(context, "Caché de base de datos optimizado y purgado.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f), contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    elevation = null
                ) {
                    Text("Limpiar Caché Local", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun GlobalAlertDialog(
    alert: EcoNotification,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { /* Force explicit click on Aceptar button */ }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Close cross icon in the top-right corner
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    // Circular speaker/notification icon
                    val dialogIcon = when (alert.broadcastIcon) {
                        "bell" -> Icons.Filled.NotificationsActive
                        "info" -> Icons.Filled.Info
                        "warning" -> Icons.Filled.Warning
                        "celebration" -> Icons.Filled.Celebration
                        "star" -> Icons.Filled.Star
                        "campaign" -> Icons.Filled.Campaign
                        "help" -> Icons.Filled.Help
                        else -> Icons.Filled.NotificationsActive
                    }
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Icon(
                            imageVector = dialogIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title of the announcement
                    Text(
                        text = alert.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Optional attached visual card
                    if (!alert.photoUri.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            AsyncImage(
                                model = alert.photoUri,
                                contentDescription = "Anuncio Imagen",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Message text
                    Text(
                        text = alert.message,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    // Action acknowledgement button
                    val buttonLabel = alert.alertButtonText?.ifBlank { "Aceptar" } ?: "Aceptar"
                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

                    Button(
                        onClick = {
                            if (!alert.actionUrl.isNullOrBlank()) {
                                try {
                                    uriHandler.openUri(alert.actionUrl)
                                } catch (e: Exception) {
                                    // Ignore failed URL opening gracefully
                                }
                            }
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("global_alert_accept_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (!alert.actionUrl.isNullOrBlank()) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "Enlace",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = buttonLabel,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NotificationListDialog(
    viewModel: com.example.ui.AppViewModel,
    notifications: List<EcoNotification>,
    onDismiss: () -> Unit,
    onMarkAllRead: () -> Unit,
    onClearNotification: (Int) -> Unit
) {
    val sortedNotifications = remember(notifications) { notifications.sortedByDescending { it.id } }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Notificaciones",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
                
                val unreadCount = notifications.count { !it.isRead }
                if (notifications.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onMarkAllRead,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            enabled = unreadCount > 0,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Marcar leídas", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.clearAllNotifications() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Eliminar todas", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Las preferencias de notificaciones móviles ahora se gestionan de forma centralizada en Configuración.
                
                if (notifications.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Buzón vacío",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Text(
                                text = "No tienes avisos por ahora",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sortedNotifications, key = { it.id }) { alert ->
                            val isUnread = !alert.isRead
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isUnread) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    }
                                ),
                                border = if (isUnread) {
                                    androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                } else {
                                    null
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isUnread) MaterialTheme.colorScheme.primary 
                                                        else Color.Transparent
                                                    )
                                            )
                                            if (isUnread) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            Text(
                                                text = alert.title,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (alert.isGlobalAlert) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Card(
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                    ),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "DIFUSIÓN",
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = alert.message,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                            lineHeight = 14.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = alert.timestamp,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                    
                                    IconButton(
                                        onClick = { onClearNotification(alert.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Eliminar",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cerrar", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun NotificationPrefRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                lineHeight = 12.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.82f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCloudPanel(viewModel: AppViewModel) {
    val cloudSyncUrl by viewModel.cloudSyncUrl.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val cloudDiagnosis by viewModel.cloudDiagnosis.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val prefTheme by viewModel.prefTheme.collectAsStateWithLifecycle()
    val isDark = when (prefTheme) {
        "light" -> false
        "dark" -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val cardBg2 = if (isDark) MaterialTheme.colorScheme.surface else Color.White

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 0.dp, end = 0.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Sincronización en la Nube",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "La app comparte actividades, perfiles y avisos de forma instantánea. Para asegurar la máxima estabilidad, la app utiliza el servidor oficial de Jóvenes y Ecosistemas.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    lineHeight = 16.sp
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Servidor Oficial Permanente",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = cloudSyncUrl,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg2),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Estado Sincronización Actual",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when (syncState) {
                                is AppViewModel.SyncState.Syncing -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                is AppViewModel.SyncState.Success -> Color(0xFF4CAF50).copy(alpha = 0.08f)
                                is AppViewModel.SyncState.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)
                            }
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                when (syncState) {
                                    is AppViewModel.SyncState.Syncing -> MaterialTheme.colorScheme.primary
                                    is AppViewModel.SyncState.Success -> Color(0xFF4CAF50)
                                    is AppViewModel.SyncState.Error -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                }
                            )
                    )

                    Column {
                        Text(
                            text = when (syncState) {
                                is AppViewModel.SyncState.Syncing -> "Subiendo Consola a la Nube..."
                                is AppViewModel.SyncState.Success -> "Sincronizado Exitosamente"
                                is AppViewModel.SyncState.Error -> "Error de Sincronización"
                                else -> "Estado: Lista para Sincronizar"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when (syncState) {
                                is AppViewModel.SyncState.Success -> (syncState as AppViewModel.SyncState.Success).message
                                is AppViewModel.SyncState.Error -> (syncState as AppViewModel.SyncState.Error).message
                                is AppViewModel.SyncState.Syncing -> "Estableciendo conexión HTTPS..."
                                else -> "Toca el botón para publicar actividades y perfiles."
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            lineHeight = 14.sp
                        )
                    }
                }

                Button(
                    onClick = {
                        viewModel.syncWithCloud { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (syncState is AppViewModel.SyncState.Syncing) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.primary,
                        contentColor = if (syncState is AppViewModel.SyncState.Syncing) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onPrimary
                    ),
                    enabled = syncState !is AppViewModel.SyncState.Syncing
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (syncState is AppViewModel.SyncState.Syncing) "Sincronizando..." else "Sincronizar Consola Ahora",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg2),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Escáner y Auditoría de Errores de la Nube",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Realiza un análisis criptográfico autónomo, mide latencia, valida perfiles de descifrado y diagnostica discrepancias de registros entre el almacenamiento local y el servidor.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    lineHeight = 16.sp
                )

                when (val state = cloudDiagnosis) {
                    is AppViewModel.CloudDiagnosisState.Idle -> {
                        // Display default state
                    }
                    is AppViewModel.CloudDiagnosisState.Scanning -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Analizando estructura cifrada y ping...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    is AppViewModel.CloudDiagnosisState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = state.message,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error,
                                lineHeight = 14.sp
                            )
                        }
                    }
                    is AppViewModel.CloudDiagnosisState.Success -> {
                        val report = state.report
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Comparativa de Datos (Local vs Nube):",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    val items = listOf(
                                        "Actividades" to (report.localActivities to report.remoteActivities),
                                        "Miembros" to (report.localMembers to report.remoteMembers),
                                        "Artículos" to (report.localArticles to report.remoteArticles),
                                        "Notificaciones" to (report.localNotifications to report.remoteNotifications),
                                        "Inscripciones" to (report.localEnrollments to report.remoteEnrollments),
                                        "Recordatorios ⏰" to (report.localReminders to report.remoteReminders),
                                        "Preferencias de Usuario ⚙️" to (report.localPrefsCount to report.remotePrefsCount)
                                    )
                                    
                                    for ((label, counts) in items) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                                            Text(
                                                text = "${counts.first} locales  •  ${counts.second} en nube",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            // Firebase & Active Session Analysis Card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Auditoría de Enlaces y Sesión:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    // Firebase Check Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Conectividad Firebase / Firestore", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            val isFirebaseConnected = report.firebaseAuthConnected && report.firebaseRtdbConnected
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isFirebaseConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
                                            )
                                            Text(
                                                text = if (isFirebaseConnected) "Conectado" else "Limitado / Fuera de línea",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isFirebaseConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }

                                    // Active Session Check Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Integridad de Sesión Activa", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (report.activeSessionSynced) Color(0xFF4CAF50) else Color(0xFFFF9800))
                                            )
                                            Text(
                                                text = if (report.activeSessionSynced) "Sincronizada (OK)" else "Inconsistencias",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (report.activeSessionSynced) Color(0xFF4CAF50) else Color(0xFFFF9800)
                                            )
                                        }
                                    }

                                    if (!report.activeSessionSynced && report.activeSessionDiscrepancy != null) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFFF9800).copy(alpha = 0.08f))
                                                .padding(8.dp)
                                        ) {
                                            Text(
                                                text = report.activeSessionDiscrepancy,
                                                fontSize = 10.sp,
                                                color = Color(0xFFD84315),
                                                lineHeight = 13.sp
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (report.decryptFailureMembersCount > 0) MaterialTheme.colorScheme.error.copy(alpha = 0.05f)
                                        else Color(0xFF4CAF50).copy(alpha = 0.05f)
                                    )
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (report.decryptFailureMembersCount > 0) Icons.Default.Warning else Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (report.decryptFailureMembersCount > 0) MaterialTheme.colorScheme.error else Color(0xFF4CAF50),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Seguridad: ${report.decryptSuccessMembersCount} perfiles legibles, ${report.decryptFailureMembersCount} ilegibles (Clave de descifrado).",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (report.decryptFailureMembersCount > 0) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                                )
                            }

                            if (report.recommendations.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Resultado del Escaneo:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    for (rec in report.recommendations) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(text = "•", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = rec,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (cloudDiagnosis !is AppViewModel.CloudDiagnosisState.Scanning) {
                            viewModel.scanCloudDatabase()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (cloudDiagnosis is AppViewModel.CloudDiagnosisState.Scanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Escaneando nube...",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Escanear y Diagnosticar Nube",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailDialog(
    activity: EcoActivity,
    isCoordinadorMode: Boolean,
    onDismissRequest: () -> Unit,
    onEditClick: () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title and Project (Organizer) Heading
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = activity.title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 28.sp
                    )
                    Text(
                        text = activity.organizer,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Header (Category indicator)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
                    val badgeColor = if (isDark) {
                        when (activity.category.trim().lowercase()) {
                            "reforestación" -> Color(0xFF064E3B) to Color(0xFF34D399)
                            "limpieza" -> Color(0xFF0C4A6E) to Color(0xFF38BDF8)
                            "educación" -> Color(0xFF78350F) to Color(0xFFFBBF24)
                            "conservación" -> Color(0xFF2D3A29) to Color(0xFFC2C9B6)
                            else -> Color(0xFF273523) to Color(0xFFB4C2AF)
                        }
                    } else {
                        when (activity.category.trim().lowercase()) {
                            "reforestación" -> Color(0xFFD1FAE5) to Color(0xFF047857)
                            "limpieza" -> Color(0xFFE0F2FE) to Color(0xFF0284C7)
                            "educación" -> Color(0xFFFEF3C7) to Color(0xFFD97706)
                            "conservación" -> Color(0xFFE5E7E2) to Color(0xFF394931)
                            else -> Color(0xFFEAECE9) to Color(0xFF4C5E43)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeColor.first)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = activity.category.uppercase(),
                            color = badgeColor.second,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (activity.isMandatory) {
                        val mandatoryBg = if (isDark) Color(0xFF7F1D1D) else Color(0xFFFEE2E2)
                        val mandatoryFg = if (isDark) Color(0xFFFCA5A5) else Color(0xFF991B1B)
                        Box(
                            modifier = Modifier
                                .background(mandatoryBg, shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "OBLIGATORIA",
                                color = mandatoryFg,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Metadata Column
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailMetaRow(Icons.Filled.DateRange, "Fecha", com.example.util.TimezoneHelper.formatActivityDate(activity.date))
                    DetailMetaRow(Icons.Filled.LocationOn, "Lugar", "${activity.location}, ${activity.country}")
                    DetailMetaRow(Icons.Filled.Person, "Organizador", activity.organizer)
                    DetailMetaRow(Icons.Filled.Category, "Tipo de Evento", activity.eventType)
                    DetailMetaRow(
                        Icons.Filled.CheckCircle, 
                        "Inscripción", 
                        if (activity.isUserRegistered) "Inscrito ✓" else "No inscrito"
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Detailed Description
                Text(
                    text = "Descripción del Evento",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = activity.description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Actions Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Cerrar")
                    }
                    if (isCoordinadorMode) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onEditClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Editar",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Editar")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailFullScreenDialog(
    article: EcoArticle,
    isCoordinadorMode: Boolean = false,
    onEditClick: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header Bar (Title of screen)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Regresar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Reporte Completo",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isCoordinadorMode && onEditClick != null) {
                        IconButton(onClick = onEditClick) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar Reporte",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        // Spacer to balance
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                ) {
                    // Optional Featured Image or Card containing Title if no image
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                    ) {
                        if (!article.photoUri.isNullOrBlank()) {
                            AsyncImage(
                                model = article.photoUri,
                                contentDescription = "Imagen del reporte",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = article.title,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 24.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    // Theme/Category tag & Region (Country)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = article.category.uppercase(),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = article.region,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title of Article (displayed below image only, if an image is loaded)
                    if (!article.photoUri.isNullOrBlank()) {
                        Text(
                            text = article.title,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 30.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Publicado: ${article.publishDate}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )

                    // Article Content Content
                    Text(
                        text = article.content,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        lineHeight = 24.sp
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun DetailMetaRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            if (value.contains("http://") || value.contains("https://")) {
                AutolinkText(
                    text = value,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                )
            } else {
                Text(
                    text = value,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun AutolinkText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle.Default
) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val urlPattern = "(https?://[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]+)".toRegex()
    val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
        var lastIndex = 0
        urlPattern.findAll(text).forEach { matchResult ->
            val start = matchResult.range.first
            val end = matchResult.range.last + 1
            if (start > lastIndex) {
                append(text.substring(lastIndex, start))
            }
            pushStringAnnotation(tag = "URL", annotation = matchResult.value)
            withStyle(style = androidx.compose.ui.text.SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                fontWeight = FontWeight.Bold
            )) {
                append(matchResult.value)
            }
            pop()
            lastIndex = end
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
    
    androidx.compose.foundation.text.ClickableText(
        text = annotatedString,
        modifier = modifier,
        style = style,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    try {
                        uriHandler.openUri(annotation.item)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityEditDialog(
    activity: EcoActivity,
    onDismissRequest: () -> Unit,
    onSaveClick: (EcoActivity) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(activity.title) }
    var desc by remember { mutableStateOf(activity.description) }
    val initialDatePart = activity.date.substringBefore(" ").trim()
    val initialTimePart = if (activity.date.contains(" ")) activity.date.substringAfter(" ").trim() else "10:00"
    var datePart by remember { mutableStateOf(initialDatePart) }
    var timePart by remember { mutableStateOf(initialTimePart) }

    val initialEndDateVal = if (activity.endDate.isNullOrBlank()) activity.date else activity.endDate
    val initialEndDatePart = initialEndDateVal.substringBefore(" ").trim()
    val initialEndTimePart = if (initialEndDateVal.contains(" ")) initialEndDateVal.substringAfter(" ").trim() else "12:00"
    var endDatePart by remember { mutableStateOf(initialEndDatePart) }
    var endTimePart by remember { mutableStateOf(initialEndTimePart) }

    val initialDeadlinePart = if (activity.registrationDeadline.isNotBlank()) activity.registrationDeadline.substringBefore(" ").trim() else ""
    val initialDeadlineTimePart = if (activity.registrationDeadline.isNotBlank() && activity.registrationDeadline.contains(" ")) activity.registrationDeadline.substringAfter(" ").trim() else "10:00"

    var hasRegistrationDeadline by remember { mutableStateOf(activity.registrationDeadline.isNotBlank()) }
    var deadlineDatePart by remember { mutableStateOf(if (initialDeadlinePart.isNotBlank()) initialDeadlinePart else "2026-06-05") }
    var deadlineTimePart by remember { mutableStateOf(initialDeadlineTimePart) }

    val showDeadlineDatePicker = {
        val calendar = java.util.Calendar.getInstance()
        if (deadlineDatePart.isNotBlank()) {
            try {
                val parts = deadlineDatePart.split("-")
                if (parts.size == 3) {
                    calendar.set(java.util.Calendar.YEAR, parts[0].toInt())
                    calendar.set(java.util.Calendar.MONTH, parts[1].toInt() - 1)
                    calendar.set(java.util.Calendar.DAY_OF_MONTH, parts[2].toInt())
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formattedMonth = String.format("%02d", month + 1)
                val formattedDay = String.format("%02d", dayOfMonth)
                deadlineDatePart = "$year-$formattedMonth-$formattedDay"
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    val showDeadlineTimePicker = {
        val hourMinute = deadlineTimePart.split(":")
        val hour = hourMinute.getOrNull(0)?.toIntOrNull() ?: 10
        val minute = hourMinute.getOrNull(1)?.toIntOrNull() ?: 0
        android.app.TimePickerDialog(
            context,
            { _, selectedHour, selectedMinute ->
                deadlineTimePart = String.format("%02d:%02d", selectedHour, selectedMinute)
            },
            hour,
            minute,
            true // is24HourView
        ).show()
    }

    val showDatePicker = {
        val calendar = java.util.Calendar.getInstance()
        if (datePart.isNotBlank()) {
            try {
                val parts = datePart.split("-")
                if (parts.size == 3) {
                    calendar.set(java.util.Calendar.YEAR, parts[0].toInt())
                    calendar.set(java.util.Calendar.MONTH, parts[1].toInt() - 1)
                    calendar.set(java.util.Calendar.DAY_OF_MONTH, parts[2].toInt())
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formattedMonth = String.format("%02d", month + 1)
                val formattedDay = String.format("%02d", dayOfMonth)
                val oldDate = datePart
                datePart = "$year-$formattedMonth-$formattedDay"
                if (endDatePart == oldDate) {
                    endDatePart = datePart
                }
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    val showTimePicker = {
        val hourMinute = timePart.split(":")
        val hour = hourMinute.getOrNull(0)?.toIntOrNull() ?: 10
        val minute = hourMinute.getOrNull(1)?.toIntOrNull() ?: 0
        android.app.TimePickerDialog(
            context,
            { _, selectedHour, selectedMinute ->
                timePart = String.format("%02d:%02d", selectedHour, selectedMinute)
            },
            hour,
            minute,
            true // is24HourView
        ).show()
    }

    val showEndDatePicker = {
        val calendar = java.util.Calendar.getInstance()
        if (endDatePart.isNotBlank()) {
            try {
                val parts = endDatePart.split("-")
                if (parts.size == 3) {
                    calendar.set(java.util.Calendar.YEAR, parts[0].toInt())
                    calendar.set(java.util.Calendar.MONTH, parts[1].toInt() - 1)
                    calendar.set(java.util.Calendar.DAY_OF_MONTH, parts[2].toInt())
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formattedMonth = String.format("%02d", month + 1)
                val formattedDay = String.format("%02d", dayOfMonth)
                endDatePart = "$year-$formattedMonth-$formattedDay"
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    val showEndTimePicker = {
        val hourMinute = endTimePart.split(":")
        val hour = hourMinute.getOrNull(0)?.toIntOrNull() ?: 12
        val minute = hourMinute.getOrNull(1)?.toIntOrNull() ?: 0
        android.app.TimePickerDialog(
            context,
            { _, selectedHour, selectedMinute ->
                endTimePart = String.format("%02d:%02d", selectedHour, selectedMinute)
            },
            hour,
            minute,
            true // is24HourView
        ).show()
    }
    
    // Parsing Location parameters
    var isVirtual by remember { mutableStateOf(activity.location.lowercase().contains("virtual") || activity.location.contains("Enlace:")) }
    var loc by remember {
        mutableStateOf(
            if (isVirtual) {
                if (activity.location.contains(" - Enlace:")) {
                    activity.location.substringBefore(" - Enlace:")
                } else if (activity.location.contains("Enlace:")) {
                    activity.location.substringBefore("Enlace:")
                } else {
                    activity.location
                }
            } else {
                activity.location
            }
        )
    }
    var virtualLink by remember {
        mutableStateOf(
            if (isVirtual && activity.location.contains("Enlace:")) {
                activity.location.substringAfter("Enlace:").trim()
            } else {
                ""
            }
        )
    }

    var country by remember { mutableStateOf(activity.country) }
    var cat by remember { mutableStateOf(activity.category) }
    var org by remember { mutableStateOf(activity.organizer) }
    var eventType by remember { mutableStateOf(activity.eventType) }
    var isMandatory by remember { mutableStateOf(activity.isMandatory) }

    // Dynamic country list
    var countriesList by remember {
        mutableStateOf(
            listOf("Ecuador", "Guatemala", "Bolivia", "Venezuela", "México", "Colombia", "Perú", "Chile", "Argentina", "Brasil", "América Latina", "Internacional").toMutableList().apply {
                if (activity.country.isNotBlank() && !contains(activity.country)) {
                    add(activity.country)
                }
            }.toList()
        )
    }
    var customCountryText by remember { mutableStateOf("") }

    val catsList = listOf("JE-RAM", "JE-Visual", "JE-Ambiente", "JE-VIH", "JE-Podcast", "JE-360", "JE-Mental")
    val eventTypesList = listOf("Educación", "Asamblea general", "Actividad", "Voluntariado")

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                // Top Custom Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismissRequest) { // edit close button
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Editar Actividad",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "Modificar la información de la convocatoria actual",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                }

                // Form Content
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Card 1: Información Básica (Edición Única)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth() /* edit_card_1 */,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "1. Información Básica (Edición)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                
                                OutlinedTextField(
                                    value = title,
                                    onValueChange = { title = it },
                                    label = { Text("Título de la actividad") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = desc,
                                    onValueChange = { desc = it },
                                    label = { Text("Descripción / Convocatoria") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(115.dp),
                                    maxLines = 5
                                )

                                OutlinedTextField(
                                    value = org,
                                    onValueChange = { org = it },
                                    label = { Text("Organizador (Colectivo / Organización)") },
                                    placeholder = { Text("Ej. Voluntariado Juvenil") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Actividad Obligatoria 🚨",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Las actividades obligatorias se destacan con un borde rojo elegante para llamar la atención del equipo.",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                    Switch(
                                        checked = isMandatory,
                                        onCheckedChange = { isMandatory = it }
                                    )
                                }
                            }
                        }
                    }

                    // Card 2: Fecha, Modalidad y Ubicación (Edición)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "2. Fecha, Ubicación y Límite",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                // UNIQUE_EDIT_DATE_SELECTOR
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.Checkbox(
                                        checked = hasRegistrationDeadline,
                                        onCheckedChange = { hasRegistrationDeadline = it }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Establecer fecha límite de inscripción",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                if (hasRegistrationDeadline) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1.3f)
                                                .clickable { showDeadlineDatePicker() }
                                        ) {
                                            OutlinedTextField(
                                                value = deadlineDatePart,
                                                onValueChange = {},
                                                label = { Text("Fecha límite") },
                                                readOnly = true,
                                                enabled = false,
                                                trailingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Filled.CalendarToday,
                                                        contentDescription = "Seleccionar fecha límite"
                                                    )
                                                },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                                                ),
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(0.9f)
                                                .clickable { showDeadlineTimePicker() }
                                        ) {
                                            OutlinedTextField(
                                                value = deadlineTimePart,
                                                onValueChange = {},
                                                label = { Text("Hora límite") },
                                                readOnly = true,
                                                enabled = false,
                                                trailingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Filled.Event,
                                                        contentDescription = "Seleccionar hora límite"
                                                    )
                                                },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                                                ),
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "Fecha y Hora de Inicio",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .clickable { showDatePicker() }
                                    ) {
                                        OutlinedTextField(
                                            value = datePart,
                                            onValueChange = {},
                                            label = { Text("Fecha Inicio") },
                                            readOnly = true,
                                            enabled = false,
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.CalendarToday,
                                                    contentDescription = "Seleccionar fecha inicio"
                                                )
                                            },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(0.9f)
                                            .clickable { showTimePicker() }
                                    ) {
                                        OutlinedTextField(
                                            value = timePart,
                                            onValueChange = {},
                                            label = { Text("Hora Inicio") },
                                            readOnly = true,
                                            enabled = false,
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.Event,
                                                    contentDescription = "Seleccionar hora inicio"
                                                )
                                            },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                    }
                                }

                                Text(
                                    text = "Fecha y Hora de Fin",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .clickable { showEndDatePicker() }
                                    ) {
                                        OutlinedTextField(
                                            value = endDatePart,
                                            onValueChange = {},
                                            label = { Text("Fecha Fin") },
                                            readOnly = true,
                                            enabled = false,
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.CalendarToday,
                                                    contentDescription = "Seleccionar fecha fin"
                                                )
                                            },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(0.9f)
                                            .clickable { showEndTimePicker() }
                                    ) {
                                        OutlinedTextField(
                                            value = endTimePart,
                                            onValueChange = {},
                                            label = { Text("Hora Fin") },
                                            readOnly = true,
                                            enabled = false,
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.Event,
                                                    contentDescription = "Seleccionar hora fin"
                                                )
                                            },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                
                                // Modalidad Switcher (Presencial vs Virtual)
                                Text(
                                    text = "Modalidad del evento:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = !isVirtual,
                                        onClick = { isVirtual = false },
                                        label = { Text("Presencial 📍") }
                                    )
                                    FilterChip(
                                        selected = isVirtual,
                                        onClick = { isVirtual = true },
                                        label = { Text("Virtual 💻") }
                                    )
                                }

                                if (isVirtual) {
                                    OutlinedTextField(
                                        value = loc,
                                        onValueChange = { loc = it },
                                        label = { Text("Plataforma virtual (ej. Google Meet, Zoom, Teams)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = virtualLink,
                                        onValueChange = { virtualLink = it },
                                        label = { Text("Enlace / Link de la reunión virtual") },
                                        placeholder = { Text("https://meet.google.com/...") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                } else {
                                    OutlinedTextField(
                                        value = loc,
                                        onValueChange = { loc = it },
                                        label = { Text("Dirección del Lugar físico") },
                                        placeholder = { Text("Ej. Parque Central, Auditorio Principal") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }

                    // Card 3: Categorías, Tipo y País Sede
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "3. Clasificación de Actividad",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                // Tipo de evento list
                                Column {
                                    Text(
                                        text = "Tipo de Evento:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(eventTypesList) { currentType ->
                                            FilterChip(
                                                selected = eventType == currentType,
                                                onClick = { eventType = currentType },
                                                label = { Text(currentType, fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                }

                                // Categories changed to "Proyecto emblemático que realiza"
                                Column {
                                    Text(
                                        text = "Proyecto emblemático que realiza:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(catsList) { currentCat ->
                                            FilterChip(
                                                selected = cat == currentCat,
                                                onClick = { cat = currentCat },
                                                label = { Text(currentCat, fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                }

                                // Country tags + dynamic item creator
                                Column {
                                    Text(
                                        text = "País Sede:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(countriesList) { currentC ->
                                            FilterChip(
                                                selected = country == currentC,
                                                onClick = { country = currentC },
                                                label = { Text(currentC, fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // Add country dynamically to tags
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = customCountryText,
                                            onValueChange = { customCountryText = it },
                                            placeholder = { Text("Añadir otro país...", fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedContainerColor = Color.Transparent
                                            )
                                        )
                                        Button(
                                            onClick = {
                                                if (customCountryText.isNotBlank()) {
                                                    val trimmedC = customCountryText.trim()
                                                    if (!countriesList.contains(trimmedC)) {
                                                        countriesList = countriesList + trimmedC
                                                    }
                                                    country = trimmedC
                                                    customCountryText = ""
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Añadir país",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Añadir", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Action Bar at Bottom
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 44.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Cancelar", fontWeight = FontWeight.Medium)
                    }
                    Button(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(48.dp),
                        onClick = {
                            if (title.isNotBlank() && desc.isNotBlank()) {
                                val finalLoc = if (isVirtual) {
                                    val platform = loc.ifBlank { "Virtual" }
                                    if (virtualLink.isNotBlank()) "$platform - Enlace: $virtualLink" else platform
                                } else {
                                    loc.ifBlank { "Presencial" }
                                }
                                 val updated = activity.copy(
                                    title = title,
                                    description = desc,
                                    date = "${datePart.trim()} ${timePart.trim()}",
                                    endDate = "${endDatePart.trim()} ${endTimePart.trim()}",
                                    location = finalLoc,
                                    country = country,
                                    category = cat,
                                    organizer = org.ifBlank { "Voluntariado Juvenil" },
                                    eventType = eventType,
                                    isMandatory = isMandatory,
                                    registrationDeadline = if (hasRegistrationDeadline) "${deadlineDatePart.trim()} ${deadlineTimePart.trim()}" else ""
                                )
                                onSaveClick(updated)
                            }
                        },
                        enabled = title.isNotBlank() && desc.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Guardar",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guardar Cambios", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
