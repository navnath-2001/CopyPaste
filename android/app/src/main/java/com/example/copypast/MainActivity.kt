
package com.example.copypast

import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.thread

// ============================================================
// COPYPAST — PROFESSIONAL MOBILE APP
// ============================================================

private const val FIRESTORE_DATABASE = "clipboard"
private const val LAPTOP_TO_PHONE = "laptop_to_phone"
private const val PHONE_TO_LAPTOP = "phone_to_laptop"
private const val CHUNK_SIZE = 350_000
private const val GITHUB_LATEST_API =
    "https://api.github.com/repos/navnath-2001/CopyPaste/releases/latest"
private const val GITHUB_RELEASES_URL =
    "https://github.com/navnath-2001/CopyPaste/releases/latest"

private val Blue = Color(0xFF2563EB)
private val Navy = Color(0xFF0F172A)
private val Slate = Color(0xFF475569)
private val Muted = Color(0xFF64748B)
private val PageLight = Color(0xFFF5F8FC)
private val CardLight = Color.White
private val BorderLight = Color(0xFFE2E8F0)
private val Green = Color(0xFF16A34A)
private val GreenSoft = Color(0xFFEAF8EF)
private val Red = Color(0xFFDC2626)
private val RedSoft = Color(0xFFFEF2F2)
private val BlueSoft = Color(0xFFEFF6FF)

// ============================================================
// MAIN ACTIVITY
// ============================================================

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CopyPastTheme {
                AuthGate()
            }
        }
    }
}

// ============================================================
// THEME
// ============================================================

@Composable
private fun CopyPastTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = Blue,
            onPrimary = Color.White,
            background = PageLight,
            surface = CardLight,
            onBackground = Navy,
            onSurface = Navy
        ),
        content = content
    )
}

// ============================================================
// AUTH GATE
// ============================================================

