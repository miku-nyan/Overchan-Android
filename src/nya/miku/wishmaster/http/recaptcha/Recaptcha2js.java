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

package nya.miku.wishmaster.http.recaptcha;

import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.http.interactive.InteractiveException;

import java.util.concurrent.atomic.AtomicBoolean;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.view.ViewGroup;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Объект рекапчи 2.0, работает в обычном режиме (js через webview).<br>
 * Работа через прокси не поддерживается.<br>
 * Решённые капчи (значения, которые нужно будет передавать как "g-recaptcha-response") будут сохраняться в стеке объекта {@link Recaptcha2solved}.
 * @author miku-nyan
 *
 */
public class Recaptcha2js extends InteractiveException {
    private static final long serialVersionUID = 1L;
    
    private static final String INTERCEPT = "_intercept?";
    private static final String FALLBACK_INTERCEPT = "_fallback";
    private static final String FALLBACK_FILTER = "g-recaptcha-response=";
    
    private final String baseUrl, publicKey, sToken;
    
    private static final String getRecahtchaHtml(String publicKey, String sToken) {
        return
            "<script type=\"text/javascript\">" +
                "window.globalOnCaptchaEntered = function(res) { " +
                    "location.href = \"" + INTERCEPT + "\" + res; " +
                "}" +
            "</script>" +
            "<script src=\"https://www.google.com/recaptcha/api.js\" async defer></script>" +
            "<form action=\"" + FALLBACK_INTERCEPT + "\" method=\"GET\" id=\"_overchan_submitform\">" +
                "<div class=\"g-recaptcha\" data-sitekey=\"" + publicKey + "\" " +
                    (sToken != null && sToken.length() > 0 ? ("data-stoken=\"" + sToken + "\" ") : "") +
                    "data-callback=\"globalOnCaptchaEntered\"></div>" +
            "</form>" +
            "<script type=\"text/javascript\">" +
                "function _overchan_add_fallback_submit() { " +
                    "var element = document.createElement(\"input\"); " +
                    "element.setAttribute(\"type\", \"submit\"); " +
                    "element.setAttribute(\"value\", \"Submit\");" +
                    "var foo = document.getElementById(\"_overchan_submitform\"); " +
                    "foo.appendChild(element); " +
                "}" +
            "</script>";
    }
    
    private volatile boolean done = false;
    
    /**
     * @param baseUrl URL, с которого должна открываться капча
     * @param publicKey открытый ключ
     * @param sToken Secure Token
     */
    public Recaptcha2js(String baseUrl, String publicKey, String sToken) {
        this.baseUrl = baseUrl;
        this.publicKey = publicKey;
        this.sToken = sToken;
    }
    
    @Deprecated
    public Recaptcha2js(String publicKey) {
        this(null, publicKey, null);
    }
    
    @Override
    public String getServiceName() {
        return "Recaptcha";
    }
    
    @Override
    public void handle(final Activity activity, final CancellableTask task, final Callback callback) {
        if (task.isCancelled()) return;
        activity.runOnUiThread(new Runnable() {
            @SuppressLint("SetJavaScriptEnabled")
            @Override
            public void run() {
                final Dialog dialog = new Dialog(activity);
                WebView webView = new WebView(activity);
                webView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                webView.setWebViewClient(new WebViewClient() {
                    AtomicBoolean fallbackButtonAdded = new AtomicBoolean(false);
                    @Override
                    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                        handler.proceed();
                    }
                    @Override
                    public void onPageStarted(WebView view, String url, Bitmap favicon) {
                        if (url.contains(INTERCEPT) || url.contains(FALLBACK_INTERCEPT)) {
                            String hash = url.contains(INTERCEPT) ? url.substring(url.indexOf(INTERCEPT) + INTERCEPT.length()) :
                                (url.indexOf(FALLBACK_FILTER) != -1 ? url.substring(url.indexOf(FALLBACK_FILTER) + FALLBACK_FILTER.length()) : null);
                            Recaptcha2solved.push(publicKey, hash != null ? hash : "NULL");
                            if (!done && !task.isCancelled()) activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (!done && !task.isCancelled()) {
                                        done = true;
                                        callback.onSuccess();
                                        dialog.dismiss();
                                    }
                                }
                            });
                        }
                        super.onPageStarted(view, url, favicon);
                    }
                    @Override
                    public void onLoadResource(WebView view, String url) {
                        if (url.contains("/api/fallback?") && fallbackButtonAdded.compareAndSet(false, true)) {
                            view.loadUrl("javascript:_overchan_add_fallback_submit()");
                        }
                        super.onLoadResource(view, url);
                    }
                });
                //webView.getSettings().setUserAgentString(HttpConstants.USER_AGENT_STRING);
                webView.getSettings().setJavaScriptEnabled(true);
                dialog.setTitle("Recaptcha");
                dialog.setContentView(webView);
                dialog.setCanceledOnTouchOutside(false);
                dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        if (!done && !task.isCancelled()) {
                            done = true;
                            callback.onError("Cancelled");
                        }
                    }
                });
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                dialog.show();
                String url = baseUrl != null ? baseUrl : "https://127.0.0.1/";
                webView.loadDataWithBaseURL(url, getRecahtchaHtml(publicKey, sToken), "text/html", "UTF-8", null);
            }
        });
    }
}
