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

package nya.miku.wishmaster.common;

import java.io.File;

import nya.miku.wishmaster.R;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.Preference;
import android.provider.DocumentsContract;
import android.view.ActionMode;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

/**
 * Класс, содержащий вызовы методов из новых API, недоступных в ранних версиях Android
 * @author miku-nyan
 *
 */
public class CompatibilityImpl {
    private CompatibilityImpl() {}
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void setIcon(Preference preference, int iconResId) {
        preference.setIcon(iconResId);
    }
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void setIcon(Preference preference, Drawable icon) {
        preference.setIcon(icon);
    }
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void activeActionBar(Activity activity) {
        activity.getActionBar().setDisplayHomeAsUpEnabled(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            setHomeButtonEnabledTrue(activity.getActionBar());
        }
    }
    
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private static void setHomeButtonEnabledTrue(ActionBar actionBar) {
        actionBar.setHomeButtonEnabled(true);
    }
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void setShowAsActionIfRoom(MenuItem item) {
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
    }
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void setShowAsActionAlways(MenuItem item) {
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void setActionBarNoIcon(Activity activity) {
        ActionBar actionBar = activity.getActionBar();
        if (actionBar != null) actionBar.setDisplayShowHomeEnabled(false);
    }
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static boolean hideActionBar(Activity activity) {
        ActionBar actionBar = activity.getActionBar();
        if (actionBar == null || !actionBar.isShowing()) return false;
        actionBar.hide();
        return true;
    }
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static boolean showActionBar(Activity activity) {
        ActionBar actionBar = activity.getActionBar();
        if (actionBar == null || actionBar.isShowing()) return false;
        actionBar.show();
        return true;
    }
    
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public static void setActionBarCustomFavicon(Activity activity, Drawable icon) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            //activity.getActionBar().setDisplayShowHomeEnabled(true);
            //activity.getActionBar().setIcon(icon);
        } else {
            activity.getActionBar().setIcon(icon);
        }
    }
    
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public static void setActionBarDefaultIcon(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            //activity.getActionBar().setDisplayShowHomeEnabled(false);
        } else {
            activity.getActionBar().setIcon(R.drawable.ic_launcher);
        }
    }
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void setCustomSelectionActionModeMenuCallback(TextView textView, final int titleRes, final int iconRes, final Runnable callback) {
        textView.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                setShowAsActionAlways(menu.add(Menu.NONE, 1, Menu.FIRST, titleRes).setIcon(iconRes));
                menu.removeItem(android.R.id.selectAll);
                return true;
            }
            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }
            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item.getItemId() == 1) {
                    callback.run();
                    mode.finish();
                    return true;
                }
                return false;
            }
            @Override
            public void onDestroyActionMode(ActionMode mode) {
            }
        });
    }
    
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public static void setActionBarNoBackground(Activity activity) {
        try {
            activity.getActionBar().setBackgroundDrawable(null);
        } catch (Exception e) {}
    }
    
    @TargetApi(Build.VERSION_CODES.FROYO)
    public static File getExternalCacheDir(ContextWrapper context) {
        return context.getExternalCacheDir();
    }
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void copyToClipboardAPI11(Activity activity, String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
    }
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    public static Point getDisplaySize(Display display) {
        Point size = new Point();
        display.getSize(size);
        return size;
    }
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static boolean isTextSelectable(TextView textView) {
        return textView.isTextSelectable();
    }
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void setTextIsSelectable(TextView textView) {
        textView.setTextIsSelectable(true);
    }
    
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static void clearCookies(CookieManager cookieManager) {
        cookieManager.removeAllCookies(null);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static void removeOnGlobalLayoutListener(View view, OnGlobalLayoutListener onGlobalLayoutListener) {
        view.getViewTreeObserver().removeOnGlobalLayoutListener(onGlobalLayoutListener);
    }
    
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static boolean isDocumentUri(Context context, Uri uri) {
        return DocumentsContract.isDocumentUri(context, uri);
    }
    
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static String getDocumentId(Uri uri) {
        return DocumentsContract.getDocumentId(uri);
    }
    
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public static void setDimAmount(Window window, float f) {
        window.setDimAmount(f);
    }
    
    @TargetApi(Build.VERSION_CODES.FROYO)
    public static File getDefaultDownloadDir() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }
    
    @TargetApi(Build.VERSION_CODES.ECLAIR)
    public static void setScrollbarFadingEnabled(WebView webView, boolean fadeScrollbars) {
        webView.setScrollbarFadingEnabled(fadeScrollbars);
    }
    
    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.ECLAIR_MR1)
    public static void setDefaultZoomFAR(WebSettings settings) {
        settings.setDefaultZoom(WebSettings.ZoomDensity.FAR);
    }
    
    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.ECLAIR_MR1)
    public static void setDefaultZoomCLOSE(WebSettings settings) {
        settings.setDefaultZoom(WebSettings.ZoomDensity.CLOSE);
    }
    
    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.ECLAIR_MR1)
    public static void setDefaultZoomMEDIUM(WebSettings settings) {
        settings.setDefaultZoom(WebSettings.ZoomDensity.MEDIUM);
    }
    
    @TargetApi(Build.VERSION_CODES.ECLAIR_MR1)
    public static void setLoadWithOverviewMode(WebSettings settings, boolean overview) {
        settings.setLoadWithOverviewMode(overview);
    }
    
    @TargetApi(Build.VERSION_CODES.FROYO)
    public static void setBlockNetworkLoads(WebSettings settings, boolean flag) {
        settings.setBlockNetworkLoads(flag);
    }

    @TargetApi(Build.VERSION_CODES.ECLAIR)
    public static void setVideoViewZOrderOnTop(VideoView videoView) {
        videoView.setZOrderOnTop(true);
    }
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void recreateActivity(Activity activity) {
        activity.recreate();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static void setImageAlpha(ImageView imageView, int alpha) {
        imageView.setImageAlpha(alpha);
    }
    
}
