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

package nya.miku.wishmaster.chans.krautchan;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;

import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;

/**
 * parse boards list page: krautchan.net/nav
 * @author miku-nyan
 *
 */
public class KrautBoardsListReader implements Closeable {
    private static final List<String> SFW_BOARDS = Arrays.asList(new String[] {
            "a", "c", "co", "e", "f", "fe", "fit", "jp", "k", "l", "li", "m", "n", "ng",
            "p", "ph", "prog", "sp", "t", "trv", "tv", "v", "w", "wk", "wp", "z", "zp" 
    });
    private static final String[] ATTACHMENT_FORMATS =
            new String[] { "gif", "jpg", "jpeg", "png", "psd", "webm", "mp3", "ogg", "rar", "zip", "torrent", "swf" };
    
    private final Reader _in;
    private StringBuilder readBuffer = new StringBuilder();
    private String currentCategory;
    private List<SimpleBoardModel> boards;
    
    private static final int FILTER_CATEGORY = 0;
    private static final int FILTER_BOARD = 1;
    private static final char[][] FILTERS = {
        "<span class=\"nav_group_button\"".toCharArray(),
        "<li id=\"board_".toCharArray()
    };
    
    private static final char[] CLOSE = ">".toCharArray();
    private static final char[] LI_CLOSE = "</li>".toCharArray();
    private static final char[] SPAN_CLOSE = "</span>".toCharArray();
    private static final char[] H2_CLOSE = "</h2>".toCharArray();
    
    private static final Pattern BOARD_PATTERN = Pattern.compile("/(\\w+)/\\s+-\\s+(.*)", Pattern.DOTALL);
    
    public KrautBoardsListReader(Reader reader) {
        _in = reader;
    }
    
    public KrautBoardsListReader(InputStream in) {
        this(new BufferedReader(new InputStreamReader(in)));
    }
    
    public SimpleBoardModel[] readBoardsList() throws IOException {
        boards = new ArrayList<SimpleBoardModel>();
        
        int filtersCount = FILTERS.length;
        int[] pos = new int[filtersCount];
        int[] len = new int[filtersCount];
        for (int i=0; i<filtersCount; ++i) len[i] = FILTERS[i].length;
        
        int curChar;
        while ((curChar = _in.read()) != -1) {
            for (int i=0; i<filtersCount; ++i) {
                if (curChar == FILTERS[i][pos[i]]) {
                    ++pos[i];
                    if (pos[i] == len[i]) {
                        handleFilter(i);
                        pos[i] = 0;
                    }
                } else {
                    if (pos[i] != 0) pos[i] = curChar == FILTERS[i][0] ? 1 : 0;
                }
            }
        }
        return boards.toArray(new SimpleBoardModel[boards.size()]);
    }
    
    private void handleFilter(int filter) throws IOException {
        switch (filter) {
            case FILTER_CATEGORY:
                skipUntilSequence(SPAN_CLOSE);
                String cat = readUntilSequence(H2_CLOSE);
                if (!cat.contains("<span")) currentCategory = StringEscapeUtils.unescapeHtml4(cat);
                break;
            case FILTER_BOARD:
                skipUntilSequence(CLOSE);
                String board = readUntilSequence(LI_CLOSE).replaceAll("<[^>]*>", "").trim();
                Matcher boardMatcher = BOARD_PATTERN.matcher(board);
                if (boardMatcher.matches()) {
                    SimpleBoardModel model = new SimpleBoardModel();
                    model.chan = KrautModule.CHAN_NAME;
                    model.boardName = boardMatcher.group(1);
                    model.boardDescription = boardMatcher.group(2);
                    model.boardCategory = currentCategory;
                    model.nsfw = SFW_BOARDS.indexOf(model.boardName) == -1;
                    boards.add(model);
                }
        }
    }

    private void skipUntilSequence(char[] sequence) throws IOException {
        int len = sequence.length;
        if (len == 0) return;
        int pos = 0;
        int curChar;
        while ((curChar = _in.read()) != -1) {
            if (curChar == sequence[pos]) {
                ++pos;
                if (pos == len) break;
            } else {
                if (pos != 0) pos = curChar == sequence[0] ? 1 : 0;
            }
        }
    }
    
    private String readUntilSequence(char[] sequence) throws IOException {
        int len = sequence.length;
        if (len == 0) return "";
        readBuffer.setLength(0);
        int pos = 0;
        int curChar;
        while ((curChar = _in.read()) != -1) {
            readBuffer.append((char) curChar);
            if (curChar == sequence[pos]) {
                ++pos;
                if (pos == len) break;
            } else {
                if (pos != 0) pos = curChar == sequence[0] ? 1 : 0;
            }
        }
        int buflen = readBuffer.length();
        if (buflen >= len) {
            readBuffer.setLength(buflen - len);
            return readBuffer.toString();
        } else {
            return "";
        }
    }
    
    @Override
    public void close() throws IOException {
        _in.close();
    }
    
    public static BoardModel getDefaultBoardModel(String boardName, String description, String category) {
        BoardModel bm = new BoardModel();
        bm.chan = KrautModule.CHAN_NAME;
        bm.boardName = boardName;
        bm.boardDescription = description;
        bm.boardCategory = category;
        bm.nsfw = SFW_BOARDS.indexOf(boardName) == -1;
        bm.uniqueAttachmentNames = true;
        bm.timeZoneId = "Europe/Berlin";
        bm.defaultUserName = "Bernd";
        bm.bumpLimit = 500;
        bm.readonlyBoard = false;
        bm.requiredFileForNewThread = true;
        bm.allowDeletePosts = true;
        bm.allowDeleteFiles = false;
        bm.allowNames = !boardName.equals("b") && !boardName.equals("int") && !boardName.equals("vip") && !boardName.equals("trv");
        bm.allowSubjects = true;
        bm.allowSage = true;
        bm.allowEmails = false;
        bm.allowCustomMark = false;
        bm.allowRandomHash = false;
        bm.allowIcons = false;
        bm.attachmentsMaxCount = 4;
        bm.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        bm.markType = BoardModel.MARK_BBCODE;
        bm.firstPage = 1;
        bm.lastPage = BoardModel.LAST_PAGE_UNDEFINED;
        bm.searchAllowed = false;
        bm.catalogAllowed = false;
        return bm;
    }
}