@Composable
fun AuthGate() {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    var showRegister by remember { mutableStateOf(false) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener {
            user = it.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    if (user == null) {
        if (showRegister) {
            RegisterScreen(
                auth = auth,
                onBackToLogin = { showRegister = false }
            )
        } else {
            LoginScreen(
                auth = auth,
                onCreateAccount = { showRegister = true }
            )
        }
    } else {
        ClipboardScreen(auth = auth)
    }
}

// ============================================================
// LOGIN
// ============================================================

@Composable
fun LoginScreen(
    auth: FirebaseAuth,
    onCreateAccount: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    AuthPage(
        title = "Welcome back",
        subtitle = "Sign in to sync your clipboard across devices."
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                message = ""
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email") },
            placeholder = { Text("you@example.com") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                message = ""
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(14.dp)
        )

        AnimatedVisibility(visible = message.isNotBlank()) {
            Column {
                Spacer(Modifier.height(12.dp))
                MessageBanner(
                    text = message
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    message = "Please enter your email and password."
                    return@Button
                }

                loading = true
                message = ""

                auth.signInWithEmailAndPassword(email.trim(), password)
                    .addOnSuccessListener {
                        loading = false
                    }
                    .addOnFailureListener {
                        loading = false
                        message = it.message ?: "Unable to sign in."
                    }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = !loading,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Blue
            )
        ) {
            Text(
                if (loading) "Signing in..." else "SIGN IN",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onCreateAccount,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                "CREATE ACCOUNT",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================
// REGISTER
// ============================================================

@Composable
fun RegisterScreen(
    auth: FirebaseAuth,
    onBackToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    AuthPage(
        title = "Create your account",
        subtitle = "Use this same account on your laptop and phone."
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                message = ""
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email") },
            placeholder = { Text("you@example.com") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                message = ""
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                message = ""
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Confirm password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(14.dp)
        )

        AnimatedVisibility(visible = message.isNotBlank()) {
            Column {
                Spacer(Modifier.height(12.dp))
                MessageBanner(
                    text = message
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                when {
                    email.isBlank() || password.isBlank() || confirmPassword.isBlank() ->
                        message = "Please fill in all fields."

                    password.length < 6 ->
                        message = "Password must contain at least 6 characters."

                    password != confirmPassword ->
                        message = "Passwords do not match."

                    else -> {
                        loading = true
                        message = ""

                        auth.createUserWithEmailAndPassword(
                            email.trim(),
                            password
                        ).addOnSuccessListener {
                            loading = false
                        }.addOnFailureListener {
                            loading = false
                            message = it.message ?: "Unable to create account."
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = !loading,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Blue)
        ) {
            Text(
                if (loading) "Creating account..." else "CREATE ACCOUNT",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBackToLogin,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                "BACK TO SIGN IN",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================
// AUTH PAGE
// ============================================================

@Composable
private fun AuthPage(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = PageLight
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(18.dp))

            CopyPastLogo(size = 76.dp)

            Spacer(Modifier.height(18.dp))

            Text(
                "CopyPast",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Navy
            )

            Spacer(Modifier.height(4.dp))

            Text(
                "FAST  •  EASY  •  SMART",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Blue,
                letterSpacing = 1.2.sp
            )

            Spacer(Modifier.height(30.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardLight),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp)
                ) {
                    Text(
                        title,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )

                    Spacer(Modifier.height(7.dp))

                    Text(
                        subtitle,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = Muted
                    )

                    Spacer(Modifier.height(24.dp))

                    content()
                }
            }

            Spacer(Modifier.height(22.dp))

            Text(
                "CopyPast • Secure clipboard synchronization",
                fontSize = 11.sp,
                color = Muted
            )
        }
    }
}

// ============================================================
// MAIN CLIPBOARD SCREEN
// ============================================================

@Composable
fun ClipboardScreen(auth: FirebaseAuth) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance(FIRESTORE_DATABASE) }
    val uid = auth.currentUser?.uid ?: return

    LaunchedEffect(uid) {
        db.collection("users")
            .document(uid)
            .collection("session")
            .document("status")
            .set(
                mapOf(
                    "loggedIn" to true,
                    "device" to "Phone",
                    "timestamp" to System.currentTimeMillis()
                )
            )
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var isSyncEnabled by remember { mutableStateOf(true) }
    var laptopText by remember { mutableStateOf("No clipboard received yet.") }
    var statusText by remember { mutableStateOf("Ready to sync") }
    var statusOnline by remember { mutableStateOf(true) }
    var receivedImageUri by remember { mutableStateOf<Uri?>(null) }
    var latestType by remember { mutableStateOf("TEXT") }

    var latestVersion by remember { mutableStateOf<String?>(null) }
    var updateApkUrl by remember { mutableStateOf<String?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    val history = remember { mutableStateListOf<HistoryItem>() }

    DisposableEffect(Unit) {
        checkForCopyPastUpdate(context) { version, apkUrl ->
            latestVersion = version
            updateApkUrl = apkUrl
            showUpdateDialog = true
        }
        onDispose { }
    }

    // Laptop -> Phone
    DisposableEffect(uid, isSyncEnabled) {
        if (!isSyncEnabled) {
            onDispose { }
        } else {
            val transferRef = db.collection("users")
                .document(uid)
                .collection("clipboard")
                .document(LAPTOP_TO_PHONE)

            val listener = transferRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    statusOnline = false
                    statusText = "Connection error"
                    return@addSnapshotListener
                }

                statusOnline = true

                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val data = snapshot.data ?: return@addSnapshotListener
                val type = data["type"] as? String ?: "text"

                if (type == "text") {
                    val text = data["text"] as? String

                    if (!text.isNullOrEmpty()) {
                        copyTextToPhone(context, text)
                        laptopText = text
                        receivedImageUri = null
                        latestType = "TEXT"
                        statusText = "Clipboard received from laptop"

                        history.add(
                            0,
                            HistoryItem(
                                type = "TEXT",
                                direction = "Laptop → Phone",
                                preview = text.take(120),
                                time = System.currentTimeMillis()
                            )
                        )

                        if (history.size > 30) history.removeAt(history.lastIndex)

                        deleteTransfer(db, uid)
                    }
                } else if (type == "image") {
                    val imageId = data["imageId"] as? String
                    val chunkCount = (data["chunks"] as? Number)?.toInt() ?: 0

                    if (!imageId.isNullOrEmpty() && chunkCount > 0) {
                        statusText = "Receiving image..."

                        receiveImageFromLaptop(
                            context = context,
                            db = db,
                            uid = uid,
                            imageId = imageId,
                            chunkCount = chunkCount,
                            onSuccess = { imageUri ->
                                receivedImageUri = imageUri
                                laptopText = "Image received and copied to clipboard."
                                latestType = "IMAGE"
                                statusText = "Image received successfully"

                                history.add(
                                    0,
                                    HistoryItem(
                                        type = "IMAGE",
                                        direction = "Laptop → Phone",
                                        preview = "Image copied to phone clipboard",
                                        time = System.currentTimeMillis()
                                    )
                                )

                                if (history.size > 30) history.removeAt(history.lastIndex)

                                deleteTransfer(db, uid)
                            },
                            onFailure = {
                                statusText = "Image receive failed: $it"
                            }
                        )
                    }
                }
            }

            onDispose { listener.remove() }
        }
    }

    // Remote logout
    DisposableEffect(uid) {
        val sessionRef = db.collection("users")
            .document(uid)
            .collection("session")
            .document("status")

        val listener = sessionRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

            val loggedIn = snapshot.getBoolean("loggedIn") ?: true
            val device = snapshot.getString("device")

            if (!loggedIn && device != "Phone") {
                auth.signOut()
            }
        }

        onDispose { listener.remove() }
    }

    if (showUpdateDialog && latestVersion != null) {
        UpdateDialog(
            latestVersion = latestVersion!!,
            apkUrl = updateApkUrl,
            onDismiss = { showUpdateDialog = false }
        )
    }

    Scaffold(
        containerColor = PageLight,
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("⌂", fontSize = 22.sp) },
                    label = { Text("Home") }
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("◷", fontSize = 21.sp) },
                    label = { Text("History") }
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Text("⚙", fontSize = 20.sp) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { innerPadding ->

        when (selectedTab) {
            0 -> HomeTab(
                modifier = Modifier.padding(innerPadding),
                db = db,
                uid = uid,
                isSyncEnabled = isSyncEnabled,
                onSyncChanged = {
                    isSyncEnabled = it
                    statusOnline = it
                    statusText = if (it) {
                        "Synchronization enabled"
                    } else {
                        "Synchronization paused"
                    }
                },
                laptopText = laptopText,
                latestType = latestType,
                receivedImageUri = receivedImageUri,
                statusText = statusText,
                statusOnline = statusOnline,
                context = context,
                onSendSuccess = {
                    statusText = it
                    history.add(
                        0,
                        HistoryItem(
                            type = latestType,
                            direction = "Phone → Laptop",
                            preview = if (latestType == "IMAGE") {
                                "Image sent to laptop"
                            } else {
                                "Text sent to laptop"
                            },
                            time = System.currentTimeMillis()
                        )
                    )
                    if (history.size > 30) history.removeAt(history.lastIndex)
                },
                onSendFailure = { statusText = it }
            )

            1 -> HistoryTab(
                modifier = Modifier.padding(innerPadding),
                history = history
            )

            2 -> SettingsTab(
                modifier = Modifier.padding(innerPadding),
                auth = auth,
                db = db,
                uid = uid,
                context = context,
                latestVersion = latestVersion
            )
        }
    }
}

// ============================================================
// HOME TAB
// ============================================================

@Composable
private fun HomeTab(
    modifier: Modifier,
    db: FirebaseFirestore,
    uid: String,
    isSyncEnabled: Boolean,
    onSyncChanged: (Boolean) -> Unit,
    laptopText: String,
    latestType: String,
    receivedImageUri: Uri?,
    statusText: String,
    statusOnline: Boolean,
    context: Context,
    onSendSuccess: (String) -> Unit,
    onSendFailure: (String) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 22.dp,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Header()
        }

        item {
            SyncCard(
                enabled = isSyncEnabled,
                online = statusOnline,
                onChanged = onSyncChanged
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DeviceCard(
                    modifier = Modifier.weight(1f),
                    title = "Laptop",
                    subtitle = "Connected",
                    icon = "PC",
                    online = statusOnline
                )

                DeviceCard(
                    modifier = Modifier.weight(1f),
                    title = "Phone",
                    subtitle = "This device",
                    icon = "PHONE",
                    online = true
                )
            }
        }

        item {
            ClipboardCard(
                text = laptopText,
                type = latestType,
                imageUri = receivedImageUri,
                enabled = isSyncEnabled,
                onSend = {
                    if (!isSyncEnabled) {
                        onSendFailure("Turn on synchronization first")
                        return@ClipboardCard
                    }

                    sendCurrentClipboard(
                        context = context,
                        db = db,
                        uid = uid,
                        onSuccess = onSendSuccess,
                        onFailure = onSendFailure
                    )
                }
            )
        }

        item {
            StatusCard(
                text = statusText,
                online = statusOnline
            )
        }

        item {
            InfoCard()
        }
    }
}

// ============================================================
// HEADER
// ============================================================

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CopyPastLogo(size = 58.dp)

        Spacer(Modifier.width(13.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                "CopyPast",
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Navy
            )

            Text(
                "Your clipboard, everywhere.",
                fontSize = 13.sp,
                color = Muted
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(BlueSoft)
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Text(
                "LIVE",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Blue
            )
        }
    }
}

// ============================================================
// LOGO
// ============================================================

@Composable
private fun CopyPastLogo(size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.23f))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF0B1F3A),
                        Color(0xFF123E70),
                        Color(0xFF2563EB)
                    )
                )
            )
            .padding(size * 0.13f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(size * 0.17f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White)
            )

            Spacer(Modifier.height(size * 0.07f))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .height(size * 0.17f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF38BDF8))
            )

            Spacer(Modifier.height(size * 0.08f))

            Text(
                "↔",
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = (size.value * 0.22f).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

// ============================================================
// SYNC CARD
// ============================================================

@Composable
private fun SyncCard(
    enabled: Boolean,
    online: Boolean,
    onChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (enabled) GreenSoft else Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (enabled) "✓" else "—",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) Green else Muted
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (enabled) "Synchronization is ON" else "Synchronization is OFF",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )

                Spacer(Modifier.height(3.dp))

                Text(
                    if (enabled && online) {
                        "Real-time clipboard sync is active"
                    } else {
                        "Clipboard synchronization is paused"
                    },
                    fontSize = 12.sp,
                    color = Muted
                )
            }

            Switch(
                checked = enabled,
                onCheckedChange = onChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Green,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFCBD5E1),
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
    }
}

