package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GlobalStrings
import com.example.data.PilgrimContent
import com.example.data.PilgrimPreferencesRepository
import com.example.data.WeatherRepository
import java.util.Locale
import com.example.model.ActiveModal
import com.example.model.AppLanguage
import com.example.model.AppTab
import com.example.model.CounterMode
import com.example.model.MoreTextTab
import com.example.model.NusukType
import com.example.model.PilgrimProfile
import com.example.model.PrayerCity
import com.example.model.PrayerSchedule
import com.example.model.RitualStep
import com.example.model.Scene3DType
import com.example.model.TawafFloor
import com.example.model.WeatherCity
import com.example.model.WeatherState
import com.example.util.GeoMath
import com.example.util.GeoPoint
import com.example.util.HeatAlertEvaluator
import com.example.util.LocationAndSensorHelper
import com.example.util.PilgrimTtsHelper
import com.example.util.PrayerCalculator
import com.example.util.WeatherAlertNotifier
import com.example.util.WeatherJobScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PendingLocationAction {
    NONE, PRAYER_GPS, WEATHER_GPS, ENABLE_WEATHER_ALERTS, SHARE_LOCATION, FIND_MIQAT, QIBLA_COMPASS, SAVE_HOTEL_GPS
}

data class PilgrimUiState(
    val showSplash: Boolean = true,
    val currentTab: AppTab = AppTab.HOME,
    val language: AppLanguage = AppLanguage.AR,
    val darkMode: Boolean = false,
    val elderMode: Boolean = false,
    val prayerCity: PrayerCity = PrayerCity.MAKKAH,
    val prayerLat: Double = 21.4225,
    val prayerLng: Double = 39.8262,
    val showAllPrayers: Boolean = false,
    val prayerSchedule: PrayerSchedule = PrayerCalculator.calculate(21.4225, 39.8262),
    val weatherState: WeatherState = WeatherState(),
    val weatherAlertsEnabled: Boolean = false,
    val weatherDangerThresholdC: Int = 40,
    val waterReminderActive: Boolean = false,
    val selectedScene3D: Scene3DType = Scene3DType.KAABA,
    val autoOrbit3D: Boolean = true,
    val show3DServicesLayer: Boolean = true,
    val selectedNusuk: NusukType = NusukType.UMRAH,
    val counterMode: CounterMode = CounterMode.TAWAF,
    val tawafFloor: TawafFloor = TawafFloor.SAHN,
    val round: Int = 1,
    val jamIdx: Int = 0,
    val jamCounts: List<Int> = listOf(0, 0, 0),
    val tasbih: Int = 0,
    val totalKm: Double = 0.0,
    val tawafTotalRounds: Int = 0,
    val saiTotalRounds: Int = 0,
    val weightKg: Int = 70,
    val completedSteps: Set<String> = emptySet(),
    val nearestMiqatText: String = "اضغط لتحديد أقرب ميقات مكاني لموقعك الحالي الآن.",
    val qiblaActive: Boolean = false,
    val qiblaBearingDeg: Float? = null,
    val qiblaDistanceMeters: Double? = null,
    val deviceAzimuthDeg: Float = 0f,
    val shoeLocation: String = "لم يتم التحديد",
    val hotelName: String = "أبراج الساعة",
    val hotelLat: Double? = null,
    val hotelLng: Double? = null,
    val hotelDistanceMeters: Double? = null,
    val profile: PilgrimProfile = PilgrimProfile(),
    val moreTextTab: MoreTextTab = MoreTextTab.DUAS,
    val moreSearchQuery: String = "",
    val faqSearchQuery: String = "",
    val selectedFidyahKey: String? = null,
    val lovedOnes: List<String> = listOf("أمي وأبي: اللهم اغفر لهما وارحمهما"),
    val checklistMap: Map<String, Boolean> = emptyMap(),
    val activeModal: ActiveModal = ActiveModal.NONE,
    val previewStep: RitualStep? = null,
    val toastMessage: String? = null
)

