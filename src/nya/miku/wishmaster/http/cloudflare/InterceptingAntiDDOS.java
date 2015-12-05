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

package nya.miku.wishmaster.http.cloudflare;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.HttpConstants;
import nya.miku.wishmaster.http.client.ExtendedHttpClient;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;

import cz.msebera.android.httpclient.client.CookieStore;
import cz.msebera.android.httpclient.cookie.Cookie;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.net.http.SslError;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Anti-DDOS проверка с перехватом запросов от webview в httpclient (требуется API >= 11)
 * @author miku-nyan
 *
 */

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
/*package*/ class InterceptingAntiDDOS {
    private static final String TAG = "CloudflareChecker_Intercept";
    
    private InterceptingAntiDDOS() {}
    private static InterceptingAntiDDOS instanace;
    static synchronized InterceptingAntiDDOS getInstance() {
        if (instanace == null) instanace = new InterceptingAntiDDOS();
        return instanace;
    }
    
    private volatile boolean processing = false;
    private volatile boolean processing2 = false;
    private volatile Cookie currentCookie;
    private volatile WebView webView;
    private Object lock = new Object();
    
    /*package*/ boolean isProcessing() {
        return processing;
    }
    
    /** Метод anti-DDOS проверки, все запросы перехватываются из webview в httpclient (для использования с прокси-сервером на API >= 11) */
    Cookie check(final CloudflareException exception, final ExtendedHttpClient httpClient, final CancellableTask task, final Activity activity) {
        synchronized (lock) {
            if (processing) return null;
            processing = true;
        }
        processing2 = true;
        currentCookie = null;
        
        final HttpRequestModel rqModel = HttpRequestModel.builder().setGET().build();
        final CookieStore cookieStore = httpClient.getCookieStore();
        CloudflareChecker.removeCookie(cookieStore, exception.getRequiredCookieName());
        final ViewGroup layout = (ViewGroup)activity.getWindow().getDecorView().getRootView();
        final WebViewClient client = new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }
            
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                HttpResponseModel responseModel = null;
                try {
                    responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, null, task);
                    for (int i = 0; i < 3  && responseModel.statusCode == 400; ++i) {
                        Logger.d(TAG, "HTTP 400");
                        responseModel.release();
                        responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, null, task);
                    }
                    for (Cookie cookie : cookieStore.getCookies()) {
                        if (CloudflareChecker.isClearanceCookie(cookie, url, exception.getRequiredCookieName())) {
                            Logger.d(TAG, "Cookie found: " + cookie.getValue());
                            currentCookie = cookie;
                            processing2 = false;
                            return new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream("cookie received".getBytes()));
                        }
                    }
                    BufOutputStream output = new BufOutputStream();
                    IOUtils.copyStream(responseModel.stream, output);
                    return new WebResourceResponse(null, null, output.toInputStream());
                } catch (Exception e) {
                    Logger.e(TAG, e);
                } finally {
                    if (responseModel != null) responseModel.release();
                }
                return new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream("something wrong".getBytes()));
            }
        };
        
        activity.runOnUiThread(new Runnable() {
            @SuppressLint("SetJavaScriptEnabled")
            @Override
            public void run() {
                webView = new WebView(activity);
                webView.setVisibility(View.GONE);
                layout.addView(webView);
                webView.setWebViewClient(client);
                webView.getSettings().setUserAgentString(HttpConstants.USER_AGENT_STRING);
                webView.getSettings().setJavaScriptEnabled(true);
                webView.loadUrl(exception.getCheckUrl());
            }
        });

        long startTime = System.currentTimeMillis();
        while (processing2) {
            long time = System.currentTimeMillis() - startTime;
            if ((task != null && task.isCancelled()) || time > CloudflareChecker.TIMEOUT) {
                processing2 = false;
            }
        }
        
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    layout.removeView(webView);
                    webView.stopLoading();
                    webView.clearCache(true);
                    webView.destroy();
                    webView = null;
                } finally {
                    processing = false;
                }
            }
        });
        return currentCookie;
    }
    
    private static class BufOutputStream extends ByteArrayOutputStream {
        public BufOutputStream() {
            super(1024);
        }
        public InputStream toInputStream() {
            return new ByteArrayInputStream(buf, 0, count);
        }
    }
}
