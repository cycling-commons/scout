package org.cyclingcommons.scout.sensors.radar

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.cyclingcommons.scout.domain.RadarLinkState
import org.cyclingcommons.scout.domain.RadarObservation

/** Everything the UI needs to know about the radar link. */
data class RadarStatus(
    val link: RadarLinkState = RadarLinkState.ABSENT,
    /** Start/Resume is still trying to reach a saved radar. */
    val seeking: Boolean = false,
    val devices: List<RadarDeviceRow> = emptyList(),
    val savedName: String? = null,
    val savedAddress: String? = null,
    val antDeviceNumber: Int? = null,
    val antAvailable: Boolean = false,
    val bluetoothOn: Boolean = false,
    val bluetoothPermission: Boolean = false,
    val transport: RadarTransport = RadarTransport.AUTO,
) {
    val hasSavedRadar: Boolean get() = savedAddress != null
    val tracking: Boolean get() = link == RadarLinkState.TRACKING
}

/**
 * Radar connection policy for a ride (SPEC §8, TECHNICAL §5): connect only while
 * RUNNING or on the pair screen, never scan mid-ride, and back off between retries
 * instead of hammering the radio at a fixed interval. A tap on "No radar" opens a
 * fresh 45 s seek (Garmin parity) when the Varia was off at Start.
 *
 * Capability probes (ANT service present, Bluetooth on, permissions granted) are
 * cached here — they are binder calls and must not run on the sampling tick.
 */
class RadarCoordinator(context: Context) {
    private val session = CompositeRadarSession(context)
    private val prefs = RadarPrefs(context)
    private val found = LinkedHashMap<String, RadarDeviceRow>()

    private val _status = MutableStateFlow(RadarStatus())
    val status: StateFlow<RadarStatus> = _status.asStateFlow()

    /** Set when the link needs the UI to refresh outside a scheduled tick. */
    var onStatusChanged: (() -> Unit)? = null

    private var seekUntilMs = 0L
    private var lastRetryAtMs = 0L
    private var retryGapMs = RETRY_GAP_MIN_MS
    private var hadTracking = false
    private var rideActive = false

    init {
        session.onStateChanged = { link ->
            if (link == RadarLinkState.TRACKING) {
                hadTracking = true
                retryGapMs = RETRY_GAP_MIN_MS
            }
            _status.update { it.copy(link = link, seeking = isSeeking(now())) }
            onStatusChanged?.invoke()
        }
        session.onBleDeviceFound = ::mergeDevice
        session.ble.onNameResolved = { address, name ->
            prefs.rememberName(address, name)
            if (prefs.address.equals(address, ignoreCase = true)) {
                prefs.name = name
            }
            mergeDevice(RadarDeviceRow(address, name, likelyRadar = true))
            _status.update {
                if (it.savedAddress.equals(address, ignoreCase = true)) {
                    it.copy(savedName = name)
                } else {
                    it
                }
            }
        }
        session.onAntDeviceFound = { number ->
            _status.update {
                it.copy(
                    antDeviceNumber = number,
                    savedName = antLabel(number),
                    savedAddress = antAddress(number),
                )
            }
            onStatusChanged?.invoke()
        }
        refreshCapabilities()
    }

    fun observation(): RadarObservation = session.observation()

    /** Re-read cached capability flags — on resume, after a permission prompt, on pair. */
    fun refreshCapabilities() {
        _status.update {
            it.copy(
                link = session.state(),
                antAvailable = session.antAvailable(),
                bluetoothOn = session.ble.bluetoothEnabled(),
                bluetoothPermission = session.ble.hasBluetoothPermission(),
                transport = prefs.transport,
                savedName = prefs.name,
                savedAddress = savedAddress(),
                antDeviceNumber = prefs.antDeviceNumber,
            )
        }
    }

    fun onRideStart() {
        rideActive = true
        hadTracking = false
        beginSeek()
    }

    /**
     * Garmin parity: a tap on "No radar" opens a fresh seek. The first Start/Resume
     * window is 45 s and then stops; without this, a Varia powered on later never
     * connects. Does not scan — reconnects the saved device only.
     */
    fun searchFromTap() {
        if (!rideActive || !hasSavedRadar()) return
        beginSeek()
    }

    fun onRideStop() {
        rideActive = false
        hadTracking = false
        seekUntilMs = 0L
        session.disconnect()
        publishLink()
    }

    /** Called on the ride tick: retry a dropped or missing radar, with backoff. */
    fun onTick(nowMs: Long) {
        retryIfDue(nowMs)
        val seeking = isSeeking(nowMs)
        if (_status.value.seeking != seeking) {
            _status.update { it.copy(seeking = seeking, link = session.state()) }
        }
    }

