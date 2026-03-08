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
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
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
 * Provides GPS location updates with automatic fallback.
 *
 * Uses FusedLocationProviderClient when Google Play Services is available (most devices),
 * and falls back to LocationManager (GPS + Network providers) for degoogled/F-Droid devices.
 *
 * The public API is identical regardless of backend: locationUpdates() emits a Flow<Location>.
 */
class LocationProvider(private val context: Context) {

    companion object {
        private const val TAG = "LocationProvider"
        private const val UPDATE_INTERVAL_MS = 5000L
        private const val FASTEST_INTERVAL_MS = 2000L
    }

    /** Checked once at construction — Play Services availability won't change at runtime. */
    private val usePlayServices: Boolean = isPlayServicesAvailable()

    init {
        Log.d(TAG, "Location backend: ${if (usePlayServices) "Play Services" else "LocationManager"}")
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

        if (usePlayServices) {
            val fusedClient: FusedLocationProviderClient =
                LocationServices.getFusedLocationProviderClient(context)

            // Emit last known location immediately for fast first display
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
        } else {
            // Fallback: LocationManager for devices without Play Services
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
                        Log.w(TAG, "Failed to emit last known location (fallback)")
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException getting last known location (fallback)", e)
            }

            // LocationListener compatible with API 26-28 (onStatusChanged required before API 29)
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    val sendResult = trySend(location)
                    if (sendResult.isFailure) {
                        Log.w(TAG, "Failed to emit location update (fallback)")
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
                Log.e(TAG, "SecurityException requesting location updates (fallback)", e)
                close(e)
                return@callbackFlow
            }

            awaitClose {
                locationManager.removeUpdates(listener)
            }
        }
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isPlayServicesAvailable(): Boolean {
        return try {
            val result = GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context)
            result == ConnectionResult.SUCCESS
        } catch (e: Exception) {
            // Play Services library might not even be resolvable on some ROMs
            false
        }
    }
}
