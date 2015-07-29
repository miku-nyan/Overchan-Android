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
import nya.miku.wishmaster.http.cloudflare.InteractiveException;
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
    
    private final String publicKey;
    
    private static final String getRecahtchaHtml(String publicKey) {
        return
            "<script type=\"text/javascript\">" +
                "window.globalOnCaptchaEntered = function(res) { " +
                    "location.href = \"" + INTERCEPT + "\" + res; " +
                "}" +
            "</script>" +
            "<script src=\"https://www.google.com/recaptcha/api.js\" async defer></script>" +
            "<div class=\"g-recaptcha\" data-sitekey=\"" + publicKey + "\" data-callback=\"globalOnCaptchaEntered\"></div>";
    }
    
    private volatile boolean done = false;
    
    /**
     * @param publicKey открытый ключ
     */
    public Recaptcha2js(String publicKey) {
        this.publicKey = publicKey;
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
                    @Override
                    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                        handler.proceed();
                    }
                    @Override
                    public void onPageStarted(WebView view, String url, Bitmap favicon) {
                        if (url.contains(INTERCEPT)) {
                            String hash = url.substring(url.indexOf(INTERCEPT) + INTERCEPT.length());
                            Recaptcha2solved.push(publicKey, hash);
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
                webView.loadDataWithBaseURL("https://127.0.0.1/", getRecahtchaHtml(publicKey), "text/html", "UTF-8", null);
            }
        });
    }
}
