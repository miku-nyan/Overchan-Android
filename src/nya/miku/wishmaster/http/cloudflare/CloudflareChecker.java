/*
 * Overchan Android (Meta Imageboard Client)
 * Copyright (C) 2014-2016  miku-nyan <https://github.com/miku-nyan>
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

import java.util.Date;
import java.util.Locale;

import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.HttpConstants;
import nya.miku.wishmaster.http.client.ExtendedHttpClient;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.WebViewProxy;
import nya.miku.wishmaster.ui.CompatibilityImpl;

import cz.msebera.android.httpclient.HttpHost;
import cz.msebera.android.httpclient.client.CookieStore;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.conn.params.ConnRouteParams;
import cz.msebera.android.httpclient.cookie.Cookie;
import cz.msebera.android.httpclient.cookie.SetCookie;
import cz.msebera.android.httpclient.impl.cookie.BasicClientCookie;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Выполнение Cloudflare-проверок
 * @author miku-nyan
 *
 */

@SuppressWarnings("deprecation")
public class CloudflareChecker {
    private static final String TAG = "CloudflareChecker";
    
    /** таймаут при анти-ддос проверке в милисекундах */
    public static final long TIMEOUT = 35 * 1000;
    
    private CloudflareChecker() {}
    private static CloudflareChecker instance;
    
    /** Получить объект-синглтон */
    public static synchronized CloudflareChecker getInstance() {
        if (instance == null) instance = new CloudflareChecker();
        return instance;
    }
    
    /** Возвращает false, если в данный момент (какая-либо) анти-ддос уже выполняется */
    public boolean isAvaibleAntiDDOS() {
        return !(processing || InterceptingAntiDDOS.getInstance().isProcessing());
    }
    
    /**
     * Пройти анти-ддос проверку cloudflare
     * @param exception Cloudflare исключение
     * @param httpClient HTTP клиент
     * @param task отменяемая задача
     * @param activity активность, в контексте которого будет запущен WebView (webkit)
     * @return полученная cookie или null, если проверка не прошла по таймауту, или проверка уже проходит в другом потоке
     */
    public Cookie checkAntiDDOS(CloudflareException exception, HttpClient httpClient, CancellableTask task, Activity activity) {
        if (exception.isRecaptcha()) throw new IllegalArgumentException();
        
        HttpHost proxy = null;
        if (httpClient instanceof ExtendedHttpClient) {
            proxy = ((ExtendedHttpClient) httpClient).getProxy();
        } else if (httpClient != null) {
            try {
                proxy = ConnRouteParams.getDefaultProxy(httpClient.getParams());
            } catch (Exception e) { /*ignore*/ }
        }
        if (proxy != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            if (httpClient instanceof ExtendedHttpClient) {
                return InterceptingAntiDDOS.getInstance().check(exception, (ExtendedHttpClient) httpClient, task, activity);
            } else {
                throw new IllegalArgumentException(
                        "cannot run anti-DDOS checking with proxy settings; http client is not instance of ExtendedHttpClient");
            }
        } else {
            return checkAntiDDOS(exception, proxy, task, activity);
        }
    }
    
    private volatile boolean processing = false;
    private volatile boolean processing2 = false;
    private volatile Cookie currentCookie;
    private volatile WebView webView;
    private volatile Context webViewContext;
    private Object lock = new Object();
    private Cookie checkAntiDDOS(final CloudflareException exception, final HttpHost proxy, CancellableTask task, final Activity activity) {
        synchronized (lock) {
            if (processing) return null;
            processing = true;
        }
        processing2 = true;
        currentCookie = null;
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            CookieSyncManager.createInstance(activity);
            CookieManager.getInstance().removeAllCookie();
        } else {
            CompatibilityImpl.clearCookies(CookieManager.getInstance());
        }
        
