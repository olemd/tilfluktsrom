package no.naiv.tilfluktsrom.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Periodic background worker that refreshes the home screen widget.
 *
 * F-Droid flavor: uses LocationManager only (no Google Play Services).
 */
class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "WidgetUpdateWorker"
        private const val WORK_NAME = "widget_update"
        private const val LOCATION_TIMEOUT_MS = 10_000L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val location = requestFreshLocation() ?: getSavedLocation()
        if (location != null) {
            ShelterWidgetProvider.requestUpdateWithLocation(
                applicationContext, location.latitude, location.longitude
            )
        } else {
            ShelterWidgetProvider.requestUpdate(applicationContext)
        }
        return Result.success()
    }

    /** Returns null if older than 24 hours. */
    private fun getSavedLocation(): Location? {
        val prefs = applicationContext.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("last_lat")) return null
        val age = System.currentTimeMillis() - prefs.getLong("last_time", 0L)
        if (age > 24 * 60 * 60 * 1000L) return null
        return Location("saved").apply {
            latitude = prefs.getFloat("last_lat", 0f).toDouble()
            longitude = prefs.getFloat("last_lon", 0f).toDouble()
        }
    }

    private suspend fun requestFreshLocation(): Location? {
        val context = applicationContext
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        return requestViaLocationManager()
    }

    private suspend fun requestViaLocationManager(): Location? {
        val locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE)
            as? LocationManager ?: return null

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            else -> return null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return requestCurrentLocation(locationManager, provider)
        }
        // API 26-29: fall back to passive cache
        return try {
            locationManager.getLastKnownLocation(provider)
        } catch (e: SecurityException) {
            null
        }
    }

    private suspend fun requestCurrentLocation(locationManager: LocationManager, provider: String): Location? {
        return try {
            withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    locationManager.getCurrentLocation(
                        provider,
                        signal,
                        applicationContext.mainExecutor
                    ) { location ->
                        if (cont.isActive) cont.resume(location)
                    }
                    cont.invokeOnCancellation { signal.cancel() }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException requesting location via LocationManager", e)
            null
        }
    }
}
