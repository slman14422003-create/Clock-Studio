package com.clock.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import java.util.Locale;

/** Live qibla compass: true bearing and distance on the WGS-84 ellipsoid from the saved prayer location. */
public class QiblaActivity extends BaseActivity implements SensorEventListener {
    /** Centre of the Kaaba. */
    private static final double KAABA_LAT = 21.422487, KAABA_LON = 39.826206;

    private SensorManager sm;
    private Sensor rot, acc, mag;
    private CompassView compass;
    private TextView guide, hint;
    private LinearLayout rowNow;
    private double qibla = -1;
    private float declination;
    private float heading = Float.NaN;
    private boolean aligned;
    private final float[] grav = new float[3], geo = new float[3];
    private boolean hasAcc;
    private final float[] rm = new float[9], rm2 = new float[9], ori = new float[3];
    private int accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH;
    private long lastText;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        useGroupedBackground();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Header hd = new Header(this, getString(R.string.qibla));
        hd.addView(Ui.iconButton(this, R.drawable.ic_back, R.string.back, false, v -> finish()), 0,
                new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        ((LinearLayout.LayoutParams) hd.getChildAt(0).getLayoutParams()).setMarginEnd(Ui.dp(this, 10));
        root.addView(hd);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        hd.bindScroll(sv);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 30));
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        PrayerTimes.Cfg g = PrayerTimes.cfg(this);
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rot = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        acc = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mag = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        if (!g.configured) {
            col.addView(Ui.footnote(this, getString(R.string.qibla_need_loc)));
            TextView go = Ui.navText(this, getString(R.string.location), true);
            go.setOnClickListener(v -> {
                startActivity(new Intent(this, PrayerActivity.class));
                finish();
            });
            col.addView(go);
            return;
        }
        double[] geod = geodesic(g.lat, g.lon);
        qibla = geod[1];
        double distKm = geod[0] / 1000.0;
        declination = new GeomagneticField((float) g.lat, (float) g.lon, 0f, System.currentTimeMillis()).getDeclination();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(Glass.card(this, 28));
        card.setPadding(Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 16));
        compass = new CompassView(this, qibla);
        card.addView(compass, new LinearLayout.LayoutParams(-1, -2));
        guide = Ui.text(this, getString(R.string.qibla_hold_flat), 19, R.color.text_primary);
        guide.setTypeface(Ui.medium());
        guide.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-1, -2);
        gl.topMargin = Ui.dp(this, 8);
        card.addView(guide, gl);
        hint = Ui.text(this, "", 13, R.color.text_secondary);
        hint.setGravity(Gravity.CENTER);
        card.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 14));
        col.addView(card, cardLp);
        Anim.riseIn(card, 24, 0);

        String[] winds = getResources().getStringArray(R.array.compass_names);
        String wind = winds[(int) Math.round(qibla / 45.0) % 8];
        LinearLayout rows = Ui.group(this);
        Ui.addRow(rows, Ui.row(this, R.drawable.ic_compass, R.color.ok, getString(R.string.qibla_dir),
                getString(R.string.qibla_deg, Math.round(qibla)) + " · " + wind, false, null), true);
        Ui.addRow(rows, Ui.row(this, R.drawable.ic_globe, R.color.info, getString(R.string.qibla_distance),
                getString(R.string.qibla_km, String.format(Locale.US, "%,d", Math.round(distKm))), false, null), true);
        Ui.addRow(rows, Ui.row(this, R.drawable.ic_prayer, R.color.violet, getString(R.string.location),
                g.place, true, v -> startActivity(new Intent(this, PrayerActivity.class))), true);
        rowNow = Ui.row(this, R.drawable.ic_standby, R.color.warn, getString(R.string.qibla_now), "--", false, null);
        Ui.addRow(rows, rowNow, true);
        col.addView(rows);
        col.addView(Ui.footnote(this, getString(R.string.qibla_true_north)));

        if (rot == null && (acc == null || mag == null)) guide.setText(R.string.qibla_no_sensor);
        Anim.riseIn(rows, 20, 90);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sm == null || compass == null) return;
        if (rot != null) {
            sm.registerListener(this, rot, SensorManager.SENSOR_DELAY_GAME);
        } else if (acc != null && mag != null) {
            sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME);
            sm.registerListener(this, mag, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        if (sm != null) sm.unregisterListener(this);
        super.onPause();
    }

    @Override
    public void onAccuracyChanged(Sensor s, int a) {
        accuracy = a;
        if (hint != null) {
            hint.setText(a <= SensorManager.SENSOR_STATUS_ACCURACY_LOW ? R.string.qibla_calibrate : R.string.empty_text);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float az;
        int type = e.sensor.getType();
        if (type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rm, e.values);
            az = azimuth();
        } else if (type == Sensor.TYPE_ACCELEROMETER) {
            for (int i = 0; i < 3; i++) grav[i] = grav[i] * 0.8f + e.values[i] * 0.2f;
            hasAcc = true;
            return;
        } else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
            for (int i = 0; i < 3; i++) geo[i] = geo[i] * 0.8f + e.values[i] * 0.2f;
            if (!hasAcc || !SensorManager.getRotationMatrix(rm, null, grav, geo)) return;
            az = azimuth();
        } else {
            return;
        }
        float h = (az + declination + 360f) % 360f;
        if (Float.isNaN(heading)) heading = h;
        else heading = (heading + delta(heading, h) * 0.2f + 360f) % 360f;
        compass.setHeading(heading);

        float diff = delta(heading, (float) qibla);
        boolean now = Math.abs(diff) <= 3f;
        if (now != aligned) {
            aligned = now;
            compass.setAligned(now);
            if (now) {
                Ui.tick(compass);
                Anim.bounce(guide);
            }
        }
        long t = System.currentTimeMillis();
        if (t - lastText > 120) {
            lastText = t;
            guide.setTextColor(Ui.color(this, now ? R.color.ok : R.color.text_primary));
            int deg = Math.round(Math.abs(diff));
            guide.setText(now ? getString(R.string.qibla_aligned)
                    : getString(diff > 0 ? R.string.qibla_turn_right : R.string.qibla_turn_left, deg));
            if (rowNow != null) Ui.setValue(rowNow, Math.round(heading) + "°");
        }
    }

    /** Heading of the phone's top edge; when the phone stands upright the back camera direction is used instead. */
    private float azimuth() {
        SensorManager.getOrientation(rm, ori);
        if (Math.abs(ori[1]) > 1.1f || Math.abs(ori[2]) > 1.9f) {
            if (SensorManager.remapCoordinateSystem(rm, SensorManager.AXIS_X, SensorManager.AXIS_Z, rm2)) {
                SensorManager.getOrientation(rm2, ori);
            }
        }
        return (float) Math.toDegrees(ori[0]);
    }

    private static float delta(float from, float to) {
        float d = (to - from) % 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    /** {distance in metres, initial bearing in degrees from true north} on the WGS-84 ellipsoid (Vincenty inverse). */
    static double[] geodesic(double lat1d, double lon1d) {
        final double a = 6378137.0, f = 1 / 298.257223563, bb = (1 - f) * a;
        double L = Math.toRadians(KAABA_LON - lon1d);
        double u1 = Math.atan((1 - f) * Math.tan(Math.toRadians(lat1d)));
        double u2 = Math.atan((1 - f) * Math.tan(Math.toRadians(KAABA_LAT)));
        double sinU1 = Math.sin(u1), cosU1 = Math.cos(u1), sinU2 = Math.sin(u2), cosU2 = Math.cos(u2);
        double lambda = L, lambdaP;
        double sinSigma = 0, cosSigma = 0, sigma = 0, cosSqAlpha = 0, cos2SigmaM = 0, sinLambda, cosLambda;
        int iter = 0;
        do {
            sinLambda = Math.sin(lambda);
            cosLambda = Math.cos(lambda);
            sinSigma = Math.sqrt(Math.pow(cosU2 * sinLambda, 2) + Math.pow(cosU1 * sinU2 - sinU1 * cosU2 * cosLambda, 2));
            if (sinSigma == 0) return new double[]{0, 0};
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda;
            sigma = Math.atan2(sinSigma, cosSigma);
            double sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma;
            cosSqAlpha = 1 - sinAlpha * sinAlpha;
            cos2SigmaM = cosSqAlpha == 0 ? 0 : cosSigma - 2 * sinU1 * sinU2 / cosSqAlpha;
            double c = f / 16 * cosSqAlpha * (4 + f * (4 - 3 * cosSqAlpha));
            lambdaP = lambda;
            lambda = L + (1 - c) * f * sinAlpha * (sigma + c * sinSigma * (cos2SigmaM + c * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)));
        } while (Math.abs(lambda - lambdaP) > 1e-12 && ++iter < 100);
        if (iter >= 100) return sphere(lat1d, lon1d);
        double uSq = cosSqAlpha * (a * a - bb * bb) / (bb * bb);
        double A = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)));
        double B = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)));
        double dSigma = B * sinSigma * (cos2SigmaM + B / 4 * (cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)
                - B / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)));
        double s = bb * A * (sigma - dSigma);
        double az = Math.atan2(cosU2 * Math.sin(lambda), cosU1 * sinU2 - sinU1 * cosU2 * Math.cos(lambda));
        return new double[]{s, (Math.toDegrees(az) + 360.0) % 360.0};
    }

    private static double[] sphere(double lat, double lon) {
        double p1 = Math.toRadians(lat), p2 = Math.toRadians(KAABA_LAT);
        double dl = Math.toRadians(KAABA_LON - lon);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        double dp = p2 - p1;
        double h = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return new double[]{2 * 6371008.8 * Math.asin(Math.min(1.0, Math.sqrt(h))), (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0};
    }

    /** Dial that turns with the phone; the qibla needle and the Kaaba mark are fixed on the dial. */
    static final class CompassView extends View {
        private final double qibla;
        private float heading, glow;
        private boolean aligned;
        private ValueAnimator glowAnim;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String[] dirs;
        private final Path path = new Path();
        private final RectF box = new RectF();

        CompassView(Context c, double qibla) {
            super(c);
            this.qibla = qibla;
            dirs = c.getResources().getStringArray(R.array.compass_dirs);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }

        void setHeading(float h) {
            heading = h;
            invalidate();
        }

        void setAligned(boolean a) {
            aligned = a;
            if (glowAnim != null) glowAnim.cancel();
            glowAnim = ValueAnimator.ofFloat(glow, a ? 1f : 0f);
            glowAnim.setDuration(Anim.D_MED);
            glowAnim.setInterpolator(Anim.OUT);
            glowAnim.addUpdateListener(v -> {
                glow = (float) v.getAnimatedValue();
                invalidate();
            });
            glowAnim.start();
        }

        @Override
        protected void onMeasure(int w, int h) {
            int s = Math.min(MeasureSpec.getSize(w), Ui.dp(getContext(), 340));
            setMeasuredDimension(MeasureSpec.getSize(w), s);
        }

        @Override
        protected void onDraw(Canvas cv) {
            Context c = getContext();
            float d = Ui.dpf(c, 1);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float r = Math.min(cx, cy) - 16 * d;
            int ok = Ui.color(c, R.color.ok);
            int accent = ColorUtils.blendARGB(Ui.color(c, R.color.accent), ok, glow);

            // glow behind the dial when facing the qibla
            if (glow > 0.01f) {
                fill.setShader(new RadialGradient(cx, cy, r + 16 * d, new int[]{Ui.alpha(ok, 0), Ui.alpha(ok, (int) (110 * glow))},
                        new float[]{0.86f, 1f}, Shader.TileMode.CLAMP));
                cv.drawCircle(cx, cy, r + 16 * d, fill);
                fill.setShader(null);
            }
            fill.setColor(Ui.color(c, R.color.field));
            cv.drawCircle(cx, cy, r, fill);
            stroke.setStrokeWidth(2 * d);
            stroke.setColor(ColorUtils.blendARGB(Ui.color(c, R.color.stroke), ok, glow));
            cv.drawCircle(cx, cy, r, stroke);
            fill.setColor(Ui.color(c, R.color.surface));
            cv.drawCircle(cx, cy, r * 0.62f, fill);

            // fixed pointer for the direction the phone faces
            path.reset();
            path.moveTo(cx, cy - r + 6 * d);
            path.lineTo(cx - 8 * d, cy - r - 10 * d);
            path.lineTo(cx + 8 * d, cy - r - 10 * d);
            path.close();
            fill.setColor(accent);
            cv.drawPath(path, fill);

            cv.save();
            cv.rotate(-heading, cx, cy);
            for (int i = 0; i < 72; i++) {
                boolean big = i % 6 == 0;
                stroke.setStrokeWidth((big ? 2.2f : 1.2f) * d);
                stroke.setColor(Ui.color(c, big ? R.color.text_primary : R.color.text_hint));
                float len = (big ? 12 : 6) * d;
                cv.drawLine(cx, cy - r + 8 * d, cx, cy - r + 8 * d + len, stroke);
                cv.rotate(5f, cx, cy);
            }
            text.setTextSize(18 * d);
            for (int i = 0; i < 4; i++) {
                text.setColor(Ui.color(c, i == 0 ? R.color.bad : R.color.text_secondary));
                cv.drawText(dirs[i], cx, cy - r + 42 * d, text);
                cv.rotate(90f, cx, cy);
            }

            // qibla needle and Kaaba mark
            cv.save();
            cv.rotate((float) qibla, cx, cy);
            stroke.setStrokeWidth(4 * d);
            stroke.setColor(accent);
            cv.drawLine(cx, cy, cx, cy - r * 0.40f, stroke);
            path.reset();
            path.moveTo(cx, cy - r * 0.54f);
            path.lineTo(cx - 10 * d, cy - r * 0.38f);
            path.lineTo(cx + 10 * d, cy - r * 0.38f);
            path.close();
            fill.setColor(accent);
            cv.drawPath(path, fill);
            float kw = 15 * d * (1f + 0.12f * glow);
            float ky = cy - r * 0.76f;
            box.set(cx - kw, ky - kw, cx + kw, ky + kw);
            fill.setColor(0xFF1C1C1E);
            cv.drawRoundRect(box, 4 * d, 4 * d, fill);
            fill.setColor(0xFFD4AF37);
            cv.drawRect(box.left, box.top + kw * 0.5f, box.right, box.top + kw * 0.82f, fill);
            cv.restore();
            cv.restore();

            // centre hub and live heading
            fill.setColor(accent);
            cv.drawCircle(cx, cy, 7 * d, fill);
            fill.setColor(Ui.color(c, R.color.surface));
            cv.drawCircle(cx, cy, 3 * d, fill);
            text.setTextSize(15 * d);
            text.setColor(Ui.color(c, R.color.text_secondary));
            cv.drawText(Math.round(heading) + "°", cx, cy + r * 0.34f, text);
        }
    }
}
