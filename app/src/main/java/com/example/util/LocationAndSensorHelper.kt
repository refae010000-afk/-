package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.example.data.PilgrimContent
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val lat: Double, val lng: Double)

object GeoMath {
    const val KAABA_LAT = 21.4225
    const val KAABA_LNG = 39.8262

    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLng = (lng2 - lng1) * PI / 180.0
        val a = sin(dLat / 2).pow(2) +
            cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) * sin(dLng / 2).pow(2)
        return r * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    fun qiblaBearingDegrees(lat: Double, lng: Double): Double {
        val l1 = lat * PI / 180.0
        val n1 = lng * PI / 180.0
        val l2 = KAABA_LAT * PI / 180.0
        val n2 = KAABA_LNG * PI / 180.0
        val y = sin(n2 - n1) * cos(l2)
        val x = cos(l1) * sin(l2) - sin(l1) * cos(l2) * cos(n2 - n1)
        return ((atan2(y, x) * 180.0 / PI) + 360.0) % 360.0
    }

    fun nearestMiqat(lat: Double, lng: Double): Pair<String, Double> {
        var bestName = PilgrimContent.miqats.first().name
        var minKm = Double.MAX_VALUE
        for (m in PilgrimContent.miqats) {
            val km = distanceMeters(lat, lng, m.lat, m.lng) / 1000.0
            if (km < minKm) {
                minKm = km
                bestName = m.name
            }
        }
        return bestName to minKm
    }
}

class LocationAndSensorHelper(private val context: Context) {
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(context) }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): GeoPoint? {
        if (!hasLocationPermission()) return null
        return suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { loc: Location? ->
                    if (loc != null) {
                        cont.resume(GeoPoint(loc.latitude, loc.longitude))
                    } else {
                        fusedClient.lastLocation
                            .addOnSuccessListener { last ->
                                cont.resume(last?.let { GeoPoint(it.latitude, it.longitude) })
                            }
                            .addOnFailureListener { cont.resume(null) }
                    }
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
            cont.invokeOnCancellation { cts.cancel() }
        }
    }

    fun azimuthDegreesFlow(): Flow<Float> = callbackFlow {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sm == null) {
            close()
            return@callbackFlow
        }

        val rotVector = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnet = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasMagnet = false
        val rMat = FloatArray(9)
        val orient = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rMat, event.values)
                        SensorManager.getOrientation(rMat, orient)
                        val deg = ((Math.toDegrees(orient[0].toDouble()) + 360.0) % 360.0).toFloat()
                        trySend(deg)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, gravity, 0, 3)
                        hasGravity = true
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                        hasMagnet = true
                    }
                }
                if (rotVector == null && hasGravity && hasMagnet) {
                    if (SensorManager.getRotationMatrix(rMat, null, gravity, geomagnetic)) {
                        SensorManager.getOrientation(rMat, orient)
                        val deg = ((Math.toDegrees(orient[0].toDouble()) + 360.0) % 360.0).toFloat()
                        trySend(deg)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (rotVector != null) {
            sm.registerListener(listener, rotVector, SensorManager.SENSOR_DELAY_UI)
        } else {
            accel?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
            magnet?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        }

        awaitClose {
            sm.unregisterListener(listener)
        }
    }
}
