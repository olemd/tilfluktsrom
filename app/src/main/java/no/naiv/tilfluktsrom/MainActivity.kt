package no.naiv.tilfluktsrom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import no.naiv.tilfluktsrom.data.MapCacheManager
import no.naiv.tilfluktsrom.data.Shelter
import no.naiv.tilfluktsrom.data.ShelterRepository
import no.naiv.tilfluktsrom.databinding.ActivityMainBinding
import no.naiv.tilfluktsrom.location.LocationProvider
import no.naiv.tilfluktsrom.location.ShelterFinder
import no.naiv.tilfluktsrom.location.ShelterWithDistance
import no.naiv.tilfluktsrom.ui.ShelterListAdapter
import no.naiv.tilfluktsrom.util.DistanceUtils
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MainActivity : AppCompatActivity(), SensorEventListener {

    companion object {
        private const val TAG = "MainActivity"
        private const val DEFAULT_ZOOM = 14.0
        private const val NEAREST_COUNT = 3
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: ShelterRepository
    private lateinit var locationProvider: LocationProvider
    private lateinit var mapCacheManager: MapCacheManager
    private lateinit var sensorManager: SensorManager
    private lateinit var shelterAdapter: ShelterListAdapter

    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var currentLocation: Location? = null
    private var allShelters: List<Shelter> = emptyList()
    private var nearestShelters: List<ShelterWithDistance> = emptyList()
    private var selectedShelterIndex = 0
    private var deviceHeading = 0f
    private var isCompassMode = false
    private var locationJob: Job? = null
    private var shelterMarkers: MutableList<Marker> = mutableListOf()

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fineGranted || coarseGranted) {
            startLocationUpdates()
        } else {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ShelterRepository(this)
        locationProvider = LocationProvider(this)
        mapCacheManager = MapCacheManager(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        setupMap()
        setupShelterList()
        setupButtons()
        loadData()
    }

    private fun setupMap() {
        binding.mapView.apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            controller.setZoom(DEFAULT_ZOOM)
            // Default center: roughly central Norway
            controller.setCenter(GeoPoint(59.9, 10.7))

            // Add user location overlay
            myLocationOverlay = MyLocationNewOverlay(
                GpsMyLocationProvider(this@MainActivity), this
            ).apply {
                enableMyLocation()
            }
            overlays.add(myLocationOverlay)
        }
    }

    private fun setupShelterList() {
        shelterAdapter = ShelterListAdapter { selected ->
            val idx = nearestShelters.indexOf(selected)
            if (idx >= 0) {
                selectedShelterIndex = idx
                updateSelectedShelter()
            }
        }

        binding.shelterList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = shelterAdapter
        }
    }

    private fun setupButtons() {
        binding.toggleViewFab.setOnClickListener {
            isCompassMode = !isCompassMode
            if (isCompassMode) {
                binding.mapView.visibility = View.GONE
                binding.compassContainer.visibility = View.VISIBLE
                binding.toggleViewFab.setImageResource(R.drawable.ic_map)
            } else {
                binding.mapView.visibility = View.VISIBLE
                binding.compassContainer.visibility = View.GONE
                binding.toggleViewFab.setImageResource(R.drawable.ic_compass)
            }
        }

        binding.refreshButton.setOnClickListener {
            forceRefresh()
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            val hasData = repository.hasCachedData()

            if (!hasData) {
                if (!isNetworkAvailable()) {
                    binding.statusText.text = getString(R.string.error_no_data_offline)
                    return@launch
                }
                showLoading(getString(R.string.loading_shelters))
                val success = repository.refreshData()
                hideLoading()

                if (!success) {
                    binding.statusText.text = getString(R.string.error_download_failed)
                    return@launch
                }
            }

            // Observe shelter data reactively
            launch {
                repository.getAllShelters().collectLatest { shelters ->
                    allShelters = shelters
                    binding.statusText.text = getString(R.string.status_shelters_loaded, shelters.size)
                    updateShelterMarkers()
                    currentLocation?.let { updateNearestShelters(it) }
                }
            }

            // Request location and start updates
            requestLocationPermission()

            // Check for stale data in background
            if (hasData && repository.isDataStale() && isNetworkAvailable()) {
                launch {
                    val success = repository.refreshData()
                    if (success) {
                        Toast.makeText(this@MainActivity, R.string.update_success, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun requestLocationPermission() {
        if (locationProvider.hasLocationPermission()) {
            startLocationUpdates()
        } else {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun startLocationUpdates() {
        locationJob?.cancel()
        locationJob = lifecycleScope.launch {
            locationProvider.locationUpdates().collectLatest { location ->
                currentLocation = location
                updateNearestShelters(location)

                // Center map on first location fix
                if (nearestShelters.isEmpty()) {
                    binding.mapView.controller.animateTo(
                        GeoPoint(location.latitude, location.longitude)
                    )
                }

                // Cache map tiles on first launch
                if (!mapCacheManager.hasCacheForLocation(location.latitude, location.longitude)) {
                    if (isNetworkAvailable()) {
                        cacheMapTiles(location.latitude, location.longitude)
                    }
                }
            }
        }
    }

    private fun updateNearestShelters(location: Location) {
        if (allShelters.isEmpty()) return

        nearestShelters = ShelterFinder.findNearest(
            allShelters, location.latitude, location.longitude, NEAREST_COUNT
        )

        shelterAdapter.submitList(nearestShelters)
        selectedShelterIndex = 0
        shelterAdapter.selectPosition(0)
        updateSelectedShelter()
    }

    private fun updateSelectedShelter() {
        if (nearestShelters.isEmpty()) return

        val selected = nearestShelters[selectedShelterIndex]
        val distanceText = DistanceUtils.formatDistance(selected.distanceMeters)

        // Update bottom sheet
        binding.selectedShelterAddress.text = selected.shelter.adresse
        binding.selectedShelterDetails.text = getString(
            R.string.shelter_room_nr, selected.shelter.romnr
        ) + " - " + getString(
            R.string.shelter_capacity, selected.shelter.plasser
        ) + " - " + distanceText

        // Update mini arrow in bottom sheet
        val bearing = selected.bearingDegrees.toFloat()
        binding.miniArrow.setDirection(bearing - deviceHeading)

        // Update compass view
        binding.compassDistanceText.text = distanceText
        binding.compassAddressText.text = selected.shelter.adresse
        binding.directionArrow.setDirection(bearing - deviceHeading)

        // Center map on shelter if in map mode
        if (!isCompassMode) {
            highlightShelterOnMap(selected)
        }
    }

    private fun updateShelterMarkers() {
        // Remove old markers
        shelterMarkers.forEach { binding.mapView.overlays.remove(it) }
        shelterMarkers.clear()

        // Add markers for all shelters
        allShelters.forEach { shelter ->
            val marker = Marker(binding.mapView).apply {
                position = GeoPoint(shelter.latitude, shelter.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = shelter.adresse
                snippet = getString(R.string.shelter_capacity, shelter.plasser) +
                    " - " + getString(R.string.shelter_room_nr, shelter.romnr)
                icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_shelter)
            }
            shelterMarkers.add(marker)
            binding.mapView.overlays.add(marker)
        }

        binding.mapView.invalidate()
    }

    private fun highlightShelterOnMap(selected: ShelterWithDistance) {
        val shelterPoint = GeoPoint(selected.shelter.latitude, selected.shelter.longitude)

        // If we have location, show both user and shelter in view
        currentLocation?.let { loc ->
            val userPoint = GeoPoint(loc.latitude, loc.longitude)
            val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPoints(
                listOf(userPoint, shelterPoint)
            )
            // Add padding so markers aren't at the edge
            binding.mapView.zoomToBoundingBox(boundingBox.increaseByScale(1.5f), true)
        } ?: run {
            binding.mapView.controller.animateTo(shelterPoint)
        }
    }

    private fun cacheMapTiles(latitude: Double, longitude: Double) {
        lifecycleScope.launch {
            binding.statusText.text = getString(R.string.status_caching_map)
            mapCacheManager.cacheMapArea(
                binding.mapView, latitude, longitude
            ) { progress ->
                binding.statusText.text = getString(R.string.status_caching_map) +
                    " (${(progress * 100).toInt()}%)"
            }
            binding.statusText.text = getString(R.string.status_shelters_loaded, allShelters.size)
        }
    }

    private fun forceRefresh() {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, R.string.error_download_failed, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            binding.statusText.text = getString(R.string.status_updating)
            val success = repository.refreshData()
            if (success) {
                Toast.makeText(this@MainActivity, R.string.update_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, R.string.update_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLoading(message: String) {
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.loadingText.text = message
    }

    private fun hideLoading() {
        binding.loadingOverlay.visibility = View.GONE
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // --- Sensor handling for compass ---

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()

        // Register for rotation vector (best compass source)
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            // Fallback to accelerometer + magnetometer
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
            sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        sensorManager.unregisterListener(this)
    }

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                deviceHeading = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (deviceHeading < 0) deviceHeading += 360f
                updateDirectionArrows()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravity, 0, 3)
                updateFromAccelMag()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                updateFromAccelMag()
            }
        }
    }

    private fun updateFromAccelMag() {
        val r = FloatArray(9)
        if (SensorManager.getRotationMatrix(r, null, gravity, geomagnetic)) {
            SensorManager.getOrientation(r, orientation)
            deviceHeading = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (deviceHeading < 0) deviceHeading += 360f
            updateDirectionArrows()
        }
    }

    private fun updateDirectionArrows() {
        if (nearestShelters.isEmpty()) return
        val selected = nearestShelters[selectedShelterIndex]
        val bearing = selected.bearingDegrees.toFloat()
        val arrowAngle = bearing - deviceHeading

        binding.directionArrow.setDirection(arrowAngle)
        binding.miniArrow.setDirection(arrowAngle)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