        final ViewGroup layout = (ViewGroup) activity.getWindow().getDecorView().getRootView();
        final WebViewClient client = new WebViewClient() {
            @Override
            public void onPageFinished(WebView webView, String url) {
                super.onPageFinished(webView, url);
                Logger.d(TAG, "Got Page: "+url);
                String value = null;
                try {
                    String[] cookies = CookieManager.getInstance().getCookie(url).split("[;]");
                    for (String cookie : cookies) {
                        if ((cookie != null) && (!cookie.trim().equals("")) && (cookie.startsWith(" " + exception.getRequiredCookieName() + "="))) {
                            value = cookie.substring(exception.getRequiredCookieName().length() + 2);
                        }
                    }
                } catch (NullPointerException e) {
                    Logger.e(TAG, e);
                }
                if (value != null) {
                    BasicClientCookie cf_cookie = new BasicClientCookie(exception.getRequiredCookieName(), value);
                    cf_cookie.setDomain("." + Uri.parse(url).getHost());
                    cf_cookie.setPath("/");
                    currentCookie = cf_cookie;
                    Logger.d(TAG, "Cookie found: "+value);
                    processing2 = false;
                } else {
                    Logger.d(TAG, "Cookie is not found");
                }
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
                webViewContext = webView.getContext();
                if (proxy != null) WebViewProxy.setProxy(webViewContext, proxy.getHostName(), proxy.getPort());
                webView.loadUrl(exception.getCheckUrl());
            }
        });

        long startTime = System.currentTimeMillis();
        while (processing2) {
            long time = System.currentTimeMillis() - startTime;
            if ((task != null && task.isCancelled()) || time > TIMEOUT) {
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
                    if (proxy != null) WebViewProxy.setProxy(webViewContext, null, 0);
                    processing = false;
                }
            }
        });
        
        return currentCookie;
    }
    
    /**
     * Проверить рекапчу cloudflare, получить cookie
     * @param exception Cloudflare исключение
     * @param httpClient HTTP клиент
     * @param task отменяемая задача
     * @param challenge challenge рекапчи
     * @param recaptchaAnswer ответ на рекапчу
     * @return полученная cookie или null, если проверка не прошла
     */
    public Cookie checkRecaptcha(CloudflareException exception, ExtendedHttpClient httpClient, CancellableTask task, String url) {
        if (!exception.isRecaptcha()) throw new IllegalArgumentException("wrong type of CloudflareException");
        HttpResponseModel responseModel = null;
        try {
            HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setNoRedirect(false).build();
            CookieStore cookieStore = httpClient.getCookieStore();
            removeCookie(cookieStore, exception.getRequiredCookieName());
            responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, null, task);
            for (int i = 0; i < 3  && responseModel.statusCode == 400; ++i) {
                Logger.d(TAG, "HTTP 400");
                responseModel.release();
                responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, null, task);
            }
            for (Cookie cookie : cookieStore.getCookies()) {
                if (isClearanceCookie(cookie, url, exception.getRequiredCookieName())) {
                    Logger.d(TAG, "Cookie found: " + cookie.getValue());
                    return cookie;
                }
            }
            Logger.d(TAG, "Cookie is not found");
        } catch (Exception e) {
            Logger.e(TAG, e);
        } finally {
            if (responseModel != null) {
                responseModel.release();
            }
        }
        return null;
    }
    
    static boolean isClearanceCookie(Cookie cookie, String url, String requiredCookieName) {
        try {
            String cookieName = cookie.getName();
            String cookieDomain = cookie.getDomain();
            if (!cookieDomain.startsWith(".")) {
                cookieDomain = "." + cookieDomain;
            }
            
            String urlCookie = "." + Uri.parse(url).getHost();
            if (cookieName.equals(requiredCookieName) && urlCookie.endsWith(cookieDomain.toLowerCase(Locale.US))) {
                return true;
            }
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
        return false;
    }
    
    static void removeCookie(CookieStore store, String name) {
        boolean flag = false;
        for (Cookie cookie : store.getCookies()) {
            if (cookie.getName().equals(name)) {
                if (cookie instanceof SetCookie) {
                    flag = true;
                    ((SetCookie) cookie).setExpiryDate(new Date(0));
                } else {
                    Logger.e(TAG, "cannot remove cookie (object does not implement SetCookie): " + cookie.toString());
                }
            }
        }
        if (flag) store.clearExpired(new Date());
    }
}
