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
import no.naiv.tilfluktsrom.MainActivity
import no.naiv.tilfluktsrom.R
import no.naiv.tilfluktsrom.data.ShelterDatabase
import no.naiv.tilfluktsrom.location.ShelterFinder
import no.naiv.tilfluktsrom.util.DistanceUtils
import java.util.concurrent.TimeUnit

/**
 * Home screen widget showing the nearest shelter with distance.
 *
 * F-Droid flavor: uses LocationManager only (no Google Play Services).
 */
class ShelterWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "ShelterWidget"
        const val ACTION_REFRESH = "no.naiv.tilfluktsrom.widget.REFRESH"
        private const val EXTRA_LATITUDE = "lat"
        private const val EXTRA_LONGITUDE = "lon"

        fun requestUpdate(context: Context) {
            val intent = Intent(context, ShelterWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            context.sendBroadcast(intent)
        }

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

        val openAppIntent = Intent(context, MainActivity::class.java)
        val openAppPending = PendingIntent.getActivity(
            context, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, openAppPending)

        val refreshIntent = Intent(context, ShelterWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPending = PendingIntent.getBroadcast(
            context, 0, refreshIntent, PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRefreshButton, refreshPending)

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

        val nearest = ShelterFinder.findNearest(
            shelters, location.latitude, location.longitude, 1
        ).firstOrNull()

        if (nearest == null) {
            showFallback(context, views, context.getString(R.string.widget_no_data))
            appWidgetManager.updateAppWidget(appWidgetId, views)
            return
        }

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

    private fun showFallback(context: Context, views: RemoteViews, message: String) {
        views.setTextViewText(R.id.widgetAddress, message)
        views.setTextViewText(R.id.widgetDetails, "")
        views.setTextViewText(R.id.widgetDistance, "")
        views.setTextViewText(R.id.widgetTimestamp, formatTimestamp(context))
    }

    private fun formatTimestamp(context: Context): String {
        val format = DateFormat.getTimeFormat(context)
        val timeStr = format.format(System.currentTimeMillis())
        return context.getString(R.string.widget_updated_at, timeStr)
    }

    /**
     * Get the best available location via LocationManager or SharedPreferences.
     * Safe to call from a background thread.
     */
    private fun getBestLocation(context: Context): Location? {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return null

        val lmLocation = getLocationManagerLocation(context)
        if (lmLocation != null) return lmLocation

        return getSavedLocation(context)
    }

    /** Returns null if older than 24 hours to avoid retaining stale location data. */
    private fun getSavedLocation(context: Context): Location? {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("last_lat")) return null
        val age = System.currentTimeMillis() - prefs.getLong("last_time", 0L)
        if (age > 24 * 60 * 60 * 1000L) return null
        return Location("saved").apply {
            latitude = prefs.getFloat("last_lat", 0f).toDouble()
            longitude = prefs.getFloat("last_lon", 0f).toDouble()
        }
    }

    private fun getLocationManagerLocation(context: Context): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE)
            as? LocationManager ?: return null

        try {
            val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val cached = listOfNotNull(lastGps, lastNetwork).maxByOrNull { it.time }
            if (cached != null) return cached
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException getting last known location", e)
            return null
        }

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
}
