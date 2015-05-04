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

import java.lang.reflect.Field;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;

import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.HttpConstants;

import org.apache.http.HttpHost;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.BasicCookieStoreHC4;
import org.apache.http.impl.client.HttpClients;

import android.os.Build;

/**
 * Основной HTTP-клиент, используемый в проекте.<br>
 * Экземпляр хранит свои настройки HTTP-прокси сервера и объект хранилища Cookies,
 * см. методы {@link #getProxy()} и {@link #getCookieStore()}.
 * @author miku-nyan
 *
 */

/* Google пометила все классы и интерфейсы пакета org.apache.http как "deprecated" в API 22 (Android 5.1)
 * На самом деле используется актуальная версия apache-hc httpclient 4.3.5.1-android
 * Подробности: https://issues.apache.org/jira/browse/HTTPCLIENT-1632 */
@SuppressWarnings("deprecation")

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
        this.cookieStore = new BasicCookieStoreHC4();
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
    
    private static HttpClient build(boolean safe, HttpHost proxy, CookieStore cookieStore) {
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
        fixSupportedProtocols();
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
    
    private static AtomicBoolean fixedSupportedProtocols = new AtomicBoolean(false);
    
    private static void fixSupportedProtocols() {
        if (fixedSupportedProtocols.compareAndSet(false, true)) {
            try {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.FROYO) {
                    Logger.d(TAG, "fix 'supportedProtocol' value of SSL socket factory");
                    Class<?> classOpenSSLSocketImpl = Class.forName("org.apache.harmony.xnet.provider.jsse.OpenSSLSocketImpl");
                    Field field = classOpenSSLSocketImpl.getDeclaredField("supportedProtocols");
                    field.setAccessible(true);
                    field.set(null, new String[] { "SSLv3", "SSLv3", "TLSv1" });
                }
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
        }
    }
}
