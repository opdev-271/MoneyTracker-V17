package com.moneytracker.app.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.Nullable;

import com.moneytracker.app.R;
import com.moneytracker.app.util.SettingsPrefs;

/**
 * Frosted-glass backdrop for a floating panel: it draws whatever sits behind it,
 * blurred, clipped to a rounded rectangle. Sits *underneath* the real panel (the nav
 * pill), which keeps its own translucent tint, rim and shadow on top.
 *
 * <p>Android has no equivalent of iOS's {@code UIVisualEffectView} — nothing in the
 * framework samples the backdrop of a view inside a window. The blur APIs that do exist
 * either need API 31+ ({@code RenderEffect}, which blurs a view's <em>own</em> content,
 * not what is behind it) or blur a whole window ({@code setBackgroundBlurRadius}, which
 * the system may disable at any time). So this samples the backdrop itself:
 *
 * <ol>
 *   <li>Draw the capture target into a heavily downscaled bitmap — 1/8 linear, so a
 *       nav-pill-sized area is roughly 3000 pixels and rendering it costs very little.</li>
 *   <li>Box-blur that bitmap. At this size a few passes in Java are microseconds, and
 *       three passes approximate a Gaussian closely enough that no one can tell.</li>
 *   <li>Draw it back up through a filtered {@link BitmapShader}. The bilinear upscale
 *       does most of the visual smoothing for free, and drawing through a shader means
 *       the rounded corners come from {@code drawRoundRect} and are anti-aliased —
 *       unlike {@code clipPath} on a hardware canvas, whose hard edges would be the
 *       one detail that gives away that this is a fake.</li>
 * </ol>
 *
 * <p>The result works identically on every supported API level rather than looking like
 * two different apps either side of API 31.
 *
 * <p>The capture target must not be an ancestor of this view, or drawing it would
 * recurse. Pointing it at a sibling (the fragment container) rather than the root also
 * means this panel and the FAB stay out of their own reflection.
 */
public class GlassPanelView extends View {

    /** Linear downscale of the captured backdrop. Also does most of the blurring. */
    private static final float DOWNSCALE = 8f;
    /** Box-blur radius in downscaled pixels; ~3 here reads as ~24px of blur on screen. */
    private static final int BLUR_RADIUS = 3;
    private static final int BLUR_PASSES = 3;

    private View captureTarget;
    private float cornerRadius;
    private int fallbackColor;

    private Bitmap snapshot;
    private Canvas snapshotCanvas;
    private int[] pixels;
    private BitmapShader shader;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint solidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix shaderMatrix = new Matrix();
    private final RectF bounds = new RectF();
    private final int[] selfLocation = new int[2];
    private final int[] targetLocation = new int[2];

    private boolean liveBlur = true;

    private final ViewTreeObserver.OnScrollChangedListener scrollListener = this::capture;
    private final ViewTreeObserver.OnGlobalLayoutListener layoutListener = this::capture;

    public GlassPanelView(Context context) {
        this(context, null);
    }

    public GlassPanelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        fallbackColor = context.getColor(R.color.background);
        solidPaint.setColor(context.getColor(R.color.surface));
        // The app already lets the user turn animations off for older phones; a live
        // backdrop capture is exactly the kind of per-frame work that setting is about.
        liveBlur = SettingsPrefs.isAnimationsEnabled(context);
    }

    public void setCaptureTarget(View captureTarget) {
        this.captureTarget = captureTarget;
        capture();
    }

    public void setCornerRadius(float cornerRadiusPx) {
        this.cornerRadius = cornerRadiusPx;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!liveBlur) {
            return;
        }
        getViewTreeObserver().addOnScrollChangedListener(scrollListener);
        getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnScrollChangedListener(scrollListener);
        getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
        snapshot = null;
        snapshotCanvas = null;
        shader = null;
        pixels = null;
        super.onDetachedFromWindow();
    }

    /**
     * Re-samples the backdrop. Driven by scroll and layout rather than by every frame:
     * the panel only needs a new sample when what is behind it actually moved, and
     * capturing unconditionally on pre-draw would pin the app at a full redraw per frame
     * forever, even sitting idle on a static screen.
     */
    public void capture() {
        if (!liveBlur || captureTarget == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        int width = Math.max(1, (int) (getWidth() / DOWNSCALE));
        int height = Math.max(1, (int) (getHeight() / DOWNSCALE));

        if (snapshot == null || snapshot.getWidth() != width || snapshot.getHeight() != height) {
            snapshot = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            snapshotCanvas = new Canvas(snapshot);
            pixels = new int[width * height];
            shader = new BitmapShader(snapshot, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            paint.setShader(shader);
        }

        getLocationInWindow(selfLocation);
        captureTarget.getLocationInWindow(targetLocation);

        snapshotCanvas.save();
        // Anything the target does not paint (during a transition, say) must not show
        // through as black.
        snapshotCanvas.drawColor(fallbackColor);
        snapshotCanvas.scale(1f / DOWNSCALE, 1f / DOWNSCALE);
        snapshotCanvas.translate(targetLocation[0] - selfLocation[0], targetLocation[1] - selfLocation[1]);
        captureTarget.draw(snapshotCanvas);
        snapshotCanvas.restore();

        blur(snapshot, pixels);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        capture();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        bounds.set(0, 0, getWidth(), getHeight());
        if (snapshot == null || shader == null) {
            // No sample yet, or live blur turned off with the animations setting. The
            // panel on top is translucent, so it needs something opaque behind it or it
            // would read as a smeared hole rather than as a surface.
            canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, solidPaint);
            return;
        }
        shaderMatrix.reset();
        shaderMatrix.setScale(
                (float) getWidth() / snapshot.getWidth(),
                (float) getHeight() / snapshot.getHeight());
        shader.setLocalMatrix(shaderMatrix);
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, paint);
    }

    /** Separable box blur, run in place over the downscaled snapshot. */
    private static void blur(Bitmap bitmap, int[] pixels) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int pass = 0; pass < BLUR_PASSES; pass++) {
            blurPass(pixels, width, height, true);
            blurPass(pixels, width, height, false);
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    }

    private static void blurPass(int[] pixels, int width, int height, boolean horizontal) {
        int outerCount = horizontal ? height : width;
        int innerCount = horizontal ? width : height;
        int step = horizontal ? 1 : width;
        int[] line = new int[innerCount];

        for (int outer = 0; outer < outerCount; outer++) {
            int base = horizontal ? outer * width : outer;
            for (int i = 0; i < innerCount; i++) {
                line[i] = pixels[base + i * step];
            }
            for (int i = 0; i < innerCount; i++) {
                int r = 0, g = 0, b = 0, samples = 0;
                int from = Math.max(0, i - BLUR_RADIUS);
                int to = Math.min(innerCount - 1, i + BLUR_RADIUS);
                for (int j = from; j <= to; j++) {
                    int color = line[j];
                    r += (color >> 16) & 0xFF;
                    g += (color >> 8) & 0xFF;
                    b += color & 0xFF;
                    samples++;
                }
                pixels[base + i * step] =
                        0xFF000000 | ((r / samples) << 16) | ((g / samples) << 8) | (b / samples);
            }
        }
    }
}
