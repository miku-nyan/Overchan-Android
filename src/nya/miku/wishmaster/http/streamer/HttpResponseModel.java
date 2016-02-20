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

package nya.miku.wishmaster.http.streamer;

import java.io.Closeable;
import java.io.InputStream;

import nya.miku.wishmaster.common.Logger;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.client.methods.HttpUriRequest;
import cz.msebera.android.httpclient.util.EntityUtils;

/**
 * Модель HTTP ответа, содержит поток полученных данных и другую информацию
 * @author miku-nyan
 *
 */

public class HttpResponseModel {
    private static final String TAG = "HttpResponseModel";
    
    /*package*/ HttpResponseModel() {}
    
    /** поток полученного контента из HTTP ответа */
    public InputStream stream = null;
    /** объём данных контента */
    public long contentLength = 0;
    
    /** полученный код состояния HTTP */
    public int statusCode;
    /** описание полученного состояния HTTP */
    public String statusReason;
    /** возвращает true, если контент не изменился с прошлого запроса (в этом случае загрузка не производится, поток будет равен null!) */
    public boolean notModified() {
        return statusCode == 304;
    }
    
    /** HTTP-заголовок location (в случае редиректа, куда переходить) */
    public String locationHeader;
    /** все HTTP-заголовки */
    public Header[] headers;
    
    /** оригинальный объект HTTP-запроса */
    HttpUriRequest request;
    /** оригинальный объект HTTP-ответа */
    HttpResponse response;
    
    /** освободить ресурсы. Необоходимо вызывать всегда после работы с HTTP, даже в случае ошибки. */
    public void release() {
        release(request, response);
    }
    
    /** статический метод для освобождения ресурсов произвольных объектов HTTP-запроса и HTTP-ответа */
    static void release(HttpUriRequest request, HttpResponse response) {
        try {
            if (request != null) request.abort();
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
        try {
            if (response != null) {
                HttpEntity entity = response.getEntity();
                EntityUtils.consume(entity);
            }
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
        try {
            if (response != null && response instanceof Closeable) ((Closeable) response).close();
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
    }
}