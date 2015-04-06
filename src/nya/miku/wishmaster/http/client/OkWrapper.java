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

package nya.miku.wishmaster.http.client;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.HttpConstants;
import okio.BufferedSink;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.RequestLine;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.Configurable;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.CookieSpec;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.cookie.SM;
import org.apache.http.cookie.SetCookie2;
import org.apache.http.entity.InputStreamEntityHC4;
import org.apache.http.impl.cookie.BrowserCompatSpecHC4;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import android.annotation.TargetApi;
import android.os.Build;

import com.squareup.okhttp.Headers;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;

/**
 * Обёртка над {@link OkHttpClient} в интерфейс Apache {@link HttpClient}.<br>
 * Экспериментальное решение для поддержки SPDY.
 * @author miku-nyan
 *
 */

/* Google пометила все классы и интерфейсы пакета org.apache.http как "deprecated" в API 22 (Android 5.1)
 * На самом деле используется актуальная версия apache-hc httpclient 4.3.5.1-android
 * Подробности: https://issues.apache.org/jira/browse/HTTPCLIENT-1632 */
@SuppressWarnings("deprecation")

@TargetApi(Build.VERSION_CODES.GINGERBREAD)
public class OkWrapper implements HttpClient {
    private static final String TAG = "OkWrapper";
    
    private final OkHttpClient client;
    private final int defaultTimeout;
    private final boolean defaultEnableRedirects;
    
