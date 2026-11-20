package com.jpm.hrms.geo;

import java.util.Locale;

/** A place on the map in decimal degrees. */
public record GeoPoint(double lat, double lng) {
    private static final double EARTH_RADIUS_KM = 6371.0088;

    public static boolean valid(double lat, double lng) {
        return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180 && !(lat == 0 && lng == 0);
    }

    /** Straight-line (great-circle) distance in kilometres, rounded to 0.1 km. Roads are usually longer. */
    public double kmTo(GeoPoint o) {
        double dLat = Math.toRadians(o.lat - lat);
        double dLng = Math.toRadians(o.lng - lng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(o.lat)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double km = 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a)));
        return Math.round(km * 10) / 10.0;
    }

    /** "11.042255,77.319373" as used in Google Maps links. */
    public String text() {
        return String.format(Locale.ROOT, "%.6f,%.6f", lat, lng);
    }
}
