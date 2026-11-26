package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.backup.AutoBackupWorker
import com.example.data.local.KeystoreSecretStore
import com.example.data.remote.FirebaseManager
import com.example.data.repository.ShopRepository
import java.util.concurrent.TimeUnit

class NoorApplication : Application() {
    lateinit var secretStore: KeystoreSecretStore
        private set
    lateinit var repository: ShopRepository
        private set

    override fun onCreate() {
        super.onCreate()
        FirebaseManager.ensureInitialized(this)
        secretStore = KeystoreSecretStore(this)
        repository = ShopRepository(this, secretStore)

        createNotificationChannels()
        monitorConnectivity()
        scheduleAutoBackupWorker()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel(
                    "noor_screen_share",
                    "AI Screen Share",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Active while Noor AI Assistant is viewing your app screen"
                }
            )
        }
    }

    private fun monitorConnectivity() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        val isOnlineNow = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        repository.syncEngine.setOnlineState(isOnlineNow)

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                repository.syncEngine.setOnlineState(true)
            }

            override fun onLost(network: Network) {
                repository.syncEngine.setOnlineState(false)
            }
        })
    }

    private fun scheduleAutoBackupWorker() {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "noor_auto_backup_work",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
