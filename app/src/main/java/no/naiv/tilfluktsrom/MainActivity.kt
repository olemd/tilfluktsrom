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
import kotlinx.coroutines.delay
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
    private var deviceHeading = 0f
    private var isCompassMode = false
    private var locationJob: Job? = null
    private var cachingJob: Job? = null
    // Map from shelter lokalId to its map marker, for icon swapping on selection
    private var shelterMarkerMap: MutableMap<String, Marker> = mutableMapOf()
    private var highlightedMarkerId: String? = null

    // The currently selected shelter — can be any shelter, not just one from nearestShelters
    private var selectedShelter: ShelterWithDistance? = null
    // When true, location updates won't auto-select the nearest shelter
    private var userSelectedShelter = false
    // When true, location updates won't auto-zoom the map
    private var userHasInteractedWithMap = false

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

            // Detect user touch interaction (pan/zoom) to suppress auto-zoom
            setOnTouchListener { v, _ ->
                if (!userHasInteractedWithMap) {
                    userHasInteractedWithMap = true
                    binding.resetNavigationFab.visibility = View.VISIBLE
                }
                v.performClick()
                false // Don't consume the event
            }

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
        shelterAdapter = ShelterListAdapter { swd ->
            userSelectedShelter = true
            userHasInteractedWithMap = false
            selectShelter(swd)
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

        // Reset to navigation: re-fit map to show user + selected shelter
        binding.resetNavigationFab.setOnClickListener {
            userHasInteractedWithMap = false
            binding.resetNavigationFab.visibility = View.GONE
            selectedShelter?.let { highlightShelterOnMap(it) }
        }

        binding.refreshButton.setOnClickListener {
            forceRefresh()
        }

        binding.cacheRetryButton.setOnClickListener {
            currentLocation?.let { loc ->
                if (isNetworkAvailable()) {
                    startCaching(loc.latitude, loc.longitude)
                } else {
                    Toast.makeText(this, R.string.error_download_failed, Toast.LENGTH_SHORT).show()
                }
            }
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

        // Highlight which nearest-list item matches the current selection
        val selectedIdx = if (selectedShelter != null) {
            nearestShelters.indexOfFirst { it.shelter.lokalId == selectedShelter!!.shelter.lokalId }
        } else -1

        shelterAdapter.submitList(nearestShelters)
        shelterAdapter.selectPosition(selectedIdx)

        if (userSelectedShelter && selectedShelter != null) {
            // Recalculate distance/bearing for the user's picked shelter
            refreshSelectedShelterDistance(location)
        } else {
            // Auto-select nearest
            if (nearestShelters.isNotEmpty()) {
                selectShelter(nearestShelters[0])
            }
        }

        updateSelectedShelterUI()
    }

    /**
     * Select a specific shelter (from list tap, marker tap, or auto-select).
     * Recalculates distance/bearing from current location.
     */
    private fun selectShelter(swd: ShelterWithDistance) {
        selectedShelter = swd
        currentLocation?.let { refreshSelectedShelterDistance(it) }

        // Update list highlight
        val idx = nearestShelters.indexOfFirst { it.shelter.lokalId == swd.shelter.lokalId }
        shelterAdapter.selectPosition(idx)

        updateSelectedShelterUI()
    }

    /**
     * Select a shelter by its data object (e.g. from a marker tap).
     * Computes a fresh ShelterWithDistance from the current location.
     */
    private fun selectShelterByData(shelter: Shelter) {
        val loc = currentLocation
        val swd = if (loc != null) {
            ShelterWithDistance(
                shelter = shelter,
                distanceMeters = DistanceUtils.distanceMeters(
                    loc.latitude, loc.longitude, shelter.latitude, shelter.longitude
                ),
                bearingDegrees = DistanceUtils.bearingDegrees(
                    loc.latitude, loc.longitude, shelter.latitude, shelter.longitude
                )
            )
        } else {
            ShelterWithDistance(shelter = shelter, distanceMeters = 0.0, bearingDegrees = 0.0)
        }

        userSelectedShelter = true
        userHasInteractedWithMap = false
        binding.resetNavigationFab.visibility = View.GONE
        selectShelter(swd)
    }

    /** Recalculate distance/bearing for the currently selected shelter. */
    private fun refreshSelectedShelterDistance(location: Location) {
        val current = selectedShelter ?: return
        selectedShelter = ShelterWithDistance(
            shelter = current.shelter,
            distanceMeters = DistanceUtils.distanceMeters(
                location.latitude, location.longitude,
                current.shelter.latitude, current.shelter.longitude
            ),
            bearingDegrees = DistanceUtils.bearingDegrees(
                location.latitude, location.longitude,
                current.shelter.latitude, current.shelter.longitude
            )
        )
    }

    /** Update all UI elements for the currently selected shelter. */
    private fun updateSelectedShelterUI() {
        val selected = selectedShelter ?: return
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

        // Emphasize the selected marker on the map
        highlightSelectedMarker(selected.shelter.lokalId)

        // Only auto-zoom the map if the user hasn't manually panned/zoomed
        if (!isCompassMode && !userHasInteractedWithMap) {
            highlightShelterOnMap(selected)
        }
    }

    /** Swap marker icons so the selected shelter stands out. */
    private fun highlightSelectedMarker(lokalId: String) {
        if (lokalId == highlightedMarkerId) return

        val normalIcon = ContextCompat.getDrawable(this, R.drawable.ic_shelter)
        val selectedIcon = ContextCompat.getDrawable(this, R.drawable.ic_shelter_selected)

        // Reset previous
        highlightedMarkerId?.let { prevId ->
            shelterMarkerMap[prevId]?.icon = normalIcon
        }

        // Highlight new
        shelterMarkerMap[lokalId]?.icon = selectedIcon
        highlightedMarkerId = lokalId

        binding.mapView.invalidate()
    }

    private fun updateShelterMarkers() {
        // Remove old markers
        shelterMarkerMap.values.forEach { binding.mapView.overlays.remove(it) }
        shelterMarkerMap.clear()
        highlightedMarkerId = null

        val normalIcon = ContextCompat.getDrawable(this, R.drawable.ic_shelter)
        val selectedIcon = ContextCompat.getDrawable(this, R.drawable.ic_shelter_selected)
        val currentSelectedId = selectedShelter?.shelter?.lokalId

        // Add markers for all shelters — tapping any marker selects it
        allShelters.forEach { shelter ->
            val isSelected = shelter.lokalId == currentSelectedId
            val marker = Marker(binding.mapView).apply {
                position = GeoPoint(shelter.latitude, shelter.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = shelter.adresse
                snippet = getString(R.string.shelter_capacity, shelter.plasser) +
                    " - " + getString(R.string.shelter_room_nr, shelter.romnr)
                icon = if (isSelected) selectedIcon else normalIcon
                setOnMarkerClickListener { _, _ ->
                    selectShelterByData(shelter)
                    true
                }
            }
            if (isSelected) highlightedMarkerId = shelter.lokalId
            shelterMarkerMap[shelter.lokalId] = marker
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
        // Show prompt with OK / Skip choice
        showLoading(getString(R.string.loading_map_explanation))
        binding.loadingProgress.visibility = View.GONE
        binding.loadingButtonRow.visibility = View.VISIBLE

        binding.loadingOkButton.setOnClickListener {
            startCaching(latitude, longitude)
        }

        binding.loadingSkipButton.setOnClickListener {
            hideLoading()
            showNoCacheBanner()
        }
    }

    private fun startCaching(latitude: Double, longitude: Double) {
        binding.noCacheBanner.visibility = View.GONE
        showLoading(getString(R.string.loading_map))
        binding.loadingButtonRow.visibility = View.GONE

        cachingJob = lifecycleScope.launch {
            mapCacheManager.cacheMapArea(
                binding.mapView, latitude, longitude
            ) { progress ->
                binding.loadingText.text = getString(R.string.loading_map) +
                    " (${(progress * 100).toInt()}%)"
            }
            hideLoading()
            binding.statusText.text = getString(R.string.status_shelters_loaded, allShelters.size)
        }
    }

    private fun showNoCacheBanner() {
        binding.noCacheBanner.visibility = View.VISIBLE
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
        binding.loadingProgress.visibility = View.VISIBLE
        binding.loadingButtonRow.visibility = View.GONE
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
        val selected = selectedShelter ?: return
        val bearing = selected.bearingDegrees.toFloat()
        val arrowAngle = bearing - deviceHeading

        binding.directionArrow.setDirection(arrowAngle)
        binding.miniArrow.setDirection(arrowAngle)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