// ============================================================
// DEVICE CARD
// ============================================================

@Composable
private fun DeviceCard(
    modifier: Modifier,
    title: String,
    subtitle: String,
    icon: String,
    online: Boolean
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(15.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(39.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(BlueSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        icon,
                        fontSize = if (icon == "PHONE") 7.sp else 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Blue
                    )
                }

                Spacer(Modifier.width(9.dp))

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (online) Green else Color(0xFF94A3B8))
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Navy
            )

            Spacer(Modifier.height(2.dp))

            Text(
                subtitle,
                fontSize = 11.sp,
                color = Muted
            )
        }
    }
}

// ============================================================
// CLIPBOARD CARD
// ============================================================

@Composable
private fun ClipboardCard(
    text: String,
    type: String,
    imageUri: Uri?,
    enabled: Boolean,
    onSend: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(17.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "LATEST CLIPBOARD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Blue,
                        letterSpacing = 0.8.sp
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        if (type == "IMAGE") "Image content" else "Text content",
                        fontSize = 12.sp,
                        color = Muted
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (type == "IMAGE") Color(0xFFF3E8FF) else BlueSoft)
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Text(
                        type,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (type == "IMAGE") Color(0xFF7E22CE) else Blue
                    )
                }
            }

            Spacer(Modifier.height(13.dp))

            if (type == "IMAGE" && imageUri != null) {
                ReceivedImagePreview(
                    uri = imageUri
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    "Image copied to your phone clipboard.",
                    fontSize = 13.sp,
                    color = Slate
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(15.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(
                            1.dp,
                            BorderLight,
                            RoundedCornerShape(15.dp)
                        )
                        .padding(15.dp)
                ) {
                    Text(
                        if (enabled) text else "Synchronization is paused.",
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = Slate,
                        maxLines = 7,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = onSend,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = enabled,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Blue,
                    disabledContainerColor = Color(0xFFCBD5E1)
                )
            ) {
                Text(
                    "SEND CLIPBOARD TO LAPTOP",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun ReceivedImagePreview(uri: Uri) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        bitmap = try {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            }
        } catch (_: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Received clipboard image",
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(15.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0xFFF1F5F9)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Image preview unavailable",
                fontSize = 13.sp,
                color = Muted
            )
        }
    }
}

