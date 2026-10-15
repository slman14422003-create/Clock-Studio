package com.clock.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** City names (English and Arabic) for the time zones the world clock offers. */
final class Cities {
    private Cities() {
    }

    static final class City {
        final String zone;
        final String en;
        final String ar;

        City(String zone, String en, String ar) {
            this.zone = zone;
            this.en = en;
            this.ar = ar;
        }

        String name() {
            return Lang.isAr() ? ar : en;
        }
    }

    private static final String[][] DATA = {
            {"Asia/Damascus", "Damascus", "دمشق"},
            {"Asia/Beirut", "Beirut", "بيروت"},
            {"Asia/Amman", "Amman", "عمّان"},
            {"Asia/Baghdad", "Baghdad", "بغداد"},
            {"Asia/Riyadh", "Riyadh", "الرياض"},
            {"Asia/Kuwait", "Kuwait City", "مدينة الكويت"},
            {"Asia/Dubai", "Dubai", "دبي"},
            {"Asia/Qatar", "Doha", "الدوحة"},
            {"Asia/Muscat", "Muscat", "مسقط"},
            {"Asia/Bahrain", "Manama", "المنامة"},
            {"Asia/Aden", "Sanaa", "صنعاء"},
            {"Asia/Jerusalem", "Jerusalem", "القدس"},
            {"Africa/Cairo", "Cairo", "القاهرة"},
            {"Africa/Khartoum", "Khartoum", "الخرطوم"},
            {"Africa/Tripoli", "Tripoli", "طرابلس"},
            {"Africa/Tunis", "Tunis", "تونس"},
            {"Africa/Algiers", "Algiers", "الجزائر"},
            {"Africa/Casablanca", "Casablanca", "الدار البيضاء"},
            {"Africa/Nairobi", "Nairobi", "نيروبي"},
            {"Africa/Addis_Ababa", "Addis Ababa", "أديس أبابا"},
            {"Africa/Lagos", "Lagos", "لاغوس"},
            {"Africa/Johannesburg", "Johannesburg", "جوهانسبرغ"},
            {"Europe/Istanbul", "Istanbul", "إسطنبول"},
            {"Asia/Tehran", "Tehran", "طهران"},
            {"Europe/London", "London", "لندن"},
            {"Europe/Dublin", "Dublin", "دبلن"},
            {"Europe/Lisbon", "Lisbon", "لشبونة"},
            {"Europe/Paris", "Paris", "باريس"},
            {"Europe/Madrid", "Madrid", "مدريد"},
            {"Europe/Berlin", "Berlin", "برلين"},
            {"Europe/Rome", "Rome", "روما"},
            {"Europe/Amsterdam", "Amsterdam", "أمستردام"},
            {"Europe/Brussels", "Brussels", "بروكسل"},
            {"Europe/Vienna", "Vienna", "فيينا"},
            {"Europe/Zurich", "Zurich", "زيورخ"},
            {"Europe/Stockholm", "Stockholm", "ستوكهولم"},
            {"Europe/Oslo", "Oslo", "أوسلو"},
            {"Europe/Warsaw", "Warsaw", "وارسو"},
            {"Europe/Athens", "Athens", "أثينا"},
            {"Europe/Kyiv", "Kyiv", "كييف"},
            {"Europe/Moscow", "Moscow", "موسكو"},
            {"Atlantic/Reykjavik", "Reykjavik", "ريكيافيك"},
            {"Asia/Karachi", "Karachi", "كراتشي"},
            {"Asia/Kabul", "Kabul", "كابل"},
            {"Asia/Tashkent", "Tashkent", "طشقند"},
            {"Asia/Kolkata", "Mumbai", "مومباي"},
            {"Asia/Dhaka", "Dhaka", "دكا"},
            {"Asia/Kathmandu", "Kathmandu", "كاتماندو"},
            {"Asia/Colombo", "Colombo", "كولومبو"},
            {"Asia/Bangkok", "Bangkok", "بانكوك"},
            {"Asia/Jakarta", "Jakarta", "جاكرتا"},
            {"Asia/Kuala_Lumpur", "Kuala Lumpur", "كوالالمبور"},
            {"Asia/Singapore", "Singapore", "سنغافورة"},
            {"Asia/Manila", "Manila", "مانيلا"},
            {"Asia/Hong_Kong", "Hong Kong", "هونغ كونغ"},
            {"Asia/Shanghai", "Beijing", "بكين"},
            {"Asia/Seoul", "Seoul", "سيول"},
            {"Asia/Tokyo", "Tokyo", "طوكيو"},
            {"Australia/Perth", "Perth", "بيرث"},
            {"Australia/Sydney", "Sydney", "سيدني"},
            {"Australia/Melbourne", "Melbourne", "ملبورن"},
            {"Pacific/Auckland", "Auckland", "أوكلاند"},
            {"Pacific/Honolulu", "Honolulu", "هونولولو"},
            {"America/Anchorage", "Anchorage", "أنكوريج"},
            {"America/Los_Angeles", "Los Angeles", "لوس أنجلوس"},
            {"America/Vancouver", "Vancouver", "فانكوفر"},
            {"America/Denver", "Denver", "دنفر"},
            {"America/Chicago", "Chicago", "شيكاغو"},
            {"America/Mexico_City", "Mexico City", "مكسيكو سيتي"},
            {"America/New_York", "New York", "نيويورك"},
            {"America/Toronto", "Toronto", "تورنتو"},
            {"America/Bogota", "Bogota", "بوغوتا"},
            {"America/Lima", "Lima", "ليما"},
            {"America/Caracas", "Caracas", "كاراكاس"},
            {"America/Santiago", "Santiago", "سانتياغو"},
            {"America/Sao_Paulo", "Sao Paulo", "ساو باولو"},
            {"America/Argentina/Buenos_Aires", "Buenos Aires", "بوينس آيرس"},
    };

    static List<City> all() {
        List<City> out = new ArrayList<>();
        for (String[] d : DATA) out.add(new City(d[0], d[1], d[2]));
        return out;
    }

    /** Matches the English / Arabic name and the zone id (so every tz database city can be found). */
    static List<City> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<City> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (City c : all()) {
            if (q.isEmpty() || c.en.toLowerCase(Locale.ROOT).contains(q) || c.ar.contains(q)
                    || c.zone.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(c);
                seen.add(c.zone);
            }
        }
        if (q.length() >= 2) {
            for (String id : TimeZone.getAvailableIDs()) {
                if (!id.contains("/") || id.startsWith("Etc/") || id.startsWith("SystemV/") || seen.contains(id)) continue;
                String name = derive(id);
                if (name.toLowerCase(Locale.ROOT).contains(q)) {
                    out.add(new City(id, name, name));
                    seen.add(id);
                }
            }
        }
        return out;
    }

    static String name(String zone) {
        for (String[] d : DATA) {
            if (d[0].equals(zone)) return Lang.isAr() ? d[2] : d[1];
        }
        return derive(zone);
    }

    private static String derive(String zone) {
        int i = zone.lastIndexOf('/');
        return (i >= 0 ? zone.substring(i + 1) : zone).replace('_', ' ');
    }
}
