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
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Periodic background worker that refreshes the home screen widget.
 *
 * Scheduled every 15 minutes (WorkManager's minimum interval).
 * Actively requests a fresh location fix to populate the system cache,
 * then triggers the widget's existing update logic via broadcast.
 *
 * Location strategy (mirrors LocationProvider):
 * - Play Services: FusedLocationProviderClient.getCurrentLocation()
 * - AOSP API 30+: LocationManager.getCurrentLocation()
 * - AOSP API 26-29: LocationManager.getLastKnownLocation()
 */
class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "WidgetUpdateWorker"
        private const val WORK_NAME = "widget_update"
        private const val LOCATION_TIMEOUT_MS = 10_000L

        /** Schedule periodic widget updates. Safe to call multiple times. */
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

        /** Run once immediately (e.g. when widget is first placed or location was unavailable). */
        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }

        /** Cancel periodic updates (e.g. when all widgets are removed). */
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

    /** Read the last GPS fix persisted by MainActivity. */
    private fun getSavedLocation(): Location? {
        val prefs = applicationContext.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("last_lat")) return null
        return Location("saved").apply {
            latitude = prefs.getFloat("last_lat", 0f).toDouble()
            longitude = prefs.getFloat("last_lon", 0f).toDouble()
        }
    }

    /**
     * Actively request a location fix and return it.
     * Returns null if permission is missing or location is unavailable.
     */
    private suspend fun requestFreshLocation(): Location? {
        val context = applicationContext
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        return if (isPlayServicesAvailable()) {
            requestViaPlayServices()
        } else {
            requestViaLocationManager()
        }
    }

    /** Use FusedLocationProviderClient.getCurrentLocation() — best accuracy, best cache. */
    private suspend fun requestViaPlayServices(): Location? {
        return try {
            val client = LocationServices.getFusedLocationProviderClient(applicationContext)
            val task = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            Tasks.await(task, LOCATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException requesting location via Play Services", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "Play Services location request failed, falling back", e)
            requestViaLocationManager()
        }
    }

    /** Use LocationManager.getCurrentLocation() (API 30+) or getLastKnownLocation() fallback. */
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

    /** API 30+: actively request a single location fix. */
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

    private fun isPlayServicesAvailable(): Boolean {
        return try {
            val result = GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(applicationContext)
            result == ConnectionResult.SUCCESS
        } catch (e: Exception) {
            false
        }
    }
}
