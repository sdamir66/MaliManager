package com.sdamir66.dadban.calendar.prayer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class CityLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

object LocationHelper {

    // لیست شهرهای ایران
    val iranianCities = listOf(
        CityLocation("تهران", 35.6892, 51.3890),
        CityLocation("مشهد", 36.2605, 59.6168),
        CityLocation("اصفهان", 32.6546, 51.6680),
        CityLocation("کرج", 35.8400, 50.9391),
        CityLocation("شیراز", 29.5918, 52.5837),
        CityLocation("تبریز", 38.0800, 46.2919),
        CityLocation("قم", 34.6416, 50.8746),
        CityLocation("اهواز", 31.3183, 48.6706),
        CityLocation("کرمانشاه", 34.3142, 47.0650),
        CityLocation("ارومیه", 37.5527, 45.0761),
        CityLocation("رشت", 37.2808, 49.5832),
        CityLocation("زاهدان", 29.4963, 60.8629),
        CityLocation("همدان", 34.7983, 48.5148),
        CityLocation("کرمان", 30.2839, 57.0834),
        CityLocation("یزد", 31.8974, 54.3569),
        CityLocation("اردبیل", 38.2498, 48.2933),
        CityLocation("بندرعباس", 27.1865, 56.2808),
        CityLocation("اراک", 34.0917, 49.6892),
        CityLocation("اسلام‌شهر", 35.5550, 51.2350),
        CityLocation("زنجان", 36.6736, 48.4787),
        CityLocation("ساری", 36.5633, 53.0601),
        CityLocation("بوشهر", 28.9234, 50.8200),
        CityLocation("گرگان", 36.8456, 54.4393),
        CityLocation("قزوین", 36.2688, 50.0041),
        CityLocation("سنندج", 35.3113, 46.9960),
        CityLocation("خرم‌آباد", 33.4878, 48.3558),
        CityLocation("بیرجند", 32.8649, 59.2262),
        CityLocation("شهرکرد", 32.3256, 50.8644),
        CityLocation("یاسوج", 30.6682, 51.5880),
        CityLocation("ایلام", 33.6374, 46.4227),
        CityLocation("بجنورد", 37.4747, 57.3290),
        CityLocation("سمنان", 35.5729, 53.3971),
        CityLocation("زابل", 31.0283, 61.4978)
    )

    fun findCity(name: String): CityLocation? =
        iranianCities.find { it.name == name }

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * گرفتن موقعیت فعلی با GPS
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Pair<Double, Double>? {
        if (!hasLocationPermission(context)) return null

        val client = LocationServices.getFusedLocationProviderClient(context)
        return suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        cont.resume(location.latitude to location.longitude)
                    } else {
                        cont.resume(null)
                    }
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        }
    }
}
