package com.kylecorry.trail_sense.navigation.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isInvisible
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.kylecorry.andromeda.alerts.Alerts
import com.kylecorry.andromeda.core.coroutines.onIO
import com.kylecorry.andromeda.core.sensors.Quality
import com.kylecorry.andromeda.core.system.GeoUri
import com.kylecorry.andromeda.core.system.Resources
import com.kylecorry.andromeda.core.system.Screen
import com.kylecorry.andromeda.core.tryOrNothing
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.fragments.inBackground
import com.kylecorry.andromeda.fragments.interval
import com.kylecorry.andromeda.fragments.observe
import com.kylecorry.andromeda.fragments.show
import com.kylecorry.andromeda.pickers.Pickers
import com.kylecorry.andromeda.sense.Sensors
import com.kylecorry.andromeda.sense.orientation.DeviceOrientation
import com.kylecorry.luna.coroutines.CoroutineQueueRunner
import com.kylecorry.sol.science.geology.CoordinateBounds
import com.kylecorry.sol.science.geology.Geofence
import com.kylecorry.sol.units.Bearing
import com.kylecorry.sol.units.Coordinate
import com.kylecorry.sol.units.Distance
import com.kylecorry.sol.units.Reading
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.calibration.ui.CompassCalibrationView
import com.kylecorry.trail_sense.databinding.ActivityNavigatorBinding
import com.kylecorry.trail_sense.diagnostics.status.GpsStatusBadgeProvider
import com.kylecorry.trail_sense.diagnostics.status.SensorStatusBadgeProvider
import com.kylecorry.trail_sense.diagnostics.status.StatusBadge
import com.kylecorry.trail_sense.navigation.beacons.domain.Beacon
import com.kylecorry.trail_sense.navigation.beacons.infrastructure.persistence.BeaconRepo
import com.kylecorry.trail_sense.navigation.domain.CompassStyle
import com.kylecorry.trail_sense.navigation.domain.CompassStyleChooser
import com.kylecorry.trail_sense.navigation.domain.NavigationService
import com.kylecorry.trail_sense.navigation.infrastructure.share.LocationCopy
import com.kylecorry.trail_sense.navigation.infrastructure.share.LocationGeoSender
import com.kylecorry.trail_sense.navigation.infrastructure.share.LocationSharesheet
import com.kylecorry.trail_sense.navigation.paths.domain.Path
import com.kylecorry.trail_sense.navigation.paths.infrastructure.PathLoader
import com.kylecorry.trail_sense.navigation.paths.infrastructure.persistence.PathService
import com.kylecorry.trail_sense.navigation.paths.ui.asMappable
import com.kylecorry.trail_sense.navigation.ui.data.UpdateAstronomyLayerCommand
import com.kylecorry.trail_sense.navigation.ui.data.UpdateTideLayerCommand
import com.kylecorry.trail_sense.navigation.ui.layers.*
import com.kylecorry.trail_sense.navigation.ui.layers.compass.BeaconCompassLayer
import com.kylecorry.trail_sense.navigation.ui.layers.compass.ICompassView
import com.kylecorry.trail_sense.navigation.ui.layers.compass.MarkerCompassLayer
import com.kylecorry.trail_sense.navigation.ui.layers.compass.NavigationCompassLayer
import com.kylecorry.trail_sense.quickactions.NavigationQuickActionBinder
import com.kylecorry.trail_sense.shared.*
import com.kylecorry.trail_sense.shared.colors.AppColor
import com.kylecorry.trail_sense.shared.declination.DeclinationFactory
import com.kylecorry.trail_sense.shared.declination.DeclinationUtils
import com.kylecorry.trail_sense.shared.extensions.onDefault
import com.kylecorry.trail_sense.shared.extensions.onMain
import com.kylecorry.trail_sense.shared.permissions.alertNoCameraPermission
import com.kylecorry.trail_sense.shared.permissions.requestCamera
import com.kylecorry.trail_sense.shared.preferences.PreferencesSubsystem
import com.kylecorry.trail_sense.shared.sensors.CustomGPS
import com.kylecorry.trail_sense.shared.sensors.SensorService
import com.kylecorry.trail_sense.shared.sensors.overrides.CachedGPS
import com.kylecorry.trail_sense.shared.sensors.overrides.OverrideGPS
import com.kylecorry.trail_sense.shared.views.UserError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.util.*


