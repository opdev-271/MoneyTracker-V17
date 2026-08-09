package com.moneytracker.app.ui.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.moneytracker.app.R;

/**
 * Every DialogFragment builds with {@code MaterialAlertDialogBuilder} and
 * {@code R.style.ThemeOverlay_MoneyTracker_Dialog}, which already colors the positive
 * button via {@code colorPrimary}; this just differentiates the negative ("Cancel") and
 * neutral ("Delete") actions so a destructive action doesn't share the same accent as
 * the confirming one, and gives the dialog surface a visible hairline border.
 *
 * <p>Button views don't exist until the dialog is actually shown, so callers must invoke
 * this from inside their own {@code setOnShowListener} callback - typically the same one
 * already used to override the positive button's click behavior - rather than this class
 * installing a second, mutually-clobbering listener.
 */
public class DialogButtonStyler {

    private DialogButtonStyler() {
    }

    /** Blur radius behind an open dialog, in dp. */
    private static final float BACKDROP_BLUR_DP = 20f;
    /** Dim to pair with the blur. Much lighter than the platform default 0.6, which on
     *  top of a blur reads as a grey sheet rather than as depth. */
    private static final float BLURRED_DIM_AMOUNT = 0.25f;

    public static void style(AlertDialog dialog) {
        applyBackdropBlur(dialog);
        android.widget.Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negative != null) {
            negative.setTextColor(dialog.getContext().getColor(R.color.text_secondary));
        }
        android.widget.Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (neutral != null) {
            neutral.setTextColor(dialog.getContext().getColor(R.color.expense_red));
        }
        applySurfaceBorder(dialog);
    }

    /**
     * Blurs the app behind an open dialog, the one place Android offers a real,
     * system-composited blur: {@code FLAG_BLUR_BEHIND} applies to a whole window, so it
     * works for dialogs even though nothing equivalent exists for a view inside a window.
     *
     * <p>Available from API 31 and, even there, only when the platform says so -
     * {@code isCrossWindowBlurEnabled()} returns false on low-RAM devices, while battery
     * saver is on, and whenever the user has ticked the "disable window blurs" developer
     * option. It can also flip at runtime. Rather than tracking that, this checks at the
     * moment the dialog is shown and simply leaves the standard dim in place when blur
     * is unavailable, so the dialog is never left looking half-finished.
     */
    private static void applyBackdropBlur(AlertDialog dialog) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        WindowManager windowManager =
                (WindowManager) dialog.getContext().getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null || !windowManager.isCrossWindowBlurEnabled()) {
            return;
        }
        float density = dialog.getContext().getResources().getDisplayMetrics().density;
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.setBlurBehindRadius((int) (BACKDROP_BLUR_DP * density));
        attributes.dimAmount = BLURRED_DIM_AMOUNT;
        window.setAttributes(attributes);
    }

    /**
     * Since v10 {@code surface} equals {@code background}, so a dialog's edge - the
     * bottom especially - can visually disappear against whatever sits behind it.
     * Material builds the window background as a (possibly inset-wrapped)
     * {@link MaterialShapeDrawable}; rather than replacing it (and re-deriving its
     * insets), reach into it and add the same 1dp divider hairline the app's cards
     * use, plus re-assert the 28dp corner radius that newer platforms may override
     * via {@code android:dialogCornerRadius}.
     */
    private static void applySurfaceBorder(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        Drawable background = window.getDecorView().getBackground();
        if (background instanceof InsetDrawable) {
            background = ((InsetDrawable) background).getDrawable();
        }
        if (!(background instanceof MaterialShapeDrawable)) {
            return;
        }
        MaterialShapeDrawable shape = (MaterialShapeDrawable) background;
        float density = dialog.getContext().getResources().getDisplayMetrics().density;
        shape.setShapeAppearanceModel(
                ShapeAppearanceModel.builder().setAllCornerSizes(28f * density).build());
        shape.setStroke(1f * density, dialog.getContext().getColor(R.color.divider));
    }
}
