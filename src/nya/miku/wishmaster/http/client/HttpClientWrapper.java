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

import java.io.Closeable;
import java.io.IOException;

import cz.msebera.android.httpclient.HttpHost;
import cz.msebera.android.httpclient.HttpRequest;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.client.ClientProtocolException;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.ResponseHandler;
import cz.msebera.android.httpclient.client.methods.HttpUriRequest;
import cz.msebera.android.httpclient.conn.ClientConnectionManager;
import cz.msebera.android.httpclient.params.HttpParams;
import cz.msebera.android.httpclient.protocol.HttpContext;

/**
 * Обёртка над интерфейсом {@link HttpClient}, поддерживаются реализации {@link Closeable}.
 * @author miku-nyan
 *
 */

@SuppressWarnings("deprecation")
public abstract class HttpClientWrapper implements HttpClient, Closeable {
    
    protected abstract HttpClient getClient();
    
    @Override
    public HttpResponse execute(HttpUriRequest request) throws IOException, ClientProtocolException {
        return getClient().execute(request);
    }
    
    @Override
    public HttpResponse execute(HttpUriRequest request, HttpContext context) throws IOException, ClientProtocolException {
        return getClient().execute(request, context);
    }
    
    @Override
    public HttpResponse execute(HttpHost target, HttpRequest request) throws IOException, ClientProtocolException {
        return getClient().execute(target, request);
    }
    
    @Override
    public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> responseHandler) throws IOException, ClientProtocolException {
        return getClient().execute(request, responseHandler);
    }
    
    @Override
    public HttpResponse execute(HttpHost target, HttpRequest request, HttpContext context) throws IOException, ClientProtocolException {
        return getClient().execute(target, request, context);
    }
    
    @Override
    public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> responseHandler, HttpContext context)
            throws IOException, ClientProtocolException {
        return getClient().execute(request, responseHandler, context);
    }
    
    @Override
    public <T> T execute(HttpHost target, HttpRequest request, ResponseHandler<? extends T> responseHandler)
            throws IOException, ClientProtocolException {
        return getClient().execute(target, request, responseHandler);
    }
    
    @Override
    public <T> T execute(HttpHost target, HttpRequest request, ResponseHandler<? extends T> responseHandler, HttpContext context)
            throws IOException, ClientProtocolException {
        return getClient().execute(target, request, responseHandler, context);
    }
    
    @Override
    public ClientConnectionManager getConnectionManager() {
        return getClient().getConnectionManager();
    }
    
    @Override
    public HttpParams getParams() {
        return getClient().getParams();
    }
    
    @Override
    public void close() throws IOException {
        HttpClient client = getClient();
        if (client instanceof Closeable) {
            ((Closeable) client).close();
        }
    }
}
