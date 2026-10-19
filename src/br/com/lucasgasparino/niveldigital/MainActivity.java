package br.com.lucasgasparino.niveldigital;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements SensorEventListener, RotatingLayout.ShapeListener {
    private static final int BUF = 64;
    private static final long HOLD_LOOKBACK_MS = 300;
    private static final long HOLD_MAX_TOUCH_AGE_MS = 5000;
    private static final long HOLD_MAX_SAMPLE_GAP_MS = 1000;
    private static final long QUARTER_CONFIRM_MS = 300;
    private static final long SETTLE_MS = 600;
    private static final long FEEDBACK_COOLDOWN_MS = 1500;
    private static final long VIBRATE_IGNORE_MS = 200;
    private static final long WATCHDOG_MS = 2500;
    private static final long CAL_WAIT_MS = 1000;
    private static final long CAL_SAMPLE_MS = 2000;
    private static final long CAL_TIMEOUT_MS = 10000;
    private static final long CAL_TICK_MS = 100;
    private static final double CAL_STILL_DEG = 0.5;
    private static final double CAL_MAX_OFFSET_DEG = 10.0;

    private static final String STATE_QUARTER = "quarter";
    private static final String STATE_HELD = "held";
    private static final String STATE_HELD_MODE = "held_mode";
    private static final String STATE_HELD_VALUES = "held_values";

    private LevelSettings settings;
    private LevelView.Palette pal;
    private SensorManager sensorManager;
    private Sensor sensor;
    private boolean sensorMissing;
    private Vibrator vibrator;
    private ToneGenerator tone;
    private boolean soundBroken;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int displayRotation;
    private String versionName = "?";

    // Interface.
    private RotatingLayout root;
    private LinearLayout controls;
    private LinearLayout modeRow;
    private LinearLayout actionRow;
    private LevelView levelView;
    private Button btnAuto;
    private Button btnSurface;
    private Button btnEdge;
    private Button btnHold;
    private Button btnCalibrate;
    private String lastDescription = "";
    private long touchDownAt = -1;

    // Leitura e filtro.
    private final LevelMath.Filter filter = new LevelMath.Filter();
    private final LevelMath.Reading live = new LevelMath.Reading();
    private final LevelMath.Reading heldReading = new LevelMath.Reading();
    private final double[] bufX = new double[BUF];
    private final double[] bufY = new double[BUF];
    private final double[] bufZ = new double[BUF];
    private final long[] bufT = new long[BUF];
    private int bufNext;
    private int bufCount;
    private long lastEventTs;
    private boolean hasData;
    private double fastTilt = 90.0;
    private int modePref;
    private boolean surfaceNow;
    private boolean autoSurface;
    private int quarter;
    private int pendingQuarter = -1;
    private long pendingSince;
    private boolean held;
    private boolean levelKnown;
    private boolean level;
    private long resumeAt;
    private long lastFeedbackAt = -100000L;
    private long ignoreSensorUntil;
    private String accuracyHint;
    private String watchdogHint;

    // Valores exibidos (passo de 0,1 para nao tremer).
    private final Shown shBig = new Shown();
    private final Shown shA = new Shown();
    private final Shown shB = new Shown();
    private final Shown shEdge = new Shown();
    private final Shown shLean = new Shown();

    // Calibracao.
    private int calPhase;
    private boolean calSurface;
    private int calQuarter;
    private long calPhaseStart;
    private long calFirstAt;
    private double calSumX;
    private double calSumY;
    private double calSumZ;
    private int calCount;
    private double calMinA;
    private double calMaxA;
    private double calMinB;
    private double calMaxB;

    private final Runnable watchdogTask = new Runnable() {
        @Override
        public void run() {
            if (!hasData) {
                watchdogHint = "Sem leituras do sensor. Feche e abra o app.";
                render();
            }
        }
    };

    private final Runnable calTickTask = new Runnable() {
        @Override
        public void run() {
            onCalTick();
        }
    };

    private static final class Shown {
        private double value;
        private boolean has;

        double step(double v) {
            value = LevelMath.stepDisplay(value, v, has);
            has = true;
            return value;
        }

        void reset() {
            value = 0;
            has = false;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new LevelSettings(this);
        pal = LevelView.Palette.of(this);
        modePref = settings.mode();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (sensor == null) {
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        }
        sensorMissing = sensor == null;
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = "?";
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        if (!night) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        buildUi();
        if (state != null) {
            restoreState(state);
        }
        updateButtons();
        render();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void buildUi() {
        root = new RotatingLayout(this, this);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(getColor(R.color.bg));

        levelView = new LevelView(this);
        levelView.setSensorMissing(sensorMissing);
        content.addView(levelView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        controls = new LinearLayout(this);
        controls.setPadding(dp(12), dp(8), dp(12), dp(12));
        modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        btnAuto = button("Auto", v -> setMode(LevelMath.MODE_AUTO));
        btnSurface = button("Superfície", v -> setMode(LevelMath.MODE_SURFACE));
        btnEdge = button("Borda", v -> setMode(LevelMath.MODE_EDGE));
        btnHold = button("Travar", v -> {
            long now = SystemClock.uptimeMillis();
            boolean fresh = touchDownAt > 0 && now - touchDownAt < HOLD_MAX_TOUCH_AGE_MS;
            toggleHold(fresh ? touchDownAt : now);
        });
        btnHold.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                touchDownAt = e.getEventTime();
            }
            return false;
        });
        btnCalibrate = button("Calibrar", v -> {
            if (calPhase != 0) {
                cancelCalibration(null);
            } else {
                showCalibrateDialog();
            }
        });
        Button btnSettings = button("Ajustes", v -> showSettingsDialog());

        addCell(modeRow, btnAuto);
        addCell(modeRow, btnSurface);
        addCell(modeRow, btnEdge);
        addCell(actionRow, btnHold);
        addCell(actionRow, btnCalibrate);
        addCell(actionRow, btnSettings);

        controls.addView(modeRow);
        controls.addView(actionRow);
        content.addView(controls, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        root.setContent(content);
        setContentView(root);
        applyShape(false);
    }

    private Button button(String text, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setSingleLine(true);
        b.setAutoSizeTextTypeUniformWithConfiguration(11, 15, 1, TypedValue.COMPLEX_UNIT_SP);
        b.setMinHeight(dp(52));
        b.setStateListAnimator(null);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(4), 0, dp(4), 0);
        GradientDrawable fill = new GradientDrawable();
        fill.setCornerRadius(dp(14));
        fill.setColor(pal.frasco);
        fill.setStroke(dp(1), pal.linhas);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33808080), fill, null));
        b.setTag(fill);
        b.setOnClickListener(onClick);
        return b;
    }

    private void addCell(LinearLayout row, Button b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        row.addView(b, lp);
    }

    /** Retrato: botoes em duas linhas. Paisagem: as duas linhas lado a lado (seis botoes). */
    private void applyShape(boolean landscape) {
        controls.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        LinearLayout.LayoutParams a = landscape
                ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        LinearLayout.LayoutParams b = landscape
                ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        modeRow.setLayoutParams(a);
        actionRow.setLayoutParams(b);
    }

    @Override
    public void onShapeChanged(boolean landscape) {
        if (controls != null) {
            applyShape(landscape);
        }
    }

    private void restoreState(Bundle s) {
        quarter = LevelMath.wrapQuarter(s.getInt(STATE_QUARTER, 0));
        root.setQuarter(quarter);
        if (s.getBoolean(STATE_HELD, false)) {
            double[] v = s.getDoubleArray(STATE_HELD_VALUES);
            if (v != null && v.length == 6) {
                heldReading.mode = s.getInt(STATE_HELD_MODE, LevelMath.MODE_SURFACE);
                heldReading.xu = v[0];
                heldReading.yu = v[1];
                heldReading.total = v[2];
                heldReading.edge = v[3];
                heldReading.lean = v[4];
                heldReading.tiltFromFlat = v[5];
                held = true;
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_QUARTER, quarter);
        out.putBoolean(STATE_HELD, held);
        if (held) {
            out.putInt(STATE_HELD_MODE, heldReading.mode);
            out.putDoubleArray(STATE_HELD_VALUES, new double[] {
                heldReading.xu, heldReading.yu, heldReading.total,
                heldReading.edge, heldReading.lean, heldReading.tiltFromFlat});
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        displayRotation = getWindowManager().getDefaultDisplay().getRotation();
        filter.reset();
        hasData = false;
        levelKnown = false;
        lastEventTs = 0;
        resumeAt = SystemClock.uptimeMillis();
        watchdogHint = null;
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
            handler.postDelayed(watchdogTask, WATCHDOG_MS);
        }
        updateButtons();
        render();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        handler.removeCallbacksAndMessages(null);
        calPhase = 0;
        releaseTone();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float[] v = event.values;
        if (v == null || v.length < 3) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now < ignoreSensorUntil) {
            return;
        }
        double rx = v[0];
        double ry = v[1];
        double rz = v[2];
        if (!LevelMath.finite(rx) || !LevelMath.finite(ry) || !LevelMath.finite(rz)) {
            return;
        }
        double sx = LevelMath.toU(displayRotation, rx, ry);
        double sy = LevelMath.toV(displayRotation, rx, ry);
        double dt = lastEventTs == 0 ? -1.0 : (event.timestamp - lastEventTs) * 1e-9;
        lastEventTs = event.timestamp;
        filter.step(sx, sy, rz, dt);
        if (!filter.init) {
            return;
        }
        hasData = true;
        watchdogHint = null;
        double[] f = filter.fast;
        fastTilt = LevelMath.tiltFromFlat(f[0], f[1], f[2]);
        if (!held) {
            updateQuarter(f, now);
            resolveMode(now);
        }
        storeSample(now);
        if (calPhase == 2) {
            accumulateCalibration(f);
        }
        if (!held) {
            computeLive();
            updateLevelState(now);
        }
        render();
    }

    @Override
    public void onAccuracyChanged(Sensor s, int accuracy) {
        String hint = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
                ? "Leitura do sensor pouco confiável" : null;
        if (hint == null ? accuracyHint != null : !hint.equals(accuracyHint)) {
            accuracyHint = hint;
            render();
        }
    }

    /** Quarto com confirmacao de 300 ms. Congelado enquanto travado. */
    private void updateQuarter(double[] f, long now) {
        int cand = LevelMath.updateQuarter(quarter, f[0], f[1], f[2]);
        if (cand == quarter) {
            pendingQuarter = -1;
            return;
        }
        if (cand != pendingQuarter) {
            pendingQuarter = cand;
            pendingSince = now;
            return;
        }
        if (now - pendingSince >= QUARTER_CONFIRM_MS) {
            quarter = cand;
            pendingQuarter = -1;
            root.setQuarter(quarter);
        }
    }

    private void resolveMode(long now) {
        boolean previous = surfaceNow;
        if (modePref == LevelMath.MODE_AUTO) {
            autoSurface = LevelMath.autoIsSurface(autoSurface, fastTilt);
            surfaceNow = autoSurface;
        } else {
            surfaceNow = modePref == LevelMath.MODE_SURFACE;
        }
        if (surfaceNow != previous) {
            levelKnown = false;
            resetDisplay();
        }
    }

    private void storeSample(long now) {
        double[] s = filter.slow;
        bufX[bufNext] = s[0];
        bufY[bufNext] = s[1];
        bufZ[bufNext] = s[2];
        bufT[bufNext] = now;
        bufNext = (bufNext + 1) % BUF;
        if (bufCount < BUF) {
            bufCount++;
        }
    }

    private void computeLive() {
        double[] s = filter.slow;
        int mode = surfaceNow ? LevelMath.MODE_SURFACE : LevelMath.MODE_EDGE;
        LevelMath.compute(live, s[0], s[1], s[2], quarter, mode,
                settings.surfX(), settings.surfY(), settings.edgeCal(quarter));
    }

    private void updateLevelState(long now) {
        double mag = surfaceNow ? live.total : Math.abs(live.edge);
        double tol = settings.tolerance();
        if (!levelKnown) {
            level = LevelMath.updateLevel(false, mag, tol);
            levelKnown = true;
            return;
        }
        boolean was = level;
        level = LevelMath.updateLevel(was, mag, tol);
        if (level && !was && canFeedback(now)) {
            feedback(now);
            levelView.announceForAccessibility("Nivelado");
        }
    }

    private boolean canFeedback(long now) {
        return calPhase == 0
                && now - resumeAt >= SETTLE_MS
                && now - lastFeedbackAt >= FEEDBACK_COOLDOWN_MS;
    }

    private void feedback(long now) {
        lastFeedbackAt = now;
        if (settings.sound()) {
            playTone();
        }
        if (settings.vibrate()) {
            vibrateOnce(now);
        }
    }

    private void playTone() {
        if (soundBroken) {
            return;
        }
        try {
            if (tone == null) {
                tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
            }
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
        } catch (RuntimeException e) {
            soundBroken = true;
            releaseTone();
        }
    }

    private void releaseTone() {
        if (tone != null) {
            try {
                tone.release();
            } catch (RuntimeException ignored) {
                // Nada a fazer: o gerador ja nao existe.
            }
            tone = null;
        }
    }

    private void vibrateOnce(long now) {
        if (vibrator == null) {
            return;
        }
        try {
            if (!vibrator.hasVibrator()) {
                return;
            }
            vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE));
            ignoreSensorUntil = now + VIBRATE_IGNORE_MS;
        } catch (RuntimeException ignored) {
            // Vibracao indisponivel neste aparelho: segue sem ela.
        }
    }

    private void setMode(int m) {
        if (modePref == m) {
            return;
        }
        modePref = m;
        settings.setMode(m);
        held = false;
        if (m == LevelMath.MODE_AUTO) {
            autoSurface = surfaceNow;
        }
        if (calPhase != 0) {
            cancelCalibration(null);
        }
        levelKnown = false;
        resetDisplay();
        updateButtons();
        render();
    }

    private void toggleHold(long downTime) {
        if (held) {
            unhold();
            return;
        }
        if (!hasData || sensorMissing || bufCount == 0 || calPhase != 0) {
            return;
        }
        long target = downTime - HOLD_LOOKBACK_MS;
        int pick = (bufNext - 1 + BUF) % BUF;
        long best = Long.MAX_VALUE;
        for (int i = 0; i < bufCount; i++) {
            long d = Math.abs(bufT[i] - target);
            if (d < best) {
                best = d;
                pick = i;
            }
        }
        if (best > HOLD_MAX_SAMPLE_GAP_MS) {
            pick = (bufNext - 1 + BUF) % BUF;
        }
        LevelMath.compute(heldReading, bufX[pick], bufY[pick], bufZ[pick], quarter,
                surfaceNow ? LevelMath.MODE_SURFACE : LevelMath.MODE_EDGE,
                settings.surfX(), settings.surfY(), settings.edgeCal(quarter));
        held = true;
        updateButtons();
        render();
    }

    private void unhold() {
        held = false;
        levelKnown = false;
        updateButtons();
        render();
    }

    private void resetDisplay() {
        shBig.reset();
        shA.reset();
        shB.reset();
        shEdge.reset();
        shLean.reset();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.getRepeatCount() == 0) {
                toggleHold(event.getDownTime());
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void render() {
        boolean showData = held || hasData;
        LevelMath.Reading r = held ? heldReading : live;
        if (!held && !hasData) {
            live.mode = modePref == LevelMath.MODE_EDGE ? LevelMath.MODE_EDGE : LevelMath.MODE_SURFACE;
        }
        int unit = settings.unit();
        String suffix = unit == LevelMath.UNIT_PERCENT ? "%" : "°";
        String big;
        String sub1;
        String sub2;
        if (!showData) {
            big = "--";
            sub1 = "";
            sub2 = "";
        } else if (r.mode == LevelMath.MODE_SURFACE) {
            big = LevelMath.format1(shBig.step(LevelMath.toUnit(r.total, unit))) + suffix;
            sub1 = "Lateral " + LevelMath.formatSigned1(shA.step(LevelMath.toUnit(r.xu, unit))) + suffix
                    + "   Frente/trás " + LevelMath.formatSigned1(shB.step(LevelMath.toUnit(r.yu, unit)))
                    + suffix;
            sub2 = "Positivo: direita / frente mais alta";
        } else {
            big = LevelMath.format1(shEdge.step(LevelMath.toUnit(Math.abs(r.edge), unit))) + suffix;
            if (level) {
                sub1 = "Nivelado";
            } else {
                sub1 = r.edge > 0 ? "Direita mais alta" : "Esquerda mais alta";
            }
            sub2 = "Prumo " + LevelMath.format1(shLean.step(LevelMath.toUnit(Math.abs(r.lean), unit))) + suffix;
        }

        boolean calibrated = surfaceNow ? settings.hasSurfaceCal() : settings.hasEdgeCal(quarter);
        String status;
        if (calPhase == 1) {
            status = "SOLTE O APARELHO…";
        } else if (calPhase == 2) {
            status = "CALIBRANDO " + calibrationPercent() + "%";
        } else if (held) {
            status = "TRAVADO";
        } else if (!hasData) {
            status = "AGUARDANDO SENSOR…";
        } else if (level) {
            status = "NIVELADO";
        } else {
            String name = surfaceNow ? "SUPERFÍCIE" : "BORDA";
            if (modePref == LevelMath.MODE_AUTO) {
                name = "AUTO · " + name;
            }
            status = name + (calibrated ? " · CALIBRADO" : "");
        }

        String hint = null;
        if (!held && hasData) {
            if (modePref == LevelMath.MODE_SURFACE && fastTilt > 45) {
                hint = "Deite o aparelho com a tela para cima";
            } else if (modePref == LevelMath.MODE_EDGE && fastTilt < 30) {
                hint = "Apoie o aparelho em pé sobre uma borda";
            }
        }
        if (watchdogHint != null) {
            hint = watchdogHint;
        } else if (accuracyHint != null) {
            hint = accuracyHint;
        }

        levelView.setReading(r, showData);
        levelView.setHeld(held);
        levelView.setLevel(level);
        levelView.setTolerance(settings.tolerance());
        levelView.setSensorMissing(sensorMissing);
        levelView.setTexts(big, sub1, sub2, status, hint, calPhase != 0);
        updateDescription(status, big, sub1, sub2);
    }

    private int calibrationPercent() {
        long elapsed = SystemClock.uptimeMillis() - calPhaseStart;
        return (int) Math.min(100, Math.max(0, elapsed * 100 / CAL_SAMPLE_MS));
    }

    private void updateDescription(String status, String big, String sub1, String sub2) {
        String d = status + ". " + big + ". " + sub1 + ". " + sub2;
        if (!d.equals(lastDescription)) {
            lastDescription = d;
            levelView.setContentDescription(d);
        }
    }

    private void updateButtons() {
        styleButton(btnAuto, modePref == LevelMath.MODE_AUTO, pal.ambar, pal.sobreAmbar);
        styleButton(btnSurface, modePref == LevelMath.MODE_SURFACE, pal.ambar, pal.sobreAmbar);
        styleButton(btnEdge, modePref == LevelMath.MODE_EDGE, pal.ambar, pal.sobreAmbar);
        styleButton(btnHold, held, pal.azul, pal.sobreAzul);
        btnHold.setText(held ? "Destravar" : "Travar");
        btnCalibrate.setText(calPhase != 0 ? "Cancelar" : "Calibrar");
        enableLook(btnHold, !sensorMissing && (hasData || held));
        enableLook(btnCalibrate, !sensorMissing && (hasData || calPhase != 0));
    }

    private void styleButton(Button b, boolean on, int onFill, int onText) {
        GradientDrawable fill = (GradientDrawable) b.getTag();
        fill.setColor(on ? onFill : pal.frasco);
        b.setTextColor(on ? onText : pal.texto);
        b.setSelected(on);
    }

    private void enableLook(Button b, boolean enabled) {
        b.setEnabled(enabled);
        b.setAlpha(enabled ? 1f : 0.4f);
    }

    private void showCalibrateDialog() {
        if (sensorMissing) {
            return;
        }
        String title;
        boolean has;
        if (surfaceNow) {
            title = "Calibrar · Superfície";
            has = settings.hasSurfaceCal();
        } else {
            title = "Calibrar · Borda (" + edgeName(quarter) + ")";
            has = settings.hasEdgeCal(quarter);
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("Coloque o aparelho sobre uma superfície de referência nivelada, "
                        + "na posição em que vai medir. Toque em Calibrar e solte o aparelho: "
                        + "a medição leva cerca de 3 segundos e o aparelho não pode se mexer.")
                .setPositiveButton("Calibrar", (d, which) -> startCalibration())
                .setNegativeButton("Cancelar", null);
        if (has) {
            b.setNeutralButton("Redefinir", (d, which) -> resetCalibration());
        }
        b.show();
    }

    private static String edgeName(int q) {
        switch (LevelMath.wrapQuarter(q)) {
            case 0:
                return "borda inferior";
            case 1:
                return "lateral esquerda";
            case 2:
                return "borda superior";
            default:
                return "lateral direita";
        }
    }

    private void resetCalibration() {
        if (surfaceNow) {
            settings.clearSurfaceCal();
        } else {
            settings.clearEdgeCal(quarter);
        }
        levelKnown = false;
        toast("Calibração removida");
        updateButtons();
        render();
    }

    private void startCalibration() {
        if (sensorMissing) {
            return;
        }
        if (!hasData) {
            toast("Aguardando sensor…");
            return;
        }
        held = false;
        levelKnown = false;
        calSurface = surfaceNow;
        calQuarter = quarter;
        calPhase = 1;
        calPhaseStart = SystemClock.uptimeMillis();
        calFirstAt = 0;
        resetCalSums();
        handler.removeCallbacks(calTickTask);
        handler.postDelayed(calTickTask, CAL_TICK_MS);
        updateButtons();
        render();
    }

    private void resetCalSums() {
        calSumX = 0;
        calSumY = 0;
        calSumZ = 0;
        calCount = 0;
        calMinA = Double.POSITIVE_INFINITY;
        calMaxA = Double.NEGATIVE_INFINITY;
        calMinB = Double.POSITIVE_INFINITY;
        calMaxB = Double.NEGATIVE_INFINITY;
    }

    private void accumulateCalibration(double[] f) {
        calSumX += f[0];
        calSumY += f[1];
        calSumZ += f[2];
        calCount++;
        double a;
        double b;
        if (calSurface) {
            a = LevelMath.axisElevation(f[0], f[1], f[2]);
            b = LevelMath.axisElevation(f[1], f[0], f[2]);
        } else {
            a = LevelMath.axisElevation(LevelMath.toU(calQuarter, f[0], f[1]),
                    LevelMath.toV(calQuarter, f[0], f[1]), f[2]);
            b = a;
        }
        calMinA = Math.min(calMinA, a);
        calMaxA = Math.max(calMaxA, a);
        calMinB = Math.min(calMinB, b);
        calMaxB = Math.max(calMaxB, b);
    }

    /** Espera 1 s, amostra 2 s; reinicia a amostra se o aparelho se mexer; desiste apos 10 s. */
    private void onCalTick() {
        if (calPhase == 0) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (surfaceNow != calSurface || quarter != calQuarter) {
            cancelCalibration("A posição mudou. Calibração cancelada.");
            return;
        }
        if (calPhase == 1) {
            if (now - calPhaseStart >= CAL_WAIT_MS) {
                calPhase = 2;
                calPhaseStart = now;
                calFirstAt = now;
                resetCalSums();
            }
        } else {
            if (now - calFirstAt > CAL_TIMEOUT_MS) {
                cancelCalibration("O aparelho se mexeu. Tente de novo com ele parado.");
                return;
            }
            if (now - calPhaseStart >= CAL_SAMPLE_MS) {
                double range = Math.max(calMaxA - calMinA, calMaxB - calMinB);
                if (calCount > 0 && range <= CAL_STILL_DEG) {
                    finishCalibration(now);
                    return;
                }
                calPhaseStart = now;
                resetCalSums();
            }
        }
        render();
        handler.postDelayed(calTickTask, CAL_TICK_MS);
    }

    private void finishCalibration(long now) {
        double mx = calSumX / calCount;
        double my = calSumY / calCount;
        double mz = calSumZ / calCount;
        if (calSurface) {
            double ox = LevelMath.axisElevation(mx, my, mz);
            double oy = LevelMath.axisElevation(my, mx, mz);
            if (Math.abs(ox) > CAL_MAX_OFFSET_DEG || Math.abs(oy) > CAL_MAX_OFFSET_DEG) {
                cancelCalibration("Inclinação grande demais para calibrar (máx. 10°).");
                return;
            }
            settings.setSurfaceCal(ox, oy);
        } else {
            double o = LevelMath.axisElevation(LevelMath.toU(calQuarter, mx, my),
                    LevelMath.toV(calQuarter, mx, my), mz);
            if (Math.abs(o) > CAL_MAX_OFFSET_DEG) {
                cancelCalibration("Inclinação grande demais para calibrar (máx. 10°).");
                return;
            }
            settings.setEdgeCal(calQuarter, o);
        }
        calPhase = 0;
        handler.removeCallbacks(calTickTask);
        levelKnown = false;
        toast("Calibração salva");
        feedback(now);
        updateButtons();
        render();
    }

    private void cancelCalibration(String message) {
        boolean was = calPhase != 0;
        calPhase = 0;
        handler.removeCallbacks(calTickTask);
        if (was) {
            levelKnown = false;
            if (message != null) {
                toast(message);
            }
        }
        updateButtons();
        render();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showSettingsDialog() {
        int pad = dp(20);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, dp(8), pad, dp(8));

        Switch swSound = new Switch(this);
        swSound.setText("Som ao nivelar (volume de mídia)");
        swSound.setChecked(settings.sound());
        swSound.setOnCheckedChangeListener((cb, on) -> settings.setSound(on));
        box.addView(swSound);

        Switch swVibrate = new Switch(this);
        swVibrate.setText("Vibrar ao nivelar");
        swVibrate.setChecked(settings.vibrate());
        swVibrate.setOnCheckedChangeListener((cb, on) -> settings.setVibrate(on));
        box.addView(swVibrate);

        box.addView(sectionTitle("Unidade"));
        RadioGroup unitGroup = new RadioGroup(this);
        unitGroup.setOrientation(LinearLayout.VERTICAL);
        unitGroup.addView(radio("Graus (°)", 1));
        unitGroup.addView(radio("Porcentagem (%)", 2));
        unitGroup.check(settings.unit() == LevelMath.UNIT_PERCENT ? 2 : 1);
        unitGroup.setOnCheckedChangeListener((g, id) -> {
            settings.setUnit(id == 2 ? LevelMath.UNIT_PERCENT : LevelMath.UNIT_DEG);
            resetDisplay();
            render();
        });
        box.addView(unitGroup);

        box.addView(sectionTitle("Tolerância de nível"));
        RadioGroup tolGroup = new RadioGroup(this);
        tolGroup.setOrientation(LinearLayout.VERTICAL);
        String[] tolLabels = {"0,1°", "0,2°", "0,5°", "1,0°"};
        for (int i = 0; i < LevelSettings.toleranceCount(); i++) {
            tolGroup.addView(radio(tolLabels[i], 11 + i));
        }
        tolGroup.check(11 + settings.toleranceIndex());
        tolGroup.setOnCheckedChangeListener((g, id) -> {
            settings.setTolerance(LevelSettings.toleranceAt(id - 11));
            levelKnown = false;
            render();
        });
        box.addView(tolGroup);

        Button clear = new Button(this);
        clear.setAllCaps(false);
        clear.setText("Apagar todas as calibrações");
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Apagar calibrações?")
                .setMessage("Todas as posições voltam a medir sem correção.")
                .setPositiveButton("Apagar", (d, which) -> {
                    settings.clearAllCal();
                    levelKnown = false;
                    toast("Calibrações apagadas");
                    updateButtons();
                    render();
                })
                .setNegativeButton("Cancelar", null)
                .show());
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clearLp.setMargins(0, dp(12), 0, dp(12));
        box.addView(clear, clearLp);

        TextView tips = new TextView(this);
        tips.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tips.setTextColor(pal.secundario);
        tips.setText("• O modo Auto escolhe Superfície (deitado) ou Borda (em pé).\n"
                + "• As teclas de volume travam e destravam a leitura sem tocar na tela.\n"
                + "• Calibre cada posição sobre uma superfície que você sabe estar nivelada.\n"
                + "• A tela fica ligada enquanto o app está aberto.");
        box.addView(tips);

        TextView version = new TextView(this);
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        version.setTextColor(pal.secundario);
        version.setPadding(0, dp(16), 0, 0);
        version.setText("Versão " + versionName);
        box.addView(version);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        new AlertDialog.Builder(this)
                .setTitle("Ajustes")
                .setView(scroll)
                .setPositiveButton("Fechar", null)
                .show();
    }

    private TextView sectionTitle(String text) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setText(text);
        t.setPadding(0, dp(14), 0, dp(2));
        return t;
    }

    private RadioButton radio(String text, int id) {
        RadioButton rb = new RadioButton(this);
        rb.setId(id);
        rb.setText(text);
        return rb;
    }
}
