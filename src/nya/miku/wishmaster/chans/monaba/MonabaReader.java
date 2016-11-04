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

package nya.miku.wishmaster.chans.monaba;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringEscapeUtils;
import android.annotation.SuppressLint;

import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.util.CryptoUtils;
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.common.Logger;

/**
 * Парсер страниц борд на движке Monaba
 *
 */
@SuppressLint("SimpleDateFormat")
public class MonabaReader implements Closeable {
    private static final String TAG = "MonabaReader";
    
    private static final DateFormat DEFAULT_MONABA_DATEFORMAT;
    static {
        DEFAULT_MONABA_DATEFORMAT = new SimpleDateFormat("dd MMM yyyy (EEE) hh:mm:ss", Locale.US);
        DEFAULT_MONABA_DATEFORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
    }
    
    private static final Pattern REPLY_URL_PATTERN =
            Pattern.compile("<a.*?data-post-local-id=(\\d+)[^>]*href=['\"](.*?)#[^>]*>", Pattern.DOTALL);
    private static final Pattern ATTACHMENT_PATTERN = Pattern.compile("<a[^>]*title=\"(.*?)\".*href=\"(.*?)\"");
    private static final Pattern ATTACHMENT_SIZE_PATTERN =
            Pattern.compile("([\\.\\d]+)\\s*([km])?b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTACHMENT_PX_SIZE_PATTERN = Pattern.compile("(\\d+)x(\\d+)");
    
    private static final char[] DATA_START = "<form class=\"delete-form\"".toCharArray();
    
    private static final int FILTER_PAGE_END = 0;
    private static final int FILTER_THREAD_END = 1;
    private static final int FILTER_ATTACHMENT = 2;
    private static final int FILTER_ATTACHMENT_INFO = 3;
    private static final int FILTER_ATTACHMENT_THUMBNAIL = 4;
    private static final int FILTER_POSTNUMBER = 5;
    private static final int FILTER_SUBJECT = 6;
    private static final int FILTER_NAME = 7;
    private static final int FILTER_DATE = 8;
    private static final int FILTER_OMITTEDPOSTS = 9;
    private static final int FILTER_START_COMMENT = 10;
    
    private static final char[][] FILTERS_OPEN = {
        "</form>".toCharArray(),
        "<div class=\"thread\"".toCharArray(),
        "<div class=\"file-name\">".toCharArray(),
        "<div class=\"file-info\">".toCharArray(),
        "<img".toCharArray(),
        "data-post-local-id=\"".toCharArray(),
        "<span class=\"reply-title\">".toCharArray(),
        "<span class=\"poster-name\">".toCharArray(),
        "<span class=\"time\">".toCharArray(),
        "<div class=\"omitted\">".toCharArray(),
        "<div class=\"message\">".toCharArray()
    };
    
    private static final char[][] FILTERS_CLOSE = {
        null,
        null,
        "</div>".toCharArray(),
        "</div>".toCharArray(),
        ">".toCharArray(),
        "\"".toCharArray(),
        "</span>".toCharArray(),
        "</span>".toCharArray(),
        "</span>".toCharArray(),
        "</div>".toCharArray(),
        "</div>".toCharArray()
    };
    
    private final Reader _in;
    private boolean canCloudflare;
    
    private StringBuilder readBuffer = new StringBuilder();
    private List<ThreadModel> threads;
    protected ThreadModel currentThread;
    private List<PostModel> postsBuf;
    private PostModel currentPost;
    private StringBuilder omittedDigitsBuffer = new StringBuilder();
    private List<AttachmentModel> currentAttachments;
    
    public MonabaReader(Reader reader, boolean canCloudflare) {
        _in = reader;
        this.canCloudflare = canCloudflare;
    }
    
    public MonabaReader(InputStream in, boolean canCloudFlare) {
        this(new BufferedReader(new InputStreamReader(in)), canCloudFlare);
    }
    
    public MonabaReader(InputStream in) {
        this(in, false);
    }
    
    private void initThreadModel() {
        currentThread = new ThreadModel();
        currentThread.postsCount = 0;
        currentThread.attachmentsCount = -1;
        postsBuf = new ArrayList<>();
    }
    
    private void initPostModel() {
        currentPost = new PostModel();
        currentPost.name = "";
        currentAttachments = new ArrayList<AttachmentModel>();
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
            if (canCloudflare) {
                currentPost.subject = CryptoUtils.fixCloudflareEmails(currentPost.subject);
                currentPost.comment = CryptoUtils.fixCloudflareEmails(currentPost.comment);
            }
            postprocessPost(currentPost);
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
        switch (filterIndex) {
            case FILTER_THREAD_END:
                finalizeThread();
                break;
            case FILTER_POSTNUMBER:
                currentPost.number = readUntilSequence(FILTERS_CLOSE[filterIndex]);
                break;
            case FILTER_SUBJECT:
                currentPost.subject = StringEscapeUtils.unescapeHtml4(readUntilSequence(FILTERS_CLOSE[filterIndex])).trim();
                break;
            case FILTER_NAME:
                currentPost.name = readUntilSequence(FILTERS_CLOSE[filterIndex]);
                break;
            case FILTER_DATE:
                parseDate(readUntilSequence(FILTERS_CLOSE[filterIndex]));
                break;
            case FILTER_ATTACHMENT:
                Matcher matcher = ATTACHMENT_PATTERN.matcher(readUntilSequence(FILTERS_CLOSE[filterIndex]));
                AttachmentModel attachment = new AttachmentModel();
                attachment.size = -1;
                attachment.width = -1;
                attachment.height = -1;
                if (matcher.find()) {
                    attachment.originalName = matcher.group(1);
                    attachment.path = matcher.group(2);
                    String ext = attachment.path.substring(attachment.path.lastIndexOf('.') + 1).toLowerCase(Locale.US);
                    switch (ext) {
                        case "jpg":
                        case "jpeg":
                        case "png":
                            attachment.type = AttachmentModel.TYPE_IMAGE_STATIC;
                            break;
                        case "gif":
                            attachment.type = AttachmentModel.TYPE_IMAGE_GIF;
                            break;
                        case "webm":
                        case "mp4":
                        case "ogv":
                            attachment.type = AttachmentModel.TYPE_VIDEO;
                            break;
                        case "mp3":
                        case "ogg":
                            attachment.type = AttachmentModel.TYPE_AUDIO;
                            break;
                        default:
                            attachment.type = AttachmentModel.TYPE_OTHER_FILE;
                    }
                }
                ++currentThread.attachmentsCount;
                currentAttachments.add(attachment);
                break;
            case FILTER_ATTACHMENT_INFO:
                parseAttachmentSizes(readUntilSequence(FILTERS_CLOSE[filterIndex]));
                break;
            case FILTER_ATTACHMENT_THUMBNAIL:
                parseThumbnail(readUntilSequence(FILTERS_CLOSE[filterIndex]));
                break;
            case FILTER_OMITTEDPOSTS:
                parseOmittedString(readUntilSequence(FILTERS_CLOSE[filterIndex]));
                break;
            case FILTER_START_COMMENT:
                currentPost.comment = readUntilSequence(FILTERS_CLOSE[filterIndex]);
                finalizePost();
                break;
        }
    }
    
    private void parseAttachmentSizes(String html) {
        int currentAttachmentsCount = currentAttachments.size();
        if (currentAttachmentsCount > 0) {
            AttachmentModel currentAttachment = currentAttachments.get(currentAttachmentsCount - 1);
            if (currentAttachment.size == -1) {
                Matcher byteSizeMatcher = ATTACHMENT_SIZE_PATTERN.matcher(html);
                if (byteSizeMatcher.find()) {
                    try {
                        String digits = byteSizeMatcher.group(1);
                        int multiplier = 1;
                        String prefix = byteSizeMatcher.group(2);
                        if (prefix != null) {
                            if (prefix.equalsIgnoreCase("k")) multiplier = 1024;
                            else if (prefix.equalsIgnoreCase("m")) multiplier = 1024 * 1024;
                        }
                        int value = Math.round(Float.parseFloat(digits) / 1024 * multiplier);
                        currentAttachment.size = value;
                    } catch (NumberFormatException e) {}
                }
            }
            if (currentAttachment.width == -1 && currentAttachment.height == -1) {
                Matcher pxSizeMatcher = ATTACHMENT_PX_SIZE_PATTERN.matcher(html);
                if (pxSizeMatcher.find()) {
                    currentAttachment.width = Integer.parseInt(pxSizeMatcher.group(1));
                    currentAttachment.height = Integer.parseInt(pxSizeMatcher.group(2));
                }
            }
        }
    }
    
    private void parseThumbnail(String imgTag) {
        int currentAttachmentsCount = currentAttachments.size();
        if (currentAttachmentsCount > 0 && currentAttachments.get(currentAttachmentsCount - 1).thumbnail == null) {
            int start, end;
            if ((start = imgTag.indexOf("src=\"")) != -1 && (end = imgTag.indexOf('\"', start + 5)) != -1) {
                currentAttachments.get(currentAttachmentsCount - 1).thumbnail = imgTag.substring(start + 5, end);
            }
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
    
    protected void postprocessPost(PostModel post) {
        currentPost.comment = RegexUtils.replaceAll(currentPost.comment, REPLY_URL_PATTERN, "<a href=\"$2#$1\">");
    }
    
    protected void parseDate(String date) {
        if (date.length() > 0) {
            try {
                currentPost.timestamp = DEFAULT_MONABA_DATEFORMAT.parse(date).getTime();
            } catch (Exception e) {
                Logger.e(TAG, "cannot parse date; make sure you choose the right DateFormat for this chan", e);
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
