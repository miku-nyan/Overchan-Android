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

package nya.miku.wishmaster.http.client;

import nya.miku.wishmaster.http.HttpConstants;
import nya.miku.wishmaster.http.SSLCompatibility;
import cz.msebera.android.httpclient.HttpHost;
import cz.msebera.android.httpclient.client.CookieStore;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.config.CookieSpecs;
import cz.msebera.android.httpclient.client.config.RequestConfig;
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
    private final CookieStore cookieStore;
    private final HttpHost proxy;
    private volatile HttpClient httpClient;
    
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
    
    @Override
    protected HttpClient getClient() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = build(proxy, getCookieStore());
                }
            }
        }
        return httpClient;
    }
    
    /**
     * Конструктор
     * @param proxy адрес HTTP прокси (возможно null)
     */
    public ExtendedHttpClient(HttpHost proxy) {
        super();
        this.cookieStore = new BasicCookieStore();
        this.proxy = proxy;
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
                setCookieSpec(CookieSpecs.STANDARD).
                setStaleConnectionCheckEnabled(false);
    }
    
    private static HttpClient build(final HttpHost proxy, CookieStore cookieStore) {
        SSLCompatibility.waitIfInstallingAsync();
        return HttpClients.custom().
                setDefaultRequestConfig(getDefaultRequestConfigBuilder(HttpConstants.DEFAULT_HTTP_TIMEOUT).build()).
                setUserAgent(HttpConstants.USER_AGENT_STRING).
                setProxy(proxy).
                setDefaultCookieStore(cookieStore).
                setSSLSocketFactory(ExtendedSSLSocketFactory.getSocketFactory()).
                build();
    }
}
