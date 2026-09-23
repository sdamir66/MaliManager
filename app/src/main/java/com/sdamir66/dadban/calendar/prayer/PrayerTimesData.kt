data class PrayerTimesData(
    val fajr: String,        // اذان صبح
    val sunrise: String,     // طلوع آفتاب
    val dhuhr: String,       // اذان ظهر
    val asr: String,         // عصر
    val maghrib: String,     // اذان مغرب (= غروب آفتاب در این روش)
    val isha: String,        // اذان عشا
    val dateMillis: Long,
    val latitude: Double,
    val longitude: Double
)
