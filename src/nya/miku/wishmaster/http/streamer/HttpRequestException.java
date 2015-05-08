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

import nya.miku.wishmaster.R;

/**
 * Исключение возбуждается в случае ошибки в HTTP запросе.
 * @author miku-nyan
 *
 */
public class HttpRequestException extends Exception {
    private static final long serialVersionUID = 1L;
    private boolean sslException = false;
    
    public HttpRequestException(Exception e) {
        super(e);
    }
    public HttpRequestException(Exception e, boolean sslException) {
        this(e);
        this.sslException = sslException;
    }
    public boolean isSslException() {
        return sslException;
    }
    @Override
    public String getMessage() {
        String message = super.getMessage();
        if (message.startsWith("org.apache.http.conn.HttpHostConnectException: ")) {
            return message.substring(47);
        } else if (message.equals("java.net.SocketTimeoutException")) {
            try {
                return nya.miku.wishmaster.common.MainApplication.getInstance().getString(R.string.error_connection_timeout);
            } catch (Exception e) {
                return "Connection timed out";
            }
        }
        return message;
    }

}