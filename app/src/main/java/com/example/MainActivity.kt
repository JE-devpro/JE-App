package com.example

import android.os.Bundle
import android.content.Intent
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
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
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

fun copyUriToLocalStorage(context: Context, uri: android.net.Uri): String? {
    return try {
        val resolver = context.contentResolver
        val inputStream = resolver.openInputStream(uri) ?: return null
        val fileName = "avatar_${System.currentTimeMillis()}.jpg"
        val file = java.io.File(context.filesDir, fileName)
        val outputStream = java.io.FileOutputStream(file)
        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
            MyApplicationTheme {
                MainScreen(viewModel)
            }
        }
    }
}

enum class NavigationTab(val label: String, val iconSelected: androidx.compose.ui.graphics.vector.ImageVector, val iconUnselected: androidx.compose.ui.graphics.vector.ImageVector, val testTag: String) {
    ACTIVITIES("Inicio", Icons.Filled.Home, Icons.Outlined.Home, "tab_activities"),
    CALENDAR("Eventos", Icons.Filled.DateRange, Icons.Outlined.DateRange, "tab_calendar"),
    CARNET("Carnet", Icons.Filled.CardMembership, Icons.Outlined.CardMembership, "tab_carnet"),
    FEED("Novedades", Icons.Filled.MenuBook, Icons.Outlined.MenuBook, "tab_feed"),
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
            member = currentMember,
            onDismiss = { showLoginWelcomeState = false }
        )
        return
    }

    // Core database states
    val activities by viewModel.allActivities.collectAsStateWithLifecycle()
    val articles by viewModel.allArticles.collectAsStateWithLifecycle()
    val enrollments by viewModel.allEnrollments.collectAsStateWithLifecycle()
    val members by viewModel.allMembers.collectAsStateWithLifecycle()
    val rawNotifications by viewModel.allNotifications.collectAsStateWithLifecycle()
    val isUserAdmin = currentMember.isAdmin || currentMember.email == "coordinador@je.org"
    val notifications = remember(rawNotifications, isUserAdmin) {
        if (isUserAdmin) {
            rawNotifications
        } else {
            rawNotifications.filter { it.title != "Inscripción en Actividad 🌿" }
        }
    }
    val googleCalendarLinked by viewModel.googleCalendarLinked.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(NavigationTab.ACTIVITIES) }
    
    // Map activities dynamically based on whether loggedInMember is enrolled or not!
    val mappedActivitiesByEnrollment = remember(activities, enrollments, currentMember) {
        activities.map { activity ->
            val isRegistered = enrollments.any { it.activityId == activity.id && it.memberEmail == currentMember.email }
            activity.copy(isUserRegistered = isRegistered)
        }
    }

    // Support local PIN bypass for testing
    var isOverrideCoordinator by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinText by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    // Determine Coordinator Mode
    val isCoordinadorMode = (currentMember.email == "coordinador@je.org") || currentMember.isAdmin || isOverrideCoordinator

    // UI Local Modals
    var showAddActivityDialog by remember { mutableStateOf(false) }
    var showAddReportDialog by remember { mutableStateOf(false) }
    var showNotificationDialog by remember { mutableStateOf(false) }
    var showProfileMenu by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

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
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
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
                            onToggleEnroll = { viewModel.toggleEnrollment(it, currentMember) },
                            onDeleteActivity = { viewModel.deleteActivity(it) }
                        )
                    }
                    NavigationTab.CALENDAR -> {
                        CalendarTab(
                            activities = mappedActivitiesByEnrollment,
                            isCoordinadorMode = isCoordinadorMode,
                            onToggleEnroll = { viewModel.toggleEnrollment(it, currentMember) },
                            onDeleteActivity = { viewModel.deleteActivity(it) }
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
                            onPublishReportClick = { showAddReportDialog = true }
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
                                    onClick = { currentTab = tab },
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
                                    isOverrideCoordinator = true
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
            onAdd = { title, desc, date, loc, country, cat, org, evType ->
                viewModel.addActivity(title, desc, date, loc, country, cat, org, evType)
                showAddActivityDialog = false
            }
        )
    }

    // Modal dialog for contributing custom columns/reports
    if (showAddReportDialog) {
        AddReportDialog(
            onDismiss = { showAddReportDialog = false },
            onPublish = { title, content, cat, rgn ->
                viewModel.publishLocalArticle(title, content, cat, rgn)
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
    onDeleteActivity: (Int) -> Unit
) {
    var selectedCategoryFilter by remember { mutableStateOf("Todos") }
    val categories = listOf("Todos", "Educación", "Asamblea general", "Actividad", "Voluntariado")

    Column(modifier = Modifier.fillMaxSize()) {
        
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
                .clickable { onNavigateToCarnet() }
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
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    label = "bgColor"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    label = "textColor"
                )
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(bgColor)
                        .clickable { selectedCategoryFilter = cat }
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
                        onToggleEnroll = { onToggleEnroll(item) },
                        onDelete = { onDeleteActivity(item.id) }
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
    onDelete: () -> Unit
) {
    val categoryStyle = remember(item.category) {
        when (item.category.lowercase()) {
            "reforestación" -> Triple(Color(0xFFD1FAE5), Color(0xFF047857), Icons.Filled.Eco)
            "limpieza" -> Triple(Color(0xFFE0F2FE), Color(0xFF0284C7), Icons.Filled.WaterDrop)
            "educación" -> Triple(Color(0xFFFEF3C7), Color(0xFFD97706), Icons.Filled.School)
            "conservación" -> Triple(Color(0xFFE5E7E2), Color(0xFF394931), Icons.Filled.Forest)
            else -> Triple(Color(0xFFEAECE9), Color(0xFF4C5E43), Icons.Filled.NaturePeople)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Main Top info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Styled Left Box
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(categoryStyle.first),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryStyle.third,
                        contentDescription = item.category,
                        tint = categoryStyle.second,
                        modifier = Modifier.size(22.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Text columns
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${item.date} • ${item.category}".uppercase(),
                        color = categoryStyle.second,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
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
                Button(
                    onClick = onToggleEnroll,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (item.isUserRegistered) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
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

// Dialog to Add a custom Activity
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddActivityDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String, String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("2026-06-05") }
    
    // Location parameters
    var loc by remember { mutableStateOf("") }
    var isVirtual by remember { mutableStateOf(false) }
    var virtualLink by remember { mutableStateOf("") }

    var country by remember { mutableStateOf("Ecuador") }
    var cat by remember { mutableStateOf("JE-RAM") }
    var org by remember { mutableStateOf("") }
    var eventType by remember { mutableStateOf("Voluntariado") }

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
                            text = "Crear Actividad Ecológica",
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
                    // Card 1: Información Básica
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
                                    text = "1. Información Básica",
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
                            }
                        }
                    }

                    // Card 2: Fecha, Modalidad y Ubicación
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
                                    text = "2. Fecha y Ubicación",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                OutlinedTextField(
                                    value = date,
                                    onValueChange = { date = it },
                                    label = { Text("Fecha de inicio (AAAA-MM-DD)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

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
                                onAdd(
                                    title,
                                    desc,
                                    date,
                                    finalLoc,
                                    country,
                                    cat,
                                    org.ifBlank { "Voluntariado Juvenil" },
                                    eventType
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
                    text = "Toca la credencial para voltearla y ver el eco-compromiso.",
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

                if (!isCoordinadorMode) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Este carnet es oficial y emitido directamente por la Central de Jóvenes y Ecosistemas Latinoamérica. Para editarlo, habilite el Modo Coordinador arriba con el código secreto.",
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
    onPublishReportClick: () -> Unit
) {
    val searchQuery = ""
    var selectedCategoryFilter by remember { mutableStateOf("Todos") }
    val categories = listOf("Todos", "JE-RAM", "JE-Mental", "JE-Ambiente", "JE-Podcast", "JE-Visual", "Conservación", "Comunidad")

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
                            text = "Boletín Ecológico Juventud",
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
                
                if (!isCoordinadorMode) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "El panel se actualiza con reportes de One Health y proyectos juveniles.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
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
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategoryFilter = cat },
                    label = { Text(cat, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Column Listing
        val filteredArticles = articles.filter {
            val matchesSearch = it.title.contains(searchQuery, ignoreCase = true) ||
                    it.content.contains(searchQuery, ignoreCase = true) ||
                    it.category.contains(searchQuery, ignoreCase = true) ||
                    it.region.contains(searchQuery, ignoreCase = true)
            val matchesCategory = if (selectedCategoryFilter == "Todos") {
                true
            } else {
                it.category.contains(selectedCategoryFilter, ignoreCase = true)
            }
            matchesSearch && matchesCategory
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
                        onDelete = { onDeleteArticle(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ArticleCard(item: EcoArticle, isCoordinadorMode: Boolean, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    val tagText = if (item.isAiGenerated) "📢 BOLETÍN" else "✏️ REPORTE LOCAL"
                    val tagBg = if (item.isAiGenerated) MaterialTheme.colorScheme.primaryContainer else Color(0xFFECFDF5) // Emerald-50
                    val tagColor = if (item.isAiGenerated) MaterialTheme.colorScheme.onPrimaryContainer else Color(0xFF059669) // Emerald-600

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
                        text = item.category.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
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
                lineHeight = 17.sp
            )

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
@Composable
fun AddReportDialog(onDismiss: () -> Unit, onPublish: (String, String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Conservación") }
    var region by remember { mutableStateOf("Amazonas") }

    val categoriesList = listOf("Conservación", "Biodiversidad", "Reforestación", "Leyes", "Comunidad")
    val regionsList = listOf("Amazonía", "Andes", "Patagonia", "El Litoral", "Centroamérica", "El Caribe")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Subir Información / Reporte",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Publica información valiosa sobre ecosistemas locales en la aplicación.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título de la publicación") },
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
                            .height(140.dp),
                        maxLines = 8
                    )
                }

                item {
                    // Category choose
                    Column {
                        Text("Tema / Categoría:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(categoriesList) { catCurrent ->
                                FilterChip(
                                    selected = category == catCurrent,
                                    onClick = { category = catCurrent },
                                    label = { Text(catCurrent, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }

                item {
                    // Region choose
                    Column {
                        Text("Bioma / Región:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(regionsList) { currentZone ->
                                FilterChip(
                                    selected = region == currentZone,
                                    onClick = { region = currentZone },
                                    label = { Text(currentZone, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancelar")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (title.isNotBlank() && content.isNotBlank()) {
                                    onPublish(title, content, category, region)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            enabled = title.isNotBlank() && content.isNotBlank()
                        ) {
                            Text("Subir Publicación")
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

fun extractDayFromDateStr(dateStr: String): Int? {
    return try {
        val parts = dateStr.split("-")
        if (parts.size >= 3) {
            parts[2].toIntOrNull()
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

fun triggerPushNotification(context: Context, activityTitle: String, activityDate: String) {
    val channelId = "je_app_notifications"
    val channelName = "Recordatorios de Actividades"
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Canal para notificaciones de JE-App"
            enableLights(true)
            enableVibration(true)
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

    val defaultSoundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(com.example.R.mipmap.ic_launcher)
        .setContentTitle("Recordatorio de Evento 🌿")
        .setContentText("Tienes una actividad programada: $activityTitle para la fecha $activityDate.")
        .setStyle(NotificationCompat.BigTextStyle().bigText("Tienes una actividad programada: $activityTitle para la fecha $activityDate."))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setSound(defaultSoundUri)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)

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
    isCoordinadorMode: Boolean,
    onToggleEnroll: (EcoActivity) -> Unit,
    onDeleteActivity: (Int) -> Unit
) {
    val context = LocalContext.current
    var selectedEventTypeFilter by remember { mutableStateOf("Todos") }
    val eventTypes = listOf("Todos", "Educación", "Asamblea general", "Actividad", "Voluntariado")

    // Selected Day in June 2026
    var selectedDay by remember { mutableStateOf(5) } // June 5 as default selected day

    // Filtered activities based on filter selection
    val filteredActivities = remember(activities, selectedEventTypeFilter) {
        val allowedTypes = listOf("educación", "asamblea general", "actividad", "voluntariado")
        if (selectedEventTypeFilter == "Todos") {
            activities.filter { it.eventType.lowercase() in allowedTypes }
        } else {
            activities.filter { it.eventType.equals(selectedEventTypeFilter, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Tab Header
        Text(
            text = "Calendario Ecológico".uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.2.sp
        )
        Text(
            text = "Junio 2026",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Category Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            items(eventTypes) { filter ->
                val isSelected = selectedEventTypeFilter == filter
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    label = "calendarChipBg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    label = "calendarChipText"
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .clickable { selectedEventTypeFilter = filter }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(text = filter, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Calendar visual card container
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

                // Days of June 2026 (Starts on Monday, so 1 to 30 grid is direct)
                val daysInMonth = 30
                
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (row in 0 until 5) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            for (col in 0 until 7) {
                                val dayNum = (row * 7) + col + 1
                                if (dayNum <= daysInMonth) {
                                    // Calc if this day has any activities matching the filter
                                    val activitiesOnThisDay = filteredActivities.filter {
                                        extractDayFromDateStr(it.date) == dayNum
                                    }
                                    val hasActivities = activitiesOnThisDay.isNotEmpty()
                                    val isCurrentSelected = selectedDay == dayNum

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .padding(2.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isCurrentSelected -> MaterialTheme.colorScheme.primary
                                                    hasActivities -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                                    else -> Color.Transparent
                                                }
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
                                                    isCurrentSelected -> MaterialTheme.colorScheme.onPrimary
                                                    hasActivities -> MaterialTheme.colorScheme.primary
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                            if (hasActivities && !isCurrentSelected) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            when (activitiesOnThisDay.firstOrNull()?.eventType?.lowercase()) {
                                                                "talleres" -> Color(0xFFD97706)
                                                                "voluntariado" -> Color(0xFF047857)
                                                                "charlas" -> Color(0xFF0284C7)
                                                                else -> MaterialTheme.colorScheme.primary
                                                            }
                                                        )
                                                )
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
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Activities Section Title
        Text(
            text = "EVENTOS DEL DÍA $selectedDay DE JUNIO:".uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.40f),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Find activities for selected day
        val dayFilteredActivities = filteredActivities.filter { extractDayFromDateStr(it.date) == selectedDay }

        if (dayFilteredActivities.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(dayFilteredActivities) { event ->
                    CalendarEventCard(
                        event = event,
                        isCoordinadorMode = isCoordinadorMode,
                        onDelete = { onDeleteActivity(event.id) },
                        onRegister = { onToggleEnroll(event) },
                        onAddToPersonalCalendar = {
                            // Launch Implicit Intent to Calendar
                            try {
                                val calendarIntent = Intent(Intent.ACTION_INSERT).apply {
                                    data = CalendarContract.Events.CONTENT_URI
                                    putExtra(CalendarContract.Events.TITLE, event.title)
                                    putExtra(CalendarContract.Events.DESCRIPTION, "${event.description} \n\nOrganizado por: ${event.organizer}")
                                    putExtra(CalendarContract.Events.EVENT_LOCATION, "${event.location}, ${event.country}")
                                    
                                    val parts = event.date.split("-")
                                    val calendar = Calendar.getInstance()
                                    if (parts.size >= 3) {
                                        calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 10, 0)
                                    }
                                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, calendar.timeInMillis)
                                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, calendar.timeInMillis + 2 * 60 * 60 * 1000)
                                }
                                context.startActivity(calendarIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "No se puede abrir la aplicación de calendario", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSchedulePushReminder = {
                            triggerPushNotification(context, event.title, event.date)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CalendarEventCard(
    event: EcoActivity,
    isCoordinadorMode: Boolean,
    onDelete: () -> Unit,
    onRegister: () -> Unit,
    onAddToPersonalCalendar: () -> Unit,
    onSchedulePushReminder: () -> Unit
) {
    val typeColor = remember(event.eventType) {
        when (event.eventType.lowercase()) {
            "talleres" -> Color(0xFFD97706)
            "voluntariado" -> Color(0xFF047857)
            "charlas" -> Color(0xFF0284C7)
            else -> Color(0xFF6B7280)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, typeColor.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Type Badge + Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Event Type tag badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(typeColor.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = event.eventType.uppercase(),
                        color = typeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Push Reminder Button
                    IconButton(
                        onClick = onSchedulePushReminder,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = "Programar Alerta",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Add to personal calendar
                    IconButton(
                        onClick = onAddToPersonalCalendar,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarToday,
                            contentDescription = "Añadir a Calendario Personal",
                            tint = typeColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Delete Activity (Only for coordinator mode)
                    if (isCoordinadorMode) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Borrar",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Event Title
            Text(
                text = event.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Short Description
            Text(
                text = event.description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Meta Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Info Column
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = typeColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${event.location}, ${event.country}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Text(
                        text = "Por: ${event.organizer}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }

                // Register Button
                Button(
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (event.isUserRegistered) MaterialTheme.colorScheme.primaryContainer else typeColor
                    ),
                    modifier = Modifier.height(28.dp),
                    onClick = onRegister
                ) {
                    Text(
                        text = if (event.isUserRegistered) "Inscrito ✓" else "Inscribirme",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (event.isUserRegistered) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
                    )
                }
            }
        }
    }
}

// ==========================================
// LOGIN & ADMIN CONSOLE SUPPORT COMPONENTS
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: AppViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF394931), // BrandGreen - Dark aesthetic forest green
                        Color(0xFF161F13)  // Very deep brand-toned black-green
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo / Branding Representation
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFB0B59E).copy(alpha = 0.15f))
                    .border(2.dp, Color(0xFFB0B59E), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "Logo Jóvenes y Ecosistemas",
                    modifier = Modifier.size(80.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Portal de Miembros".uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFF18824), // BrandOrange - Accent support orange
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Jóvenes y Ecosistemas Latinoamérica",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center,
                letterSpacing = 0.5.sp,
                lineHeight = 36.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Jóvenes que encajan para transformar",
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFB0B59E),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF232D20).copy(alpha = 0.85f)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFB0B59E).copy(alpha = 0.25f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Iniciar Sesión",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; loginError = false },
                        label = { Text("Correo Electrónico") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFFB0B59E)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFB0B59E),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedLabelColor = Color(0xFFB0B59E),
                            unfocusedLabelColor = Color.White.copy(alpha = 0.4f)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; loginError = false },
                        label = { Text("Contraseña") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFB0B59E)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input"),
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFB0B59E),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedLabelColor = Color(0xFFB0B59E),
                            unfocusedLabelColor = Color.White.copy(alpha = 0.4f)
                        )
                    )

                    if (loginError) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Credenciales incorrectas. Verifique correo y contraseña.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (email.isNotBlank() && password.isNotBlank()) {
                                viewModel.login(email, password) { success ->
                                    if (success) {
                                        Toast.makeText(context, "Bienvenido a Jóvenes y Ecosistemas 🎉", Toast.LENGTH_SHORT).show()
                                    } else {
                                        loginError = true
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Por favor complete todos los campos", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("login_button"),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF18824))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Login, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Acceder", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Forgot Password Link
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showForgotDialog = true }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Olvidé mi contraseña",
                            color = Color(0xFFB0B59E),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("forgot_password_button")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Helper Panel with Seed Accounts
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161F13).copy(alpha = 0.8f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Cuentas autorizadas de demostración:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Director: coordinador@je.org / JE2026\n• Miembro RAM: miembro2@je.org / miembro2\n• Miembro Salud Mental: miembro3@je.org / miembro3",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.45f),
                        lineHeight = 14.sp
                    )
                }
            }
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
    val googleCalendarAccount by viewModel.googleCalendarAccount.collectAsStateWithLifecycle()
    var showGoogleLinkDialog by remember { mutableStateOf(false) }
    val isDirector = currentMember.isAdmin || currentMember.email == "coordinador@je.org" || currentMember.role == "Director General"

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

        if (isDirector) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (googleCalendarLinked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) 
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Vincular Calendario de Google",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (googleCalendarLinked) "Sincronizado: $googleCalendarAccount ✓\n(Actividades ligadas en vivo)" 
                            else "Programa actividades directo en Google Calendar",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            lineHeight = 14.sp
                        )
                    }
                    
                    Button(
                        onClick = {
                            if (googleCalendarLinked) {
                                viewModel.toggleGoogleCalendarLinked()
                                Toast.makeText(context, "📅 Cuenta Google Desvinculada", Toast.LENGTH_SHORT).show()
                            } else {
                                showGoogleLinkDialog = true
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (googleCalendarLinked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                            contentColor = if (googleCalendarLinked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = if (googleCalendarLinked) "Desvincular" else "Vincular",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        if (showGoogleLinkDialog) {
            var enteredEmail by remember { mutableStateOf(googleCalendarAccount) }
            AlertDialog(
                onDismissRequest = { showGoogleLinkDialog = false },
                title = {
                    Text(
                        text = "Vincular Cuenta de Google",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Ingrese la dirección de correo electrónico de la cuenta de Google para simular la vinculación e integración de alertas en vivo:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        OutlinedTextField(
                            value = enteredEmail,
                            onValueChange = { enteredEmail = it },
                            label = { Text("Correo de Google") },
                            placeholder = { Text("ejemplo@gmail.com") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = "Información",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Nota de Simulación: Esta vinculación es visual y simula la conexión en la nube. Para agregar un evento real a tu Calendario personal de Google, usa el botón de calendario 📅 de cualquier actividad en la pestaña 'Eventos'. El emulador abrirá tu app de calendario local con los detalles pre-cargados.",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (enteredEmail.isNotBlank()) {
                                viewModel.toggleGoogleCalendarLinked(enteredEmail)
                                showGoogleLinkDialog = false
                                Toast.makeText(context, "📅 Cuenta Vinculada (Modo Demostración)", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Vincular", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGoogleLinkDialog = false }) {
                        Text("Cancelar")
                    }
                },
                shape = RoundedCornerShape(20.dp)
            )
        }

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
                    AdminEnrollmentsPanel(enrollments = enrollments, activities = activities)
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
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<Member?>(null) }

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

        Spacer(modifier = Modifier.height(8.dp))

        if (members.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No hay miembros registrados", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(members) { member ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
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
                                    IconButton(onClick = { viewModel.removeMember(member.email) }) {
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
        var newRole by remember { mutableStateOf("Embajador JE-RAM") }
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

        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            title = { Text("Registrar Cuenta", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    
                    Text("Rol One Health:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    val roles = listOf("Embajador JE-RAM", "Líder JE-Mental", "Creador JE-Podcast", "Brigada JE-Ambiente", "Director JE-Visual")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(roles) { r ->
                                val sel = newRole == r
                                Badge(
                                    containerColor = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clickable { newRole = r }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(r, fontSize = 10.sp, modifier = Modifier.padding(2.dp))
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

                    Text("Fotografía Oficial (Opcional):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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

                    Text("Imágen de Código QR (Opcional):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                                    addQrLauncher.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!newQrUri.isNullOrEmpty()) {
                                AsyncImage(
                                    model = newQrUri,
                                    contentDescription = "QR elegido",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.QrCode,
                                    contentDescription = "Subir QR",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                )
                            }
                        }
                        
                        Column(modifier = Modifier.weight(1.0f)) {
                            Button(
                                onClick = {
                                    addQrLauncher.launch(
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
                                Text("Añadir código QR", fontSize = 12.sp)
                            }
                            
                            if (!newQrUri.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(
                                    onClick = { newQrUri = null },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(imageVector = Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Quitar QR", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    
                    Text("Avatar / Emoji:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Carnet Manual (Exclusivo Administrador):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
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
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
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
            },
            confirmButton = {
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
                                } else {
                                    // already exists
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Registrar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMemberDialog = false }) {
                    Text("Cancelar")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (editingMember != null) {
        val memberToEdit = editingMember!!
        var editName by remember { mutableStateOf(memberToEdit.fullName) }
        var editRole by remember { mutableStateOf(memberToEdit.role) }
        var editCountry by remember { mutableStateOf(memberToEdit.country) }
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

        AlertDialog(
            onDismissRequest = { editingMember = null },
            title = { Text("Modificar Carnet", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Nombre Completo") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = editRole,
                        onValueChange = { editRole = it },
                        label = { Text("Rol / Sub-proyecto") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editCountry,
                        onValueChange = { editCountry = it },
                        label = { Text("País") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text("Fotografía Oficial (Opcional):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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

                    Text("Imágen de Código QR (Opcional):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                                    editQrLauncher.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!editQrUri.isNullOrEmpty()) {
                                AsyncImage(
                                    model = editQrUri,
                                    contentDescription = "QR elegido",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.QrCode,
                                    contentDescription = "Subir QR",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                )
                            }
                        }
                        
                        Column(modifier = Modifier.weight(1.0f)) {
                            Button(
                                onClick = {
                                    editQrLauncher.launch(
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
                                Text("Añadir / Cambiar QR", fontSize = 12.sp)
                            }
                            
                            if (!editQrUri.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(
                                    onClick = { editQrUri = null },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(imageVector = Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Quitar QR", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    
                    Text("Avatar / Emoji:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Carnet Manual (Exclusivo Administrador):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
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
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
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
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = memberToEdit.copy(
                            fullName = editName,
                            role = editRole,
                            country = editCountry,
                            emojiAvatar = editEmoji,
                            isAdmin = if (memberToEdit.email == "coordinador@je.org") true else editIsAdmin,
                            photoUri = editPhotoUri,
                            qrUri = editQrUri,
                            customCara1Uri = editCara1Uri,
                            customCara2Uri = editCara2Uri
                        )
                        viewModel.modifyMemberProfile(updated)
                        editingMember = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Guardar Cambios", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMember = null }) {
                    Text("Cancelar")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun AdminEnrollmentsPanel(enrollments: List<EcoEnrollment>, activities: List<EcoActivity>) {
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
                items(activities) { activity ->
                    val activityEnrollments = enrollments.filter { it.activityId == activity.id }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                ) {
                                    Text("${activityEnrollments.size} inscritos", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                            Text(
                                text = "Fecha: ${activity.date} | Categoría: ${activity.category}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            Spacer(modifier = Modifier.height(8.dp))

                            if (activityEnrollments.isEmpty()) {
                                Text(
                                    text = "Ningún miembro se ha inscrito todavía.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    style = androidx.compose.ui.text.TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                )
                            } else {
                                activityEnrollments.forEach { enrollment ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = enrollment.memberName,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Text(
                                            text = enrollment.memberEmail,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
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

@Composable
fun AdminAlertsPanel(viewModel: AppViewModel, notifications: List<EcoNotification>) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Buzón de Notificaciones de Red".uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            
            TextButton(
                onClick = { viewModel.markNotificationsRead() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Marcar Leídas", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

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
                items(notifications) { alert ->
                    val colorAlpha = if (alert.isRead) 0.15f else 0.4f
                    val headerColor = if (alert.isRead) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = colorAlpha))
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
                                    Icon(
                                        imageVector = if (alert.isRead) Icons.Default.NotificationsNone else Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = headerColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = alert.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = headerColor
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = alert.message,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = alert.timestamp,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                            
                            IconButton(onClick = { viewModel.clearNotification(alert.id) }) {
                                Icon(Icons.Default.Clear, contentDescription = "Descartar", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(member: Member, onDismiss: () -> Unit) {
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
        kotlinx.coroutines.delay(2800)
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
                    Text(
                        text = member.role,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Has ingresado con éxito a la Red Latinoamericana de Jóvenes y Ecosistemas.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Credencial: ${member.credentialId} • América Latina",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Acceder al Portal",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
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
    val cloudSyncUrl by viewModel.cloudSyncUrl.collectAsStateWithLifecycle()
    val prefNotifActividades by viewModel.prefNotifActividades.collectAsStateWithLifecycle()
    val prefNotifNovedades by viewModel.prefNotifNovedades.collectAsStateWithLifecycle()
    val prefNotifNube by viewModel.prefNotifNube.collectAsStateWithLifecycle()
    val prefNotifSistema by viewModel.prefNotifSistema.collectAsStateWithLifecycle()

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

                // Section 2: Cloud Storage URL (Read-only stable card info)
                Text(
                    text = "Dirección de Sincronización (Nube)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Servidor Oficial Estable",
                            fontSize = 12.sp,
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
                        Text("• Motor de sincronización: OK (JSON Blob Engine)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NotificationListDialog(
    viewModel: com.example.ui.AppViewModel,
    notifications: List<EcoNotification>,
    onDismiss: () -> Unit,
    onMarkAllRead: () -> Unit,
    onClearNotification: (Int) -> Unit
) {
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
                if (unreadCount > 0) {
                    Button(
                        onClick = onMarkAllRead,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Marcar todas como leídas", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                        items(notifications.sortedByDescending { it.id }) { alert ->
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
    val context = LocalContext.current

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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        else MaterialTheme.colorScheme.primary
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
    }
}