// ============================================================
// STATUS CARD
// ============================================================

@Composable
private fun StatusCard(
    text: String,
    online: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (online) GreenSoft else RedSoft
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (online) Color(0xFFBBE7C8) else Color(0xFFFECACA)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (online) Green else Red)
            )

            Spacer(Modifier.width(10.dp))

            Column {
                Text(
                    if (online) "CONNECTED" else "CONNECTION ISSUE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (online) Green else Red
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text,
                    fontSize = 12.sp,
                    color = if (online) Color(0xFF166534) else Color(0xFF991B1B)
                )
            }
        }
    }
}

// ============================================================
// INFO CARD
// ============================================================

@Composable
private fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
    ) {
        Column(Modifier.padding(15.dp)) {
            Text(
                "HOW COPYPAST WORKS",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Blue,
                letterSpacing = 0.8.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Copy on your laptop and the content appears on your phone. "
                        + "Copy on your phone and tap Send to Laptop.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = Muted
            )
        }
    }
}

// ============================================================
// HISTORY
// ============================================================

private data class HistoryItem(
    val type: String,
    val direction: String,
    val preview: String,
    val time: Long
)

@Composable
private fun HistoryTab(
    modifier: Modifier,
    history: List<HistoryItem>
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Text(
            "History",
            fontSize = 29.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Navy
        )

        Spacer(Modifier.height(5.dp))

        Text(
            "Recent clipboard transfers during this session.",
            fontSize = 13.sp,
            color = Muted
        )

        Spacer(Modifier.height(18.dp))

        if (history.isEmpty()) {
            EmptyState()
        } else {
            history.forEach { item ->
                HistoryCard(item)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun HistoryCard(item: HistoryItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (item.type == "IMAGE") Color(0xFFF3E8FF) else BlueSoft),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (item.type == "IMAGE") "IMG" else "TXT",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (item.type == "IMAGE") Color(0xFF7E22CE) else Blue
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.direction,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )

                Spacer(Modifier.height(3.dp))

                Text(
                    item.preview,
                    fontSize = 12.sp,
                    color = Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "↔",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Blue
            )

            Spacer(Modifier.height(10.dp))

            Text(
                "No transfers yet",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Navy
            )

            Spacer(Modifier.height(5.dp))

            Text(
                "Your recent clipboard activity will appear here.",
                fontSize = 13.sp,
                color = Muted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ============================================================
// SETTINGS
// ============================================================

@Composable
private fun SettingsTab(
    modifier: Modifier,
    auth: FirebaseAuth,
    db: FirebaseFirestore,
    uid: String,
    context: Context,
    latestVersion: String?
) {
    val currentVersion = remember {
        getInstalledVersion(context)
    }

    var notifications by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Text(
            "Settings",
            fontSize = 29.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Navy
        )

        Spacer(Modifier.height(5.dp))

        Text(
            "Manage your CopyPast account and app preferences.",
            fontSize = 13.sp,
            color = Muted
        )

        Spacer(Modifier.height(18.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
        ) {
            Column(Modifier.padding(17.dp)) {
                Text(
                    "ACCOUNT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Blue,
                    letterSpacing = 0.8.sp
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    auth.currentUser?.email ?: "Signed in",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    "Firebase account",
                    fontSize = 12.sp,
                    color = Muted
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
        ) {
            Column(Modifier.padding(17.dp)) {
                Text(
                    "PREFERENCES",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Blue,
                    letterSpacing = 0.8.sp
                )

                Spacer(Modifier.height(8.dp))

                PreferenceRow(
                    checked = notifications,
                    onCheckedChange = { notifications = it }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
        ) {
            Column(Modifier.padding(17.dp)) {
                Text(
                    "ABOUT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Blue,
                    letterSpacing = 0.8.sp
                )

                Spacer(Modifier.height(12.dp))

                SettingLine("Installed version", currentVersion)
                Spacer(Modifier.height(8.dp))
                SettingLine("Latest release", latestVersion ?: "Checking...")
                Spacer(Modifier.height(8.dp))
                SettingLine("Database", "Firebase Firestore")
                Spacer(Modifier.height(8.dp))
                SettingLine("Sync", "Real-time")
            }
        }

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = {
                db.collection("users")
                    .document(uid)
                    .collection("session")
                    .document("status")
                    .set(
                        mapOf(
                            "loggedIn" to false,
                            "device" to "Phone",
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                    .addOnCompleteListener {
                        auth.signOut()
                    }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = RedSoft,
                contentColor = Red
            )
        ) {
            Text(
                "SIGN OUT",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(22.dp))

        Text(
            "CopyPast • FAST • EASY • SMART",
            modifier = Modifier.fillMaxWidth(),
            fontSize = 11.sp,
            color = Muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun PreferenceRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Update notifications",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Navy
            )

            Spacer(Modifier.height(3.dp))

            Text(
                "Check GitHub for new CopyPast releases",
                fontSize = 11.sp,
                color = Muted
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Blue,
                checkedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun SettingLine(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = Muted
        )

        Text(
            value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Navy
        )
    }
}

// ============================================================
// UPDATE DIALOG
// ============================================================

@Composable
private fun UpdateDialog(
    latestVersion: String,
    apkUrl: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        title = {
            Column {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(BlueSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "↑",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Blue
                    )
                }

                Spacer(Modifier.height(15.dp))

                Text(
                    "New update available",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Navy
                )
            }
        },
        text = {
            Column {
                Text(
                    "A newer version of CopyPast is ready.",
                    fontSize = 14.sp,
                    color = Slate,
                    lineHeight = 21.sp
                )

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(BlueSoft)
                        .padding(13.dp)
                ) {
                    Column {
                        Text(
                            "LATEST VERSION",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Blue
                        )

                        Spacer(Modifier.height(3.dp))

                        Text(
                            "CopyPast v$latestVersion",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Navy
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    "Download the latest APK from the official CopyPast GitHub release.",
                    fontSize = 12.sp,
                    color = Muted,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = apkUrl ?: GITHUB_RELEASES_URL
                    try {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                target.toUri()
                            )
                        )
                    } catch (_: Exception) {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                GITHUB_RELEASES_URL.toUri()
                            )
                        )
                    }
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Blue
                )
            ) {
                Text(
                    "DOWNLOAD UPDATE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "LATER",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    )
}

// ============================================================
// MESSAGE
// ============================================================

@Composable
private fun MessageBanner(
    text: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RedSoft)
            .padding(12.dp)
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = Red
        )
    }
}

// ============================================================
// SEND CURRENT CLIPBOARD
// ============================================================

private fun sendCurrentClipboard(
    context: Context,
    db: FirebaseFirestore,
    uid: String,
    onSuccess: (String) -> Unit,
    onFailure: (String) -> Unit
) {
    val clipboard = context.getSystemService(
        Context.CLIPBOARD_SERVICE
    ) as ClipboardManager

    val clip = clipboard.primaryClip

    if (clip == null || clip.itemCount == 0) {
        onFailure("Phone clipboard is empty")
        return
    }

    val item = clip.getItemAt(0)

    if (item.uri != null) {
        sendImageToLaptop(
            context = context,
            db = db,
            uid = uid,
            uri = item.uri!!,
            onSuccess = { onSuccess("Image sent to laptop") },
            onFailure = onFailure
        )
    } else {
        val clipboardText = item.coerceToText(context).toString()

        if (clipboardText.isBlank()) {
            onFailure("Phone clipboard is empty")
            return
        }

        sendTextToLaptop(
            db = db,
            uid = uid,
            text = clipboardText,
            onSuccess = { onSuccess("Text sent to laptop") },
            onFailure = onFailure
        )
    }
}

// ============================================================
// SEND TEXT PHONE -> LAPTOP
// ============================================================

private fun sendTextToLaptop(
    db: FirebaseFirestore,
    uid: String,
    text: String,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit
) {
    db.collection("users")
        .document(uid)
        .collection("clipboard")
        .document(PHONE_TO_LAPTOP)
        .set(
            mapOf(
                "type" to "text",
                "text" to text,
                "device" to "Phone",
                "timestamp" to System.currentTimeMillis()
            )
        )
        .addOnSuccessListener { onSuccess() }
        .addOnFailureListener {
            onFailure(it.message ?: "Unable to send text.")
        }
}

// ============================================================
// SEND IMAGE PHONE -> LAPTOP
// ============================================================

private fun sendImageToLaptop(
    context: Context,
    db: FirebaseFirestore,
    uid: String,
    uri: Uri,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit
) {
    thread {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw Exception("Cannot open clipboard image")

            val imageBytes = inputStream.use { it.readBytes() }

            if (imageBytes.isEmpty()) {
                throw Exception("Image is empty")
            }

            val encoded = Base64.encodeToString(
                imageBytes,
                Base64.NO_WRAP
            )

            val imageId =
                System.currentTimeMillis().toString() +
                        "_" +
                        sha256(imageBytes).take(12)

            val chunks = encoded.chunked(CHUNK_SIZE)

            val parent = db.collection("users")
                .document(uid)
                .collection("clipboard")
                .document(PHONE_TO_LAPTOP)

            deleteTransferSync(
                db,
                uid,
                PHONE_TO_LAPTOP
            )

            for (index in chunks.indices) {
                Tasks.await(
                    parent
                        .collection("chunks")
                        .document(index.toString())
                        .set(
                            mapOf(
                                "data" to chunks[index]
                            )
                        )
                )
            }

            Tasks.await(
                parent.set(
                    mapOf(
                        "type" to "image",
                        "imageId" to imageId,
                        "chunks" to chunks.size,
                        "mimeType" to "image/png",
                        "size" to imageBytes.size,
                        "device" to "Phone",
                        "timestamp" to System.currentTimeMillis()
                    )
                )
            )

            Handler(Looper.getMainLooper()).post {
                onSuccess()
            }
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                onFailure(e.message ?: "Unknown image error")
            }
        }
    }
}

// ============================================================
// RECEIVE IMAGE LAPTOP -> PHONE
// ============================================================

private fun receiveImageFromLaptop(
    context: Context,
    db: FirebaseFirestore,
    uid: String,
    imageId: String,
    chunkCount: Int,
    onSuccess: (Uri) -> Unit,
    onFailure: (String) -> Unit
) {
    thread {
        try {
            val parent = db.collection("users")
                .document(uid)
                .collection("clipboard")
                .document(LAPTOP_TO_PHONE)

            val encodedImage = StringBuilder()

            for (index in 0 until chunkCount) {
                val snapshot = Tasks.await(
                    parent
                        .collection("chunks")
                        .document(index.toString())
                        .get()
                )

                if (!snapshot.exists()) {
                    throw Exception("Missing image chunk $index")
                }

                val chunk = snapshot.getString("data")

                if (chunk.isNullOrEmpty()) {
                    throw Exception("Empty image chunk $index")
                }

                encodedImage.append(chunk)
            }

            val imageBytes = Base64.decode(
                encodedImage.toString(),
                Base64.DEFAULT
            )

            if (imageBytes.isEmpty()) {
                throw Exception("Decoded image is empty")
            }

            val imageFile = File(
                context.cacheDir,
                "copypast_$imageId.png"
            )

            FileOutputStream(imageFile).use {
                it.write(imageBytes)
            }

            val imageUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )

            val clipboard = context.getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager

            val clip = ClipData.newUri(
                context.contentResolver,
                "CopyPast Image",
                imageUri
            )

            clipboard.setPrimaryClip(clip)

            Handler(Looper.getMainLooper()).post {
                onSuccess(imageUri)
            }
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                onFailure(e.message ?: "Unknown receive error")
            }
        }
    }
}

// ============================================================
// COPY TEXT TO PHONE
// ============================================================

private fun copyTextToPhone(
    context: Context,
    text: String
) {
    val clipboard = context.getSystemService(
        Context.CLIPBOARD_SERVICE
    ) as ClipboardManager

    clipboard.setPrimaryClip(
        ClipData.newPlainText(
            "CopyPast",
            text
        )
    )
}

// ============================================================
// DELETE TRANSFER
// ============================================================

private fun deleteTransfer(
    db: FirebaseFirestore,
    uid: String
) {
    thread {
        deleteTransferSync(
            db,
            uid,
            LAPTOP_TO_PHONE
        )
    }
}

private fun deleteTransferSync(
    db: FirebaseFirestore,
    uid: String,
    direction: String
) {
    try {
        val parent = db.collection("users")
            .document(uid)
            .collection("clipboard")
            .document(direction)

        val chunks = Tasks.await(
            parent
                .collection("chunks")
                .get()
        )

        for (document in chunks.documents) {
            Tasks.await(
                document.reference.delete()
            )
        }

        Tasks.await(parent.delete())
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// ============================================================
// SHA-256
// ============================================================

private fun sha256(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")

    return digest.digest(bytes).joinToString("") {
        "%02x".format(it)
    }
}

// ============================================================
// GITHUB UPDATE CHECKER
// ============================================================

private fun checkForCopyPastUpdate(
    context: Context,
    onUpdateAvailable: (String, String) -> Unit
) {
    thread {
        var connection: HttpURLConnection? = null

        try {
            connection = (URL(GITHUB_LATEST_API).openConnection()
                    as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty(
                    "Accept",
                    "application/vnd.github+json"
                )
                setRequestProperty(
                    "User-Agent",
                    "CopyPast-Android"
                )
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@thread
            }

            val response = connection.inputStream
                .bufferedReader()
                .use { it.readText() }

            val json = JSONObject(response)

            val latestVersion = json
                .optString("tag_name")
                .removePrefix("v")
                .trim()

            if (latestVersion.isBlank()) {
                return@thread
            }

            val currentVersion = getInstalledVersion(context)
                .removePrefix("v")
                .trim()

            if (!isNewerVersion(latestVersion, currentVersion)) {
                return@thread
            }

            val assets = json.optJSONArray("assets")
                ?: return@thread

            var apkUrl = ""

            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i)
                    ?: continue

                val name = asset.optString("name")

                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    break
                }
            }

            if (apkUrl.isBlank()) {
                apkUrl = GITHUB_RELEASES_URL
            }

            val finalUrl = apkUrl

            Handler(Looper.getMainLooper()).post {
                onUpdateAvailable(
                    latestVersion,
                    finalUrl
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection?.disconnect()
        }
    }
}

// ============================================================
// VERSION HELPERS
// ============================================================

private fun getInstalledVersion(context: Context): String {
    return try {
        @Suppress("DEPRECATION")
        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
            .orEmpty()
    } catch (_: Exception) {
        "1.0.0"
    }
}

private fun isNewerVersion(
    latest: String,
    current: String
): Boolean {
    fun parseVersion(value: String): List<Int> {
        return value
            .split(".")
            .map {
                it.takeWhile(Char::isDigit)
                    .toIntOrNull() ?: 0
            }
    }

    val latestParts = parseVersion(latest)
    val currentParts = parseVersion(current)
    val maxSize = maxOf(
        latestParts.size,
        currentParts.size
    )

    for (i in 0 until maxSize) {
        val latestPart = latestParts.getOrElse(i) { 0 }
        val currentPart = currentParts.getOrElse(i) { 0 }

        if (latestPart > currentPart) return true
        if (latestPart < currentPart) return false
    }

    return false
}

