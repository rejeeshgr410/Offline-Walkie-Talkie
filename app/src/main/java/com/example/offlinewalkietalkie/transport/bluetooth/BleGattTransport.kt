package com.example.offlinewalkietalkie.transport.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.example.offlinewalkietalkie.protocol.VivoLeanProtocol
import com.example.offlinewalkietalkie.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Direct BLE GATT transport for TalkLoop's application-owned protocol.
 *
 * Implements Vivo's production walkie-talkie architecture:
 * - Pure Direct BLE: Simultaneous BluetoothGattServer (peripheral) and BluetoothGatt (central).
 * - Requests LE Coded PHY (S=8) where hardware exposes it, with normal PHY fallback.
 * - Single-packet & chunked voice delivery via WRITE_TYPE_NO_RESPONSE for sub-millisecond audio.
 * - Targets Android 10+; radio behavior remains device and firmware dependent.
 * - Automatic background peer discovery and connection (zero manual pairing friction).
 * - 100% Offline: zero dependency on Wi-Fi, Hotspot, cellular data, or Google Play Services.
 */
@SuppressLint("MissingPermission")
class BleGattTransport(
    private val context: Context,
    private val localIdentityKeyHex: String = ""
) : Transport {

    companion object {
        private const val TAG = "TL/BleGattTransport"
        private const val PAIRING_TIMEOUT_MS = 120_000L

        // TalkLoop Dedicated Application Service & Characteristic UUIDs (Application-Owned Protocol)
        val SERVICE_UUID: UUID = UUID.fromString("1A800001-B4B5-4C3D-8F9E-7A6B5C4D3E2F")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("1A801001-B4B5-4C3D-8F9E-7A6B5C4D3E2F")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val PACKET_TYPE_SINGLE: Byte = 0x00
        private const val PACKET_TYPE_CHUNK: Byte = 0x01
        private const val DEFAULT_MTU = 23
        private const val TARGET_MTU = 512
        private const val SAFE_CHUNK_PAYLOAD = 180
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _state = MutableStateFlow(TransportState.IDLE)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    override val peers: Flow<List<Peer>> = _peers.map { it.values.toList() }

    private val _connectionRequests = MutableSharedFlow<ConnectionRequest>(
        // Service discovery may complete before the ViewModel's coroutine has
        // started collecting.  Retain the current pairing request so neither
        // side silently loses its verification screen on a fast BLE link.
        replay = 1,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val connectionRequests: Flow<ConnectionRequest> = _connectionRequests.asSharedFlow()
    private val _pendingConnectionRequest = MutableStateFlow<ConnectionRequest?>(null)
    override val pendingConnectionRequest: StateFlow<ConnectionRequest?> = _pendingConnectionRequest.asStateFlow()

    private val _pairingConfirmations = MutableSharedFlow<PairingConfirmation>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val pairingConfirmations: Flow<PairingConfirmation> = _pairingConfirmations.asSharedFlow()

    private val _incomingFrames = MutableSharedFlow<IncomingFrame>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override fun incomingFrames(): Flow<IncomingFrame> = _incomingFrames.asSharedFlow()

    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private data class ServerNotification(val peerId: String, val bytes: ByteArray)
    private val serverNotificationQueue = Channel<ServerNotification>(Channel.UNLIMITED)
    @Volatile private var pendingNotificationResult: CompletableDeferred<Int>? = null

    // Active client GATT connections: peerAddress -> BluetoothGatt
    private val clientGatts = ConcurrentHashMap<String, BluetoothGatt>()

    // Remote peer characteristics: peerAddress -> BluetoothGattCharacteristic
    private val peerCharacteristics = ConcurrentHashMap<String, BluetoothGattCharacteristic>()

    // Devices connected to our GATT Server: peerAddress -> BluetoothDevice
    private val serverConnectedDevices = ConcurrentHashMap<String, BluetoothDevice>()

    // Negotiated MTU per peer
    private val peerMtus = ConcurrentHashMap<String, Int>()

    // Addresses currently in connection flight
    private val connectingAddresses = ConcurrentHashMap.newKeySet<String>()

    // Reconnection tracking for exponential backoff (Phase B)
    private val reconnectJobs = ConcurrentHashMap<String, Job>()
    private val pairingDeadlineJobs = ConcurrentHashMap<String, Job>()
    private val reconnectAttempts = ConcurrentHashMap<String, Int>()
    private val intentionallyDisconnectedPeers = ConcurrentHashMap.newKeySet<String>()
    private val ignoredRotatedScanAddresses = ConcurrentHashMap.newKeySet<String>()
    private val locallyConfirmedPairings = ConcurrentHashMap.newKeySet<String>()
    private val remotelyConfirmedPairings = ConcurrentHashMap.newKeySet<String>()
    private val verifiedPairingAddresses = ConcurrentHashMap.newKeySet<String>()

    // Outgoing packet sequence counter
    private val outgoingPacketSeq = AtomicInteger(1)

    // Incomplete incoming chunk buffers: "$address:$packetId" -> ChunkBuffer
    private class ChunkBuffer(val totalChunks: Int) {
        val chunks = ConcurrentHashMap<Int, ByteArray>()
        val timestamp = System.currentTimeMillis()
    }
    private val incomingChunkBuffers = ConcurrentHashMap<String, ChunkBuffer>()

    private var gattServer: BluetoothGattServer? = null
    private var serverCharacteristic: BluetoothGattCharacteristic? = null

    // Synchronized 6-digit authentication verification codes per peer: peerAddress -> 6-digit code
    private val peerAuthCodes = ConcurrentHashMap<String, String>()

    private var leAdvertiser: BluetoothLeAdvertiser? = null
    private var leScanner: BluetoothLeScanner? = null
    private var localDisplayName: String = Build.MODEL

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_ON) {
                    Log.i(TAG, "Bluetooth turned back ON -> auto-resuming advertising and discovery")
                    coroutineScope.launch {
                        delay(600)
                        if (_isAdvertising.value || _state.value == TransportState.ACTIVE) {
                            startAdvertising(LocalIdentity(localDisplayName))
                        }
                        if (_isDiscovering.value || _state.value == TransportState.ACTIVE) {
                            startDiscovery(LocalIdentity(localDisplayName))
                        }
                    }
                } else if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                    Log.i(TAG, "Bluetooth turned OFF -> pausing transport")
                    reportError("Bluetooth was turned off")
                    _state.value = TransportState.IDLE
                    cancelAllReconnects()
                }
            }
        }
    }

    init {
        coroutineScope.launch {
            for (notification in serverNotificationQueue) {
                val server = gattServer ?: continue
                val device = serverConnectedDevices[notification.peerId] ?: continue
                val characteristic = serverCharacteristic ?: continue
                val completion = CompletableDeferred<Int>()
                pendingNotificationResult = completion
                val started = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        server.notifyCharacteristicChanged(device, characteristic, false, notification.bytes) ==
                            BluetoothStatusCodes.SUCCESS
                    } else {
                        @Suppress("DEPRECATION")
                        characteristic.value = notification.bytes
                        @Suppress("DEPRECATION")
                        server.notifyCharacteristicChanged(device, characteristic, false)
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Server notification failed to start", e)
                    false
                }
                if (started) {
                    val status = withTimeoutOrNull(1_500) { completion.await() }
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.w(TAG, "Server notification completion status=$status peer=${notification.peerId}")
                    }
                }
                if (pendingNotificationResult === completion) pendingNotificationResult = null
            }
        }
        try {
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(bluetoothStateReceiver, filter)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not register bluetoothStateReceiver", e)
        }
    }

    // GATT Server Callback (Peripheral Role)
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
            pendingNotificationResult?.complete(status)
        }
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            val address = device?.address ?: return
            val name = device.name?.takeIf { it.isNotBlank() } ?: _peers.value[address]?.name ?: "Peer ${address.takeLast(5)}"
            Log.i(TAG, "GattServer connectionStateChange: $address -> newState=$newState (status=$status)")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                cancelReconnect(address)
                replaceRotatedConnections(address, name)
                serverConnectedDevices[address] = device
                addOrUpdatePeer(address, name, isConnected = true)

                // The scanner elects one deterministic GATT client.  Never
                // connect back from the server callback: doing so races the
                // original link and is the primary source of duplicate peers
                // and failed code verification on real Android devices.
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    reportError("BLE link to $name ended (GATT status $status)")
                }
                serverConnectedDevices.remove(address)
                val isClientConnected = clientGatts.containsKey(address)
                if (!isClientConnected) clearPairingState(address)
                addOrUpdatePeer(address, name, isConnected = isClientConnected)
                if (!isClientConnected && !intentionallyDisconnectedPeers.contains(address) && _state.value == TransportState.ACTIVE) {
                    // Android BLE privacy addresses rotate.  Reconnecting to
                    // this cached address causes repeated GATT 133 failures;
                    // the already-running scanner will find the peer's fresh
                    // address and run the deterministic election again.
                    removePeerIfDisconnected(address)
                    Log.i(TAG, "Server link ended for $address; waiting for fresh scan result")
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            val address = device?.address ?: return
            if (value != null && value.isNotEmpty()) {
                handleIncomingBytes(address, value)
                // The initiating client writes CONNECT_REQ before its CCCD
                // subscription is guaranteed complete. Echo its code back as
                // an explicit pairing receipt after a short delay so it also
                // gets a durable local pairing event over notifications.
                val packet = VivoLeanProtocol.decode(value)
                if (packet?.type == VivoLeanProtocol.TYPE_CONNECT_REQ) {
                    val parts = String(packet.payload, Charsets.UTF_8).split("|", limit = 3)
                    val code = parts.firstOrNull().orEmpty()
                    if (code.length == 6 && code.all { it.isDigit() }) {
                        coroutineScope.launch {
                            delay(700)
                            writeRawPacket(
                                address,
                                VivoLeanProtocol.encode(
                                    type = VivoLeanProtocol.TYPE_AUTO_CONNECT,
                                    payload = "PAIR|$code|$localDisplayName|$localIdentityKeyHex".toByteArray(Charsets.UTF_8)
                                )
                            )
                        }
                    }
                }
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            val address = device?.address
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
            if (address != null) {
                val name = device.name?.takeIf { it.isNotBlank() } ?: _peers.value[address]?.name ?: "Peer ${address.takeLast(5)}"
                addOrUpdatePeer(address, name, isConnected = true)
            }
        }

        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
            val address = device?.address ?: return
            Log.i(TAG, "Server MTU changed for $address: mtu=$mtu")
            peerMtus[address] = mtu
        }
    }

    // GATT Client Callback (Central Role)
    private val gattClientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            val address = gatt?.device?.address ?: return
            val name = gatt.device?.name?.takeIf { it.isNotBlank() } ?: _peers.value[address]?.name ?: "Peer ${address.takeLast(5)}"
            Log.i(TAG, "GattClient connectionStateChange: $address -> newState=$newState (status=$status)")

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                cancelReconnect(address)
                replaceRotatedConnections(address, name)
                clientGatts[address] = gatt
                clearError()
                addOrUpdatePeer(address, name, isConnected = true)

                // Request high MTU for high-throughput voice transmission
                try {
                    gatt.requestMtu(TARGET_MTU)
                } catch (e: Throwable) {
                    Log.w(TAG, "requestMtu failed", e)
                }

                // Discover services on remote peer
                coroutineScope.launch {
                    delay(150)
                    try {
                        gatt.discoverServices()
                    } catch (e: Throwable) {
                        Log.w(TAG, "discoverServices failed", e)
                    }
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                clientGatts.remove(address)
                peerCharacteristics.remove(address)
                peerMtus.remove(address)
                try { gatt.close() } catch (e: Throwable) {}
                val isServerConnected = serverConnectedDevices.containsKey(address)
                if (!isServerConnected) clearPairingState(address)
                addOrUpdatePeer(address, name, isConnected = isServerConnected)

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    reportError("BLE connection to $name failed (GATT status $status)")
                    val failures = reconnectAttempts.getOrDefault(address, 0)
                    if (failures >= 3) {
                        Log.w(TAG, "Persistent GATT error $status on $address; pruning stale peer to allow fresh scan")
                        cancelReconnect(address)
                        val current = _peers.value.toMutableMap()
                        current.remove(address)
                        _peers.value = current
                        return
                    }
                }

                if (!isServerConnected && !intentionallyDisconnectedPeers.contains(address) && _state.value == TransportState.ACTIVE) {
                    removePeerIfDisconnected(address)
                    Log.i(TAG, "Client link ended for $address; waiting for fresh scan result")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            val address = gatt?.device?.address ?: return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
                if (characteristic != null) {
                    peerCharacteristics[address] = characteristic
                    Log.i(TAG, "Discovered Walkie-Talkie GATT Characteristic for $address")

                    // Re-publish peer now that GATT characteristic is verified and ready for transmission
                    val peerName = _peers.value[address]?.name ?: "Peer ${address.takeLast(5)}"
                    addOrUpdatePeer(address, peerName, isConnected = true)

                    // Generate or retrieve the synchronized 6-digit authentication code
                    val existingCode = peerAuthCodes[address]
                    val code = existingCode ?: "%06d".format(
                        java.util.concurrent.ThreadLocalRandom.current().nextInt(100000, 999999)
                    )
                    registerPairingCode(address, code)

                    // Transmit Lean TYPE_CONNECT_REQ carrying this exact code and localDisplayName to the peer
                    val payloadStr = "$code|$localDisplayName|$localIdentityKeyHex"
                    val connectReqPacket = VivoLeanProtocol.encode(
                        type = VivoLeanProtocol.TYPE_CONNECT_REQ,
                        payload = payloadStr.toByteArray(Charsets.UTF_8)
                    )
                    coroutineScope.launch {
                        delay(250)
                        writeRawPacket(address, connectReqPacket)
                    }

                    // Wait for the server's PAIR receipt, which carries its
                    // stable identity fingerprint. Publishing an identity-less
                    // request immediately caused a trusted reconnect to flash
                    // the verification screen and left it stranded when the
                    // server then rejected a changed identity. Keep a delayed
                    // fallback for older peers that do not send the receipt.
                    coroutineScope.launch {
                        delay(1_500)
                        if (peerAuthCodes[address] == code &&
                            _pendingConnectionRequest.value?.peerId != address
                        ) {
                            publishConnectionRequest(
                                ConnectionRequest(
                                    peerId = address,
                                    peerName = peerName,
                                    authCode = code
                                )
                            )
                        }
                    }
                    startPairingDeadline(address, peerName)

                    // Enable notifications on this characteristic
                    gatt.setCharacteristicNotification(characteristic, true)
                    val descriptor = characteristic.getDescriptor(CCCD_UUID)
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        try {
                            gatt.writeDescriptor(descriptor)
                        } catch (e: Throwable) {
                            Log.w(TAG, "writeDescriptor failed", e)
                        }
                    }

                    // Request LE Coded PHY (Long Range S=8) if supported
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bluetoothAdapter?.isLeCodedPhySupported == true) {
                        Log.i(TAG, "Requesting LE Coded PHY (S=8) for $address")
                        try {
                            gatt.setPreferredPhy(
                                BluetoothDevice.PHY_LE_CODED,
                                BluetoothDevice.PHY_LE_CODED,
                                BluetoothDevice.PHY_OPTION_S8
                            )
                        } catch (e: Throwable) {
                            Log.w(TAG, "setPreferredPhy failed", e)
                        }
                    }
                } else {
                    reportError("Peer $address did not expose the TalkLoop BLE service")
                }
            } else {
                reportError("Service discovery failed for $address (GATT status $status)")
            }
        }

        // Android 13+ (API 33+) Callback
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val address = gatt.device?.address ?: return
            if (value.isNotEmpty()) {
                handleIncomingBytes(address, value)
            }
        }

        // Android 12 and below (< API 33) Callback
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            val address = gatt?.device?.address ?: return
            val value = characteristic?.value
            if (value != null && value.isNotEmpty()) {
                handleIncomingBytes(address, value)
            }
        }

        override fun onPhyUpdate(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
            Log.i(TAG, "PHY update for ${gatt?.device?.address}: txPhy=$txPhy, rxPhy=$rxPhy (status=$status)")
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            val address = gatt?.device?.address ?: return
            Log.i(TAG, "GattClient MTU changed for $address: mtu=$mtu (status=$status)")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                peerMtus[address] = mtu
            } else {
                reportError("MTU negotiation failed for $address (GATT status $status)")
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(TAG, "BLE GATT Advertising active")
            _isAdvertising.value = true
            _state.value = TransportState.ACTIVE
        }

        override fun onStartFailure(errorCode: Int) {
            Log.w(TAG, "BLE GATT Advertising failed with code $errorCode")
            reportError("BLE advertising failed (code $errorCode)")
            if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE) {
                Log.i(TAG, "Retrying advertising without scan response...")
                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setConnectable(true)
                    .setTimeout(0)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .build()
                val data = AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)
                    .addServiceUuid(ParcelUuid(SERVICE_UUID))
                    .build()
                try {
                    leAdvertiser?.startAdvertising(settings, data, object : AdvertiseCallback() {
                        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                            Log.i(TAG, "BLE GATT Advertising active (minimal payload fallback)")
                            _isAdvertising.value = true
                            _state.value = TransportState.ACTIVE
                        }
                        override fun onStartFailure(err: Int) {
                            Log.e(TAG, "BLE fallback advertising failed: $err")
                            reportError("BLE advertising fallback failed (code $err)")
                            _isAdvertising.value = false
                        }
                    })
                    return
                } catch (e: Throwable) {
                    Log.e(TAG, "Fallback advertising error", e)
                }
            }
            _isAdvertising.value = false
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val address = device.address
            val recordName = result.scanRecord?.deviceName
            val rawName = device.name?.takeIf { it.isNotBlank() } ?: recordName?.takeIf { it.isNotBlank() }
            val name = rawName ?: _peers.value[address]?.name ?: "TalkLoop ${address.takeLast(5)}"

            // A client process can be killed while Android still reports its old
            // GATT-server link as connected.  With BLE privacy enabled the relaunched
            // phone is immediately seen under a new address, and the stale entry used
            // to suppress the elected client forever.  Only evict an unauthenticated
            // server-only entry with no active SAS exchange; authenticated links are
            // intentionally retained across address rotation.
            evictStaleUnauthenticatedServerLink(address, name)

            val isConn = clientGatts.containsKey(address) || serverConnectedDevices.containsKey(address)
            addOrUpdatePeer(address, name, isConnected = isConn)

            // Prevent duplicate connections if we already have an active connected link to this device name
            val isKnownName = !name.startsWith("TalkLoop") && !name.startsWith("Peer ") && !name.startsWith("Walkie-Talkie")
            val hasActiveConnected = isKnownName && _peers.value.values.any {
                it.name.equals(name, ignoreCase = true) && it.isConnected && it.id != address
            }

            // Elect exactly one GATT client.  With two apps scanning and
            // advertising at once, allowing both to connect creates duplicate
            // physical links and incompatible pairing-code prompts.
            val iAmInitiator = BleConnectionPolicy.shouldInitiateClient(
                localAddress = localBluetoothAddress(),
                remoteAddress = address,
                localName = localDisplayName,
                remoteName = name
            )
            if (!isConn && iAmInitiator && !hasActiveConnected && !connectingAddresses.contains(address) && !intentionallyDisconnectedPeers.contains(address)) {
                connectingAddresses.add(address)
                coroutineScope.launch {
                    Log.i(TAG, "Connecting GATT to discovered TalkLoop peer $name ($address)")
                    connect(address)
                    delay(4000)
                    connectingAddresses.remove(address)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE Scan failed with code $errorCode")
            reportError("BLE discovery failed (code $errorCode)")
            _isDiscovering.value = false
        }
    }

    private fun evictStaleUnauthenticatedServerLink(newAddress: String, name: String) {
        val isGenericName = name.startsWith("TalkLoop") || name.startsWith("Walkie-Talkie") || name.startsWith("Peer ")
        if (isGenericName) return

        val stale = serverConnectedDevices.entries.firstOrNull { (oldAddress, _) ->
            oldAddress != newAddress &&
                _peers.value[oldAddress]?.name.equals(name, ignoreCase = true) &&
                _peers.value[oldAddress]?.isAuthenticated != true &&
                !peerAuthCodes.containsKey(oldAddress) &&
                oldAddress !in locallyConfirmedPairings &&
                oldAddress !in remotelyConfirmedPairings &&
                oldAddress !in verifiedPairingAddresses &&
                !clientGatts.containsKey(oldAddress)
        } ?: return

        Log.i(TAG, "Evicting stale unauthenticated server link ${stale.key} for rotated address $newAddress ($name)")
        try {
            gattServer?.cancelConnection(stale.value)
        } catch (_: Throwable) {
            // Removing local state still permits a fresh, deterministic connect.
        }
        serverConnectedDevices.remove(stale.key)
        peerCharacteristics.remove(stale.key)
        peerMtus.remove(stale.key)
        clearPairingState(stale.key)
        val current = _peers.value.toMutableMap()
        current.remove(stale.key)
        _peers.value = current
    }

    /**
     * A killed/restarted phone can reconnect with a new BLE privacy address
     * before Android reports the old ACL as disconnected. Once an actual new
     * GATT connection (not merely a scan result) exists for the same named
     * handset, retaining the old address makes the ViewModel canonicalize the
     * stale session key and hides the new pairing/AUTH path. Retire every old
     * bearer for that handset so the new address must establish a fresh ECDH
     * session. Persistent trust still removes the second SAS prompt.
     */
    private fun replaceRotatedConnections(newAddress: String, name: String) {
        val isGenericName = name.startsWith("TalkLoop") ||
            name.startsWith("Walkie-Talkie") ||
            name.startsWith("Peer ")
        if (isGenericName) return

        val staleAddresses = (clientGatts.keys + serverConnectedDevices.keys)
            .filter { oldAddress ->
                oldAddress != newAddress &&
                    _peers.value[oldAddress]?.name.equals(name, ignoreCase = true)
            }
            .toSet()
        if (staleAddresses.isEmpty()) return

        val current = _peers.value.toMutableMap()
        staleAddresses.forEach { oldAddress ->
            Log.i(TAG, "Replacing rotated GATT link $oldAddress with $newAddress for '$name'")
            intentionallyDisconnectedPeers.add(oldAddress)
            cancelReconnect(oldAddress)
            clientGatts.remove(oldAddress)?.let { staleGatt ->
                try { staleGatt.disconnect() } catch (_: Throwable) { }
                try { staleGatt.close() } catch (_: Throwable) { }
            }
            serverConnectedDevices.remove(oldAddress)?.let { staleDevice ->
                try { gattServer?.cancelConnection(staleDevice) } catch (_: Throwable) { }
            }
            peerCharacteristics.remove(oldAddress)
            peerMtus.remove(oldAddress)
            connectingAddresses.remove(oldAddress)
            clearPairingState(oldAddress)
            current.remove(oldAddress)
        }
        _peers.value = current
    }

    private fun addOrUpdatePeer(address: String, name: String, isConnected: Boolean) {
        val current = _peers.value.toMutableMap()
        val existing = current[address]
        val cleanName = if ((name.startsWith("TalkLoop") || name.startsWith("Walkie-Talkie") || name.startsWith("Peer ")) &&
            existing != null && !existing.name.startsWith("TalkLoop") && !existing.name.startsWith("Walkie-Talkie") && !existing.name.startsWith("Peer ")) {
            existing.name
        } else {
            name
        }

        // Deduplicate only stale identities. Android BLE privacy uses rotating
        // remote addresses, so a scan can report the same phone under a new
        // address while a working GATT link still exists on the old one. Never
        // close or replace that live link merely because its scan address moved.
        val isGeneric = cleanName.startsWith("TalkLoop") || cleanName.startsWith("Walkie-Talkie") || cleanName.startsWith("Peer ")
        if (!isGeneric) {
            val duplicateAddresses = current.filter { it.key != address && it.value.name.equals(cleanName, ignoreCase = true) }.keys
            val liveDuplicate = duplicateAddresses.firstOrNull { dupAddr ->
                current[dupAddr]?.isConnected == true ||
                    clientGatts.containsKey(dupAddr) ||
                    serverConnectedDevices.containsKey(dupAddr)
            }
            if (liveDuplicate != null && !isConnected) {
                if (ignoredRotatedScanAddresses.add(address)) {
                    Log.d(TAG, "Ignoring rotated scan address $address for '$cleanName'; live link is $liveDuplicate")
                }
                return
            }
            duplicateAddresses.filter { dupAddr ->
                current[dupAddr]?.isConnected != true &&
                    !clientGatts.containsKey(dupAddr) &&
                    !serverConnectedDevices.containsKey(dupAddr)
            }.forEach { dupAddr ->
                Log.i(TAG, "Pruning duplicate peer address $dupAddr for device '$cleanName' in favor of $address")
                current.remove(dupAddr)
                cancelReconnect(dupAddr)
            }
        }

        val isAuth = if (isConnected) (existing?.isAuthenticated ?: false) else false
        current[address] = Peer(id = address, name = cleanName, isConnected = isConnected, isAuthenticated = isAuth)
        _peers.value = current
    }

    private fun localBluetoothAddress(): String? = try {
        bluetoothAdapter?.address
    } catch (_: SecurityException) {
        null
    }

    private fun removePeerIfDisconnected(address: String) {
        val current = _peers.value
        if (current[address]?.isConnected != true &&
            !clientGatts.containsKey(address) &&
            !serverConnectedDevices.containsKey(address)
        ) {
            _peers.value = current - address
        }
    }

    /** Pairing must not leave a phantom connected peer indefinitely. */
    private fun startPairingDeadline(address: String, peerName: String) {
        pairingDeadlineJobs.remove(address)?.cancel()
        pairingDeadlineJobs[address] = coroutineScope.launch {
            delay(PAIRING_TIMEOUT_MS)
            if (peerAuthCodes.containsKey(address)) {
                reportError("Pairing with $peerName timed out. Connect again and confirm the same code on both phones.")
                disconnect(address)
            }
        }
    }

    private fun pairingCompleted(address: String) {
        if (address !in locallyConfirmedPairings || address !in remotelyConfirmedPairings) return
        verifiedPairingAddresses.add(address)
        pairingDeadlineJobs.remove(address)?.cancel()
        peerAuthCodes.remove(address)
        clearError()
        if (_pendingConnectionRequest.value?.peerId == address) _pendingConnectionRequest.value = null
    }

    private fun registerPairingCode(address: String, code: String) {
        val previousCode = peerAuthCodes.put(address, code)
        if (previousCode != code) {
            locallyConfirmedPairings.remove(address)
            remotelyConfirmedPairings.remove(address)
            verifiedPairingAddresses.remove(address)
        }
    }

    private fun clearPairingState(address: String) {
        peerAuthCodes.remove(address)
        locallyConfirmedPairings.remove(address)
        remotelyConfirmedPairings.remove(address)
        verifiedPairingAddresses.remove(address)
        pairingDeadlineJobs.remove(address)?.cancel()
        if (_pendingConnectionRequest.value?.peerId == address) {
            _pendingConnectionRequest.value = null
        }
    }

    private fun publishConnectionRequest(request: ConnectionRequest) {
        Log.i(TAG, "Publishing pairing request for ${request.peerName} (${request.peerId}) code=${request.authCode}")
        _pendingConnectionRequest.value = request
        _connectionRequests.tryEmit(request)
    }

    private fun reportError(message: String) {
        Log.w(TAG, message)
        _lastError.value = message
    }

    private fun clearError() {
        _lastError.value = null
    }

    private fun String.isValidIdentityHex(): Boolean =
        length == 64 && all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }

    private fun scheduleExponentialReconnect(address: String) {
        if (reconnectJobs.containsKey(address)) return
        val attempts = reconnectAttempts.getOrDefault(address, 0)
        if (attempts >= 6) {
            Log.i(TAG, "Max reconnect attempts reached for $address (6 attempts). Retaining background discovery.")
            reconnectAttempts.remove(address)
            return
        }

        // Exponential backoff: 1s, 2s, 4s, 8s, 16s (capped at 16s)
        val delayMs = (1000L * (1 shl attempts)).coerceAtMost(16000L)
        Log.i(TAG, "Scheduling exponential reconnect for $address in ${delayMs}ms (attempt ${attempts + 1}/6)")

        val job = coroutineScope.launch {
            delay(delayMs)
            reconnectJobs.remove(address)
            if (!intentionallyDisconnectedPeers.contains(address) && !clientGatts.containsKey(address) && !serverConnectedDevices.containsKey(address)) {
                reconnectAttempts[address] = attempts + 1
                Log.i(TAG, "Executing reconnect attempt ${attempts + 1} for $address")
                connect(address)
            }
        }
        reconnectJobs[address] = job
    }

    private fun cancelReconnect(address: String) {
        reconnectJobs.remove(address)?.cancel()
        reconnectAttempts.remove(address)
    }

    private fun cancelAllReconnects() {
        reconnectJobs.values.forEach { it.cancel() }
        reconnectJobs.clear()
        reconnectAttempts.clear()
    }

    private fun handleIncomingBytes(address: String, data: ByteArray) {
        if (data.isEmpty()) return

        // 1. Lean control packets must be recognized before the BLE envelope.
        // A Lean header is big-endian and the first byte for packet types 1-12
        // is normally 0x00.  Checking PACKET_TYPE_SINGLE first therefore
        // swallowed CONNECT_REQ as an app frame, leaving the peer with a
        // transport link but no pairing-code handshake.
        if (VivoLeanProtocol.isLeanPacket(data)) {
            handleLeanControlPacket(address, data)
            return
        }

        // 2. TalkLoop transport framing (single or chunked)
        if (data[0] == PACKET_TYPE_SINGLE) {
            val frameBytes = data.copyOfRange(1, data.size)
            _incomingFrames.tryEmit(IncomingFrame(address, frameBytes))
            return
        }

        if (data[0] == PACKET_TYPE_CHUNK) {
            // Fragmented chunk: [0x01, packetSeq: Byte, chunkIndex: Byte, totalChunks: Byte, payload...]
            if (data.size < 4) return
            val packetSeq = data[1].toInt() and 0xFF
            val chunkIndex = data[2].toInt() and 0xFF
            val totalChunks = data[3].toInt() and 0xFF

            if (totalChunks <= 0 || totalChunks > 64 || chunkIndex >= totalChunks) {
                Log.w(TAG, "Malformed chunk header: idx=$chunkIndex total=$totalChunks seq=$packetSeq")
                return
            }
            val chunkPayload = data.copyOfRange(4, data.size)

            val key = "$address:$packetSeq"
            if (incomingChunkBuffers.size > 32) {
                val cutoff = System.currentTimeMillis() - 3000
                incomingChunkBuffers.entries.removeIf { it.value.timestamp < cutoff }
            }

            val buffer = incomingChunkBuffers.computeIfAbsent(key) { ChunkBuffer(totalChunks) }
            buffer.chunks[chunkIndex] = chunkPayload

            if (buffer.chunks.size == buffer.totalChunks) {
                var totalSize = 0
                for (i in 0 until buffer.totalChunks) {
                    totalSize += buffer.chunks[i]?.size ?: 0
                }
                if (totalSize > 65536) {
                    Log.w(TAG, "Reassembled frame exceeds size limit ($totalSize bytes)")
                    incomingChunkBuffers.remove(key)
                    return
                }

                val out = ByteArrayOutputStream(totalSize)
                for (i in 0 until buffer.totalChunks) {
                    buffer.chunks[i]?.let { out.write(it) }
                }
                incomingChunkBuffers.remove(key)
                _incomingFrames.tryEmit(IncomingFrame(address, out.toByteArray()))
            }
            return
        }

        // 3. Fallback: Raw unpacketed frame (direct Frame bytes starting with Frame.MAGIC 0x5754)
        _incomingFrames.tryEmit(IncomingFrame(address, data))

        // Clean stale chunk buffers older than 10 seconds
        val now = System.currentTimeMillis()
        incomingChunkBuffers.entries.removeIf { now - it.value.timestamp > 10_000 }
    }

    private fun handleLeanControlPacket(address: String, data: ByteArray) {
        val packet = VivoLeanProtocol.decode(data) ?: return
        when (packet.type) {
            VivoLeanProtocol.TYPE_CONNECT_REQ -> {
                val rawPayload = String(packet.payload, Charsets.UTF_8).trim()
                val parts = rawPayload.split("|", limit = 3)
                val receivedCode = parts.firstOrNull().orEmpty()
                val remotePeerName = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                val remoteIdentityKey = parts.getOrNull(2)?.takeIf { it.isValidIdentityHex() }
                if (receivedCode.length != 6 || receivedCode.any { !it.isDigit() }) {
                    Log.w(TAG, "Ignoring malformed pairing code from $address")
                    return
                }

                // Only the elected client originates this code.  The server
                // stores and displays it verbatim; it never replaces it with
                // a competing local code.
                registerPairingCode(address, receivedCode)
                val peerName = remotePeerName ?: _peers.value[address]?.name ?: "TalkLoop ${address.takeLast(5)}"
                addOrUpdatePeer(address, peerName, isConnected = true)
                Log.i(TAG, "Received pairing code $receivedCode from $address ($peerName)")
                publishConnectionRequest(ConnectionRequest(address, peerName, receivedCode, remoteIdentityKey))
                startPairingDeadline(address, peerName)
            }
            VivoLeanProtocol.TYPE_AUDIO_FRAME -> _incomingFrames.tryEmit(IncomingFrame(address, packet.payload))
            VivoLeanProtocol.TYPE_CODEC_REQ -> {
                Log.i(TAG, "Received Lean CODEC_REQ mode: 0x${Integer.toHexString(packet.codecMode)} from $address")
                val ack = VivoLeanProtocol.encode(type = VivoLeanProtocol.TYPE_CODEC_ACK, codecMode = packet.codecMode)
                coroutineScope.launch { writeRawPacket(address, ack) }
            }
            VivoLeanProtocol.TYPE_CODEC_ACK ->
                Log.i(TAG, "Received Lean CODEC_ACK mode: 0x${Integer.toHexString(packet.codecMode)} from $address")
            VivoLeanProtocol.TYPE_AUTO_CONNECT -> {
                val parts = String(packet.payload, Charsets.UTF_8).split("|")
                if (parts.size >= 2 && parts[0] == "PAIR" && parts[1].length == 6 && parts[1].all { it.isDigit() }) {
                    val peerName = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
                        ?: _peers.value[address]?.name
                        ?: "TalkLoop ${address.takeLast(5)}"
                    val remoteIdentityKey = parts.getOrNull(3)?.takeIf { it.isValidIdentityHex() }
                    registerPairingCode(address, parts[1])
                    addOrUpdatePeer(address, peerName, isConnected = true)
                    Log.i(TAG, "Received pairing receipt from $address ($peerName)")
                    publishConnectionRequest(ConnectionRequest(address, peerName, parts[1], remoteIdentityKey))
                    startPairingDeadline(address, peerName)
                } else if (parts.size >= 2 && parts[0] == "CONFIRM" && parts[1].length == 6 && parts[1].all { it.isDigit() }) {
                    if (peerAuthCodes[address] != parts[1]) {
                        Log.w(TAG, "Ignoring pairing confirmation with a stale or mismatched code from $address")
                        return
                    }
                    Log.i(TAG, "Received pairing confirmation from $address")
                    remotelyConfirmedPairings.add(address)
                    pairingCompleted(address)
                    _pairingConfirmations.tryEmit(PairingConfirmation(address, parts[1]))
                } else if (parts.size >= 2 && parts[0] == "REJECT" && peerAuthCodes[address] == parts[1]) {
                    reportError("Pairing was rejected on the other phone.")
                    coroutineScope.launch { disconnect(address) }
                }
            }
            else -> _incomingFrames.tryEmit(IncomingFrame(address, data))
        }
    }

    override suspend fun startAdvertising(identity: LocalIdentity) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _state.value = TransportState.ERROR
            reportError("Bluetooth is unavailable. Turn it on and try again.")
            return
        }
        localDisplayName = identity.endpointName

        startGattServer()

        leAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (leAdvertiser == null) {
            reportError("BLE advertising is not available on this device")
            return
        }

        // Try Extended Advertising with LE Coded PHY (S=8) on API 26+ if hardware supports it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bluetoothAdapter.isLeCodedPhySupported) {
            try {
                val params = AdvertisingSetParameters.Builder()
                    .setLegacyMode(false)
                    .setConnectable(true)
                    .setScannable(true)
                    .setPrimaryPhy(BluetoothDevice.PHY_LE_CODED)
                    .setSecondaryPhy(BluetoothDevice.PHY_LE_CODED)
                    .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
                    .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
                    .build()

                val data = AdvertiseData.Builder()
                    .setIncludeDeviceName(true)
                    .addServiceUuid(ParcelUuid(SERVICE_UUID))
                    .build()

                leAdvertiser?.startAdvertisingSet(params, data, null, null, null, object : AdvertisingSetCallback() {
                    override fun onAdvertisingSetStarted(advertisingSet: AdvertisingSet?, txPower: Int, status: Int) {
                        if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                            Log.i(TAG, "BLE Extended Advertising Set (LE Coded PHY S=8) active at txPower=$txPower")
                            _isAdvertising.value = true
                            _state.value = TransportState.ACTIVE
                        } else {
                            Log.w(TAG, "AdvertisingSet start failed status=$status, falling back to legacy")
                            startLegacyAdvertising()
                        }
                    }
                })
                return
            } catch (e: Throwable) {
                Log.w(TAG, "startAdvertisingSet threw exception, falling back to legacy advertising", e)
            }
        }

        startLegacyAdvertising()
    }

    private fun startLegacyAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        // P0-02 FIX: Primary legacy advertisement MUST NOT include device name,
        // which easily exceeds 31 bytes on phones with long Bluetooth names.
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        // Scan response carries device name only (up to 29 bytes for name) without
        // adding 128-bit service data that would blow past the 31-byte limit.
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        try {
            leAdvertiser?.stopAdvertising(advertiseCallback)
            leAdvertiser?.startAdvertising(settings, data, scanResponse, advertiseCallback)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to startAdvertising with scan response, trying data only", e)
            try {
                leAdvertiser?.startAdvertising(settings, data, advertiseCallback)
            } catch (e2: Throwable) {
                Log.e(TAG, "Failed to startAdvertising fallback", e2)
            }
        }
    }

    private fun startGattServer() {
        if (gattServer != null) return
        try {
            val server = bluetoothManager?.openGattServer(context, gattServerCallback)
            if (server != null) {
                val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
                val characteristic = BluetoothGattCharacteristic(
                    CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_READ or
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
                )
                val descriptor = BluetoothGattDescriptor(
                    CCCD_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                )
                characteristic.addDescriptor(descriptor)
                service.addCharacteristic(characteristic)
                server.addService(service)

                gattServer = server
                serverCharacteristic = characteristic
                Log.i(TAG, "GATT Server running with Walkie-Talkie Service ($SERVICE_UUID)")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error starting GATT Server", e)
        }
    }

    override suspend fun startDiscovery(identity: LocalIdentity) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        leScanner = bluetoothAdapter.bluetoothLeScanner
        if (leScanner == null) {
            reportError("BLE scanning is not available on this device")
            return
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val settingsBuilder = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bluetoothAdapter.isLeCodedPhySupported) {
            settingsBuilder.setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
            settingsBuilder.setLegacy(false)
            try {
                // Duplicate filtering optimization matching Vivo pj.w.b
                val method = settingsBuilder.javaClass.getMethod("setDuplicatedFilter", Boolean::class.javaPrimitiveType)
                method.invoke(settingsBuilder, false)
            } catch (_: Throwable) {}
        }
        val settings = settingsBuilder.build()

        try {
            leScanner?.stopScan(scanCallback)
            leScanner?.startScan(listOf(filter), settings, scanCallback)
            _isDiscovering.value = true
            Log.i(TAG, "BLE GATT scanning active for Walkie-Talkie peers (LE Coded PHY enabled: ${bluetoothAdapter.isLeCodedPhySupported})")
        } catch (e: Throwable) {
            Log.e(TAG, "startScan failed", e)
            reportError("BLE discovery could not start: ${e.message ?: e.javaClass.simpleName}")
            _isDiscovering.value = false
        }
    }

    override suspend fun stopAdvertising() {
        try {
            leAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Throwable) {}
        _isAdvertising.value = false
    }

    override suspend fun connect(peerId: String) {
        intentionallyDisconnectedPeers.remove(peerId)
        val device = try {
            bluetoothAdapter?.getRemoteDevice(peerId)
        } catch (e: Throwable) {
            null
        } ?: return

        if (clientGatts.containsKey(peerId)) return

        Log.i(TAG, "Connecting GATT client to $peerId")
        try {
            val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattClientCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattClientCallback)
            }
            if (gatt != null) {
                clientGatts[peerId] = gatt
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to connect GATT to $peerId", e)
            reportError("BLE connection could not start: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    override suspend fun acceptConnection(peerId: String) {
        // Receiving the CONNECT_REQ means the server-side GATT link is already
        // alive.  Opening a second client connection here used to tear down the
        // stable link on some stacks before authentication could finish.
        if (serverConnectedDevices.containsKey(peerId) || clientGatts.containsKey(peerId)) return
        connect(peerId)
    }

    override suspend fun confirmPairing(peerId: String, authCode: String) {
        if (authCode.length != 6 || authCode.any { !it.isDigit() }) return
        if (peerAuthCodes[peerId] != authCode) {
            Log.w(TAG, "Ignoring local confirmation with a stale or mismatched code for $peerId")
            return
        }
        val confirmation = VivoLeanProtocol.encode(
            type = VivoLeanProtocol.TYPE_AUTO_CONNECT,
            payload = "CONFIRM|$authCode".toByteArray(Charsets.UTF_8)
        )
        // GATT notifications and WRITE_TYPE_NO_RESPONSE have no delivery ACK.
        // Repeat this idempotent control packet so one controller-buffer loss
        // cannot leave the peers with asymmetric pairing state.
        repeat(3) { attempt ->
            writeRawPacket(peerId, confirmation)
            if (attempt < 2) delay(180L * (attempt + 1))
        }
        locallyConfirmedPairings.add(peerId)
        pairingCompleted(peerId)
    }

    override suspend fun rejectConnection(peerId: String) {
        peerAuthCodes[peerId]?.let { code ->
            val rejection = VivoLeanProtocol.encode(
                type = VivoLeanProtocol.TYPE_AUTO_CONNECT,
                payload = "REJECT|$code".toByteArray(Charsets.UTF_8)
            )
            repeat(2) { attempt ->
                writeRawPacket(peerId, rejection)
                if (attempt == 0) delay(180)
            }
            // Server-side notifications are serialized asynchronously. Keep
            // the GATT link alive long enough for REJECT to leave the queue;
            // otherwise the remote UI only observes a bare disconnect and can
            // remain on a stale verification screen.
            delay(650)
        }
        disconnect(peerId)
    }

    override suspend fun disconnect(peerId: String) {
        intentionallyDisconnectedPeers.add(peerId)
        cancelReconnect(peerId)
        clientGatts.remove(peerId)?.let { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Throwable) {}
        }
        peerCharacteristics.remove(peerId)
        peerMtus.remove(peerId)
        clearPairingState(peerId)
        serverConnectedDevices.remove(peerId)
        val current = _peers.value.toMutableMap()
        current[peerId]?.let {
            current[peerId] = it.copy(isConnected = false, isAuthenticated = false)
            _peers.value = current
        }
    }

    override suspend fun send(peerId: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return

        val mtu = peerMtus[peerId] ?: DEFAULT_MTU
        val maxChunk = (mtu - 3 - 4).coerceIn(16, SAFE_CHUNK_PAYLOAD)

        if (bytes.size <= maxChunk) {
            // Single packet
            val packet = ByteArray(1 + bytes.size).apply {
                this[0] = PACKET_TYPE_SINGLE
                System.arraycopy(bytes, 0, this, 1, bytes.size)
            }
            writeRawPacket(peerId, packet)
        } else {
            // Fragmented chunks with safe unsigned sequence and pacing (P0-06)
            val packetSeq = (outgoingPacketSeq.getAndIncrement() and 0xFF).toByte()
            val totalChunks = ((bytes.size + maxChunk - 1) / maxChunk)
            if (totalChunks > 64) {
                Log.w(TAG, "Frame too large for chunking: ${bytes.size}B -> $totalChunks chunks")
                return
            }

            var offset = 0
            var chunkIndex = 0

            while (offset < bytes.size) {
                val chunkSize = (bytes.size - offset).coerceAtMost(maxChunk)
                val chunk = ByteArray(4 + chunkSize).apply {
                    this[0] = PACKET_TYPE_CHUNK
                    this[1] = packetSeq
                    this[2] = (chunkIndex and 0xFF).toByte()
                    this[3] = (totalChunks and 0xFF).toByte()
                    System.arraycopy(bytes, offset, this, 4, chunkSize)
                }
                writeRawPacket(peerId, chunk)
                offset += chunkSize
                chunkIndex++
                if (offset < bytes.size) {
                    delay(5) // Metered 5ms pacing between successive chunks prevents BLE controller buffer overflow
                }
            }
        }
    }

    private fun writeRawPacket(peerId: String, packet: ByteArray) {
        // Priority 1: Write directly to remote characteristic via GATT Client
        val gatt = clientGatts[peerId]
        val char = peerCharacteristics[peerId]

        if (gatt != null && char != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(char, packet, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                } else {
                    @Suppress("DEPRECATION")
                    char.value = packet
                    @Suppress("DEPRECATION")
                    char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(char)
                }
                return
            } catch (e: Throwable) {
                Log.w(TAG, "Client writeCharacteristic error", e)
            }
        }

        // Priority 2: Notify connected device via GATT Server
        val serverDev = serverConnectedDevices[peerId]
        val srvChar = serverCharacteristic
        val srv = gattServer

        if (srv != null && serverDev != null && srvChar != null) {
            serverNotificationQueue.trySend(ServerNotification(peerId, packet.copyOf()))
            return
        }

        // If not connected yet, trigger auto-connect
        coroutineScope.launch { connect(peerId) }
    }

    override suspend fun stop() {
        stopAdvertising()
        try {
            leScanner?.stopScan(scanCallback)
        } catch (e: Throwable) {}
        _isDiscovering.value = false

        reconnectJobs.values.forEach { it.cancel() }
        reconnectJobs.clear()
        pairingDeadlineJobs.values.forEach { it.cancel() }
        pairingDeadlineJobs.clear()
        reconnectAttempts.clear()
        intentionallyDisconnectedPeers.clear()

        clientGatts.values.forEach { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Throwable) {}
        }
        clientGatts.clear()
        peerCharacteristics.clear()
        peerMtus.clear()
        serverConnectedDevices.clear()
        connectingAddresses.clear()
        incomingChunkBuffers.clear()
        ignoredRotatedScanAddresses.clear()
        locallyConfirmedPairings.clear()
        remotelyConfirmedPairings.clear()
        verifiedPairingAddresses.clear()

        try {
            gattServer?.close()
        } catch (e: Throwable) {}
        gattServer = null
        serverCharacteristic = null

        try {
            context.unregisterReceiver(bluetoothStateReceiver)
        } catch (e: Throwable) {}

        _peers.value = emptyMap()
        _state.value = TransportState.IDLE
    }
}
