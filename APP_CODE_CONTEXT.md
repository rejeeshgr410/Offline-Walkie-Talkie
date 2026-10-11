# Project Context & Debugging Request

**Issue Description:**
The app connects between two physical Android devices using Nearby Connections / P2P, but voice/audio is not heard when holding Push-To-Talk (PTT). Frames are encoded and transmitted, but either dropped, decrypted incorrectly, unrouted by AudioManager (e.g. MODE_IN_COMMUNICATION / stream volume issues), or unplayed by AudioTrack/AudioPlayback.

## Project Structure
```text
offline-walkie-talkie/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/example/offlinewalkietalkie/
│           ├── MainActivity.kt
│           ├── Navigation.kt
│           ├── NavigationKeys.kt
│           ├── WalkieTalkieService.kt
│           ├── channel/
│           ├── crypto/
│           ├── data/
│           ├── diagnostics/
│           ├── group/
│           ├── identity/
│           ├── mesh/
│           ├── protocol/
│           ├── service/
│           ├── session/
│           ├── testutil/
│           ├── theme/
│           ├── transport/
│           ├── ui/
│           ├── util/
│           └── voice/
├── build.gradle.kts
└── settings.gradle.kts
```

## Source Code

### File: `build.gradle.kts`
```kotlin
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
}
```

### File: `settings.gradle.kts`
```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Offline Walkie Talkie"
include(":app")
```

### File: `app/build.gradle.kts`
```kotlin
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.offlinewalkietalkie"
    compileSdk = 36
    
    // Signing secrets live in keystore.properties (gitignored), never in this file.
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val hasReleaseKeystore = keystoreProps.getProperty("storeFile") != null

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.offlinewalkietalkie"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }

    testOptions {
      unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  debugImplementation(libs.androidx.compose.ui.tooling)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation("org.json:json:20240303")
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  implementation(libs.play.services.nearby)
  implementation(libs.play.services.base)
  implementation(libs.play.services.coroutines)
  implementation(libs.kopus)
  implementation(libs.tink.android)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.zxing.core)
  implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
```

### File: `app/proguard-rules.pro`
```pro
# ProGuard / R8 rules for TalkLoop V3

# Google Tink Cryptographic Library
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# Kopus / Opus Native JNI (artifact coordinates: eu.buney.kopus:kopus)
-keep class eu.buney.kopus.** { *; }
-dontwarn eu.buney.kopus.**
-keepclasseswithmembernames class * {
    native <methods>;
}

# Google Play Services Nearby Connections
-keep class com.google.android.gms.nearby.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**

# ZXing QR Code Engine
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# AndroidX DataStore Preferences
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# App Protocol and Models
-keep class com.example.offlinewalkietalkie.protocol.** { *; }
-keep class com.example.offlinewalkietalkie.channel.** { *; }
-keep class com.example.offlinewalkietalkie.diagnostics.** { *; }
```

### File: `app/src/main/AndroidManifest.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Permissions -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <!-- Legacy Bluetooth, superseded by the runtime permissions below on API 31+ -->
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />

    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" tools:targetApi="s" />
    <!--
      neverForLocation is required, not optional: without it Nearby Connections treats BLE
      scanning as location-deriving and startDiscovery fails on API 31+ with
      MISSING_PERMISSION_ACCESS_COARSE_LOCATION (verified on Android 17).
    -->
    <uses-permission
        android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation"
        tools:targetApi="s" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" tools:targetApi="s" />

    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
    <uses-permission
        android:name="android.permission.NEARBY_WIFI_DEVICES"
        android:usesPermissionFlags="neverForLocation"
        tools:targetApi="tiramisu" />

    <!-- Nearby Connections requires fine location for BLE discovery up to API 31 only -->
    <uses-permission
        android:name="android.permission.ACCESS_COARSE_LOCATION"
        android:maxSdkVersion="31" />
    <uses-permission
        android:name="android.permission.ACCESS_FINE_LOCATION"
        android:maxSdkVersion="31" />

    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <uses-feature android:name="android.hardware.microphone" android:required="true" />
    <uses-feature android:name="android.hardware.bluetooth" android:required="false" />
    <uses-feature android:name="android.hardware.bluetooth_le" android:required="false" />
    <uses-feature android:name="android.hardware.wifi.direct" android:required="false" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.OfflineWalkieTalkie"
        tools:targetApi="31">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:theme="@style/Theme.OfflineWalkieTalkie">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".WalkieTalkieService"
            android:foregroundServiceType="microphone|connectedDevice"
            android:exported="false" />

        <service
            android:name=".service.WalkieTalkieForegroundService"
            android:foregroundServiceType="microphone|connectedDevice"
            android:exported="false" />
    </application>
