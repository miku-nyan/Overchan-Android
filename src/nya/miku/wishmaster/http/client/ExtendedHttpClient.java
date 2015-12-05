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

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;

import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.HttpConstants;

import cz.msebera.android.httpclient.HttpHost;
import cz.msebera.android.httpclient.client.CookieStore;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.config.CookieSpecs;
import cz.msebera.android.httpclient.client.config.RequestConfig;
import cz.msebera.android.httpclient.conn.socket.LayeredConnectionSocketFactory;
import cz.msebera.android.httpclient.conn.ssl.SSLConnectionSocketFactory;
import cz.msebera.android.httpclient.conn.ssl.SSLContexts;
import cz.msebera.android.httpclient.conn.ssl.TrustStrategy;
import cz.msebera.android.httpclient.impl.client.BasicCookieStore;
import cz.msebera.android.httpclient.impl.client.HttpClients;

/**
 * Основной HTTP-клиент, используемый в проекте.<br>
 * Экземпляр хранит свои настройки HTTP-прокси сервера и объект хранилища Cookies,
 * см. методы {@link #getProxy()} и {@link #getCookieStore()}.
 * @author miku-nyan
 *
 */

public class ExtendedHttpClient extends HttpClientWrapper {
    private static final String TAG = "ExtendedHttpClient";
    
    private final CookieStore cookieStore;
    private final HttpHost proxy;
    
    /**
     * Получить хранилище Cookies данного экземпляра
     */
    public CookieStore getCookieStore() {
        return cookieStore;
    }
    
    /**
     * Получить значение HTTP-прокси данного экземпляра
     */
    public HttpHost getProxy() {
        return proxy;
    }
    
    /**
     * Конструктор
     * @param safe включить проверку сертификата и имени хоста для SSL
     * @param proxy адрес HTTP прокси (возможно null)
     */
    public ExtendedHttpClient(boolean safe, HttpHost proxy) {
        super();
        this.cookieStore = new BasicCookieStore();
        this.proxy = proxy;
        setClient(build(safe, proxy, cookieStore));
    }
    
    /**
     * Получить билдер конфига запросов с параметрами по умолчанию для данного класса
     * @param timeout значение таймпута
     */
    public static RequestConfig.Builder getDefaultRequestConfigBuilder(int timeout) {
        return RequestConfig.custom().
                setConnectTimeout(timeout).
                setConnectionRequestTimeout(timeout).
                setSocketTimeout(timeout).
                setCookieSpec(CookieSpecs.BROWSER_COMPATIBILITY).
                setStaleConnectionCheckEnabled(false);
    }
    
    private static HttpClient build(boolean safe, final HttpHost proxy, CookieStore cookieStore) {
        return HttpClients.custom().
                setDefaultRequestConfig(getDefaultRequestConfigBuilder(HttpConstants.DEFAULT_HTTP_TIMEOUT).build()).
                setUserAgent(HttpConstants.USER_AGENT_STRING).
                setProxy(proxy).
                setDefaultCookieStore(cookieStore).
                setSSLSocketFactory(obtainSSLSocketFactory(safe)).
                build();
    }
    
    /**
     * Получить фабрику сокетов SSL
     * @param safe безопасность, если false, проверка имени и сертификата будет отключена
     */
    private static LayeredConnectionSocketFactory obtainSSLSocketFactory(boolean safe) {
        if (safe) {
            return SSLConnectionSocketFactory.getSocketFactory();
        } else {
            try {
                if (unsafe_ssl_context == null) unsafe_ssl_context = SSLContexts.custom().loadTrustMaterial(null, TRUST_ALL).build();
                return new SSLConnectionSocketFactory(unsafe_ssl_context, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            } catch (Exception e) {
                Logger.e(TAG, "cannot instantiate the unsafe SSL socket factory", e);
                return SSLConnectionSocketFactory.getSocketFactory();
            }
        }
    }
    
    private static SSLContext unsafe_ssl_context = null;
    
    /** стратегия доверять всем без проверки сертификата */
    private static final TrustStrategy TRUST_ALL = new TrustStrategy() {
        @Override
        public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            return true;
        }
    };
}
