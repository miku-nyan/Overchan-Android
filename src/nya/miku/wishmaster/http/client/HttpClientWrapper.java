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

import java.io.Closeable;
import java.io.IOException;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

/**
 * Обёртка над интерфейсом {@link HttpClient}, поддерживаются реализации {@link Closeable}.
 * @author miku-nyan
 *
 */

/* Google пометила все классы и интерфейсы пакета org.apache.http как "deprecated" в API 22 (Android 5.1)
 * На самом деле используется актуальная версия apache-hc httpclient 4.3.5.1-android
 * Подробности: https://issues.apache.org/jira/browse/HTTPCLIENT-1632 */
@SuppressWarnings("deprecation")

public class HttpClientWrapper implements HttpClient, Closeable {
    
    private HttpClient client;
    
    protected HttpClientWrapper() {}
    
    protected void setClient(HttpClient client) {
        this.client = client;
    }

    @Override
    public HttpResponse execute(HttpUriRequest request) throws IOException, ClientProtocolException {
        return client.execute(request);
    }
    
    @Override
    public HttpResponse execute(HttpUriRequest request, HttpContext context) throws IOException, ClientProtocolException {
        return client.execute(request, context);
    }
    
    @Override
    public HttpResponse execute(HttpHost target, HttpRequest request) throws IOException, ClientProtocolException {
        return client.execute(target, request);
    }
    
    @Override
    public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> responseHandler) throws IOException, ClientProtocolException {
        return client.execute(request, responseHandler);
    }
    @Override
    public HttpResponse execute(HttpHost target, HttpRequest request, HttpContext context) throws IOException, ClientProtocolException {
        return client.execute(target, request, context);
    }
    
    @Override
    public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> responseHandler, HttpContext context)
            throws IOException, ClientProtocolException {
        return client.execute(request, responseHandler, context);
    }
    
    @Override
    public <T> T execute(HttpHost target, HttpRequest request, ResponseHandler<? extends T> responseHandler)
            throws IOException, ClientProtocolException {
        return client.execute(target, request, responseHandler);
    }
    
    @Override
    public <T> T execute(HttpHost target, HttpRequest request, ResponseHandler<? extends T> responseHandler, HttpContext context)
            throws IOException, ClientProtocolException {
        return client.execute(target, request, responseHandler, context);
    }
    
    @Override
    public ClientConnectionManager getConnectionManager() {
        return client.getConnectionManager();
    }
    
    @Override
    public HttpParams getParams() {
        return client.getParams();
    }

    @Override
    public void close() throws IOException {
        if (client instanceof Closeable) {
            ((Closeable) client).close();
        }
    }

}
