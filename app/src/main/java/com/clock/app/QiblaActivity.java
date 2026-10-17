package com.clock.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
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

import java.util.Locale;

/** Live qibla compass: needs the saved prayer location and the phone's rotation sensor (no internet, no permission). */
public class QiblaActivity extends BaseActivity implements SensorEventListener {
    private static final double KAABA_LAT = 21.4225, KAABA_LON = 39.8262;

    private SensorManager sm;
    private Sensor rot, acc, mag;
    private CompassView compass;
    private TextView degText, distText, hint;
    private double qibla = -1;
    private double distKm;
    private float declination;
    private float heading = Float.NaN;
    private boolean aligned;
    private final float[] grav = new float[3], geo = new float[3];
    private boolean hasAcc, hasMag;
    private final float[] rm = new float[9], rm2 = new float[9], ori = new float[3];
    private int accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH;

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
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 30));
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
        qibla = bearing(g.lat, g.lon);
        distKm = distance(g.lat, g.lon);
        declination = new GeomagneticField((float) g.lat, (float) g.lon, 0f, System.currentTimeMillis()).getDeclination();

        compass = new CompassView(this, qibla);
        col.addView(compass, new LinearLayout.LayoutParams(-1, -2));

        degText = Ui.text(this, getString(R.string.qibla_deg, Math.round(qibla)), 22, R.color.text_primary);
        degText.setTypeface(Ui.medium());
        degText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, -2);
        dl.topMargin = Ui.dp(this, 14);
        col.addView(degText, dl);
        distText = Ui.text(this, getString(R.string.qibla_dist, String.format(Locale.US, "%,d", Math.round(distKm))), 15, R.color.text_secondary);
        distText.setGravity(Gravity.CENTER);
        col.addView(distText);
        hint = Ui.text(this, "", 14, R.color.text_secondary);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-2, -2);
        hl.topMargin = Ui.dp(this, 12);
        col.addView(hint, hl);
        if (rot == null && (acc == null || mag == null)) hint.setText(R.string.qibla_no_sensor);
        else hint.setText(R.string.qibla_hold_flat);
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
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float az;
        if (e.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rm, e.values);
            az = azimuth();
        } else if (e.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            for (int i = 0; i < 3; i++) grav[i] = grav[i] * 0.8f + e.values[i] * 0.2f;
            hasAcc = true;
            return;
        } else if (e.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            for (int i = 0; i < 3; i++) geo[i] = geo[i] * 0.8f + e.values[i] * 0.2f;
            hasMag = true;
            if (!hasAcc || !SensorManager.getRotationMatrix(rm, null, grav, geo)) return;
            az = azimuth();
        } else {
            return;
        }
        float h = (az + declination + 360f) % 360f;
        if (Float.isNaN(heading)) heading = h;
        else heading = (heading + delta(heading, h) * 0.18f + 360f) % 360f;
        compass.setHeading(heading);
        boolean now = Math.abs(delta(heading, (float) qibla)) <= 3f;
        if (now != aligned) {
            aligned = now;
            compass.setAligned(now);
            if (now) Ui.tick(compass);
            hint.setText(now ? R.string.qibla_aligned
                    : accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW ? R.string.qibla_calibrate : R.string.qibla_hold_flat);
        } else if (!now && accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
            hint.setText(R.string.qibla_calibrate);
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

    static double bearing(double lat, double lon) {
        double p1 = Math.toRadians(lat), p2 = Math.toRadians(KAABA_LAT);
        double dl = Math.toRadians(KAABA_LON - lon);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0;
    }

    static double distance(double lat, double lon) {
        double r = 6371.0;
        double p1 = Math.toRadians(lat), p2 = Math.toRadians(KAABA_LAT);
        double dp = p2 - p1, dl = Math.toRadians(KAABA_LON - lon);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /** Dial that turns with the phone; the qibla arrow and the Kaaba mark are fixed on the dial. */
    static final class CompassView extends View {
        private final double qibla;
        private float heading;
        private boolean aligned;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String[] dirs;
        private final Path arrow = new Path();
        private final RectF box = new RectF();

        CompassView(Context c, double qibla) {
            super(c);
            this.qibla = qibla;
            dirs = c.getResources().getStringArray(R.array.compass_dirs);
            stroke.setStyle(Paint.Style.STROKE);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }

        void setHeading(float h) {
            heading = h;
            invalidate();
        }

        void setAligned(boolean a) {
            aligned = a;
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            int s = MeasureSpec.getSize(w);
            setMeasuredDimension(s, s);
        }

        @Override
        protected void onDraw(Canvas cv) {
            Context c = getContext();
            float d = Ui.dpf(c, 1);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float r = Math.min(cx, cy) - 14 * d;
            int accent = Ui.color(c, aligned ? R.color.ok : R.color.accent);

            fill.setColor(Ui.color(c, R.color.surface));
            cv.drawCircle(cx, cy, r, fill);
            stroke.setStrokeWidth(aligned ? 5 * d : 2 * d);
            stroke.setColor(aligned ? accent : Ui.color(c, R.color.stroke));
            cv.drawCircle(cx, cy, r, stroke);

            // fixed marker for the phone's own direction
            Path top = new Path();
            top.moveTo(cx, cy - r + 4 * d);
            top.lineTo(cx - 9 * d, cy - r - 12 * d);
            top.lineTo(cx + 9 * d, cy - r - 12 * d);
            top.close();
            fill.setColor(Ui.color(c, R.color.text_secondary));
            cv.drawPath(top, fill);

            cv.save();
            cv.rotate(-heading, cx, cy);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 72; i++) {
                boolean big = i % 6 == 0;
                stroke.setStrokeWidth((big ? 2.2f : 1.2f) * d);
                stroke.setColor(Ui.color(c, big ? R.color.text_primary : R.color.text_hint));
                float len = (big ? 13 : 7) * d;
                cv.drawLine(cx, cy - r + 6 * d, cx, cy - r + 6 * d + len, stroke);
                cv.rotate(5f, cx, cy);
            }
            text.setTextSize(17 * d);
            for (int i = 0; i < 4; i++) {
                text.setColor(Ui.color(c, i == 0 ? R.color.bad : R.color.text_secondary));
                cv.drawText(dirs[i], cx, cy - r + 42 * d, text);
                cv.rotate(90f, cx, cy);
            }

            // qibla direction
            cv.save();
            cv.rotate((float) qibla, cx, cy);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeWidth(4 * d);
            stroke.setColor(accent);
            cv.drawLine(cx, cy, cx, cy - r * 0.50f, stroke);
            fill.setColor(accent);
            arrow.reset();
            arrow.moveTo(cx, cy - r * 0.62f);
            arrow.lineTo(cx - 11 * d, cy - r * 0.46f);
            arrow.lineTo(cx + 11 * d, cy - r * 0.46f);
            arrow.close();
            cv.drawPath(arrow, fill);
            float kw = 15 * d;
            box.set(cx - kw, cy - r * 0.78f - kw, cx + kw, cy - r * 0.78f + kw);
            fill.setColor(0xFF1C1C1E);
            cv.drawRoundRect(box, 4 * d, 4 * d, fill);
            fill.setColor(0xFFD4AF37);
            cv.drawRect(box.left, box.top + kw * 0.55f, box.right, box.top + kw * 0.85f, fill);
            cv.restore();
            cv.restore();

            fill.setColor(accent);
            cv.drawCircle(cx, cy, 6 * d, fill);
        }
    }
}
