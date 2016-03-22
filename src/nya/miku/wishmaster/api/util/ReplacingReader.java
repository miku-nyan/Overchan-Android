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

package nya.miku.wishmaster.api.util;

import java.io.IOException;
import java.io.Reader;

/**
 * Обёртка Reader, заменяющая при чтении одну последовательность символов на другую.
 * Рекомендуется использовать с буферизованным ридером (этот класс не содержит в себе буфер и читает по одному символу). 
 * @author miku-nyan
 *
 */
public class ReplacingReader extends Reader {
    
    private final Reader in;
    private final CharSequence from, to;
    
    private char[] buf;
    private int bufCurrentLen = 0;
    private boolean bufReading = false;
    private int bufReadingPos = 0;
    
    private boolean replacementReading = false;
    private int replacementReadingPos = 0;
    
    /**
     * Конструктор
     * @param in исходный Reader
     * @param from последовательность символов, которую требуется заменять
     * @param to последовательность, на которую требуется заменять from
     */
    public ReplacingReader(Reader in, CharSequence from, CharSequence to) {
        this.in = in;
        this.from = from;
        this.to = to;
        buf = new char[from.length()];
    }
    
    @Override
    public void close() throws IOException {
        in.close();
    }

    @Override
    public int read() throws IOException {
        if (replacementReading) {
            char fromReplacement = to.charAt(replacementReadingPos++);
            if (replacementReadingPos >= to.length()) {
                replacementReading = false;
                replacementReadingPos = 0;
            }
            return fromReplacement;
        }
        
        if (bufReading) {
            char fromBuf = buf[bufReadingPos++];
            if (bufReadingPos >= bufCurrentLen) {
                bufReading = false;
                bufCurrentLen = 0;
            }
            return fromBuf;
        }
        
        bufCurrentLen = 0;
        int curFromPos = 0;
        int ch;
        while ((ch = in.read()) != -1) {
            buf[bufCurrentLen++] = (char) ch;
            if (ch != from.charAt(curFromPos++)) break;
            if (curFromPos == from.length()) {
                replacementReading = true;
                break;
            }
        }
        
        if (replacementReading) {
            if (to.length() > 1) {
                replacementReadingPos = 1;
            } else {
                replacementReading = false;
            }
            return to.charAt(0);
        }
        
        if (bufCurrentLen > 1) {
            bufReading = true;
            bufReadingPos = 1;
        } else {
            bufReading = false;
        }
        if (bufCurrentLen == 0) return -1;
        return buf[0];
    }

    @Override
    public int read(char[] buffer, int offset, int count) throws IOException {
        int charsRead = 0;
        for (int i = 0; i < count; i++) {
            charsRead = i;
            int nextChar = read();
            if (nextChar == -1) {
                break;
            }
            buffer[offset + i] = (char) nextChar;
        }
        return charsRead;
    }

}
