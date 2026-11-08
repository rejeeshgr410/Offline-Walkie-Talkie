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
@Serializable data object RadarCompass : androidx.navigation3.runtime.NavKey
@Serializable data object DiscoverMap : androidx.navigation3.runtime.NavKey
@Serializable data object OfflineMaps : androidx.navigation3.runtime.NavKey
@Serializable data object VoiceHistory : androidx.navigation3.runtime.NavKey