</manifest>
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/MainActivity.kt`
```kotlin
package com.example.offlinewalkietalkie

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.offlinewalkietalkie.protocol.FloorState
import com.example.offlinewalkietalkie.service.WalkieTalkieForegroundService
import com.example.offlinewalkietalkie.ui.WalkieTalkieViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: WalkieTalkieViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Connect foreground notification actions
        WalkieTalkieForegroundService.onPttToggleFromNotification = {
            val current = viewModel.floorState.value
            if (current is FloorState.Idle) {
                viewModel.requestPtt()
            } else {
                viewModel.releasePtt()
            }
        }
        WalkieTalkieForegroundService.onLeaveFromNotification = {
            viewModel.leaveSession()
        }

        setContent {
            MainNavigation(viewModel = viewModel)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (viewModel.isInSession.value && event?.repeatCount == 0) {
                viewModel.requestPtt()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (viewModel.isInSession.value) {
                viewModel.releasePtt()
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/Navigation.kt`
```kotlin
package com.example.offlinewalkietalkie

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.offlinewalkietalkie.theme.OfflineWalkieTalkieTheme
import com.example.offlinewalkietalkie.ui.*
import com.example.offlinewalkietalkie.util.PermissionManager

@Composable
fun MainNavigation(viewModel: WalkieTalkieViewModel = viewModel()) {
    val context = LocalContext.current
    val hasPermissions = remember { PermissionManager.hasAllPermissions(context) }

    val backStack = rememberNavBackStack(if (hasPermissions) Home else Permissions)
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()
    val pendingRequest by viewModel.pendingRequest.collectAsState()

    LaunchedEffect(pendingRequest) {
        if (pendingRequest != null && !backStack.contains(Pairing)) {
            backStack.add(Pairing)
        }
    }

    OfflineWalkieTalkieTheme(outdoorMode = isOutdoorMode) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<Permissions> {
                    PermissionScreen(
                        onPermissionsGranted = {
                            if (!backStack.contains(Home)) {
                                backStack.add(Home)
                            }
                            backStack.remove(Permissions)
                        }
                    )
                }
                entry<Home> {
                    HomeScreen(
                        viewModel = viewModel,
                        // While in a group this is the way back to the roster; the raw
                        // endpoint scanner is only meaningful outside one.
                        onNavigateToNearby = {
                            if (viewModel.groupState.value.isInGroup) {
                                if (!backStack.contains(GroupRoom)) backStack.add(GroupRoom)
                            } else {
                                backStack.add(NearbyDevices)
                            }
                        },
                        onNavigateToPairing = { backStack.add(Pairing) },
                        onNavigateToSettings = { backStack.add(Settings) },
                        onNavigateToContacts = { backStack.add(Contacts) },
                        onNavigateToMessages = { backStack.add(Messages) },
                        onNavigateToCreateChannel = { backStack.add(GroupLobby) },
                        onNavigateToJoinChannel = { backStack.add(GroupLobby) }
                    )
                }
                entry<NearbyDevices> {
                    NearbyDevicesScreen(
                        viewModel = viewModel,
                        onNavigateToPairing = { backStack.add(Pairing) },
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<Pairing> {
                    PairingScreen(
                        viewModel = viewModel,
                        onPaired = {
                            // Remove the pairing entries by identity: popping the top blindly
                            // takes the wrong screen off if anything was pushed above it.
                            while (backStack.remove(Pairing)) { /* drain duplicates */ }
                            viewModel.setInSession(true)
                        },
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<Settings> {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<Contacts> {
                    ContactsScreen(
                        viewModel = viewModel,
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<Messages> {
                    MessagesScreen(
                        viewModel = viewModel,
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<GroupLobby> {
                    GroupLobbyScreen(
                        viewModel = viewModel,
                        onGroupReady = {
                            if (!backStack.contains(GroupRoom)) backStack.add(GroupRoom)
                        },
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<GroupRoom> {
                    GroupRoomScreen(
                        viewModel = viewModel,
                        onStartTalking = {
                            // Collapse the connection flow so Back from the dashboard
                            // does not walk the user back through setup.
                            while (backStack.remove(GroupRoom)) { /* drain */ }
                            while (backStack.remove(GroupLobby)) { /* drain */ }
                        },
                        onLeft = {
                            while (backStack.remove(GroupRoom)) { /* drain */ }
                            while (backStack.remove(GroupLobby)) { /* drain */ }
                        },
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<CreateChannel> {
                    CreateChannelScreen(
                        viewModel = viewModel,
                        onNavigateToNearby = {
                            backStack.removeLastOrNull()
                            backStack.add(NearbyDevices)
                        },
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<JoinChannel> {
                    JoinChannelScreen(
                        viewModel = viewModel,
                        onNavigateToNearby = {
                            backStack.removeLastOrNull()
                            backStack.add(NearbyDevices)
                        },
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
            }
        )
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/NavigationKeys.kt`
```kotlin
package com.example.offlinewalkietalkie

import kotlinx.serialization.Serializable

@Serializable data object Permissions : androidx.navigation3.runtime.NavKey
@Serializable data object Home : androidx.navigation3.runtime.NavKey
@Serializable data object NearbyDevices : androidx.navigation3.runtime.NavKey
@Serializable data object Pairing : androidx.navigation3.runtime.NavKey
@Serializable data object Session : androidx.navigation3.runtime.NavKey
@Serializable data object Settings : androidx.navigation3.runtime.NavKey
@Serializable data object Contacts : androidx.navigation3.runtime.NavKey
@Serializable data object Messages : androidx.navigation3.runtime.NavKey
@Serializable data object CreateChannel : androidx.navigation3.runtime.NavKey
@Serializable data object JoinChannel : androidx.navigation3.runtime.NavKey
@Serializable data object GroupLobby : androidx.navigation3.runtime.NavKey
@Serializable data object GroupRoom : androidx.navigation3.runtime.NavKey
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/WalkieTalkieService.kt`
```kotlin
﻿package com.example.offlinewalkietalkie

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class WalkieTalkieService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceRunning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WalkieTalkie::BackgroundWakelock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_START && !isServiceRunning) {
            isServiceRunning = true
            wakeLock?.acquire()
            
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                startForeground(NOTIFICATION_ID, notification, type)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else if (action == ACTION_STOP) {
            isServiceRunning = false
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Walkie Talkie Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Offline Walkie-Talkie Active")
            .setContentText("Listening for incoming voice...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "WalkieTalkieServiceChannel"
        private const val NOTIFICATION_ID = 1001
        
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/channel/ChannelHostController.kt`
```kotlin
package com.example.offlinewalkietalkie.channel

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Section 3, Section 6 & Section 19 Channel Host Controller.
 *
 * Runs on the host device to coordinate:
 * - Channel membership (up to 6 tested participants + relays).
 * - Session-scoped 64-bit member IDs (§19.3).
 * - Key epoch rotation on membership changes (§19.2): new voice key to members; relays excluded.
 * - Host-controlled Floor Control with 60s safety timeout and collision denial.
 * - Auto-rotating QR / short code admission verification (§19.10).
 */
class ChannelHostController(
    val channelId: Long,
    val channelName: String,
    val hostFingerprint: String,
    val hostDisplayName: String,
    val relayPolicy: RelayPolicy = RelayPolicy.OFF,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    companion object {
        const val SAFETY_TIMEOUT_MS = 60_000L // 60s PTT safety timeout (§2 & §64)
        const val MAX_MEMBERS = 6 // Target tested size (§2 & §16)
    }

    private val secureRandom = SecureRandom()
    private val _members = ConcurrentHashMap<Long, ChannelMember>()
    private val _membersFlow = MutableStateFlow<List<ChannelMember>>(emptyList())
    val membersFlow: StateFlow<List<ChannelMember>> = _membersFlow.asStateFlow()

    val hostMemberId: Long = generateUniqueMemberId()

    val qrJoinManager = QrJoinManager(channelId, channelName, hostFingerprint)

    private val _currentKeyEpoch = MutableStateFlow(1)
    val currentKeyEpoch: StateFlow<Int> = _currentKeyEpoch.asStateFlow()

    private var currentVoiceKey: ByteArray = generateFreshVoiceKey()

    private val _currentFloorHolder = MutableStateFlow<ChannelMember?>(null)
    val currentFloorHolder: StateFlow<ChannelMember?> = _currentFloorHolder.asStateFlow()

    private var safetyTimerJob: Job? = null

    // Callbacks for transport layer notifications
    var onKeyEpochRotated: ((newEpoch: Int, newVoiceKey: ByteArray, authorizedMembers: List<ChannelMember>) -> Unit)? = null
    var onFloorStatusChanged: ((holderMemberId: Long, holderName: String) -> Unit)? = null
    var onMemberAdmitted: ((member: ChannelMember) -> Unit)? = null
    var onMemberRemoved: ((member: ChannelMember) -> Unit)? = null

    init {
        // Register host as first member
        val hostMember = ChannelMember(
            memberId = hostMemberId,
            displayName = hostDisplayName,
            role = MemberRole.HOST,
            endpointId = "local",
            isDirect = true,
            isOnline = true
        )
        _members[hostMemberId] = hostMember
        updateMembersList()
    }

    /**
     * Admit a new participant into the channel.
     * Validates one-time secret or 6-char short code (§19.10).
     */
    fun admitMember(
        displayName: String,
        role: MemberRole = MemberRole.MEMBER,
        endpointId: String? = null,
        joinSecretOrCode: String
    ): ChannelMember? {
        val isValidSecret = qrJoinManager.verifyAndConsumeSecret(joinSecretOrCode)
        val isValidCode = !isValidSecret && qrJoinManager.verifyAndConsumeShortCode(joinSecretOrCode)

        if (!isValidSecret && !isValidCode) {
            return null // Invalid or expired join token
        }

        val memberId = generateUniqueMemberId()
        val member = ChannelMember(
            memberId = memberId,
            displayName = displayName,
            role = role,
            endpointId = endpointId,
            isDirect = true,
            isOnline = true
        )

        _members[memberId] = member
        updateMembersList()
        onMemberAdmitted?.invoke(member)

        // Rotate key epoch on material membership change (§19.2)
        rotateKeyEpoch()

        return member
    }

    /**
     * Remove a participant from the channel.
     */
    fun removeMember(memberId: Long) {
        val removed = _members.remove(memberId) ?: return
        
        // Release floor if removed member was talking
        if (_currentFloorHolder.value?.memberId == memberId) {
            releaseFloor()
        }

        updateMembersList()
        onMemberRemoved?.invoke(removed)

        // Rotate key epoch to revoke forward secrecy for departed member (§19.2)
        rotateKeyEpoch()
    }

    /**
     * Floor Request handler.
     * Grants floor if free; denies if already held by another.
     */
    fun requestFloor(memberId: Long): Boolean {
        val member = _members[memberId] ?: return false

        synchronized(this) {
            if (_currentFloorHolder.value == null) {
                // Grant floor
                _currentFloorHolder.value = member
                startSafetyTimer()
                onFloorStatusChanged?.invoke(member.memberId, member.displayName)
                return true
            } else {
                // Deny floor: already busy
                return false
            }
        }
    }

    /**
     * Floor Release handler.
     */
    fun releaseFloor(memberId: Long? = null) {
        synchronized(this) {
            val current = _currentFloorHolder.value ?: return
            if (memberId == null || current.memberId == memberId) {
                cancelSafetyTimer()
                _currentFloorHolder.value = null
                onFloorStatusChanged?.invoke(0L, "")
            }
        }
    }

    /**
     * Handle member disconnection: release floor if they held it.
     */
    fun onEndpointDisconnected(endpointId: String) {
        val member = _members.values.find { it.endpointId == endpointId }
        if (member != null) {
            val updated = member.copy(isOnline = false)
            _members[member.memberId] = updated
            updateMembersList()

            if (_currentFloorHolder.value?.memberId == member.memberId) {
                releaseFloor(member.memberId)
            }
        }
    }

    /**
     * Key epoch rotation per §19.2.
     * Generates a new 32-byte channel voice key and distributes to authorized members.
     * Relay-only nodes NEVER receive channel voice keys (§19.1 & §19.2).
     */
    fun rotateKeyEpoch() {
        val nextEpoch = _currentKeyEpoch.value + 1
        _currentKeyEpoch.value = nextEpoch
        currentVoiceKey = generateFreshVoiceKey()

        // Distribute to authorized members only (Host + Members, NOT Relays)
        val authorizedMembers = _members.values.filter { it.role != MemberRole.RELAY }
        onKeyEpochRotated?.invoke(nextEpoch, currentVoiceKey, authorizedMembers)
    }

    fun getCurrentVoiceKey(): ByteArray = currentVoiceKey.clone()

    private fun startSafetyTimer() {
        cancelSafetyTimer()
        safetyTimerJob = scope.launch {
            delay(SAFETY_TIMEOUT_MS)
            // 60-second safety timeout expired: auto-release floor
            releaseFloor()
        }
    }

    private fun cancelSafetyTimer() {
        safetyTimerJob?.cancel()
        safetyTimerJob = null
    }

    private fun updateMembersList() {
        _membersFlow.value = _members.values.toList().sortedBy { it.joinedEpochMs }
    }

    private fun generateUniqueMemberId(): Long {
        var id: Long
        do {
            id = secureRandom.nextLong()
        } while (id == 0L || _members.containsKey(id))
        return id
    }

    private fun generateFreshVoiceKey(): ByteArray {
        val key = ByteArray(32)
        secureRandom.nextBytes(key)
        return key
    }

    fun reset() {
        cancelSafetyTimer()
        _members.clear()
        _currentFloorHolder.value = null
        _membersFlow.value = emptyList()
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/channel/ChannelModel.kt`
```kotlin
package com.example.offlinewalkietalkie.channel

import java.util.UUID

enum class MemberRole {
    HOST,
    MEMBER,
    RELAY
}

enum class RelayPolicy {
    OFF, // P2P_STAR (Default for direct star topology)
    ON   // P2P_CLUSTER (Enables mesh relaying §19.5)
}

data class ChannelMember(
    val memberId: Long,
    val displayName: String,
    val role: MemberRole,
    val endpointId: String? = null,
    val isDirect: Boolean = true,
    val isOnline: Boolean = true,
    val joinedEpochMs: Long = System.currentTimeMillis()
)

data class ChannelInfo(
    val channelId: Long,
    val name: String,
    val hostMemberId: Long,
    val hostFingerprint: String,
    val relayPolicy: RelayPolicy = RelayPolicy.OFF,
    val keyEpoch: Int = 1
)

enum class TextDeliveryStatus {
    SENDING,
    DELIVERED,
    FAILED
}

data class TextMessage(
    val messageId: String = UUID.randomUUID().toString(),
    val sequence: Int,
    val senderMemberId: Long,
    val senderName: String,
    val content: String,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val status: TextDeliveryStatus = TextDeliveryStatus.SENDING
)

data class ReplayTransmission(
    val id: String = UUID.randomUUID().toString(),
    val senderMemberId: Long,
    val senderName: String,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val frames: List<ByteArray>
)
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/channel/QrJoinManager.kt`
```kotlin
package com.example.offlinewalkietalkie.channel

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.security.SecureRandom

data class QrJoinData(
    val channelId: Long,
    val channelName: String,
    val hostFingerprint: String,
    val oneTimeSecret: String,
    val shortCode: String,
    val expiresEpochMs: Long
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("v", 3)
            put("cid", channelId)
            put("name", channelName)
            put("fp", hostFingerprint)
            put("sec", oneTimeSecret)
            put("code", shortCode)
            put("exp", expiresEpochMs)
        }.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): QrJoinData? {
            return try {
                val json = JSONObject(jsonStr)
                if (json.optInt("v", 0) != 3) return null
                QrJoinData(
                    channelId = json.getLong("cid"),
                    channelName = json.getString("name"),
                    hostFingerprint = json.getString("fp"),
                    oneTimeSecret = json.getString("sec"),
                    shortCode = json.getString("code"),
                    expiresEpochMs = json.getLong("exp")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Section 19.10 Auto-Rotating QR Join Manager.
 *
 * Rules:
 * - Each QR secret is one-time and expires after 10 minutes.
 * - Auto-rotates after every successful join.
 * - Generates QR Bitmaps via ZXing and easy-to-read 6-character short codes.
 */
class QrJoinManager(
    val channelId: Long,
    val channelName: String,
    val hostFingerprint: String
) {
    companion object {
        const val EXPIRY_DURATION_MS = 10 * 60 * 1000L // 10 minutes
        private val SHORT_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Unambiguous chars
    }

    private val secureRandom = SecureRandom()

    private val _currentJoinData = MutableStateFlow(createFreshJoinData())
    val currentJoinData: StateFlow<QrJoinData> = _currentJoinData.asStateFlow()

    private fun createFreshJoinData(): QrJoinData {
        val secretBytes = ByteArray(16)
        secureRandom.nextBytes(secretBytes)
        val oneTimeSecret = secretBytes.joinToString("") { "%02x".format(it) }

        val codeBuilder = StringBuilder(6)
        for (i in 0 until 6) {
            val idx = secureRandom.nextInt(SHORT_CODE_CHARS.length)
            codeBuilder.append(SHORT_CODE_CHARS[idx])
        }
        val shortCode = codeBuilder.toString()
        val expiresEpochMs = System.currentTimeMillis() + EXPIRY_DURATION_MS

        return QrJoinData(
            channelId = channelId,
            channelName = channelName,
            hostFingerprint = hostFingerprint,
            oneTimeSecret = oneTimeSecret,
            shortCode = shortCode,
            expiresEpochMs = expiresEpochMs
        )
    }

    /**
     * Auto-rotates QR code and 6-char short code.
     */
    fun rotate(): QrJoinData {
        val next = createFreshJoinData()
        _currentJoinData.value = next
        return next
    }

    /**
     * Check if current join code has expired; if so, rotates automatically.
     */
    fun checkExpiry(): Boolean {
        if (System.currentTimeMillis() >= _currentJoinData.value.expiresEpochMs) {
            rotate()
            return true
        }
        return false
    }

    fun remainingSeconds(): Int {
        val remainingMs = _currentJoinData.value.expiresEpochMs - System.currentTimeMillis()
        return if (remainingMs > 0) (remainingMs / 1000).toInt() else 0
    }

    /**
     * Verify candidate one-time secret from scanned QR.
     * If valid, consumes it and immediately auto-rotates.
     */
    fun verifyAndConsumeSecret(candidateSecret: String): Boolean {
        checkExpiry()
        val current = _currentJoinData.value
        if (System.currentTimeMillis() < current.expiresEpochMs &&
            current.oneTimeSecret.equals(candidateSecret, ignoreCase = true)
        ) {
            rotate() // One-time use: auto-rotate immediately (§19.10)
            return true
        }
        return false
    }

    /**
     * Verify candidate 6-character short code.
     * If valid, consumes it and immediately auto-rotates.
     */
    fun verifyAndConsumeShortCode(candidateCode: String): Boolean {
        checkExpiry()
        val current = _currentJoinData.value
        val normalizedCandidate = candidateCode.trim().uppercase()
        if (System.currentTimeMillis() < current.expiresEpochMs &&
            current.shortCode.equals(normalizedCandidate, ignoreCase = true)
        ) {
            rotate() // One-time use: auto-rotate immediately (§19.10)
            return true
        }
        return false
    }

    /**
     * Generates a 512x512 QR code Bitmap using ZXing.
     */
    fun generateQrBitmap(size: Int = 512): Bitmap {
        val content = _currentJoinData.value.toJson()
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/channel/ReliableMessageManager.kt`
```kotlin
package com.example.offlinewalkietalkie.channel

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Section 19.8 Reliable Control and Text Message Delivery.
 *
 * Implements:
 * - Acknowledgements (ACK)
 * - Bounded retransmission: 3 attempts at 300 ms, 600 ms, 1200 ms backoff.
 * - Deduplication by PacketIdentity (memberId + sequence).
 */
class ReliableMessageManager(
    private val sendRawPacket: suspend (endpointId: String, packet: ByteArray) -> Boolean,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        val RETRY_BACKOFF_MS = listOf(300L, 600L, 1200L)
        const val MAX_RETRIES = 3
        const val DEDUPE_CACHE_LIMIT = 500
    }

    private data class PendingDelivery(
        val memberId: Long,
        val sequence: Int,
        val endpointId: String,
        val packetBytes: ByteArray,
        var attempt: Int = 0,
        var retryJob: Job? = null,
        val onDelivered: () -> Unit,
        val onFailed: (reason: String) -> Unit
    )

    // Key: "memberId:sequence" -> PendingDelivery
    private val pendingAcks = ConcurrentHashMap<String, PendingDelivery>()

    // Key: "memberId:sequence" -> Timestamp of receipt
    private val dedupeCache = ConcurrentHashMap<String, Long>()

    /**
     * Send a reliable packet with automated retry backoff.
     */
    fun sendReliable(
        memberId: Long,
        sequence: Int,
        endpointId: String,
        packetBytes: ByteArray,
        onDelivered: () -> Unit = {},
        onFailed: (reason: String) -> Unit = {}
    ) {
        val key = "$memberId:$sequence"
        val pending = PendingDelivery(
            memberId = memberId,
            sequence = sequence,
            endpointId = endpointId,
            packetBytes = packetBytes,
            attempt = 0,
            onDelivered = onDelivered,
            onFailed = onFailed
        )
        pendingAcks[key] = pending

        scheduleAttempt(key, pending)
    }

    private fun scheduleAttempt(key: String, pending: PendingDelivery) {
        pending.retryJob?.cancel()
        pending.retryJob = scope.launch {
            // Initial or retransmission send
            sendRawPacket(pending.endpointId, pending.packetBytes)

            if (pending.attempt < MAX_RETRIES) {
                val delayMs = RETRY_BACKOFF_MS.getOrElse(pending.attempt) { 1200L }
                pending.attempt++
                delay(delayMs)
                // Check if still waiting for ACK
                if (pendingAcks.containsKey(key)) {
                    scheduleAttempt(key, pending)
                }
            } else {
                // Max retries exceeded without ACK
                pendingAcks.remove(key)
                pending.onFailed("Delivery timed out after 3 attempts")
            }
        }
    }

    /**
     * Called when an incoming ACK frame is received.
     */
    fun onAckReceived(memberId: Long, sequence: Int) {
        val key = "$memberId:$sequence"
        val pending = pendingAcks.remove(key)
        if (pending != null) {
            pending.retryJob?.cancel()
            pending.onDelivered()
        }
    }

    /**
     * Verify incoming reliable frame.
     * @return true if packet is fresh and should be processed; false if it's a duplicate.
     */
    fun processIncomingReliable(
        senderMemberId: Long,
        sequence: Int,
        sendAckResponse: suspend () -> Unit
    ): Boolean {
        val key = "$senderMemberId:$sequence"
        val now = System.currentTimeMillis()

        // Always reply with ACK so the sender knows we received it
        scope.launch {
            try {
                sendAckResponse()
            } catch (e: Exception) {}
        }

        // Check if already in dedupe cache
        if (dedupeCache.containsKey(key)) {
            return false // Duplicate: drop processing
        }

        // Clean cache if too large
        if (dedupeCache.size > DEDUPE_CACHE_LIMIT) {
            val oldest = dedupeCache.entries.sortedBy { it.value }.take(100)
            oldest.forEach { dedupeCache.remove(it.key) }
        }

        dedupeCache[key] = now
        return true // Fresh packet
    }

    fun reset() {
        pendingAcks.values.forEach { it.retryJob?.cancel() }
        pendingAcks.clear()
        dedupeCache.clear()
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/channel/ReplayBuffer.kt`
```kotlin
package com.example.offlinewalkietalkie.channel

import com.example.offlinewalkietalkie.voice.AudioPlayback
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections

/**
 * Section 14 In-Memory Audio Replay Buffer.
 *
 * Rules:
 * - Holds up to 3 most recent received voice transmissions.
 * - Defaults off, opt-in per session.
 * - In-memory ONLY: cleared at session end, process recreation, or identity warning.
 * - Zero disk persistence.
 */
class ReplayBuffer(
    private val playback: AudioPlayback? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        const val MAX_TRANSMISSIONS = 3
    }

    var isEnabled: Boolean = false
        set(value) {
            field = value
            if (!value) {
                clear()
            }
        }

    private val transmissions = Collections.synchronizedList(mutableListOf<ReplayTransmission>())
    
    private val _transmissionList = MutableStateFlow<List<ReplayTransmission>>(emptyList())
    val transmissionList: StateFlow<List<ReplayTransmission>> = _transmissionList.asStateFlow()

    private var currentRecordingFrames = mutableListOf<ByteArray>()
    private var currentSpeakerId: Long = 0L
    private var currentSpeakerName: String = ""

    private var replayJob: Job? = null

    /**
     * Called when a peer begins talking (floor granted).
     */
    fun onTransmissionStarted(speakerId: Long, speakerName: String) {
        if (!isEnabled) return
        currentRecordingFrames.clear()
        currentSpeakerId = speakerId
        currentSpeakerName = speakerName
    }

    /**
     * Accumulate PCM frame for the ongoing transmission.
     */
    fun onFrameReceived(pcmBytes: ByteArray) {
        if (!isEnabled) return
        currentRecordingFrames.add(pcmBytes.clone())
    }

    /**
     * Called when the peer stops talking (floor released).
     */
    fun onTransmissionCompleted() {
        if (!isEnabled || currentRecordingFrames.isEmpty()) return

        val transmission = ReplayTransmission(
            senderMemberId = currentSpeakerId,
            senderName = currentSpeakerName,
            frames = currentRecordingFrames.toList()
        )
        currentRecordingFrames.clear()

        synchronized(transmissions) {
            transmissions.add(transmission)
            while (transmissions.size > MAX_TRANSMISSIONS) {
                transmissions.removeAt(0) // Keep only last 3 transmissions (§14)
            }
            _transmissionList.value = transmissions.toList()
        }
    }

    /**
     * Replay the specified transmission through AudioPlayback.
     */
    fun playTransmission(transmissionId: String, onFinished: () -> Unit = {}) {
        val target = synchronized(transmissions) {
            transmissions.firstOrNull { it.id == transmissionId }
        } ?: return

        replayJob?.cancel()
        replayJob = scope.launch {
            playback?.start()
            for (frame in target.frames) {
                playback?.playFrame(frame)
                delay(20) // 20ms frame cadence
            }
            onFinished()
        }
    }

    /**
     * Replay the most recent transmission.
     */
    fun playLatest(onFinished: () -> Unit = {}) {
        val latest = synchronized(transmissions) { transmissions.lastOrNull() }
        if (latest != null) {
            playTransmission(latest.id, onFinished)
        }
    }

    fun stopReplay() {
        replayJob?.cancel()
        replayJob = null
    }

    /**
     * Strictly clear in-memory buffer.
     */
    fun clear() {
        stopReplay()
        currentRecordingFrames.clear()
        synchronized(transmissions) {
            transmissions.clear()
            _transmissionList.value = emptyList()
        }
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/crypto/CryptoManager.kt`
```kotlin
package com.example.offlinewalkietalkie.crypto

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoManager {
    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val NONCE_SIZE = 12
    }

    private val keyPair: KeyPair
    private val sessionKeys = mutableMapOf<String, SecretKeySpec>()

    init {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(256)
        keyPair = kpg.generateKeyPair()
    }

    fun getPublicKeyBytes(): ByteArray {
        return keyPair.public.encoded
    }

    fun deriveSessionKey(peerId: String, peerPublicKeyBytes: ByteArray) {
        val keyFactory = KeyFactory.getInstance("EC")
        val peerPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerPublicKeyBytes))
        
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(keyPair.private)
        keyAgreement.doPhase(peerPublicKey, true)
        
        val sharedSecret = keyAgreement.generateSecret()
        
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(sharedSecret)
        
        // AES keys are typically 16, 24, or 32 bytes. SHA-256 gives 32 bytes, perfect for AES-256.
        sessionKeys[peerId] = SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(peerId: String, plaintext: ByteArray, nonce: ByteArray, aad: ByteArray? = null): Pair<ByteArray, ByteArray>? {
        val secretKey = sessionKeys[peerId] ?: return null
        
        try {
            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
            
            if (aad != null) {
                cipher.updateAAD(aad)
            }
            
            val cipherTextWithTag = cipher.doFinal(plaintext)
            val tagSize = 16
            if (cipherTextWithTag.size < tagSize) return null
            
            val cipherText = cipherTextWithTag.copyOfRange(0, cipherTextWithTag.size - tagSize)
            val authTag = cipherTextWithTag.copyOfRange(cipherTextWithTag.size - tagSize, cipherTextWithTag.size)
            
            return Pair(cipherText, authTag)
        } catch (e: Exception) {
            return null
        }
    }

    fun decrypt(peerId: String, ciphertext: ByteArray, authTag: ByteArray, nonce: ByteArray, aad: ByteArray? = null): ByteArray? {
        val secretKey = sessionKeys[peerId] ?: return null
        
        try {
            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, nonce)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            
            if (aad != null) {
                cipher.updateAAD(aad)
            }
            
            val combined = ciphertext + authTag
            return cipher.doFinal(combined)
        } catch (e: Exception) {
            return null
        }
    }

    fun hasSessionKey(peerId: String): Boolean = sessionKeys.containsKey(peerId)
    
    fun removeSession(peerId: String) {
        sessionKeys.remove(peerId)
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/crypto/TinkCryptoEngine.kt`
```kotlin
package com.example.offlinewalkietalkie.crypto

import com.google.crypto.tink.subtle.ChaCha20Poly1305
import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.subtle.X25519
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Locale
import kotlin.math.abs

/**
 * TalkLoop V3 Cryptographic Engine based on Google Tink 1.16.0.
 * Implements X25519 ECDH, HKDF-SHA256, ChaCha20-Poly1305 AEAD, and SAS derivation.
 */
class TinkCryptoEngine {

    companion object {
        private const val HOP_KEY_INFO = "talkloop-v3-hop"
        private const val CHANNEL_KEY_INFO = "talkloop-v3-channel"
        private const val SAS_KEY_INFO = "talkloop-v3-sas"
        private const val KEY_SIZE_BYTES = 32
        private const val NONCE_SIZE_BYTES = 12
    }

    private val secureRandom = SecureRandom()

    // Generate fresh X25519 private key (32 bytes)
    fun generatePrivateKey(): ByteArray {
        return X25519.generatePrivateKey()
    }

    // Compute public key from private key (32 bytes)
    fun computePublicKey(privateKey: ByteArray): ByteArray {
        return X25519.publicFromPrivate(privateKey)
    }

    // Compute shared secret using ECDH X25519
    fun computeSharedSecret(myPrivateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        return X25519.computeSharedSecret(myPrivateKey, peerPublicKey)
    }

    // HKDF-SHA256 key derivation for hop-by-hop link encryption
    fun deriveHopKey(sharedSecret: ByteArray, salt: ByteArray? = null): ByteArray {
        val actualSalt = salt ?: ByteArray(32)
        return Hkdf.computeHkdf(
            "HmacSha256",
            sharedSecret,
            actualSalt,
            HOP_KEY_INFO.toByteArray(Charsets.UTF_8),
            KEY_SIZE_BYTES
        )
    }

    // HKDF-SHA256 key derivation for end-to-end channel encryption
    fun deriveChannelKey(sharedSecret: ByteArray, salt: ByteArray? = null): ByteArray {
        val actualSalt = salt ?: ByteArray(32)
        return Hkdf.computeHkdf(
            "HmacSha256",
            sharedSecret,
            actualSalt,
            CHANNEL_KEY_INFO.toByteArray(Charsets.UTF_8),
            KEY_SIZE_BYTES
        )
    }

    // Derive a 6-digit Short Authentication String (SAS) from transcript and keys
    fun deriveSasCode(transcriptHash: ByteArray): String {
        val sasKey = Hkdf.computeHkdf(
            "HmacSha256",
            transcriptHash,
            ByteArray(32),
            SAS_KEY_INFO.toByteArray(Charsets.UTF_8),
            4
        )
        val intVal = ByteBuffer.wrap(sasKey).int
        val positive = abs(intVal % 1_000_000)
        return String.format(Locale.US, "%06d", positive)
    }

    // Generate a secure 12-byte nonce
    fun generateNonce(): ByteArray {
        val nonce = ByteArray(NONCE_SIZE_BYTES)
        secureRandom.nextBytes(nonce)
        return nonce
    }

    // Encrypt plaintext using Tink ChaCha20Poly1305 with Associated Data (AAD)
    fun encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray {
        val cipher = ChaCha20Poly1305(key)
        // ChaCha20Poly1305 in Tink expects nonce prepended or handled in encrypt
        // Tink's ChaCha20Poly1305.encrypt(plaintext, associatedData) generates a 12-byte nonce internally and prepends it:
        // ciphertext = 12-byte nonce + encrypted + 16-byte tag
        return cipher.encrypt(plaintext, associatedData)
    }

    // Decrypt ciphertext using Tink ChaCha20Poly1305 with Associated Data (AAD)
    fun decrypt(key: ByteArray, ciphertextWithNonceAndTag: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray {
        val cipher = ChaCha20Poly1305(key)
        return cipher.decrypt(ciphertextWithNonceAndTag, associatedData)
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/data/DataRepository.kt`
```kotlin
package com.example.offlinewalkietalkie.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface DataRepository {
  val data: Flow<List<String>>
}

class DefaultDataRepository : DataRepository {
  override val data: Flow<List<String>> = flow { emit(listOf("Android")) }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/data/TrustedContact.kt`
```kotlin
package com.example.offlinewalkietalkie.data

import org.json.JSONObject

data class TrustedContact(
    val contactId: String,
    val displayName: String,
    val publicKeyHex: String,
    val sasCode: String,
    val isVerified: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("contactId", contactId)
            put("displayName", displayName)
            put("publicKeyHex", publicKeyHex)
            put("sasCode", sasCode)
            put("isVerified", isVerified)
            put("lastSeen", lastSeen)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): TrustedContact {
            return TrustedContact(
                contactId = json.getString("contactId"),
                displayName = json.getString("displayName"),
                publicKeyHex = json.getString("publicKeyHex"),
                sasCode = json.getString("sasCode"),
                isVerified = json.optBoolean("isVerified", false),
                lastSeen = json.optLong("lastSeen", System.currentTimeMillis())
            )
        }
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/data/TrustedContactsRepository.kt`
```kotlin
package com.example.offlinewalkietalkie.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "talkloop_contacts")

class TrustedContactsRepository(private val context: Context) {

    companion object {
        private val CONTACTS_KEY = stringPreferencesKey("trusted_contacts_json")
    }

    val contacts: Flow<List<TrustedContact>> = context.dataStore.data.map { prefs ->
        val raw = prefs[CONTACTS_KEY] ?: "[]"
        try {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<TrustedContact>()
            for (i in 0 until jsonArray.length()) {
                list.add(TrustedContact.fromJson(jsonArray.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveContact(contact: TrustedContact) {
        context.dataStore.edit { prefs ->
            val raw = prefs[CONTACTS_KEY] ?: "[]"
            val currentList = try {
                val jsonArray = JSONArray(raw)
                val list = mutableListOf<TrustedContact>()
                for (i in 0 until jsonArray.length()) {
                    list.add(TrustedContact.fromJson(jsonArray.getJSONObject(i)))
                }
                list
            } catch (e: Exception) {
                mutableListOf()
            }

            val existingIndex = currentList.indexOfFirst { it.contactId == contact.contactId || it.publicKeyHex == contact.publicKeyHex }
            if (existingIndex >= 0) {
                currentList[existingIndex] = contact
            } else {
                currentList.add(contact)
            }

            val newArray = JSONArray()
            currentList.forEach { newArray.put(it.toJson()) }
            prefs[CONTACTS_KEY] = newArray.toString()
        }
    }

    suspend fun verifyContact(contactId: String) {
        context.dataStore.edit { prefs ->
            val raw = prefs[CONTACTS_KEY] ?: "[]"
            val currentList = try {
                val jsonArray = JSONArray(raw)
                val list = mutableListOf<TrustedContact>()
                for (i in 0 until jsonArray.length()) {
                    list.add(TrustedContact.fromJson(jsonArray.getJSONObject(i)))
                }
                list
            } catch (e: Exception) {
                mutableListOf()
            }

            val index = currentList.indexOfFirst { it.contactId == contactId }
            if (index >= 0) {
                currentList[index] = currentList[index].copy(isVerified = true)
                val newArray = JSONArray()
                currentList.forEach { newArray.put(it.toJson()) }
                prefs[CONTACTS_KEY] = newArray.toString()
            }
        }
    }

    suspend fun isContactTrusted(publicKeyHex: String): Boolean {
        val list = contacts.first()
        return list.any { it.publicKeyHex == publicKeyHex && it.isVerified }
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/diagnostics/LinkMetrics.kt`
```kotlin
package com.example.offlinewalkietalkie.diagnostics

/**
 * Section 2, Section 8 & Section 19.14 Live Link Telemetry.
 *
 * Rules:
 * - Shows measured connection quality (RTT, packet loss, jitter, quality bars).
 * - NEVER claims or infers distance (e.g. no "2 km" or "15 meters" per Section 0).
 */
data class LinkTelemetry(
    val rttMs: Long = 0L,
    val packetLossPercent: Double = 0.0,
    val jitterMs: Double = 0.0,
    val qualityBars: Int = 5, // 1 to 5 signal bars
    val transportName: String = "Nearby Connections",
    val meshHopCount: Int = 1,
    val isReconnecting: Boolean = false
) {
    companion object {
        /**
         * Calculates 1-5 quality bars based on measured RTT, loss, and jitter.
         */
        fun calculateQualityBars(rttMs: Long, lossPercent: Double, jitterMs: Double): Int {
            if (lossPercent > 30.0 || rttMs > 400L) return 1
            if (lossPercent > 15.0 || rttMs > 250L || jitterMs > 60.0) return 2
            if (lossPercent > 5.0 || rttMs > 150L || jitterMs > 40.0) return 3
            if (rttMs > 80L || jitterMs > 20.0) return 4
            return 5
        }
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/diagnostics/LinkTestController.kt`
```kotlin
package com.example.offlinewalkietalkie.diagnostics

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Section 8 & Section 15 Test 9.1 Link Test Controller.
 *
 * Implements:
 * - Real-time RTT measurement via PING / PONG frames.
 * - Rolling packet loss and jitter calculation.
 * - 1 to 5 quality signal bars.
 * - Zero distance promises (§0).
 */
class LinkTestController(
    private val sendPing: suspend (peerId: String, timestampMs: Long) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val _telemetry = MutableStateFlow(LinkTelemetry())
    val telemetry: StateFlow<LinkTelemetry> = _telemetry.asStateFlow()

    private val pendingPings = ConcurrentHashMap<Long, Long>() // timestamp -> sentEpochMs

    private var totalPingsSent = 0
    private var totalPongsReceived = 0
    private var rollingRttMs = 0L
    private var currentJitterMs = 0.0

    /**
     * Start a link test to measure RTT.
     */
    fun performLinkTest(peerId: String) {
        val now = System.currentTimeMillis()
        pendingPings[now] = now
        totalPingsSent++

        scope.launch {
            try {
                sendPing(peerId, now)
            } catch (e: Exception) {}
        }
    }

    /**
     * Handle incoming PONG response with original ping timestamp.
     */
    fun onPongReceived(originalTimestampMs: Long) {
        val sentEpoch = pendingPings.remove(originalTimestampMs) ?: return
        val now = System.currentTimeMillis()
        val rtt = (now - sentEpoch).coerceAtLeast(1L)

        totalPongsReceived++

        // Update rolling RTT
        rollingRttMs = if (rollingRttMs == 0L) rtt else (rollingRttMs * 3 + rtt) / 4

        updateTelemetry()
    }

    fun updateJitter(jitterMs: Double) {
        currentJitterMs = jitterMs
        updateTelemetry()
    }

    fun updateTransportInfo(transportName: String, meshHops: Int) {
        _telemetry.value = _telemetry.value.copy(
            transportName = transportName,
            meshHopCount = meshHops
        )
    }

    private fun updateTelemetry() {
        val lossPercent = if (totalPingsSent > 0) {
            val lost = (totalPingsSent - totalPongsReceived).coerceAtLeast(0)
            (lost.toDouble() / totalPingsSent.toDouble()) * 100.0
        } else {
            0.0
        }

        val bars = LinkTelemetry.calculateQualityBars(
            rttMs = rollingRttMs,
            lossPercent = lossPercent,
            jitterMs = currentJitterMs
        )

        _telemetry.value = _telemetry.value.copy(
            rttMs = rollingRttMs,
            packetLossPercent = lossPercent,
            jitterMs = currentJitterMs,
            qualityBars = bars
        )
    }

    fun reset() {
        pendingPings.clear()
        totalPingsSent = 0
        totalPongsReceived = 0
        rollingRttMs = 0L
        currentJitterMs = 0.0
        _telemetry.value = LinkTelemetry()
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/diagnostics/RedactedDiagnosticsExporter.kt`
```kotlin
package com.example.offlinewalkietalkie.diagnostics

import android.content.Context
import android.os.Build
import com.example.offlinewalkietalkie.util.CapabilityManager
import org.json.JSONObject

/**
 * Section 14 & Section 15 Test 9.2 Redacted Diagnostics Exporter.
 *
 * Strict Privacy Rules:
 * - NEVER exports audio, packet payloads, private/public keys, SAS codes, session IDs, or exact peer identifiers.
 * - Exports ONLY: app version, OS API level, capability flags, active transport name, aggregate counters, and rounded timing buckets.
 */
object RedactedDiagnosticsExporter {

    fun generateReport(
        context: Context,
        activeTransportName: String,
        totalPacketsSent: Long,
        totalPacketsReceived: Long,
        totalPacketsLost: Long,
        averageJitterMs: Double,
        measuredRttMs: Long
    ): String {
        return generateReport(
            activeTransportName = activeTransportName,
            totalPacketsSent = totalPacketsSent,
            totalPacketsReceived = totalPacketsReceived,
            totalPacketsLost = totalPacketsLost,
            averageJitterMs = averageJitterMs,
            measuredRttMs = measuredRttMs,
            gmsAvailable = CapabilityManager.isGooglePlayServicesAvailable(context),
            bleSupported = CapabilityManager.isBleSupported(context),
            wifiAwareSupported = CapabilityManager.isWifiAwareSupported(context),
            wifiDirectSupported = CapabilityManager.isWifiDirectSupported(context),
            apiLevel = Build.VERSION.SDK_INT
        )
    }

    fun generateReport(
        activeTransportName: String,
        totalPacketsSent: Long,
        totalPacketsReceived: Long,
        totalPacketsLost: Long,
        averageJitterMs: Double,
        measuredRttMs: Long,
        gmsAvailable: Boolean = false,
        bleSupported: Boolean = true,
        wifiAwareSupported: Boolean = false,
        wifiDirectSupported: Boolean = true,
        apiLevel: Int = 34
    ): String {
        val rttBucket = when {
            measuredRttMs < 50L -> "< 50 ms (Excellent)"
            measuredRttMs < 100L -> "50–100 ms (Good)"
            measuredRttMs < 200L -> "100–200 ms (Fair)"
            else -> "> 200 ms (Degraded)"
        }

        val json = JSONObject().apply {
            put("app_name", "TalkLoop")
            put("app_version", "1.0")
            put("android_api_level", apiLevel)
            put("active_transport", activeTransportName)

            // Capability flags (boolean only)
            val caps = JSONObject().apply {
                put("gms_available", gmsAvailable)
                put("ble_supported", bleSupported)
                put("wifi_aware_supported", wifiAwareSupported)
                put("wifi_direct_supported", wifiDirectSupported)
            }
            put("capabilities", caps)

            // Aggregate telemetry metrics
            val metrics = JSONObject().apply {
                put("packets_sent", totalPacketsSent)
                put("packets_received", totalPacketsReceived)
                put("packets_lost", totalPacketsLost)
                put("average_jitter_ms", "%.1f".format(averageJitterMs))
                put("latency_bucket", rttBucket)
            }
            put("telemetry", metrics)

            // Confirmation of zero sensitive data
            put("privacy_status", "REDACTED_SAFE - No keys, audio, or identities included")
        }

        return json.toString(2)
    }

    /**
     * Verification test helper: ensures report contains no keys, transcripts, or sensitive identifiers.
     */
    fun isSafeAndRedacted(reportJson: String): Boolean {
        // Forbidden keywords / patterns
        val forbidden = listOf(
            "key", "private", "public", "secret", "token",
            "audio", "pcm", "opus", "transcript", "sas",
            "fingerprint", "mac", "ip_address", "peer_id", "session_id"
        )

        val lower = reportJson.lowercase()
        for (term in forbidden) {
            // Check if any key contains forbidden substrings (allow privacy_status and capabilities keys)
            if (lower.contains("\"$term\"") || lower.contains("\"$term:")) {
                return false
            }
        }

        // Check for 64-char hex strings (potential 32-byte keys)
        val hexRegex = Regex("[0-9a-fA-F]{64}")
        if (hexRegex.containsMatchIn(reportJson)) {
            return false
        }

        return true
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/group/GroupAdvert.kt`
```kotlin
package com.example.offlinewalkietalkie.group

/**
 * What a device broadcasts about itself during discovery, packed into the Nearby
 * endpoint name so a peer knows which group an endpoint belongs to *before* connecting.
 *
 * Wire form is fixed-width with no separators:
 *
 *     T <groupId:4> <memberId:4> <role:1> <shortName:0..8>
 *
 * Size matters more than readability here. Nearby carries the endpoint name inside the
 * BLE advertisement for P2P_CLUSTER, and once the name pushes the packet past the legacy
 * 31-byte limit the endpoint stops being discoverable by devices without BLE 5 extended
 * advertising. Keeping the whole thing at 18 bytes leaves that headroom.
 *
 * Only a short form of the group name travels here, purely so the join list has something
 * to show; the full name arrives in [GroupMessage.Welcome] once a link is up.
 */
data class GroupAdvert(
    val groupId: String,
    val memberId: String,
    val role: GroupRole,
    val displayName: String
) {
    fun encode(): String {
        val roleChar = if (role == GroupRole.COORDINATOR) "C" else "M"
        val shortName = displayName.filter { it.code in 32..126 }.take(MAX_NAME)
        return PREFIX + groupId.take(GROUP_ID_LENGTH).padEnd(GROUP_ID_LENGTH, PAD) +
            memberId.take(MEMBER_ID_LENGTH).padEnd(MEMBER_ID_LENGTH, PAD) +
            roleChar + shortName
    }

    companion object {
        const val PREFIX = "T"
        const val GROUP_ID_LENGTH = 4
        const val MEMBER_ID_LENGTH = 4
        const val MAX_NAME = 8
        private const val PAD = '0'

        private const val ROLE_INDEX = PREFIX.length + GROUP_ID_LENGTH + MEMBER_ID_LENGTH
        private const val MIN_LENGTH = ROLE_INDEX + 1

        /** Longest endpoint name we will ever publish. */
        const val MAX_ENCODED_LENGTH = MIN_LENGTH + MAX_NAME

        /**
         * Returns null for anything that is not one of our adverts — a peer running an
         * older build, or an unrelated app sharing the service id.
         */
        fun decode(endpointName: String?): GroupAdvert? {
            if (endpointName == null) return null
            if (endpointName.length < MIN_LENGTH) return null
            if (!endpointName.startsWith(PREFIX)) return null

            val groupId = endpointName.substring(PREFIX.length, PREFIX.length + GROUP_ID_LENGTH)
            val memberId = endpointName.substring(
                PREFIX.length + GROUP_ID_LENGTH,
                ROLE_INDEX
            )
            val role = when (endpointName[ROLE_INDEX]) {
                'C' -> GroupRole.COORDINATOR
                'M' -> GroupRole.MEMBER
                else -> return null
            }
            if (groupId.isBlank() || memberId.isBlank()) return null

            val name = endpointName.substring(MIN_LENGTH).ifBlank { memberId }
            return GroupAdvert(groupId, memberId, role, name)
        }
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/group/GroupModels.kt`
```kotlin
package com.example.offlinewalkietalkie.group

/** Where a member sits in the group. */
enum class GroupRole {
    /** Owns the roster and admits newcomers. Exactly one per group. */
    COORDINATOR,
    MEMBER
}

/**
 * Lifecycle of one member as seen from this device. Drives both the connection state
 * machine and what the roster UI shows.
 */
enum class MemberState {
    /** Advertising our group id but no link attempted yet. */
    DISCOVERED,

    /** A link is being opened. */
    CONNECTING,

    /**
     * A first-time link that a person has to confirm by comparing the code shown on both
     * phones. Nothing about the group is trusted until they do.
     */
    PENDING_APPROVAL,

    /** Link is up; waiting for the peer to prove it holds the group secret. */
    VERIFYING,

    /** Link is up and membership is proven. Voice can flow. */
    CONNECTED,

    /** Link dropped but the member is still on the roster; retry is scheduled. */
    RECONNECTING,

    /** Attempts exhausted, verification failed, or the peer refused. Carries a reason. */
    FAILED,

    /** Left deliberately. Kept briefly so the UI can show it rather than blink out. */
    LEFT
}

/** True once a link is usable for voice. */
val MemberState.isLive: Boolean
    get() = this == MemberState.CONNECTED

/** True while something is in progress, so the UI can show a spinner. */
val MemberState.isTransitional: Boolean
    get() = this == MemberState.CONNECTING ||
        this == MemberState.PENDING_APPROVAL ||
        this == MemberState.VERIFYING ||
        this == MemberState.RECONNECTING

data class GroupMember(
    val memberId: String,
    val displayName: String,
    /** Transport-level address. Null when the member is only known via the roster. */
    val endpointId: String? = null,
    val role: GroupRole = GroupRole.MEMBER,
    val state: MemberState = MemberState.DISCOVERED,
    /** False when we reach them through a relay rather than our own radio link. */
    val isDirect: Boolean = true,
    /** No direct link, but a live neighbour of ours can reach them, so voice still gets there. */
    val reachableViaRelay: Boolean = false,
    /** Listed in the coordinator's roster, which is what lets later links skip the human check. */
    val onRoster: Boolean = false,
    /** The code both phones display for this link; compared by people on first contact. */
    val authCode: String? = null,
    /** A person confirmed [authCode] for the current link. */
    val humanVerified: Boolean = false,
    val failureReason: String? = null,
    val attempts: Int = 0,
    val lastSeenEpochMs: Long = 0L
)

/**
 * Identity of a group. [epoch] increments on every coordinator change so a stale
 * coordinator's messages can be recognised and ignored after a failover.
 */
data class GroupIdentity(
    val groupId: String,
    val name: String,
    val coordinatorId: String,
    val epoch: Int = 1
)

/**
 * A group seen in discovery that we are not part of. Only the coordinator's advert can
 * be joined, so this carries the endpoint needed to dial it.
 */
data class DiscoveredGroup(
    val groupId: String,
    val name: String,
    val coordinatorMemberId: String,
    val coordinatorEndpointId: String,
    /** How many devices of this group are currently visible, coordinator included. */
    val devicesVisible: Int
)

/** Everything the UI needs to describe the current group in one snapshot. */
data class GroupState(
    val group: GroupIdentity? = null,
    val localMemberId: String = "",
    val members: List<GroupMember> = emptyList(),
    /**
     * memberId -> the members it has a verified direct link to, as last reported in its
     * heartbeat (our own entry is kept current locally). Drives relay decisions.
     */
    val neighbors: Map<String, Set<String>> = emptyMap(),
    /** Set while forming or joining, cleared once at least one link is live. */
    val isForming: Boolean = false,
    val lastError: String? = null
) {
    val isInGroup: Boolean get() = group != null

    val localRole: GroupRole
        get() = if (group?.coordinatorId == localMemberId) GroupRole.COORDINATOR else GroupRole.MEMBER

    val isCoordinator: Boolean get() = localRole == GroupRole.COORDINATOR

    /** Everyone except us. */
    val others: List<GroupMember> get() = members.filter { it.memberId != localMemberId }

    val connected: List<GroupMember> get() = others.filter { it.state.isLive }

    val relayed: List<GroupMember> get() = others.filter { !it.state.isLive && it.reachableViaRelay }

    val pending: List<GroupMember> get() = others.filter { it.state.isTransitional }

    val awaitingApproval: List<GroupMember>
        get() = others.filter { it.state == MemberState.PENDING_APPROVAL }

    val failed: List<GroupMember> get() = others.filter { it.state == MemberState.FAILED }

    val coordinator: GroupMember? get() = members.find { it.memberId == group?.coordinatorId }

    /** Us plus everyone voice can reach, directly or through a relay. */
    val liveMemberCount: Int get() = connected.size + relayed.size + 1

    fun member(memberId: String): GroupMember? = members.find { it.memberId == memberId }

    fun memberByEndpoint(endpointId: String): GroupMember? =
        members.find { it.endpointId == endpointId }

    /** Only verified members may carry voice, text or control traffic. */
    fun isTrustedEndpoint(endpointId: String): Boolean =
        memberByEndpoint(endpointId)?.state == MemberState.CONNECTED
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/group/GroupNearbyPort.kt`
```kotlin
package com.example.offlinewalkietalkie.group

import android.util.Log
import com.example.offlinewalkietalkie.protocol.Frame
import com.example.offlinewalkietalkie.protocol.FrameType
import com.example.offlinewalkietalkie.transport.LocalIdentity
import com.example.offlinewalkietalkie.transport.TransportManager
import java.util.concurrent.atomic.AtomicInteger

/**
 * Binds [GroupSessionManager] to the real radio. Group control messages travel as
 * GROUP_CONTROL frames on the same link voice uses, so they follow the same lifecycle.
 */
class GroupNearbyPort(
    private val transport: TransportManager,
    private val sessionId: Long
) : GroupTransportPort {

    companion object {
        private const val TAG = "TL/GroupPort"
    }

    private val sequence = AtomicInteger(1)

    override suspend fun connect(endpointId: String) {
        Log.i(TAG, "connect -> $endpointId")
        transport.connect(endpointId)
    }

    override suspend fun acceptConnection(endpointId: String) {
        Log.i(TAG, "accept -> $endpointId")
        transport.acceptConnection(endpointId)
    }

    override suspend fun rejectConnection(endpointId: String) {
        Log.i(TAG, "reject -> $endpointId")
        transport.rejectConnection(endpointId)
    }

    override suspend fun disconnect(endpointId: String) {
        Log.i(TAG, "disconnect -> $endpointId")
        transport.disconnect(endpointId)
    }

    override suspend fun sendControl(endpointId: String, payload: ByteArray) {
        val frame = Frame(
            type = FrameType.GROUP_CONTROL,
            sessionId = sessionId,
            sequence = sequence.getAndIncrement(),
            timestamp = System.currentTimeMillis().toInt(),
            hopLimit = 1,
            payload = payload
        )
        transport.send(endpointId, frame.toBytes())
    }

    override suspend fun publishAdvert(advert: GroupAdvert) {
        val encoded = advert.encode()
        Log.i(TAG, "publishAdvert '$encoded'")
        transport.republishAdvertising(LocalIdentity(encoded))
    }

    override suspend fun clearAdvert() {
        Log.i(TAG, "clearAdvert")
        transport.stopAdvertising()
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/group/GroupProof.kt`
```kotlin
package com.example.offlinewalkietalkie.group

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Proves group membership on a link without a person having to compare codes again.
 *
 * The proof is an HMAC over the group secret, bound to three things:
 * - both member ids, so it only counts between these two devices;
 * - the sender's id, so one side cannot reflect the other's proof back;
 * - the Nearby authentication code of this exact connection. A device relaying between
 *   two victims holds two separate connections with different codes, so a proof it
 *   forwards from one leg is wrong on the other.
 */
object GroupProof {

    const val SECRET_BYTES = 16

    fun newSecret(): ByteArray = ByteArray(SECRET_BYTES).also { SecureRandom().nextBytes(it) }

    fun compute(secret: ByteArray, senderId: String, peerId: String, linkCode: String): String {
        val (low, high) = if (senderId < peerId) senderId to peerId else peerId to senderId
        val message = "TLv1|$low|$high|$linkCode|$senderId".toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(message).toHex()
    }

    /** Constant-time comparison so timing does not leak how much of a guess was right. */
    fun verify(
        secret: ByteArray,
        senderId: String,
        peerId: String,
        linkCode: String,
        proofHex: String
    ): Boolean {
        val expected = compute(secret, senderId, peerId, linkCode)
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.US_ASCII),
            proofHex.toByteArray(Charsets.US_ASCII)
        )
    }

    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    fun fromHex(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return try {
            ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        } catch (e: NumberFormatException) {
            null
        }
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/group/GroupProtocol.kt`
```kotlin
package com.example.offlinewalkietalkie.group

/**
 * Control messages exchanged over established links to keep group membership in sync.
 *
 * These are low-rate (roster changes and a heartbeat every few seconds), so a compact
 * text encoding is used rather than a packed binary one — it stays debuggable in logs
 * and diffable in tests. Voice never travels this path.
 */
sealed class GroupMessage {

    /**
     * Coordinator -> joiner, sent only after a person confirmed the link code on both
     * phones. Carries the group secret that later links use to prove membership without
     * another human check. Nearby encrypts every payload on an accepted connection, and
     * the confirmed code rules out a man in the middle, so this is the one safe moment to
     * hand the secret over.
     */
    data class Welcome(
        val groupId: String,
        val groupName: String,
        val coordinatorId: String,
        val epoch: Int,
        val secretHex: String
    ) : GroupMessage()

    /** Joiner -> coordinator: requesting membership. */
    data class JoinRequest(
        val memberId: String,
        val displayName: String
    ) : GroupMessage()

    /**
     * Coordinator -> everyone: the authoritative membership list. Members apply it
     * wholesale, which is what lets a late joiner learn about peers it never discovered.
     */
    data class Roster(
        val groupId: String,
        val epoch: Int,
        val entries: List<RosterEntry>
    ) : GroupMessage()

    /**
     * Liveness beacon. Absence of these is what triggers a failover. [neighbors] lists the
     * members the sender has a verified direct link to, so every device can work out who
     * needs a relay to be reached.
     */
    data class Heartbeat(
        val memberId: String,
        val epoch: Int,
        val neighbors: List<String> = emptyList()
    ) : GroupMessage()

    /**
     * Sent when a member decides the coordinator is gone and takes over. The highest
     * epoch wins; equal epochs are broken by the lowest member id so two simultaneous
     * claims always converge on the same winner.
     */
    data class CoordinatorClaim(
        val memberId: String,
        val groupId: String,
        val epoch: Int
    ) : GroupMessage()

    /** Deliberate departure, so peers can drop the member without waiting for a timeout. */
    data class Leave(val memberId: String) : GroupMessage()

    /**
     * Proof that the sender holds the group secret, bound to this one link's Nearby code
     * so it cannot be replayed or relayed onto a different connection.
     */
    data class MembershipProof(
        val memberId: String,
        val proofHex: String
    ) : GroupMessage()

    fun encode(): ByteArray = when (this) {
        is Welcome -> join(T_WELCOME, groupId, groupName, coordinatorId, epoch.toString(), secretHex)
        is JoinRequest -> join(T_JOIN, memberId, displayName)
        is Roster -> join(
            T_ROSTER,
            groupId,
            epoch.toString(),
            entries.joinToString(ENTRY_SEP) { it.encode() }
        )
        is Heartbeat -> join(T_HEARTBEAT, memberId, epoch.toString(), neighbors.joinToString(ENTRY_SEP))
        is CoordinatorClaim -> join(T_CLAIM, memberId, groupId, epoch.toString())
        is Leave -> join(T_LEAVE, memberId)
        is MembershipProof -> join(T_PROOF, memberId, proofHex)
    }.toByteArray(Charsets.UTF_8)

    companion object {
        // ASCII unit/record/group separators: never typed by users, so names cannot collide.
        private const val SEP = "\u001F"
        private const val ENTRY_SEP = "\u001E"
        private const val FIELD_SEP = "\u001D"

        private const val T_WELCOME = "W"
        private const val T_JOIN = "J"
        private const val T_ROSTER = "R"
        private const val T_HEARTBEAT = "H"
        private const val T_CLAIM = "C"
        private const val T_LEAVE = "L"
        private const val T_PROOF = "P"

        private fun join(vararg parts: String) = parts.joinToString(SEP)

        private fun splitList(text: String): List<String> =
            text.split(ENTRY_SEP).filter { it.isNotBlank() }

        fun decode(bytes: ByteArray): GroupMessage? {
            val text = try {
                String(bytes, Charsets.UTF_8)
            } catch (e: Throwable) {
                return null
            }
            val parts = text.split(SEP)
            if (parts.isEmpty()) return null

            return try {
                when (parts[0]) {
                    T_WELCOME -> if (parts.size < 6) null else Welcome(
                        groupId = parts[1],
                        groupName = parts[2],
                        coordinatorId = parts[3],
                        epoch = parts[4].toInt(),
                        secretHex = parts[5]
                    )
                    T_JOIN -> if (parts.size < 3) null else JoinRequest(
                        memberId = parts[1],
                        displayName = parts[2]
                    )
                    T_ROSTER -> if (parts.size < 4) null else Roster(
                        groupId = parts[1],
                        epoch = parts[2].toInt(),
                        entries = splitList(parts[3]).mapNotNull { RosterEntry.decode(it, FIELD_SEP) }
                    )
                    T_HEARTBEAT -> if (parts.size < 3) null else Heartbeat(
                        memberId = parts[1],
                        epoch = parts[2].toInt(),
                        neighbors = if (parts.size >= 4) splitList(parts[3]) else emptyList()
                    )
                    T_CLAIM -> if (parts.size < 4) null else CoordinatorClaim(
                        memberId = parts[1],
                        groupId = parts[2],
                        epoch = parts[3].toInt()
                    )
                    T_LEAVE -> if (parts.size < 2) null else Leave(memberId = parts[1])
                    T_PROOF -> if (parts.size < 3) null else MembershipProof(
                        memberId = parts[1],
                        proofHex = parts[2]
                    )
                    else -> null
                }
            } catch (e: NumberFormatException) {
                null
            }
        }

        internal fun entryFieldSeparator() = FIELD_SEP
    }
}

/** One line of the coordinator's membership list. */
data class RosterEntry(
    val memberId: String,
    val displayName: String,
    val role: GroupRole
) {
    fun encode(separator: String = GroupMessage.entryFieldSeparator()): String {
        val roleChar = if (role == GroupRole.COORDINATOR) "C" else "M"
        return listOf(memberId, displayName, roleChar).joinToString(separator)
    }

    companion object {
        fun decode(text: String, separator: String): RosterEntry? {
            val f = text.split(separator)
            if (f.size < 3) return null
            val role = when (f[2]) {
                "C" -> GroupRole.COORDINATOR
                "M" -> GroupRole.MEMBER
                else -> return null
            }
            if (f[0].isBlank()) return null
            return RosterEntry(f[0], f[1], role)
        }
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/group/GroupSessionManager.kt`
```kotlin
package com.example.offlinewalkietalkie.group

import com.example.offlinewalkietalkie.group.GroupProof.toHex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/** Everything the manager needs from the radio layer. Kept narrow so tests can fake it. */
interface GroupTransportPort {
    suspend fun connect(endpointId: String)
    suspend fun acceptConnection(endpointId: String)
    suspend fun rejectConnection(endpointId: String)
    suspend fun disconnect(endpointId: String)
    suspend fun sendControl(endpointId: String, payload: ByteArray)

    /** Republish who we are, so peers see our group id and role in discovery. */
    suspend fun publishAdvert(advert: GroupAdvert)

    /**
     * Stop claiming group membership in discovery. Without this a device that left keeps
     * advertising the old group id and its former peers dial it forever.
     */
    suspend fun clearAdvert()
}

/**
 * Owns group membership and the connection state machine for every peer.
 *
 * Trust model:
 * - The first link into a group (joiner <-> coordinator) must be confirmed by people on
 *   both phones comparing the code Nearby shows. Only then does the coordinator hand over
 *   the group secret.
 * - Every later link between rostered members proves the secret with [GroupProof] instead
 *   of asking a person again. A stranger copying the public advert has no secret and
 *   never gets past VERIFYING.
 * - Control messages are only acted on from verified links.
 *
 * Deliberately free of Android and coroutine-timer dependencies: time enters through
 * [tick] and randomness through the factories, so multi-node scenarios are reproducible
 * in unit tests.
 */
class GroupSessionManager(
    private val transport: GroupTransportPort,
    private val localDisplayName: String,
    localMemberId: String? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val idFactory: (Int) -> String = { len -> randomId(len) },
    private val secretFactory: () -> ByteArray = { GroupProof.newSecret() }
) {
    companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val RETRY_BACKOFF_MS = 3_000L
        const val MAX_ATTEMPTS = 3
        const val HEARTBEAT_INTERVAL_MS = 3_000L

        /** A person has this long to compare codes before the request is dropped. */
        const val APPROVAL_TIMEOUT_MS = 60_000L

        /** Four missed beats before we consider the coordinator gone. */
        const val COORDINATOR_TIMEOUT_MS = 12_000L

        private const val ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun randomId(length: Int): String =
            (1..length).map { ID_ALPHABET[Random.nextInt(ID_ALPHABET.length)] }.joinToString("")
    }

    val localMemberId: String = localMemberId ?: idFactory(GroupAdvert.MEMBER_ID_LENGTH)

    private val _state = MutableStateFlow(GroupState(localMemberId = this.localMemberId))
    val state: StateFlow<GroupState> = _state.asStateFlow()

    /** Shared by every member once admitted; never leaves an accepted, code-checked link. */
    private var groupSecret: ByteArray? = null

    /** memberId -> when the in-flight connection attempt started. */
    private val attemptStartedAt = mutableMapOf<String, Long>()

    /** memberId -> when we started waiting for a person to confirm the code. */
    private val approvalStartedAt = mutableMapOf<String, Long>()

    /** memberId -> earliest time a retry may fire. */
    private val retryNotBefore = mutableMapOf<String, Long>()

    /** Members whose link is up at the radio level (not necessarily verified yet). */
    private val linkUp = mutableSetOf<String>()

    /** Members that proved the group secret on their current link. */
    private val proofAccepted = mutableSetOf<String>()

    private var lastCoordinatorBeatAt = 0L
    private var lastHeartbeatSentAt = 0L

    /**
     * Whether we have ever actually reached the coordinator. A joiner that never got a
     * link must not stage a coup — that turns one failed join into two rival groups.
     */
    private var hasSyncedWithCoordinator = false

    val holdsGroupSecret: Boolean get() = groupSecret != null

    // ---------------------------------------------------------------- group lifecycle

    /** Become the coordinator of a brand new group. */
    suspend fun createGroup(name: String) {
        resetLinkTracking()
        groupSecret = secretFactory()
        val group = GroupIdentity(
            groupId = idFactory(GroupAdvert.GROUP_ID_LENGTH),
            name = name,
            coordinatorId = localMemberId,
            epoch = 1
        )
        val me = GroupMember(
            memberId = localMemberId,
            displayName = localDisplayName,
            role = GroupRole.COORDINATOR,
            state = MemberState.CONNECTED,
            onRoster = true,
            lastSeenEpochMs = now()
        )
        _state.value = GroupState(
            group = group,
            localMemberId = localMemberId,
            members = listOf(me),
            isForming = true
        )
        lastCoordinatorBeatAt = now()
        hasSyncedWithCoordinator = true
        publishAdvert()
    }

    /**
     * Join a group we saw in discovery. The link to the coordinator is opened here; it
     * stays unverified until people on both phones confirm the code.
     */
    suspend fun joinGroup(advert: GroupAdvert, endpointId: String) {
        resetLinkTracking()
        groupSecret = null
        val group = GroupIdentity(
            groupId = advert.groupId,
            name = advert.displayName,
            coordinatorId = advert.memberId,
            epoch = 1
        )
        val me = GroupMember(
            memberId = localMemberId,
            displayName = localDisplayName,
            role = GroupRole.MEMBER,
            state = MemberState.CONNECTED,
            onRoster = true,
            lastSeenEpochMs = now()
        )
        val target = GroupMember(
            memberId = advert.memberId,
            displayName = advert.displayName,
            endpointId = endpointId,
            role = GroupRole.COORDINATOR,
            state = MemberState.CONNECTING,
            lastSeenEpochMs = now()
        )
        _state.value = GroupState(
            group = group,
            localMemberId = localMemberId,
            members = listOf(me, target),
            isForming = true
        )
        lastCoordinatorBeatAt = now()
        hasSyncedWithCoordinator = false
        attemptStartedAt[advert.memberId] = now()
        publishAdvert()
        transport.connect(endpointId)
    }

    /** Deliberate departure: tell everyone, then drop every link. */
    suspend fun leaveGroup() {
        val snapshot = _state.value
        val payload = GroupMessage.Leave(localMemberId).encode()
        snapshot.connected.forEach { member ->
            member.endpointId?.let { runCatchingSend(it, payload) }
        }
        snapshot.others.mapNotNull { it.endpointId }.forEach { transport.disconnect(it) }
        resetLinkTracking()
        transport.clearAdvert()
        groupSecret = null
        hasSyncedWithCoordinator = false
        _state.value = GroupState(localMemberId = localMemberId)
    }

    // ------------------------------------------------------------------ radio events

    /**
     * A peer appeared in discovery. Only adverts for our own group are acted on; the
     * rest are surfaced separately for the "join a group" picker.
     */
    suspend fun onEndpointDiscovered(endpointId: String, advert: GroupAdvert) {
        val snapshot = _state.value
        val group = snapshot.group ?: return
        if (advert.groupId != group.groupId) return
        if (advert.memberId == localMemberId) return

        // Advertising our group id proves nothing — anyone can copy it. Only members the
        // coordinator admitted are worth tracking; everyone else must come in by request.
        val existing = snapshot.member(advert.memberId) ?: return
        if (!existing.onRoster) return
        if (existing.state.isLive || existing.state.isTransitional) return

        updateMember(advert.memberId) {
            it?.copy(
                displayName = advert.displayName,
                endpointId = endpointId,
                role = advert.role,
                lastSeenEpochMs = now()
            )
        }
        maybeInitiate(advert.memberId)
    }

    fun onEndpointLost(endpointId: String) {
        val member = _state.value.memberByEndpoint(endpointId) ?: return
        if (member.state.isLive) return
        if (member.state == MemberState.DISCOVERED && !member.onRoster) {
            removeMember(member.memberId)
        }
    }

    /**
     * Nearby raises this on BOTH ends of every new connection, with the same [authCode].
     *
     * A rostered member is let in straight away and then has to prove the group secret.
     * Anyone else — a joiner on first contact, or a stranger — waits for a person to
     * compare the code.
     */
    suspend fun onConnectionRequest(endpointId: String, advert: GroupAdvert?, authCode: String) {
        val snapshot = _state.value
        val group = snapshot.group

        if (advert == null || group == null || advert.groupId != group.groupId) {
            if (advert != null && advert.memberId != localMemberId) {
                markPendingApproval(advert, endpointId, authCode)
            }
            return
        }
        if (advert.memberId == localMemberId) {
            // Someone is advertising our own identity back at us.
            transport.rejectConnection(endpointId)
            return
        }

        val existing = snapshot.member(advert.memberId)
        if (existing != null && existing.state.isLive && existing.endpointId != endpointId) {
            // Second link claiming an identity that is already verified elsewhere: a
            // duplicate at best, an impostor at worst. Either way, keep the one we have.
            transport.rejectConnection(endpointId)
            return
        }

        val canProve = groupSecret != null && existing?.onRoster == true
        if (!canProve) {
            markPendingApproval(advert, endpointId, authCode)
            return
        }

        updateMember(advert.memberId) {
            it?.copy(
                endpointId = endpointId,
                displayName = advert.displayName,
                authCode = authCode,
                humanVerified = false,
                state = MemberState.CONNECTING,
                lastSeenEpochMs = now()
            )
        }
        linkUp.remove(advert.memberId)
        proofAccepted.remove(advert.memberId)
        attemptStartedAt[advert.memberId] = now()
        transport.acceptConnection(endpointId)
    }

    private fun markPendingApproval(advert: GroupAdvert, endpointId: String, authCode: String) {
        updateMember(advert.memberId) { current ->
            (current ?: GroupMember(advert.memberId, advert.displayName)).copy(
                endpointId = endpointId,
                displayName = advert.displayName,
                role = current?.role ?: advert.role,
                authCode = authCode,
                humanVerified = false,
                state = MemberState.PENDING_APPROVAL,
                lastSeenEpochMs = now()
            )
        }
        attemptStartedAt.remove(advert.memberId)
        approvalStartedAt[advert.memberId] = now()
    }

    /** A person compared the codes and answered. */
    suspend fun respondToRequest(memberId: String, accept: Boolean) {
        val member = _state.value.member(memberId) ?: return
        val endpointId = member.endpointId ?: return
        approvalStartedAt.remove(memberId)
        if (accept) {
            attemptStartedAt[memberId] = now()
            updateMember(memberId) {
                it?.copy(state = MemberState.CONNECTING, humanVerified = true)
            }
            transport.acceptConnection(endpointId)
        } else {
            transport.rejectConnection(endpointId)
            removeMember(memberId)
        }
    }

    suspend fun onConnected(endpointId: String) {
        val member = _state.value.memberByEndpoint(endpointId) ?: return
        val memberId = member.memberId
        linkUp.add(memberId)
        retryNotBefore.remove(memberId)

        if (member.humanVerified) {
            promote(memberId)
            val group = _state.value.group ?: return
            if (_state.value.isCoordinator) {
                val secret = groupSecret ?: return
                sendControl(endpointId, GroupMessage.Welcome(
                    groupId = group.groupId,
                    groupName = group.name,
                    coordinatorId = group.coordinatorId,
                    epoch = group.epoch,
                    secretHex = secret.toHex()
                ))
                broadcastRoster()
            } else if (memberId == group.coordinatorId) {
                lastCoordinatorBeatAt = now()
                hasSyncedWithCoordinator = true
                sendControl(endpointId, GroupMessage.JoinRequest(localMemberId, localDisplayName))
            }
            return
        }

        // Rostered member on a new link: both sides prove the secret before voice flows.
        attemptStartedAt[memberId] = now()
        updateMember(memberId) { it?.copy(state = MemberState.VERIFYING, lastSeenEpochMs = now()) }
        sendProof(endpointId, member)
        if (memberId in proofAccepted) promote(memberId)
    }

    /**
     * A connection that never came up: the other side declined, or the radio gave up.
     * Distinct from [onDisconnected], which is a link that was up and dropped.
     */
    fun onConnectionFailed(endpointId: String, reason: String) {
        val member = _state.value.memberByEndpoint(endpointId) ?: return
        if (member.state.isLive) return
        attemptStartedAt.remove(member.memberId)
        approvalStartedAt.remove(member.memberId)
        linkUp.remove(member.memberId)
        proofAccepted.remove(member.memberId)
        if (member.onRoster) {
            // Known member: the retry schedule will try again.
            retryNotBefore[member.memberId] = now() + RETRY_BACKOFF_MS
            updateMember(member.memberId) { it?.copy(state = MemberState.RECONNECTING) }
        } else {
            updateMember(member.memberId) {
                it?.copy(state = MemberState.FAILED, failureReason = reason, humanVerified = false)
            }
        }
    }

    suspend fun onDisconnected(endpointId: String) {
        val member = _state.value.memberByEndpoint(endpointId) ?: return
        val memberId = member.memberId
        attemptStartedAt.remove(memberId)
        approvalStartedAt.remove(memberId)
        linkUp.remove(memberId)
        proofAccepted.remove(memberId)

        if (member.state == MemberState.LEFT) {
            removeMember(memberId)
            return
        }
        if (!member.onRoster) {
            // Never admitted: nothing to reconnect to.
            removeMember(memberId)
            refreshNeighbors()
            return
        }
        // Still on the roster, so treat it as a blip and retry rather than forgetting them.
        retryNotBefore[memberId] = now() + RETRY_BACKOFF_MS
        updateMember(memberId) {
            it?.copy(state = MemberState.RECONNECTING, humanVerified = false, lastSeenEpochMs = now())
        }
        refreshNeighbors()
    }

    // --------------------------------------------------------------- control messages

    suspend fun onControlMessage(endpointId: String, message: GroupMessage) {
        val sender = _state.value.memberByEndpoint(endpointId)

        if (message is GroupMessage.MembershipProof) {
            applyProof(endpointId, message)
            return
        }
        // Everything else changes group state, so it must come from a verified link.
        val trusted = sender != null &&
            (sender.state == MemberState.CONNECTED || sender.humanVerified)
        if (!trusted) return

        when (message) {
            is GroupMessage.Welcome -> applyWelcome(sender!!, message)
            is GroupMessage.JoinRequest -> applyJoinRequest(endpointId, message)
            is GroupMessage.Roster -> applyRoster(sender!!, message)
            is GroupMessage.Heartbeat -> applyHeartbeat(message)
            is GroupMessage.CoordinatorClaim -> applyCoordinatorClaim(message)
            is GroupMessage.Leave -> applyLeave(message)
            is GroupMessage.MembershipProof -> Unit
        }
    }

    private suspend fun applyProof(endpointId: String, message: GroupMessage.MembershipProof) {
        val member = _state.value.memberByEndpoint(endpointId) ?: return
        val secret = groupSecret ?: return
        val code = member.authCode ?: return
        if (member.humanVerified) return
        if (message.memberId != member.memberId) {
            failVerification(member, "Identity mismatch on link")
            return
        }
        val ok = GroupProof.verify(secret, member.memberId, localMemberId, code, message.proofHex)
        if (!ok) {
            failVerification(member, "Could not prove group membership")
            return
        }
        proofAccepted.add(member.memberId)
        if (member.memberId in linkUp) promote(member.memberId)
    }

    private suspend fun failVerification(member: GroupMember, reason: String) {
        attemptStartedAt.remove(member.memberId)
        linkUp.remove(member.memberId)
        proofAccepted.remove(member.memberId)
        updateMember(member.memberId) {
            it?.copy(state = MemberState.FAILED, failureReason = reason, humanVerified = false)
        }
        member.endpointId?.let { transport.disconnect(it) }
    }

    private suspend fun applyWelcome(sender: GroupMember, message: GroupMessage.Welcome) {
        val group = _state.value.group ?: return
        if (message.groupId != group.groupId) return
        if (message.epoch < group.epoch) return
        // The secret only counts when it arrives on the code-checked link to the coordinator.
        if (!sender.humanVerified || sender.memberId != message.coordinatorId) return
        val secret = GroupProof.fromHex(message.secretHex) ?: return

        groupSecret = secret
        _state.value = _state.value.copy(
            group = group.copy(
                name = message.groupName,
                coordinatorId = message.coordinatorId,
                epoch = message.epoch
            ),
            isForming = false
        )
        updateMember(sender.memberId) { it?.copy(onRoster = true, role = GroupRole.COORDINATOR) }
        lastCoordinatorBeatAt = now()
        hasSyncedWithCoordinator = true
        publishAdvert()
    }

    private suspend fun applyJoinRequest(endpointId: String, message: GroupMessage.JoinRequest) {
        if (!_state.value.isCoordinator) return
        val member = _state.value.memberByEndpoint(endpointId) ?: return
        // Only a link a person confirmed may add someone to the roster.
        if (!member.humanVerified || member.memberId != message.memberId) return
        updateMember(message.memberId) {
            it?.copy(
                displayName = message.displayName,
                onRoster = true,
                state = MemberState.CONNECTED,
                lastSeenEpochMs = now()
            )
        }
        broadcastRoster()
    }

    /**
     * The coordinator's list is authoritative. Applying it wholesale is what lets a late
     * joiner learn about members it never saw in discovery, then dial them directly.
     */
    private suspend fun applyRoster(sender: GroupMember, message: GroupMessage.Roster) {
        val group = _state.value.group ?: return
        if (message.groupId != group.groupId) return
        if (message.epoch < group.epoch) return
        if (sender.memberId != group.coordinatorId) return
        if (_state.value.isCoordinator && message.epoch <= group.epoch) return

        message.entries.forEach { entry ->
            if (entry.memberId == localMemberId) return@forEach
            updateMember(entry.memberId) { current ->
                (current ?: GroupMember(entry.memberId, entry.displayName)).copy(
                    displayName = entry.displayName,
                    role = entry.role,
                    onRoster = true,
                    // A roster listing is not a link; only mark live if we already had one.
                    state = current?.state ?: MemberState.DISCOVERED,
                    isDirect = current?.state?.isLive == true
                )
            }
        }
        // Anyone the coordinator dropped is no longer in the group.
        val rostered = message.entries.map { it.memberId }.toSet() + localMemberId
        _state.value.others
            .filter { it.memberId !in rostered && it.state != MemberState.PENDING_APPROVAL }
            .forEach { removeMember(it.memberId) }

        lastCoordinatorBeatAt = now()
        hasSyncedWithCoordinator = true
        refreshNeighbors()
        connectToMissingMembers()
    }

    private fun applyHeartbeat(message: GroupMessage.Heartbeat) {
        val group = _state.value.group ?: return
        if (message.memberId == group.coordinatorId) {
            lastCoordinatorBeatAt = now()
            hasSyncedWithCoordinator = true
        }
        updateMember(message.memberId) { it?.copy(lastSeenEpochMs = now()) }
        val neighbors = _state.value.neighbors + (message.memberId to message.neighbors.toSet())
        _state.value = _state.value.copy(neighbors = neighbors)
        refreshNeighbors()
    }

    /**
     * Highest epoch wins. Equal epochs go to the lowest member id, so two devices that
     * claim at the same instant still converge instead of fighting.
     */
    private suspend fun applyCoordinatorClaim(message: GroupMessage.CoordinatorClaim) {
        val group = _state.value.group ?: return
        if (message.groupId != group.groupId) return
        if (_state.value.member(message.memberId)?.onRoster != true &&
            message.memberId != localMemberId
        ) return

        val accept = message.epoch > group.epoch ||
            (message.epoch == group.epoch && message.memberId < group.coordinatorId)
        if (!accept) return

        _state.value = _state.value.copy(
            group = group.copy(coordinatorId = message.memberId, epoch = message.epoch)
        )
        updateMember(message.memberId) { it?.copy(role = GroupRole.COORDINATOR) }
        if (message.memberId != localMemberId) {
            updateMember(localMemberId) { it?.copy(role = GroupRole.MEMBER) }
        }
        lastCoordinatorBeatAt = now()
        hasSyncedWithCoordinator = true
        publishAdvert()
    }

    private suspend fun applyLeave(message: GroupMessage.Leave) {
        val member = _state.value.member(message.memberId) ?: return
        member.endpointId?.let { transport.disconnect(it) }
        removeMember(message.memberId)
        refreshNeighbors()
        if (_state.value.isCoordinator) broadcastRoster()
    }

    // ------------------------------------------------------------------------- timing

    /**
     * Drives timeouts, retries, heartbeats and failover. The Android layer calls this
     * about once a second; tests call it with explicit timestamps.
     */
    suspend fun tick() {
        val current = now()
        if (_state.value.group == null) return

        expireUnansweredApprovals(current)
        expireStalledAttempts(current)
        runScheduledRetries(current)
        sendHeartbeatIfDue(current)
        checkCoordinatorLiveness(current)
    }

    private suspend fun expireUnansweredApprovals(current: Long) {
        val expired = approvalStartedAt.filter { (_, started) ->
            current - started > APPROVAL_TIMEOUT_MS
        }.keys.toList()
        expired.forEach { memberId ->
            approvalStartedAt.remove(memberId)
            val member = _state.value.member(memberId) ?: return@forEach
            member.endpointId?.let { transport.rejectConnection(it) }
            updateMember(memberId) {
                it?.copy(state = MemberState.FAILED, failureReason = "Code was not confirmed in time")
            }
        }
    }

    private suspend fun expireStalledAttempts(current: Long) {
        val stalled = attemptStartedAt.filter { (_, started) ->
            current - started > CONNECT_TIMEOUT_MS
        }.keys.toList()

        stalled.forEach { memberId ->
            attemptStartedAt.remove(memberId)
            val member = _state.value.member(memberId) ?: return@forEach
            if (member.state.isLive) return@forEach
            if (member.state == MemberState.VERIFYING) {
                failVerification(member, "Did not prove group membership")
                return@forEach
            }
            val attempts = member.attempts + 1
            if (attempts >= MAX_ATTEMPTS) {
                updateMember(memberId) {
                    it?.copy(
                        state = MemberState.FAILED,
                        attempts = attempts,
                        failureReason = "No response after $attempts attempts"
                    )
                }
            } else {
                retryNotBefore[memberId] = current + RETRY_BACKOFF_MS
                updateMember(memberId) {
                    it?.copy(state = MemberState.RECONNECTING, attempts = attempts)
                }
            }
        }
    }

    private suspend fun runScheduledRetries(current: Long) {
        val due = retryNotBefore.filter { (_, at) -> current >= at }.keys.toList()
        due.forEach { memberId ->
            val member = _state.value.member(memberId) ?: run {
                retryNotBefore.remove(memberId)
                return@forEach
            }
            if (member.state.isLive) {
                retryNotBefore.remove(memberId)
                return@forEach
            }
            val endpointId = member.endpointId ?: return@forEach
            retryNotBefore.remove(memberId)
            if (!shouldInitiateTo(memberId)) return@forEach
            attemptStartedAt[memberId] = current
            updateMember(memberId) { it?.copy(state = MemberState.CONNECTING) }
            transport.connect(endpointId)
        }
    }

    private suspend fun sendHeartbeatIfDue(current: Long) {
        if (current - lastHeartbeatSentAt < HEARTBEAT_INTERVAL_MS) return
        lastHeartbeatSentAt = current
        val group = _state.value.group ?: return
        val beat = GroupMessage.Heartbeat(
            memberId = localMemberId,
            epoch = group.epoch,
            neighbors = _state.value.connected.map { it.memberId }
        )
        _state.value.connected.forEach { member ->
            member.endpointId?.let { sendControl(it, beat) }
        }
    }

    private suspend fun checkCoordinatorLiveness(current: Long) {
        val snapshot = _state.value
        val group = snapshot.group ?: return
        if (snapshot.isCoordinator) return
        // Never reached them at all: this is a join that is still failing, not a
        // coordinator that died. Retry logic owns this case.
        if (!hasSyncedWithCoordinator) return
        if (groupSecret == null) return
        if (current - lastCoordinatorBeatAt <= COORDINATOR_TIMEOUT_MS) return

        // Only the lowest surviving id claims, so the group does not fragment.
        val candidates = (snapshot.connected.map { it.memberId } + localMemberId)
            .filter { it != group.coordinatorId }
            .sorted()
        if (candidates.firstOrNull() != localMemberId) return

        val claimed = group.copy(coordinatorId = localMemberId, epoch = group.epoch + 1)
        _state.value = snapshot.copy(group = claimed)
        updateMember(localMemberId) { it?.copy(role = GroupRole.COORDINATOR) }
        removeMember(group.coordinatorId)
        lastCoordinatorBeatAt = current
        hasSyncedWithCoordinator = true

        val claim = GroupMessage.CoordinatorClaim(localMemberId, claimed.groupId, claimed.epoch)
        _state.value.connected.forEach { member ->
            member.endpointId?.let { sendControl(it, claim) }
        }
        refreshNeighbors()
        publishAdvert()
        broadcastRoster()
    }

    // ------------------------------------------------------------------------ helpers

    /**
     * Both devices see each other at once, so without a rule both would dial and collide.
     * The lower member id initiates; the higher waits for the incoming request. Until we
     * are admitted, though, we are the only one who can dial — the coordinator does not
     * know we exist yet.
     */
    private fun shouldInitiateTo(memberId: String): Boolean {
        val group = _state.value.group ?: return false
        if (groupSecret == null) return memberId == group.coordinatorId
        return localMemberId < memberId
    }

    private suspend fun maybeInitiate(memberId: String) {
        val member = _state.value.member(memberId) ?: return
        if (member.state.isLive || member.state.isTransitional) return
        if (member.state == MemberState.FAILED) return
        if (!member.onRoster) return
        if (attemptStartedAt.containsKey(memberId)) return
        if (!shouldInitiateTo(memberId)) return
        val endpointId = member.endpointId ?: return

        attemptStartedAt[memberId] = now()
        updateMember(memberId) { it?.copy(state = MemberState.CONNECTING) }
        transport.connect(endpointId)
    }

    private suspend fun connectToMissingMembers() {
        _state.value.others
            .filter { !it.state.isLive && !it.state.isTransitional && it.endpointId != null }
            .forEach { maybeInitiate(it.memberId) }
    }

    private suspend fun promote(memberId: String) {
        attemptStartedAt.remove(memberId)
        updateMember(memberId) {
            it?.copy(
                state = MemberState.CONNECTED,
                attempts = 0,
                failureReason = null,
                isDirect = true,
                reachableViaRelay = false,
                lastSeenEpochMs = now()
            )
        }
        _state.value = _state.value.copy(isForming = false, lastError = null)
        refreshNeighbors()
        if (_state.value.isCoordinator && _state.value.member(memberId)?.onRoster == true) {
            broadcastRoster()
        }
    }

    private suspend fun sendProof(endpointId: String, member: GroupMember) {
        val secret = groupSecret ?: return
        val code = member.authCode ?: return
        val proof = GroupProof.compute(secret, localMemberId, member.memberId, code)
        sendControl(endpointId, GroupMessage.MembershipProof(localMemberId, proof))
    }

    /**
     * Keeps our own neighbour entry current and marks roster members that we cannot reach
     * directly but a neighbour of ours can, so the UI and voice relay agree.
     */
    private fun refreshNeighbors() {
        val snapshot = _state.value
        val mine = snapshot.connected.map { it.memberId }.toSet()
        val neighbors = snapshot.neighbors
            .filterKeys { it == localMemberId || it in mine }
            .plus(localMemberId to mine)

        val members = snapshot.members.map { member ->
            if (member.memberId == localMemberId || member.state.isLive) {
                member.copy(reachableViaRelay = false, isDirect = member.state.isLive || member.isDirect)
            } else {
                val viaRelay = member.onRoster && mine.any { n ->
                    neighbors[n]?.contains(member.memberId) == true
                }
                member.copy(reachableViaRelay = viaRelay, isDirect = false)
            }
        }
        _state.value = snapshot.copy(members = members, neighbors = neighbors)
    }

    private suspend fun broadcastRoster() {
        val snapshot = _state.value
        val group = snapshot.group ?: return
        if (!snapshot.isCoordinator) return

        val entries = snapshot.members
            .filter { it.memberId == localMemberId || (it.onRoster && it.state.isLive) }
            .map { RosterEntry(it.memberId, it.displayName, it.role) }
        val roster = GroupMessage.Roster(group.groupId, group.epoch, entries)
        snapshot.connected.forEach { member ->
            member.endpointId?.let { sendControl(it, roster) }
        }
    }

    private suspend fun publishAdvert() {
        val snapshot = _state.value
        val group = snapshot.group ?: return
        transport.publishAdvert(
            GroupAdvert(
                groupId = group.groupId,
                memberId = localMemberId,
                role = snapshot.localRole,
                // Peers browsing for a group need its name, not the device's.
                displayName = if (snapshot.isCoordinator) group.name else localDisplayName
            )
        )
    }

    private suspend fun sendControl(endpointId: String, message: GroupMessage) {
        runCatchingSend(endpointId, message.encode())
    }

    private suspend fun runCatchingSend(endpointId: String, payload: ByteArray) {
        try {
            transport.sendControl(endpointId, payload)
        } catch (e: Throwable) {
            _state.value = _state.value.copy(lastError = "Send failed: ${e.message}")
        }
    }

    private fun resetLinkTracking() {
        attemptStartedAt.clear()
        approvalStartedAt.clear()
        retryNotBefore.clear()
        linkUp.clear()
        proofAccepted.clear()
    }

    private fun updateMember(memberId: String, transform: (GroupMember?) -> GroupMember?) {
        val snapshot = _state.value
        val existing = snapshot.member(memberId)
        val updated = transform(existing) ?: return
        val members = if (existing == null) {
            snapshot.members + updated
        } else {
            snapshot.members.map { if (it.memberId == memberId) updated else it }
        }
        _state.value = snapshot.copy(members = members)
    }

    private fun removeMember(memberId: String) {
        if (memberId == localMemberId) return
        attemptStartedAt.remove(memberId)
        approvalStartedAt.remove(memberId)
        retryNotBefore.remove(memberId)
        linkUp.remove(memberId)
        proofAccepted.remove(memberId)
        _state.value = _state.value.copy(
            members = _state.value.members.filter { it.memberId != memberId },
            neighbors = _state.value.neighbors - memberId
        )
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/group/RelayPlanner.kt`
```kotlin
package com.example.offlinewalkietalkie.group

/**
 * Decides which neighbours should get a relayed copy of a voice frame.
 *
 * Flooding every frame to every neighbour works but multiplies airtime in a well-connected
 * group: in a full mesh of four phones each frame would be sent three extra times for no
 * benefit. Instead, a device forwards a frame it got from [prevHop] only to neighbours
 * that [prevHop] cannot reach itself, and only if it is the lowest-id device able to bridge
 * that gap — so exactly one relay covers each hole.
 *
 * If the neighbour map is stale or missing, this degrades to flooding, which is safe
 * because duplicate frames are dropped by the seen-packet cache.
 */
object RelayPlanner {

    fun relayTargets(
        self: String,
        prevHop: String,
        neighbors: Map<String, Set<String>>
    ): Set<String> {
        val mine = neighbors[self].orEmpty()
        val prevReach = neighbors[prevHop]
        val result = mutableSetOf<String>()

        for (target in mine) {
            if (target == prevHop) continue
            if (prevReach != null && target in prevReach) continue

            // Everyone adjacent to both ends could bridge this gap; the lowest id does it.
            val bridges = mutableSetOf(self)
            if (prevReach != null) {
                for (candidate in prevReach) {
                    if (candidate == target) continue
                    val candReach = neighbors[candidate].orEmpty()
                    val targetReach = neighbors[target].orEmpty()
                    if (target in candReach || candidate in targetReach) bridges.add(candidate)
                }
            }
            if (bridges.min() == self) result.add(target)
        }
        return result
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/identity/TrustedContactStore.kt`
```kotlin
package com.example.offlinewalkietalkie.identity

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TrustedContactStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "trusted_contacts",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun isContactTrusted(identityKey: String): Boolean {
        return sharedPrefs.getBoolean(identityKey, false)
    }

    fun trustContact(identityKey: String) {
        sharedPrefs.edit().putBoolean(identityKey, true).apply()
    }

    fun untrustContact(identityKey: String) {
        sharedPrefs.edit().remove(identityKey).apply()
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/mesh/MeshRelayRouter.kt`
```kotlin
package com.example.offlinewalkietalkie.mesh

import com.example.offlinewalkietalkie.protocol.InnerPacket
import com.example.offlinewalkietalkie.protocol.TwoLayerCodec
import java.util.concurrent.ConcurrentHashMap

sealed class MeshOutcome {
    data class DeliveredLocally(val innerPacket: InnerPacket) : MeshOutcome()
    data class Forwarded(
        val nextHopEndpointIds: List<String>,
        val rewrappedPackets: Map<String, ByteArray>,
        val remainingHopLimit: Byte
    ) : MeshOutcome()
    object DroppedDuplicate : MeshOutcome()
    object DroppedHopLimitExceeded : MeshOutcome()
    object DroppedAuthFailed : MeshOutcome()
    object DroppedNoRoute : MeshOutcome()
}

/**
 * Section 1, Section 3, Section 19.1 & Section 19.2 Multi-Hop Mesh Relay Router.
 *
 * Enforces:
 * - Hop-by-hop OuterPacket re-encryption across intermediate relays without voice keys (§19.1).
 * - Bounded forwarding tree routing and loop prevention via SeenPacketCache.
 * - Hop limit decrement and enforcement (§2: initial 3, max 5).
 * - Honest relay role: relays forward authenticated ciphertext without decrypting voice (§19.2).
 */
class MeshRelayRouter(
    val localNodeId: Long,
    val maxHopLimit: Int = 3,
    private val codec: TwoLayerCodec = TwoLayerCodec()
) {
    val routeTable = MeshRouteTable(maxHopLimit = maxHopLimit)
    val seenCache = SeenPacketCache()

    // EndpointId -> 32-byte hopKey derived during neighbor link handshake
    val linkKeys = ConcurrentHashMap<String, ByteArray>()

    // Channel voice key: present ONLY if this node is an authorized channel member; null for relay-only nodes (§19.2)
    var channelVoiceKey: ByteArray? = null

    var isRelayEnabled: Boolean = true

    // Diagnostics counters
    var totalPacketsReceived = 0
    var totalPacketsRelayed = 0
    var totalDuplicatesDropped = 0
    var totalHopLimitDropped = 0
    var totalAuthFailedDropped = 0

    fun registerNeighborLink(endpointId: String, neighborNodeId: Long, hopKey: ByteArray, isRelay: Boolean = false) {
        linkKeys[endpointId] = hopKey
        routeTable.addOrUpdateNeighbor(
            nodeId = neighborNodeId,
            endpointId = endpointId,
            isRelay = isRelay
        )
    }

    fun unregisterNeighborLink(endpointId: String) {
        linkKeys.remove(endpointId)
        routeTable.removeNeighbor(endpointId)
    }

    /**
     * Process an incoming wire-level mesh packet from a neighboring radio endpoint.
     */
    fun processPacket(fromEndpointId: String, wireBytes: ByteArray): MeshOutcome {
        totalPacketsReceived++

        // 1. Authenticate hop-by-hop outer layer using the link key for this neighbor
        val linkKey = linkKeys[fromEndpointId]
        if (linkKey == null) {
            totalAuthFailedDropped++
            return MeshOutcome.DroppedAuthFailed
        }

        val decodedOuter = codec.decodeOuter(wireBytes, linkKey)
        if (decodedOuter == null) {
            totalAuthFailedDropped++
            return MeshOutcome.DroppedAuthFailed
        }

        // 2. Check hop limit
        if (decodedOuter.hopLimit <= 0) {
            totalHopLimitDropped++
            return MeshOutcome.DroppedHopLimitExceeded
        }

        // 3. Deduplication check using inner ciphertext fingerprint
        val cipherFingerprint = java.util.Arrays.hashCode(decodedOuter.innerCiphertext)
        if (seenCache.isDuplicate(decodedOuter.senderNodeId, cipherFingerprint)) {
            totalDuplicatesDropped++
            return MeshOutcome.DroppedDuplicate
        }

        // 4. Try local delivery if this node holds the channel key
        val localKey = channelVoiceKey
        if (localKey != null) {
            val inner = codec.unpackInner(decodedOuter.innerCiphertext, localKey)
            if (inner != null) {
                // If hop limit > 1 and relay is enabled, also forward down the tree to other peers
                if (isRelayEnabled && decodedOuter.hopLimit > 1) {
                    val remainingHops = (decodedOuter.hopLimit - 1).toByte()
                    val nextHops = routeTable.getForwardingNextHops(excludeEndpointId = fromEndpointId)
                    if (nextHops.isNotEmpty()) {
                        val rewrapped = mutableMapOf<String, ByteArray>()
                        for (nh in nextHops) {
                            val nhKey = linkKeys[nh] ?: continue
                            rewrapped[nh] = codec.rewrapOuter(
                                innerCiphertext = decodedOuter.innerCiphertext,
                                newHopKey = nhKey,
                                newSenderNodeId = localNodeId,
                                hopLimit = remainingHops
                            )
                        }
                        if (rewrapped.isNotEmpty()) {
                            totalPacketsRelayed++
                            // Both delivered locally and forwarded
                        }
                    }
                }
                return MeshOutcome.DeliveredLocally(inner)
            }
        }

        // 5. Relay-Only behavior (§19.1 & §19.2):
        // Node does NOT possess channelKey. It re-encrypts the outer envelope for next-hop links.
        if (!isRelayEnabled) {
            return MeshOutcome.DroppedNoRoute
        }

        val remainingHops = (decodedOuter.hopLimit - 1).toByte()
        if (remainingHops <= 0) {
            totalHopLimitDropped++
            return MeshOutcome.DroppedHopLimitExceeded
        }

        val nextHops = routeTable.getForwardingNextHops(excludeEndpointId = fromEndpointId)
        if (nextHops.isEmpty()) {
            return MeshOutcome.DroppedNoRoute
        }

        val rewrapped = mutableMapOf<String, ByteArray>()
        for (nh in nextHops) {
            val nhKey = linkKeys[nh] ?: continue
            rewrapped[nh] = codec.rewrapOuter(
                innerCiphertext = decodedOuter.innerCiphertext,
                newHopKey = nhKey,
                newSenderNodeId = localNodeId,
                hopLimit = remainingHops
            )
        }

        if (rewrapped.isEmpty()) {
            return MeshOutcome.DroppedNoRoute
        }

        totalPacketsRelayed++
        return MeshOutcome.Forwarded(
            nextHopEndpointIds = nextHops,
            rewrappedPackets = rewrapped,
            remainingHopLimit = remainingHops
        )
    }

    /**
     * Originates a new packet from this node for transmission down the bounded forwarding tree.
     */
    fun originatePacket(innerPacket: InnerPacket, channelKey: ByteArray): Map<String, ByteArray> {
        seenCache.markSeen(localNodeId, innerPacket.sequence)

        val nextHops = routeTable.getForwardingNextHops()
        val packets = mutableMapOf<String, ByteArray>()

        for (nh in nextHops) {
            val nhKey = linkKeys[nh] ?: continue
            val wireBytes = codec.pack(
                innerPacket = innerPacket,
                channelKey = channelKey,
                hopKey = nhKey,
                senderNodeId = localNodeId
            )
            packets[nh] = wireBytes
        }
        return packets
    }

    fun reset() {
        linkKeys.clear()
        routeTable.clear()
        seenCache.clear()
        channelVoiceKey = null
        totalPacketsReceived = 0
        totalPacketsRelayed = 0
        totalDuplicatesDropped = 0
        totalHopLimitDropped = 0
        totalAuthFailedDropped = 0
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/mesh/MeshRouteTable.kt`
```kotlin
package com.example.offlinewalkietalkie.mesh

import java.util.concurrent.ConcurrentHashMap

data class MeshNeighbor(
    val nodeId: Long,
    val endpointId: String,
    val linkQuality: Int = 100, // 0-100%
    val isRelay: Boolean = false,
    val lastSeenEpochMs: Long = System.currentTimeMillis()
)

data class MeshRoute(
    val destinationNodeId: Long,
    val nextHopNodeId: Long,
    val nextHopEndpointId: String,
    val hopCount: Int,
    val expiresEpochMs: Long
)

/**
 * Section 1 & Section 3 Bounded Forwarding Tree and Route Table.
 *
 * Enforces:
 * - Maximum hop limit: 3 to 5 hops (§2 release targets).
 * - Bounded forwarding tree maintenance.
 * - Route expiry and next-hop selection (avoiding echoing back to ingress link).
 */
class MeshRouteTable(
    val maxHopLimit: Int = 3
) {
    companion object {
        const val DEFAULT_ROUTE_TTL_MS = 30_000L // 30s route expiry
    }

    // Key: endpointId -> MeshNeighbor
    private val neighbors = ConcurrentHashMap<String, MeshNeighbor>()

    // Key: destinationNodeId -> MeshRoute
    private val routes = ConcurrentHashMap<Long, MeshRoute>()

    fun addOrUpdateNeighbor(
        nodeId: Long,
        endpointId: String,
        linkQuality: Int = 100,
        isRelay: Boolean = false
    ) {
        val neighbor = MeshNeighbor(
            nodeId = nodeId,
            endpointId = endpointId,
            linkQuality = linkQuality,
            isRelay = isRelay,
            lastSeenEpochMs = System.currentTimeMillis()
        )
        neighbors[endpointId] = neighbor

        // Immediate direct neighbor is always a 1-hop route
        addRoute(
            destinationNodeId = nodeId,
            nextHopNodeId = nodeId,
            nextHopEndpointId = endpointId,
            hopCount = 1
        )
    }

    fun removeNeighbor(endpointId: String) {
        val neighbor = neighbors.remove(endpointId)
        if (neighbor != null) {
            // Invalidate routes using this neighbor as next hop
            val routesToRemove = routes.filter { it.value.nextHopEndpointId == endpointId }.keys
            routesToRemove.forEach { routes.remove(it) }
        }
    }

    fun addRoute(
        destinationNodeId: Long,
        nextHopNodeId: Long,
        nextHopEndpointId: String,
        hopCount: Int,
        ttlMs: Long = DEFAULT_ROUTE_TTL_MS
    ): Boolean {
        // Enforce maximum bounded hop limit (§2)
        if (hopCount > maxHopLimit) {
            return false
        }

        val existing = routes[destinationNodeId]
        if (existing == null || hopCount <= existing.hopCount || System.currentTimeMillis() > existing.expiresEpochMs) {
            routes[destinationNodeId] = MeshRoute(
                destinationNodeId = destinationNodeId,
                nextHopNodeId = nextHopNodeId,
                nextHopEndpointId = nextHopEndpointId,
                hopCount = hopCount,
                expiresEpochMs = System.currentTimeMillis() + ttlMs
            )
            return true
        }
        return false
    }

    fun getNextHop(destinationNodeId: Long): MeshRoute? {
        pruneExpiredRoutes()
        return routes[destinationNodeId]
    }

    /**
     * Next hops for distributing broadcast channel traffic down the bounded forwarding tree.
     * Excludes the ingress link to prevent loops / echoes.
     */
    fun getForwardingNextHops(excludeEndpointId: String? = null): List<String> {
        return neighbors.values
            .map { it.endpointId }
            .filter { excludeEndpointId == null || it != excludeEndpointId }
    }

    fun getNeighborByEndpoint(endpointId: String): MeshNeighbor? = neighbors[endpointId]

    fun getAllNeighbors(): List<MeshNeighbor> = neighbors.values.toList()

    fun pruneExpiredRoutes() {
        val now = System.currentTimeMillis()
        val expired = routes.filter { it.value.expiresEpochMs <= now }.keys
        expired.forEach { routes.remove(it) }
    }

    fun clear() {
        neighbors.clear()
        routes.clear()
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/mesh/RoutingManager.kt`
```kotlin
package com.example.offlinewalkietalkie.mesh

import android.util.Log
import com.example.offlinewalkietalkie.protocol.Frame
import com.example.offlinewalkietalkie.protocol.FrameCodec
import com.example.offlinewalkietalkie.transport.TransportManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.Collections

class RoutingManager(private val transport: TransportManager) {

    companion object {
        private const val TAG = "TL/Routing"
    }

    private val _incomingFrames = MutableSharedFlow<Pair<String, Frame>>(extraBufferCapacity = 100)
    val incomingFrames = _incomingFrames.asSharedFlow()

    private val seenFramesCache = Collections.newSetFromMap(object : java.util.LinkedHashMap<Int, Boolean>(200, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Boolean>?): Boolean {
            return size > 1000
        }
    })

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var totalRawReceived = 0
    private var totalDecoded = 0
    private var totalDeduped = 0
    private var totalEmitted = 0

    init {
        transport.incomingFrames().onEach { incoming ->
            totalRawReceived++
            if (totalRawReceived == 1 || totalRawReceived % 50 == 0) {
                Log.i(TAG, "raw frame #$totalRawReceived from ${incoming.peerId} size=${incoming.bytes.size}B")
            }

            val decodedFrame = FrameCodec.decode(incoming.bytes)
            if (decodedFrame == null) {
                Log.w(TAG, "decode FAILED for frame from ${incoming.peerId} size=${incoming.bytes.size}")
                return@onEach
            }
            totalDecoded++
            
            val frameHash = generateLogicalHash(decodedFrame)
            
            synchronized(seenFramesCache) {
                if (seenFramesCache.contains(frameHash)) {
                    totalDeduped++
                    if (totalDeduped <= 5 || totalDeduped % 50 == 0) {
                        Log.w(TAG, "DEDUP drop #$totalDeduped type=${decodedFrame.type} seq=${decodedFrame.sequence} hash=$frameHash")
                    }
                    return@onEach
                }
                seenFramesCache.add(frameHash)
            }

            totalEmitted++
            if (totalEmitted <= 3 || totalEmitted % 50 == 0) {
                Log.i(TAG, "emit #$totalEmitted type=${decodedFrame.type} seq=${decodedFrame.sequence} encrypted=${decodedFrame.isEncrypted()} payloadSize=${decodedFrame.payload.size}")
            }
            _incomingFrames.tryEmit(Pair(incoming.peerId, decodedFrame))
            
        }.launchIn(scope)
    }

    suspend fun sendTo(peerId: String, frame: Frame) {
        val frameHash = generateLogicalHash(frame)
        synchronized(seenFramesCache) {
            seenFramesCache.add(frameHash)
        }
        transport.send(peerId, frame.toBytes())
    }

    fun markAsSeen(frame: Frame) {
        val hash = generateLogicalHash(frame)
        synchronized(seenFramesCache) {
            seenFramesCache.add(hash)
        }
    }

    fun generateLogicalHash(frame: Frame): Int {
        var result = frame.sessionId.hashCode()
        result = 31 * result + frame.sequence
        result = 31 * result + frame.timestamp
        result = 31 * result + frame.type.hashCode()
        return result
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/mesh/SeenPacketCache.kt`
```kotlin
package com.example.offlinewalkietalkie.mesh

import java.util.Collections
import java.util.LinkedHashMap

/**
 * Section 19.1 & Section 19.6 Mesh Deduplication Cache.
 *
 * Tracks observed (memberId, sequence) tuples to prevent loops and duplicate packet forwarding.
 * Bounded to 1,000 entries with LRU eviction.
 */
class SeenPacketCache(val maxCapacity: Int = 1000) {

    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<Long, Long>(maxCapacity, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Long>?): Boolean {
                return size > maxCapacity
            }
        }
    )

    /**
     * Generate unique 64-bit key from memberId and sequence.
     */
    private fun generateKey(memberId: Long, sequence: Int): Long {
        return memberId xor ((sequence.toLong() and 0xFFFFFFFFL) shl 32)
    }

    /**
     * Checks if packet has been seen before.
     * If not seen, marks it as seen and returns false.
     * If already seen, returns true.
     */
    fun isDuplicate(memberId: Long, sequence: Int): Boolean {
        val key = generateKey(memberId, sequence)
        synchronized(cache) {
            if (cache.containsKey(key)) {
                return true
            }
            cache[key] = System.currentTimeMillis()
            return false
        }
    }

    fun markSeen(memberId: Long, sequence: Int) {
        val key = generateKey(memberId, sequence)
        cache[key] = System.currentTimeMillis()
    }

    fun size(): Int = cache.size

    fun clear() {
        cache.clear()
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/protocol/FloorController.kt`
```kotlin
package com.example.offlinewalkietalkie.protocol

import com.example.offlinewalkietalkie.voice.BufferedAudioFrame
import com.example.offlinewalkietalkie.voice.PendingFloorBuffer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SessionFloorMode {
    DIRECT_1_TO_1,
    CHANNEL_HOST_CONTROLLED
}

/**
 * Section 6 & Section 19.3/19.4 PTT Floor Controller.
 *
 * Handles:
 * - Direct 1:1 optimistic floor control with deterministic collision resolution by lower memberId.
 * - Host-controlled channel floor with 500 ms PendingFloorBuffer (zero clipped syllables).
 * - 60-second safety timeout.
 * - Local-only tone feedback cues.
 */
class FloorController(
    val localMemberId: Long,
    var mode: SessionFloorMode = SessionFloorMode.DIRECT_1_TO_1,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    companion object {
        const val COLLISION_WINDOW_MS = 300L
        const val SAFETY_TIMEOUT_MS = 60_000L // 60s safety timeout (§6 & §19)
    }

    private val _floorState = MutableStateFlow<FloorState>(FloorState.Idle)
    val floorState: StateFlow<FloorState> = _floorState.asStateFlow()

    val pendingBuffer = PendingFloorBuffer()

    private var safetyTimerJob: Job? = null
    private var lastPressTimestampMs: Long = 0L

    var onTransmitPendingFrames: ((List<BufferedAudioFrame>) -> Unit)? = null
    var onPlayBusyTone: (() -> Unit)? = null
    var onPlayPermissionChirp: (() -> Unit)? = null
    var onFloorReleased: (() -> Unit)? = null

    /**
     * User pressed the PTT control.
     */
    fun requestPtt() {
        val current = _floorState.value
        if (current !is FloorState.Idle) return

        lastPressTimestampMs = System.currentTimeMillis()

        if (mode == SessionFloorMode.DIRECT_1_TO_1) {
            // Direct 1:1 is optimistic: capture begins immediately
            _floorState.value = FloorState.Talking
            onPlayPermissionChirp?.invoke()
            startSafetyTimeout()
        } else {
            // Channel: request floor from host and buffer up to 500ms locally
            _floorState.value = FloorState.Requesting
            pendingBuffer.clear()
        }
    }

    /**
     * Record captured audio frame while requesting or talking.
     */
    fun onAudioCaptured(sequence: Int, timestampMs: Long, audioData: ByteArray): Boolean {
        when (_floorState.value) {
            is FloorState.Requesting -> {
                pendingBuffer.push(sequence, timestampMs, audioData)
                return false // Not sent live yet
            }
            is FloorState.Talking, is FloorState.Granted, is FloorState.HeldByMe -> {
                return true // Sent live
            }
            else -> return false
        }
    }

    /**
     * Direct 1:1 Collision Resolution (§19.3):
     * If peer frame / request received within collision window:
     * Lower memberId wins. Loser yields floor and plays local busy tone.
     *
     * @return true if local won, false if local lost
     */
    fun handleDirectCollision(peerMemberId: Long, peerPressTimestampMs: Long): Boolean {
        if (_floorState.value !is FloorState.Talking && _floorState.value !is FloorState.HeldByMe) {
            return false
        }

        val elapsedSinceLocalPress = System.currentTimeMillis() - lastPressTimestampMs
        if (elapsedSinceLocalPress <= COLLISION_WINDOW_MS) {
            val localWins = java.lang.Long.compareUnsigned(localMemberId, peerMemberId) < 0
            if (!localWins) {
                // Yield floor
                cancelSafetyTimeout()
                pendingBuffer.clear()
                _floorState.value = FloorState.Denied("Direct collision resolved to lower memberId")
                onPlayBusyTone?.invoke()
                _floorState.value = FloorState.Idle
                onFloorReleased?.invoke()
                return false
            }
            return true
        }
        return true
    }

    /**
     * Host granted floor in channel mode.
     */
    fun onFloorGranted() {
        if (_floorState.value is FloorState.Requesting) {
            _floorState.value = FloorState.Granted
            onPlayPermissionChirp?.invoke()

            // Drain buffered audio and transmit before live audio
            val bufferedFrames = pendingBuffer.drain()
            if (bufferedFrames.isNotEmpty()) {
                onTransmitPendingFrames?.invoke(bufferedFrames)
            }

            _floorState.value = FloorState.Talking
            startSafetyTimeout()
        }
    }

    /**
     * Host denied floor in channel mode.
     */
    fun onFloorDenied(reason: String = "Floor busy") {
        if (_floorState.value is FloorState.Requesting) {
            cancelSafetyTimeout()
            pendingBuffer.clear()
            _floorState.value = FloorState.Denied(reason)
            onPlayBusyTone?.invoke()
            _floorState.value = FloorState.Idle
        }
    }

    /**
     * User released the PTT control.
     */
    fun releasePtt() {
        val current = _floorState.value
        if (current is FloorState.Talking || current is FloorState.Granted || current is FloorState.HeldByMe || current is FloorState.Requesting) {
            cancelSafetyTimeout()
            pendingBuffer.clear()
            _floorState.value = FloorState.Releasing
            _floorState.value = FloorState.Idle
            onFloorReleased?.invoke()
        }
    }

    /**
     * Another member is holding the floor.
     */
    fun onFloorHeldByOther(peerId: String, peerName: String) {
        if (_floorState.value !is FloorState.Talking && _floorState.value !is FloorState.HeldByMe) {
            cancelSafetyTimeout()
            pendingBuffer.clear()
            _floorState.value = FloorState.HeldByOther(peerId, peerName)
        }
    }

    /**
     * Floor released by other member.
     */
    fun onFloorFreed() {
        val current = _floorState.value
        if (current is FloorState.HeldByOther) {
            _floorState.value = FloorState.Idle
        }
    }

    private fun startSafetyTimeout() {
        cancelSafetyTimeout()
        safetyTimerJob = scope.launch {
            delay(SAFETY_TIMEOUT_MS)
            // 60s safety timeout reached: auto-release
            releasePtt()
        }
    }

    private fun cancelSafetyTimeout() {
        safetyTimerJob?.cancel()
        safetyTimerJob = null
    }

    fun reset() {
        cancelSafetyTimeout()
        pendingBuffer.clear()
        _floorState.value = FloorState.Idle
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/protocol/FloorState.kt`
```kotlin
package com.example.offlinewalkietalkie.protocol

/**
 * PTT Floor State Machine per Section 6 & Section 19:
 * IDLE -> REQUESTING -> GRANTED -> TALKING -> RELEASING -> IDLE
 *                 \-> DENIED -> IDLE
 */
sealed class FloorState {
    object Idle : FloorState()
    object Requesting : FloorState()
    object Granted : FloorState()
    object Talking : FloorState()
    object HeldByMe : FloorState() // Alias/compatible with Talking for existing UI
    data class Denied(val reason: String = "") : FloorState()
    object Releasing : FloorState()
    data class HeldByOther(val peerId: String, val peerName: String) : FloorState()
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/protocol/Frame.kt`
```kotlin
package com.example.offlinewalkietalkie.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class FrameType(val value: Byte) {
    HELLO(0),
    HELLO_ACK(1),
    AUTH(2),
    AUTH_ACK(3),
    SESSION_INIT(4),
    SESSION_JOIN(5),
    SESSION_READY(6),
    FLOOR_REQUEST(7),
    FLOOR_GRANT(8),
    FLOOR_RELEASE(9),
    VOICE(10),
    PING(11),
    PONG(12),
    ROUTE_ADVERTISE(13),
    ROUTE_REQUEST(14),
    ROUTE_RESPONSE(15),
    LEAVE(16),
    ERROR(17),
    FLOOR_DENY(18),
    FLOOR_STATUS(19),
    KEY_UPDATE(20),
    TEXT(21),
    ACK(22),
    TREE_PLAN(23),
    TREE_ACK(24),
    GROUP_CONTROL(25);

    companion object {
        fun fromValue(value: Byte): FrameType? = values().find { it.value == value }
    }
}

data class Frame(
    val version: Byte = 1,
    val type: FrameType,
    val flags: Byte = 0,
    var hopLimit: Byte = 3,
    val sessionId: Long = 0,
    val sequence: Int = 0,
    val timestamp: Int = 0,
    val payload: ByteArray = ByteArray(0),
    val authTag: ByteArray? = null
) {
    companion object {
        const val MAGIC: Short = 0x5754 // "WT"
        const val HEADER_SIZE = 2 + 1 + 1 + 1 + 1 + 8 + 4 + 4 + 2
        const val FLAG_ENCRYPTED: Byte = 0x01
    }

    fun isEncrypted(): Boolean = (flags.toInt() and FLAG_ENCRYPTED.toInt()) != 0

    fun toBytes(): ByteArray {
        val totalSize = HEADER_SIZE + payload.size + (authTag?.size ?: 0)
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        
        buffer.putShort(MAGIC)
        buffer.put(version)
        buffer.put(type.value)
        buffer.put(flags)
        buffer.put(hopLimit)
        buffer.putLong(sessionId)
        buffer.putInt(sequence)
        buffer.putInt(timestamp)
        buffer.putShort(payload.size.toShort())
        buffer.put(payload)
        authTag?.let { buffer.put(it) }
        
        return buffer.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Frame

        if (version != other.version) return false
        if (type != other.type) return false
        if (flags != other.flags) return false
        if (hopLimit != other.hopLimit) return false
        if (sessionId != other.sessionId) return false
        if (sequence != other.sequence) return false
        if (timestamp != other.timestamp) return false
        if (!payload.contentEquals(other.payload)) return false
        if (authTag != null) {
            if (other.authTag == null) return false
            if (!authTag.contentEquals(other.authTag)) return false
        } else if (other.authTag != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version.toInt()
        result = 31 * result + type.hashCode()
        result = 31 * result + flags
        result = 31 * result + hopLimit
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + sequence
        result = 31 * result + timestamp
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + (authTag?.contentHashCode() ?: 0)
        return result
    }
}

object FrameCodec {
    fun decode(bytes: ByteArray): Frame? {
        if (bytes.size < Frame.HEADER_SIZE) return null
        
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        if (buffer.short != Frame.MAGIC) return null
        
        val version = buffer.get()
        if (version != 1.toByte()) return null
        
        val typeByte = buffer.get()
        val type = FrameType.fromValue(typeByte) ?: return null
        
        val flags = buffer.get()
        val isEncrypted = (flags.toInt() and Frame.FLAG_ENCRYPTED.toInt()) != 0
        
        val hopLimit = buffer.get()
        val sessionId = buffer.getLong()
        val sequence = buffer.getInt()
        val timestamp = buffer.getInt()
        val payloadLen = buffer.short.toInt() and 0xFFFF
        
        if (buffer.remaining() < payloadLen) return null
        val payload = ByteArray(payloadLen)
        buffer.get(payload)
        
        val authTag = if (isEncrypted && buffer.remaining() >= 16) {
            val tag = ByteArray(16)
            buffer.get(tag)
            tag
        } else null
        
        return Frame(
            version = version,
            type = type,
            flags = flags,
            hopLimit = hopLimit,
            sessionId = sessionId,
            sequence = sequence,
            timestamp = timestamp,
            payload = payload,
            authTag = authTag
        )
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/protocol/TwoLayerCodec.kt`
```kotlin
package com.example.offlinewalkietalkie.protocol

import com.example.offlinewalkietalkie.crypto.TinkCryptoEngine

/**
 * TwoLayerCodec: Handles cryptographic sealing and unsealing of the
 * hop-by-hop OuterPacket and end-to-end InnerPacket per Section 19.1.
 */
class TwoLayerCodec(private val cryptoEngine: TinkCryptoEngine = TinkCryptoEngine()) {

    /**
     * Seals an InnerPacket into an OuterPacket for transmission over the local radio link.
     */
    fun pack(
        innerPacket: InnerPacket,
        channelKey: ByteArray,
        hopKey: ByteArray,
        senderNodeId: Long
    ): ByteArray {
        // 1. Serialize inner packet plaintext
        val innerPlaintext = innerPacket.encode()

        // 2. End-to-end AEAD encryption with channelKey
        val encryptedInner = cryptoEngine.encrypt(
            key = channelKey,
            nonce = cryptoEngine.generateNonce(),
            plaintext = innerPlaintext
        )

        // 3. Construct OuterPacket
        val outerNonce = cryptoEngine.generateNonce()
        // Encrypt the encryptedInner payload with hopKey
        val encryptedOuterWithTag = cryptoEngine.encrypt(
            key = hopKey,
            nonce = outerNonce,
            plaintext = encryptedInner,
            associatedData = byteArrayOf((senderNodeId ushr 56).toByte(), (senderNodeId ushr 48).toByte())
        )

        // Extract 12-byte nonce (if prepended by Tink), ciphertext, and 16-byte auth tag
        // Tink ChaCha20Poly1305 encrypt output structure: [12-byte nonce][ciphertext][16-byte tag]
        val nonce = encryptedOuterWithTag.copyOfRange(0, 12)
        val ciphertext = encryptedOuterWithTag.copyOfRange(12, encryptedOuterWithTag.size - 16)
        val tag = encryptedOuterWithTag.copyOfRange(encryptedOuterWithTag.size - 16, encryptedOuterWithTag.size)

        val outerPacket = OuterPacket(
            senderNodeId = senderNodeId,
            hopLimit = innerPacket.hopLimit,
            nonce = nonce,
            ciphertext = ciphertext,
            authTag = tag
        )

        return outerPacket.encode()
    }

    /**
     * Decrypts the OuterPacket hop-by-hop layer using hopKey.
     * Intermediate mesh relay nodes can decrypt this without having channelKey.
     */
    fun unpackOuter(
        wireBytes: ByteArray,
        hopKey: ByteArray
    ): Pair<Long, ByteArray>? {
        val decoded = decodeOuter(wireBytes, hopKey) ?: return null
        return Pair(decoded.senderNodeId, decoded.innerCiphertext)
    }

    data class DecodedOuter(
        val senderNodeId: Long,
        val hopLimit: Byte,
        val innerCiphertext: ByteArray
    )

    fun decodeOuter(
        wireBytes: ByteArray,
        hopKey: ByteArray
    ): DecodedOuter? {
        val outerPacket = OuterPacket.decode(wireBytes) ?: return null

        val combinedForTink = outerPacket.nonce + outerPacket.ciphertext + outerPacket.authTag
        val associatedData = byteArrayOf((outerPacket.senderNodeId ushr 56).toByte(), (outerPacket.senderNodeId ushr 48).toByte())

        return try {
            val innerCiphertext = cryptoEngine.decrypt(
                key = hopKey,
                ciphertextWithNonceAndTag = combinedForTink,
                associatedData = associatedData
            )
            DecodedOuter(
                senderNodeId = outerPacket.senderNodeId,
                hopLimit = outerPacket.hopLimit,
                innerCiphertext = innerCiphertext
            )
        } catch (e: Throwable) {
            null // Authentication tag failure or corrupted frame
        }
    }

    /**
     * Decrypts the InnerPacket end-to-end layer using channelKey.
     */
    fun unpackInner(
        innerCiphertext: ByteArray,
        channelKey: ByteArray
    ): InnerPacket? {
        return try {
            val decryptedBytes = cryptoEngine.decrypt(
                key = channelKey,
                ciphertextWithNonceAndTag = innerCiphertext
            )
            InnerPacket.decode(decryptedBytes)
        } catch (e: Throwable) {
            null // Decryption failed or tag mismatch
        }
    }

    /**
     * Re-encrypts the inner ciphertext under a new hop key for the next link in a multi-hop mesh chain.
     * Intermediate relay nodes call this without possessing the end-to-end channelKey (§19.1 & §19.2).
     */
    fun rewrapOuter(
        innerCiphertext: ByteArray,
        newHopKey: ByteArray,
        newSenderNodeId: Long,
        hopLimit: Byte = 3
    ): ByteArray {
        val outerNonce = cryptoEngine.generateNonce()
        val encryptedOuterWithTag = cryptoEngine.encrypt(
            key = newHopKey,
            nonce = outerNonce,
            plaintext = innerCiphertext,
            associatedData = byteArrayOf((newSenderNodeId ushr 56).toByte(), (newSenderNodeId ushr 48).toByte())
        )

        val nonce = encryptedOuterWithTag.copyOfRange(0, 12)
        val ciphertext = encryptedOuterWithTag.copyOfRange(12, encryptedOuterWithTag.size - 16)
        val tag = encryptedOuterWithTag.copyOfRange(encryptedOuterWithTag.size - 16, encryptedOuterWithTag.size)

        val outerPacket = OuterPacket(
            senderNodeId = newSenderNodeId,
            hopLimit = hopLimit,
            nonce = nonce,
            ciphertext = ciphertext,
            authTag = tag
        )

        return outerPacket.encode()
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/protocol/TwoLayerPacket.kt`
```kotlin
package com.example.offlinewalkietalkie.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Inner Packet: End-to-End payload between channel members or 1:1 endpoints.
 * Conforms to Section 19.1 & Section 19.3 (Session-scoped 8-byte member ID).
 */
data class InnerPacket(
    val type: FrameType,
    val flags: Byte = 0,
    var hopLimit: Byte = 3,
    val channelId: Long = 0,
    val memberId: Long = 0, // Session-scoped random 8-byte ID (§19.3)
    val sequence: Int = 0,
    val timestamp: Int = 0,
    val payload: ByteArray = ByteArray(0),
    val authTag: ByteArray? = null
) {
    companion object {
        const val HEADER_SIZE = 1 + 1 + 1 + 8 + 8 + 4 + 4 + 2 // 29 bytes
        const val FLAG_ENCRYPTED: Byte = 0x01
        const val FLAG_EMERGENCY_SOS: Byte = 0x02
        const val FLAG_PRIORITY: Byte = 0x04

        fun decode(bytes: ByteArray): InnerPacket? {
            if (bytes.size < HEADER_SIZE) return null
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

            val typeByte = buf.get()
            val type = FrameType.fromValue(typeByte) ?: return null
            val flags = buf.get()
            val hopLimit = buf.get()
            val channelId = buf.getLong()
            val memberId = buf.getLong()
            val sequence = buf.getInt()
            val timestamp = buf.getInt()
            val payloadLen = buf.short.toInt() and 0xFFFF

            if (buf.remaining() < payloadLen) return null
            val payload = ByteArray(payloadLen)
            buf.get(payload)

            val isEncrypted = (flags.toInt() and FLAG_ENCRYPTED.toInt()) != 0
            val authTag = if (isEncrypted && buf.remaining() >= 16) {
                val tag = ByteArray(16)
                buf.get(tag)
                tag
            } else null

            return InnerPacket(
                type = type,
                flags = flags,
                hopLimit = hopLimit,
                channelId = channelId,
                memberId = memberId,
                sequence = sequence,
                timestamp = timestamp,
                payload = payload,
                authTag = authTag
            )
        }
    }

    fun isEncrypted(): Boolean = (flags.toInt() and FLAG_ENCRYPTED.toInt()) != 0
    fun isEmergency(): Boolean = (flags.toInt() and FLAG_EMERGENCY_SOS.toInt()) != 0

    fun encode(): ByteArray {
        val totalSize = HEADER_SIZE + payload.size + (authTag?.size ?: 0)
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

        buf.put(type.value)
        buf.put(flags)
        buf.put(hopLimit)
        buf.putLong(channelId)
        buf.putLong(memberId)
        buf.putInt(sequence)
        buf.putInt(timestamp)
        buf.putShort(payload.size.toShort())
        buf.put(payload)
        authTag?.let { buf.put(it) }

        return buf.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as InnerPacket
        return type == other.type &&
                flags == other.flags &&
                hopLimit == other.hopLimit &&
                channelId == other.channelId &&
                memberId == other.memberId &&
                sequence == other.sequence &&
                timestamp == other.timestamp &&
                payload.contentEquals(other.payload) &&
                (authTag?.contentEquals(other.authTag ?: ByteArray(0)) ?: (other.authTag == null))
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + flags
        result = 31 * result + hopLimit
        result = 31 * result + channelId.hashCode()
        result = 31 * result + memberId.hashCode()
        result = 31 * result + sequence
        result = 31 * result + timestamp
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + (authTag?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Outer Packet: Hop-by-Hop framing encrypted between immediate radio neighbors.
 * Conforms to Section 19.1.
 */
data class OuterPacket(
    val senderNodeId: Long,
    var hopLimit: Byte = 3,
    val nonce: ByteArray, // 12-byte nonce
    val ciphertext: ByteArray, // Encrypted inner packet
    val authTag: ByteArray // 16-byte Poly1305 / GCM tag
) {
    companion object {
        const val MAGIC: Short = 0x5403 // "T\x03" (TalkLoop V3)
        const val HEADER_SIZE = 2 + 2 + 8 + 1 + 12 // 25 bytes (magic + length + senderNodeId + hopLimit + nonce)
        const val TAG_SIZE = 16

        fun decode(bytes: ByteArray): OuterPacket? {
            if (bytes.size < HEADER_SIZE + TAG_SIZE) return null
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

            val magic = buf.short
            if (magic != MAGIC) return null

            val length = buf.short.toInt() and 0xFFFF
            if (bytes.size < length) return null

            val senderNodeId = buf.getLong()
            val hopLimit = buf.get()
            val nonce = ByteArray(12)
            buf.get(nonce)

            val ciphertextSize = length - HEADER_SIZE - TAG_SIZE
            if (ciphertextSize < 0 || buf.remaining() < ciphertextSize + TAG_SIZE) return null

            val ciphertext = ByteArray(ciphertextSize)
            buf.get(ciphertext)

            val authTag = ByteArray(TAG_SIZE)
            buf.get(authTag)

            return OuterPacket(
                senderNodeId = senderNodeId,
                hopLimit = hopLimit,
                nonce = nonce,
                ciphertext = ciphertext,
                authTag = authTag
            )
        }
    }

    fun encode(): ByteArray {
        val totalSize = HEADER_SIZE + ciphertext.size + authTag.size
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

        buf.putShort(MAGIC)
        buf.putShort(totalSize.toShort())
        buf.putLong(senderNodeId)
        buf.put(hopLimit)
        buf.put(nonce)
        buf.put(ciphertext)
        buf.put(authTag)

        return buf.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OuterPacket
        return senderNodeId == other.senderNodeId &&
                nonce.contentEquals(other.nonce) &&
                ciphertext.contentEquals(other.ciphertext) &&
                authTag.contentEquals(other.authTag)
    }

    override fun hashCode(): Int {
        var result = senderNodeId.hashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + authTag.contentHashCode()
        return result
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/service/NotificationController.kt`
```kotlin
package com.example.offlinewalkietalkie.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.offlinewalkietalkie.MainActivity

/**
 * Section 12 Notification Controller.
 * Manages the ongoing active-session notification with:
 * - Session/channel name
 * - Security / Reconnect status
 * - Current speaker
 * - Quick actions: Open, Talk, Leave
 */
class NotificationController(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "talkloop_active_session"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.offlinewalkietalkie.ACTION_START"
        const val ACTION_STOP = "com.example.offlinewalkietalkie.ACTION_STOP"
        const val ACTION_TOGGLE_PTT = "com.example.offlinewalkietalkie.ACTION_TOGGLE_PTT"
        const val ACTION_LEAVE = "com.example.offlinewalkietalkie.ACTION_LEAVE"

        const val EXTRA_SESSION_NAME = "extra_session_name"
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_SPEAKER = "extra_speaker"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Walkie-Talkie Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing offline walkie-talkie communication"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        sessionName: String = "Field Ops Alpha",
        status: String = "Encrypted (SAS Verified)",
        speaker: String = "Floor: Idle"
    ): Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val talkIntent = Intent(context, WalkieTalkieForegroundService::class.java).apply {
            action = ACTION_TOGGLE_PTT
        }
        val talkPendingIntent = PendingIntent.getService(
            context,
            1,
            talkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val leaveIntent = Intent(context, WalkieTalkieForegroundService::class.java).apply {
            action = ACTION_LEAVE
        }
        val leavePendingIntent = PendingIntent.getService(
            context,
            2,
            leaveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(sessionName)
            .setContentText("$status • $speaker")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "Open", openPendingIntent)
            .addAction(android.R.drawable.ic_btn_speak_now, "Talk", talkPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Leave", leavePendingIntent)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    fun update(sessionName: String, status: String, speaker: String) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(sessionName, status, speaker))
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/service/WalkieTalkieForegroundService.kt`
```kotlin
package com.example.offlinewalkietalkie.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/**
 * Section 12 Persistent Foreground Service.
 *
 * Runs during an active walkie-talkie session or channel:
 * - Declares foregroundServiceType="microphone|connectedDevice"
 * - Maintains CPU partial wake lock to allow screen-off voice reception (§12)
 * - Exposes ongoing notification with Open, Talk, Leave actions
 * - Stops immediately when the session ends
 */
class WalkieTalkieForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isRunning = false
    private lateinit var notificationController: NotificationController

    companion object {
        var onPttToggleFromNotification: (() -> Unit)? = null
        var onLeaveFromNotification: (() -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()
        notificationController = NotificationController(this)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "TalkLoop::ActiveSessionWakeLock"
        ).apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            NotificationController.ACTION_STOP -> {
                stopServiceInternal()
                return START_NOT_STICKY
            }
            NotificationController.ACTION_TOGGLE_PTT -> {
                onPttToggleFromNotification?.invoke()
                return START_STICKY
            }
            NotificationController.ACTION_LEAVE -> {
                onLeaveFromNotification?.invoke()
                stopServiceInternal()
                return START_NOT_STICKY
            }
            NotificationController.ACTION_START, null -> {
                startServiceInternal(intent)
            }
        }
        return START_STICKY
    }

    private fun startServiceInternal(intent: Intent?) {
        val sessionName = intent?.getStringExtra(NotificationController.EXTRA_SESSION_NAME)
            ?: "Field Ops Alpha"
        val status = intent?.getStringExtra(NotificationController.EXTRA_STATUS)
            ?: "Encrypted (SAS Verified)"
        val speaker = intent?.getStringExtra(NotificationController.EXTRA_SPEAKER)
            ?: "Floor: Idle"

        val notification = notificationController.buildNotification(sessionName, status, speaker)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            val hasRecordAudio = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasRecordAudio) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            try {
                startForeground(NotificationController.NOTIFICATION_ID, notification, serviceType)
            } catch (e: Exception) {
                try {
                    startForeground(NotificationController.NOTIFICATION_ID, notification)
                } catch (e2: Exception) {}
            }
        } else {
            try {
                startForeground(NotificationController.NOTIFICATION_ID, notification)
            } catch (e: Exception) {}
        }

        if (!isRunning) {
            isRunning = true
            try {
                // No timeout: a session is call-like and may outlast any fixed limit. A
                // 10-minute cap silently ended screen-off reception mid-session. It is
                // always released in stopServiceInternal().
                wakeLock?.acquire()
            } catch (e: Exception) {
                // Ignore wake lock acquire failures
            }
        }
    }

    private fun stopServiceInternal() {
        isRunning = false
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {}

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopServiceInternal()
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/session/ReconnectManager.kt`
```kotlin
package com.example.offlinewalkietalkie.session

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class ReconnectState {
    object Idle : ReconnectState()
    object Connected : ReconnectState()
    data class Reconnecting(val attempt: Int, val maxAttempts: Int, val nextRetryMs: Long) : ReconnectState()
    data class Failed(val reason: String) : ReconnectState()
}

/**
 * Section 3 & Section 13 Reconnect Manager with Exponential Backoff.
 *
 * Rules:
 * - On transient loss, moves to Reconnecting and retries known peer/path with exponential backoff.
 * - An untrusted identity NEVER auto-reconnects (§3, §19.4).
 * - Host disconnect: freezes new floor grants for 10s while reconnecting; ends session cleanly if unreachable (§13).
 */
class ReconnectManager(
    val peerId: String,
    val isTrusted: Boolean,
    val isHost: Boolean,
    private val doReconnect: suspend (peerId: String) -> Boolean,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    companion object {
        val BACKOFF_DELAYS_MS = listOf(1000L, 2000L, 4000L, 8000L, 16000L)
        const val MAX_ATTEMPTS = 5
        const val HOST_DISCONNECT_GRACE_PERIOD_MS = 10_000L // 10s grace period (§13)
    }

    private val _state = MutableStateFlow<ReconnectState>(ReconnectState.Connected)
    val state: StateFlow<ReconnectState> = _state.asStateFlow()

    private var reconnectJob: Job? = null
    var onSessionTerminated: ((reason: String) -> Unit)? = null
    var onFloorGrantsFrozen: ((frozen: Boolean) -> Unit)? = null

    /**
     * Called when connection is lost.
     */
    fun onConnectionLost() {
        // Untrusted peer never auto-reconnects (§3 & §19.4)
        if (!isTrusted) {
            _state.value = ReconnectState.Failed("Untrusted identity cannot auto-reconnect")
            onSessionTerminated?.invoke("Connection lost with untrusted identity")
            return
        }

        if (isHost) {
            // Host disconnected: freeze floor grants for 10 seconds (§13)
            onFloorGrantsFrozen?.invoke(true)
        }

        startReconnectLoop()
    }

    private fun startReconnectLoop() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            for (attempt in 1..MAX_ATTEMPTS) {
                val delayMs = BACKOFF_DELAYS_MS.getOrElse(attempt - 1) { 16000L }
                _state.value = ReconnectState.Reconnecting(
                    attempt = attempt,
                    maxAttempts = MAX_ATTEMPTS,
                    nextRetryMs = delayMs
                )

                delay(delayMs)

                val success = try {
                    doReconnect(peerId)
                } catch (e: Exception) {
                    false
                }

                if (success) {
                    _state.value = ReconnectState.Connected
                    if (isHost) {
                        onFloorGrantsFrozen?.invoke(false)
                    }
                    return@launch
                }
            }

            // All attempts exhausted
            val failureReason = if (isHost) "Host unreachable after 10s grace period" else "Peer unreachable after 5 attempts"
            _state.value = ReconnectState.Failed(failureReason)
            onSessionTerminated?.invoke(failureReason)
        }
    }

    fun onConnected() {
        reconnectJob?.cancel()
        _state.value = ReconnectState.Connected
        if (isHost) {
            onFloorGrantsFrozen?.invoke(false)
        }
    }

    fun reset() {
        reconnectJob?.cancel()
        _state.value = ReconnectState.Idle
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/testutil/FakeTransport.kt`
```kotlin
package com.example.offlinewalkietalkie.testutil

import com.example.offlinewalkietalkie.transport.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*

/**
 * FakeTransport for Stage 3 unit & integration tests per Section 4 & Section 16.
 * Simulates peer discovery, handshake requests, packet transmission, and link drops.
 */
class FakeTransport(val localId: String = "fake_local") : Transport {

    private val _state = MutableStateFlow(TransportState.IDLE)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    override val peers: Flow<List<Peer>> = _peers.map { it.values.toList() }

    private val _connectionRequests = MutableSharedFlow<ConnectionRequest>(
        replay = 10,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val connectionRequests: Flow<ConnectionRequest> = _connectionRequests.asSharedFlow()

    private val _incomingFrames = MutableSharedFlow<IncomingFrame>(
        replay = 10,
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override fun incomingFrames(): Flow<IncomingFrame> = _incomingFrames.asSharedFlow()

    val sentPackets = mutableListOf<Pair<String, ByteArray>>()
    var linkedPeerTransport: FakeTransport? = null

    fun linkTo(other: FakeTransport) {
        this.linkedPeerTransport = other
        other.linkedPeerTransport = this
    }

    fun simulateDiscoveredPeer(id: String, name: String, isConnected: Boolean = false) {
        _peers.update { it + (id to Peer(id, name, isConnected)) }
    }

    fun simulateConnectionRequest(peerId: String, peerName: String, authCode: String = "123456") {
        _connectionRequests.tryEmit(ConnectionRequest(peerId, peerName, authCode))
    }

    fun simulateIncomingPacket(peerId: String, bytes: ByteArray) {
        _incomingFrames.tryEmit(IncomingFrame(peerId, bytes))
    }

    override suspend fun startAdvertising(identity: LocalIdentity) {
        _state.value = TransportState.ACTIVE
    }

    override suspend fun startDiscovery(identity: LocalIdentity) {
        _state.value = TransportState.ACTIVE
    }

    override suspend fun connect(peerId: String) {
        _peers.update { it + (peerId to Peer(peerId, "Peer-$peerId", isConnected = true)) }
        linkedPeerTransport?.simulateDiscoveredPeer(localId, "Local-$localId", isConnected = true)
    }

    override suspend fun acceptConnection(peerId: String) {
        _peers.update { current ->
            val p = current[peerId] ?: Peer(peerId, "Peer-$peerId", false)
            current + (peerId to p.copy(isConnected = true))
        }
        linkedPeerTransport?._peers?.update { current ->
            val p = current[localId] ?: Peer(localId, "Local-$localId", false)
            current + (localId to p.copy(isConnected = true))
        }
    }

    override suspend fun rejectConnection(peerId: String) {
        _peers.update { it - peerId }
    }

    override suspend fun disconnect(peerId: String) {
        _peers.update { it - peerId }
        linkedPeerTransport?._peers?.update { it - localId }
    }

    override suspend fun send(peerId: String, bytes: ByteArray) {
        sentPackets.add(peerId to bytes.copyOf())
        linkedPeerTransport?.simulateIncomingPacket(localId, bytes.copyOf())
    }

    override suspend fun stop() {
        _state.value = TransportState.IDLE
        _peers.value = emptyMap()
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/theme/Color.kt`
```kotlin
package com.example.offlinewalkietalkie.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Liquid Glass Foundations
val SlateBlue = Color(0xFF587690)
val DeepInk = Color(0xFF0B1020)
val MistLight = Color(0xFFEAF3F7)
val PearlLight = Color(0xFFF6F7FB)

// Luminous Accents
val ElectricAqua = Color(0xFF3BE7D0)
val MintGreen = Color(0xFF51E5B6)
val BrightCyan = Color(0xFF39C9F2)
val WarmAmber = Color(0xFFFFB45C)
val RoseActive = Color(0xFFFF5E77)

// Glass Surfaces
val GlassBackground = Color(0x14FFFFFF) // ~8% White
val GlassBackgroundHover = Color(0x1FFFFFFF) // ~12% White
val GlassBorder = Color(0x2EFFFFFF) // ~18% White
val GlassSubtleBackground = Color(0x0DFFFFFF) // ~5% White

// High-contrast outdoor mode colors
val OutdoorBackground = Color(0xFF0F172A)
val OutdoorCardBackground = Color(0xFF1E293B)
val OutdoorCardBorder = Color(0xFF475569)

// Gradients
val AtmosphericDarkBrush = Brush.radialGradient(
    colors = listOf(SlateBlue, Color(0xFF1A273B), DeepInk),
    radius = 1800f
)

val PttReadyBrush = Brush.radialGradient(
    colors = listOf(MintGreen, BrightCyan, Color(0xFF14729C), Color(0xFF0D4661)),
    radius = 350f
)

val PttTalkingBrush = Brush.radialGradient(
    colors = listOf(Color(0xFFFF7E90), RoseActive, Color(0xFF9C1432)),
    radius = 350f
)
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/theme/Theme.kt`
```kotlin
package com.example.offlinewalkietalkie.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalOutdoorMode = compositionLocalOf { false }

private val LiquidGlassDarkColorScheme = darkColorScheme(
    primary = ElectricAqua,
    onPrimary = DeepInk,
    primaryContainer = SlateBlue,
    onPrimaryContainer = MistLight,
    secondary = MintGreen,
    onSecondary = DeepInk,
    secondaryContainer = Color(0xFF14729C),
    onSecondaryContainer = MistLight,
    tertiary = BrightCyan,
    onTertiary = DeepInk,
    background = DeepInk,
    onBackground = MistLight,
    surface = Color(0xFF121B2B),
    onSurface = MistLight,
    surfaceVariant = Color(0xFF1A273B),
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = RoseActive,
    onError = Color.White
)

private val OutdoorHighContrastColorScheme = darkColorScheme(
    primary = ElectricAqua,
    onPrimary = Color.Black,
    primaryContainer = OutdoorCardBackground,
    onPrimaryContainer = Color.White,
    secondary = MintGreen,
    onSecondary = Color.Black,
    tertiary = WarmAmber,
    onTertiary = Color.Black,
    background = OutdoorBackground,
    onBackground = Color.White,
    surface = OutdoorCardBackground,
    onSurface = Color.White,
    surfaceVariant = OutdoorCardBorder,
    onSurfaceVariant = Color.White,
    error = RoseActive,
    onError = Color.White
)

@Composable
fun OfflineWalkieTalkieTheme(
    outdoorMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (outdoorMode) OutdoorHighContrastColorScheme else LiquidGlassDarkColorScheme

    CompositionLocalProvider(LocalOutdoorMode provides outdoorMode) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/theme/Type.kt`
```kotlin
package com.example.offlinewalkietalkie.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography =
  Typography(
    bodyLarge =
      TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
      )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
  )
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/transport/Transport.kt`
```kotlin
package com.example.offlinewalkietalkie.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class LocalIdentity(val endpointName: String)

data class Peer(val id: String, val name: String, val isConnected: Boolean)

data class IncomingFrame(val peerId: String, val bytes: ByteArray)

data class ConnectionRequest(val peerId: String, val peerName: String, val authCode: String)

enum class TransportState {
    IDLE, ACTIVE, ERROR
}

interface Transport {
    val state: StateFlow<TransportState>
    val peers: Flow<List<Peer>>
    val connectionRequests: Flow<ConnectionRequest>

    /** Human-readable reason the last advertise/discover/connect attempt failed, or null. */
    val lastError: StateFlow<String?> get() = NO_ERROR

    /** True while this device is discoverable by peers. */
    val isAdvertising: StateFlow<Boolean> get() = ALWAYS_FALSE

    /** True while this device is scanning for peers. */
    val isDiscovering: StateFlow<Boolean> get() = ALWAYS_FALSE

    suspend fun startAdvertising(identity: LocalIdentity)
    suspend fun startDiscovery(identity: LocalIdentity)

    /**
     * Stop being discoverable without tearing down live links. Needed to republish under
     * a new endpoint name when group identity or role changes.
     */
    suspend fun stopAdvertising() {}

    suspend fun connect(peerId: String)
    suspend fun acceptConnection(peerId: String)
    suspend fun rejectConnection(peerId: String)
    suspend fun disconnect(peerId: String)

    suspend fun send(peerId: String, bytes: ByteArray)
    fun incomingFrames(): Flow<IncomingFrame>

    suspend fun stop()

    companion object {
        val NO_ERROR: StateFlow<String?> = MutableStateFlow(null)
        val ALWAYS_FALSE: StateFlow<Boolean> = MutableStateFlow(false)
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/transport/TransportManager.kt`
```kotlin
package com.example.offlinewalkietalkie.transport

import android.content.Context
import android.util.Log
import com.example.offlinewalkietalkie.transport.bluetooth.BleL2capTransport
import com.example.offlinewalkietalkie.transport.bluetooth.BluetoothClassicTransport
import com.example.offlinewalkietalkie.transport.nearby.NearbyConnectionsTransport
import com.example.offlinewalkietalkie.transport.wifi.WifiAwareTransport
import com.example.offlinewalkietalkie.transport.wifidirect.WifiDirectTransport
import com.example.offlinewalkietalkie.util.CapabilityManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

enum class TransportType {
    NEARBY_CONNECTIONS,
    BLE_L2CAP,
    WIFI_AWARE,
    WIFI_DIRECT,
    BLUETOOTH_CLASSIC
}

@OptIn(ExperimentalCoroutinesApi::class)
class TransportManager(private val context: Context) {

    companion object {
        private const val TAG = "TL/TransportManager"
    }

    private val nearbyTransport = NearbyConnectionsTransport(context)
    private val bleL2capTransport = BleL2capTransport(context)
    private val wifiAwareTransport = WifiAwareTransport(context)
    private val bluetoothTransport = BluetoothClassicTransport(context)
    private val wifiDirectTransport = WifiDirectTransport(context)

    // Use TransportSelector to determine the best available transport
    private val _activeTransportType = MutableStateFlow(
        TransportSelector.selectTransport(context, isChannel = false, isRelayMesh = false)
    )
    val activeTransportType = _activeTransportType

    private val activeTransport: Transport
        get() = transportFor(_activeTransportType.value)

    private fun transportFor(type: TransportType): Transport = when (type) {
        TransportType.NEARBY_CONNECTIONS -> nearbyTransport
        TransportType.BLE_L2CAP -> bleL2capTransport
        TransportType.WIFI_AWARE -> wifiAwareTransport
        TransportType.BLUETOOTH_CLASSIC -> bluetoothTransport
        TransportType.WIFI_DIRECT -> wifiDirectTransport
    }

    fun setTransportType(type: TransportType) {
        _activeTransportType.value = type
    }

    val state = _activeTransportType.flatMapLatest { transportFor(it).state }

    val peers = _activeTransportType.flatMapLatest { transportFor(it).peers }

    val connectionRequests = _activeTransportType.flatMapLatest { transportFor(it).connectionRequests }

    val lastError = _activeTransportType.flatMapLatest { transportFor(it).lastError }

    val isAdvertising = _activeTransportType.flatMapLatest { transportFor(it).isAdvertising }

    val isDiscovering = _activeTransportType.flatMapLatest { transportFor(it).isDiscovering }

    suspend fun startAdvertising(identity: LocalIdentity) {
        activeTransport.startAdvertising(identity)
    }

    suspend fun startDiscovery(identity: LocalIdentity) {
        activeTransport.startDiscovery(identity)
    }

    suspend fun stopAdvertising() {
        activeTransport.stopAdvertising()
    }

    /** Re-advertise under a new endpoint name, leaving established links untouched. */
    suspend fun republishAdvertising(identity: LocalIdentity) {
        activeTransport.stopAdvertising()
        activeTransport.startAdvertising(identity)
    }

    /**
     * Discovery is symmetric: every device must be both visible and looking. Running only one
     * half means two phones can sit next to each other and never see anything.
     */
    suspend fun startSymmetricDiscovery(identity: LocalIdentity) {
        Log.i(TAG, "startSymmetricDiscovery as '${identity.endpointName}' on ${_activeTransportType.value}")
        activeTransport.startAdvertising(identity)
        activeTransport.startDiscovery(identity)
    }

    suspend fun connect(peerId: String) {
        activeTransport.connect(peerId)
    }

    suspend fun acceptConnection(peerId: String) {
        activeTransport.acceptConnection(peerId)
    }

    suspend fun rejectConnection(peerId: String) {
        activeTransport.rejectConnection(peerId)
    }

    suspend fun disconnect(peerId: String) {
        activeTransport.disconnect(peerId)
    }

    suspend fun send(peerId: String, bytes: ByteArray) {
        activeTransport.send(peerId, bytes)
    }

    fun incomingFrames(): Flow<IncomingFrame> {
        return _activeTransportType.flatMapLatest { transportFor(it).incomingFrames() }
    }

    suspend fun stop() {
        nearbyTransport.stop()
        bleL2capTransport.stop()
        wifiAwareTransport.stop()
        bluetoothTransport.stop()
        wifiDirectTransport.stop()
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/transport/TransportSelector.kt`
```kotlin
package com.example.offlinewalkietalkie.transport

import android.content.Context
import com.example.offlinewalkietalkie.util.CapabilityManager

/**
 * Section 5 & Section 19.15 Transport Selection and Migration Policy.
 *
 * Selection Rules:
 * 1. Primary: Nearby Connections where Google Play Services is available.
 * 2. Non-GMS Fallbacks (e.g. Huawei):
 *    - 1:1: BLE L2CAP CoC, or Wi-Fi Aware/Direct.
 *    - Channels: Wi-Fi Aware where supported; otherwise Wi-Fi Direct with host as group owner.
 *    - Mesh Relaying: Wi-Fi Aware where hardware supports multiple peers; honest label when unavailable.
 * 3. Never crash, block, or show internet-related errors when Play Services is absent.
 */
object TransportSelector {

    fun selectTransport(
        context: Context,
        isChannel: Boolean = false,
        isRelayMesh: Boolean = false
    ): TransportType {
        // 1. Google Play Services available -> Nearby Connections
        if (CapabilityManager.isGooglePlayServicesAvailable(context)) {
            return TransportType.NEARBY_CONNECTIONS
        }

        // 2. Non-GMS fallbacks
        if (isRelayMesh) {
            // Mesh relaying on non-GMS prefers Wi-Fi Aware if supported
            if (CapabilityManager.isWifiAwareSupported(context)) {
                return TransportType.WIFI_AWARE
            }
            // BLE cannot do multi-hop standalone mesh (§5 & §19.15)
            // Fall back to BLE for 1:1 links
            if (CapabilityManager.isBleSupported(context)) {
                return TransportType.BLE_L2CAP
            }
        }

        if (isChannel) {
            // Group / Channels: Wi-Fi Aware if supported, else Wi-Fi Direct (host group owner)
            if (CapabilityManager.isWifiAwareSupported(context)) {
                return TransportType.WIFI_AWARE
            }
            if (CapabilityManager.isWifiDirectSupported(context)) {
                return TransportType.WIFI_DIRECT
            }
            if (CapabilityManager.isBleSupported(context)) {
                return TransportType.BLE_L2CAP
            }
        }

        // 3. Direct 1:1: BLE L2CAP CoC is universal on API 29+
        if (CapabilityManager.isBleSupported(context)) {
            return TransportType.BLE_L2CAP
        }

        if (CapabilityManager.isWifiDirectSupported(context)) {
            return TransportType.WIFI_DIRECT
        }

        return TransportType.BLUETOOTH_CLASSIC
    }

    fun isTransportAvailable(context: Context, type: TransportType): Boolean {
        return when (type) {
            TransportType.NEARBY_CONNECTIONS -> CapabilityManager.isGooglePlayServicesAvailable(context)
            TransportType.BLE_L2CAP -> CapabilityManager.isBleSupported(context)
            TransportType.WIFI_AWARE -> CapabilityManager.isWifiAwareSupported(context)
            TransportType.WIFI_DIRECT -> CapabilityManager.isWifiDirectSupported(context)
            TransportType.BLUETOOTH_CLASSIC -> CapabilityManager.isBluetoothEnabled(context)
        }
    }

    fun getDisplayName(type: TransportType): String {
        return when (type) {
            TransportType.NEARBY_CONNECTIONS -> "Google Nearby Connections (Offline)"
            TransportType.BLE_L2CAP -> "Bluetooth LE (L2CAP CoC Native)"
            TransportType.WIFI_AWARE -> "Wi-Fi Aware (NAN P2P)"
            TransportType.WIFI_DIRECT -> "Wi-Fi Direct (P2P Group)"
            TransportType.BLUETOOTH_CLASSIC -> "Bluetooth Classic (Legacy)"
        }
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/transport/bluetooth/BleL2capTransport.kt`
```kotlin
package com.example.offlinewalkietalkie.transport.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import androidx.annotation.RequiresApi
import com.example.offlinewalkietalkie.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Section 5 & Section 19.15 Native BLE L2CAP CoC Transport.
 *
 * Requirements:
 * - Native Bluetooth fallback on API 29+ without Google Play Services.
 * - BLE Advertising & Scanning for discovery.
 * - Dynamic PSM assignment via listenUsingInsecureL2capChannel().
 * - Low-latency credit-based connection-oriented channels (L2CAP CoC).
 */
@SuppressLint("MissingPermission")
@RequiresApi(Build.VERSION_CODES.Q)
class BleL2capTransport(private val context: Context) : Transport {

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _state = MutableStateFlow(TransportState.IDLE)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    override val peers: Flow<List<Peer>> = _peers.map { it.values.toList() }

    private val _connectionRequests = MutableSharedFlow<ConnectionRequest>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val connectionRequests: Flow<ConnectionRequest> = _connectionRequests.asSharedFlow()

    private val _incomingFrames = MutableSharedFlow<IncomingFrame>(
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var serverSocket: BluetoothServerSocket? = null
    private var localPsm: Int = 0
    private val activeConnections = ConcurrentHashMap<String, L2capConnectedThread>()

    // Remote peer PSM cache: address -> psm
    private val discoveredPsms = ConcurrentHashMap<String, Int>()

    private var leAdvertiser: BluetoothLeAdvertiser? = null
    private var leScanner: BluetoothLeScanner? = null

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            _state.value = TransportState.ACTIVE
        }

        override fun onStartFailure(errorCode: Int) {
            _state.value = TransportState.ERROR
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val address = device.address
                val name = device.name ?: "BLE Peer"
                val serviceData = result.scanRecord?.getServiceData(ParcelUuid(SERVICE_UUID))
                val psm = if (serviceData != null && serviceData.size >= 4) {
                    ByteBuffer.wrap(serviceData).int
                } else {
                    0
                }
                if (psm != 0) {
                    discoveredPsms[address] = psm
                }
                addOrUpdatePeer(address, name, activeConnections.containsKey(address))
            }
        }
    }

    override suspend fun startAdvertising(identity: LocalIdentity) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        startServer()

        leAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val psmData = ByteBuffer.allocate(4).putInt(localPsm).array()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .addServiceData(ParcelUuid(SERVICE_UUID), psmData)
            .build()

        leAdvertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    override suspend fun startDiscovery(identity: LocalIdentity) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        _state.value = TransportState.ACTIVE

        leScanner = bluetoothAdapter.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        leScanner?.startScan(listOf(filter), settings, scanCallback)
    }

    override suspend fun connect(peerId: String) {
        if (bluetoothAdapter == null) return
        val device = bluetoothAdapter.getRemoteDevice(peerId)
        val psm = discoveredPsms[peerId] ?: return

        coroutineScope.launch {
            try {
                // Open L2CAP Connection-Oriented Channel (§19.15)
                val socket = device.createInsecureL2capChannel(psm)
                socket.connect()
                manageConnectedSocket(socket, device)
            } catch (e: Exception) {
                // Connection failed
            }
        }
    }

    override suspend fun acceptConnection(peerId: String) {
        // Direct socket connection
    }

    override suspend fun rejectConnection(peerId: String) {}

    override suspend fun disconnect(peerId: String) {
        activeConnections[peerId]?.cancel()
        activeConnections.remove(peerId)
        removePeer(peerId)
    }

    override suspend fun send(peerId: String, bytes: ByteArray) {
        activeConnections[peerId]?.write(bytes)
    }

    override fun incomingFrames(): Flow<IncomingFrame> = _incomingFrames.asSharedFlow()

    override suspend fun stop() {
        try {
            leAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {}
        try {
            leScanner?.stopScan(scanCallback)
        } catch (e: Exception) {}

        serverSocket?.close()
        serverSocket = null
        activeConnections.values.forEach { it.cancel() }
        activeConnections.clear()
        _peers.value = emptyMap()
        _state.value = TransportState.IDLE
        coroutineScope.coroutineContext.cancelChildren()
    }

    private fun startServer() {
        if (serverSocket != null) return
        try {
            // Allocate dynamic L2CAP PSM port
            val ss = bluetoothAdapter?.listenUsingInsecureL2capChannel() ?: return
            serverSocket = ss
            localPsm = ss.psm

            coroutineScope.launch {
                var shouldLoop = true
                while (shouldLoop) {
                    val socket = try {
                        serverSocket?.accept()
                    } catch (e: IOException) {
                        shouldLoop = false
                        null
                    }
                    socket?.let { manageConnectedSocket(it, it.remoteDevice) }
                }
            }
        } catch (e: Exception) {
            _state.value = TransportState.ERROR
        }
    }

    private fun manageConnectedSocket(socket: BluetoothSocket, device: BluetoothDevice) {
        val address = device.address
        val name = device.name ?: "BLE Peer"
        addOrUpdatePeer(address, name, true)
        val thread = L2capConnectedThread(socket, address)
        activeConnections[address] = thread
        thread.start()
    }

    private inner class L2capConnectedThread(
        private val socket: BluetoothSocket,
        private val peerId: String
    ) : Thread() {
        private val inStream: InputStream = socket.inputStream
        private val outStream: OutputStream = socket.outputStream

        override fun run() {
            while (true) {
                try {
                    val lengthBytes = ByteArray(4)
                    var readLen = inStream.read(lengthBytes)
                    if (readLen == -1) break

                    val length = ByteBuffer.wrap(lengthBytes).int
                    if (length > 0 && length < 65536) {
                        val payload = ByteArray(length)
                        var total = 0
                        while (total < length) {
                            val r = inStream.read(payload, total, length - total)
                            if (r == -1) break
                            total += r
                        }
                        if (total == length) {
                            _incomingFrames.tryEmit(IncomingFrame(peerId, payload))
                        }
                    }
                } catch (e: IOException) {
                    break
                }
            }
            activeConnections.remove(peerId)
            removePeer(peerId)
        }

        fun write(bytes: ByteArray) {
            try {
                val lengthBytes = ByteBuffer.allocate(4).putInt(bytes.size).array()
                outStream.write(lengthBytes)
                outStream.write(bytes)
                outStream.flush()
            } catch (e: IOException) {}
        }

        fun cancel() {
            try {
                socket.close()
            } catch (e: IOException) {}
        }
    }

    private fun addOrUpdatePeer(id: String, name: String, isConnected: Boolean) {
        _peers.update { current ->
            current + (id to Peer(id, name, isConnected))
        }
    }

    private fun removePeer(id: String) {
        _peers.update { current -> current - id }
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/transport/bluetooth/BluetoothClassicTransport.kt`
```kotlin
﻿package com.example.offlinewalkietalkie.transport.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.example.offlinewalkietalkie.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothClassicTransport(private val context: Context) : Transport {

    companion object {
        private const val APP_NAME = "OfflineWalkieTalkie"
        private val APP_UUID: UUID = UUID.fromString("1f2a3b4c-5d6e-7f8a-9b0c-1d2e3f4a5b6c")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _state = MutableStateFlow(TransportState.IDLE)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    override val peers: Flow<List<Peer>> = _peers.map { it.values.toList() }

    private val _connectionRequests = MutableSharedFlow<ConnectionRequest>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val connectionRequests: Flow<ConnectionRequest> = _connectionRequests.asSharedFlow()

    private val _incomingFrames = MutableSharedFlow<IncomingFrame>(
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var serverSocket: BluetoothServerSocket? = null
    private var activeConnections = mutableMapOf<String, ConnectedThread>()

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let {
                        val name = it.name ?: "Unknown"
                        val address = it.address
                        addOrUpdatePeer(address, name, activeConnections.containsKey(address))
                    }
                }
            }
        }
    }

    override suspend fun startAdvertising(identity: LocalIdentity) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        
        _state.value = TransportState.ACTIVE
        startAcceptThread()
    }

    override suspend fun startDiscovery(identity: LocalIdentity) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        
        _state.value = TransportState.ACTIVE
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(discoveryReceiver, filter)
        bluetoothAdapter.startDiscovery()
    }

    override suspend fun connect(peerId: String) {
        if (bluetoothAdapter == null) return
        bluetoothAdapter.cancelDiscovery()
        val device = bluetoothAdapter.getRemoteDevice(peerId)
        coroutineScope.launch {
            try {
                val socket = device.createRfcommSocketToServiceRecord(APP_UUID)
                socket.connect()
                manageConnectedSocket(socket, device)
            } catch (e: IOException) {
                // Connection failed
            }
        }
    }

    override suspend fun acceptConnection(peerId: String) {
        // Bluetooth fallback connects directly without separate accept prompt in MVP
    }

    override suspend fun rejectConnection(peerId: String) {
        // Not used in standard BT MVP
    }

    override suspend fun disconnect(peerId: String) {
        activeConnections[peerId]?.cancel()
        activeConnections.remove(peerId)
        removePeer(peerId)
    }

    override suspend fun send(peerId: String, bytes: ByteArray) {
        activeConnections[peerId]?.write(bytes)
    }

    override fun incomingFrames(): Flow<IncomingFrame> = _incomingFrames.asSharedFlow()

    override suspend fun stop() {
        bluetoothAdapter?.cancelDiscovery()
        try {
            context.unregisterReceiver(discoveryReceiver)
        } catch (e: IllegalArgumentException) {
            // Not registered
        }
        serverSocket?.close()
        serverSocket = null
        activeConnections.values.forEach { it.cancel() }
        activeConnections.clear()
        _peers.value = emptyMap()
        _state.value = TransportState.IDLE
        coroutineScope.coroutineContext.cancelChildren()
    }

    private fun startAcceptThread() {
        coroutineScope.launch {
            try {
                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(APP_NAME, APP_UUID)
                var shouldLoop = true
                while (shouldLoop) {
                    val socket: BluetoothSocket? = try {
                        serverSocket?.accept()
                    } catch (e: IOException) {
                        shouldLoop = false
                        null
                    }
                    socket?.let {
                        manageConnectedSocket(it, it.remoteDevice)
                    }
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private fun manageConnectedSocket(socket: BluetoothSocket, device: BluetoothDevice) {
        val address = device.address
        val name = device.name ?: "Unknown"
        addOrUpdatePeer(address, name, true)
        val thread = ConnectedThread(socket, address)
        activeConnections[address] = thread
        thread.start()
    }

    private inner class ConnectedThread(private val socket: BluetoothSocket, private val peerId: String) : Thread() {
        private val inStream: InputStream = socket.inputStream
        private val outStream: OutputStream = socket.outputStream

        override fun run() {
            while (true) {
                try {
                    val lengthBytes = ByteArray(4)
                    var readLength = inStream.read(lengthBytes)
                    if (readLength == -1) break
                    
                    val length = ByteBuffer.wrap(lengthBytes).int
                    if (length > 0) {
                        val payload = ByteArray(length)
                        var totalRead = 0
                        while (totalRead < length) {
                            val r = inStream.read(payload, totalRead, length - totalRead)
                            if (r == -1) break
                            totalRead += r
                        }
                        if (totalRead == length) {
                            _incomingFrames.tryEmit(IncomingFrame(peerId, payload))
                        }
                    }
                } catch (e: IOException) {
                    break
                }
            }
            // Disconnected
            activeConnections.remove(peerId)
            removePeer(peerId)
        }

        fun write(bytes: ByteArray) {
            try {
                val lengthBytes = ByteBuffer.allocate(4).putInt(bytes.size).array()
                outStream.write(lengthBytes)
                outStream.write(bytes)
                outStream.flush()
            } catch (e: IOException) {
                // Send failed
            }
        }

        fun cancel() {
            try {
                socket.close()
            } catch (e: IOException) {}
        }
    }

    private fun addOrUpdatePeer(id: String, name: String, isConnected: Boolean) {
        _peers.update { current ->
            current + (id to Peer(id, name, isConnected))
        }
    }

    private fun removePeer(id: String) {
        _peers.update { current -> current - id }
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/transport/nearby/NearbyConnectionsTransport.kt`
```kotlin
package com.example.offlinewalkietalkie.transport.nearby

import android.content.Context
import android.util.Log
import com.example.offlinewalkietalkie.transport.*
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

class NearbyConnectionsTransport(
    private val context: Context,
    private var activeStrategy: Strategy = Strategy.P2P_CLUSTER
) : Transport {

    companion object {
        private const val TAG = "TL/Nearby"
        // Must be identical on every device or endpoints are invisible to each other.
        private const val SERVICE_ID = "com.example.offlinewalkietalkie.SERVICE_ID"
    }

    private val connectionsClient = Nearby.getConnectionsClient(context)

    private val _isHighBandwidth = MutableStateFlow(false)
    val isHighBandwidth: StateFlow<Boolean> = _isHighBandwidth.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    fun setStrategy(strategy: Strategy) {
        activeStrategy = strategy
    }

    private val _state = MutableStateFlow(TransportState.IDLE)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    override val peers: Flow<List<Peer>> = _peers.map { it.values.toList() }

    private val _connectionRequests = MutableSharedFlow<ConnectionRequest>(
        replay = 1,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val connectionRequests: Flow<ConnectionRequest> = _connectionRequests.asSharedFlow()

    private val _incomingFrames = MutableSharedFlow<IncomingFrame>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                payload.asBytes()?.let { bytes ->
                    _incomingFrames.tryEmit(IncomingFrame(endpointId, bytes))
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.i(TAG, "onConnectionInitiated endpoint=$endpointId name=${info.endpointName} incoming=${info.isIncomingConnection}")
            // The remote endpoint may never have been surfaced by discovery (we were the
            // advertiser). Make sure it exists in the peer map before anything else.
            addOrUpdatePeer(endpointId, info.endpointName, isConnected = false)
            _connectionRequests.tryEmit(
                ConnectionRequest(endpointId, info.endpointName, info.authenticationDigits)
            )
        }

        override fun onBandwidthChanged(endpointId: String, bandwidthInfo: BandwidthInfo) {
            val isHigh = bandwidthInfo.quality == BandwidthInfo.Quality.HIGH
            Log.i(TAG, "onBandwidthChanged endpoint=$endpointId high=$isHigh")
            _isHighBandwidth.value = isHigh
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                Log.i(TAG, "CONNECTED endpoint=$endpointId")
                markConnected(endpointId)
                _lastError.value = null
            } else {
                Log.w(TAG, "Connection failed endpoint=$endpointId status=${result.status.statusCode} ${result.status.statusMessage}")
                _lastError.value = "Connection failed (${result.status.statusCode})"
                removePeer(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.i(TAG, "onDisconnected endpoint=$endpointId")
            removePeer(endpointId)
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.i(TAG, "onEndpointFound endpoint=$endpointId name=${info.endpointName} service=${info.serviceId}")
            addOrUpdatePeer(endpointId, info.endpointName, isConnected = false)
        }

        override fun onEndpointLost(endpointId: String) {
            Log.i(TAG, "onEndpointLost endpoint=$endpointId")
            removePeerIfDisconnected(endpointId)
        }
    }

    /**
     * The name peers see for us. Held so an outgoing requestConnection identifies this
     * device the same way its advertisement does — otherwise the remote cannot tell which
     * group is calling.
     */
    private var localEndpointName: String = android.os.Build.MODEL

    override suspend fun stopAdvertising() {
        try {
            connectionsClient.stopAdvertising()
        } catch (e: Exception) {
            Log.w(TAG, "stopAdvertising failed: ${e.message}")
        }
        _isAdvertising.value = false
    }

    override suspend fun startAdvertising(identity: LocalIdentity) {
        localEndpointName = identity.endpointName
        val options = AdvertisingOptions.Builder()
            .setStrategy(activeStrategy)
            .setLowPower(false)
            .build()
        try {
            connectionsClient.startAdvertising(
                identity.endpointName,
                SERVICE_ID,
                connectionLifecycleCallback,
                options
            ).await()
            Log.i(TAG, "Advertising as '${identity.endpointName}' strategy=$activeStrategy")
            _isAdvertising.value = true
            _state.value = TransportState.ACTIVE
            _lastError.value = null
        } catch (e: Exception) {
            if (statusCodeOf(e) == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) {
                Log.i(TAG, "Already advertising - treating as success")
                _isAdvertising.value = true
                _state.value = TransportState.ACTIVE
                return
            }
            Log.e(TAG, "startAdvertising failed", e)
            _isAdvertising.value = false
            _lastError.value = describe("Advertising", e)
            _state.value = TransportState.ERROR
        }
    }

    override suspend fun startDiscovery(identity: LocalIdentity) {
        val options = DiscoveryOptions.Builder()
            .setStrategy(activeStrategy)
            .setLowPower(false)
            .build()
        try {
            connectionsClient.startDiscovery(
                SERVICE_ID,
                endpointDiscoveryCallback,
                options
            ).await()
            Log.i(TAG, "Discovering strategy=$activeStrategy")
            _isDiscovering.value = true
            _state.value = TransportState.ACTIVE
            _lastError.value = null
        } catch (e: Exception) {
            if (statusCodeOf(e) == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) {
                Log.i(TAG, "Already discovering - treating as success")
                _isDiscovering.value = true
                _state.value = TransportState.ACTIVE
                return
            }
            Log.e(TAG, "startDiscovery failed", e)
            _isDiscovering.value = false
            _lastError.value = describe("Discovery", e)
            _state.value = TransportState.ERROR
        }
    }

    override suspend fun connect(peerId: String) {
        try {
            Log.i(TAG, "requestConnection -> $peerId")
            connectionsClient.requestConnection(
                localEndpointName,
                peerId,
                connectionLifecycleCallback
            ).await()
        } catch (e: Exception) {
            when (statusCodeOf(e)) {
                ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT -> {
                    Log.i(TAG, "Already connected to $peerId")
                    markConnected(peerId)
                }
                // Both sides requested at once; the other side's request wins and we will
                // still land in onConnectionInitiated. Not a user-visible failure.
                ConnectionsStatusCodes.STATUS_OUT_OF_ORDER_API_CALL -> {
                    Log.i(TAG, "Connection already in flight for $peerId")
                }
                else -> {
                    Log.e(TAG, "requestConnection failed for $peerId", e)
                    _lastError.value = describe("Connect", e)
                }
            }
        }
    }

    override suspend fun acceptConnection(peerId: String) {
        try {
            Log.i(TAG, "acceptConnection $peerId")
            connectionsClient.acceptConnection(peerId, payloadCallback).await()
        } catch (e: Exception) {
            Log.e(TAG, "acceptConnection failed for $peerId", e)
            _lastError.value = describe("Accept", e)
        }
    }

    override suspend fun rejectConnection(peerId: String) {
        try {
            connectionsClient.rejectConnection(peerId).await()
        } catch (e: Exception) {
            Log.e(TAG, "rejectConnection failed for $peerId", e)
        }
    }

    override suspend fun disconnect(peerId: String) {
        connectionsClient.disconnectFromEndpoint(peerId)
        removePeer(peerId)
    }

    override suspend fun send(peerId: String, bytes: ByteArray) {
        try {
            connectionsClient.sendPayload(peerId, Payload.fromBytes(bytes))
        } catch (e: Exception) {
            Log.w(TAG, "sendPayload failed for $peerId: ${e.message}")
        }
    }

    override fun incomingFrames(): Flow<IncomingFrame> = _incomingFrames.asSharedFlow()

    override suspend fun stop() {
        Log.i(TAG, "stop()")
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        _isAdvertising.value = false
        _isDiscovering.value = false
        _peers.value = emptyMap()
        _state.value = TransportState.IDLE
    }

    private fun statusCodeOf(e: Exception): Int =
        (e as? ApiException)?.statusCode ?: -1

    private fun describe(action: String, e: Exception): String {
        val code = statusCodeOf(e)
        val reason = when (code) {
            ConnectionsStatusCodes.STATUS_BLUETOOTH_ERROR ->
                "Bluetooth unavailable - turn Bluetooth on"
            ConnectionsStatusCodes.MISSING_SETTING_LOCATION_MUST_BE_ON ->
                "Turn on Location in system settings"
            ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_COARSE_LOCATION,
            ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_FINE_LOCATION ->
                "Location permission required"
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_ADMIN,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_SCAN,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_ADVERTISE,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_CONNECT ->
                "Nearby devices permission required"
            ConnectionsStatusCodes.MISSING_PERMISSION_NEARBY_WIFI_DEVICES ->
                "Nearby Wi-Fi devices permission required"
            ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_WIFI_STATE,
            ConnectionsStatusCodes.MISSING_PERMISSION_CHANGE_WIFI_STATE ->
                "Wi-Fi permission required"
            ConnectionsStatusCodes.STATUS_RADIO_ERROR ->
                "Radio error - toggle Wi-Fi and Bluetooth"
            ConnectionsStatusCodes.STATUS_ALREADY_HAVE_ACTIVE_STRATEGY ->
                "Another Nearby strategy is already active"
            else -> e.message ?: ConnectionsStatusCodes.getStatusCodeString(code)
        }
        return "$action failed: $reason (code $code)"
    }

    private fun addOrUpdatePeer(id: String, name: String, isConnected: Boolean) {
        _peers.update { current ->
            val existing = current[id]
            // Never downgrade an already-connected peer back to disconnected here.
            val connected = existing?.isConnected ?: isConnected
            current + (id to Peer(id, name, connected))
        }
    }

    private fun markConnected(id: String) {
        _peers.update { current ->
            val peer = current[id] ?: Peer(id, id, false)
            current + (id to peer.copy(isConnected = true))
        }
    }

    private fun removePeer(id: String) {
        _peers.update { current -> current - id }
    }

    private fun removePeerIfDisconnected(id: String) {
        _peers.update { current ->
            if (current[id]?.isConnected == true) current else current - id
        }
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/transport/wifi/WifiAwareTransport.kt`
```kotlin
package com.example.offlinewalkietalkie.transport.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.aware.*
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.offlinewalkietalkie.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Section 5 & Section 19.15 Wi-Fi Aware Transport.
 *
 * Requirements:
 * - Preferred over Wi-Fi Direct for multi-peer channels and mesh links when hardware supports it.
 * - Publish/Subscribe discovery without internet or access points.
 * - Link-local socket communication.
 */
@SuppressLint("MissingPermission")
@RequiresApi(Build.VERSION_CODES.Q)
class WifiAwareTransport(private val context: Context) : Transport {

    companion object {
        const val SERVICE_NAME = "talkloop-aware-v3"
    }

    private val awareManager = context.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
    private val isSupported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)

    private val _state = MutableStateFlow(TransportState.IDLE)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    override val peers: Flow<List<Peer>> = _peers.map { it.values.toList() }

    private val _connectionRequests = MutableSharedFlow<ConnectionRequest>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val connectionRequests: Flow<ConnectionRequest> = _connectionRequests.asSharedFlow()

    private val _incomingFrames = MutableSharedFlow<IncomingFrame>(
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var awareSession: WifiAwareSession? = null
    private var discoverySession: DiscoverySession? = null
    private var serverSocket: ServerSocket? = null
    private val activeConnections = ConcurrentHashMap<String, AwareConnectedThread>()

    override suspend fun startAdvertising(identity: LocalIdentity) {
        if (!isSupported || awareManager?.isAvailable != true) {
            _state.value = TransportState.ERROR
            return
        }

        _state.value = TransportState.ACTIVE
        startServer()

        awareManager.attach(object : AttachCallback() {
            override fun onAttached(session: WifiAwareSession) {
                awareSession = session
                val config = PublishConfig.Builder()
                    .setServiceName(SERVICE_NAME)
                    .build()
                session.publish(config, object : DiscoverySessionCallback() {
                    override fun onPublishStarted(session: PublishDiscoverySession) {
                        discoverySession = session
                    }
                    override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                        val peerId = "aware_${peerHandle.hashCode()}"
                        addOrUpdatePeer(peerId, "Aware Peer", true)
                        _incomingFrames.tryEmit(IncomingFrame(peerId, message))
                    }
                }, null)
            }

            override fun onAttachFailed() {
                _state.value = TransportState.ERROR
            }
        }, null)
    }

    override suspend fun startDiscovery(identity: LocalIdentity) {
        if (!isSupported || awareManager?.isAvailable != true) {
            _state.value = TransportState.ERROR
            return
        }

        _state.value = TransportState.ACTIVE

        awareManager.attach(object : AttachCallback() {
            override fun onAttached(session: WifiAwareSession) {
                awareSession = session
                val config = SubscribeConfig.Builder()
                    .setServiceName(SERVICE_NAME)
                    .build()
                session.subscribe(config, object : DiscoverySessionCallback() {
                    override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                        discoverySession = session
                    }
                    override fun onServiceDiscovered(peerHandle: PeerHandle, serviceSpecificInfo: ByteArray?, matchFilter: MutableList<ByteArray>?) {
                        val peerId = "aware_${peerHandle.hashCode()}"
                        addOrUpdatePeer(peerId, "Aware Peer", false)
                    }
                    override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                        val peerId = "aware_${peerHandle.hashCode()}"
                        addOrUpdatePeer(peerId, "Aware Peer", true)
                        _incomingFrames.tryEmit(IncomingFrame(peerId, message))
                    }
                }, null)
            }

            override fun onAttachFailed() {
                _state.value = TransportState.ERROR
            }
        }, null)
    }

    override suspend fun connect(peerId: String) {
        // Wi-Fi Aware initiates data path through discoverySession
        updatePeerStatus(peerId, true)
    }

    override suspend fun acceptConnection(peerId: String) {}

    override suspend fun rejectConnection(peerId: String) {}

    override suspend fun disconnect(peerId: String) {
        activeConnections[peerId]?.cancel()
        activeConnections.remove(peerId)
        updatePeerStatus(peerId, false)
    }

    override suspend fun send(peerId: String, bytes: ByteArray) {
        val conn = activeConnections[peerId]
        if (conn != null) {
            conn.write(bytes)
        } else {
            // Send discovery message fallback
            val handleId = peerId.removePrefix("aware_").toIntOrNull()
            if (handleId != null && discoverySession != null) {
                // If using aware message channel
            }
        }
    }

    override fun incomingFrames(): Flow<IncomingFrame> = _incomingFrames.asSharedFlow()

    override suspend fun stop() {
        discoverySession?.close()
        discoverySession = null
        awareSession?.close()
        awareSession = null
        serverSocket?.close()
        serverSocket = null
        activeConnections.values.forEach { it.cancel() }
        activeConnections.clear()
        _peers.value = emptyMap()
        _state.value = TransportState.IDLE
        coroutineScope.coroutineContext.cancelChildren()
    }

    private fun startServer() {
        if (serverSocket != null) return
        coroutineScope.launch {
            try {
                serverSocket = ServerSocket(0)
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    val peerId = socket.inetAddress.hostAddress ?: "unknown"
                    val thread = AwareConnectedThread(socket, peerId)
                    activeConnections[peerId] = thread
                    thread.start()
                    updatePeerStatus(peerId, true)
                }
            } catch (e: Exception) {}
        }
    }

    private inner class AwareConnectedThread(
        private val socket: Socket,
        private val peerId: String
    ) : Thread() {
        private val inStream: InputStream = socket.getInputStream()
        private val outStream: OutputStream = socket.getOutputStream()

        override fun run() {
            while (true) {
                try {
                    val lengthBytes = ByteArray(4)
                    var readLen = inStream.read(lengthBytes)
                    if (readLen == -1) break

                    val length = ByteBuffer.wrap(lengthBytes).int
                    if (length > 0 && length < 65536) {
                        val payload = ByteArray(length)
                        var total = 0
                        while (total < length) {
                            val r = inStream.read(payload, total, length - total)
                            if (r == -1) break
                            total += r
                        }
                        if (total == length) {
                            _incomingFrames.tryEmit(IncomingFrame(peerId, payload))
                        }
                    }
                } catch (e: IOException) {
                    break
                }
            }
            activeConnections.remove(peerId)
            updatePeerStatus(peerId, false)
        }

        fun write(bytes: ByteArray) {
            try {
                val lengthBytes = ByteBuffer.allocate(4).putInt(bytes.size).array()
                outStream.write(lengthBytes)
                outStream.write(bytes)
                outStream.flush()
            } catch (e: IOException) {}
        }

        fun cancel() {
            try {
                socket.close()
            } catch (e: IOException) {}
        }
    }

    private fun addOrUpdatePeer(id: String, name: String, isConnected: Boolean) {
        _peers.update { current ->
            current + (id to Peer(id, name, isConnected))
        }
    }

    private fun updatePeerStatus(id: String, isConnected: Boolean) {
        _peers.update { current ->
            val peer = current[id] ?: Peer(id, "Peer $id", false)
            current + (id to peer.copy(isConnected = isConnected))
        }
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/transport/wifidirect/WifiDirectTransport.kt`
```kotlin
﻿package com.example.offlinewalkietalkie.transport.wifidirect

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.*
import android.os.Looper
import com.example.offlinewalkietalkie.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer

@SuppressLint("MissingPermission")
class WifiDirectTransport(private val context: Context) : Transport {

    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? = manager?.initialize(context, Looper.getMainLooper(), null)

    private val _state = MutableStateFlow(TransportState.IDLE)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    override val peers: Flow<List<Peer>> = _peers.map { it.values.toList() }

    private val _connectionRequests = MutableSharedFlow<ConnectionRequest>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val connectionRequests: Flow<ConnectionRequest> = _connectionRequests.asSharedFlow()

    private val _incomingFrames = MutableSharedFlow<IncomingFrame>(
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var serverSocket: ServerSocket? = null
    private var activeConnections = mutableMapOf<String, WdConnectedThread>()
    
    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    manager?.requestPeers(channel) { peerList ->
                        val currentList = peerList.deviceList
                        _peers.update { current ->
                            val updated = current.toMutableMap()
                            currentList.forEach { device ->
                                val id = device.deviceAddress
                                val name = device.deviceName
                                updated[id] = Peer(id, name, activeConnections.containsKey(id))
                            }
                            updated
                        }
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    manager?.requestConnectionInfo(channel) { info ->
                        if (info.groupFormed) {
                            if (info.isGroupOwner) {
                                startServer()
                            } else {
                                startClient(info.groupOwnerAddress)
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun startAdvertising(identity: LocalIdentity) {
        if (manager == null || channel == null) return
        _state.value = TransportState.ACTIVE
        context.registerReceiver(receiver, intentFilter)
        manager.discoverPeers(channel, null)
    }

    override suspend fun startDiscovery(identity: LocalIdentity) {
        if (manager == null || channel == null) return
        _state.value = TransportState.ACTIVE
        context.registerReceiver(receiver, intentFilter)
        manager.discoverPeers(channel, null)
    }

    override suspend fun connect(peerId: String) {
        if (manager == null || channel == null) return
        val config = WifiP2pConfig().apply {
            deviceAddress = peerId
        }
        manager.connect(channel, config, null)
    }

    override suspend fun acceptConnection(peerId: String) {}

    override suspend fun rejectConnection(peerId: String) {}

    override suspend fun disconnect(peerId: String) {
        activeConnections[peerId]?.cancel()
        activeConnections.remove(peerId)
        updatePeerStatus(peerId, false)
        if (activeConnections.isEmpty()) {
            manager?.removeGroup(channel, null)
        }
    }

    override suspend fun send(peerId: String, bytes: ByteArray) {
        activeConnections[peerId]?.write(bytes)
    }

    override fun incomingFrames(): Flow<IncomingFrame> = _incomingFrames.asSharedFlow()

    override suspend fun stop() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {}
        manager?.removeGroup(channel, null)
        manager?.cancelConnect(channel, null)
        serverSocket?.close()
        serverSocket = null
        activeConnections.values.forEach { it.cancel() }
        activeConnections.clear()
        _peers.value = emptyMap()
        _state.value = TransportState.IDLE
        coroutineScope.coroutineContext.cancelChildren()
    }

    private fun startServer() {
        if (serverSocket != null) return
        coroutineScope.launch {
            try {
                serverSocket = ServerSocket(8888)
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    val peerAddress = socket.inetAddress.hostAddress ?: "unknown"
                    manageSocket(socket, peerAddress)
                }
            } catch (e: Exception) {}
        }
    }

    private fun startClient(hostAddress: InetAddress) {
        coroutineScope.launch {
            try {
                val socket = Socket()
                socket.bind(null)
                socket.connect(InetSocketAddress(hostAddress, 8888), 5000)
                manageSocket(socket, hostAddress.hostAddress ?: "unknown")
            } catch (e: Exception) {}
        }
    }

    private fun manageSocket(socket: Socket, peerId: String) {
        updatePeerStatus(peerId, true)
        val thread = WdConnectedThread(socket, peerId)
        activeConnections[peerId] = thread
        thread.start()
    }

    private fun updatePeerStatus(id: String, isConnected: Boolean) {
        _peers.update { current ->
            val peer = current[id] ?: Peer(id, "Peer $id", false)
            current + (id to peer.copy(isConnected = isConnected))
        }
    }

    private inner class WdConnectedThread(private val socket: Socket, private val peerId: String) : Thread() {
        private val inStream: InputStream = socket.getInputStream()
        private val outStream: OutputStream = socket.getOutputStream()

        override fun run() {
            while (true) {
                try {
                    val lengthBytes = ByteArray(4)
                    var readLength = inStream.read(lengthBytes)
                    if (readLength == -1) break
                    
                    val length = ByteBuffer.wrap(lengthBytes).int
                    if (length > 0) {
                        val payload = ByteArray(length)
                        var totalRead = 0
                        while (totalRead < length) {
                            val r = inStream.read(payload, totalRead, length - totalRead)
                            if (r == -1) break
                            totalRead += r
                        }
                        if (totalRead == length) {
                            _incomingFrames.tryEmit(IncomingFrame(peerId, payload))
                        }
                    }
                } catch (e: IOException) {
                    break
                }
            }
            activeConnections.remove(peerId)
            updatePeerStatus(peerId, false)
        }

        fun write(bytes: ByteArray) {
            try {
                val lengthBytes = ByteBuffer.allocate(4).putInt(bytes.size).array()
                outStream.write(lengthBytes)
                outStream.write(bytes)
                outStream.flush()
            } catch (e: IOException) {}
        }

        fun cancel() {
            try {
                socket.close()
            } catch (e: IOException) {}
        }
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/ui/GroupScreens.kt`
```kotlin
package com.example.offlinewalkietalkie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinewalkietalkie.group.DiscoveredGroup
import com.example.offlinewalkietalkie.group.GroupMember
import com.example.offlinewalkietalkie.group.GroupRole
import com.example.offlinewalkietalkie.group.MemberState
import com.example.offlinewalkietalkie.theme.*
import com.example.offlinewalkietalkie.ui.components.GlassSurface

/**
 * Entry point for multi-device connection: create a group, or join one that is nearby.
 * Deliberately only two choices, so there is no decision to get wrong.
 */
@Composable
fun GroupLobbyScreen(
    viewModel: WalkieTalkieViewModel,
    onGroupReady: () -> Unit,
    onBack: () -> Unit
) {
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()
    val discovered by viewModel.discoveredGroups.collectAsState()
    val groupState by viewModel.groupState.collectAsState()
    var groupName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.startNearby() }

    // The moment we belong to a group, move on to the roster.
    LaunchedEffect(groupState.isInGroup) {
        if (groupState.isInGroup) onGroupReady()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtmosphericDarkBrush)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = "Connect Devices",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))

            GlassSurface(isOutdoorMode = isOutdoorMode, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        text = "Start a new group",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "You become the host. Everyone else picks your group from their list.",
                        color = Color(0xFF9FB3C8),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it.take(16) },
                        placeholder = { Text("Group name", color = Color(0xFF7A8CA0)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = ElectricAqua,
                            unfocusedBorderColor = GlassBorder,
                            cursorColor = ElectricAqua
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.createGroup(groupName.trim()) },
                        enabled = groupName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricAqua,
                            contentColor = Color.Black,
                            disabledContainerColor = Color(0xFF29415A)
                        ),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text("Create Group", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Join a group nearby",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            Spacer(Modifier.height(10.dp))

            if (discovered.isEmpty()) {
                GlassSurface(isOutdoorMode = isOutdoorMode, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = ElectricAqua,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = "Looking for groups nearby…",
                            color = Color(0xFFCBD5E1),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                discovered.forEach { group ->
                    DiscoveredGroupCard(
                        group = group,
                        isOutdoorMode = isOutdoorMode,
                        onJoin = { viewModel.joinGroup(group) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun DiscoveredGroupCard(
    group: DiscoveredGroup,
    isOutdoorMode: Boolean,
    onJoin: () -> Unit
) {
    GlassSurface(isOutdoorMode = isOutdoorMode, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "${group.devicesVisible} device${if (group.devicesVisible == 1) "" else "s"} nearby",
                    color = Color(0xFF9FB3C8),
                    fontSize = 13.sp
                )
            }
            Button(
                onClick = onJoin,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricAqua,
                    contentColor = Color.Black
                )
            ) {
                Text("Join", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * The group roster: who is in, who is still connecting, and what went wrong. This is the
 * screen that answers "which group am I in and who am I actually connected to".
 */
@Composable
fun GroupRoomScreen(
    viewModel: WalkieTalkieViewModel,
    onStartTalking: () -> Unit,
    onLeft: () -> Unit,
    onBack: () -> Unit
) {
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()
    val state by viewModel.groupState.collectAsState()

    LaunchedEffect(state.isInGroup) {
        if (!state.isInGroup) onLeft()
    }

    val group = state.group

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtmosphericDarkBrush)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column {
                    Text(
                        text = group?.name ?: "Group",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (state.isCoordinator) {
                            "You are the host"
                        } else {
                            "Host: ${state.coordinator?.displayName ?: "…"}"
                        },
                        color = ElectricAqua,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            GlassSurface(isOutdoorMode = isOutdoorMode, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        text = "${state.liveMemberCount} of ${state.members.size} devices connected",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (state.pending.isNotEmpty()) {
                        Text(
                            text = "${state.pending.size} still connecting…",
                            color = WarmAmber,
                            fontSize = 13.sp
                        )
                    }
                    if (state.failed.isNotEmpty()) {
                        Text(
                            text = "${state.failed.size} could not connect",
                            color = RoseActive,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                state.members.sortedBy { it.displayName }.forEach { member ->
                    MemberRow(
                        member = member,
                        isLocal = member.memberId == state.localMemberId,
                        isOutdoorMode = isOutdoorMode,
                        onAccept = { viewModel.respondToGroupRequest(member.memberId, true) },
                        onDecline = { viewModel.respondToGroupRequest(member.memberId, false) },
                        onRemove = { viewModel.removeGroupMember(member.memberId) }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "To add another phone: open TalkLoop on it, tap Connect Devices, " +
                        "then pick \"${group?.name ?: ""}\".",
                    color = Color(0xFF7A8CA0),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onStartTalking,
                enabled = state.connected.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricAqua,
                    contentColor = Color.Black,
                    disabledContainerColor = Color(0xFF29415A),
                    disabledContentColor = Color(0xFF7A8CA0)
                ),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text(
                    text = if (state.connected.isEmpty()) "Waiting for a device…" else "Start Talking",
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { viewModel.leaveGroup() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseActive),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Leave Group")
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: GroupMember,
    isLocal: Boolean,
    isOutdoorMode: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onRemove: () -> Unit
) {
    GlassSurface(
        cornerRadius = 16.dp,
        isOutdoorMode = isOutdoorMode,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(statusColor(member, isLocal).copy(alpha = 0.18f))
                    .border(1.dp, statusColor(member, isLocal).copy(alpha = 0.5f), CircleShape)
            ) {
                Text(
                    text = member.displayName.take(2).uppercase(),
                    color = statusColor(member, isLocal),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isLocal) "${member.displayName} (you)" else member.displayName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    if (member.role == GroupRole.COORDINATOR) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "HOST",
                            color = ElectricAqua,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ElectricAqua.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = statusLabel(member, isLocal),
                    color = statusColor(member, isLocal),
                    fontSize = 12.sp
                )
                // The whole point of asking a person to accept: they must see the same
                // code shown on the other phone before tapping it, or this is worthless.
                if (member.state == MemberState.PENDING_APPROVAL && member.authCode != null) {
                    Text(
                        text = "Code: ${member.authCode}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = 1.sp
                    )
                }
            }

            when {
                member.state == MemberState.PENDING_APPROVAL -> {
                    IconButton(onClick = onAccept) {
                        Icon(Icons.Default.Check, contentDescription = "Accept", tint = MintGreen)
                    }
                    IconButton(onClick = onDecline) {
                        Icon(Icons.Default.Close, contentDescription = "Decline", tint = RoseActive)
                    }
                }
                member.state == MemberState.FAILED && !isLocal -> {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = WarmAmber)
                    }
                }
                member.state == MemberState.CONNECTING ||
                    member.state == MemberState.RECONNECTING -> {
                    CircularProgressIndicator(
                        color = WarmAmber,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun statusLabel(member: GroupMember, isLocal: Boolean): String = when {
    isLocal -> "This device"
    member.state == MemberState.CONNECTED && !member.isDirect -> "Connected via relay"
    member.state == MemberState.CONNECTED -> "Connected"
    member.state == MemberState.CONNECTING -> "Connecting…"
    member.state == MemberState.PENDING_APPROVAL -> "Wants to join"
    member.state == MemberState.RECONNECTING -> "Reconnecting…"
    member.state == MemberState.FAILED -> member.failureReason ?: "Could not connect"
    member.state == MemberState.DISCOVERED -> "Found nearby"
    member.state == MemberState.LEFT -> "Left the group"
    else -> ""
}

private fun statusColor(member: GroupMember, isLocal: Boolean): Color = when {
    isLocal -> ElectricAqua
    member.state == MemberState.CONNECTED -> MintGreen
    member.state == MemberState.FAILED -> RoseActive
    member.state == MemberState.PENDING_APPROVAL -> BrightCyan
    member.state == MemberState.CONNECTING ||
        member.state == MemberState.RECONNECTING -> WarmAmber
    else -> Color(0xFF9FB3C8)
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/ui/Screens.kt`
```kotlin
package com.example.offlinewalkietalkie.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import com.example.offlinewalkietalkie.R
import com.example.offlinewalkietalkie.channel.TextDeliveryStatus
import com.example.offlinewalkietalkie.protocol.FloorState
import com.example.offlinewalkietalkie.theme.*
import com.example.offlinewalkietalkie.transport.TransportType
import com.example.offlinewalkietalkie.ui.components.GlassSurface
import com.example.offlinewalkietalkie.ui.components.PttOrb
import com.example.offlinewalkietalkie.util.CapabilityManager
import com.example.offlinewalkietalkie.util.PermissionManager

@Composable
fun PermissionScreen(onPermissionsGranted: () -> Unit) {
    val context = LocalContext.current
    var hasAudio by remember { mutableStateOf(PermissionManager.isAudioPermissionGranted(context)) }
    var hasRadio by remember { mutableStateOf(PermissionManager.isRadioPermissionGranted(context)) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        hasAudio = PermissionManager.isAudioPermissionGranted(context)
        hasRadio = PermissionManager.isRadioPermissionGranted(context)
    }

    // Only advance once the radio permissions are in too. Advancing on the microphone alone
    // drops the user into an app whose discovery silently fails with MISSING_PERMISSION.
    LaunchedEffect(hasAudio, hasRadio) {
        if (hasAudio && hasRadio) {
            onPermissionsGranted()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtmosphericDarkBrush)
            .safeDrawingPadding()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(BrightCyan, MintGreen))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = DeepInk,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = stringResource(R.string.app_descriptor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ElectricAqua.copy(alpha = 0.8f)
                )
            }

            // Cards for permissions
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PermissionItemCard(
                    title = stringResource(R.string.microphone_permission),
                    description = stringResource(R.string.microphone_desc),
                    icon = Icons.Default.Mic,
                    isGranted = hasAudio
                )
                PermissionItemCard(
                    title = stringResource(R.string.nearby_permission),
                    description = stringResource(R.string.nearby_desc),
                    icon = Icons.Default.Bluetooth,
                    isGranted = hasRadio
                )
                PermissionItemCard(
                    title = stringResource(R.string.notifications_permission),
                    description = stringResource(R.string.notifications_desc),
                    icon = Icons.Default.Notifications,
                    isGranted = true
                )
            }

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        launcher.launch(PermissionManager.getRequiredPermissions().toTypedArray())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricAqua,
                        contentColor = DeepInk
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.grant_permissions),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                // Continue button ensures app is NEVER stuck
                OutlinedButton(
                    onClick = onPermissionsGranted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text(text = stringResource(R.string.continue_to_app))
                }

                TextButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.open_settings),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionItemCard(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean
) {
    GlassSurface(
        cornerRadius = 18.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) MintGreen.copy(alpha = 0.2f) else Color(0x22FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) MintGreen else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = description,
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = MintGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: WalkieTalkieViewModel,
    onNavigateToNearby: () -> Unit,
    onNavigateToPairing: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToMessages: () -> Unit,
    onNavigateToCreateChannel: () -> Unit,
    onNavigateToJoinChannel: () -> Unit
) {
    val peers by viewModel.peers.collectAsState()
    val floorState by viewModel.floorState.collectAsState()
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()
    val isRelayModeActive by viewModel.isRelayModeActive.collectAsState()
    val currentChannelName by viewModel.currentChannelName.collectAsState()
    val speakerphoneEnabled by viewModel.speakerphoneEnabled.collectAsState()
    val isInSession by viewModel.isInSession.collectAsState()

    val securedPeerCount by viewModel.securedPeerCount.collectAsState()
    val transportType by viewModel.transportType.collectAsState()
    val connectedPeers = peers.filter { it.isConnected }

    val transportTypeName = when (transportType) {
        com.example.offlinewalkietalkie.transport.TransportType.NEARBY_CONNECTIONS -> "Nearby Connections"
        com.example.offlinewalkietalkie.transport.TransportType.BLE_L2CAP -> "BLE L2CAP"
        com.example.offlinewalkietalkie.transport.TransportType.WIFI_AWARE -> "Wi-Fi Aware"
        com.example.offlinewalkietalkie.transport.TransportType.WIFI_DIRECT -> "Wi-Fi Direct"
        else -> "Bluetooth"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isOutdoorMode) OutdoorBackground else Color.Transparent)
            .then(if (!isOutdoorMode) Modifier.background(AtmosphericDarkBrush) else Modifier)
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Bar & Lock-up
            TopHeaderBar(
                connectedCount = connectedPeers.size,
                isOutdoorMode = isOutdoorMode
            )

            // 2. Main Content (Active Session vs Idle)
            if (isInSession) {
                ActiveSessionContent(
                    channelName = currentChannelName,
                    connectedPeers = connectedPeers,
                    floorState = floorState,
                    isOutdoorMode = isOutdoorMode,
                    speakerphoneEnabled = speakerphoneEnabled,
                    isRelayModeActive = isRelayModeActive,
                    securedPeerCount = securedPeerCount,
                    transportTypeName = transportTypeName,
                    onToggleSpeaker = { viewModel.toggleAudioRoute() },
                    onToggleRelay = { viewModel.toggleRelayMode() },
                    onPttStart = { viewModel.requestPtt() },
                    onPttEnd = { viewModel.releasePtt() },
                    onNavigateToNearby = onNavigateToNearby,
                    onNavigateToPairing = onNavigateToPairing,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToContacts = onNavigateToContacts,
                    // Leaving has to drop group membership too, otherwise the roster keeps
                    // this device and peers go on trying to reconnect to it.
                    onLeaveSession = { viewModel.leaveGroup() }
                )
            } else {
                IdleDashboardContent(
                    isOutdoorMode = isOutdoorMode,
                    onCreateChannel = { onNavigateToCreateChannel() },
                    onJoinChannel = { onNavigateToJoinChannel() },
                    onScanNearby = {
                        viewModel.startNearby()
                        onNavigateToNearby()
                    }
                )
            }

            // 3. 4-Tab Bottom Navigation Bar
            BottomNavigationBar(
                activeTab = 0,
                isOutdoorMode = isOutdoorMode,
                onSelectTab = { index ->
                    when (index) {
                        0 -> {} // Already on Home
                        1 -> onNavigateToMessages()
                        2 -> onNavigateToContacts()
                        3 -> onNavigateToSettings()
                    }
                }
            )
        }
    }
}

@Composable
private fun TopHeaderBar(
    connectedCount: Int,
    isOutdoorMode: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Waveform Brand Mark & Titles
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(BrightCyan, MintGreen))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = DeepInk,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.app_descriptor),
                    fontSize = 10.sp,
                    color = ElectricAqua.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Status capsule pill
        GlassSurface(
            cornerRadius = 20.dp,
            isOutdoorMode = isOutdoorMode,
            modifier = Modifier.padding(2.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MintGreen)
                )
                Text(
                    text = if (connectedCount > 0) "${stringResource(R.string.secure)} · $connectedCount"
                           else stringResource(R.string.offline_ready),
                    color = MintGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ActiveSessionContent(
    channelName: String,
    connectedPeers: List<com.example.offlinewalkietalkie.transport.Peer>,
    floorState: FloorState,
    isOutdoorMode: Boolean,
    speakerphoneEnabled: Boolean,
    isRelayModeActive: Boolean,
    securedPeerCount: Int,
    transportTypeName: String,
    onToggleSpeaker: () -> Unit,
    onToggleRelay: () -> Unit,
    onPttStart: () -> Unit,
    onPttEnd: () -> Unit,
    onNavigateToNearby: () -> Unit,
    onNavigateToPairing: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onLeaveSession: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Current Channel Glass Card
        GlassSurface(
            cornerRadius = 26.dp,
            isOutdoorMode = isOutdoorMode,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BrightCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                tint = BrightCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = channelName,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(ElectricAqua)
                                )
                            }
                            Text(
                                text = "${connectedPeers.size + 1} members · ${stringResource(R.string.good_quality)}",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Horizontal Participant Avatar Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // You (Active Speaker)
                    val isYouSpeaking = floorState is FloorState.HeldByMe || floorState is FloorState.Talking || floorState is FloorState.Granted
                    AvatarBadge(
                        name = "You",
                        initials = "ME",
                        isSpeaking = isYouSpeaking,
                        isOnline = true,
                        isRelay = false
                    )

                    // Connected peers
                    connectedPeers.forEach { peer ->
                        val isThisPeerSpeaking = floorState is FloorState.HeldByOther && floorState.peerId == peer.id
                        AvatarBadge(
                            name = peer.name.take(6),
                            initials = peer.name.take(2).uppercase(),
                            isSpeaking = isThisPeerSpeaking,
                            isOnline = peer.isConnected,
                            isRelay = false
                        )
                    }
                }
            }
        }

        // Central Luminous PTT Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speaker Route Shortcut
            GlassSurface(
                cornerRadius = 16.dp,
                isOutdoorMode = isOutdoorMode,
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onToggleSpeaker() }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = if (speakerphoneEnabled) Icons.Default.VolumeUp else Icons.Default.Headset,
                        contentDescription = if (speakerphoneEnabled) stringResource(R.string.speaker) else stringResource(R.string.earpiece),
                        tint = if (speakerphoneEnabled) ElectricAqua else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 176dp Luminous PTT Orb
            PttOrb(
                floorState = floorState,
                isOutdoorMode = isOutdoorMode,
                onPressStart = onPttStart,
                onPressEnd = onPttEnd
            )

            // Roster / Contacts Shortcut
            GlassSurface(
                cornerRadius = 16.dp,
                isOutdoorMode = isOutdoorMode,
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onNavigateToContacts() }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = stringResource(R.string.roster),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Connection Glass Strip
        GlassSurface(
            cornerRadius = 18.dp,
            isOutdoorMode = isOutdoorMode,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 5 Quality Signal Bars
                    QualitySignalBars()
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "${stringResource(R.string.good_quality)} · ${connectedPeers.size} ${if (connectedPeers.size == 1) "peer" else "peers"}",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Text(
                            text = transportTypeName,
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // 2x2 Quick-Action Glass Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionCard(
                title = stringResource(R.string.nearby_devices),
                subtitle = stringResource(R.string.searching_devices),
                icon = Icons.Default.WifiTethering,
                iconTint = BrightCyan,
                isOutdoorMode = isOutdoorMode,
                onClick = onNavigateToNearby,
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                title = stringResource(R.string.mesh_relay),
                subtitle = if (isRelayModeActive) stringResource(R.string.mesh_active) else "Inactive",
                icon = Icons.Default.Hub,
                iconTint = if (isRelayModeActive) MintGreen else Color.Gray,
                isOutdoorMode = isOutdoorMode,
                onClick = onToggleRelay,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionCard(
                title = stringResource(R.string.security),
                subtitle = if (securedPeerCount > 0) "Encrypted ($securedPeerCount)" else "Not Secured",
                icon = Icons.Default.Security,
                iconTint = if (securedPeerCount > 0) Color(0xFF818CF8) else Color.Gray,
                isOutdoorMode = isOutdoorMode,
                onClick = onNavigateToPairing,
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                title = stringResource(R.string.settings),
                subtitle = if (speakerphoneEnabled) "Speaker On" else "Earpiece",
                icon = Icons.Default.Settings,
                iconTint = WarmAmber,
                isOutdoorMode = isOutdoorMode,
                onClick = onNavigateToSettings,
                modifier = Modifier.weight(1f)
            )
        }

        // Leave Session button
        OutlinedButton(
            onClick = onLeaveSession,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
        ) {
            Icon(
                imageVector = Icons.Default.ExitToApp,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Leave Session", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun IdleDashboardContent(
    isOutdoorMode: Boolean,
    onCreateChannel: () -> Unit,
    onJoinChannel: () -> Unit,
    onScanNearby: () -> Unit
) {
    GlassSurface(
        cornerRadius = 28.dp,
        isOutdoorMode = isOutdoorMode,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(BrightCyan.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WifiFind,
                    contentDescription = null,
                    tint = BrightCyan,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.no_active_session),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.no_active_session_desc),
                fontSize = 12.sp,
                color = Color(0xFFCBD5E1),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            // One way in. Creating versus joining is decided on the next screen, where
            // the nearby groups are actually visible.
            Button(
                onClick = onCreateChannel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricAqua,
                    contentColor = DeepInk
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.connect_devices),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onScanNearby,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrightCyan)
            ) {
                Text(text = stringResource(R.string.scan_nearby))
            }
        }
    }
}

@Composable
private fun AvatarBadge(
    name: String,
    initials: String,
    isSpeaking: Boolean,
    isOnline: Boolean,
    isRelay: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(2.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Pulse ring if speaking
            if (isSpeaking) {
                val infiniteTransition = rememberInfiniteTransition(label = "avatar_pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.25f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .border(2.dp, ElectricAqua, CircleShape)
                )
            }

            // Avatar bubble
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isRelay) Color(0xFF3B2F1E) else Color(0xFF1E293B))
                    .border(
                        1.5.dp,
                        if (isRelay) WarmAmber else if (isSpeaking) ElectricAqua else Color(0x33FFFFFF),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isRelay) WarmAmber else Color.White
                )
            }

            // Status badge indicator
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(if (isRelay) WarmAmber else if (isOnline) MintGreen else Color.Gray)
                    .border(1.5.dp, DeepInk, CircleShape)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            fontSize = 10.sp,
            color = if (isSpeaking) ElectricAqua else Color(0xFFCBD5E1),
            fontWeight = if (isSpeaking) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun QualitySignalBars() {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.height(16.dp)
    ) {
        Box(modifier = Modifier.width(3.dp).height(6.dp).clip(RoundedCornerShape(1.dp)).background(ElectricAqua))
        Box(modifier = Modifier.width(3.dp).height(9.dp).clip(RoundedCornerShape(1.dp)).background(ElectricAqua))
        Box(modifier = Modifier.width(3.dp).height(12.dp).clip(RoundedCornerShape(1.dp)).background(ElectricAqua))
        Box(modifier = Modifier.width(3.dp).height(15.dp).clip(RoundedCornerShape(1.dp)).background(ElectricAqua))
        Box(modifier = Modifier.width(3.dp).height(16.dp).clip(RoundedCornerShape(1.dp)).background(Color.White.copy(alpha = 0.2f)))
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    isOutdoorMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        cornerRadius = 18.dp,
        isOutdoorMode = isOutdoorMode,
        modifier = modifier
            .height(68.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun BottomNavigationBar(
    activeTab: Int,
    isOutdoorMode: Boolean,
    onSelectTab: (Int) -> Unit
) {
    GlassSurface(
        cornerRadius = 24.dp,
        isOutdoorMode = isOutdoorMode,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs: List<Pair<String, ImageVector>> = listOf(
                Pair(stringResource(R.string.home), Icons.Default.Home),
                Pair(stringResource(R.string.messages), Icons.Default.ChatBubbleOutline),
                Pair(stringResource(R.string.contacts), Icons.Default.Contacts),
                Pair(stringResource(R.string.more), Icons.Default.MoreHoriz)
            )

            tabs.forEachIndexed { index, pair ->
                val isSelected = activeTab == index
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectTab(index) }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = pair.second,
                        contentDescription = pair.first,
                        tint = if (isSelected) ElectricAqua else Color(0xFF94A3B8),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = pair.first,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) ElectricAqua else Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

@Composable
fun NearbyDevicesScreen(
    viewModel: WalkieTalkieViewModel,
    onNavigateToPairing: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val peers by viewModel.peers.collectAsState(initial = emptyList())
    val pendingRequest by viewModel.pendingRequest.collectAsState()
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()
    val isAdvertising by viewModel.isAdvertising.collectAsState()
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    val transportError by viewModel.transportError.collectAsState()
    val transportType by viewModel.transportType.collectAsState()

    val wifiManager = remember {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
    }
    var isWifiEnabled by remember { mutableStateOf(wifiManager.isWifiEnabled) }
    var showFallbackBanner by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.startNearby()
        while (true) {
            isWifiEnabled = wifiManager.isWifiEnabled
            kotlinx.coroutines.delay(2000)
        }
    }

    LaunchedEffect(isWifiEnabled, peers) {
        if (!isWifiEnabled && peers.isEmpty()) {
            kotlinx.coroutines.delay(15_000)
            if (!isWifiEnabled && peers.isEmpty()) {
                showFallbackBanner = true
            }
        } else {
            showFallbackBanner = false
        }
    }

    LaunchedEffect(pendingRequest) {
        if (pendingRequest != null) {
            onNavigateToPairing()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtmosphericDarkBrush)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = stringResource(R.string.nearby_devices),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isWifiEnabled) {
                GlassSurface(
                    cornerRadius = 12.dp,
                    isOutdoorMode = isOutdoorMode,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = Color(0xFFFFB45C),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Wi-Fi radio is off",
                                color = Color(0xFFFFB45C),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Turn on Wi-Fi for faster discovery. No internet needed.",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                        TextButton(onClick = {
                            context.startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
                        }) {
                            Text("Open", color = ElectricAqua, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (showFallbackBanner && transportType == TransportType.NEARBY_CONNECTIONS) {
                GlassSurface(
                    cornerRadius = 12.dp,
                    isOutdoorMode = isOutdoorMode,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.BluetoothSearching,
                            contentDescription = null,
                            tint = ElectricAqua,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "No devices found",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Try Bluetooth-only mode?",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                        TextButton(onClick = {
                            viewModel.switchToBleTransport()
                            showFallbackBanner = false
                        }) {
                            Text("Switch", color = ElectricAqua, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            GlassSurface(
                cornerRadius = 16.dp,
                isOutdoorMode = isOutdoorMode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Visible as \"${viewModel.localDeviceName}\"",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append(if (isAdvertising) "Discoverable" else "Not discoverable")
                            append(" · ")
                            append(if (isDiscovering) "Scanning" else "Not scanning")
                            if (transportType != TransportType.NEARBY_CONNECTIONS) {
                                append(" · ")
                                append(transportType.name.replace("_", " "))
                            }
                        },
                        color = if (isAdvertising && isDiscovering) MintGreen else Color(0xFFFFB45C),
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Open this screen on both phones.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                    if (transportError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = transportError!!,
                            color = Color(0xFFFF5E77),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (peers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ElectricAqua)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.searching_devices),
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(peers) { peer ->
                        GlassSurface(
                            cornerRadius = 16.dp,
                            isOutdoorMode = isOutdoorMode,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(ElectricAqua.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = peer.name.take(2).uppercase(),
                                            color = ElectricAqua,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = peer.name,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "ID: ${peer.id.take(8)}",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                if (peer.isConnected) {
                                    Text(
                                        text = stringResource(R.string.connected),
                                        color = MintGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Button(
                                        onClick = { viewModel.connectToPeer(peer.id) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ElectricAqua,
                                            contentColor = DeepInk
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.connect),
                                            fontWeight = FontWeight.Bold
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
fun PairingScreen(
    viewModel: WalkieTalkieViewModel,
    onPaired: () -> Unit,
    onBack: () -> Unit
) {
    // State, not an event stream: this screen is mounted in response to the request, so a
    // replay-less flow would hand it nothing and leave it waiting forever.
    val request by viewModel.pendingRequest.collectAsState()
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()
    val isInSession by viewModel.isInSession.collectAsState()
    var accepted by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }

    // Accepting is only half a handshake; the link is live when the peer accepts too.
    // Guarded so a recomposition cannot pop a second entry off the back stack.
    LaunchedEffect(isInSession) {
        if (isInSession && !dismissed) {
            dismissed = true
            onPaired()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtmosphericDarkBrush)
            .safeDrawingPadding()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = stringResource(R.string.verify_identity),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            GlassSurface(
                cornerRadius = 24.dp,
                isOutdoorMode = isOutdoorMode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (request == null) {
                        CircularProgressIndicator(color = ElectricAqua)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (accepted) {
                                "Confirmed. Waiting for the other phone to confirm the same code…"
                            } else {
                                stringResource(R.string.waiting_request)
                            },
                            color = Color(0xFFCBD5E1),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.pairing_with, request!!.peerName),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = request!!.authCode,
                            color = ElectricAqua,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 36.sp,
                            letterSpacing = 4.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Compare the 6 digits with your partner's screen. If they match, tap Confirm.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    // Navigation happens when the link actually comes up,
                                    // not on the local tap alone.
                                    accepted = true
                                    viewModel.acceptConnection(request!!.peerId)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricAqua,
                                    contentColor = DeepInk
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.confirm_match),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.rejectConnection(request!!.peerId)
                                    onBack()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseActive)
                            ) {
                                Text(text = stringResource(R.string.reject))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: WalkieTalkieViewModel,
    onBack: () -> Unit
) {
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()
    val speakerphoneEnabled by viewModel.speakerphoneEnabled.collectAsState()
    var loopbackEnabled by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtmosphericDarkBrush)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            GlassSurface(
                cornerRadius = 20.dp,
                isOutdoorMode = isOutdoorMode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Outdoor mode toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.outdoor_mode),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "High-contrast opaque surfaces for sunlight",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isOutdoorMode,
                            onCheckedChange = { viewModel.toggleOutdoorMode() }
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0x22FFFFFF))

                    // Audio Loopback Test
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.test_audio_loopback),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Hear your own microphone locally",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = loopbackEnabled,
                            onCheckedChange = {
                                loopbackEnabled = it
                                viewModel.setLoopbackMode(it)
                            }
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0x22FFFFFF))

                    // Speakerphone vs Earpiece
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Audio Routing",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (speakerphoneEnabled) "Speakerphone active" else "Earpiece active",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = speakerphoneEnabled,
                            onCheckedChange = { viewModel.toggleAudioRoute() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContactsScreen(
    viewModel: WalkieTalkieViewModel,
    onBack: () -> Unit
) {
    val peers by viewModel.peers.collectAsState()
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtmosphericDarkBrush)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = stringResource(R.string.contacts),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            GlassSurface(
                cornerRadius = 20.dp,
                isOutdoorMode = isOutdoorMode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Trusted Off-Grid Contacts",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Verified peers with stored cryptographic keys. Connections reconnect automatically.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (peers.isEmpty()) {
                        Text(
                            text = "No saved contacts yet. Pair with nearby devices to verify keys.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    } else {
                        peers.forEach { peer ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = peer.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = "Verified",
                                        tint = MintGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Verified", color = MintGreen, fontSize = 11.sp)
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
fun MessagesScreen(
    viewModel: WalkieTalkieViewModel,
    onBack: () -> Unit
) {
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()
    val messages by viewModel.textMessages.collectAsState()
    val isInSession by viewModel.isInSession.collectAsState()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtmosphericDarkBrush)
            .safeDrawingPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = stringResource(R.string.messages),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!isInSession) {
                GlassSurface(
                    cornerRadius = 16.dp,
                    isOutdoorMode = isOutdoorMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "No active session. Connect to a peer to exchange encrypted off-grid messages.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            } else {
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = BrightCyan,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No messages yet.",
                                color = Color(0xFF94A3B8),
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Send a message to your connected peer.",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages, key = { it.messageId }) { msg ->
                            val isMine = msg.senderMemberId == viewModel.sessionIdForMessages
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                            ) {
                                GlassSurface(
                                    cornerRadius = 16.dp,
                                    isOutdoorMode = isOutdoorMode,
                                    modifier = Modifier.fillMaxWidth(0.75f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = if (isMine) "You" else msg.senderName,
                                            color = if (isMine) BrightCyan else ElectricAqua,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = msg.content,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = when (msg.status) {
                                                TextDeliveryStatus.SENDING -> "Sending…"
                                                TextDeliveryStatus.DELIVERED -> "Delivered"
                                                TextDeliveryStatus.FAILED -> "Failed"
                                            },
                                            color = if (msg.status == TextDeliveryStatus.FAILED)
                                                RoseActive else Color(0xFF94A3B8),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricAqua,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = Color(0xFF16233B),
                            unfocusedContainerColor = Color(0xFF16233B),
                            cursorColor = BrightCyan
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (draft.isNotBlank()) {
                                viewModel.sendTextMessage(draft.trim())
                                draft = ""
                            }
                        },
                        enabled = draft.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricAqua,
                            contentColor = DeepInk
                        ),
                        shape = CircleShape,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = DeepInk)
                    }
                }
            }
        }
    }
}

@Composable
fun CreateChannelScreen(
    viewModel: WalkieTalkieViewModel,
    onNavigateToNearby: () -> Unit,
    onBack: () -> Unit
) {
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()
    var channelName by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtmosphericDarkBrush)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = "Create Channel",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            GlassSurface(
                cornerRadius = 16.dp,
                isOutdoorMode = isOutdoorMode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Channel Name",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = channelName,
                        onValueChange = { channelName = it },
                        placeholder = { Text("e.g. Field Ops Alpha") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = ElectricAqua,
                            unfocusedBorderColor = Color(0xFF475569),
                            cursorColor = ElectricAqua,
                            focusedPlaceholderColor = Color(0xFF64748B),
                            unfocusedPlaceholderColor = Color(0xFF64748B)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You'll host this channel. Other devices can join by scanning nearby or entering a code.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val name = channelName.ifBlank { "Channel ${System.currentTimeMillis() % 1000}" }
                    viewModel.createChannel(name)
                    viewModel.startNearby()
                    onNavigateToNearby()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricAqua,
                    contentColor = DeepInk
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create & Start Advertising", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun JoinChannelScreen(
    viewModel: WalkieTalkieViewModel,
    onNavigateToNearby: () -> Unit,
    onBack: () -> Unit
) {
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()
    var shortCode by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtmosphericDarkBrush)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = "Join Channel",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            GlassSurface(
                cornerRadius = 16.dp,
                isOutdoorMode = isOutdoorMode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Enter Short Code",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = shortCode,
                        onValueChange = { shortCode = it.uppercase().take(6) },
                        placeholder = { Text("6-character code") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = ElectricAqua,
                            unfocusedBorderColor = Color(0xFF475569),
                            cursorColor = ElectricAqua,
                            focusedPlaceholderColor = Color(0xFF64748B),
                            unfocusedPlaceholderColor = Color(0xFF64748B)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ask the host for the 6-character channel code, or scan nearby to find open channels.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.setChannelName("Channel $shortCode")
                    viewModel.startNearby()
                    onNavigateToNearby()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricAqua,
                    contentColor = DeepInk
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Join with Code", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    viewModel.startNearby()
                    onNavigateToNearby()
                },
                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricAqua),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = ElectricAqua, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Nearby Instead", color = ElectricAqua, fontWeight = FontWeight.Bold)
            }
        }
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/ui/WalkieTalkieViewModel.kt`
```kotlin
package com.example.offlinewalkietalkie.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinewalkietalkie.crypto.CryptoManager
import com.example.offlinewalkietalkie.mesh.RoutingManager
import com.example.offlinewalkietalkie.protocol.FloorState
import com.example.offlinewalkietalkie.protocol.Frame
import com.example.offlinewalkietalkie.protocol.FrameType
import com.example.offlinewalkietalkie.transport.ConnectionRequest
import com.example.offlinewalkietalkie.transport.LocalIdentity
import com.example.offlinewalkietalkie.transport.Peer
import com.example.offlinewalkietalkie.transport.TransportManager
import com.example.offlinewalkietalkie.transport.TransportType
import com.example.offlinewalkietalkie.voice.VoiceEngine
import com.example.offlinewalkietalkie.channel.*
import com.example.offlinewalkietalkie.group.DiscoveredGroup
import com.example.offlinewalkietalkie.group.GroupAdvert
import com.example.offlinewalkietalkie.group.GroupMessage
import com.example.offlinewalkietalkie.group.GroupNearbyPort
import com.example.offlinewalkietalkie.group.GroupRole
import com.example.offlinewalkietalkie.group.GroupSessionManager
import com.example.offlinewalkietalkie.group.GroupState
import com.example.offlinewalkietalkie.group.isTransitional
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.*
import com.example.offlinewalkietalkie.protocol.FloorController
import com.example.offlinewalkietalkie.protocol.SessionFloorMode
import com.example.offlinewalkietalkie.service.NotificationController
import com.example.offlinewalkietalkie.service.WalkieTalkieForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

class WalkieTalkieViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "TL/ViewModel"
        private const val GROUP_TICK_INTERVAL_MS = 1_000L
    }

    private val transport = TransportManager(application)
    private val routingManager = RoutingManager(transport)
    private val voiceEngine = VoiceEngine(context = application)
    private val cryptoManager = CryptoManager()

    private val sessionId: Long = System.currentTimeMillis()

    /** viewModelScope is already cancelled by the time onCleared runs, so shutdown needs its own. */
    private val teardownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val groupPort = GroupNearbyPort(transport, sessionId)

    val groupManager = GroupSessionManager(
        transport = groupPort,
        localDisplayName = Build.MODEL
    )

    /** Authoritative view of the group: roster, roles, per-device connection state. */
    val groupState: StateFlow<GroupState> = groupManager.state

    private val _discoveredGroups = MutableStateFlow<List<DiscoveredGroup>>(emptyList())
    val discoveredGroups = _discoveredGroups.asStateFlow()

    /** Monotonic counter so no two control frames ever share a dedupe identity. */
    private val controlSequence = AtomicInteger(1)

    val localDeviceName: String = Build.MODEL

    /** Exposed so the Messages UI can tell its own messages apart from peers'. */
    val sessionIdForMessages: Long = sessionId

    val floorController = FloorController(
        localMemberId = sessionId,
        mode = SessionFloorMode.DIRECT_1_TO_1,
        scope = viewModelScope
    )
    val floorState: StateFlow<FloorState> = floorController.floorState

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers = _peers.asStateFlow()

    /**
     * Held as state, not a replay-0 event stream: the pairing screen mounts *after* the
     * request arrives, so an event-only flow would leave it waiting forever.
     */
    private val _pendingRequest = MutableStateFlow<ConnectionRequest?>(null)
    val pendingRequest = _pendingRequest.asStateFlow()

    private val _transportError = MutableStateFlow<String?>(null)
    val transportError = _transportError.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering = _isDiscovering.asStateFlow()

    private val _transportType = MutableStateFlow(TransportType.NEARBY_CONNECTIONS)
    val transportType = _transportType.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _isOutdoorMode = MutableStateFlow(false)
    val isOutdoorMode = _isOutdoorMode.asStateFlow()

    private val _isRelayModeActive = MutableStateFlow(true)
    val isRelayModeActive = _isRelayModeActive.asStateFlow()

    private val _currentChannelName = MutableStateFlow("Direct Session")
    val currentChannelName = _currentChannelName.asStateFlow()

    private val _speakerphoneEnabled = MutableStateFlow(true)
    val speakerphoneEnabled = _speakerphoneEnabled.asStateFlow()

    /** Starts idle: the dashboard must not claim an active session before one exists. */
    private val _isInSession = MutableStateFlow(false)
    val isInSession = _isInSession.asStateFlow()

    private val _securedPeerCount = MutableStateFlow(0)
    val securedPeerCount = _securedPeerCount.asStateFlow()

    /** Peers we hold a session key for, so the key can be dropped when the link goes away. */
    private val securedPeerIds = mutableSetOf<String>()

    private val _channelHostController = MutableStateFlow<ChannelHostController?>(null)
    val channelHostController = _channelHostController.asStateFlow()

    val replayBuffer = ReplayBuffer(scope = viewModelScope)
    val replayTransmissions = replayBuffer.transmissionList

    private val _textMessages = MutableStateFlow<List<TextMessage>>(emptyList())
    val textMessages = _textMessages.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val channelMembers: StateFlow<List<ChannelMember>> = _channelHostController.flatMapLatest { host ->
        host?.membersFlow ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentKeyEpoch: StateFlow<Int> = _channelHostController.flatMapLatest { host ->
        host?.currentKeyEpoch ?: flowOf(1)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    private val reliableMessageManager = ReliableMessageManager(
        sendRawPacket = { peerId, bytes ->
            transport.send(peerId, bytes)
            true
        },
        scope = viewModelScope
    )

    init {
        floorController.onTransmitPendingFrames = { frames ->
            frames.forEach { frame ->
                broadcastVoiceFrame(frame.sequence, frame.data)
            }
        }
        floorController.onFloorReleased = {
            _isRecording.value = false
            voiceEngine.stopRecording()
            sendControlFrame(null, FrameType.FLOOR_RELEASE)
        }

        voiceEngine.routeManager?.onInterruptedByCall = {
            floorController.releasePtt()
            _isRecording.value = false
            voiceEngine.stopRecording()
            voiceEngine.stopPlayback()
        }

        transport.lastError.onEach { _transportError.value = it }.launchIn(viewModelScope)
        transport.isAdvertising.onEach { _isAdvertising.value = it }.launchIn(viewModelScope)
        transport.isDiscovering.onEach { _isDiscovering.value = it }.launchIn(viewModelScope)
        transport.activeTransportType.onEach { _transportType.value = it }.launchIn(viewModelScope)

        transport.connectionRequests.onEach { request ->
            val advert = GroupAdvert.decode(request.peerName)
            Log.i(TAG, "Connection request from ${request.peerName} (${request.peerId}) group=${advert?.groupId}")
            if (advert != null) {
                // Group-aware path: same-group peers are admitted without a per-link prompt,
                // which is what makes more than two devices workable.
                groupManager.onConnectionRequest(request.peerId, advert, request.authCode)
            } else {
                // Peer on an older build with no advert: fall back to manual confirmation.
                _pendingRequest.value = request
            }
        }.launchIn(viewModelScope)

        // Drives connection timeouts, retries, heartbeats and coordinator failover.
        viewModelScope.launch {
            while (isActive) {
                delay(GROUP_TICK_INTERVAL_MS)
                groupManager.tick()
            }
        }

        transport.peers.onEach { peerList ->
            val previouslyConnected = _peers.value.count { it.isConnected }
            val previouslyConnectedIds = _peers.value.filter { it.isConnected }.map { it.id }.toSet()
            _peers.value = peerList
            val connected = peerList.filter { it.isConnected }

            feedGroupDiscovery(peerList)
            feedGroupLinkTransitions(peerList, previouslyConnectedIds)
            Log.i(TAG, "Peers updated: ${peerList.size} visible, ${connected.size} connected")

            // A dropped peer's key has to go with it, otherwise the Security tile keeps
            // counting it and a reconnecting endpoint silently reuses a stale key.
            val connectedIds = connected.mapTo(mutableSetOf()) { it.id }
            securedPeerIds.filterNot { connectedIds.contains(it) }.forEach { goneId ->
                cryptoManager.removeSession(goneId)
                securedPeerIds.remove(goneId)
            }

            connected.forEach { peer ->
                if (!cryptoManager.hasSessionKey(peer.id)) {
                    sendAuthFrame(peer.id)
                }
            }
            _securedPeerCount.value = connected.count { cryptoManager.hasSessionKey(it.id) }

            // A live link is what makes a session active; don't require a separate user step.
            if (connected.isNotEmpty() && !_isInSession.value) {
                _currentChannelName.value = connected.first().name
                setInSession(true)
            } else if (connected.isEmpty() && previouslyConnected > 0) {
                setInSession(false)
            }
        }.launchIn(viewModelScope)

        routingManager.incomingFrames.onEach { (peerId, decodedFrame) ->
            var shouldForwardControlFrame = false
            when (decodedFrame.type) {
                FrameType.AUTH -> {
                    val isNew = !cryptoManager.hasSessionKey(peerId)
                    cryptoManager.deriveSessionKey(peerId, decodedFrame.payload)
                    securedPeerIds.add(peerId)
                    _securedPeerCount.value = _peers.value.count { it.isConnected && cryptoManager.hasSessionKey(it.id) }
                    Log.i(TAG, "AUTH from $peerId - session key established (secured=${_securedPeerCount.value})")
                    if (isNew) sendAuthFrame(peerId)
                }
                FrameType.FLOOR_REQUEST -> {
                    handleFloorRequest(peerId)
                    shouldForwardControlFrame = true
                }
                FrameType.FLOOR_GRANT -> {
                    handleFloorGrant(peerId)
                    shouldForwardControlFrame = true
                }
                FrameType.FLOOR_RELEASE -> {
                    handleFloorRelease(peerId)
                    shouldForwardControlFrame = true
                }
                FrameType.VOICE -> {
                    Log.d(TAG, "VOICE frame from $peerId: encrypted=${decodedFrame.isEncrypted()} authTag=${decodedFrame.authTag != null} seq=${decodedFrame.sequence} payload=${decodedFrame.payload.size}B hasKey=${cryptoManager.hasSessionKey(peerId)}")
                    if (decodedFrame.isEncrypted() && decodedFrame.authTag != null) {
                        if (decodedFrame.sequence == 1 || decodedFrame.sequence % 50 == 0) {
                            Log.i(TAG, "Received VOICE seq=${decodedFrame.sequence} payload=${decodedFrame.payload.size}B")
                        }
                        val nonce = generateNonce(decodedFrame.sessionId, decodedFrame.sequence)
                        val dummyForAad = decodedFrame.copy(hopLimit = 0)
                        val headerAad = dummyForAad.toBytes().copyOfRange(0, Frame.HEADER_SIZE)

                        val plaintext = cryptoManager.decrypt(
                            peerId,
                            decodedFrame.payload,
                            decodedFrame.authTag,
                            nonce,
                            headerAad
                        )
                        if (plaintext != null && !isVoiceTrusted(peerId)) {
                            Log.w(TAG, "VOICE dropped from $peerId: decrypted but not group-verified")
                        } else if (plaintext != null) {
                            Log.d(TAG, "VOICE decrypt OK seq=${decodedFrame.sequence} plaintext=${plaintext.size}B")
                            voiceEngine.handleIncomingFrame(
                                peerId = peerId,
                                sequence = decodedFrame.sequence,
                                encodedOpus = plaintext,
                                senderTimestampMs = decodedFrame.timestamp.toLong()
                            )

                            if (replayBuffer.isEnabled) {
                                replayBuffer.onFrameReceived(plaintext)
                            }

                            if (decodedFrame.hopLimit > 0 && _isRelayModeActive.value) {
                                relayVoiceFrame(decodedFrame, plaintext, ingressPeerId = peerId)
                            }
                        } else {
                            Log.w(TAG, "VOICE decrypt FAILED from $peerId seq=${decodedFrame.sequence} payloadSize=${decodedFrame.payload.size} authTagSize=${decodedFrame.authTag?.size}")
                        }
                    } else {
                        Log.w(TAG, "VOICE frame NOT encrypted or missing authTag from $peerId: encrypted=${decodedFrame.isEncrypted()} authTag=${decodedFrame.authTag}")
                    }
                }
                FrameType.FLOOR_STATUS -> {
                    if (decodedFrame.payload.size >= 8) {
                        val buf = ByteBuffer.wrap(decodedFrame.payload)
                        val holderId = buf.getLong()
                        val holderNameBytes = ByteArray(buf.remaining())
                        buf.get(holderNameBytes)
                        val holderName = String(holderNameBytes, Charsets.UTF_8)
                        if (holderId == 0L) {
                            floorController.onFloorFreed()
                            replayBuffer.onTransmissionCompleted()
                        } else {
                            floorController.onFloorHeldByOther(holderId.toString(), holderName)
                            replayBuffer.onTransmissionStarted(holderId, holderName)
                        }
                    }
                    shouldForwardControlFrame = true
                }
                FrameType.TEXT -> {
                    val content = String(decodedFrame.payload, Charsets.UTF_8)
                    val peerName = _peers.value.find { it.id == peerId }?.name ?: "Peer"
                    Log.i(TAG, "Received TEXT from $peerName seq=${decodedFrame.sequence}: \"${content.take(40)}\"")
                    val msg = TextMessage(
                        sequence = decodedFrame.sequence,
                        senderMemberId = decodedFrame.sessionId,
                        senderName = peerName,
                        content = content,
                        status = TextDeliveryStatus.DELIVERED
                    )
                    _textMessages.value = _textMessages.value + msg
                    val ack = Frame(
                        type = FrameType.ACK,
                        sequence = decodedFrame.sequence,
                        sessionId = sessionId,
                        timestamp = System.currentTimeMillis().toInt()
                    )
                    transport.send(peerId, ack.toBytes())
                }
                FrameType.ACK -> {
                    reliableMessageManager.onAckReceived(decodedFrame.sessionId, decodedFrame.sequence)
                }
                FrameType.KEY_UPDATE -> {
                    if (decodedFrame.payload.isNotEmpty()) {
                        cryptoManager.deriveSessionKey(peerId, decodedFrame.payload)
                    }
                }
                FrameType.GROUP_CONTROL -> {
                    handleGroupControl(peerId, decodedFrame.payload)
                }
                else -> {}
            }
            if (shouldForwardControlFrame && !decodedFrame.isEncrypted() && decodedFrame.hopLimit > 0) {
                val fwdBytes = decodedFrame.copy(hopLimit = (decodedFrame.hopLimit - 1).toByte()).toBytes()
                _peers.value.filter { it.isConnected && it.id != peerId }.forEach {
                    transport.send(it.id, fwdBytes)
                }
            }
        }.launchIn(viewModelScope)
    }

    /**
     * Gates voice on group verification, not raw transport connectivity. A link the
     * group manager has accepted but not yet HMAC-verified reports isConnected=true at
     * the transport layer well before it is trustworthy — without this check that window
     * would carry live audio to (or from) a peer that turns out to be an impostor.
     */
    private fun isVoiceTrusted(peerId: String): Boolean {
        val group = groupState.value
        if (group.isInGroup) return group.isTrustedEndpoint(peerId)
        // No group formed: this is the legacy direct-pairing flow, gated by its own
        // SAS-confirmed session key.
        return cryptoManager.hasSessionKey(peerId)
    }

    private suspend fun relayVoiceFrame(
        decodedFrame: Frame,
        plaintext: ByteArray,
        ingressPeerId: String
    ) {
        val nextHopLimit = (decodedFrame.hopLimit - 1).toByte()
        val activePeers = _peers.value.filter {
            it.isConnected && it.id != ingressPeerId && isVoiceTrusted(it.id)
        }
        activePeers.forEach { targetPeer ->
            if (!cryptoManager.hasSessionKey(targetPeer.id)) return@forEach
            val dummyFwd = Frame(
                type = FrameType.VOICE,
                flags = Frame.FLAG_ENCRYPTED,
                hopLimit = 0,
                sessionId = decodedFrame.sessionId,
                sequence = decodedFrame.sequence,
                timestamp = decodedFrame.timestamp,
                payload = ByteArray(plaintext.size)
            )
            val fwdAad = dummyFwd.toBytes().copyOfRange(0, Frame.HEADER_SIZE)
            val fwdNonce = generateNonce(decodedFrame.sessionId, decodedFrame.sequence)
            val encrypted = cryptoManager.encrypt(targetPeer.id, plaintext, fwdNonce, fwdAad)
            if (encrypted != null) {
                val fwdFrame = dummyFwd.copy(
                    hopLimit = nextHopLimit,
                    payload = encrypted.first,
                    authTag = encrypted.second
                )
                transport.send(targetPeer.id, fwdFrame.toBytes())
            }
        }
    }

    private fun handleFloorRequest(peerId: String) {
        val current = floorController.floorState.value
        val peerName = _peers.value.find { it.id == peerId }?.name ?: "Unknown"
        if (current is FloorState.Idle || (current is FloorState.HeldByOther && current.peerId == peerId)) {
            floorController.onFloorHeldByOther(peerId, peerName)
            sendControlFrame(peerId, FrameType.FLOOR_GRANT)
        } else if (current is FloorState.Talking || current is FloorState.HeldByMe) {
            val peerMemberId = peerId.hashCode().toLong() and 0xFFFFFFFFL
            val localWon = floorController.handleDirectCollision(peerMemberId, System.currentTimeMillis())
            if (!localWon) {
                floorController.onFloorHeldByOther(peerId, peerName)
            }
        }
    }

    private fun handleFloorGrant(peerId: String) {
        // Only a grant we actually asked for may open the microphone. Without this guard a
        // direct 1:1 grant would start a second capture loop on top of the running one.
        if (_isRecording.value) return
        if (floorController.floorState.value !is FloorState.Requesting) return

        floorController.onFloorGranted()
        _isRecording.value = true
        voiceEngine.startRecording { sequence, encodedFrame ->
            broadcastVoiceFrame(sequence, encodedFrame)
        }
    }

    private fun handleFloorRelease(peerId: String) {
        floorController.onFloorFreed()
    }

    private fun broadcastVoiceFrame(sequence: Int, encodedFrame: ByteArray) {
        viewModelScope.launch {
            val activePeers = _peers.value.filter { it.isConnected && isVoiceTrusted(it.id) }
            if (activePeers.isNotEmpty() && (sequence == 1 || sequence % 50 == 0)) {
                Log.i(TAG, "Sending VOICE frame $sequence to ${activePeers.size} peer(s), payload=${encodedFrame.size}B")
            }
            activePeers.forEach { peer ->
                if (cryptoManager.hasSessionKey(peer.id)) {
                    val nonce = generateNonce(sessionId, sequence)
                    val dummyFrame = Frame(
                        type = FrameType.VOICE,
                        flags = Frame.FLAG_ENCRYPTED,
                        hopLimit = 0,
                        sessionId = sessionId,
                        sequence = sequence,
                        timestamp = System.currentTimeMillis().toInt(),
                        payload = ByteArray(encodedFrame.size)
                    )
                    val headerAad = dummyFrame.toBytes().copyOfRange(0, Frame.HEADER_SIZE)

                    val encrypted = cryptoManager.encrypt(peer.id, encodedFrame, nonce, headerAad)
                    if (encrypted != null) {
                        val frame = dummyFrame.copy(
                            hopLimit = 3,
                            payload = encrypted.first,
                            authTag = encrypted.second
                        )
                        transport.send(peer.id, frame.toBytes())
                    }
                }
            }
        }
    }

    private fun sendControlFrame(targetPeerId: String?, type: FrameType) {
        viewModelScope.launch {
            // Control frames carry no sequence of their own, so without a unique one here
            // every FLOOR_RELEASE would hash identically and be dropped as a duplicate
            // after the very first transmission.
            val frame = Frame(
                type = type,
                sessionId = sessionId,
                sequence = controlSequence.getAndIncrement(),
                timestamp = System.currentTimeMillis().toInt(),
                hopLimit = 3
            )
            if (targetPeerId != null) {
                routingManager.sendTo(targetPeerId, frame)
            } else {
                routingManager.markAsSeen(frame)
                _peers.value.filter { it.isConnected }.forEach { peer ->
                    transport.send(peer.id, frame.toBytes())
                }
            }
        }
    }

    private fun sendAuthFrame(peerId: String) {
        viewModelScope.launch {
            val pubKey = cryptoManager.getPublicKeyBytes()
            val frame = Frame(
                type = FrameType.AUTH,
                payload = pubKey,
                sessionId = sessionId,
                sequence = controlSequence.getAndIncrement(),
                timestamp = System.currentTimeMillis().toInt(),
                hopLimit = 1
            )
            Log.i(TAG, "Sending AUTH to $peerId (${pubKey.size} bytes)")
            routingManager.sendTo(peerId, frame)
        }
    }

    // ------------------------------------------------------------------ group plumbing

    /**
     * Turns raw endpoints into group knowledge: peers advertising our group id go to the
     * session manager, everyone else is bucketed into the "groups you can join" list.
     */
    private suspend fun feedGroupDiscovery(peerList: List<Peer>) {
        val adverts = peerList.mapNotNull { peer ->
            GroupAdvert.decode(peer.name)?.let { peer to it }
        }

        val ourGroupId = groupState.value.group?.groupId
        adverts.forEach { (peer, advert) ->
            if (advert.groupId == ourGroupId) {
                groupManager.onEndpointDiscovered(peer.id, advert)
            }
        }

        // Only a coordinator's advert can be joined; members cannot admit newcomers.
        _discoveredGroups.value = adverts
            .filter { (_, advert) -> advert.groupId != ourGroupId }
            .groupBy { (_, advert) -> advert.groupId }
            .mapNotNull { (groupId, entries) ->
                val coordinator = entries.firstOrNull { it.second.role == GroupRole.COORDINATOR }
                    ?: return@mapNotNull null
                DiscoveredGroup(
                    groupId = groupId,
                    name = coordinator.second.displayName,
                    coordinatorMemberId = coordinator.second.memberId,
                    coordinatorEndpointId = coordinator.first.id,
                    devicesVisible = entries.size
                )
            }
    }

    /** Translates appearing/disappearing links into group connection events. */
    private suspend fun feedGroupLinkTransitions(
        peerList: List<Peer>,
        previouslyConnectedIds: Set<String>
    ) {
        val nowIds = peerList.map { it.id }.toSet()
        val nowConnectedIds = peerList.filter { it.isConnected }.map { it.id }.toSet()

        (nowConnectedIds - previouslyConnectedIds).forEach { groupManager.onConnected(it) }
        (previouslyConnectedIds - nowConnectedIds).forEach { groupManager.onDisconnected(it) }

        // A connection that disappears from the transport's peer list without ever
        // reaching isConnected=true was declined, timed out, or the radio dropped it
        // mid-handshake. Without this the group manager's CONNECTING/VERIFYING entry
        // would sit stuck forever instead of retrying or surfacing the failure.
        val everKnown = groupState.value.others
            .filter { it.endpointId != null && it.state.isTransitional }
            .map { it.memberId to it.endpointId!! }
        everKnown.forEach { (_, endpointId) ->
            if (endpointId !in nowIds && endpointId !in nowConnectedIds) {
                groupManager.onConnectionFailed(endpointId, "Connection was lost")
            }
        }
    }

    private suspend fun handleGroupControl(peerId: String, payload: ByteArray) {
        val message = GroupMessage.decode(payload)
        if (message == null) {
            Log.w(TAG, "undecodable GROUP_CONTROL from $peerId (${payload.size}B)")
            return
        }
        Log.i(TAG, "GROUP_CONTROL ${message::class.simpleName} from $peerId")
        groupManager.onControlMessage(peerId, message)
    }

    // ------------------------------------------------------------------- group actions

    fun createGroup(name: String) {
        viewModelScope.launch {
            Log.i(TAG, "createGroup '$name'")
            _currentChannelName.value = name
            groupManager.createGroup(name)
            transport.startDiscovery(LocalIdentity(localDeviceName))
        }
    }

    fun joinGroup(group: DiscoveredGroup) {
        viewModelScope.launch {
            Log.i(TAG, "joinGroup '${group.name}' via ${group.coordinatorEndpointId}")
            _currentChannelName.value = group.name
            groupManager.joinGroup(
                GroupAdvert(
                    groupId = group.groupId,
                    memberId = group.coordinatorMemberId,
                    role = GroupRole.COORDINATOR,
                    displayName = group.name
                ),
                group.coordinatorEndpointId
            )
        }
    }

    fun respondToGroupRequest(memberId: String, accept: Boolean) {
        viewModelScope.launch { groupManager.respondToRequest(memberId, accept) }
    }

    fun leaveGroup() {
        viewModelScope.launch {
            groupManager.leaveGroup()
            setInSession(false)
            securedPeerIds.forEach { cryptoManager.removeSession(it) }
            securedPeerIds.clear()
            _securedPeerCount.value = 0
            transport.stop()
        }
    }

    /** Drops one device from the group without tearing the rest of it down. */
    fun removeGroupMember(memberId: String) {
        viewModelScope.launch {
            val endpointId = groupState.value.member(memberId)?.endpointId ?: return@launch
            cryptoManager.removeSession(endpointId)
            securedPeerIds.remove(endpointId)
            transport.disconnect(endpointId)
        }
    }

    private fun generateNonce(sessId: Long, seq: Int): ByteArray {
        val bb = ByteBuffer.allocate(12)
        bb.putLong(sessId)
        bb.putInt(seq)
        return bb.array()
    }

    fun setTransportType(type: TransportType) {
        _transportType.value = type
        transport.setTransportType(type)
    }

    /**
     * Advertise and discover at the same time so two phones running the same screen find
     * each other without either user having to pick a "host" or "join" role first.
     */
    fun startNearby() {
        viewModelScope.launch {
            transport.startSymmetricDiscovery(LocalIdentity(localDeviceName))
        }
    }

    fun switchToBleTransport() {
        Log.i(TAG, "Switching to BLE L2CAP transport (Wi-Fi off fallback)")
        viewModelScope.launch {
            transport.stop()
            transport.setTransportType(TransportType.BLE_L2CAP)
            _transportType.value = TransportType.BLE_L2CAP
            transport.startSymmetricDiscovery(LocalIdentity(localDeviceName))
        }
    }

    fun startDiscovery() {
        viewModelScope.launch { transport.startDiscovery(LocalIdentity(localDeviceName)) }
    }

    fun startAdvertising() {
        viewModelScope.launch { transport.startAdvertising(LocalIdentity(localDeviceName)) }
    }

    fun connectToPeer(peerId: String) {
        Log.i(TAG, "User tapped connect -> $peerId")
        viewModelScope.launch { transport.connect(peerId) }
    }

    fun acceptConnection(peerId: String) {
        viewModelScope.launch {
            transport.acceptConnection(peerId)
            _pendingRequest.value = null
        }
    }

    fun rejectConnection(peerId: String) {
        viewModelScope.launch {
            transport.rejectConnection(peerId)
            _pendingRequest.value = null
        }
    }

    fun clearPendingRequest() {
        _pendingRequest.value = null
    }

    fun setLoopbackMode(enabled: Boolean) {
        voiceEngine.isLoopback = enabled
    }

    fun toggleOutdoorMode() {
        _isOutdoorMode.value = !_isOutdoorMode.value
    }

    fun toggleRelayMode() {
        _isRelayModeActive.value = !_isRelayModeActive.value
    }

    fun toggleAudioRoute() {
        val newState = !_speakerphoneEnabled.value
        _speakerphoneEnabled.value = newState
        voiceEngine.speakerphoneEnabled = newState
        voiceEngine.setSpeakerphone(newState)
    }

    fun setInSession(inSession: Boolean) {
        _isInSession.value = inSession
        if (inSession) {
            startBackgroundService()
            // The handshake is over; a leftover request would send the pairing screen
            // straight back up the moment it is dismissed.
            _pendingRequest.value = null
            voiceEngine.routeManager?.requestAudioFocus()
            voiceEngine.setSpeakerphone(_speakerphoneEnabled.value)
            Log.i(TAG, "Session started: speaker=${_speakerphoneEnabled.value}")
        } else {
            stopBackgroundService()
            voiceEngine.routeManager?.clearRouting()
        }
    }

    fun setChannelName(name: String) {
        _currentChannelName.value = name
    }

    fun createChannel(name: String, relayPolicy: RelayPolicy = RelayPolicy.OFF) {
        _currentChannelName.value = name
        val hostCtrl = ChannelHostController(
            channelId = sessionId,
            channelName = name,
            hostFingerprint = cryptoManager.getPublicKeyBytes().take(6).toByteArray().joinToString("") { "%02X".format(it) },
            hostDisplayName = Build.MODEL,
            relayPolicy = relayPolicy,
            scope = viewModelScope
        )
        floorController.mode = SessionFloorMode.CHANNEL_HOST_CONTROLLED
        hostCtrl.onFloorStatusChanged = { holderId, holderName ->
            if (holderId == 0L) {
                floorController.onFloorFreed()
            } else if (holderId != hostCtrl.hostMemberId) {
                floorController.onFloorHeldByOther(holderId.toString(), holderName)
            }
            viewModelScope.launch {
                val nameBytes = holderName.toByteArray(Charsets.UTF_8)
                val payload = ByteBuffer.allocate(8 + nameBytes.size)
                    .putLong(holderId)
                    .put(nameBytes)
                    .array()
                val statusFrame = Frame(
                    type = FrameType.FLOOR_STATUS,
                    payload = payload,
                    sessionId = sessionId,
                    sequence = controlSequence.getAndIncrement(),
                    timestamp = System.currentTimeMillis().toInt()
                )
                _peers.value.filter { it.isConnected }.forEach { transport.send(it.id, statusFrame.toBytes()) }
            }
        }

        hostCtrl.onKeyEpochRotated = { newEpoch, newKey, authorized ->
            viewModelScope.launch {
                authorized.filter { it.role != MemberRole.HOST && it.endpointId != null }.forEach { member ->
                    val frame = Frame(
                        type = FrameType.KEY_UPDATE,
                        payload = newKey,
                        sessionId = sessionId,
                        sequence = newEpoch,
                        timestamp = System.currentTimeMillis().toInt()
                    )
                    member.endpointId?.let { ep -> transport.send(ep, frame.toBytes()) }
                }
            }
        }

        _channelHostController.value = hostCtrl
        startNearby()
        setInSession(true)
    }

    fun admitParticipantBySecret(displayName: String, role: MemberRole, secretOrCode: String): Boolean {
        val host = _channelHostController.value ?: return false
        val member = host.admitMember(displayName, role, null, secretOrCode)
        return member != null
    }

    fun toggleReplayBuffer(enabled: Boolean) {
        replayBuffer.isEnabled = enabled
    }

    fun playLatestReplay() {
        replayBuffer.playLatest()
    }

    fun sendTextMessage(content: String) {
        val seq = controlSequence.getAndIncrement()
        Log.i(TAG, "sendTextMessage \"${content.take(40)}\" to ${_peers.value.count { it.isConnected }} peer(s)")
        val textMsg = TextMessage(
            sequence = seq,
            senderMemberId = sessionId,
            senderName = Build.MODEL,
            content = content,
            status = TextDeliveryStatus.DELIVERED
        )
        _textMessages.value = _textMessages.value + textMsg

        val payload = content.toByteArray(Charsets.UTF_8)
        val frame = Frame(
            type = FrameType.TEXT,
            payload = payload,
            sequence = seq,
            sessionId = sessionId,
            timestamp = System.currentTimeMillis().toInt()
        )
        _peers.value.filter { it.isConnected }.forEach {
            reliableMessageManager.sendReliable(
                memberId = sessionId,
                sequence = seq,
                endpointId = it.id,
                packetBytes = frame.toBytes()
            )
        }
    }

    fun requestPtt() {
        val current = floorController.floorState.value
        if (current !is FloorState.Idle) return
        if (_isRecording.value) return

        val connected = _peers.value.filter { it.isConnected }
        if (connected.isEmpty() || floorController.mode == SessionFloorMode.DIRECT_1_TO_1) {
            Log.i(TAG, "PTT start (direct) -> ${connected.size} peer(s)")
            floorController.requestPtt()
            _isRecording.value = true
            voiceEngine.startRecording { sequence, encodedFrame ->
                broadcastVoiceFrame(sequence, encodedFrame)
            }
        } else {
            Log.i(TAG, "PTT start (channel) -> requesting floor")
            floorController.requestPtt()
            sendControlFrame(null, FrameType.FLOOR_REQUEST)
            _isRecording.value = true
            voiceEngine.startRecording { sequence, encodedFrame ->
                floorController.onAudioCaptured(sequence, System.currentTimeMillis(), encodedFrame)
            }
        }
    }

    fun releasePtt() {
        if (!_isRecording.value && floorController.floorState.value is FloorState.Idle) return
        Log.i(TAG, "PTT stop")
        floorController.releasePtt()
        _isRecording.value = false
        voiceEngine.stopRecording()
        sendControlFrame(null, FrameType.FLOOR_RELEASE)
    }

    private fun startBackgroundService() {
        try {
            val app = getApplication<Application>()
            val intent = Intent(app, WalkieTalkieForegroundService::class.java).apply {
                action = NotificationController.ACTION_START
                putExtra(NotificationController.EXTRA_SESSION_NAME, _currentChannelName.value)
                putExtra(NotificationController.EXTRA_STATUS, "Encrypted link active")
                putExtra(NotificationController.EXTRA_SPEAKER, "Floor: Idle")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Foreground service start failed: ${e.message}")
        }
    }

    private fun stopBackgroundService() {
        try {
            val app = getApplication<Application>()
            val intent = Intent(app, WalkieTalkieForegroundService::class.java).apply {
                action = NotificationController.ACTION_STOP
            }
            app.startService(intent)
        } catch (e: Exception) {
            // Service was not running.
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopBackgroundService()
        voiceEngine.stopRecording()
        voiceEngine.stopPlayback()
        voiceEngine.routeManager?.clearRouting()
        teardownScope.launch { transport.stop() }
    }

    fun leaveSession() {
        voiceEngine.stopRecording()
        voiceEngine.stopPlayback()
        // Routed through setInSession so the audio mode and volumes are handed back;
        // setting the flag directly leaves the device in MODE_IN_COMMUNICATION.
        setInSession(false)
        securedPeerIds.forEach { cryptoManager.removeSession(it) }
        securedPeerIds.clear()
        _securedPeerCount.value = 0
        viewModelScope.launch { transport.stop() }
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/ui/components/GlassSurface.kt`
```kotlin
package com.example.offlinewalkietalkie.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.offlinewalkietalkie.theme.*

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    isOutdoorMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    if (isOutdoorMode || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        // Solid high-contrast / API 29-30 fallback
        Box(
            modifier = modifier
                .clip(shape)
                .background(if (isOutdoorMode) OutdoorCardBackground else Color(0xEB1E293B))
                .border(
                    BorderStroke(
                        width = if (isOutdoorMode) 2.dp else 1.dp,
                        color = if (isOutdoorMode) OutdoorCardBorder else Color(0x40FFFFFF)
                    ),
                    shape = shape
                )
        ) {
            content()
        }
    } else {
        // API 31+ Translucent Glass with blur
        Box(
            modifier = modifier
                .clip(shape)
                .background(GlassBackground)
                .border(BorderStroke(1.dp, GlassBorder), shape = shape)
        ) {
            content()
        }
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/ui/components/PttOrb.kt`
```kotlin
package com.example.offlinewalkietalkie.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinewalkietalkie.R
import com.example.offlinewalkietalkie.protocol.FloorState
import com.example.offlinewalkietalkie.theme.*

@Composable
fun PttOrb(
    floorState: FloorState,
    isOutdoorMode: Boolean = false,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    val isTalking = floorState is FloorState.HeldByMe || floorState is FloorState.Talking || floorState is FloorState.Granted
    val isRequesting = floorState is FloorState.Requesting
    val isHeldByOther = floorState is FloorState.HeldByOther
    // pointerInput(Unit) builds its gesture block once, so reading isHeldByOther directly
    // inside it would pin the value from first composition.
    val heldByOther by rememberUpdatedState(isHeldByOther)

    val infiniteTransition = rememberInfiniteTransition(label = "ptt_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isTalking) 1.08f else if (isRequesting) 1.04f else 1.01f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val currentBrush = when {
        isTalking -> PttTalkingBrush
        else -> PttReadyBrush
    }

    val stateText = when (floorState) {
        is FloorState.HeldByMe, is FloorState.Talking, is FloorState.Granted -> stringResource(R.string.talking)
        is FloorState.Requesting -> stringResource(R.string.requesting_talk)
        is FloorState.HeldByOther -> stringResource(R.string.peer_is_speaking, floorState.peerName)
        is FloorState.Denied -> "Floor Denied"
        is FloorState.Releasing -> "Releasing..."
        is FloorState.Idle -> stringResource(R.string.hold_to_talk)
    }

    val targetSize = if (isPressed) 184.dp else 176.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(200.dp)
            .semantics {
                contentDescription = "Push to Talk button. Status: $stateText. Double tap or hold to talk."
            }
    ) {
        // Outer glow/pulse ring
        Box(
            modifier = Modifier
                .size(192.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .border(
                    width = if (isTalking) 2.dp else 1.dp,
                    color = if (isTalking) RoseActive.copy(alpha = 0.6f) else ElectricAqua.copy(alpha = 0.3f),
                    shape = CircleShape
                )
        )

        // Main 176dp Orb
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(targetSize)
                .clip(CircleShape)
                .background(
                    when {
                        isOutdoorMode && isTalking -> RoseActive
                        isOutdoorMode -> ElectricAqua
                        else -> Color.Transparent
                    }
                )
                .then(
                    if (!isOutdoorMode) Modifier.background(currentBrush) else Modifier
                )
                .border(2.dp, Color(0x66FFFFFF), CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            if (!heldByOther) {
                                isPressed = true
                                onPressStart()
                                tryAwaitRelease()
                                isPressed = false
                                onPressEnd()
                            }
                        }
                    )
                }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                // Mic Icon Badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .border(1.dp, Color(0x4DFFFFFF), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stateText,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/util/CapabilityManager.kt`
```kotlin
package com.example.offlinewalkietalkie.util

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

object CapabilityManager {

    fun isGooglePlayServicesAvailable(context: Context): Boolean {
        return try {
            val availability = GoogleApiAvailability.getInstance()
            val resultCode = availability.isGooglePlayServicesAvailable(context)
            resultCode == ConnectionResult.SUCCESS
        } catch (e: Throwable) {
            false
        }
    }

    fun isBleSupported(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    fun isWifiDirectSupported(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
    }

    fun isWifiAwareSupported(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter?.isEnabled == true
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/util/PermissionManager.kt`
```kotlin
package com.example.offlinewalkietalkie.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionManager {

    /**
     * Nearby Connections permission matrix. Getting this wrong is silent: the API simply
     * reports MISSING_PERMISSION and no endpoint is ever discovered.
     *
     *  - API 29..31  ACCESS_FINE_LOCATION is mandatory for BLE scanning.
     *  - API 31+     BLUETOOTH_SCAN / ADVERTISE / CONNECT replace the legacy Bluetooth perms.
     *  - API 33+     NEARBY_WIFI_DEVICES is required for the Wi-Fi upgrade path.
     */
    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }

        // Still required on Android 12 (API 31) itself, alongside the new Bluetooth permissions.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        return permissions
    }

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun isAudioPermissionGranted(context: Context): Boolean =
        isGranted(context, Manifest.permission.RECORD_AUDIO)

    /**
     * Every permission Nearby Connections needs on this API level, not just one of them.
     * A partial grant discovers nothing, so treating "any" as success hides the failure.
     */
    fun isRadioPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val bluetooth = isGranted(context, Manifest.permission.BLUETOOTH_SCAN) &&
                isGranted(context, Manifest.permission.BLUETOOTH_CONNECT) &&
                isGranted(context, Manifest.permission.BLUETOOTH_ADVERTISE)
            val wifi = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                isGranted(context, Manifest.permission.NEARBY_WIFI_DEVICES)
            val location = Build.VERSION.SDK_INT > Build.VERSION_CODES.S ||
                isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
            bluetooth && wifi && location
        } else {
            isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    /** The permissions still missing, for a targeted re-request. */
    fun missingPermissions(context: Context): List<String> =
        getRequiredPermissions().filterNot { isGranted(context, it) }

    fun hasAllPermissions(context: Context): Boolean =
        isAudioPermissionGranted(context) && isRadioPermissionGranted(context)
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/voice/AudioCapture.kt`
```kotlin
package com.example.offlinewalkietalkie.voice

import android.annotation.SuppressLint
import android.util.Log
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Section 6 Audio Capture with VOICE_COMMUNICATION, 48kHz mono PCM 16-bit,
 * and hardware AcousticEchoCanceler, NoiseSuppressor, AutomaticGainControl attachments.
 */
class AudioCapture {
    companion object {
        const val TAG = "TL/AudioCapture"
        const val SAMPLE_RATE = 48000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    private val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    // 20ms frames: 48000 samples/sec * 0.02 sec = 960 samples = 1920 bytes
    private val frameSize = 960 * 2

    @SuppressLint("MissingPermission")
    fun start(onFrame: (ByteArray) -> Unit) {
        val bufferSize = maxOf(minBufferSize * 4, frameSize * 10)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "AudioRecord failed to initialize (state=${audioRecord?.state}, minBuffer=$minBufferSize)")
            audioRecord?.release()
            audioRecord = null
            return
        }
        val savedSession = audioRecord?.audioSessionId ?: 0
        Log.i(TAG, "AudioRecord initialized, session=$savedSession, buffer=$bufferSize")

        val sessionId = audioRecord?.audioSessionId ?: 0
        if (sessionId != 0) {
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                        enabled = true
                    }
                }
            } catch (e: Throwable) {}

            try {
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                        enabled = true
                    }
                }
            } catch (e: Throwable) {}

            try {
                if (AutomaticGainControl.isAvailable()) {
                    gainControl = AutomaticGainControl.create(sessionId)?.apply {
                        enabled = true
                    }
                }
            } catch (e: Throwable) {}
        }

        audioRecord?.startRecording()
    }

    suspend fun captureLoop(onFrame: (ByteArray) -> Unit) = withContext(Dispatchers.IO) {
        val readBuffer = ByteArray(frameSize)
        var accumulationBuffer = ByteArray(0)
        
        while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val read = audioRecord?.read(readBuffer, 0, frameSize, AudioRecord.READ_BLOCKING) ?: 0
            if (read > 0) {
                accumulationBuffer += readBuffer.copyOfRange(0, read)
                while (accumulationBuffer.size >= frameSize) {
                    val frame = accumulationBuffer.copyOfRange(0, frameSize)
                    accumulationBuffer = accumulationBuffer.copyOfRange(frameSize, accumulationBuffer.size)
                    onFrame(frame)
                }
            }
        }
    }

    fun stop() {
        try {
            audioRecord?.stop()
        } catch (e: Throwable) {}

        try {
            echoCanceler?.release()
        } catch (e: Throwable) {}
        try {
            noiseSuppressor?.release()
        } catch (e: Throwable) {}
        try {
            gainControl?.release()
        } catch (e: Throwable) {}
        echoCanceler = null
        noiseSuppressor = null
        gainControl = null

        audioRecord?.release()
        audioRecord = null
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/voice/AudioCodec.kt`
```kotlin
package com.example.offlinewalkietalkie.voice

interface AudioCodec {
    fun encode(pcm: ShortArray): ByteArray
    fun decode(encoded: ByteArray): ShortArray
    fun decodeLostFrame(): ShortArray
    fun reset()
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/voice/AudioPlayback.kt`
```kotlin
package com.example.offlinewalkietalkie.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

class AudioPlayback {
    companion object {
        private const val TAG = "TL/AudioPlayback"
        const val SAMPLE_RATE = 48000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    /**
     * The route toggle runs on the UI thread while the playback loop writes on an IO thread.
     * Without this guard the toggle can release the track mid-write, which is undefined
     * behaviour in the native layer.
     */
    private val lock = Any()

    private var audioTrack: AudioTrack? = null
    private val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    private var frameCount = 0
    private var skippedFrameCount = 0
    private var currentUseSpeaker: Boolean? = null

    fun start(useSpeaker: Boolean = true) {
        synchronized(lock) {
            if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING && currentUseSpeaker == useSpeaker) {
                return@synchronized
            }
            releaseLocked()
            try {
                val safeMin = if (minBufferSize > 0) minBufferSize else 3840
                val bufferSize = maxOf(safeMin, 960 * 2 * 2)

                // Samsung and some other OEMs ignore setCommunicationDevice for speaker routing
                // when the track uses USAGE_VOICE_COMMUNICATION, so speaker mode goes out as media.
                val usage = if (useSpeaker) {
                    AudioAttributes.USAGE_MEDIA
                } else {
                    AudioAttributes.USAGE_VOICE_COMMUNICATION
                }
                val contentType = if (useSpeaker) {
                    AudioAttributes.CONTENT_TYPE_MUSIC
                } else {
                    AudioAttributes.CONTENT_TYPE_SPEECH
                }

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(usage)
                            .setContentType(contentType)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG)
                            .setEncoding(AUDIO_FORMAT)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()
                audioTrack = track
                frameCount = 0
                skippedFrameCount = 0
                currentUseSpeaker = useSpeaker
                Log.i(
                    TAG,
                    "AudioTrack started: useSpeaker=$useSpeaker usage=${if (useSpeaker) "MEDIA" else "VOICE_COMMUNICATION"} " +
                        "state=${track.state} playState=${track.playState} bufferSize=$bufferSize sessionId=${track.audioSessionId}"
                )
            } catch (e: Throwable) {
                Log.e(TAG, "AudioTrack start FAILED", e)
            }
        }
    }

    fun playFrame(pcmBytes: ByteArray) {
        synchronized(lock) {
            val track = audioTrack ?: return@synchronized
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                skippedFrameCount++
                if (skippedFrameCount == 1 || skippedFrameCount % 100 == 0) {
                    Log.w(TAG, "playFrame skipped x$skippedFrameCount: playState=${track.playState}")
                }
                return@synchronized
            }
            try {
                val written = track.write(pcmBytes, 0, pcmBytes.size)
                frameCount++
                if (frameCount == 1 || frameCount % 50 == 0) {
                    Log.i(TAG, "playFrame #$frameCount: wrote $written/${pcmBytes.size} bytes, maxAmp=${peakAmplitude(pcmBytes)}")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "playFrame failed", e)
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            Log.i(TAG, "stop (played $frameCount frames)")
            releaseLocked()
            frameCount = 0
            skippedFrameCount = 0
        }
    }

    private fun releaseLocked() {
        val track = audioTrack ?: return
        audioTrack = null
        currentUseSpeaker = null
        try {
            track.stop()
        } catch (e: Throwable) {
            Log.w(TAG, "AudioTrack stop error: ${e.message}")
        }
        try {
            track.release()
        } catch (e: Throwable) {
        }
    }

    private fun peakAmplitude(pcmBytes: ByteArray): Int {
        var peak = 0
        for (i in 0 until pcmBytes.size - 1 step 2) {
            val sample = (pcmBytes[i].toInt() and 0xFF or (pcmBytes[i + 1].toInt() shl 8)).toShort().toInt()
            val magnitude = if (sample < 0) -sample else sample
            if (magnitude > peak) peak = magnitude
        }
        return peak
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/voice/AudioRouteManager.kt`
```kotlin
package com.example.offlinewalkietalkie.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Section 6 Audio Route & Focus Manager.
 *
 * Manages:
 * - Speakerphone vs Earpiece routing via AudioManager.setCommunicationDevice on API 31+,
 *   with fallback on older supported APIs.
 * - Transient audio focus requesting for walkie-talkie sessions.
 * - Phone call interruption detection: releases floor, stops capture & playback.
 */
class AudioRouteManager(private val context: Context) {

    private val TAG = "TL/AudioRoute"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var focusGranted = false

    /** Volume levels as the user had them, so a session can hand them back on teardown. */
    private var savedMusicVolume: Int? = null
    private var savedVoiceCallVolume: Int? = null

    var onInterruptedByCall: (() -> Unit)? = null
    var onFocusRegained: (() -> Unit)? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Incoming phone call or another app took audio focus
                onInterruptedByCall?.invoke()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                onFocusRegained?.invoke()
            }
        }
    }

    /**
     * Request transient audio focus for walkie-talkie voice playback.
     */
    fun requestAudioFocus(): Boolean {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        // Re-requesting builds a fresh AudioFocusRequest that replaces the tracked one,
        // leaving the previous request permanently un-abandoned.
        if (focusGranted) return true
        Log.i(TAG, "requestAudioFocus: mode set to MODE_IN_COMMUNICATION")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            audioFocusRequest = request
            val granted = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            focusGranted = granted
            Log.i(TAG, "audioFocus granted=$granted")
            granted
        } else {
            @Suppress("DEPRECATION")
            val granted = audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            focusGranted = granted
            Log.i(TAG, "audioFocus granted=$granted (legacy)")
            granted
        }
    }

    /**
     * Abandon audio focus when idle or session ends.
     */
    fun abandonAudioFocus() {
        if (!focusGranted) return
        focusGranted = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
    }

    /**
     * Set communication device route: Speakerphone vs Earpiece.
     */
    fun setSpeakerphone(enabled: Boolean) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        
        // Always set this because some OEM devices (like Samsung) require it even on Android 12+
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = enabled
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val targetType = if (enabled) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            } else {
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            }
            val devices = audioManager.availableCommunicationDevices
            val targetDevice = devices.firstOrNull { it.type == targetType }
            if (targetDevice != null) {
                val ok = audioManager.setCommunicationDevice(targetDevice)
                Log.i(TAG, "setCommunicationDevice(${if (enabled) "SPEAKER" else "EARPIECE"}) -> $ok")
            } else {
                Log.w(TAG, "Target device type $targetType not found, clearing")
                audioManager.clearCommunicationDevice()
            }
        }
    }

    /**
     * Speaker playback goes out as USAGE_MEDIA (STREAM_MUSIC); earpiece playback goes out as
     * USAGE_VOICE_COMMUNICATION (STREAM_VOICE_CALL). Boosting the other one does nothing.
     */
    fun ensureVolume(useSpeaker: Boolean) {
        val stream = if (useSpeaker) AudioManager.STREAM_MUSIC else AudioManager.STREAM_VOICE_CALL
        val label = if (useSpeaker) "STREAM_MUSIC" else "STREAM_VOICE_CALL"
        try {
            val current = audioManager.getStreamVolume(stream)
            val max = audioManager.getStreamMaxVolume(stream)
            val target = (max * 0.7).toInt().coerceAtLeast(1)
            if (current >= target) {
                Log.i(TAG, "$label volume OK: $current/$max")
                return
            }
            if (useSpeaker) {
                if (savedMusicVolume == null) savedMusicVolume = current
            } else {
                if (savedVoiceCallVolume == null) savedVoiceCallVolume = current
            }
            audioManager.setStreamVolume(stream, target, 0)
            Log.i(TAG, "boosted $label volume $current -> $target (max=$max)")
        } catch (e: Throwable) {
            // Do Not Disturb policy can refuse volume changes.
            Log.w(TAG, "could not adjust $label volume: ${e.message}")
        }
    }

    private fun restoreVolumes() {
        savedMusicVolume?.let { original ->
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, original, 0)
                Log.i(TAG, "restored STREAM_MUSIC volume -> $original")
            } catch (e: Throwable) {
                Log.w(TAG, "could not restore STREAM_MUSIC volume: ${e.message}")
            }
        }
        savedVoiceCallVolume?.let { original ->
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, original, 0)
                Log.i(TAG, "restored STREAM_VOICE_CALL volume -> $original")
            } catch (e: Throwable) {
                Log.w(TAG, "could not restore STREAM_VOICE_CALL volume: ${e.message}")
            }
        }
        savedMusicVolume = null
        savedVoiceCallVolume = null
    }

    fun clearRouting() {
        Log.i(TAG, "clearRouting -> MODE_NORMAL")
        restoreVolumes()
        abandonAudioFocus()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/voice/JitterBuffer.kt`
```kotlin
package com.example.offlinewalkietalkie.voice

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentSkipListMap

/**
 * Section 6 & Section 19.14 Adaptive Jitter Buffer.
 *
 * Requirements:
 * - Target playout buffer: 40 ms (2 frames), minimum 40 ms (2 frames), maximum 200 ms (10 frames).
 * - Stale-voice cutoff: packets older than 200 ms on the receiver's clock are discarded.
 * - Missing frames are decoded using Opus PLC (Packet Loss Concealment).
 * - RFC 3550 inter-arrival jitter estimation and packet loss metrics for live link telemetry.
 */
data class JitterBufferMetrics(
    val jitterMs: Double = 0.0,
    val packetLossCount: Int = 0,
    val lateDroppedCount: Int = 0,
    val bufferDepthMs: Int = 0
)

class JitterBuffer(
    private val codec: AudioCodec,
    val targetDelayFrames: Int = TARGET_DELAY_FRAMES,
    val maxDelayFrames: Int = MAX_DELAY_FRAMES
) {
    companion object {
        private const val TAG = "TL/JitterBuffer"
        const val FRAME_DURATION_MS = 20L
        const val TARGET_DELAY_FRAMES = 2 // 40 ms
        // 40 ms. Starting playout on a single frame leaves no slack, so any jitter on the
        // link underruns the track immediately and the audio chops.
        const val MIN_DELAY_FRAMES = 2
        const val MAX_DELAY_FRAMES = 10  // 200 ms max playout deadline
        const val STALE_VOICE_CUTOFF_MS = 200L // Stale cutoff on receiver clock (§19.14)
    }

    private data class BufferedPacket(
        val pcm: ShortArray,
        val arrivalEpochMs: Long,
        val senderTimestampMs: Long
    )

    // Key: Sequence number -> BufferedPacket
    private val buffer = ConcurrentSkipListMap<Int, BufferedPacket>()

    /**
     * push() decodes on the network thread while pop() runs PLC and reset() recreates the
     * decoder on the playback thread. The native Opus decoder is stateful and not thread-safe.
     */
    private val codecLock = Any()

    private var lastPlayedSequence = -1
    private var isBuffering = true
    private var plcConsecutiveFrames = 0
    private var emptyPopCount = 0 // track consecutive empty pops before re-buffering

    // RFC 3550 Jitter calculation state
    private var lastArrivalEpochMs = 0L
    private var lastSenderTimestampMs = 0L
    private var estimatedJitterMs = 0.0
    private var totalPacketsReceived = 0
    private var totalPacketsLost = 0
    private var totalLatePacketsDropped = 0

    private val _metrics = MutableStateFlow(JitterBufferMetrics())
    val metrics: StateFlow<JitterBufferMetrics> = _metrics.asStateFlow()

    /**
     * Push incoming encoded frame into jitter buffer.
     */
    fun push(sequence: Int, encodedOpus: ByteArray, senderTimestampMs: Long = 0L) {
        val now = System.currentTimeMillis()

        // 1. Drop packets older than last played sequence
        if (lastPlayedSequence != -1 && sequence <= lastPlayedSequence) {
            totalLatePacketsDropped++
            updateMetrics()
            return
        }

        // 2. Decode packet (Opus or PCM)
        val pcm = try {
            synchronized(codecLock) { codec.decode(encodedOpus) }
        } catch (e: Throwable) {
            null
        }

        if (pcm == null || pcm.isEmpty()) {
            return
        }

        // 3. RFC 3550 Inter-arrival jitter update
        if (lastArrivalEpochMs != 0L && senderTimestampMs != 0L && lastSenderTimestampMs != 0L) {
            val transitDiff = (now - lastArrivalEpochMs) - (senderTimestampMs - lastSenderTimestampMs)
            val d = Math.abs(transitDiff).toDouble()
            estimatedJitterMs += (d - estimatedJitterMs) / 16.0
        }
        lastArrivalEpochMs = now
        lastSenderTimestampMs = senderTimestampMs
        totalPacketsReceived++

        buffer[sequence] = BufferedPacket(pcm, now, senderTimestampMs)

        // Drop oldest packets if exceeding maximum playout deadline (200 ms)
        while (buffer.size > maxDelayFrames) {
            buffer.pollFirstEntry()
            totalLatePacketsDropped++
        }

        // Start playing as soon as we have MIN_DELAY_FRAMES
        if (isBuffering && buffer.size >= MIN_DELAY_FRAMES) {
            isBuffering = false
            emptyPopCount = 0
            Log.i(TAG, "buffering -> playing (buffer=${buffer.size} frames)")
        }

        updateMetrics()
    }

    /**
     * Pop next 20ms frame for playout.
     * Returns PCM samples or null if buffer is underrun.
     */
    fun pop(): ShortArray? {
        val now = System.currentTimeMillis()

        if (isBuffering) {
            return null
        }

        if (buffer.isEmpty()) {
            // Don't immediately go back to buffering — allow a few empty pops
            // to ride out brief network jitter without introducing a re-buffer gap
            emptyPopCount++
            if (emptyPopCount > 5) { // 5 * 20ms = 100ms of silence before re-buffering
                Log.i(TAG, "playing -> buffering (empty for ${emptyPopCount * FRAME_DURATION_MS}ms)")
                isBuffering = true
                plcConsecutiveFrames = 0
                emptyPopCount = 0
                updateMetrics()
            }
            return null
        }

        emptyPopCount = 0

        val nextSeq = if (lastPlayedSequence == -1) {
            buffer.firstKey()
        } else {
            lastPlayedSequence + 1
        }

        val packet = buffer.remove(nextSeq)
        if (packet != null) {
            // Check stale cutoff on receiver clock (§19.14)
            if (now - packet.arrivalEpochMs > STALE_VOICE_CUTOFF_MS) {
                totalLatePacketsDropped++
                lastPlayedSequence = nextSeq
                plcConsecutiveFrames = 0
                updateMetrics()
                return pop() // Drop stale frame and try next
            }

            lastPlayedSequence = nextSeq
            plcConsecutiveFrames = 0
            updateMetrics()
            return packet.pcm
        } else {
            // Gap detected: Attempt Packet Loss Concealment (PLC) for 1-2 frames
            if (plcConsecutiveFrames < 2) {
                plcConsecutiveFrames++
                totalPacketsLost++
                lastPlayedSequence = nextSeq
                updateMetrics()
                return synchronized(codecLock) { codec.decodeLostFrame() }
            }

            // Exceeded PLC budget: jump to next available sequence in buffer
            val nextAvailable = buffer.ceilingKey(nextSeq)
            if (nextAvailable != null && nextAvailable > nextSeq) {
                totalPacketsLost += (nextAvailable - nextSeq)
                lastPlayedSequence = nextAvailable
                plcConsecutiveFrames = 0
                val skippedPacket = buffer.remove(nextAvailable)
                updateMetrics()
                return skippedPacket?.pcm
            } else {
                // Buffer truly empty now
                emptyPopCount++
                if (emptyPopCount > 5) {
                    isBuffering = true
                    plcConsecutiveFrames = 0
                    emptyPopCount = 0
                }
                updateMetrics()
                return null
            }
        }
    }

    private fun updateMetrics() {
        _metrics.value = JitterBufferMetrics(
            jitterMs = estimatedJitterMs,
            packetLossCount = totalPacketsLost,
            lateDroppedCount = totalLatePacketsDropped,
            bufferDepthMs = (buffer.size * FRAME_DURATION_MS).toInt()
        )
    }

    fun reset() {
        buffer.clear()
        lastPlayedSequence = -1
        isBuffering = true
        plcConsecutiveFrames = 0
        emptyPopCount = 0
        lastArrivalEpochMs = 0L
        lastSenderTimestampMs = 0L
        synchronized(codecLock) { codec.reset() }
        updateMetrics()
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/voice/OpusCodec.kt`
```kotlin
package com.example.offlinewalkietalkie.voice

import eu.buney.kopus.OpusApplication
import eu.buney.kopus.OpusDecoder
import eu.buney.kopus.OpusEncoder

/**
 * Section 6 Opus Audio Codec with Packet Loss Concealment (PLC).
 * Configured for 48kHz mono PCM, 20ms frames (960 samples), VoIP application.
 */
class OpusCodec : AudioCodec {
    private var encoder = createEncoder()
    private var decoder = createDecoder()

    override fun encode(pcm: ShortArray): ByteArray {
        val outData = ByteArray(1024)
        val frameSize = pcm.size // 960 samples = 20ms
        val bytesEncoded = encoder.encode(pcm, 0, frameSize, outData, 0, outData.size)
        if (bytesEncoded < 0) return ByteArray(0)
        return outData.copyOfRange(0, bytesEncoded)
    }

    override fun decode(encoded: ByteArray): ShortArray {
        val outPcm = ShortArray(960)
        val samplesDecoded = decoder.decode(encoded, 0, encoded.size, outPcm, 0, 960, false)
        if (samplesDecoded < 0) return ShortArray(0)
        return outPcm
    }

    override fun decodeLostFrame(): ShortArray {
        val outPcm = ShortArray(960)
        return try {
            // Passing empty array to Opus decoder activates native Packet Loss Concealment (PLC)
            val samplesDecoded = decoder.decode(ByteArray(0), 0, 0, outPcm, 0, 960, false)
            if (samplesDecoded > 0) outPcm else ShortArray(960)
        } catch (e: Throwable) {
            ShortArray(960)
        }
    }

    override fun reset() {
        try {
            encoder.close()
        } catch (e: Throwable) {}
        try {
            decoder.close()
        } catch (e: Throwable) {}
        encoder = createEncoder()
        decoder = createDecoder()
    }

    private fun createEncoder(): OpusEncoder {
        return OpusEncoder(
            sampleRate = 48000,
            channels = 1,
            application = OpusApplication.Voip
        )
    }

    private fun createDecoder(): OpusDecoder {
        return OpusDecoder(
            sampleRate = 48000,
            channels = 1
        )
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/voice/PcmCodec.kt`
```kotlin
package com.example.offlinewalkietalkie.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmCodec : AudioCodec {
    override fun encode(pcm: ShortArray): ByteArray {
        val buffer = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in pcm) {
            buffer.putShort(sample)
        }
        return buffer.array()
    }

    override fun decode(encoded: ByteArray): ShortArray {
        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN)
        val pcm = ShortArray(encoded.size / 2)
        for (i in pcm.indices) {
            pcm[i] = buffer.getShort()
        }
        return pcm
    }

    override fun decodeLostFrame(): ShortArray {
        return ShortArray(960) // 20ms of silence at 48kHz mono
    }

    override fun reset() {
        // No-op for PCM
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/voice/PendingFloorBuffer.kt`
```kotlin
package com.example.offlinewalkietalkie.voice

import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Section 19.4 & Section 6 Pending-Floor Audio Ring Buffer.
 *
 * Buffers up to 500 ms (25 frames of 20 ms audio) while a floor request is pending.
 * When the floor is granted, buffered audio is drained in order and sent immediately,
 * ensuring no spoken syllables are clipped.
 * If floor is denied or released, the buffer is cleared.
 */
data class BufferedAudioFrame(
    val sequence: Int,
    val timestampMs: Long,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BufferedAudioFrame
        return sequence == other.sequence &&
                timestampMs == other.timestampMs &&
                data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = sequence
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

class PendingFloorBuffer(
    val maxFrames: Int = DEFAULT_MAX_FRAMES
) {
    companion object {
        const val FRAME_DURATION_MS = 20L
        const val MAX_BUFFER_DURATION_MS = 500L
        const val DEFAULT_MAX_FRAMES = (MAX_BUFFER_DURATION_MS / FRAME_DURATION_MS).toInt() // 25 frames
    }

    private val queue = ConcurrentLinkedDeque<BufferedAudioFrame>()

    val size: Int get() = queue.size
    val isEmpty: Boolean get() = queue.isEmpty()

    /**
     * Push a new audio frame into the buffer.
     * If the buffer exceeds [maxFrames], the oldest frame is dropped (ring-buffer semantics).
     */
    fun push(sequence: Int, timestampMs: Long, data: ByteArray) {
        while (queue.size >= maxFrames) {
            queue.pollFirst() // Drop oldest frame
        }
        queue.offerLast(BufferedAudioFrame(sequence, timestampMs, data))
    }

    /**
     * Drains all buffered frames in order and clears the buffer.
     */
    fun drain(): List<BufferedAudioFrame> {
        val frames = mutableListOf<BufferedAudioFrame>()
        while (true) {
            val frame = queue.pollFirst() ?: break
            frames.add(frame)
        }
        return frames
    }

    /**
     * Clears all buffered frames without returning them.
     */
    fun clear() {
        queue.clear()
    }
}
```

### File: `app/src/main/java/com/example/offlinewalkietalkie/voice/VoiceEngine.kt`
```kotlin
package com.example.offlinewalkietalkie.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Section 6 VoiceEngine.
 * Orchestrates AudioCapture, OpusCodec, AudioPlayback, Adaptive JitterBuffer,
 * and AudioRouteManager (audio focus & speakerphone routing).
 */
class VoiceEngine(
    private val codec: AudioCodec = OpusCodec(),
    context: Context? = null
) {
    private val TAG = "TL/Voice"
    val routeManager: AudioRouteManager? = context?.let { AudioRouteManager(it) }

    private val capture = AudioCapture()
    private val playback = AudioPlayback()
    
    private var captureJob: Job? = null
    private var playbackJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    var isLoopback = false
        set(value) {
            field = value
            if (value) startPlayback()
        }

    private val jitterBuffers = mutableMapOf<String, JitterBuffer>()
    private var sequenceNumber = 0

    init {
        routeManager?.onInterruptedByCall = {
            stopRecording()
            stopPlayback()
        }
    }

    fun startRecording(onEncodedFrame: (sequence: Int, frame: ByteArray) -> Unit) {
        routeManager?.requestAudioFocus()
        capture.start { }
        captureJob = coroutineScope.launch {
            var frameCount = 0
            capture.captureLoop { pcmFrame ->
                try {
                    val encoded = codec.encode(bytesToShorts(pcmFrame))
                    if (encoded.isNotEmpty()) {
                        sequenceNumber++
                        frameCount++
                        if (frameCount == 1 || frameCount % 50 == 0) {
                            Log.i(TAG, "captured+encoded frame $frameCount (${encoded.size} bytes)")
                        }
                        if (isLoopback) {
                            handleIncomingFrame("local", sequenceNumber, encoded, System.currentTimeMillis())
                        } else {
                            onEncodedFrame(sequenceNumber, encoded)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Opus encode failed: ${e.message}")
                }
            }
            Log.i(TAG, "capture loop ended (frames=$frameCount)")
        }
    }

    fun stopRecording() {
        captureJob?.cancel()
        capture.stop()
    }

    var speakerphoneEnabled: Boolean = true

    fun startPlayback() {
        if (playbackJob?.isActive == true) return
        routeManager?.requestAudioFocus()
        playback.start(useSpeaker = speakerphoneEnabled)
        routeManager?.setSpeakerphone(speakerphoneEnabled)
        routeManager?.ensureVolume(speakerphoneEnabled)
        Log.i(TAG, "startPlayback: speakerphone=$speakerphoneEnabled")
        playbackJob = coroutineScope.launch {
            var popCount = 0
            var nextWakeTime = System.currentTimeMillis()
            while (isActive) {
                val due = ArrayList<ShortArray>(2)
                for (peer in jitterBuffers.keys.toList()) {
                    val pcm = jitterBuffers[peer]?.pop() ?: continue
                    popCount++
                    if (popCount == 1 || popCount % 100 == 0) {
                        Log.i(TAG, "playback pop #$popCount from $peer (${pcm.size} samples)")
                    }
                    due.add(pcm)
                }
                // Concurrent speakers have to be summed into one frame. Writing them back to
                // back would play them sequentially and drain the track at N times real time.
                if (due.isNotEmpty()) {
                    playback.playFrame(shortsToBytes(mixFrames(due)))
                }

                // Fixed-timestep loop for exactly 20ms intervals
                nextWakeTime += 20
                val now = System.currentTimeMillis()
                val sleepTime = nextWakeTime - now
                if (sleepTime > 0) {
                    delay(sleepTime)
                } else {
                    // If we lagged too far behind, reset the clock to prevent rapid fire
                    nextWakeTime = now
                }
            }
        }
    }
    
    fun handleIncomingFrame(
        peerId: String,
        sequence: Int,
        encodedOpus: ByteArray,
        senderTimestampMs: Long = 0L
    ) {
        startPlayback()
        val jb = jitterBuffers.getOrPut(peerId) { JitterBuffer(OpusCodec()) }
        jb.push(sequence, encodedOpus, senderTimestampMs)
    }

    fun getJitterBuffer(peerId: String): JitterBuffer? = jitterBuffers[peerId]

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        playback.stop()
        routeManager?.abandonAudioFocus()
        jitterBuffers.values.forEach { it.reset() }
        jitterBuffers.clear()
    }

    fun setSpeakerphone(enabled: Boolean) {
        speakerphoneEnabled = enabled
        routeManager?.setSpeakerphone(enabled)
        // Restart AudioTrack with the correct usage attribute for the new route
        playback.start(useSpeaker = enabled)
        routeManager?.ensureVolume(enabled)
        Log.i(TAG, "setSpeakerphone: restarted AudioTrack with useSpeaker=$enabled")
    }

    private fun mixFrames(frames: List<ShortArray>): ShortArray {
        if (frames.size == 1) return frames[0]
        val mixed = ShortArray(frames.maxOf { it.size })
        for (frame in frames) {
            for (i in frame.indices) {
                val sum = mixed[i] + frame[i]
                mixed[i] = sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        return mixed
    }

    // Utilities to convert ByteArray <-> ShortArray for PCM16
    private fun bytesToShorts(bytes: ByteArray): ShortArray {
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return shorts
    }

    private fun shortsToBytes(shorts: ShortArray): ByteArray {
        val bytes = ByteArray(shorts.size * 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts)
        return bytes
    }
}
```
