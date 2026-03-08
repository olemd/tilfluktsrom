package no.naiv.tilfluktsrom.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.text.format.DateFormat
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import no.naiv.tilfluktsrom.MainActivity
import no.naiv.tilfluktsrom.R
import no.naiv.tilfluktsrom.data.ShelterDatabase
import no.naiv.tilfluktsrom.location.ShelterFinder
import no.naiv.tilfluktsrom.util.DistanceUtils
import java.util.concurrent.TimeUnit

/**
 * Home screen widget showing the nearest shelter with distance.
 *
 * Update strategy:
 * - Background: WorkManager runs every 15 min while widget exists
 * - Live: MainActivity sends ACTION_REFRESH on each GPS location update
 * - Manual: user taps the refresh button on the widget
 *
 * Location resolution (in priority order):
 * 1. Location provided via intent extras (from WorkManager or MainActivity)
 * 2. FusedLocationProviderClient cache/active request (Play Services)
 * 3. LocationManager cache/active request (AOSP fallback)
 * 4. Last GPS fix saved to SharedPreferences by MainActivity
 *
 * Note: Background processes cannot reliably trigger GPS hardware on
 * Android 8+. The SharedPreferences fallback ensures the widget works
 * after app updates and reboots without opening the app first.
 */
class ShelterWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "ShelterWidget"
        const val ACTION_REFRESH = "no.naiv.tilfluktsrom.widget.REFRESH"
        private const val EXTRA_LATITUDE = "lat"
        private const val EXTRA_LONGITUDE = "lon"

        /** Trigger a widget refresh from anywhere (e.g. MainActivity on location update). */
        fun requestUpdate(context: Context) {
            val intent = Intent(context, ShelterWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            context.sendBroadcast(intent)
        }

        /** Trigger a widget refresh with a known location (from WidgetUpdateWorker). */
        fun requestUpdateWithLocation(context: Context, latitude: Double, longitude: Double) {
            val intent = Intent(context, ShelterWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
                putExtra(EXTRA_LATITUDE, latitude)
                putExtra(EXTRA_LONGITUDE, longitude)
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdateWorker.cancel(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WidgetUpdateWorker.schedule(context)
        updateAllWidgetsAsync(context, null)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_REFRESH) {
            val providedLocation = if (intent.hasExtra(EXTRA_LATITUDE)) {
                Location("widget").apply {
                    latitude = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                    longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
                }
            } else null

            updateAllWidgetsAsync(context, providedLocation)
        }
    }

    /**
     * Run widget update on a background thread so we can call
     * FusedLocationProviderClient.getLastLocation() synchronously.
     * Uses goAsync() to keep the BroadcastReceiver alive until done.
     */
    private fun updateAllWidgetsAsync(context: Context, providedLocation: Location?) {
        val pendingResult = goAsync()
        Thread {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val widgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, ShelterWidgetProvider::class.java)
                )
                val location = providedLocation ?: getBestLocation(context)
                for (appWidgetId in widgetIds) {
                    updateWidget(context, appWidgetManager, appWidgetId, location)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update widgets", e)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        location: Location?
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_nearest_shelter)

        // Tapping widget body opens the app
        val openAppIntent = Intent(context, MainActivity::class.java)
        val openAppPending = PendingIntent.getActivity(
            context, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, openAppPending)

        // Refresh button sends our custom broadcast
        val refreshIntent = Intent(context, ShelterWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPending = PendingIntent.getBroadcast(
            context, 0, refreshIntent, PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRefreshButton, refreshPending)

        // Check permission
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showFallback(context, views, context.getString(R.string.widget_open_app))
            appWidgetManager.updateAppWidget(appWidgetId, views)
            return
        }

        if (location == null) {
            showFallback(context, views, context.getString(R.string.widget_no_location))
            appWidgetManager.updateAppWidget(appWidgetId, views)
            return
        }

        // Query shelters from Room (fast: ~556 rows, <10ms)
        val shelters = try {
            val dao = ShelterDatabase.getInstance(context).shelterDao()
            kotlinx.coroutines.runBlocking { dao.getAllSheltersList() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query shelters", e)
            emptyList()
        }

        if (shelters.isEmpty()) {
            showFallback(context, views, context.getString(R.string.widget_no_data))
            appWidgetManager.updateAppWidget(appWidgetId, views)
            return
        }

        // Find nearest shelter
        val nearest = ShelterFinder.findNearest(
            shelters, location.latitude, location.longitude, 1
        ).firstOrNull()

        if (nearest == null) {
            showFallback(context, views, context.getString(R.string.widget_no_data))
            appWidgetManager.updateAppWidget(appWidgetId, views)
            return
        }

        // Show shelter info
        views.setTextViewText(R.id.widgetAddress, nearest.shelter.adresse)
        views.setTextViewText(
            R.id.widgetDetails,
            context.getString(R.string.shelter_capacity, nearest.shelter.plasser)
        )
        views.setTextViewText(
            R.id.widgetDistance,
            DistanceUtils.formatDistance(nearest.distanceMeters)
        )
        views.setTextViewText(R.id.widgetTimestamp, formatTimestamp(context))

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    /** Show a fallback message when location or data is unavailable. */
    private fun showFallback(context: Context, views: RemoteViews, message: String) {
        views.setTextViewText(R.id.widgetAddress, message)
        views.setTextViewText(R.id.widgetDetails, "")
        views.setTextViewText(R.id.widgetDistance, "")
        views.setTextViewText(R.id.widgetTimestamp, formatTimestamp(context))
    }

    /** Format current time as HH:mm, respecting the user's 12/24h preference. */
    private fun formatTimestamp(context: Context): String {
        val format = DateFormat.getTimeFormat(context)
        return format.format(System.currentTimeMillis())
    }

    /**
     * Get the best available location.
     * Tries FusedLocationProviderClient first (Play Services, better cache),
     * then LocationManager (AOSP), then last saved GPS fix from SharedPreferences.
     * Safe to call from a background thread.
     */
    private fun getBestLocation(context: Context): Location? {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return null

        // Try Play Services first — maintains a better location cache
        val fusedLocation = getFusedLastLocation(context)
        if (fusedLocation != null) return fusedLocation

        // Fall back to LocationManager
        val lmLocation = getLocationManagerLocation(context)
        if (lmLocation != null) return lmLocation

        // Fall back to last location saved by MainActivity
        return getSavedLocation(context)
    }

    /** Read the last GPS fix persisted by MainActivity to SharedPreferences. */
    private fun getSavedLocation(context: Context): Location? {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("last_lat")) return null
        return Location("saved").apply {
            latitude = prefs.getFloat("last_lat", 0f).toDouble()
            longitude = prefs.getFloat("last_lon", 0f).toDouble()
        }
    }

    /**
     * Get location via Play Services — blocks, call from background thread.
     * Tries cached location first, then actively requests a fix if cache is empty.
     */
    private fun getFusedLastLocation(context: Context): Location? {
        if (!isPlayServicesAvailable(context)) return null
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            // Try cache first (instant)
            val cached = Tasks.await(client.lastLocation, 3, TimeUnit.SECONDS)
            if (cached != null) return cached
            // Cache empty — actively request a fix (turns on GPS/network)
            val task = client.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY, null
            )
            Tasks.await(task, 10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "FusedLocationProvider failed", e)
            null
        }
    }

    /**
     * Get location via LocationManager (AOSP).
     * Tries cache first, then actively requests a fix on API 30+.
     * Blocks — call from background thread.
     */
    private fun getLocationManagerLocation(context: Context): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE)
            as? LocationManager ?: return null

        // Try cache first
        try {
            val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val cached = listOfNotNull(lastGps, lastNetwork).maxByOrNull { it.time }
            if (cached != null) return cached
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException getting last known location", e)
            return null
        }

        // Cache empty — actively request on API 30+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = when {
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                    LocationManager.NETWORK_PROVIDER
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                    LocationManager.GPS_PROVIDER
                else -> return null
            }
            try {
                val latch = java.util.concurrent.CountDownLatch(1)
                var result: Location? = null
                val signal = CancellationSignal()
                locationManager.getCurrentLocation(
                    provider, signal, context.mainExecutor
                ) { location ->
                    result = location
                    latch.countDown()
                }
                latch.await(10, TimeUnit.SECONDS)
                signal.cancel()
                return result
            } catch (e: Exception) {
                Log.e(TAG, "Active location request failed", e)
            }
        }

        return null
    }

    private fun isPlayServicesAvailable(context: Context): Boolean {
        return try {
            val result = GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context)
            result == ConnectionResult.SUCCESS
        } catch (e: Exception) {
            false
        }
    }
}
