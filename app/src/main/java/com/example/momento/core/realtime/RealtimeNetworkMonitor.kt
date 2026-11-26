package com.example.momento.core.realtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

fun interface RealtimeNetworkMonitor {
    fun watch(onRestored: () -> Unit): AutoCloseable

    fun hasNetwork(): Boolean = true
}

class AndroidRealtimeNetworkMonitor @Inject constructor(
    @ApplicationContext context: Context,
) : RealtimeNetworkMonitor {
    private val connectivity = checkNotNull(context.getSystemService(ConnectivityManager::class.java))

    override fun hasNetwork(): Boolean = connectivity.activeNetwork != null

    override fun watch(onRestored: () -> Unit): AutoCloseable {
        var current = connectivity.activeNetwork
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (network != current) {
                    onRestored()
                }
                current = network
            }

            override fun onLost(network: Network) {
                if (network == current) {
                    current = null
                }
            }
        }
        connectivity.registerDefaultNetworkCallback(callback)
        return AutoCloseable {
            connectivity.unregisterNetworkCallback(callback)
        }
    }
}
