package com.clock.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.atomic.AtomicBoolean;

/** One-shot coarse location without Google services: last known fix first, then a single fresh update. */
final class PrayerLoc {
    private PrayerLoc() {
    }

    interface Cb {
        void on(double lat, double lon);

        void fail();
    }

    static boolean has(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static void locate(Context c, final Cb cb) {
        final AtomicBoolean done = new AtomicBoolean();
        final Handler main = new Handler(Looper.getMainLooper());
        final Runnable timeout = () -> {
            if (done.compareAndSet(false, true)) cb.fail();
        };
        try {
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) {
                cb.fail();
                return;
            }
            Location best = null;
            for (String p : lm.getProviders(true)) {
                try {
                    Location l = lm.getLastKnownLocation(p);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                } catch (SecurityException ignored) {
                }
            }
            if (best != null) {
                cb.on(best.getLatitude(), best.getLongitude());
                return;
            }
            String prov = null;
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) prov = LocationManager.NETWORK_PROVIDER;
            else if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) prov = LocationManager.GPS_PROVIDER;
            if (prov == null) {
                cb.fail();
                return;
            }
            main.postDelayed(timeout, 20000);
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(prov, null, c.getMainExecutor(), loc -> {
                    if (!done.compareAndSet(false, true)) return;
                    main.removeCallbacks(timeout);
                    if (loc != null) cb.on(loc.getLatitude(), loc.getLongitude());
                    else cb.fail();
                });
            } else {
                lm.requestSingleUpdate(prov, new LocationListener() {
                    @Override
                    public void onLocationChanged(Location l) {
                        if (!done.compareAndSet(false, true)) return;
                        main.removeCallbacks(timeout);
                        cb.on(l.getLatitude(), l.getLongitude());
                    }

                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {
                    }

                    @Override
                    public void onProviderEnabled(String provider) {
                    }

                    @Override
                    public void onProviderDisabled(String provider) {
                    }
                }, Looper.getMainLooper());
            }
        } catch (SecurityException e) {
            if (done.compareAndSet(false, true)) cb.fail();
        }
    }
}
