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

package nya.miku.wishmaster.chans.arhivach;

import android.annotation.SuppressLint;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BadgeIconModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.util.CryptoUtils;
import nya.miku.wishmaster.common.Logger;

/**
 * Created by Kalaver <Kalaver@users.noreply.github.com> on 03.07.2015.
 */
@SuppressLint("SimpleDateFormat")
public class ArhivachThreadReader  implements Closeable {
    private static final String TAG = "ArhivachThreadReader";

    private static final DateFormat CHAN_DATEFORMAT;
    static {
        DateFormatSymbols chanSymbols = new DateFormatSymbols();
        chanSymbols.setShortWeekdays(new String[] { "", "Вск", "Пнд", "Втр", "Срд", "Чтв", "Птн", "Суб" });

        CHAN_DATEFORMAT = new SimpleDateFormat("dd/MM/yy EEE HH:mm:ss", chanSymbols);
        CHAN_DATEFORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
    }

    private static final Pattern URL_PATTERN =
            Pattern.compile("((https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])");

    private static final char[] DATA_START = "class=\"thread_inner\"".toCharArray();

    private static final int FILTER_THREAD_END = 0;
   // private static final int FILTER_POST_HEAD = 1;
    private static final int FILTER_ATTACHMENT = 1;
    private static final int FILTER_ATTACHMENT_ORIGINAL = 2;
    private static final int FILTER_ATTACHMENT_THUMBNAIL = 3;
    private static final int FILTER_START_COMMENT_BODY = 4;
    private static final int FILTER_START_COMMENT = 5;
    private static final int FILTER_END_COMMENT = 6;

    private static final int FILTER_SAGE = 7;
    private static final int FILTER_NAME = 8;
    private static final int FILTER_TRIP = 9;
    private static final int FILTER_TIME = 10;
    private static final int FILTER_ID_START = 11;
    private static final int FILTER_SUBJECT = 12;
    private static final int FILTER_ID = 13;
    private static final int FILTER_MAIL = 14;
    private static final int FILTER_OP = 15;
    private static final int FILTER_DELETED = 16;

    public static final char[][] FILTERS_OPEN = {
            "</html>".toCharArray(),

            //"class=\"post_head\"".toCharArray(),

            "<div class=\"post_image_block\"".toCharArray(),
            "<a".toCharArray(),
            "<img".toCharArray(),

            "class=\"post_comment_body\"".toCharArray(),

            "class=\"post_comment\"".toCharArray(),

            "</div>".toCharArray(),

            "class=\"poster_sage\"".toCharArray(),

            "class=\"poster_name\">".toCharArray(),

            "class=\"poster_trip\"".toCharArray(),

            "class=\"post_time\">".toCharArray(),

            "class=\"post_id\"".toCharArray(),

            "class=\"post_subject\">".toCharArray(),

            "id=\"".toCharArray(),

            "href=\"mailto:".toCharArray(),

            "label-success\">OP".toCharArray(),

            "class=\"post post_deleted\"".toCharArray(),
    };

    private static final char[][] FILTERS_CLOSE = {
            null,

           // "</div>".toCharArray(),

            ">".toCharArray(),
            ">".toCharArray(),
            ">".toCharArray(),

            ">".toCharArray(),

            ">".toCharArray(),

            null,

            ">".toCharArray(),

            "</span>".toCharArray(),

            "</span>".toCharArray(),

            "</span>".toCharArray(),

            "</span>".toCharArray(),

            "</".toCharArray(),

            "\"".toCharArray(),

            ">".toCharArray(),

            "</span>".toCharArray(),

            ">".toCharArray(),
    };

    private final Reader _in;

    private StringBuilder readBuffer = new StringBuilder();
    private List<ThreadModel> threads;
    private ThreadModel currentThread;
    private List<PostModel> postsBuf;
    private PostModel currentPost;
    private List<AttachmentModel> currentAttachments;


    public ArhivachThreadReader(Reader reader) {
        _in = reader;
    }

    public ArhivachThreadReader(InputStream in) {
        this(new BufferedReader(new InputStreamReader(in)));
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
        postsBuf = new ArrayList<PostModel>();
    }

