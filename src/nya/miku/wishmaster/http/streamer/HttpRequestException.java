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

import javax.net.ssl.SSLException;

import nya.miku.wishmaster.R;

/**
 * Исключение возбуждается в случае ошибки в HTTP запросе.
 * @author miku-nyan
 *
 */
public class HttpRequestException extends Exception {
    private static final long serialVersionUID = 1L;
    private final boolean sslException;
    
    public HttpRequestException(Exception e) {
        super((e != null ? (e.getMessage() != null ? e.getMessage() : e.toString()) : null), e);
        sslException = e instanceof SSLException;
    }
    
    public boolean isSslException() {
        return sslException;
    }
    
    @Override
    public String getMessage() {
        if (sslException) return getString(R.string.error_ssl, "SSL/HTTPS Error (Connection is Untrusted)");
        String message = super.getMessage();
        if (message.equals("java.net.SocketTimeoutException")) return getString(R.string.error_connection_timeout, "Connection timed out");
        return message;
    }
    
    private String getString(int resId, String defaultValue) {
        try {
            return nya.miku.wishmaster.common.MainApplication.getInstance().getString(resId);
        } catch (Exception e) {
            return defaultValue;
        }
    }

}