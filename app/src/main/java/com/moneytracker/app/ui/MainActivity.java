package com.moneytracker.app.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentContainerView;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.moneytracker.app.R;
import com.moneytracker.app.data.entity.Category;
import com.moneytracker.app.ui.addtransaction.AddTransactionActivity;
import com.moneytracker.app.ui.onboarding.OnboardingActivity;
import com.moneytracker.app.ui.widget.GlassPanelView;
import com.moneytracker.app.util.ElevationShadows;
import com.moneytracker.app.util.LocaleHelper;
import com.moneytracker.app.util.OnboardingPrefs;
import com.moneytracker.app.util.SettingsPrefs;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigation;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyStoredLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!OnboardingPrefs.isDone(this)) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        ElevationShadows.apply(findViewById(android.R.id.content));

        FragmentContainerView navHost = findViewById(R.id.nav_host_fragment);
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(navHost.getId());
        NavController navController = navHostFragment.getNavController();

        GlassPanelView glassNav = findViewById(R.id.glass_nav);
        glassNav.setCaptureTarget(navHost);
        glassNav.setCornerRadius(getResources().getDimension(R.dimen.nav_pill_corner_radius));

        bottomNavigation = findViewById(R.id.bottom_navigation);
        NavigationUI.setupWithNavController(bottomNavigation, navController);
        // Switching tabs replaces the whole backdrop without scrolling it, which is the
        // one content change the scroll-driven capture cannot see by itself.
        navController.addOnDestinationChangedListener((controller, destination, arguments) ->
                glassNav.post(glassNav::capture));
        // Overrides the item listener setupWithNavController installed, but keeps its
        // destination-changed listener (installed separately), so the checked state
        // still tracks navigation. This just adds the per-tab icon animation on top.
        bottomNavigation.setOnItemSelectedListener(item -> {
            boolean handled = NavigationUI.onNavDestinationSelected(item, navController);
            if (handled) {
                animateNavIcon(item.getItemId());
            }
            return handled;
        });
        bottomNavigation.setOnItemReselectedListener(item -> animateNavIcon(item.getItemId()));
        bottomNavigation.post(() -> unclipNavIconContainers(bottomNavigation));

        findViewById(R.id.btn_quick_add).setOnClickListener(v ->
                startActivity(AddTransactionActivity.newIntent(this, Category.TYPE_EXPENSE, -1L)));
    }

    /**
     * Material sizes each item's icon container to exactly the icon, so an icon that hops
     * even a pixel gets clipped against it (and against the menu row above that) - which
     * showed up as the home/wallet icon losing its top or bottom edge mid-animation. The
     * pill itself has room to spare around the icons, so letting these containers draw
     * outside their bounds is enough; the hop stays well inside the pill.
     */
    private static void unclipNavIconContainers(ViewGroup root) {
        root.setClipChildren(false);
        root.setClipToPadding(false);
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof ViewGroup) {
                unclipNavIconContainers((ViewGroup) child);
            }
        }
    }

    /** Per-tab tap animation: home/wallet icons hop; analytics bars wave and the
     * settings gear spins via their AnimatedVectorDrawable menu icons. */
    private void animateNavIcon(int itemId) {
        if (!SettingsPrefs.isAnimationsEnabled(this)) {
            return;
        }
        View itemView = bottomNavigation.findViewById(itemId);
        if (itemView == null) {
            return;
        }
        ImageView iconView = findFirstImageView(itemView);
        if (itemId == R.id.analyticsFragment || itemId == R.id.settingsFragment) {
            Drawable icon = iconView != null ? iconView.getDrawable() : null;
            if (icon instanceof Animatable) {
                ((Animatable) icon).start();
                return;
            }
        }
        View target = iconView != null ? iconView : itemView;
        float hop = -5f * getResources().getDisplayMetrics().density;
        target.animate().cancel();
        target.setTranslationY(0f);
        target.animate().translationY(hop).setDuration(160)
                .withEndAction(() -> target.animate().translationY(0f).setDuration(320)
                        .setInterpolator(new OvershootInterpolator(1.6f)).start())
                .start();
    }

    /** Depth-first search for the item's icon ImageView, avoiding any dependence on
     * Material's internal view ids; returns null if the structure ever changes. */
    private static ImageView findFirstImageView(View root) {
        if (root instanceof ImageView) {
            return (ImageView) root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                ImageView found = findFirstImageView(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
