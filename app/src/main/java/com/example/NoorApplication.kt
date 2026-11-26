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
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.backup.AutoBackupWorker
import com.example.data.local.KeystoreSecretStore
import com.example.data.remote.FirebaseManager
import com.example.data.repository.ShopRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class NoorApplication : Application(), ImageLoaderFactory {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var secretStore: KeystoreSecretStore
        private set
    lateinit var repository: ShopRepository
        private set

    override fun onCreate() {
        super.onCreate()
        FirebaseManager.ensureInitialized(this)
        secretStore = KeystoreSecretStore(this)
        repository = ShopRepository(this, secretStore)

        // Defer non-UI startup tasks off the critical main-thread frame
        appScope.launch(Dispatchers.IO) {
            secretStore.warmUpCache()
            createNotificationChannels()
            monitorConnectivity()
            scheduleAutoBackupWorker()
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024L * 1024L)
                    .build()
            }
            .crossfade(true)
            .build()
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
