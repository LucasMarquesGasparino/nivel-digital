package br.com.lucasgasparino.niveldigital;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.view.View;

/**
 * Desenha o instrumento (superficie 2D ou borda 1D) no proprio retangulo.
 * Nao sabe do quarto: recebe leituras no referencial do usuario e textos ja formatados.
 * Nada e alocado em onDraw.
 */
final class LevelView extends View {

    static final class Palette {
        final int bg;
        final int frasco;
        final int linhas;
        final int texto;
        final int secundario;
        final int ambar;
        final int verde;
        final int azul;
        final int sobreAmbar;
        final int sobreAzul;

        private Palette(int bg, int frasco, int linhas, int texto, int secundario, int ambar,
                        int verde, int azul, int sobreAmbar, int sobreAzul) {
            this.bg = bg;
            this.frasco = frasco;
            this.linhas = linhas;
            this.texto = texto;
            this.secundario = secundario;
            this.ambar = ambar;
            this.verde = verde;
            this.azul = azul;
            this.sobreAmbar = sobreAmbar;
            this.sobreAzul = sobreAzul;
        }

        static Palette of(Context context) {
            int ui = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if (ui == Configuration.UI_MODE_NIGHT_YES) {
                return new Palette(0xff101318, 0xff171b21, 0xff39414b, 0xfff3f5f7, 0xffa3acb7,
                        0xfff3b84b, 0xff55c98a, 0xff6cb4ff, 0xff101318, 0xff101318);
            }
            return new Palette(0xfff4f6f8, 0xffffffff, 0xffc3cad3, 0xff14181d, 0xff4f5966,
                    0xffb7791f, 0xff1e8e5a, 0xff1f6fd1, 0xff14181d, 0xffffffff);
        }
    }

    private static final float[] RINGS_DEG = {1f, 5f, 10f};
    private static final double[] TICKS_DEG = {-20, -10, -5, -2, -1, 0, 1, 2, 5, 10, 20};
    private static final String[] TICK_LABELS = {
            "−20", "−10", "−5", "−2", "−1", "0", "1", "2", "5", "10", "20"};

    private final Palette pal;
    private final float density;
    private final float scaledDensity;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final double[] bubble = new double[2];
    private final Typeface light;
    private final Typeface medium;
    private final Typeface regular;
    private final LevelMath.Reading reading = new LevelMath.Reading();

    private boolean hasData;
    private boolean held;
    private boolean level;
    private boolean sensorMissing;
    private boolean calibrating;
    private double tolerance = 0.2;
    private String big = "--";
    private String sub1 = "";
    private String sub2 = "";
    private String status = "";
    private String hint;