sealed interface PilgrimEvent {
    data class ShareText(val text: String) : PilgrimEvent
    data object RequestLocationPermission : PilgrimEvent
    data object RequestNotificationPermission : PilgrimEvent
}

class PilgrimViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PilgrimPreferencesRepository(application)
    private val weatherRepo = WeatherRepository()
    private val locationHelper = LocationAndSensorHelper(application)
    private val ttsHelper = PilgrimTtsHelper(application)

    private val _uiState = MutableStateFlow(PilgrimUiState())
    val uiState: StateFlow<PilgrimUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PilgrimEvent>()
    val events = _events.asSharedFlow()

    private var pendingLocationAction = PendingLocationAction.NONE
    private var pendingNotificationAction: (() -> Unit)? = null
    private var waterReminderJob: Job? = null
    private var compassJob: Job? = null
    private var toastJob: Job? = null

    init {
        viewModelScope.launch {
            delay(1600)
            _uiState.update { it.copy(showSplash = false) }
        }

        viewModelScope.launch {
            prefs.dataFlow.collect { stored ->
                _uiState.update {
                    it.copy(
                        language = stored.language,
                        darkMode = stored.darkMode,
                        elderMode = stored.elderMode,
                        selectedNusuk = stored.nusukType,
                        counterMode = stored.counterMode,
                        round = stored.round,
                        jamCounts = stored.jamCounts,
                        tasbih = stored.tasbih,
                        totalKm = stored.totalKm,
                        tawafTotalRounds = stored.tawafTotalRounds,
                        saiTotalRounds = stored.saiTotalRounds,
                        weightKg = stored.weightKg,
                        completedSteps = stored.completedStepIds,
                        shoeLocation = stored.shoeLocation,
                        hotelName = stored.hotelName,
                        hotelLat = stored.hotelLat,
                        hotelLng = stored.hotelLng,
                        profile = stored.profile,
                        lovedOnes = stored.lovedOnes,
                        checklistMap = stored.checklistMap,
                        weatherAlertsEnabled = stored.weatherAlertsEnabled,
                        weatherDangerThresholdC = stored.weatherDangerThresholdC
                    )
                }
            }
        }

        // Live 1-second prayer countdown ticker
        viewModelScope.launch {
            while (isActive) {
                val st = _uiState.value
                _uiState.update {
                    it.copy(prayerSchedule = PrayerCalculator.calculate(st.prayerLat, st.prayerLng))
                }
                delay(1000)
            }
        }

        loadWeather(WeatherCity.MAKKAH)
    }

    fun showToast(msg: String) {
        toastJob?.cancel()
        _uiState.update { it.copy(toastMessage = msg) }
        toastJob = viewModelScope.launch {
            delay(2400)
            _uiState.update { it.copy(toastMessage = null) }
        }
    }

    private fun vibrate(durations: LongArray) {
        try {
            val ctx = getApplication<Application>()
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (durations.size == 1) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durations[0], VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(VibrationEffect.createWaveform(durations, -1))
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durations, -1)
            }
        } catch (_: Exception) {
        }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch { prefs.setLanguage(lang) }
    }

    fun toggleDarkMode(force: Boolean? = null) {
        val next = force ?: !_uiState.value.darkMode
        viewModelScope.launch { prefs.setDarkMode(next) }
    }

    fun toggleElderMode(force: Boolean? = null) {
        val next = force ?: !_uiState.value.elderMode
        viewModelScope.launch { prefs.setElderMode(next) }
    }

    fun toggleAllPrayers() {
        _uiState.update { it.copy(showAllPrayers = !it.showAllPrayers) }
    }

    fun selectPrayerCity(city: PrayerCity) {
        when (city) {
            PrayerCity.MAKKAH -> {
                _uiState.update {
                    it.copy(
                        prayerCity = city,
                        prayerLat = 21.4225,
                        prayerLng = 39.8262,
                        prayerSchedule = PrayerCalculator.calculate(21.4225, 39.8262)
                    )
                }
            }
            PrayerCity.MADINAH -> {
                _uiState.update {
                    it.copy(
                        prayerCity = city,
                        prayerLat = 24.4672,
                        prayerLng = 39.6112,
                        prayerSchedule = PrayerCalculator.calculate(24.4672, 39.6112)
                    )
                }
            }
            PrayerCity.GPS -> {
                runWithLocation(PendingLocationAction.PRAYER_GPS)
            }
        }
    }

    fun loadWeather(city: WeatherCity) {
        if (city == WeatherCity.MY_GPS) {
            runWithLocation(PendingLocationAction.WEATHER_GPS)
            return
        }
        val threshold = _uiState.value.weatherDangerThresholdC
        _uiState.update { it.copy(weatherState = it.weatherState.copy(city = city, isLoading = true)) }
        viewModelScope.launch {
            val result = weatherRepo.fetchWeather(city, threshold)
            _uiState.update { it.copy(weatherState = result) }
            evaluateAndPushIfNeeded(result)
        }
    }

    fun toggleWeatherAlerts(enable: Boolean? = null) {
        val next = enable ?: !_uiState.value.weatherAlertsEnabled
        val app = getApplication<Application>()
        val strings = GlobalStrings.forLang(_uiState.value.language)
        if (!next) {
            WeatherJobScheduler.cancelMonitor(app)
            viewModelScope.launch { prefs.setWeatherAlertsEnabled(false) }
            showToast(strings.heatPushDisabledToast)
            return
        }

        if (!WeatherAlertNotifier.hasNotificationPermission(app)) {
            pendingNotificationAction = { activateWeatherAlertsWithGps() }
            viewModelScope.launch { _events.emit(PilgrimEvent.RequestNotificationPermission) }
        } else {
            activateWeatherAlertsWithGps()
        }
    }

    private fun activateWeatherAlertsWithGps() {
        val app = getApplication<Application>()
        val strings = GlobalStrings.forLang(_uiState.value.language)
        WeatherAlertNotifier.ensureChannel(app, _uiState.value.language)
        viewModelScope.launch { prefs.setWeatherAlertsEnabled(true) }
        WeatherJobScheduler.schedulePeriodicMonitor(app)
        showToast(strings.heatPushEnabledToast)
        runWithLocation(PendingLocationAction.ENABLE_WEATHER_ALERTS)
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        val action = pendingNotificationAction
        pendingNotificationAction = null
        if (granted) {
            action?.invoke()
        } else {
            showToast(GlobalStrings.forLang(_uiState.value.language).notifPermissionToast)
        }
    }

    fun adjustWeatherDangerThreshold(delta: Int) {
        val next = (_uiState.value.weatherDangerThresholdC + delta).coerceIn(32, 48)
        viewModelScope.launch { prefs.setWeatherDangerThresholdC(next) }
        val cur = _uiState.value.weatherState
        val updatedDanger = HeatAlertEvaluator.evaluateDanger(
            tempC = cur.temperatureC,
            feelsLikeC = cur.feelsLikeC,
            uvIndex = cur.uvIndex,
            dangerThresholdC = next
        )
        val updatedWeather = cur.copy(dangerLevel = updatedDanger)
        _uiState.update { it.copy(weatherDangerThresholdC = next, weatherState = updatedWeather) }
        if (_uiState.value.weatherAlertsEnabled) {
            evaluateAndPushIfNeeded(updatedWeather, forcePush = true)
        }
    }

    fun sendTestHeatAlertNotification() {
        val app = getApplication<Application>()
        val st = _uiState.value
        val strings = GlobalStrings.forLang(st.language)
        if (!WeatherAlertNotifier.hasNotificationPermission(app)) {
            pendingNotificationAction = {
                WeatherAlertNotifier.showHeatAlert(
                    context = app,
                    weather = _uiState.value.weatherState,
                    language = _uiState.value.language,
                    thresholdC = _uiState.value.weatherDangerThresholdC,
                    isTestPreview = true
                )
            }
            viewModelScope.launch { _events.emit(PilgrimEvent.RequestNotificationPermission) }
            return
        }
        val sent = WeatherAlertNotifier.showHeatAlert(
            context = app,
            weather = st.weatherState,
            language = st.language,
            thresholdC = st.weatherDangerThresholdC,
            isTestPreview = true
        )
        if (!sent) showToast(strings.notifPermissionToast)
    }

    private fun evaluateAndPushIfNeeded(weather: WeatherState, forcePush: Boolean = false) {
        val st = _uiState.value
        if (!st.weatherAlertsEnabled && !forcePush) return
        val shouldAlert = HeatAlertEvaluator.shouldPushAlert(
            dangerLevel = weather.dangerLevel,
            tempC = weather.temperatureC,
            feelsLikeC = weather.feelsLikeC,
            dangerThresholdC = st.weatherDangerThresholdC
        )
        if (shouldAlert) {
            val sent = WeatherAlertNotifier.showHeatAlert(
                context = getApplication(),
                weather = weather,
                language = st.language,
                thresholdC = st.weatherDangerThresholdC
            )
            if (sent) {
                viewModelScope.launch { prefs.saveLastHeatAlertMs(System.currentTimeMillis()) }
            }
        }
    }

    fun toggleWaterReminder() {
        val strings = GlobalStrings.forLang(_uiState.value.language)
        if (_uiState.value.waterReminderActive) {
            waterReminderJob?.cancel()
            waterReminderJob = null
            _uiState.update { it.copy(waterReminderActive = false) }
            showToast(strings.waterOffToast)
        } else {
            _uiState.update { it.copy(waterReminderActive = true) }
            vibrate(longArrayOf(80))
            showToast(strings.waterOnToast)
            waterReminderJob = viewModelScope.launch {
                while (isActive) {
                    delay(1_800_000L)
                    vibrate(longArrayOf(0, 150, 80, 150))
                    showToast("💧 Zamzam / Water")
                }
            }
        }
    }

    fun selectScene3D(scene: Scene3DType) {
        _uiState.update { it.copy(selectedScene3D = scene) }
    }

    fun jumpTo3D(scene: Scene3DType) {
        _uiState.update { it.copy(currentTab = AppTab.TOUR_3D, selectedScene3D = scene) }
    }

    fun toggleAutoOrbit3D() {
        _uiState.update { it.copy(autoOrbit3D = !it.autoOrbit3D) }
    }

    fun toggle3DServicesLayer() {
        _uiState.update { it.copy(show3DServicesLayer = !it.show3DServicesLayer) }
    }

    fun setNusukType(nusuk: NusukType) {
        viewModelScope.launch { prefs.setNusuk(nusuk) }
    }

    fun setCounterMode(mode: CounterMode) {
        viewModelScope.launch { prefs.setCounterMode(mode) }
    }

    fun setTawafFloor(floor: TawafFloor) {
        _uiState.update { it.copy(tawafFloor = floor) }
    }

    fun selectJamrah(index: Int) {
        _uiState.update { it.copy(jamIdx = index.coerceIn(0, 2)) }
    }

    fun incrementCounter() {
        val st = _uiState.value
        if (st.counterMode == CounterMode.JAMARAT) {
            val idx = st.jamIdx
            val currentList = st.jamCounts.toMutableList()
            if (currentList[idx] < 7) {
                currentList[idx] = currentList[idx] + 1
                val count = currentList[idx]
                vibrate(if (count == 7) longArrayOf(0, 100, 60, 150) else longArrayOf(55))
                if (count == 7) {
                    showToast(if (idx < 2) "أتممت 7! ادعُ ثم انتقل للتالية" else "أتممت الـ 21 حصاة بحمد الله")
                }
                viewModelScope.launch { prefs.setJamCounts(currentList) }
            } else if (idx < 2) {
                selectJamrah(idx + 1)
            }
            return
        }

        val isTawaf = st.counterMode == CounterMode.TAWAF
        val addedKm = if (isTawaf) 0.25 else 0.40
        val newKm = st.totalKm + addedKm
        val newTawafRounds = st.tawafTotalRounds + if (isTawaf) 1 else 0
        val newSaiRounds = st.saiTotalRounds + if (!isTawaf) 1 else 0
        if (st.round < 7) {
            val newRound = st.round + 1
            vibrate(if (newRound == 7) longArrayOf(0, 100, 60, 150) else longArrayOf(60))
            showToast("تم تسجيل $newRound/7")
            viewModelScope.launch { prefs.setRoundAndActivity(newRound, newKm, newTawafRounds, newSaiRounds) }
        } else {
            vibrate(longArrayOf(0, 120, 60, 180))
            showToast(if (isTawaf) "أتممت الطواف! صلِّ ركعتين" else "أتممت السعي! توجه للحلق/التقصير")
            viewModelScope.launch { prefs.setRoundAndActivity(st.round, newKm, newTawafRounds, newSaiRounds) }
        }
    }

    fun decrementCounter() {
        val st = _uiState.value
        if (st.counterMode == CounterMode.JAMARAT) {
            val list = st.jamCounts.toMutableList()
            if (list[st.jamIdx] > 0) {
                list[st.jamIdx] = list[st.jamIdx] - 1
                viewModelScope.launch { prefs.setJamCounts(list) }
            }
        } else if (st.round > 1) {
            val isTawaf = st.counterMode == CounterMode.TAWAF
            val subKm = if (isTawaf) 0.25 else 0.40
            val newKm = (st.totalKm - subKm).coerceAtLeast(0.0)
            val newTawaf = (st.tawafTotalRounds - if (isTawaf) 1 else 0).coerceAtLeast(0)
            val newSai = (st.saiTotalRounds - if (!isTawaf) 1 else 0).coerceAtLeast(0)
            viewModelScope.launch { prefs.setRoundAndActivity(st.round - 1, newKm, newTawaf, newSai) }
        }
    }

    fun resetCounter() {
        val st = _uiState.value
        val strings = GlobalStrings.forLang(st.language)
        if (st.counterMode == CounterMode.JAMARAT) {
            _uiState.update { it.copy(jamIdx = 0) }
            viewModelScope.launch { prefs.setJamCounts(listOf(0, 0, 0)) }
        } else {
            viewModelScope.launch { prefs.setRoundAndActivity(1, st.totalKm, st.tawafTotalRounds, st.saiTotalRounds) }
        }
        showToast(strings.resetToast)
    }

    fun adjustWeightKg(delta: Int) {
        val next = (_uiState.value.weightKg + delta).coerceIn(35, 180)
        viewModelScope.launch { prefs.setWeightKg(next) }
    }

    fun resetActivityStats() {
        viewModelScope.launch {
            prefs.resetActivityStats()
            showToast("تم تصفير إحصائيات النشاط والسعرات")
        }
    }

    fun incrementTasbih() {
        val next = _uiState.value.tasbih + 1
        vibrate(if (next % 33 == 0) longArrayOf(120) else longArrayOf(30))
        viewModelScope.launch { prefs.setTasbih(next) }
    }

    fun resetTasbih() {
        viewModelScope.launch { prefs.setTasbih(0) }
    }

    fun toggleStepDone(stepId: String) {
        vibrate(longArrayOf(40))
        viewModelScope.launch { prefs.toggleStepDone(stepId) }
    }

    fun openStepPreview(step: RitualStep) {
        _uiState.update { it.copy(previewStep = step, activeModal = ActiveModal.STEP_PREVIEW) }
    }

    fun speak(text: String, langCode: String = "ar") {
        if (ttsHelper.speak(text, langCode)) {
            showToast("🔊 ...")
        } else {
            showToast("النطق الصوتي غير متاح حالياً")
        }
    }

    fun speakCurrentRound() {
        val st = _uiState.value
        val activeRound = if (st.counterMode == CounterMode.JAMARAT) st.jamCounts[st.jamIdx] else st.round
        val idx = (activeRound - 1).coerceIn(0, 6)
        val dua = PilgrimContent.roundDuas[st.counterMode]?.getOrNull(idx) ?: return
        speak(dua)
    }

    fun openModal(modal: ActiveModal) {
        _uiState.update { it.copy(activeModal = modal) }
        if (modal == ActiveModal.HOTEL) {
            refreshHotelDistanceIfAvailable()
        }
    }

    fun closeModal() {
        _uiState.update { it.copy(activeModal = ActiveModal.NONE) }
    }

    fun saveShoeLocation(text: String) {
        val strings = GlobalStrings.forLang(_uiState.value.language)
        viewModelScope.launch {
            prefs.saveShoe(text)
            closeModal()
            showToast(strings.savedToast)
        }
    }

    fun saveHotelName(name: String) {
        val strings = GlobalStrings.forLang(_uiState.value.language)
        viewModelScope.launch {
            prefs.saveHotelName(name)
            closeModal()
            showToast(strings.savedToast)
        }
    }

    fun saveHotelGps() = runWithLocation(PendingLocationAction.SAVE_HOTEL_GPS)
    fun shareMyLocation() = runWithLocation(PendingLocationAction.SHARE_LOCATION)
    fun findNearestMiqat() = runWithLocation(PendingLocationAction.FIND_MIQAT)
    fun activateQiblaCompass() = runWithLocation(PendingLocationAction.QIBLA_COMPASS)

    private fun runWithLocation(action: PendingLocationAction) {
        pendingLocationAction = action
        if (locationHelper.hasLocationPermission()) {
            executeLocationAction(action)
        } else {
            viewModelScope.launch { _events.emit(PilgrimEvent.RequestLocationPermission) }
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        val action = pendingLocationAction
        pendingLocationAction = PendingLocationAction.NONE
        if (granted && action != PendingLocationAction.NONE) {
            executeLocationAction(action)
        } else if (!granted) {
            showToast(GlobalStrings.forLang(_uiState.value.language).gpsNeededToast)
        }
    }

    private fun executeLocationAction(action: PendingLocationAction) {
        viewModelScope.launch {
            val point: GeoPoint? = locationHelper.getCurrentLocation()
            val strings = GlobalStrings.forLang(_uiState.value.language)
            if (point == null) {
                showToast(strings.gpsNeededToast)
                return@launch
            }
            when (action) {
                PendingLocationAction.PRAYER_GPS -> {
                    prefs.saveLastUserCoordinates(point.lat, point.lng)
                    _uiState.update {
                        it.copy(
                            prayerCity = PrayerCity.GPS,
                            prayerLat = point.lat,
                            prayerLng = point.lng,
                            prayerSchedule = PrayerCalculator.calculate(point.lat, point.lng)
                        )
                    }
                    showToast(strings.savedToast)
                }
                PendingLocationAction.WEATHER_GPS, PendingLocationAction.ENABLE_WEATHER_ALERTS -> {
                    prefs.saveLastUserCoordinates(point.lat, point.lng)
                    _uiState.update {
                        it.copy(weatherState = it.weatherState.copy(city = WeatherCity.MY_GPS, isLoading = true))
                    }
                    val result = weatherRepo.fetchWeatherByCoordinates(
                        lat = point.lat,
                        lng = point.lng,
                        city = WeatherCity.MY_GPS,
                        dangerThresholdC = _uiState.value.weatherDangerThresholdC
                    )
                    _uiState.update { it.copy(weatherState = result) }
                    evaluateAndPushIfNeeded(result)
                }
                PendingLocationAction.SHARE_LOCATION -> {
                    val shareTxt = "${strings.appTitle}: https://maps.google.com/?q=${point.lat},${point.lng}"
                    _events.emit(PilgrimEvent.ShareText(shareTxt))
                }
                PendingLocationAction.FIND_MIQAT -> {
                    val (name, km) = GeoMath.nearestMiqat(point.lat, point.lng)
                    _uiState.update {
                        it.copy(nearestMiqatText = "$name (${String.format(Locale.US, "%.1f", km)} ${strings.kmUnit})")
                    }
                }
                PendingLocationAction.SAVE_HOTEL_GPS -> {
                    prefs.saveHotelGps(point.lat, point.lng)
                    _uiState.update { it.copy(hotelDistanceMeters = 0.0) }
                    showToast("✓ تم حفظ إحداثيات GPS للفندق")
                }
                PendingLocationAction.QIBLA_COMPASS -> {
                    val bearing = GeoMath.qiblaBearingDegrees(point.lat, point.lng).toFloat()
                    val dist = GeoMath.distanceMeters(point.lat, point.lng, GeoMath.KAABA_LAT, GeoMath.KAABA_LNG)
                    _uiState.update {
                        it.copy(
                            qiblaActive = true,
                            qiblaBearingDeg = bearing,
                            qiblaDistanceMeters = dist
                        )
                    }
                    startCompassListening()
                }
                PendingLocationAction.NONE -> Unit
            }
        }
    }

    private fun startCompassListening() {
        compassJob?.cancel()
        compassJob = viewModelScope.launch {
            locationHelper.azimuthDegreesFlow().collect { azimuth ->
                _uiState.update { it.copy(deviceAzimuthDeg = azimuth) }
            }
        }
    }

    private fun refreshHotelDistanceIfAvailable() {
        val lat = _uiState.value.hotelLat ?: return
        val lng = _uiState.value.hotelLng ?: return
        if (!locationHelper.hasLocationPermission()) return
        viewModelScope.launch {
            val cur = locationHelper.getCurrentLocation() ?: return@launch
            val dist = GeoMath.distanceMeters(cur.lat, cur.lng, lat, lng)
            _uiState.update { it.copy(hotelDistanceMeters = dist) }
        }
    }

    fun saveProfile(profile: PilgrimProfile) {
        val strings = GlobalStrings.forLang(_uiState.value.language)
        viewModelScope.launch {
            prefs.saveProfile(profile)
            closeModal()
            showToast(strings.savedToast)
        }
    }

    fun addLovedOne(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val next = _uiState.value.lovedOnes + clean
        viewModelScope.launch {
            prefs.saveLovedOnes(next)
            showToast("تمت الإضافة")
        }
    }

    fun removeLovedOne(index: Int) {
        val list = _uiState.value.lovedOnes.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            viewModelScope.launch { prefs.saveLovedOnes(list) }
        }
    }

    fun toggleChecklist(id: String, checked: Boolean) {
        viewModelScope.launch { prefs.toggleChecklist(id, checked) }
    }

    fun setMoreTextTab(tab: MoreTextTab) {
        _uiState.update { it.copy(moreTextTab = tab) }
    }

    fun setMoreSearchQuery(q: String) {
        _uiState.update { it.copy(moreSearchQuery = q) }
    }

    fun setFaqSearchQuery(q: String) {
        _uiState.update { it.copy(faqSearchQuery = q) }
    }

    fun selectFidyahRule(key: String) {
        _uiState.update { it.copy(selectedFidyahKey = key) }
    }

    override fun onCleared() {
        super.onCleared()
        ttsHelper.shutdown()
    }
}
