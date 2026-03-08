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
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import kotlinx.coroutines.runBlocking
import no.naiv.tilfluktsrom.MainActivity
import no.naiv.tilfluktsrom.R
import no.naiv.tilfluktsrom.data.ShelterDatabase
import no.naiv.tilfluktsrom.location.ShelterFinder
import no.naiv.tilfluktsrom.util.DistanceUtils

/**
 * Home screen widget showing the nearest shelter with distance.
 *
 * Update strategy: no automatic periodic updates (updatePeriodMillis=0).
 * Updates only when the user taps the refresh button, which sends ACTION_REFRESH.
 * Tapping the widget body opens MainActivity.
 *
 * Uses LocationManager directly (not the hybrid LocationProvider) because
 * BroadcastReceiver context makes FusedLocationProviderClient setup awkward.
 * For a one-shot getLastKnownLocation, LocationManager is equally effective.
 */
class ShelterWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "ShelterWidget"
        const val ACTION_REFRESH = "no.naiv.tilfluktsrom.widget.REFRESH"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, ShelterWidgetProvider::class.java)
            )
            for (appWidgetId in widgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
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

        // Check location permission
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showFallback(views, context.getString(R.string.widget_open_app))
            appWidgetManager.updateAppWidget(appWidgetId, views)
            return
        }

        // Get last known location from LocationManager
        val location = getLastKnownLocation(context)
        if (location == null) {
            showFallback(views, context.getString(R.string.widget_no_location))
            appWidgetManager.updateAppWidget(appWidgetId, views)
            return
        }

        // Query shelters from Room (fast: ~556 rows, <10ms)
        val shelters = try {
            val dao = ShelterDatabase.getInstance(context).shelterDao()
            runBlocking { dao.getAllSheltersList() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query shelters", e)
            emptyList()
        }

        if (shelters.isEmpty()) {
            showFallback(views, context.getString(R.string.widget_no_data))
            appWidgetManager.updateAppWidget(appWidgetId, views)
            return
        }

        // Find nearest shelter
        val nearest = ShelterFinder.findNearest(
            shelters, location.latitude, location.longitude, 1
        ).firstOrNull()

        if (nearest == null) {
            showFallback(views, context.getString(R.string.widget_no_data))
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

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    /** Show a fallback message when location or data is unavailable. */
    private fun showFallback(views: RemoteViews, message: String) {
        views.setTextViewText(R.id.widgetAddress, message)
        views.setTextViewText(R.id.widgetDetails, "")
        views.setTextViewText(R.id.widgetDistance, "")
    }

    /** Get the best last known location from GPS and Network providers. */
    private fun getLastKnownLocation(context: Context): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE)
            as? LocationManager ?: return null

        return try {
            val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            listOfNotNull(lastGps, lastNetwork).maxByOrNull { it.time }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException getting last known location", e)
            null
        }
    }
}