    private fun retryIfDue(nowMs: Long) {
        if (!rideActive || !hasSavedRadar()) return
        when (session.state()) {
            RadarLinkState.TRACKING -> {
                hadTracking = true
                retryGapMs = RETRY_GAP_MIN_MS
                return
            }
            RadarLinkState.CONNECTING, RadarLinkState.SCANNING -> {
                // One attempt in flight; BleRadarSession times CONNECTING out itself.
                if (giveUpSeeking(nowMs)) session.disconnect()
                return
            }
            RadarLinkState.DISCONNECTED, RadarLinkState.ABSENT -> Unit
        }
        if (!mayRetry(nowMs)) {
            if (giveUpSeeking(nowMs)) session.disconnect()
            return
        }
        if (nowMs - lastRetryAtMs < retryGapMs) return
        lastRetryAtMs = nowMs
        if (hadTracking) {
            retryGapMs = (retryGapMs * 2).coerceAtMost(RETRY_GAP_MAX_MS)
        }
        session.connectForRide()
    }

    fun openPairing() {
        found.clear()
        _status.update { it.copy(devices = emptyList()) }
        refreshCapabilities()
    }

    fun closePairing(rideIdle: Boolean) {
        session.stopBleScan()
        if (rideIdle) session.disconnect()
        refreshCapabilities()
    }

    fun startScan() {
        found.clear()
        _status.update { it.copy(devices = emptyList()) }
        session.startBleScan()
        refreshCapabilities()
    }

    fun stopScan() {
        session.stopBleScan()
        refreshCapabilities()
    }

    fun startAntSearch() {
        session.startAntSearch()
        refreshCapabilities()
    }

    fun select(row: RadarDeviceRow) {
        session.selectBle(row)
        refreshCapabilities()
    }

    fun forget() {
        session.forget()
        found.clear()
        _status.update {
            it.copy(
                link = RadarLinkState.ABSENT,
                devices = emptyList(),
                savedName = null,
                savedAddress = null,
                antDeviceNumber = null,
                seeking = false,
            )
        }
    }

    fun setTransport(transport: RadarTransport) {
        session.setTransportPreference(transport)
        refreshCapabilities()
    }

    private fun beginSeek() {
        retryGapMs = RETRY_GAP_MIN_MS
        lastRetryAtMs = 0L
        seekUntilMs = now() + SEEK_MS
        session.connectForRide()
        publishLink()
    }

    private fun publishLink() {
        _status.update {
            it.copy(
                link = session.state(),
                seeking = isSeeking(now()),
                savedName = prefs.name,
                savedAddress = savedAddress(),
                antDeviceNumber = prefs.antDeviceNumber,
            )
        }
    }

    private fun mergeDevice(row: RadarDeviceRow) {
        val remembered = prefs.rememberedName(row.address)
        val named =
            if (row.name.isNullOrBlank() && remembered != null) {
                row.copy(name = remembered, likelyRadar = true)
            } else {
                row
            }
        val previous = found[named.address]
        found[named.address] =
            if (previous == null) {
                named
            } else {
                named.copy(
                    name = named.name?.takeIf { it.isNotBlank() } ?: previous.name,
                    rssi = maxOf(named.rssi, previous.rssi),
                    likelyRadar = named.likelyRadar || previous.likelyRadar,
                )
            }
        _status.update { it.copy(devices = sortedDevices()) }
    }

    private fun sortedDevices(): List<RadarDeviceRow> =
        found.values.sortedWith(
            compareByDescending<RadarDeviceRow> { it.likelyRadar }
                .thenByDescending { it.rssi }
                .thenBy { it.name ?: "~" },
        )

    private fun hasSavedRadar(): Boolean =
        prefs.address != null || prefs.antDeviceNumber != null

    private fun savedAddress(): String? =
        prefs.address ?: prefs.antDeviceNumber?.let(::antAddress)

    private fun isSeeking(nowMs: Long): Boolean {
        if (!rideActive || !hasSavedRadar()) return false
        if (session.state() == RadarLinkState.TRACKING) return false
        return hadTracking || (seekUntilMs > 0L && nowMs < seekUntilMs)
    }

    private fun mayRetry(nowMs: Long): Boolean =
        hadTracking || (seekUntilMs > 0L && nowMs < seekUntilMs)

    /** Stop paying for a radio that never answered after [SEEK_MS]. */
    private fun giveUpSeeking(nowMs: Long): Boolean {
        if (hadTracking || seekUntilMs == 0L || nowMs < seekUntilMs) return false
        seekUntilMs = 0L
        return true
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        /** Give up on a radar that never showed up after Start/Resume. */
        private const val SEEK_MS = 45_000L
        private const val RETRY_GAP_MIN_MS = 5_000L
        private const val RETRY_GAP_MAX_MS = 60_000L

        fun antLabel(deviceNumber: Int) = "ANT+ radar #$deviceNumber"
        fun antAddress(deviceNumber: Int) = "ANT+$deviceNumber"
    }
}
