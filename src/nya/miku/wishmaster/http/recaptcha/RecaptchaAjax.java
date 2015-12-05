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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.http.client.ExtendedHttpClient;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.HttpHost;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.conn.params.ConnRouteParams;
import cz.msebera.android.httpclient.message.BasicHeader;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.net.http.SslError;
import android.os.Build;
import android.os.Handler;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

@SuppressWarnings("deprecation")
public class RecaptchaAjax {
    private static final String TAG = "RecaptchaAjax";
    
    private static final long TIMEOUT = 30 * 1000;
    
    private static final String CUSTOM_UA = "Mozilla/5.0";
    
    private static final String CHALLENGE_FILTER = "/recaptcha/api/image?c=";
    
    private RecaptchaAjax() {}
    
    private static Handler sHandler = null;
    
    public static void init() {
        sHandler = new Handler();
    }
    
    static String getChallenge(String key, CancellableTask task, HttpClient httpClient, String scheme) throws Exception {
        if (sHandler == null) throw new Exception("handler == null (not initialized in UI thread)");
        if (scheme == null) scheme = "http";
        String address = scheme + "://127.0.0.1/";
        String data = "<script type=\"text/javascript\"> " +
                          "var RecaptchaOptions = { " +
                              "theme : 'custom', " +
                              "custom_theme_widget: 'recaptcha_widget' " +
                          "}; " +
                      "</script>" +
                      "<div id=\"recaptcha_widget\" style=\"display:none\"> " +
                          "<div id=\"recaptcha_image\"></div> " +
                          "<input type=\"text\" id=\"recaptcha_response_field\" name=\"recaptcha_response_field\" /> " +
                      "</div>" +
                      "<script type=\"text/javascript\" src=\"" + scheme + "://www.google.com/recaptcha/api/challenge?k=" + key + "\"></script>";
        
        HttpHost proxy = null;
        if (httpClient instanceof ExtendedHttpClient) {
            proxy = ((ExtendedHttpClient) httpClient).getProxy();
        } else if (httpClient != null) {
            try {
                proxy = ConnRouteParams.getDefaultProxy(httpClient.getParams());
            } catch (Exception e) { /*ignore*/ }
        }
        if (proxy != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            return Intercepting.getInternal(address, data, task, httpClient);
        } else {
            return getChallengeInternal(address, data, task, proxy);
        }
    }
    
    private static class Holder {
        volatile WebView webView = null;
        volatile String challenge = null;
    }
    
    private static String getChallengeFromImageUrl(String url) {
        if (url.contains(CHALLENGE_FILTER)) {
            String challenge = url.substring(url.indexOf(CHALLENGE_FILTER) + CHALLENGE_FILTER.length());
            int index = challenge.indexOf('&');
            if (index >= 0) challenge = challenge.substring(0, index);
            if (challenge.length() > 0) return challenge;
        }
        return null;
    }
    
    private static String getChallengeInternal(final String address, final String data, CancellableTask task, final HttpHost proxy) throws Exception {
        Logger.d(TAG, "not intercepting; proxy: " + (proxy == null ? "disabled" : "enabled"));
        if (proxy != null) {
            Logger.d(TAG, "AJAX recaptcha not using (proxy and old API)");
            throw new Exception("proxy && old API");
            //костыль с установкой прокси через reflection не используется, т.к. в отличие от js-antiddos, здесь не критично (получит noscript капчу)
        }
        final Context context = MainApplication.getInstance();
        final Holder holder = new Holder();
        
        sHandler.post(new Runnable() {
            @SuppressLint("SetJavaScriptEnabled")
            @Override
            public void run() {
                holder.webView = new WebView(context);
                holder.webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                        handler.proceed();
                    }
                    @Override
                    public void onLoadResource(WebView view, String url) {
                        String challenge = getChallengeFromImageUrl(url);
                        if (challenge != null) holder.challenge = challenge;
                        super.onLoadResource(view, url);
                    }
                });
                holder.webView.getSettings().setUserAgentString(CUSTOM_UA);
                holder.webView.getSettings().setJavaScriptEnabled(true);
                holder.webView.loadDataWithBaseURL(address, data, "text/html", "UTF-8", null);
            }
        });
        
        long startTime = System.currentTimeMillis();
        while (holder.challenge == null) {
            long time = System.currentTimeMillis() - startTime;
            if ((task != null && task.isCancelled()) || time > TIMEOUT) break;
            Thread.yield();
        }
        
        sHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    holder.webView.stopLoading();
                    holder.webView.clearCache(true);
                    holder.webView.destroy();
                } catch (Exception e) {
                    Logger.e(TAG, e);
                }
            }
        });
        
        if (holder.challenge == null) throw new RecaptchaException("couldn't get Recaptcha Challenge (AJAX)");
        return holder.challenge;
    }
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static class Intercepting {
        private static String getInternal(final String url, final String data, final CancellableTask task, final HttpClient client) throws Exception {
            Logger.d(TAG, "intercepting");
            final Context context = MainApplication.getInstance();
            final Holder holder = new Holder();
            Header[] uaHeader = new Header[] { new BasicHeader(HttpHeaders.USER_AGENT, CUSTOM_UA) };
            final HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCustomHeaders(uaHeader).build();
            
            sHandler.post(new Runnable() {
                @SuppressLint("SetJavaScriptEnabled")
                @Override
                public void run() {
                    holder.webView = new WebView(context);
                    holder.webView.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                            handler.proceed();
                        }
                        @Override
                        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                            String challenge = getChallengeFromImageUrl(url);
                            if (challenge != null) holder.challenge = challenge;
                            
                            if (url.startsWith("http://127.0.0.1") || url.startsWith("https://127.0.0.1"))
                                return new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream("127.0.0.1".getBytes()));
                            
                            HttpResponseModel responseModel = null;
                            try {
                                responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, client, null, task);
                                for (int i = 0; i < 3  && responseModel.statusCode == 400; ++i) {
                                    Logger.d(TAG, "HTTP 400");
                                    responseModel.release();
                                    responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, client, null, task);
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
                    });
                    holder.webView.getSettings().setJavaScriptEnabled(true);
                    holder.webView.loadDataWithBaseURL(url, data, "text/html", "UTF-8", null);
                }
            });
            
            long startTime = System.currentTimeMillis();
            while (holder.challenge == null) {
                long time = System.currentTimeMillis() - startTime;
                if ((task != null && task.isCancelled()) || time > TIMEOUT) break;
                Thread.yield();
            }
            
            sHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        holder.webView.stopLoading();
                        holder.webView.clearCache(true);
                        holder.webView.destroy();
                    } catch (Exception e) {
                        Logger.e(TAG, e);
                    }
                }
            });
            
            if (holder.challenge == null) throw new RecaptchaException("couldn't get Recaptcha Challenge (AJAX-Intercept)");
            return holder.challenge;
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
    
}
