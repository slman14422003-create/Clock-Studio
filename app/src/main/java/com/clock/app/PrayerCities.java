package com.clock.app;

import java.util.ArrayList;
import java.util.List;

/** Cities offered for manual prayer times (coordinates + time zone), Syria first. */
final class PrayerCities {
    private PrayerCities() {
    }

    static final class City {
        final String en, ar, tz;
        final double lat, lon;

        City(String en, String ar, double lat, double lon, String tz) {
            this.en = en;
            this.ar = ar;
            this.lat = lat;
            this.lon = lon;
            this.tz = tz;
        }

        String name() {
            return Lang.isAr() ? ar : en;
        }
    }

    private static final Object[][] D = {
            {"Damascus", "دمشق", 33.5138, 36.2765, "Asia/Damascus"},
            {"Aleppo", "حلب", 36.2021, 37.1343, "Asia/Damascus"},
            {"Homs", "حمص", 34.7324, 36.7137, "Asia/Damascus"},
            {"Hama", "حماة", 35.1318, 36.7578, "Asia/Damascus"},
            {"Latakia", "اللاذقية", 35.5317, 35.7900, "Asia/Damascus"},
            {"Tartus", "طرطوس", 34.8890, 35.8866, "Asia/Damascus"},
            {"Idlib", "إدلب", 35.9306, 36.6339, "Asia/Damascus"},
            {"Deir ez-Zor", "دير الزور", 35.3359, 40.1408, "Asia/Damascus"},
            {"Raqqa", "الرقة", 35.9528, 39.0079, "Asia/Damascus"},
            {"Daraa", "درعا", 32.6189, 36.1021, "Asia/Damascus"},
            {"As-Suwayda", "السويداء", 32.7090, 36.5695, "Asia/Damascus"},
            {"Al-Hasakah", "الحسكة", 36.5024, 40.7477, "Asia/Damascus"},
            {"Qamishli", "القامشلي", 37.0522, 41.2269, "Asia/Damascus"},
            {"Beirut", "بيروت", 33.8938, 35.5018, "Asia/Beirut"},
            {"Amman", "عمّان", 31.9454, 35.9284, "Asia/Amman"},
            {"Jerusalem", "القدس", 31.7683, 35.2137, "Asia/Jerusalem"},
            {"Gaza", "غزة", 31.5017, 34.4668, "Asia/Gaza"},
            {"Baghdad", "بغداد", 33.3152, 44.3661, "Asia/Baghdad"},
            {"Riyadh", "الرياض", 24.7136, 46.6753, "Asia/Riyadh"},
            {"Makkah", "مكة المكرمة", 21.3891, 39.8579, "Asia/Riyadh"},
            {"Madinah", "المدينة المنورة", 24.5247, 39.5692, "Asia/Riyadh"},
            {"Jeddah", "جدة", 21.4858, 39.1925, "Asia/Riyadh"},
            {"Kuwait City", "مدينة الكويت", 29.3759, 47.9774, "Asia/Kuwait"},
            {"Dubai", "دبي", 25.2048, 55.2708, "Asia/Dubai"},
            {"Abu Dhabi", "أبو ظبي", 24.4539, 54.3773, "Asia/Dubai"},
            {"Doha", "الدوحة", 25.2854, 51.5310, "Asia/Qatar"},
            {"Muscat", "مسقط", 23.5880, 58.3829, "Asia/Muscat"},
            {"Manama", "المنامة", 26.2285, 50.5860, "Asia/Bahrain"},
            {"Sanaa", "صنعاء", 15.3694, 44.1910, "Asia/Aden"},
            {"Cairo", "القاهرة", 30.0444, 31.2357, "Africa/Cairo"},
            {"Alexandria", "الإسكندرية", 31.2001, 29.9187, "Africa/Cairo"},
            {"Tripoli", "طرابلس", 32.8872, 13.1913, "Africa/Tripoli"},
            {"Tunis", "تونس", 36.8065, 10.1815, "Africa/Tunis"},
            {"Algiers", "الجزائر", 36.7538, 3.0588, "Africa/Algiers"},
            {"Rabat", "الرباط", 34.0209, -6.8416, "Africa/Casablanca"},
            {"Casablanca", "الدار البيضاء", 33.5731, -7.5898, "Africa/Casablanca"},
            {"Khartoum", "الخرطوم", 15.5007, 32.5599, "Africa/Khartoum"},
            {"Istanbul", "إسطنبول", 41.0082, 28.9784, "Europe/Istanbul"},
            {"Ankara", "أنقرة", 39.9334, 32.8597, "Europe/Istanbul"},
            {"Tehran", "طهران", 35.6892, 51.3890, "Asia/Tehran"},
            {"Karachi", "كراتشي", 24.8607, 67.0011, "Asia/Karachi"},
            {"Jakarta", "جاكرتا", -6.2088, 106.8456, "Asia/Jakarta"},
            {"Kuala Lumpur", "كوالالمبور", 3.1390, 101.6869, "Asia/Kuala_Lumpur"},
            {"London", "لندن", 51.5074, -0.1278, "Europe/London"},
            {"Paris", "باريس", 48.8566, 2.3522, "Europe/Paris"},
            {"Berlin", "برلين", 52.5200, 13.4050, "Europe/Berlin"},
            {"New York", "نيويورك", 40.7128, -74.0060, "America/New_York"},
    };

    static List<City> all() {
        List<City> out = new ArrayList<>();
        for (Object[] d : D) {
            out.add(new City((String) d[0], (String) d[1], ((Number) d[2]).doubleValue(),
                    ((Number) d[3]).doubleValue(), (String) d[4]));
        }
        return out;
    }
}