    private void initPostModel() {
        currentPost = new PostModel();
        currentPost.number = "unknown";
        currentPost.trip = "";
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
            if (currentPost.name == null) currentPost.name = "";
            if (currentPost.subject == null) currentPost.subject = "";
            if (currentPost.comment == null) currentPost.comment = "";
            if (currentPost.email == null) currentPost.email = "";
            if (currentPost.trip == null) currentPost.trip = "";
            currentPost.comment = CryptoUtils.fixCloudflareEmails(currentPost.comment);
            currentPost.subject = CryptoUtils.fixCloudflareEmails(currentPost.subject);
            postsBuf.add(currentPost);
        }
        initPostModel();
    }

    private void handleFilter(int filterIndex) throws IOException {
        switch (filterIndex) {
            case FILTER_THREAD_END:
                finalizeThread();
                break;
            case FILTER_ATTACHMENT:
                skipUntilSequence(FILTERS_CLOSE[filterIndex]);
                parseAttachment();
                break;
            case FILTER_START_COMMENT_BODY:
                skipUntilSequence(FILTERS_CLOSE[filterIndex]);
                readPost();
                finalizePost();
                break;
            case FILTER_SAGE:
                skipUntilSequence(FILTERS_CLOSE[FILTER_START_COMMENT]);
                currentPost.sage=true;
                break;
            case FILTER_NAME:
                parseName(readUntilSequence(FILTERS_CLOSE[filterIndex]));
                break;
            case FILTER_TRIP:
                skipUntilSequence(">".toCharArray());
                currentPost.trip = readUntilSequence(FILTERS_CLOSE[filterIndex]);
                break;
            case FILTER_TIME:
                parseDate(readUntilSequence(FILTERS_CLOSE[filterIndex]));
                break;
            case FILTER_ID_START:
                skipUntilSequence(FILTERS_OPEN[FILTER_ID]);
                currentPost.number=readUntilSequence(FILTERS_CLOSE[FILTER_ID]);
                skipUntilSequence(FILTERS_CLOSE[filterIndex]);
                break;
            case FILTER_SUBJECT:
                currentPost.subject=readUntilSequence(FILTERS_CLOSE[filterIndex]);
                break;
            case FILTER_MAIL:
                parseEmail(readUntilSequence(FILTERS_CLOSE[filterIndex]));
                break;
            case FILTER_OP:
                skipUntilSequence(FILTERS_CLOSE[filterIndex]);
                currentPost.op=true;
                break;
            case FILTER_DELETED:
                skipUntilSequence(FILTERS_CLOSE[filterIndex]);
                currentPost.deleted = true;
                break;
        }
    }

    protected void parseName(String s) {
        int index = s.indexOf("<");
        if (index>0) {
            currentPost.name = s.substring(0, index);
            Matcher matcher = Pattern.compile("src=\"([^\"]*)\"(?:.*?title=\"([^\"]*)\")?",Pattern.MULTILINE).matcher(s);
            ArrayList<BadgeIconModel> icons=new ArrayList<BadgeIconModel>();
            while (matcher.find()) {
                BadgeIconModel icon = new BadgeIconModel();
                icon.source=matcher.group(1);
                icon.description=matcher.group(2);
                icons.add(icon);
            }
            if (icons.size()>0)
                currentPost.icons = icons.toArray(new BadgeIconModel[icons.size()]);
        } else
            currentPost.name=s;
    }

    protected void parseEmail(String s) {
        if (s.contains("post_mail")) {
            currentPost.email=s.substring(0,s.indexOf("\""));
        }
    }

    protected void readPost() throws IOException {
        String commentData = readUntilSequence(FILTERS_OPEN[FILTER_END_COMMENT]);
        currentPost.comment = commentData;

    }

    private void parseAttachment() throws IOException {
        skipUntilSequence(FILTERS_OPEN[FILTER_ATTACHMENT_ORIGINAL]);
        String attachment = readUntilSequence(FILTERS_CLOSE[FILTER_ATTACHMENT_ORIGINAL]);

        String thumbnail="";
        String original="";

        Matcher matcher = URL_PATTERN.matcher(attachment);
        if (matcher.find()) original = matcher.group(1);
        skipUntilSequence(FILTERS_OPEN[FILTER_ATTACHMENT_THUMBNAIL]);
        attachment = readUntilSequence(FILTERS_CLOSE[FILTER_ATTACHMENT_THUMBNAIL]);
        matcher = URL_PATTERN.matcher(attachment);
        if (matcher.find()) thumbnail = matcher.group(1);

        if ((original.length()>0)) {
            AttachmentModel model = new AttachmentModel();
            model.type = AttachmentModel.TYPE_OTHER_FILE;
            model.size = -1;
            model.width = -1;
            model.height = -1;
            model.path = original;
            if (thumbnail.length()>0)
                model.thumbnail = thumbnail;
            else
                model.thumbnail = original;
            String ext = model.path.substring(model.path.lastIndexOf('.') + 1).toLowerCase(Locale.US);
            if (ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg")) model.type = AttachmentModel.TYPE_IMAGE_STATIC;
            else if (ext.equals("gif")) model.type = AttachmentModel.TYPE_IMAGE_GIF;
            else if (ext.equals("webm")) model.type = AttachmentModel.TYPE_VIDEO;
            else if (ext.equals("mp3") || ext.equals("ogg")) model.type = AttachmentModel.TYPE_VIDEO;
            ++currentThread.attachmentsCount;
            currentAttachments.add(model);
        }
    }

    protected void parseDate(String date) {
        if (date.length() > 0) {
            try {
                currentPost.timestamp = CHAN_DATEFORMAT.parse(date).getTime();
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
