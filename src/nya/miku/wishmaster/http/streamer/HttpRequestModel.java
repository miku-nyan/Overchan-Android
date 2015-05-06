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

package nya.miku.wishmaster.http.streamer;

import nya.miku.wishmaster.http.HttpConstants;

import org.apache.http.Header;
import org.apache.http.HttpEntity;

/**
 * Модель HTTP запроса для работы с синглтоном {@link HttpStreamer}.
 * Для создания используйте метод {@link #builder()}
 * @author miku-nyan
 *
 */

/* Google пометила все классы и интерфейсы пакета org.apache.http как "deprecated" в API 22 (Android 5.1)
 * На самом деле используется актуальная версия apache-hc httpclient 4.3.5.1-android
 * Подробности: https://issues.apache.org/jira/browse/HTTPCLIENT-1632 */
@SuppressWarnings("deprecation")

public class HttpRequestModel {
    //константы
    private static final int METHOD_UNDEFINED = -1;
    static final int METHOD_GET = 0;
    static final int METHOD_POST = 1;
    
    //package
    /** метод запроса (GET или POST) */
    final int method;
    /** не загружать контент, если данные не изменились с момента прошлого запроса */
    final boolean checkIfModified;
    /** отключить редирект */
    final boolean noRedirect;
    /** дополнительные HTTP-заголовки */
    final Header[] customHeaders;
    /** передаваемая сущность (контент) при POST запросе */
    final HttpEntity postEntity;
    /** значение таймаута (0 соответствует бесконечности). */
    final int timeoutValue;
    
    private HttpRequestModel(
            int method,
            boolean checkIfModified,
            boolean noRedirect,
            Header[] customHeaders,
            HttpEntity postEntity,
            int timeoutValue) {
        this.method = method;
        this.checkIfModified = checkIfModified;
        this.noRedirect = noRedirect;
        this.customHeaders = customHeaders;
        this.postEntity = postEntity;
        this.timeoutValue = timeoutValue;
    }
    
    /**
     * Получить Builder для создания экземпляра класса
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private int method = METHOD_UNDEFINED;
        private boolean checkIfModified = false;
        private boolean noRedirect = false;
        private Header[] customHeaders = null;
        private HttpEntity postEntity = null;
        private int timeoutValue = HttpConstants.DEFAULT_HTTP_TIMEOUT;
        
        private Builder() {}
        
        /**
         * Установить метод GET
         */
        public Builder setGET() {
            this.method = METHOD_GET;
            this.postEntity = null;
            return this;
        }
        
        /**
         * Установить метод POST
         * @param postEntity передаваемая сущность (контент)
         */
        public Builder setPOST(HttpEntity postEntity) {
            this.method = METHOD_POST;
            this.postEntity = postEntity;
            return this;
        }
        
        /**
         * Отключить любой редирект: страница по редиректу не будет загружаться автоматически.
         * (адрес редиректа можно будет посмотреть в locationHeader)<br>
         * По умолчанию false
         */
        public Builder setNoRedirect(boolean noRedirect) {
            this.noRedirect = noRedirect;
            return this;
        }
        
        /** 
         * checkIfModified - не загружать контент, если данные не изменились с момента прошлого запроса.
         * Только для метода GET.<br>
         * По умолчанию false
         */
        public Builder setCheckIfModified(boolean checkIfModified) {
            this.checkIfModified = checkIfModified;
            return this;
        }
        
        /**
         * Установить дополнительные HTTP-заголовки
         */
        public Builder setCustomHeaders(Header[] customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }
        
        /**
         * Установить значение таймаута (0 соответствует бесконечности)
         */
        public Builder setTimeout(int timeoutValue) {
            this.timeoutValue = timeoutValue;
            return this;
        }
        
        /**
         * Построить объект
         */
        public HttpRequestModel build() {
            if (method == METHOD_UNDEFINED) throw new IllegalStateException("method not set");
            if (method == METHOD_POST && checkIfModified) throw new IllegalStateException("check if-modified is available only for GET method");
            return new HttpRequestModel(method, checkIfModified, noRedirect, customHeaders, postEntity, timeoutValue);
        }
        
    }
    
}
