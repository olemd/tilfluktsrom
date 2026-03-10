package no.naiv.tilfluktsrom.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Provides GPS location updates using AOSP LocationManager.
 *
 * F-Droid flavor: no Google Play Services dependency. Uses GPS + Network providers
 * directly via LocationManager (available on all Android 8.0+ devices).
 */
class LocationProvider(private val context: Context) {

    companion object {
        private const val TAG = "LocationProvider"
        private const val UPDATE_INTERVAL_MS = 5000L
    }

    init {
        Log.d(TAG, "Location backend: LocationManager (F-Droid build)")
    }

    /**
     * Stream of location updates. Emits the last known location first (if available),
     * then continuous updates. Throws SecurityException if permission is not granted.
     */
    fun locationUpdates(): Flow<Location> = callbackFlow {
        if (!hasLocationPermission()) {
            close(SecurityException("Location permission not granted"))
            return@callbackFlow
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE)
            as? LocationManager

        if (locationManager == null) {
            close(IllegalStateException("LocationManager not available"))
            return@callbackFlow
        }

        // Emit best last known location immediately (pick most recent of GPS/Network)
        try {
            val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val best = listOfNotNull(lastGps, lastNetwork).maxByOrNull { it.time }
            if (best != null) {
                val result = trySend(best)
                if (result.isFailure) {
                    Log.w(TAG, "Failed to emit last known location")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException getting last known location", e)
        }

        // LocationListener compatible with API 26-28 (onStatusChanged required before API 29)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val sendResult = trySend(location)
                if (sendResult.isFailure) {
                    Log.w(TAG, "Failed to emit location update")
                }
            }

            // Required for API 26-28 compatibility (deprecated from API 29+)
            @Deprecated("Deprecated in API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            // Request from both providers: GPS is accurate, Network gives faster first fix
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    UPDATE_INTERVAL_MS,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    UPDATE_INTERVAL_MS,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException requesting location updates", e)
            close(e)
            return@callbackFlow
        }

        awaitClose {
            locationManager.removeUpdates(listener)
        }
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