class NavigatorFragment : BoundFragment<ActivityNavigatorBinding>() {

    private var shownAccuracyToast = false
    private var showSightingCompass = false
    private val compass by lazy { sensorService.getCompass() }
    private val gps by lazy { sensorService.getGPS(frequency = Duration.ofMillis(200)) }
    private var sightingCompass: SightingCompassView? = null
    private val orientation by lazy { sensorService.getDeviceOrientationSensor() }
    private val altimeter by lazy { sensorService.getAltimeter(gps = gps) }
    private val speedometer by lazy { sensorService.getSpeedometer(gps = gps) }
    private val declinationProvider by lazy {
        DeclinationFactory().getDeclinationStrategy(
            userPrefs,
            gps
        )
    }
    private var declination = 0f

    private val userPrefs by lazy { UserPreferences(requireContext()) }

    private lateinit var navController: NavController

    private val beaconRepo by lazy { BeaconRepo.getInstance(requireContext()) }
    private val pathService by lazy { PathService.getInstance(requireContext()) }

    private val sensorService by lazy { SensorService(requireContext()) }
    private val cache by lazy { PreferencesSubsystem.getInstance(requireContext()).preferences }

    private val navigationService = NavigationService()
    private val formatService by lazy { FormatService.getInstance(requireContext()) }

    private var beacons: Collection<Beacon> = listOf()
    private var paths: List<Path> = emptyList()
    private var nearbyBeacons: List<Beacon> = listOf()

    private var destination: Beacon? = null
    private var destinationBearing: Float? = null

    // Status badges
    private val gpsStatusBadgeProvider by lazy { GpsStatusBadgeProvider(gps, requireContext()) }
    private val compassStatusBadgeProvider by lazy {
        SensorStatusBadgeProvider(
            compass,
            requireContext(),
            R.drawable.ic_compass_icon
        )
    }
    private var gpsStatusBadge: StatusBadge? = null
    private var compassStatusBadge: StatusBadge? = null

    // Data commands
    private val updateTideLayerCommand by lazy {
        UpdateTideLayerCommand(
            requireContext(),
            tideLayer
        )
    }
    private val updateAstronomyLayerCommand by lazy {
        UpdateAstronomyLayerCommand(
            astronomyCompassLayer,
            userPrefs,
            gps
        ) { declination }
    }


    private var astronomyDataLoaded = false

    private var gpsErrorShown = false
    private var gpsTimeoutShown = false

    private var lastOrientation: DeviceOrientation.Orientation? = null

    private val pathLoader by lazy { PathLoader(pathService) }

    private val loadPathRunner = CoroutineQueueRunner()
    private val loadBeaconsRunner = CoroutineQueueRunner()

    private val pathLayer = PathLayer()
    private val beaconLayer = BeaconLayer()
    private val myLocationLayer = MyLocationLayer()
    private val myAccuracyLayer = MyAccuracyLayer()
    private val tideLayer = TideLayer()

    // Compass layers
    private val beaconCompassLayer = BeaconCompassLayer()
    private val astronomyCompassLayer = MarkerCompassLayer()
    private val navigationCompassLayer = NavigationCompassLayer()


    // Cached preferences
    private val baseDistanceUnits by lazy { userPrefs.baseDistanceUnits }
    private val isNearbyEnabled by lazy { userPrefs.navigation.showMultipleBeacons }
    private val nearbyCount by lazy { userPrefs.navigation.numberOfVisibleBeacons }
    private val nearbyDistance
        get() = userPrefs.navigation.maxBeaconDistance
    private val useRadarCompass by lazy { userPrefs.navigation.useRadarCompass }
    private val lockScreenPresence by lazy { userPrefs.navigation.lockScreenPresence }
    private val styleChooser by lazy { CompassStyleChooser(userPrefs.navigation) }
    private val useTrueNorth by lazy { userPrefs.compass.useTrueNorth }

