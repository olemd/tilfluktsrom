package no.naiv.tilfluktsrom

import android.Manifest
import android.content.Context
import android.content.Intent
import android.view.HapticFeedbackConstants
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import java.util.concurrent.TimeUnit
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
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
import no.naiv.tilfluktsrom.ui.CivilDefenseInfoDialog
import no.naiv.tilfluktsrom.ui.ShelterListAdapter
import no.naiv.tilfluktsrom.util.DistanceUtils
import no.naiv.tilfluktsrom.widget.ShelterWidgetProvider
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
    private var sensorManager: SensorManager? = null
    private lateinit var shelterAdapter: ShelterListAdapter

    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var currentLocation: Location? = null
    private var allShelters: List<Shelter> = emptyList()
    private var nearestShelters: List<ShelterWithDistance> = emptyList()
    private var deviceHeading = 0f
    private var isCompassMode = false
    private var cachingJob: Job? = null
    // Map from shelter lokalId to its map marker, for icon swapping on selection
    private var shelterMarkerMap: MutableMap<String, Marker> = mutableMapOf()
    private var highlightedMarkerId: String? = null

    // Whether a compass sensor is available on this device
    private var hasCompassSensor = false

    // Deep link: shelter ID to select once data is loaded
    private var pendingDeepLinkShelterId: String? = null

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
            // Check if user permanently denied (don't show rationale = permanently denied)
            val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            )
            if (!shouldShowRationale) {
                // Permission permanently denied — guide user to settings
                AlertDialog.Builder(this)
                    .setTitle(R.string.permission_location_title)
                    .setMessage(R.string.permission_denied)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ShelterRepository(this)
        locationProvider = LocationProvider(this)
        mapCacheManager = MapCacheManager(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager

        setupMap()
        setupShelterList()
        setupButtons()
        loadData()
        handleDeepLinkIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    /**
     * Handle tilfluktsrom://shelter/{lokalId} deep link.
     * If shelters are already loaded, select immediately; otherwise store as pending.
     */
    private fun handleDeepLinkIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "tilfluktsrom" || uri.host != "shelter") return

        val lokalId = uri.lastPathSegment ?: return
        // Clear intent data so config changes don't re-trigger
        intent.data = null

        val shelter = allShelters.find { it.lokalId == lokalId }
        if (shelter != null) {
            selectShelterByData(shelter)
        } else {
            pendingDeepLinkShelterId = lokalId
        }
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
            )
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
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@MainActivity)
            adapter = shelterAdapter
        }
    }

    private fun setupButtons() {
        binding.toggleViewFab.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            userHasInteractedWithMap = false
            binding.resetNavigationFab.visibility = View.GONE
            selectedShelter?.let { highlightShelterOnMap(it) }
        }

        binding.infoButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            CivilDefenseInfoDialog().show(supportFragmentManager, CivilDefenseInfoDialog.TAG)
        }

        binding.refreshButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            forceRefresh()
        }

        binding.shareButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            shareShelter()
        }

        binding.cacheRetryButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val loc = currentLocation
            if (loc == null) {
                Toast.makeText(this, R.string.status_no_location, Toast.LENGTH_SHORT).show()
            } else if (!isNetworkAvailable()) {
                Toast.makeText(this, R.string.error_download_failed, Toast.LENGTH_SHORT).show()
            } else {
                startCaching(loc.latitude, loc.longitude)
            }
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            try {
                var hasData = repository.hasCachedData()

                // Seed from bundled asset if DB is empty (works offline)
                if (!hasData) {
                    repository.seedFromAsset()
                    hasData = repository.hasCachedData()
                }

                // If seeding also failed (shouldn't happen), try network
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
                    try {
                        repository.getAllShelters().collectLatest { shelters ->
                            allShelters = shelters
                            binding.statusText.text = getString(R.string.status_shelters_loaded, shelters.size)
                            updateFreshnessIndicator()
                            updateShelterMarkers()

                            // Process pending deep links now that shelter data is available
                            pendingDeepLinkShelterId?.let { id ->
                                pendingDeepLinkShelterId = null
                                val shelter = shelters.find { it.lokalId == id }
                                if (shelter != null) {
                                    selectShelterByData(shelter)
                                } else {
                                    Toast.makeText(this@MainActivity, R.string.error_shelter_not_found, Toast.LENGTH_SHORT).show()
                                }
                            }
                            currentLocation?.let { updateNearestShelters(it) }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Error observing shelter data", e)
                        binding.statusText.text = getString(R.string.error_download_failed)
                    }
                }

                // Request location and start updates
                requestLocationPermission()

                // Always try to refresh from network (seeded data has timestamp 0 = stale)
                if (repository.isDataStale() && isNetworkAvailable()) {
                    launch {
                        val success = repository.refreshData()
                        if (success) {
                            Toast.makeText(this@MainActivity, R.string.update_success, Toast.LENGTH_SHORT).show()
                        } else if (!hasData) {
                            // Only warn if we had no data at all before
                            Toast.makeText(this@MainActivity, R.string.update_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize shelter data", e)
                hideLoading()
                binding.statusText.text = getString(R.string.error_download_failed)
            }
        }
    }

    private fun requestLocationPermission() {
        if (locationProvider.hasLocationPermission()) {
            startLocationUpdates()
            return
        }

        // Show rationale dialog if needed, then request
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            )
        ) {
            AlertDialog.Builder(this)
                .setTitle(R.string.permission_location_title)
                .setMessage(R.string.permission_location_message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    launchPermissionRequest()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show()
                }
                .show()
        } else {
            launchPermissionRequest()
        }
    }

    private fun launchPermissionRequest() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun startLocationUpdates() {
        // Use repeatOnLifecycle(STARTED) so GPS stops when Activity is paused
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    locationProvider.locationUpdates().collectLatest { location ->
                        currentLocation = location
                        saveLastLocation(location)
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
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Location updates failed", e)
                    binding.statusText.text = getString(R.string.status_no_location)
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
        ShelterWidgetProvider.requestUpdate(this)
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
            // No GPS yet — use NaN to signal "unknown distance"
            ShelterWithDistance(
                shelter = shelter,
                distanceMeters = Double.NaN,
                bearingDegrees = Double.NaN
            )
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
        val distanceText = if (selected.distanceMeters.isNaN()) {
            getString(R.string.status_no_location)
        } else {
            DistanceUtils.formatDistance(selected.distanceMeters)
        }

        // Update bottom sheet
        binding.selectedShelterAddress.text = selected.shelter.adresse
        binding.selectedShelterDetails.text = getString(
            R.string.shelter_room_nr, selected.shelter.romnr
        ) + " - " + getString(
            R.string.shelter_capacity, selected.shelter.plasser
        ) + " - " + distanceText

        // Update direction arrows with accessibility descriptions
        val bearing = selected.bearingDegrees.toFloat()
        val arrowAngle = bearing - deviceHeading
        binding.miniArrow.setDirection(arrowAngle)
        binding.miniArrow.contentDescription = getString(
            R.string.direction_arrow_description, distanceText
        )

        // Update compass view
        binding.compassDistanceText.text = distanceText
        binding.compassAddressText.text = selected.shelter.adresse
        binding.directionArrow.setDirection(arrowAngle)
        binding.directionArrow.contentDescription = getString(
            R.string.direction_arrow_description, distanceText
        )

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
            val success = mapCacheManager.cacheMapArea(
                binding.mapView, latitude, longitude
            ) { progress ->
                binding.loadingText.text = getString(R.string.loading_map) +
                    " (${(progress * 100).toInt()}%)"
            }
            hideLoading()
            if (success) {
                binding.statusText.text = getString(R.string.status_shelters_loaded, allShelters.size)
            } else {
                showNoCacheBanner()
            }
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
                updateFreshnessIndicator()
                Toast.makeText(this@MainActivity, R.string.update_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, R.string.update_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Share the currently selected shelter via ACTION_SEND.
     * Includes address, capacity, geo: URI (for non-app recipients),
     * and a tilfluktsrom:// deep link (for app users).
     */
    private fun shareShelter() {
        val selected = selectedShelter
        if (selected == null) {
            Toast.makeText(this, R.string.share_no_shelter, Toast.LENGTH_SHORT).show()
            return
        }

        val shelter = selected.shelter
        val body = getString(
            R.string.share_body,
            shelter.adresse,
            shelter.plasser,
            shelter.latitude,
            shelter.longitude
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)))
    }

    /** Update the freshness indicator below the status bar with color-coded age. */
    private fun updateFreshnessIndicator() {
        val lastUpdate = repository.getLastUpdateMs()
        if (lastUpdate == 0L) {
            binding.dataFreshnessText.visibility = View.GONE
            return
        }
        val daysSince = TimeUnit.MILLISECONDS.toDays(
            System.currentTimeMillis() - lastUpdate
        ).toInt()

        val (textRes, colorRes) = when {
            daysSince == 0 -> R.string.freshness_fresh to R.color.text_secondary
            daysSince <= 7 -> R.string.freshness_week to R.color.shelter_accent
            else           -> R.string.freshness_old to R.color.shelter_primary
        }

        binding.dataFreshnessText.text = getString(textRes, daysSince)
        binding.dataFreshnessText.setTextColor(ContextCompat.getColor(this, colorRes))
        binding.dataFreshnessText.visibility = View.VISIBLE
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

    /** Persist last GPS fix so the widget can use it even when the app isn't running. */
    private fun saveLastLocation(location: Location) {
        getSharedPreferences("widget_prefs", Context.MODE_PRIVATE).edit()
            .putFloat("last_lat", location.latitude.toFloat())
            .putFloat("last_lon", location.longitude.toFloat())
            .putLong("last_time", System.currentTimeMillis())
            .apply()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // --- Sensor handling for compass ---

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        myLocationOverlay?.enableMyLocation()

        val sm = sensorManager ?: return

        // Try rotation vector first (best compass source)
        val rotationSensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationSensor != null) {
            sm.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
            hasCompassSensor = true
        } else {
            // Fallback to accelerometer + magnetometer
            val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            if (accel != null && mag != null) {
                sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
                sm.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI)
                hasCompassSensor = true
                Log.w(TAG, "Using accelerometer+magnetometer fallback for compass")
            } else {
                hasCompassSensor = false
                Log.e(TAG, "No compass sensors available on this device")
                binding.compassAddressText.text = getString(R.string.error_no_compass)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        myLocationOverlay?.disableMyLocation()
        sensorManager?.unregisterListener(this)
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
                lowPassFilter(event.values, gravity)
                updateFromAccelMag()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                lowPassFilter(event.values, geomagnetic)
                updateFromAccelMag()
            }
        }
    }

    /** Low-pass filter to smooth noisy accelerometer/magnetometer data. */
    private fun lowPassFilter(input: FloatArray, output: FloatArray, alpha: Float = 0.25f) {
        for (i in input.indices) {
            output[i] = output[i] + alpha * (input[i] - output[i])
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
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD ||
            sensor?.type == Sensor.TYPE_ROTATION_VECTOR
        ) {
            when (accuracy) {
                SensorManager.SENSOR_STATUS_UNRELIABLE,
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> {
                    Log.w(TAG, "Compass accuracy degraded: $accuracy")
                    binding.compassAddressText.let { tv ->
                        val current = selectedShelter?.shelter?.adresse ?: ""
                        tv.text = getString(R.string.compass_accuracy_warning, current)
                    }
                }
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> {
                    // Restore normal display when accuracy improves
                    selectedShelter?.let { selected ->
                        binding.compassAddressText.text = selected.shelter.adresse
                    }
                }
            }
        }
    }
}
