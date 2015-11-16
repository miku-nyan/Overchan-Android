/*
 * Установка картинки в WebView с js-скриптом 
 * Код частично из AndroidQuery.com
 * https://github.com/androidquery/androidquery/blob/0.26.9/src/com/androidquery/util/WebImage.java
 * https://github.com/androidquery/androidquery/blob/0.26.9/src/com/androidquery/util/web_image.html 
 */

package nya.miku.wishmaster.lib.gallery;

import java.io.File;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.webkit.WebSettings;
import android.webkit.WebView;
import nya.miku.wishmaster.common.CompatibilityImpl;
import nya.miku.wishmaster.ui.AppearanceUtils;

public class JSWebView {
    private static final String TEMPLATE =
            "<html>" +
                "<meta name=\"viewport\" content=\"initial-scale=1, minimum-scale=1, maximum-scale=10, user-scalable=1\">" +
                "<body style=\"margin:0px;padding:0px;\"> " +
                    "<script>" +
                        "var ratio = 10; " +
                        "var draw; " +
                        
                        "function now() { " +
                            "var d = new Date(); " +
                            "return d.getTime(); " +
                        "} " +
                        
                        "function change() { " +
                            "resize(false); " +
                        "} " +
                        
                        "function resize(force) { " +
                            "var w = window.innerWidth; " +
                            "var h = window.innerHeight; " +
                            
                            "if (w == 0 || h == 0) return; " +
                            
                            "var r = w / h; " +
                            
                            "var diff = Math.abs((ratio - r) / r); " +
                            
                            "var n = now(); " +
                            "if (diff > 0.1) { " +
                                "draw = n + 300; " +
                            "} " +
                            
                            "if (force || n < draw) { " +
                                "ratio = r; " +
                                
                                "var box = document.getElementById(\"box\"); " +
                                "box.style.width = w; " +
                                "box.style.height = h; " +
                                
                                "var img = document.getElementById(\"img\"); " +
                                "var wScale = img.clientWidth / w;"  +
                                "var hScale = img.clientHeight / h; " +
                                "if (wScale >= hScale) { " +
                                    "img.style.width = img.clientWidth > 0 ? Math.min(w, img.clientWidth * 2) : w; " +
                                    "img.style.height = ''; " +
                                "} else { " +
                                    "img.style.width = ''; " +
                                    "img.style.height = img.clientHeight > 0 ? Math.min(h, img.clientHeight * 2) : h; " +
                                "} " +
                            "} " +
                        "} " +
                        
                    "</script>" +
                    
                    "<div id=\"box\" style=\"vertical-align:middle;text-align:center;display:table-cell;\">" +
                        "<img id=\"img\" src=\"%s\" style=\"display:none;\" onload=\"resize(true);this.style.display='inline';\"/>" +
                    "</div>" +
                    
                    "<script>" +
                        "window.onload = function() { " +
                            "resize(true); " +
                            "window.onresize = change; " +
                        "} " +
                    "</script>" +
                "</body>" +
            "</html>";
    
    @SuppressLint("SetJavaScriptEnabled")
    public static void setImage(final WebView webView, final File file) {
        fixWebViewTip(webView.getContext());
        
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1) {
            webView.setDrawingCacheEnabled(true);
        }
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
            CompatibilityImpl.setScrollbarFadingEnabled(webView, true);
        }
        
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setBuiltInZoomControls(true);
        settings.setSupportZoom(true);
        settings.setAllowFileAccess(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
            CompatibilityImpl.setBlockNetworkLoads(settings, true);
        }
        
        Runnable setup = new Runnable() {
            @Override
            public void run() {
                String html = String.format(TEMPLATE, Uri.fromFile(file).toString());
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
            }
        };
        
        if (webView.getWidth() > 0) {
            setup.run();
        } else {
            webView.loadData("<html></html>", "text/html", "utf-8");
            AppearanceUtils.callWhenLoaded(webView, setup);
        }
    }
    
    private static void fixWebViewTip(Context context){
        SharedPreferences prefs = context.getSharedPreferences("WebViewSettings", Context.MODE_PRIVATE);
        if (prefs.getInt("double_tap_toast_count", 1) > 0) {
            prefs.edit().putInt("double_tap_toast_count", 0).commit();
        }
    }
}