    LevelView(Context context) {
        super(context);
        pal = Palette.of(context);
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        density = dm.density;
        scaledDensity = dm.scaledDensity;
        light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        regular = Typeface.create("sans-serif", Typeface.NORMAL);
        strokePaint.setStyle(Paint.Style.STROKE);
        fillPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    void setReading(LevelMath.Reading r, boolean hasData) {
        reading.copyFrom(r);
        this.hasData = hasData;
        invalidate();
    }

    void setHeld(boolean held) {
        this.held = held;
        invalidate();
    }

    void setLevel(boolean level) {
        this.level = level;
        invalidate();
    }

    void setTolerance(double tolDeg) {
        this.tolerance = tolDeg;
        invalidate();
    }

    void setSensorMissing(boolean missing) {
        this.sensorMissing = missing;
        invalidate();
    }

    void setTexts(String big, String sub1, String sub2, String status, String hint, boolean calibrating) {
        this.big = big;
        this.sub1 = sub1;
        this.sub2 = sub2;
        this.status = status;
        this.hint = hint;
        this.calibrating = calibrating;
        invalidate();
    }

    private float dp(float v) {
        return v * density;
    }

    /** Escala de fonte limitada a 1,3 para o layout nao quebrar. */
    private float sp(float v) {
        return v * Math.min(scaledDensity, density * 1.3f);
    }

    private int stateColor() {
        if (held) {
            return pal.azul;
        }
        return level ? pal.verde : pal.ambar;
    }

    private int statusColor() {
        if (calibrating) {
            return pal.ambar;
        }
        if (held) {
            return pal.azul;
        }
        return level ? pal.verde : pal.secundario;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        c.drawColor(pal.bg);
        if (w <= 0 || h <= 0) {
            return;
        }
        if (sensorMissing) {
            drawMissing(c, w, h);
            return;
        }
        boolean landscape = w >= h * 1.15f;
        if (reading.mode == LevelMath.MODE_EDGE) {
            drawEdge(c, w, h, landscape);
        } else {
            drawSurface(c, w, h, landscape);
        }
    }

    private void drawMissing(Canvas c, float w, float h) {
        float cx = w / 2f;
        float cy = h / 2f;
        textPaint.setTypeface(medium);
        textPaint.setTextSize(sp(20));
        textPaint.setColor(pal.texto);
        c.drawText("Sensor indisponível", cx, cy - dp(12), textPaint);
        textPaint.setTypeface(regular);
        textPaint.setTextSize(sp(15));
        textPaint.setColor(pal.secundario);
        c.drawText("Este aparelho não informa o acelerômetro,", cx, cy + dp(18), textPaint);
        c.drawText("então o nível não funciona aqui.", cx, cy + dp(40), textPaint);
    }

    private void drawSurface(Canvas c, float w, float h, boolean landscape) {
        float cx;
        float cy;
        float r;
        if (!landscape) {
            cx = w / 2f;
            cy = 0.40f * h;
            r = Math.min(0.40f * w, Math.min(0.30f * h, dp(170)));
        } else {
            cx = w * 0.32f;
            cy = h / 2f;
            r = Math.min(0.40f * h, 0.27f * w);
        }
        float bubbleR = Math.max(0.12f * r, dp(10));

        fillPaint.setColor(pal.frasco);
        c.drawCircle(cx, cy, r, fillPaint);

        strokePaint.setStrokeWidth(dp(1));
        strokePaint.setColor(pal.linhas);
        c.drawLine(cx - r, cy, cx + r, cy, strokePaint);
        c.drawLine(cx, cy - r, cx, cy + r, strokePaint);

        textPaint.setTypeface(regular);
        textPaint.setTextSize(sp(11));
        textPaint.setColor(pal.secundario);
        for (float a : RINGS_DEG) {
            float rr = r * (float) Math.tanh(a / LevelMath.SURFACE_SCALE_DEG);
            strokePaint.setColor(pal.linhas);
            c.drawCircle(cx, cy, rr, strokePaint);
            c.drawText(ringLabel(a), cx + dp(5), cy - rr - dp(3), textPaint);
        }

        strokePaint.setStrokeWidth(dp(2));
        strokePaint.setColor(pal.linhas);
        c.drawCircle(cx, cy, r, strokePaint);

        if (hasData) {
            float tolR = r * (float) Math.tanh(tolerance / LevelMath.SURFACE_SCALE_DEG) + bubbleR;
            strokePaint.setStrokeWidth(dp(1.5f));
            strokePaint.setColor(stateColor());
            c.drawCircle(cx, cy, tolR, strokePaint);

            LevelMath.bubble2D(reading.xu, reading.yu, LevelMath.SURFACE_SCALE_DEG, bubble);
            float bx = cx + (float) bubble[0] * r * 0.86f;
            float by = cy - (float) bubble[1] * r * 0.86f;
            int col = stateColor();
            fillPaint.setColor((col & 0x00ffffff) | 0x44000000);
            c.drawCircle(bx, by, bubbleR * 1.6f, fillPaint);
            fillPaint.setColor(col);
            c.drawCircle(bx, by, bubbleR, fillPaint);
        }

        if (landscape) {
            drawTexts(c, w * 0.72f, 0f, w * 0.36f, true, cy);
        } else {
            drawTexts(c, cx, cy + r + dp(22), w * 0.92f, false, 0f);
        }
    }

    private void drawEdge(Canvas c, float w, float h, boolean landscape) {
        float tubeW = Math.min(0.86f * w, dp(520));
        float tubeH = dp(56);
        float cx = w / 2f;
        float cy = landscape ? 0.25f * h : 0.32f * h;
        float left = cx - tubeW / 2f;
        float top = cy - tubeH / 2f;
        float bottom = cy + tubeH / 2f;
        float rbx = dp(26);
        float rby = dp(20);
        float travel = tubeW / 2f - rbx;
        float radius = tubeH / 2f;

        rect.set(left, top, left + tubeW, bottom);
        fillPaint.setColor(pal.frasco);
        c.drawRoundRect(rect, radius, radius, fillPaint);
        strokePaint.setStrokeWidth(dp(2));
        strokePaint.setColor(pal.linhas);
        c.drawRoundRect(rect, radius, radius, strokePaint);

        float guide = (float) LevelMath.bubble1D(tolerance, LevelMath.EDGE_SCALE_DEG) * travel;
        strokePaint.setStrokeWidth(dp(1.5f));
        c.drawLine(cx - guide, top + dp(6), cx - guide, bottom - dp(6), strokePaint);
        c.drawLine(cx + guide, top + dp(6), cx + guide, bottom - dp(6), strokePaint);

        textPaint.setTypeface(regular);
        textPaint.setTextSize(sp(11));
        textPaint.setColor(pal.secundario);
        strokePaint.setStrokeWidth(dp(1));
        for (int i = 0; i < TICKS_DEG.length; i++) {
            float x = cx + (float) Math.tanh(TICKS_DEG[i] / LevelMath.EDGE_SCALE_DEG) * travel;
            c.drawLine(x, top, x, top + dp(7), strokePaint);
            c.drawLine(x, bottom - dp(7), x, bottom, strokePaint);
            c.drawText(TICK_LABELS[i], x, top - dp(6), textPaint);
        }

        if (hasData) {
            float bx = cx + (float) LevelMath.bubble1D(reading.edge, LevelMath.EDGE_SCALE_DEG) * travel;
            int col = stateColor();
            rect.set(bx - rbx - dp(4), cy - rby - dp(4), bx + rbx + dp(4), cy + rby + dp(4));
            fillPaint.setColor((col & 0x00ffffff) | 0x44000000);
            c.drawRoundRect(rect, rby + dp(4), rby + dp(4), fillPaint);
            rect.set(bx - rbx, cy - rby, bx + rbx, cy + rby);
            fillPaint.setColor(col);
            c.drawRoundRect(rect, rby, rby, fillPaint);
        }

        drawTexts(c, cx, bottom + dp(landscape ? 18 : 40), w * 0.92f, false, 0f);
    }

    private static String ringLabel(float deg) {
        if (deg == 1f) {
            return "1°";
        }
        if (deg == 5f) {
            return "5°";
        }
        return "10°";
    }

    /**
     * Bloco de textos: começa em top, ou (centerOn) fica centralizado verticalmente em centerY.
     */
    private void drawTexts(Canvas c, float cx, float top, float maxW, boolean centerOn, float centerY) {
        float y = top;
        if (centerOn) {
            y = centerY - textBlock(null, cx, 0f, maxW) / 2f;
        }
        textBlock(c, cx, y, maxW);
    }

    /** Desenha (c != null) ou so mede (c == null) o bloco; devolve a altura. */
    private float textBlock(Canvas c, float cx, float top, float maxW) {
        float y = top;
        y = line(c, status, sp(13), statusColor(), medium, cx, y, maxW, dp(10));
        y = line(c, big, sp(56), pal.texto, light, cx, y, maxW, dp(4));
        y = line(c, sub1, sp(15), pal.texto, regular, cx, y, maxW, dp(4));
        y = line(c, sub2, sp(13), pal.secundario, regular, cx, y, maxW, hint == null ? 0f : dp(4));
        if (hint != null) {
            y = line(c, hint, sp(13), pal.ambar, regular, cx, y, maxW, 0f);
        }
        return y - top;
    }

    private float line(Canvas c, String text, float size, int color, Typeface tf,
                       float cx, float y, float maxW, float gapAfter) {
        textPaint.setTypeface(tf);
        textPaint.setColor(color);
        float s = size;
        textPaint.setTextSize(s);
        if (text != null && !text.isEmpty()) {
            while (s > size * 0.6f && textPaint.measureText(text) > maxW) {
                s *= 0.94f;
                textPaint.setTextSize(s);
            }
            if (c != null) {
                c.drawText(text, cx, y + s * 0.95f, textPaint);
            }
        }
        return y + s * 1.25f + gapAfter;
    }
}