    override fun onDestroyView() {
        super.onDestroyView()
        sightingCompass = null
        activity?.let {
            tryOrNothing {
                Screen.setShowWhenLocked(it, false)
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val beaconId = arguments?.getLong("destination") ?: 0L

        if (beaconId != 0L) {
            showCalibrationDialog()
            inBackground {
                withContext(Dispatchers.IO) {
                    destination = beaconRepo.getBeacon(beaconId)?.toBeacon()
                    cache.putLong(LAST_BEACON_ID, beaconId)
                }
                withContext(Dispatchers.Main) {
                    handleShowWhenLocked()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sightingCompass = SightingCompassView(
            binding.viewCamera,
            binding.viewCameraLine
        )

        // Register timers
        interval(Duration.ofMinutes(1)) {
            updateAstronomyData()
        }

        interval(100) {
            updateCompassLayers()
        }

        interval(Duration.ofSeconds(1)) {
            updateSensorStatus()
        }


        // Initialize layers
        beaconLayer.setOutlineColor(Resources.color(requireContext(), R.color.colorSecondary))
        myAccuracyLayer.setColors(AppColor.Orange.color, Color.TRANSPARENT, 25)
        binding.radarCompass.setLayers(
            listOf(
                pathLayer,
                myAccuracyLayer,
                myLocationLayer,
                tideLayer,
                beaconLayer
            )
        )

        binding.roundCompass.setCompassLayers(
            listOf(
                astronomyCompassLayer,
                beaconCompassLayer,
                navigationCompassLayer
            )
        )

        binding.linearCompass.setCompassLayers(
            listOf(
                astronomyCompassLayer,
                beaconCompassLayer,
                navigationCompassLayer
            )
        )

        binding.radarCompass.setCompassLayers(
            listOf(
                astronomyCompassLayer,
                navigationCompassLayer
            )
        )


        binding.speed.setShowDescription(false)
        binding.altitude.setShowDescription(false)

        if (!Sensors.hasCompass(requireContext())) {
            requireMainActivity().errorBanner.report(
                UserError(
                    ErrorBannerReason.NoCompass,
                    getString(R.string.no_compass_message),
                    R.drawable.ic_compass_icon
                )
            )
        }

        NavigationQuickActionBinder(
            this,
            binding,
            userPrefs.navigation
        ).bind()

        observe(beaconRepo.getBeacons()) {
            inBackground {
                onDefault {
                    beacons = it.map { it.toBeacon() }
                }
                updateNearbyBeacons()
            }
        }

        observe(pathService.getLivePaths()) {
            inBackground {
                onIO {
                    paths = it.filter { path -> path.style.visible }
                    updateCompassPaths(true)
                }
            }

        }

        navController = findNavController()

        observe(compass) { }
        observe(orientation) { onOrientationUpdate() }
        observe(altimeter) { }
        observe(gps) { onLocationUpdate() }
        observe(speedometer) { }

        binding.navigationTitle.subtitle.setOnLongClickListener {
            // TODO: Show custom share sheet instead
            Pickers.menu(it, R.menu.location_share_menu) { menuItem ->
                val sender = when (menuItem) {
                    R.id.action_send -> LocationSharesheet(requireContext())
                    R.id.action_maps -> LocationGeoSender(requireContext())
                    else -> LocationCopy(requireContext())
                }
                sender.send(gps.location)
                true
            }
            true
        }

        binding.navigationTitle.subtitle.setOnClickListener {
            val sheet = LocationBottomSheet()
            sheet.gps = gps
            sheet.show(this)
        }

        binding.altitude.setOnClickListener {
            val sheet = AltitudeBottomSheet()
            sheet.currentAltitude = Reading(altimeter.altitude, Instant.now())
            sheet.show(this)
        }

        CustomUiUtils.setButtonState(binding.sightingCompassBtn, showSightingCompass)
        binding.sightingCompassBtn.setOnClickListener {
            setSightingCompassStatus(!showSightingCompass)
        }
        sightingCompass?.stop()

        binding.viewCamera.setOnClickListener {
            toggleDestinationBearing()
        }

        binding.viewCameraLine.setOnClickListener {
            toggleDestinationBearing()
        }

        binding.beaconBtn.setOnClickListener {
            if (destination == null) {
                navController.navigate(R.id.action_navigatorFragment_to_beaconListFragment)
            } else {
                destination = null
                cache.remove(LAST_BEACON_ID)
                updateNavigator()
            }
        }

        binding.beaconBtn.setOnLongClickListener {
            if (gps.hasValidReading) {
                val bundle = bundleOf(
                    "initial_location" to GeoUri(
                        gps.location,
                        if (altimeter.hasValidReading) altimeter.altitude else gps.altitude
                    )
                )
                navController.navigate(R.id.action_navigatorFragment_to_beaconListFragment, bundle)
            } else {
                navController.navigate(R.id.action_navigatorFragment_to_beaconListFragment)

            }
            true
        }

        binding.accuracyView.setOnClickListener { displayAccuracyTips() }

        binding.roundCompass.setOnClickListener {
            toggleDestinationBearing()
        }
        binding.radarCompass.setOnSingleTapListener {
            toggleDestinationBearing()
        }
        binding.linearCompass.setOnClickListener {
            toggleDestinationBearing()
        }

        scheduleUpdates(INTERVAL_60_FPS)
    }

    private fun setSightingCompassStatus(isOn: Boolean) {
        showSightingCompass = isOn
        CustomUiUtils.setButtonState(binding.sightingCompassBtn, isOn)
        if (isOn) {
            requestCamera { hasPermission ->
                if (hasPermission) {
                    enableSightingCompass()
                } else {
                    alertNoCameraPermission()
                    setSightingCompassStatus(false)
                }
            }
        } else {
            disableSightingCompass()
        }
    }

    private fun enableSightingCompass() {
        sightingCompass?.start()

        if (sightingCompass?.isRunning() == true) {
            // TODO: Extract this logic to the flashlight (if camera is in use)
            if (userPrefs.navigation.rightButton == QuickActionType.Flashlight) {
                binding.navigationTitle.rightButton.isClickable = false
            }
            if (userPrefs.navigation.leftButton == QuickActionType.Flashlight) {
                binding.navigationTitle.leftButton.isClickable = false
            }
        }
    }

    private fun disableSightingCompass() {
        sightingCompass?.stop()
        if (userPrefs.navigation.rightButton == QuickActionType.Flashlight) {
            binding.navigationTitle.rightButton.isClickable = true
        }
        if (userPrefs.navigation.leftButton == QuickActionType.Flashlight) {
            binding.navigationTitle.leftButton.isClickable = true
        }
    }

    private fun toggleDestinationBearing() {
        if (destination != null) {
            // Don't set destination bearing while navigating
            return
        }

        if (destinationBearing == null) {
            destinationBearing = compass.rawBearing
            cache.putFloat(LAST_DEST_BEARING, compass.rawBearing)
            Alerts.toast(
                requireContext(),
                getString(R.string.toast_destination_bearing_set)
            )
        } else {
            destinationBearing = null
            cache.remove(LAST_DEST_BEARING)
        }

        handleShowWhenLocked()
    }

    private fun handleShowWhenLocked() {
        activity?.let {
            val shouldShow =
                isBound && lockScreenPresence && (destination != null || destinationBearing != null)
            tryOrNothing {
                Screen.setShowWhenLocked(it, shouldShow)
            }
        }
    }

    private fun displayAccuracyTips() {
        context ?: return

        val gpsHorizontalAccuracy = gps.horizontalAccuracy
        val gpsVerticalAccuracy = gps.verticalAccuracy

        val gpsHAccuracyStr =
            if (gpsHorizontalAccuracy == null) getString(R.string.accuracy_distance_unknown) else getString(
                R.string.accuracy_distance_format,
                formatService.formatDistance(
                    Distance.meters(gpsHorizontalAccuracy).convertTo(baseDistanceUnits)
                )
            )
        val gpsVAccuracyStr =
            if (gpsVerticalAccuracy == null) getString(R.string.accuracy_distance_unknown) else getString(
                R.string.accuracy_distance_format,
                formatService.formatDistance(
                    Distance.meters(gpsVerticalAccuracy).convertTo(baseDistanceUnits)
                )
            )

        Alerts.dialog(
            requireContext(),
            getString(R.string.accuracy_info_title),
            getString(
                R.string.accuracy_info,
                gpsHAccuracyStr,
                gpsVAccuracyStr,
                gps.satellites.toString()
            ),
            contentView = CompassCalibrationView.withFrame(
                requireContext(),
                height = Resources.dp(requireContext(), 200f).toInt()
            ),
            cancelText = null,
            cancelOnOutsideTouch = false
        )
    }

    private fun updateAstronomyData() {
        inBackground {
            if (gps.location == Coordinate.zero) {
                return@inBackground
            }

            updateTideLayerCommand.execute()
            updateAstronomyLayerCommand.execute()
            astronomyDataLoaded = true
        }
    }

    override fun onResume() {
        super.onResume()
        lastOrientation = null

        // Resume navigation
        val lastBeaconId = cache.getLong(LAST_BEACON_ID)
        if (lastBeaconId != null) {
            inBackground {
                withContext(Dispatchers.IO) {
                    destination = beaconRepo.getBeacon(lastBeaconId)?.toBeacon()
                }
                withContext(Dispatchers.Main) {
                    handleShowWhenLocked()
                }
            }
        }

        val lastDestBearing = cache.getFloat(LAST_DEST_BEARING)
        if (lastDestBearing != null) {
            destinationBearing = lastDestBearing
        }

        updateDeclination()

        binding.beaconBtn.show()
        binding.roundCompass.isInvisible = useRadarCompass
        binding.radarCompass.isInvisible = !useRadarCompass

        // Update the UI
        updateNavigator()
    }

    override fun onPause() {
        super.onPause()
        loadPathRunner.cancel()
        loadBeaconsRunner.cancel()
        sightingCompass?.stop()
        requireMainActivity().errorBanner.dismiss(ErrorBannerReason.CompassPoor)
        shownAccuracyToast = false
        gpsErrorShown = false
    }

    private fun updateNearbyBeacons() {
        inBackground {
            onIO {
                loadBeaconsRunner.skipIfRunning {
                    if (!isNearbyEnabled) {
                        nearbyBeacons = listOfNotNull(destination)
                        return@skipIfRunning
                    }

                    nearbyBeacons = (navigationService.getNearbyBeacons(
                        gps.location,
                        beacons,
                        nearbyCount,
                        8f,
                        nearbyDistance
                    ) + listOfNotNull(destination)).distinctBy { it.id }
                }
            }
        }
    }

    private fun getDestinationBearing(): Float? {
        val destLocation = destination?.coordinate
        return when {
            destLocation != null -> {
                fromTrueNorth(gps.location.bearingTo(destLocation).value)
            }
            destinationBearing != null -> {
                destinationBearing
            }
            else -> {
                null
            }
        }
    }

    private fun getSelectedBeacon(nearby: Collection<Beacon>): Beacon? {
        return destination ?: getFacingBeacon(nearby)
    }

    private fun fromTrueNorth(bearing: Float): Float {
        if (useTrueNorth) {
            return bearing
        }
        return DeclinationUtils.fromTrueNorthBearing(bearing, declination)
    }

    private fun updateDeclination() {
        inBackground {
            onIO {
                declination = declinationProvider.getDeclination()
                compass.declination = declination
            }
        }
    }

    private fun getFacingBeacon(nearby: Collection<Beacon>): Beacon? {
        return navigationService.getFacingBeacon(
            getPosition(),
            nearby,
            declination,
            useTrueNorth
        )
    }

    override fun onUpdate() {
        super.onUpdate()

        if (!isBound) {
            return
        }

        val selectedBeacon = getSelectedBeacon(nearbyBeacons)

        if (selectedBeacon != null) {
            binding.navigationSheet.show(
                getPosition(),
                selectedBeacon,
                declination,
                useTrueNorth
            )
        } else {
            binding.navigationSheet.hide()
        }

        gpsStatusBadge?.let {
            binding.gpsStatus.setStatusText(it.name)
            binding.gpsStatus.setBackgroundTint(it.color)
        }

        compassStatusBadge?.let {
            binding.compassStatus.setStatusText(it.name)
            binding.compassStatus.setBackgroundTint(it.color)
        }

        // Speed
        binding.speed.title = formatService.formatSpeed(speedometer.speed.speed)

        // Azimuth
        val azimuthText = formatService.formatDegrees(compass.bearing.value, replace360 = true)
            .padStart(4, ' ')
        val directionText = formatService.formatDirection(compass.bearing.direction)
            .padStart(2, ' ')
        @SuppressLint("SetTextI18n")
        binding.navigationTitle.title.text = "$azimuthText   $directionText"

        // Altitude
        binding.altitude.title = formatService.formatDistance(
            Distance.meters(altimeter.altitude).convertTo(baseDistanceUnits)
        )

        // Compass
        listOf<ICompassView>(
            binding.roundCompass,
            binding.radarCompass,
            binding.linearCompass
        ).forEach {
            it.azimuth = compass.bearing
            it.declination = declination
            it.compassCenter = gps.location
        }

        // This gets set with the other compass layers as well, but also set it here to keep it up to date since this changes more often
        myLocationLayer.setLocation(gps.location)
        myLocationLayer.setAzimuth(compass.bearing)
        myAccuracyLayer.setLocation(gps.location, gps.horizontalAccuracy)

        // Location
        binding.navigationTitle.subtitle.text = formatService.formatLocation(gps.location)

        updateNavigationButton()

        // show on lock screen
        if (lockScreenPresence && (destination != null || destinationBearing != null)) {
            activity?.let {
                tryOrNothing {
                    Screen.setShowWhenLocked(it, true)
                }
            }
        }
    }

    private fun updateCompassLayers() {
        inBackground {
            val destBearing = getDestinationBearing()
            val destination = destination
            val destColor = destination?.color ?: AppColor.Blue.color

            val direction = destBearing?.let {
                MappableBearing(
                    Bearing(it),
                    destColor
                )
            }

            myLocationLayer.setAzimuth(compass.bearing)
            myLocationLayer.setLocation(gps.location)
            myAccuracyLayer.setLocation(gps.location, gps.horizontalAccuracy)

            // Update beacon layers
            beaconLayer.setBeacons(nearbyBeacons)
            beaconCompassLayer.setBeacons(nearbyBeacons)
            beaconCompassLayer.highlight(destination)
            beaconLayer.highlight(destination)

            // Destination
            if (destination != null) {
                navigationCompassLayer.setDestination(destination)
            } else if (direction != null) {
                navigationCompassLayer.setDestination(direction)
            } else {
                navigationCompassLayer.setDestination(null as MappableBearing?)
            }
        }
    }

    private fun updateCompassPaths(reload: Boolean = false) {
        inBackground {
            loadPathRunner.skipIfRunning {

                if (!useRadarCompass) {
                    return@skipIfRunning
                }

                val mappablePaths = onIO {
                    val loadGeofence = Geofence(
                        gps.location,
                        Distance.meters(nearbyDistance + 10)
                    )
                    val load = CoordinateBounds.from(loadGeofence)

                    val unloadGeofence =
                        loadGeofence.copy(radius = Distance.meters(loadGeofence.radius.distance + 1000))
                    val unload = CoordinateBounds.from(unloadGeofence)

                    pathLoader.update(paths, load, unload, reload)

                    val mappablePaths =
                        pathLoader.getPointsWithBacktrack(requireContext()).mapNotNull {
                            val path =
                                paths.firstOrNull { p -> p.id == it.key } ?: return@mapNotNull null
                            it.value.asMappable(requireContext(), path)
                        }

                    mappablePaths
                }

                withContext(Dispatchers.Main) {
                    if (isBound) {
                        pathLayer.setPaths(mappablePaths)
                    }
                }
            }
        }


    }

    private fun getPosition(): Position {
        return Position(
            gps.location,
            altimeter.altitude,
            compass.bearing,
            speedometer.speed.speed
        )
    }

    private fun showCalibrationDialog() {
        if (userPrefs.navigation.showCalibrationOnNavigateDialog) {
            Alerts.dialog(
                requireContext(),
                getString(R.string.calibrate_compass_dialog_title),
                getString(
                    R.string.calibrate_compass_on_navigate_dialog_content,
                    getString(android.R.string.ok)
                ),
                contentView = CompassCalibrationView.withFrame(
                    requireContext(),
                    height = Resources.dp(requireContext(), 200f).toInt()
                ),
                cancelText = null,
                cancelOnOutsideTouch = false
            )
        }
    }

    private fun onOrientationUpdate(): Boolean {

        if (orientation.orientation == lastOrientation) {
            return true
        }

        lastOrientation = orientation.orientation

        val style = styleChooser.getStyle(orientation.orientation)

        binding.linearCompass.isInvisible = style != CompassStyle.Linear
        binding.sightingCompassBtn.isInvisible = style != CompassStyle.Linear
        binding.roundCompass.isInvisible = style != CompassStyle.Round
        binding.radarCompass.isInvisible = style != CompassStyle.Radar

        if (style == CompassStyle.Linear) {
            if (showSightingCompass && sightingCompass?.isRunning() == false) {
                enableSightingCompass()
            }
        } else {
            disableSightingCompass()
        }
        return true
    }

    private fun onLocationUpdate() {

        if (gpsTimeoutShown && gps is CustomGPS && !(gps as CustomGPS).isTimedOut) {
            gpsTimeoutShown = false
            requireMainActivity().errorBanner.dismiss(ErrorBannerReason.GPSTimeout)
        }

        updateNearbyBeacons()
        updateDeclination()

        if (!astronomyDataLoaded) {
            updateAstronomyData()
        }

        if (paths.any()) {
            updateCompassPaths()
        }
    }

    private fun updateSensorStatus() {
        inBackground {
            compassStatusBadge = compassStatusBadgeProvider.getBadge()
            gpsStatusBadge = gpsStatusBadgeProvider.getBadge()

            onMain {
                detectAndShowGPSError()
                detectAndShowCompassError()
            }

        }
    }


    private fun updateNavigationButton() {
        if (destination != null) {
            binding.beaconBtn.setImageResource(R.drawable.ic_cancel)
        } else {
            binding.beaconBtn.setImageResource(R.drawable.ic_beacon)
        }
    }

    private fun updateNavigator() {
        handleShowWhenLocked()
        onLocationUpdate()
        updateNavigationButton()
    }

    private fun detectAndShowCompassError() {
        if ((compass.quality == Quality.Poor) && !shownAccuracyToast) {
            val banner = requireMainActivity().errorBanner
            banner.report(
                UserError(
                    ErrorBannerReason.CompassPoor,
                    getString(
                        R.string.compass_calibrate_toast, formatService.formatQuality(
                            compass.quality
                        ).lowercase(Locale.getDefault())
                    ),
                    R.drawable.ic_compass_icon,
                    getString(R.string.how)
                ) {
                    displayAccuracyTips()
                    banner.hide()
                })
            shownAccuracyToast = true
        } else if (compass.quality == Quality.Good || compass.quality == Quality.Moderate) {
            requireMainActivity().errorBanner.dismiss(ErrorBannerReason.CompassPoor)
        }
    }

    private fun detectAndShowGPSError() {
        if (gps is OverrideGPS && gps.location == Coordinate.zero && !gpsErrorShown) {
            val activity = requireMainActivity()
            val navController = findNavController()
            val error = UserError(
                ErrorBannerReason.LocationNotSet,
                getString(R.string.location_not_set),
                R.drawable.satellite,
                getString(R.string.set)
            ) {
                activity.errorBanner.dismiss(ErrorBannerReason.LocationNotSet)
                navController.navigate(R.id.calibrateGPSFragment)
            }
            activity.errorBanner.report(error)
            gpsErrorShown = true
        } else if (gps is CachedGPS && gps.location == Coordinate.zero && !gpsErrorShown) {
            val error = UserError(
                ErrorBannerReason.NoGPS,
                getString(R.string.location_disabled),
                R.drawable.satellite
            )
            requireMainActivity().errorBanner.report(error)
            gpsErrorShown = true
        } else if (gps is CustomGPS && (gps as CustomGPS).isTimedOut && !gpsTimeoutShown) {
            val error = UserError(
                ErrorBannerReason.GPSTimeout,
                getString(R.string.gps_signal_lost),
                R.drawable.satellite
            )
            requireMainActivity().errorBanner.report(error)
            gpsTimeoutShown = true
        }
    }

    companion object {
        const val LAST_BEACON_ID = "last_beacon_id_long"
        const val LAST_DEST_BEARING = "last_dest_bearing"
        const val CACHE_CAMERA_ZOOM = "sighting_compass_camera_zoom"
    }

    override fun generateBinding(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): ActivityNavigatorBinding {
        return ActivityNavigatorBinding.inflate(layoutInflater, container, false)
    }

}
