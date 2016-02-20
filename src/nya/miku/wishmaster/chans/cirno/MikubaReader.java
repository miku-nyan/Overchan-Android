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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.commons.lang3.StringEscapeUtils;

import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.util.CryptoUtils;
import nya.miku.wishmaster.common.Logger;

/**
 * Парсер страниц борды hatsune.ru
 * @author miku-nyan
 *
 */
public class MikubaReader implements Closeable {
    private static final String TAG = "MikubaReader";
    
    private static final DateFormat DATEFORMAT;
    static {
        DATEFORMAT = new SimpleDateFormat("EEE dd MMM yyyy hh:mm:ss", Locale.US);
        DATEFORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
    }
    
    private static final char[] DATA_START = "<div id=\"page\">".toCharArray();
    
    private static final int FILTER_PAGE_END = 0;
    private static final int FILTER_THREAD_END = 1;
    private static final int FILTER_ATTACHMENT = 2;
    private static final int FILTER_POSTNUMBER_OP = 3;
    private static final int FILTER_POSTNUMBER = 4;
    private static final int FILTER_SUBJECT = 5;
    private static final int FILTER_ENDDATE = 6;
    private static final int FILTER_START_COMMENT = 7;
    
    private static final char[][] FILTERS_OPEN = {
        "<center>".toCharArray(),
        "<hr".toCharArray(),
        "<td class=\"image\"".toCharArray(),
        "<td class=\"post\" id=\"".toCharArray(),
        "<td class=\"reply\" id=\"".toCharArray(),
        "<span class=\"replytitle\">".toCharArray(),
        "</label>".toCharArray(),
        "<blockquote".toCharArray()
    };
    
    private static final char[][] FILTERS_CLOSE = {
        null,
        null,
        "</td>".toCharArray(),
        "\"".toCharArray(),
        "\"".toCharArray(),
        "</span>".toCharArray(),
        null,
        ">".toCharArray()
    };
    
    //in comment
    private static final char[] BLOCKQUOTE_OPEN = "<blockquote".toCharArray();
    private static final char[] BLOCKQUOTE_CLOSE = "</blockquote>".toCharArray();
    private static final char[] OMITTED_OPEN = "<span class=\"omitted\">".toCharArray();
    
    private static final char[] OMITTED_CLOSE = "</span>".toCharArray();
    
    private final Reader _in;
    
    private StringBuilder readBuffer = new StringBuilder();
    private List<ThreadModel> threads;
    private ThreadModel currentThread;
    private List<PostModel> postsBuf;
    private PostModel currentPost;
    private boolean inDate;
    private StringBuilder dateBuffer = new StringBuilder();
    private StringBuilder commentBuffer = new StringBuilder();
    private StringBuilder omittedDigitsBuffer = new StringBuilder();
    private List<AttachmentModel> currentAttachments;
    
    public MikubaReader(InputStream in) {
        _in = new BufferedReader(new InputStreamReader(in));
    }
    
    private void initThreadModel() {
        currentThread = new ThreadModel();
        currentThread.postsCount = 0;
        currentThread.attachmentsCount = -1;
        postsBuf = new ArrayList<PostModel>();
    }
    
    private void initPostModel() {
        currentPost = new PostModel();
        currentPost.name = "";
        currentPost.email = "";
        currentPost.trip = "";
        currentAttachments = new ArrayList<AttachmentModel>();
        inDate = false;
        dateBuffer.setLength(0);
    }
    
    private void finalizeThread() {
        if (postsBuf.size() > 0) {
            currentThread.posts = postsBuf.toArray(new PostModel[postsBuf.size()]);
            currentThread.threadNumber = currentThread.posts[0].number;
            for (PostModel post : currentThread.posts) post.parentThread = currentThread.threadNumber;
            threads.add(currentThread);
            initThreadModel();
        }
    }
    
    private void finalizePost() {
        if (currentPost.number != null && currentPost.number.length() > 0) {
            ++currentThread.postsCount;
            currentPost.attachments = currentAttachments.toArray(new AttachmentModel[currentAttachments.size()]);
            if (currentPost.subject == null) currentPost.subject = "";
            if (currentPost.comment == null) currentPost.comment = "";
            postsBuf.add(currentPost);
        }
        initPostModel();
    }
    
