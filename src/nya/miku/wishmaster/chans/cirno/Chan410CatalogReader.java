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

package nya.miku.wishmaster.chans.cirno;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;

import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.ThreadModel;

public class Chan410CatalogReader implements Closeable {
    private static final char[] CATALOG_START = "<div class=\"cataloglist\">".toCharArray();
    
    private static final Pattern PATTERN_THREADNUMBER = Pattern.compile(".*/(\\d+)\\..*");
    
    private static final int FILTER_START = 0;
    private static final int FILTER_THUMBNAIL = 1;
    private static final int FILTER_SUBJECT = 2;
    private static final int FILTER_COMMENT = 3;
    private static final int FILTER_OMITTED = 4;
    private static final int FILTER_STATUS = 5;
    private static final int FILTER_END = 6;
    
    public static final char[][] FILTERS_OPEN = {
        "<a href=\"".toCharArray(),
        "<img class=\"catalogpic\" src=\"".toCharArray(),
        "<span class=\"catalogsubject\">".toCharArray(),
        "<span class=\"catalogmsg\">".toCharArray(),
        "<span class=\"catalogposts\">".toCharArray(),
        "<div class=\"catalogthread".toCharArray(),
        "</a>".toCharArray()
    };
    
    private static final char[][] FILTERS_CLOSE = {
        "\"".toCharArray(),
        "\"".toCharArray(),
        "</span>".toCharArray(),
        "</span>".toCharArray(),
        "</span>".toCharArray(),
        "\"".toCharArray(),
        null
    };
    
    private final Reader _in;
    
    private StringBuilder readBuffer = new StringBuilder();
    private List<ThreadModel> threads;
    private ThreadModel currentThread;
    
    public Chan410CatalogReader(Reader reader) {
        _in = reader;
    }
    
    public Chan410CatalogReader(InputStream in) {
        this(new BufferedReader(new InputStreamReader(in)));
    }
    
    public ThreadModel[] readPage() throws IOException {
        threads = new ArrayList<ThreadModel>();
        initThreadModel();
        skipUntilSequence(CATALOG_START);
        readData();
        return threads.toArray(new ThreadModel[threads.size()]);
    }
    
    private void readData() throws IOException {
        int filtersCount = FILTERS_OPEN.length;
        int[] pos = new int[filtersCount];
        int[] len = new int[filtersCount];
        for (int i=0; i<filtersCount; ++i) len[i] = FILTERS_OPEN[i].length;
        
        int curChar;
        while ((curChar = _in.read()) != -1) {
            for (int i=0; i<filtersCount; ++i) {
                if (curChar == FILTERS_OPEN[i][pos[i]]) {
                    ++pos[i];
                    if (pos[i] == len[i]) {
                        handleFilter(i);
                        pos[i] = 0;
                    }
                } else {
                    if (pos[i] != 0) pos[i] = curChar == FILTERS_OPEN[i][0] ? 1 : 0;
                }
            }
        }
        finalizeThread();
    }
    
    private void initThreadModel() {
        currentThread = new ThreadModel();
        currentThread.postsCount = 0;
        currentThread.attachmentsCount = 0;
        currentThread.posts = new PostModel[1];
        currentThread.posts[0] = new PostModel();
        currentThread.posts[0].email = "";
        currentThread.posts[0].trip = "";
        currentThread.posts[0].name = "";
    }
    
    private void finalizeThread() {
        if (currentThread.posts[0].number != null && currentThread.posts[0].number.length() > 0) {
            currentThread.threadNumber = currentThread.posts[0].number;
            currentThread.posts[0].parentThread = currentThread.posts[0].number;
            if (currentThread.posts[0].subject == null) currentThread.posts[0].subject = "";
            if (currentThread.posts[0].comment == null) currentThread.posts[0].comment = "";
            if (currentThread.posts[0].attachments == null) currentThread.posts[0].attachments = new AttachmentModel[0];
            threads.add(currentThread);
        }
        initThreadModel();
    }
    
    private void handleFilter(int filterIndex) throws IOException {
        switch (filterIndex) {
            case FILTER_START:
                String threadUrl = readUntilSequence(FILTERS_CLOSE[filterIndex]);
                Matcher matcher = PATTERN_THREADNUMBER.matcher(threadUrl);
                if (matcher.matches()) {
                    currentThread.posts[0].number = matcher.group(1);
                }
                break;
            case FILTER_THUMBNAIL:
                AttachmentModel attachment = new AttachmentModel();
                attachment.type = AttachmentModel.TYPE_IMAGE_STATIC;
                attachment.size = -1;
                attachment.width = -1;
                attachment.height = -1;
                attachment.thumbnail = readUntilSequence(FILTERS_CLOSE[filterIndex]);
                attachment.path = attachment.thumbnail.replace("/thumb/", "/src/").replaceAll("(\\d+)c\\.", "$1.");
                currentThread.posts[0].attachments = new AttachmentModel[] { attachment };
                break;
            case FILTER_SUBJECT:
                currentThread.posts[0].subject = StringEscapeUtils.unescapeHtml4(readUntilSequence(FILTERS_CLOSE[filterIndex])).trim();
                break;
            case FILTER_COMMENT:
                currentThread.posts[0].comment = readUntilSequence(FILTERS_CLOSE[filterIndex]);
                break;
            case FILTER_OMITTED:
                int postsOmitted = -1;
                try {
                    postsOmitted = Integer.parseInt(readUntilSequence(FILTERS_CLOSE[filterIndex]));
                } catch(NumberFormatException e) {}
                if (postsOmitted > 0) currentThread.postsCount = 1 + postsOmitted;
                break;
            case FILTER_STATUS:
                String status = readUntilSequence(FILTERS_CLOSE[filterIndex]);
                if (status.contains("closed")) currentThread.isClosed = true;
                if (status.contains("sticked")) currentThread.isSticky = true;
                break;
            case FILTER_END:
                finalizeThread();
                break;
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
}
