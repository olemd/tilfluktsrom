package no.naiv.tilfluktsrom.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Provides GPS location updates using the Fused Location Provider.
 * Emits location updates as a Flow for reactive consumption.
 *
 * Closes the Flow with a SecurityException if location permission is not granted,
 * so callers can detect and handle this failure explicitly.
 */
class LocationProvider(private val context: Context) {

    companion object {
        private const val TAG = "LocationProvider"
        private const val UPDATE_INTERVAL_MS = 5000L
        private const val FASTEST_INTERVAL_MS = 2000L
    }

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Stream of location updates. Emits the last known location first (if available),
     * then continuous updates. Throws SecurityException if permission is not granted.
     */
    fun locationUpdates(): Flow<Location> = callbackFlow {
        if (!hasLocationPermission()) {
            close(SecurityException("Location permission not granted"))
            return@callbackFlow
        }

        // Try to get last known location for immediate display
        try {
            fusedClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val result = trySend(location)
                        if (result.isFailure) {
                            Log.w(TAG, "Failed to emit last known location")
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Could not get last known location", e)
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException getting last location", e)
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            setWaitForAccurateLocation(false)
        }.build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val sendResult = trySend(location)
                    if (sendResult.isFailure) {
                        Log.w(TAG, "Failed to emit location update")
                    }
                }
            }
        }

        try {
            fusedClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException requesting location updates", e)
            close(e)
            return@callbackFlow
        }

        awaitClose {
            fusedClient.removeLocationUpdates(callback)
        }
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
