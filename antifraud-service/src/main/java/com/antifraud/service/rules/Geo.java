package com.antifraud.service.rules;

/** Distances on the Earth's surface. */
public final class Geo {

    /** Mean radius of the Earth, km. */
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private Geo() {
    }

    /**
     * Great-circle distance by the haversine formula. The Earth is treated as a sphere, which is
     * off by at most 0.5 %: far below what matters for telling a drive from a flight.
     */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double h = Math.pow(Math.sin(deltaPhi / 2), 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.pow(Math.sin(deltaLambda / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }
}
