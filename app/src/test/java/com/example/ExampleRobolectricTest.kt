package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.ActivityCalorieCalculator
import com.example.util.GeoMath
import com.example.util.PrayerCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("رفيق المعتمر", appName)
    }

    @Test
    fun `prayer calculator returns all six prayers`() {
        val schedule = PrayerCalculator.calculate(21.4225, 39.8262)
        assertEquals(6, schedule.allPrayers.size)
        assertTrue(schedule.nextName.isNotBlank())
        assertTrue(schedule.countdownFormatted.contains(":"))
    }

    @Test
    fun `nearest miqat from madinah is dhul hulayfah`() {
        val (miqat, distanceKm) = GeoMath.nearestMiqat(24.4672, 39.6112)
        assertTrue(miqat.contains("ذو الحليفة"))
        assertTrue(distanceKm < 20.0)
    }

    @Test
    fun `calorie calculator computes tawaf and sai calories accurately`() {
        val stats = ActivityCalorieCalculator.calculate(
            tawafRounds = 7,
            saiRounds = 7,
            totalKm = 4.55,
            weightKg = 70
        )
        assertEquals(1.75, stats.tawafDistanceKm, 0.001)
        assertEquals(2.80, stats.saiDistanceKm, 0.001)
        assertEquals(4.55, stats.totalDistanceKm, 0.001)
        assertTrue(stats.totalCalories > 200)
        assertTrue(stats.estimatedSteps > 5000)
    }

    @Test
    fun `hijri date helper returns formatted arabic hijri string`() {
        val hijri = com.example.util.HijriDateHelper.todayHijriFormatted()
        assertTrue(hijri.endsWith("هـ"))
    }

    @Test
    fun `global strings populated for all six languages`() {
        com.example.model.AppLanguage.entries.forEach { lang ->
            val s = com.example.data.GlobalStrings.forLang(lang)
            assertTrue(s.appTitle.isNotBlank())
            assertTrue(s.quickToolsSectionTitle.isNotBlank())
            assertTrue(s.filterAll.isNotBlank())
            assertEquals(6, s.prayerNames.size)
        }
    }

    @Test
    fun `heat alert evaluator flags danger temperatures and triggers push`() {
        val safe = com.example.util.HeatAlertEvaluator.evaluateDanger(
            tempC = 29,
            feelsLikeC = 30,
            uvIndex = 5,
            dangerThresholdC = 40
        )
        assertEquals(com.example.model.HeatDangerLevel.SAFE, safe)

        val hiHot = com.example.util.HeatAlertEvaluator.computeHeatIndexC(tempC = 42, humidityPct = 45)
        assertTrue(hiHot >= 42)

        val danger = com.example.util.HeatAlertEvaluator.evaluateDanger(
            tempC = 42,
            feelsLikeC = hiHot,
            uvIndex = 10,
            dangerThresholdC = 40
        )
        assertTrue(
            danger == com.example.model.HeatDangerLevel.DANGER ||
                danger == com.example.model.HeatDangerLevel.EXTREME
        )
        assertTrue(
            com.example.util.HeatAlertEvaluator.shouldPushAlert(
                dangerLevel = danger,
                tempC = 42,
                feelsLikeC = hiHot,
                dangerThresholdC = 40
            )
        )
    }

    @Test
    fun `weather alert notifier registers notification channel`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.util.WeatherAlertNotifier.ensureChannel(context, com.example.model.AppLanguage.AR)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channel = nm.getNotificationChannel(com.example.util.WeatherAlertNotifier.CHANNEL_ID)
        assertTrue(channel != null)
    }

    @Test
    fun `global 3d infrastructure labels and infos exist for all languages`() {
        com.example.model.AppLanguage.entries.forEach { lang ->
            val s = com.example.data.GlobalStrings.forLang(lang)
            assertTrue(s.servicesLayerToggleLabel.isNotBlank())
            assertTrue(s.legendBusRoute.isNotBlank())
            assertTrue(s.legendAmbulanceHub.isNotBlank())
            assertTrue(s.scene3DLabels.busRouteLabel.isNotBlank())
            assertTrue(s.scene3DLabels.ambulanceHubLabel.isNotBlank())
            assertTrue(s.scene3DLabels.waterMistLabel.isNotBlank())
            assertTrue(s.scene3DLabels.trainStationLabel.isNotBlank())
            com.example.model.Scene3DType.entries.forEach { scene ->
                val info = s.scene3DInfos[scene]
                assertTrue(info != null && info.title.isNotBlank() && info.description.isNotBlank())
            }
        }
    }

    @Test
    fun `view model toggles 3d services layer state`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.ui.PilgrimViewModel(app)
        assertTrue(vm.uiState.value.show3DServicesLayer)
        vm.toggle3DServicesLayer()
        assertTrue(!vm.uiState.value.show3DServicesLayer)
        vm.toggle3DServicesLayer()
        assertTrue(vm.uiState.value.show3DServicesLayer)
    }
}
