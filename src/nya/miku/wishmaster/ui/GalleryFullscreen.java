/*
 * Overchan Android (Meta Imageboard Client)
 * Copyright (C) 2014-2015  miku-nyan <https://github.com/miku-nyan>
 *     
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nya.miku.wishmaster.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.ui.presentation.ThemeUtils;

public class GalleryFullscreen {
    private static final int DELAY = 2000;
    
    public static void initFullscreen(GalleryActivity activity) {
        activity.setFullscreenCallback(new GalleryFullscreenImpl(activity));
    }
    
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public static class GalleryFullscreenImpl implements View.OnSystemUiVisibilityChangeListener, GalleryActivity.FullscreenCallback {
        private final Handler handler;
        private final Window window;
        private final View decorView;
        private final ActionBar actionBar;
        private ViewGroup galleryNavbarView;
        private View navbarOverlay;
        
        private volatile long lastTouchEvent = 0;
        private volatile boolean isLocked = false;
        
        private GalleryFullscreenImpl(Activity activity) {
            handler = new Handler();
            window = activity.getWindow();
            decorView = activity.getWindow().getDecorView();
            decorView.setOnSystemUiVisibilityChangeListener(this);
            actionBar = activity.getActionBar();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                int color = ThemeUtils.getThemeColor(activity.getTheme(), R.styleable.Theme_materialPrimary, Color.WHITE);
                actionBar.setBackgroundDrawable(new ColorDrawable(color & Color.argb(192, 255, 255, 255)));
            }
            galleryNavbarView = (ViewGroup) activity.findViewById(R.id.gallery_navigation_bar_container);
            galleryNavbarView.setAlpha(0.75f);
            
            setTranscluentPanels();
            showUI(true);
            
            AppearanceUtils.callWhenLoaded(decorView, new Runnable() {
                @Override
                public void run() {
                    fixNavbarOverlay();
                }
            });
        }
        
        private void fixNavbarOverlay() {
            Rect rect = new Rect();
            decorView.getWindowVisibleDisplayFrame(rect);
            int overlayHeight = decorView.getHeight() - rect.bottom;
            if (overlayHeight > 0) {
                if (navbarOverlay != null) {
                    if (!Integer.valueOf(overlayHeight).equals(navbarOverlay.getTag())) {
                        navbarOverlay.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, overlayHeight));
                        navbarOverlay.setTag(overlayHeight);
                    }
                    navbarOverlay.setVisibility(View.VISIBLE);
                } else {
                    navbarOverlay = new View(galleryNavbarView.getContext());
                    navbarOverlay.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, overlayHeight));
                    navbarOverlay.setTag(overlayHeight);
                    galleryNavbarView.addView(navbarOverlay);
                }
            } else {
                if (navbarOverlay != null) {
                    navbarOverlay.setVisibility(View.GONE);
                }
            }
        }
        
        @Override
        public void onSystemUiVisibilityChange(int visibility) {
            fixNavbarOverlay();
            boolean visible = (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
            
            if (visible) {
                actionBar.show();
                hideUIdelayed();
            } else {
                actionBar.hide();
            }
            
            galleryNavbarView.animate().
                    alpha(visible ? 0.75f : 0).
                    translationY(visible ? 0 : galleryNavbarView.getHeight()).
                    setListener(visible ? animatorListener : null);
        }
        
        private final AnimatorListenerAdapter animatorListener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setSystemUiVisible();
            }
        };
        
        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        public void setSystemUiVisible() {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
        
        @Override
        public void showUI(boolean hideAfterDelay) {
            setSystemUiVisible();
            if (hideAfterDelay) {
                isLocked = false;
                lastTouchEvent = System.currentTimeMillis();
                hideUIdelayed();
            } else {
                isLocked = true;
            }
        }
        
        private final Runnable delayedHideUI = new Runnable() {
            @Override
            public void run() {
                if (isLocked) {
                    delayedTask = false;
                    return;
                }
                long time = System.currentTimeMillis() - lastTouchEvent;
                if (time < DELAY) {
                    handler.postDelayed(this, DELAY - time);
                } else {
                    hideUI();
                    delayedTask = false;
                }
            }
        };
        
        private boolean delayedTask = false;
        
        private synchronized void hideUIdelayed() {
            if (delayedTask) return;
            delayedTask = true;
            handler.postDelayed(delayedHideUI, DELAY);
        }
        
        private void hideUI() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) setImmersive(); else setNonImmersive();
        }
        
        @TargetApi(Build.VERSION_CODES.KITKAT)
        private void setImmersive() {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE
                    | View.SYSTEM_UI_FLAG_LOW_PROFILE);
        }
        
        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        private void setNonImmersive() {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LOW_PROFILE);
        }
        
        @TargetApi(Build.VERSION_CODES.KITKAT)
        private void setTranscluentPanels() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION | WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            }
        }
        
    }
}
