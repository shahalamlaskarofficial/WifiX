package com.wifix.app.core.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Monitors the active default network alongside individual Wi-Fi and Cellular interfaces.
 * Uses passive NetworkCallbacks to keep battery usage minimal until active multi-network bonding is requested.
 */
class DualNetworkMonitor(context: Context) {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val telephonyManager =
        appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private val _snapshot = MutableStateFlow(NetworkSnapshot())
    val snapshot: StateFlow<NetworkSnapshot> = _snapshot.asStateFlow()

    private var isRegistered = false

    private val defaultCallback = createCallback(
        onCapabilities = { network, caps -> updateDefault(network, caps) },
        onLostNetwork = { network ->
            _snapshot.update { current ->
                if (current.defaultNetworkHandle == network) {
                    current.copy(
                        activeTransport = ActiveTransportType.NONE,
                        isInternetValidated = false,
                        defaultNetworkHandle = null
                    )
                } else current
            }
        }
    )

    private val wifiCallback = createCallback(
        onCapabilities = { network, caps -> updateWifi(network, caps) },
        onLostNetwork = { network ->
            _snapshot.update { current ->
                if (current.wifi.networkHandle == network) {
                    current.copy(wifi = WifiLinkInfo())
                } else current
            }
        }
    )

    private val cellularCallback = createCallback(
        onCapabilities = { network, caps -> updateCellular(network, caps) },
        onLostNetwork = { network ->
            _snapshot.update { current ->
                if (current.cellular.networkHandle == network) {
                    current.copy(cellular = CellularLinkInfo())
                } else current
            }
        }
    )

    fun start() {
        if (isRegistered) return
        try {
            connectivityManager.registerDefaultNetworkCallback(defaultCallback)

            val wifiRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(wifiRequest, wifiCallback)

            val cellularRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(cellularRequest, cellularCallback)

            isRegistered = true
            refreshActiveSnapshot()
        } catch (_: SecurityException) {
            // Gracefully handled; permission banner prompts user if required
        }
    }

    fun stop() {
        if (!isRegistered) return
        runCatching { connectivityManager.unregisterNetworkCallback(defaultCallback) }
        runCatching { connectivityManager.unregisterNetworkCallback(wifiCallback) }
        runCatching { connectivityManager.unregisterNetworkCallback(cellularCallback) }
        isRegistered = false
    }

    fun refreshActiveSnapshot() {
        val activeNetwork = connectivityManager.activeNetwork ?: return
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return
        updateDefault(activeNetwork, caps)
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            updateWifi(activeNetwork, caps)
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            updateCellular(activeNetwork, caps)
        }
    }

    private fun updateDefault(network: Network, caps: NetworkCapabilities) {
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> ActiveTransportType.VPN
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ActiveTransportType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ActiveTransportType.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ActiveTransportType.ETHERNET
            else -> ActiveTransportType.NONE
        }
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        _snapshot.update { current ->
            current.copy(
                activeTransport = transport,
                isInternetValidated = validated,
                isMetered = metered,
                defaultNetworkHandle = network
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun updateWifi(network: Network, caps: NetworkCapabilities) {
        val info: WifiInfo? = (caps.transportInfo as? WifiInfo) ?: runCatching {
            wifiManager.connectionInfo
        }.getOrNull()

        val rawSsid = info?.ssid?.removeSurrounding("\"")
        val cleanSsid = if (rawSsid.isNullOrBlank() || rawSsid == "<unknown ssid>") {
            null
        } else {
            rawSsid
        }

        val freq = info?.frequency?.takeIf { it > 0 }
        val rssi = info?.rssi?.takeIf { it != -127 }
            ?: caps.signalStrength.takeIf { it != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED }

        val standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && info != null) {
            mapWifiStandard(info.wifiStandard)
        } else {
            "802.11"
        }

        val rxSpeed = info?.rxLinkSpeedMbps?.takeIf { it > 0 }
        val txSpeed = info?.txLinkSpeedMbps?.takeIf { it > 0 }

        _snapshot.update { current ->
            current.copy(
                wifi = WifiLinkInfo(
                    isConnected = true,
                    isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    ssid = cleanSsid,
                    rssiDbm = rssi,
                    frequencyMhz = freq,
                    band = NetworkMath.classifyWifiBand(freq),
                    linkSpeedMbps = info?.linkSpeed?.takeIf { it > 0 },
                    rxLinkSpeedMbps = rxSpeed,
                    txLinkSpeedMbps = txSpeed,
                    wifiStandard = standard,
                    downstreamEstimateKbps = caps.linkDownstreamBandwidthKbps,
                    upstreamEstimateKbps = caps.linkUpstreamBandwidthKbps,
                    networkHandle = network
                )
            )
        }
    }

    private fun updateCellular(network: Network, caps: NetworkCapabilities) {
        val signal = caps.signalStrength.takeIf {
            it != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED
        }
        val generation = readCellularGeneration()

        _snapshot.update { current ->
            current.copy(
                cellular = CellularLinkInfo(
                    isConnected = true,
                    isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    generation = generation,
                    signalStrengthDbm = signal,
                    isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                    downstreamEstimateKbps = caps.linkDownstreamBandwidthKbps,
                    upstreamEstimateKbps = caps.linkUpstreamBandwidthKbps,
                    networkHandle = network
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun readCellularGeneration(): String {
        val hasPhoneState = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPhoneState) return "Permission Required"

        return try {
            when (telephonyManager.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
                TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G HSPA"
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                else -> "Cellular"
            }
        } catch (_: SecurityException) {
            "Restricted"
        }
    }

    private fun mapWifiStandard(standard: Int): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when (standard) {
                ScanResult.WIFI_STANDARD_11BE -> "Wi-Fi 7 (802.11be)"
                ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6 (802.11ax)"
                ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5 (802.11ac)"
                ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4 (802.11n)"
                ScanResult.WIFI_STANDARD_LEGACY -> "Legacy 802.11"
                else -> "802.11"
            }
        } else {
            "802.11"
        }
    }

    private fun createCallback(
        onCapabilities: (Network, NetworkCapabilities) -> Unit,
        onLostNetwork: (Network) -> Unit
    ): ConnectivityManager.NetworkCallback {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    onCapabilities(network, caps)
                }

                override fun onLost(network: Network) {
                    onLostNetwork(network)
                }
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    onCapabilities(network, caps)
                }

                override fun onLost(network: Network) {
                    onLostNetwork(network)
                }
            }
        }
    }
}
