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

import java.net.SocketTimeoutException;

import javax.net.ssl.SSLException;

import nya.miku.wishmaster.R;

/**
 * Исключение возбуждается в случае ошибки при HTTP запросе.
 * @author miku-nyan
 *
 */
public class HttpRequestException extends Exception {
    private static final long serialVersionUID = 1L;
    private final boolean sslException;
    
    public HttpRequestException(Exception e) {
        super(getMessage(e), e);
        sslException = e instanceof SSLException;
    }
    
    public static String getMessage(Exception e) {
        if (e instanceof SSLException) return getString(R.string.error_ssl, "SSL/HTTPS Error");
        if (e instanceof SocketTimeoutException) return getString(R.string.error_connection_timeout, "Connection timed out");
        if (e != null && e.getMessage() != null) return e.getMessage();
        return getString(R.string.error_connection, "Unable to connect to server");
    }
    
    public boolean isSslException() {
        return sslException;
    }
    
    private static String getString(int resId, String defaultValue) {
        try {
            return nya.miku.wishmaster.common.MainApplication.getInstance().getString(resId);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}