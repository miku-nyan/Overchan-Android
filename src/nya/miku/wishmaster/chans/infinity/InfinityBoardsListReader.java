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

package nya.miku.wishmaster.chans.infinity;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import nya.miku.wishmaster.api.models.SimpleBoardModel;

public class InfinityBoardsListReader implements Closeable {
    private final Reader _in;
    private final StringBuilder buf = new StringBuilder();
    private final static int MAX_BOARDS_COUNT = 150;
    
    private static final char[][] FILTERS = {
        "\"uri\":\"".toCharArray(),
        "\"title\":\"".toCharArray(),
        "\"sfw\":\"0\"".toCharArray(),
    };
    
    public InfinityBoardsListReader(Reader reader) {
        _in = reader;
    }
    
    public InfinityBoardsListReader(InputStream in) {
        this(new BufferedReader(new InputStreamReader(in)));
    }
    
    public SimpleBoardModel[] readBoardsList() throws IOException {
        ArrayList<SimpleBoardModel> list = new ArrayList<>();
        SimpleBoardModel current = new SimpleBoardModel();
        
        int filtersCount = FILTERS.length;
        int[] pos = new int[filtersCount];
        int[] len = new int[filtersCount];
        for (int i=0; i<filtersCount; ++i) len[i] = FILTERS[i].length;
        int curChar;
        while ((curChar = _in.read()) != -1) {
            if (curChar == '}') {
                list.add(current);
                if (list.size() == MAX_BOARDS_COUNT) break;
                current = new SimpleBoardModel();
            }
            
            for (int i=0; i<filtersCount; ++i) {
                if (curChar == FILTERS[i][pos[i]]) {
                    ++pos[i];
                    if (pos[i] == len[i]) {
                        switch (i) {
                            case 0: current.boardName = readJsonString(); break;
                            case 1: current.boardDescription = readJsonString(); break;
                            case 2: current.nsfw = true;
                        }
                        pos[i] = 0;
                    }
                } else {
                    if (pos[i] != 0) pos[i] = curChar == FILTERS[i][0] ? 1 : 0;
                }
            }
        }
        
        return list.toArray(new SimpleBoardModel[list.size()]);
    }

    private String readJsonString() throws IOException {
        int curChar;
        while ((curChar = _in.read()) != -1) {
            if (curChar == '"') {
                break;
            } else if (curChar == '\\') {
                curChar = _in.read();
                switch (curChar) {
                    case 'b':
                        buf.append('\b'); break;
                    case 't':
                        buf.append('\t'); break;
                    case 'n':
                        buf.append('\n'); break;
                    case 'f':
                        buf.append('\f'); break;
                    case 'r':
                        buf.append('\r'); break;
                    case '"':
                    case '\'':
                    case '\\':
                    case '/':
                        buf.append((char) curChar); break;
                    case 'u':
                        char[] unicode = new char[4];
                        _in.read(unicode);
                        buf.append((char)Integer.parseInt(String.valueOf(unicode), 16));
                        break;
                }
            } else {
                buf.append((char) curChar);
            }
        }
        String result = buf.toString();
        buf.setLength(0);
        return result;
    }

    @Override
    public void close() throws IOException {
        _in.close();
    }
}
