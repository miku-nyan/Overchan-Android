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

/**
 * Исключение возбуждается, если код статуса HTTP в ответе не позволяет продолжить дальнейшие действия.
 * @author miku-nyan
 *
 */
public class HttpWrongStatusCodeException extends RuntimeException {
    private static final long serialVersionUID = -2780926174833428525L;

    private final int statusCode;
    private final byte[] html;
    
    /**
     * Конструктор исключения
     * @param statusCode код статуса HTTP
     * @param msg сообщение
     * @param html HTML содержимое
     */
    public HttpWrongStatusCodeException(int statusCode, String msg, byte[] html) {
        super(msg);
        this.statusCode = statusCode;
        this.html = html;
    }
    
    /**
     * конструктор исключения
     * @param statusCode код статуса HTTP
     * @param msg сообщение
     */
    public HttpWrongStatusCodeException(int statusCode, String msg) {
        super(msg);
        this.statusCode = statusCode;
        this.html = null;
    }

    /**
     * получить код статуса HTTP
     * @return
     */
    public int getStatusCode() {
        return statusCode;
    }
    
    /**
     * Получить содержимое (если оно было загружено) 
     * @return содержимое в виде массива байтов
     */
    public byte[] getHtmlBytes() {
        return html;
    }
    
    /**
     * Получить содержимое (если оно было загружено) 
     * @return содержимое в виде строки
     */
    public String getHtmlString() {
        if (html != null) return new String(html);
        else return null;
    }
    
}
