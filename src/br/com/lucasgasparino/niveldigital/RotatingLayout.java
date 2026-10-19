package br.com.lucasgasparino.niveldigital;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Gira o unico filho em multiplos de 90 graus, para que a interface fique de pe
 * quando o aparelho e girado. A activity continua travada em retrato.
 */
final class RotatingLayout extends ViewGroup {
    interface ShapeListener {
        void onShapeChanged(boolean landscape);
    }

    private static final long FADE_MS = 150;

    private final ShapeListener listener;
    private int quarter;

    RotatingLayout(Context context, ShapeListener listener) {
        super(context);
        this.listener = listener;
    }

    void setContent(View child) {
        removeAllViews();
        addView(child, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    int getQuarter() {
        return quarter;
    }

    /** Positivo = horario, como em View.setRotation. */
    static float rotationFor(int q) {
        return q == 3 ? -90f : q * 90f;
    }

    void setQuarter(int q) {
        int next = LevelMath.wrapQuarter(q);
        if (next == quarter) {
            return;
        }
        quarter = next;
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            child.setRotation(rotationFor(quarter));
            child.setAlpha(0f);
            child.animate().alpha(1f).setDuration(FADE_MS).start();
        }
        requestLayout();
        if (listener != null) {
            listener.onShapeChanged((quarter & 1) == 1);
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        if (getChildCount() > 0) {
            boolean odd = (quarter & 1) == 1;
            int cw = odd ? h : w;
            int ch = odd ? w : h;
            getChildAt(0).measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (getChildCount() == 0) {
            return;
        }
        View child = getChildAt(0);
        int width = r - l;
        int height = b - t;
        int cw = child.getMeasuredWidth();
        int ch = child.getMeasuredHeight();
        int left = (width - cw) / 2;
        int top = (height - ch) / 2;
        child.layout(left, top, left + cw, top + ch);
        child.setRotation(rotationFor(quarter));
    }
}