    public ThreadModel[] readPage() throws IOException {
        threads = new ArrayList<ThreadModel>();
        initThreadModel();
        initPostModel();
        
        skipUntilSequence(DATA_START);
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
            if (inDate) dateBuffer.append((char) curChar);
            for (int i=0; i<filtersCount; ++i) {
                if (curChar == FILTERS_OPEN[i][pos[i]]) {
                    ++pos[i];
                    if (pos[i] == len[i]) {
                        if (i == FILTER_PAGE_END) {
                            finalizeThread();
                            return;
                        }
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
    
    private void handleFilter(int filterIndex) throws IOException {
        if (inDate && filterIndex != FILTER_ENDDATE) dateBuffer.setLength(0);
        switch (filterIndex) {
            case FILTER_THREAD_END:
                finalizeThread();
                break;
            case FILTER_ATTACHMENT:
                parseAttachment(readUntilSequence(FILTERS_CLOSE[filterIndex]));
                break;
            case FILTER_POSTNUMBER:
            case FILTER_POSTNUMBER_OP:
                currentPost.number = readUntilSequence(FILTERS_CLOSE[filterIndex]).trim().substring(1);
                break;
            case FILTER_SUBJECT:
                currentPost.subject = StringEscapeUtils.unescapeHtml4(readUntilSequence(FILTERS_CLOSE[filterIndex])).trim();
                inDate = true;
                break;
            case FILTER_ENDDATE:
                if (dateBuffer.length() > FILTERS_OPEN[FILTER_ENDDATE].length) {
                    String date = dateBuffer.substring(0, dateBuffer.length() - FILTERS_OPEN[FILTER_ENDDATE].length).trim();
                    if (date.length() > 0) {
                        try {
                            currentPost.timestamp = DATEFORMAT.parse(date).getTime();
                        } catch (Exception e) {
                            Logger.e(TAG, "cannot parse date; make sure you choose the right DateFormat for this chan", e);
                        }
                    }
                }
                inDate = false;
                dateBuffer.setLength(0);
                break;
            case FILTER_START_COMMENT:
                skipUntilSequence(FILTERS_CLOSE[filterIndex]);
                currentPost.comment = readPostComment();
                finalizePost();
                break;
        }
    }
    
    private String readPostComment() throws IOException {
        commentBuffer.setLength(0);
        int len1 = BLOCKQUOTE_OPEN.length;
        int len2 = BLOCKQUOTE_CLOSE.length;
        int len3 = OMITTED_OPEN.length;
        int pos1 = 0;
        int pos2 = 0;
        int pos3 = 0;
        int tagCounter = 1;
        int curChar;
        while ((curChar = _in.read()) != -1) {
            commentBuffer.append((char) curChar);
            
            if (curChar == BLOCKQUOTE_OPEN[pos1]) {
                ++pos1;
                if (pos1 == len1) {
                    ++tagCounter;
                    pos1 = 0;
                }
            } else {
                if (pos1 != 0) pos1 = curChar == BLOCKQUOTE_OPEN[0] ? 1 : 0;
            }
            
            if (curChar == BLOCKQUOTE_CLOSE[pos2]) {
                ++pos2;
                if (pos2 == len2) {
                    --tagCounter;
                    if (tagCounter == 0) break;
                    pos2 = 0;
                }
            } else {
                if (pos2 != 0) pos2 = curChar == BLOCKQUOTE_CLOSE[0] ? 1 : 0;
            }
            
            if (curChar == OMITTED_OPEN[pos3]) {
                ++pos3;
                if (pos3 == len3) {
                    parseOmittedString(readUntilSequence(OMITTED_CLOSE));
                    pos3 = 0;
                }
            } else {
                if (pos3 != 0) pos3 = curChar == BLOCKQUOTE_OPEN[0] ? 1 : 0;
            }
        }
        int buflen = commentBuffer.length();
        if (buflen > len2) {
            commentBuffer.setLength(buflen - len2);
            return CryptoUtils.fixCloudflareEmails(commentBuffer.toString());
        } else {
            return "";
        }
    }
    
    private void parseOmittedString(String omitted) {
        try {
            int len = omitted.length();
            for (int i=0; i<=len; ++i) {
                char ch = i == len ? ' ' : omitted.charAt(i);
                if (ch >= '0' && ch <= '9') {
                    omittedDigitsBuffer.append(ch);
                } else {
                    if (omittedDigitsBuffer.length() > 0) {
                        currentThread.postsCount += Integer.parseInt(omittedDigitsBuffer.toString());
                        omittedDigitsBuffer.setLength(0);
                        break;
                    }
                }
            }
        } catch (NumberFormatException e) {}
    }
    
    private void parseAttachment(String html) {
        int index = html.indexOf("<img");
        if (index != -1) {
            index = html.indexOf("src=\"", index + 4);
            if (index != -1) {
                int start = index + 5;
                int end = html.indexOf("\"", start);
                if (end != -1) {
                    AttachmentModel attachment = new AttachmentModel();
                    attachment.size = -1;
                    attachment.thumbnail = html.substring(start, end);
                    if (attachment.thumbnail.contains("/thu/")) {
                        attachment.path = attachment.thumbnail.replace("/thu/", "/src/");
                        attachment.type = attachment.path.toLowerCase(Locale.US).endsWith(".gif") ?
                                AttachmentModel.TYPE_IMAGE_GIF : AttachmentModel.TYPE_IMAGE_STATIC;
                    } else {
                        attachment.path = attachment.thumbnail;
                        attachment.type = AttachmentModel.TYPE_OTHER_FILE;
                        int startHref, endHref;
                        if ((startHref = html.indexOf("href=\"")) != -1 && (endHref = html.indexOf('\"', startHref + 6)) != -1) {
                            attachment.path = html.substring(startHref + 6, endHref);
                            String pathLower = attachment.path.toLowerCase(Locale.US);
                            if (pathLower.endsWith(".mp3") || pathLower.endsWith(".ogg"))
                                attachment.type = AttachmentModel.TYPE_AUDIO;
                        }
                    }
                    currentAttachments.add(attachment);
                    return;
                }
            }
        }
        
        index = html.indexOf("<embed");
        if (index != -1) {
            index = html.indexOf("src=\"", index + 6);
            if (index != -1) {
                int start = index + 5;
                int end = html.indexOf("\"", start);
                if (end != -1) {
                    AttachmentModel attachment = new AttachmentModel();
                    attachment.size = -1;
                    attachment.path = html.substring(start, end);
                    if (attachment.path.contains("youtube")) {
                        int youtubeIdIndex = attachment.path.indexOf("/v/");
                        if (youtubeIdIndex != -1) {
                            String youtubeId = attachment.path.substring(youtubeIdIndex + 3);
                            attachment.path = "http://youtube.com/watch?v=" + youtubeId;
                            attachment.thumbnail = "http://img.youtube.com/vi/" + youtubeId + "/default.jpg";
                        }
                    }
                    attachment.type = AttachmentModel.TYPE_OTHER_NOTFILE;
                    currentAttachments.add(attachment);
                }
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
    
}