    private static final HostnameVerifier ALLOW_ALL_HOSTNAME = new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };
    
    public OkWrapper(boolean safe, HttpHost proxy, CookieStore cookieStore) {
        this.defaultTimeout = HttpConstants.DEFAULT_HTTP_TIMEOUT;
        this.defaultEnableRedirects = true;
        
        client = new OkHttpClient();
        client.interceptors().add(new UserAgentInterceptor(HttpConstants.USER_AGENT_STRING));
        client.setCookieHandler(new CookieStoreWrapper(cookieStore));
        client.setReadTimeout(defaultTimeout, TimeUnit.MILLISECONDS);
        client.setConnectTimeout(defaultTimeout, TimeUnit.MILLISECONDS);
        client.setFollowRedirects(defaultEnableRedirects);
        client.setFollowSslRedirects(defaultEnableRedirects);
        if (proxy != null) {
            client.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxy.getHostName(), proxy.getPort())));
        }
        
        if (!safe) {
            try {
                TrustManager tm = new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
                    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                };
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[] { tm }, null);
                client.setSslSocketFactory(sslContext.getSocketFactory());
            } catch (Exception e) {
                Logger.e(TAG, "cannot set the unsafe SSL socket factory", e);
            }
            client.setHostnameVerifier(ALLOW_ALL_HOSTNAME);
        }
        
    }
    
    private static Request transformRequest(HttpRequest request) {
        Request.Builder builder = new Request.Builder();
        
        RequestLine requestLine = request.getRequestLine();
        String method = requestLine.getMethod();
        builder.url(requestLine.getUri());
        
        String contentType = null;
        for (Header header : request.getAllHeaders()) {
            String name = header.getName();
            if ("Content-Type".equals(name)) {
                contentType = header.getValue();
            } else {
                builder.header(name, header.getValue());
            }
        }
        
        RequestBody body = null;
        if (request instanceof HttpEntityEnclosingRequest) {
            HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
            if (entity != null) {
                // Wrap the entity in a custom Body which takes care of the content, length, and type.
                body = new HttpEntityBody(entity, contentType);
                
                Header encoding = entity.getContentEncoding();
                if (encoding != null) {
                    builder.header(encoding.getName(), encoding.getValue());
                }
            }
        }
        builder.method(method, body);
        
        return builder.build();
    }
    
    private static HttpResponse transformResponse(Response response) throws IOException {
        Logger.d(TAG, "Protocol: " + response.protocol().toString());
        int code = response.code();
        String message = response.message();
        BasicHttpResponse httpResponse = new BasicHttpResponse(HttpVersion.HTTP_1_1, code, message);
        
        ResponseBody body = response.body();
        InputStreamEntityHC4 entity = new InputStreamEntityHC4(body.byteStream(), body.contentLength());
        httpResponse.setEntity(entity);
        
        Headers headers = response.headers();
        for (int i = 0, size = headers.size(); i < size; ++i) {
            String name = headers.name(i);
            String value = headers.value(i);
            httpResponse.addHeader(name, value);
            if ("Content-Type".equalsIgnoreCase(name)) {
                entity.setContentType(value);
            } else if ("Content-Encoding".equalsIgnoreCase(name)) {
                entity.setContentEncoding(value);
            }
        }
        
        return httpResponse;
    }
    
    @Override
    public HttpResponse execute(HttpUriRequest request) throws IOException {
        return execute(null, request, (HttpContext) null);
    }
    
    @Override
    public HttpResponse execute(HttpUriRequest request, HttpContext context) throws IOException {
        return execute(null, request, context);
    }
    
    @Override
    public HttpResponse execute(HttpHost host, HttpRequest request) throws IOException {
        return execute(host, request, (HttpContext) null);
    }
    
    @Override
    public HttpResponse execute(HttpHost host, HttpRequest request, HttpContext context) throws IOException {
        OkHttpClient usingClient = client;
        
        int timeoutValue = defaultTimeout;
        boolean enableRedirects = defaultEnableRedirects;
        
        RequestConfig config = (request instanceof Configurable) ? ((Configurable) request).getConfig() : null;
        if (config != null) {
            timeoutValue = config.getConnectTimeout();
            enableRedirects = config.isRedirectsEnabled();
        } else {
            final HttpParams params = request.getParams();
            if (params != null) {
                timeoutValue = params.getIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, defaultTimeout);
                enableRedirects = params.getBooleanParameter(ClientPNames.HANDLE_REDIRECTS, true);
            }
        }
        
        if (timeoutValue != defaultTimeout || enableRedirects != defaultEnableRedirects) {
            usingClient = client.clone();
            usingClient.setReadTimeout(timeoutValue, TimeUnit.MILLISECONDS);
            usingClient.setConnectTimeout(timeoutValue, TimeUnit.MILLISECONDS);
            usingClient.setFollowRedirects(enableRedirects);
            usingClient.setFollowSslRedirects(enableRedirects);
        }
        
        Request okRequest = transformRequest(request);
        Response okResponse = usingClient.newCall(okRequest).execute();
        return transformResponse(okResponse);
    }
    
    @Override
    public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> handler) throws IOException {
        return execute(null, request, handler, null);
    }
    
    @Override
    public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> handler, HttpContext context) throws IOException {
        return execute(null, request, handler, context);
    }
    
    @Override
    public <T> T execute(HttpHost host, HttpRequest request, ResponseHandler<? extends T> handler) throws IOException {
        return execute(host, request, handler, null);
    }
    
    @Override
    public <T> T execute(HttpHost host, HttpRequest request, ResponseHandler<? extends T> handler, HttpContext context) throws IOException {
        HttpResponse response = execute(host, request, context);
        try {
            return handler.handleResponse(response);
        } finally {
            consumeContentQuietly(response);
        }
    }
    
    @Override
    public ClientConnectionManager getConnectionManager() {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public HttpParams getParams() {
        throw new UnsupportedOperationException();
    }
    
    private static void consumeContentQuietly(HttpResponse response) {
        try {
            response.getEntity().consumeContent();
        } catch (Throwable ignored) {}
    }
    
    private class UserAgentInterceptor implements Interceptor {
        private final String userAgent;
        
        public UserAgentInterceptor(String userAgent) {
            this.userAgent = userAgent;
        }
        
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request originalRequest = chain.request();
            Request requestWithUserAgent = originalRequest.newBuilder()
                .removeHeader("User-Agent")
                .addHeader("User-Agent", userAgent)
                .build();
            return chain.proceed(requestWithUserAgent);
        }
    }
    
    private static class CookieStoreWrapper extends CookieHandler {
        private static final String TAG = "CookieStoreWrapper";
        private static final CookieSpec BROWSER_COMPAT_SPEC = new BrowserCompatSpecHC4();
        private static final boolean COOKIES_LOGGING = false;
        
        private CookieStore cookieStore;
        
        public CookieStoreWrapper(CookieStore cookieStore) {
            this.cookieStore = cookieStore;
        }
        
        @Override
        public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) throws IOException {
            if (uri == null || requestHeaders == null) throw new IllegalArgumentException("Argument is null");
            final CookieSpec cookieSpec = BROWSER_COMPAT_SPEC;
            int port = uri.getPort();
            String path = uri.getPath();
            CookieOrigin origin = new CookieOrigin(
                    uri.getHost(),
                    port >= 0 ? port : 0,
                    path == null || path.length() == 0 ? "/" : path,
                    uri.getScheme().equalsIgnoreCase("https"));
            
            Map<String, List<String>> cookieMap = new HashMap<>();
            List<Cookie> matchedCookies = new ArrayList<>();
            for (Cookie cookie : cookieStore.getCookies()) {
                if (cookieSpec.match(cookie, origin)) {
                    matchedCookies.add(cookie);
                }
            }
            
            if (!matchedCookies.isEmpty()) {
                List<Header> headers = cookieSpec.formatCookies(matchedCookies);
                for (Header header : headers) {
                    cookieMap.put(header.getName(), Collections.singletonList(header.getValue()));
                }
            }
            
            final int ver = cookieSpec.getVersion();
            if (ver > 0) {
                boolean needVersionHeader = false;
                for (Cookie cookie : matchedCookies) {
                    if (ver != cookie.getVersion() || !(cookie instanceof SetCookie2)) {
                        needVersionHeader = true;
                    }
                }
                
                if (needVersionHeader) {
                    final Header header = cookieSpec.getVersionHeader();
                    if (header != null) {
                        cookieMap.put(header.getName(), Collections.singletonList(header.getValue()));
                    }
                }
            }
            
            return Collections.unmodifiableMap(cookieMap);
        }

        @Override
        public void put(URI uri, Map<String, List<String>> responseHeaders) throws IOException {
            if (uri == null || responseHeaders == null) throw new IllegalArgumentException("Argument is null");
            final CookieSpec cookieSpec = BROWSER_COMPAT_SPEC;
            int port = uri.getPort();
            String path = uri.getPath();
            CookieOrigin origin = new CookieOrigin(
                    uri.getHost(),
                    port >= 0 ? port : 0,
                    path == null || path.length() == 0 ? "/" : path,
                    uri.getScheme().equalsIgnoreCase("https"));
            
            
            processCookies(responseHeaders, SM.SET_COOKIE, cookieSpec, origin);
            if (cookieSpec.getVersion() > 0) {
                processCookies(responseHeaders, SM.SET_COOKIE2, cookieSpec, origin);
            }
        }
        
        private void processCookies(Map<String, List<String>> responseHeaders, String headerName, CookieSpec cookieSpec, CookieOrigin origin) {
            for (String key : responseHeaders.keySet()) {
                if (key.equalsIgnoreCase(headerName)) {
                    for (String value : responseHeaders.get(key)) {
                        Header header = new BasicHeader(headerName, value);
                        try {
                            List<Cookie> cookies = cookieSpec.parse(header, origin);
                            for (Cookie cookie : cookies) {
                                try {
                                    cookieSpec.validate(cookie, origin);
                                    cookieStore.addCookie(cookie);
                                } catch (MalformedCookieException e) {
                                    if (COOKIES_LOGGING) {
                                        Logger.e(TAG, "Cookie rejected: "+cookie, e);
                                    }
                                }
                            }
                        } catch (MalformedCookieException e) {
                            if (COOKIES_LOGGING) {
                                Logger.e(TAG, "Invalid cookie header: "+value, e);
                            }
                        }
                    }
                }   
            }
        }
    }
    
    /** Adapts an {@link HttpEntity} to OkHttp's {@link RequestBody}. */
    private static class HttpEntityBody extends RequestBody {
        private static final MediaType DEFAULT_MEDIA_TYPE = MediaType.parse("application/octet-stream");
        
        private final HttpEntity entity;
        private final MediaType mediaType;
        
        public HttpEntityBody(HttpEntity entity, String contentTypeHeader) {
            this.entity = entity;
            
            if (contentTypeHeader != null) {
                mediaType = MediaType.parse(contentTypeHeader);
            } else if (entity.getContentType() != null) {
                mediaType = MediaType.parse(entity.getContentType().getValue());
            } else {
                // Apache is forgiving and lets you skip specifying a content type with an entity. OkHttp is
                // not forgiving so we fall back to a generic type if it's missing.
                mediaType = DEFAULT_MEDIA_TYPE;
            }
        }

        @Override
        public long contentLength() {
            return entity.getContentLength();
        }

        @Override
        public MediaType contentType() {
            return mediaType;
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            entity.writeTo(sink.outputStream());
        }
    }
}
