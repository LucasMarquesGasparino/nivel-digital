package br.com.lucasgasparino.niveldigital;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor sensor;
    private LevelView levelView;
    private float xTilt;
    private float yTilt;
    private boolean hasSensor;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(0xff101318);
        window.setNavigationBarColor(0xff101318);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        levelView = new LevelView(this);
        setContentView(levelView);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        if (sensor == null) sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        hasSensor = sensor != null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensor != null) sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float alpha = 0.18f;
        float gx = event.values[0];
        float gy = event.values[1];
        float gz = event.values[2];
        float norm = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
        if (norm > 0) {
            float nextX = (float) Math.toDegrees(Math.atan2(gx, Math.sqrt(gy * gy + gz * gz)));
            float nextY = (float) Math.toDegrees(Math.atan2(-gy, Math.sqrt(gx * gx + gz * gz)));
            xTilt += (nextX - xTilt) * alpha;
            yTilt += (nextY - yTilt) * alpha;
            levelView.invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private static class LevelView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;
        private final MainActivity activity;

        LevelView(MainActivity activity) {
            super(activity);
            this.activity = activity;
            density = getResources().getDisplayMetrics().density;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(0xff101318);
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.43f;
            float radius = Math.min(getWidth() * 0.39f, 155f * density);
            boolean level = activity.hasSensor && Math.abs(activity.xTilt) < 0.7f && Math.abs(activity.yTilt) < 0.7f;
            int accent = level ? 0xff55c98a : 0xfff3b84b;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f * density);
            paint.setColor(0xff39414b);
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setStrokeWidth(1f * density);
            canvas.drawCircle(cx, cy, radius * 0.68f, paint);
            canvas.drawLine(cx - radius, cy, cx + radius, cy, paint);
            canvas.drawLine(cx, cy - radius, cx, cy + radius, paint);
            paint.setStrokeWidth(1.5f * density);
            paint.setColor(accent);
            canvas.drawCircle(cx, cy, radius * 0.34f, paint);

            float maxOffset = radius * 0.55f;
            float dotX = cx + clamp(activity.yTilt / 25f, -1f, 1f) * maxOffset;
            float dotY = cy + clamp(activity.xTilt / 25f, -1f, 1f) * maxOffset;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(accent);
            canvas.drawCircle(dotX, dotY, 10f * density, paint);
            paint.setColor(0x4455c98a);
            canvas.drawCircle(dotX, dotY, 18f * density, paint);

            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            paint.setTextSize(17f * density);
            paint.setColor(0xffc7cdd5);
            canvas.drawText(level ? "NIVELADO" : activity.hasSensor ? "NÍVEL DIGITAL" : "SENSOR INDISPONÍVEL",
                    cx, cy + radius + 48f * density, paint);

            paint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            paint.setTextSize(34f * density);
            paint.setColor(0xfff3f5f7);
            canvas.drawText(String.format(java.util.Locale.getDefault(), "%.1f°", activity.xTilt),
                    cx, cy + radius + 105f * density, paint);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            paint.setTextSize(14f * density);
            paint.setColor(0xff87909c);
            canvas.drawText("frente / trás", cx, cy + radius + 129f * density, paint);

            float footerY = cy + radius + 184f * density;
            paint.setTextSize(13f * density);
            paint.setColor(0xffb7bec8);
            canvas.drawText(String.format(java.util.Locale.getDefault(), "LATERAL  %+.1f°", activity.yTilt), cx, footerY, paint);
        }

        private float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
