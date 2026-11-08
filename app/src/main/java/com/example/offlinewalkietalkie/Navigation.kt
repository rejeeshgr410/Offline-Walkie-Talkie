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
                        onNavigateToJoinChannel = { backStack.add(GroupLobby) },
                        onNavigateToRadar = { backStack.add(RadarCompass) },
                        onNavigateToHistory = { backStack.add(VoiceHistory) }
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
                entry<RadarCompass> {
                    RadarScreen(
                        viewModel = viewModel,
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<VoiceHistory> {
                    VoiceHistoryScreen(
                        viewModel = viewModel,
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
            }
        )
    }
}
