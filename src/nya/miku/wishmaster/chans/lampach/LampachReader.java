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

package nya.miku.wishmaster.chans.lampach;

import android.annotation.SuppressLint;
import android.graphics.Color;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;

import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.api.util.WakabaReader;

@SuppressLint("SimpleDateFormat")
public class LampachReader extends WakabaReader {
    
    private static final Pattern SPAN_ADMIN_PATTERN = Pattern.compile("<span\\s*style=\"color:\\s*(\\w+)[^\"]*\">(.*?)</span>(.*)");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("<a[^>]*href=\"mailto:([^\"]*)\"");
    
    private static final DateFormat DATE_FORMAT;
    static {
        DATE_FORMAT = new SimpleDateFormat("dd MMM yy(EEE)HH:mm:ss", Locale.US);
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    
    private static final char[] LABELOPEN_FILTER = "<label>".toCharArray();
    private static final char[] LABELCLOSE_FILTER = "</label>".toCharArray();
    private static final char[] BLOCKQUOTE_OPEN = "<div class=\"message\">".toCharArray();
    private static final char[] BLOCKQUOTE_CLOSE = "</div>".toCharArray();
    private static final char[] NUMBEROPEN_FILTER = "<a id=\"".toCharArray();
    private static final char[] NUMBERCLOSE_FILTER = "\"".toCharArray();
    
    private int curLabelOpenPos = 0;
    private int curLabelClosePos = 0;
    private int curCommentPos = 0;
    private int curNumberPos = 0;
    private StringBuilder labelBuf = new StringBuilder();
    private boolean inLabel = false;
    private String boardName;
    
    public LampachReader(InputStream in, String boardName) {
        super(in);
        this.boardName = boardName;
    }
    
    @Override
    protected void customFilters(int ch) throws IOException {
        if (inLabel) labelBuf.append((char) ch);
        
        if (ch == NUMBEROPEN_FILTER[curNumberPos]) {
            ++curNumberPos;
            if (curNumberPos == NUMBEROPEN_FILTER.length) {
                currentPost.number = readUntilSequence(NUMBERCLOSE_FILTER);
                curNumberPos = 0;
            }
        } else {
            if (curNumberPos != 0) curNumberPos = ch == NUMBEROPEN_FILTER[0] ? 1 : 0;
        }
        
        if (ch == BLOCKQUOTE_OPEN[curCommentPos]) {
            ++curCommentPos;
            if (curCommentPos == BLOCKQUOTE_OPEN.length) {
                currentPost.comment = readUntilSequence(BLOCKQUOTE_CLOSE);
                finalizePost();
                curCommentPos = 0;
            }
        } else {
            if (curCommentPos != 0) curCommentPos = ch == BLOCKQUOTE_OPEN[0] ? 1 : 0;
        }
        
        if (ch == LABELOPEN_FILTER[curLabelOpenPos]) {
            ++curLabelOpenPos;
            if (curLabelOpenPos == LABELOPEN_FILTER.length) {
                inLabel = true;
                labelBuf.setLength(0);
                curLabelOpenPos = 0;
            }
        } else {
            if (curLabelOpenPos != 0) curLabelOpenPos = ch == LABELOPEN_FILTER[0] ? 1 : 0;
        }
        
        if (ch == LABELCLOSE_FILTER[curLabelClosePos]) {
            ++curLabelClosePos;
            if (curLabelClosePos == LABELCLOSE_FILTER.length) {
                inLabel = false;
                Matcher matcher = EMAIL_PATTERN.matcher(labelBuf.toString());
                if (matcher.find()) {
                    currentPost.email = matcher.group(1);
                    if (currentPost.email.toLowerCase(Locale.US).equals("sage")) {
                        currentPost.sage = true;
                    }
                }
                curLabelClosePos = 0;
            }
        } else {
            if (curLabelClosePos != 0) curLabelClosePos = ch == LABELCLOSE_FILTER[0] ? 1 : 0;
        }
        
    }
    
    @Override
    protected void parseDate(String date) {
        Matcher matcher = SPAN_ADMIN_PATTERN.matcher(date);
        if (matcher.find()) {
            currentPost.trip = (currentPost.trip == null ? "" : currentPost.trip)
                    + StringEscapeUtils.unescapeHtml4(matcher.group(2));
            currentPost.color = Color.parseColor(matcher.group(1));
            date = matcher.group(3);
        } else {
            if (date.startsWith("<")) date = RegexUtils.removeHtmlTags(date);
        }
        
        try {
            currentPost.timestamp = DATE_FORMAT.parse(date.replace("|", "")).getTime();
        } catch (Exception e) {
            super.parseDate(date);
        }
    }
    
    @Override
    protected void postprocessPost(PostModel post) {
        post.comment = post.comment.replace("/>",">").
                replaceAll("(href=['\"])(?:\\.\\./)?res/", "$1/" + boardName + "/res/");
        if (post.attachments != null) {
            for (AttachmentModel attachment : post.attachments) {
                if (attachment.path != null) {
                    attachment.path = fixAttachmentPath(attachment.path);
                }
                if (attachment.thumbnail != null) {
                    attachment.thumbnail = fixAttachmentPath(attachment.thumbnail);
                }
            }
        }
    }
    
    private String fixAttachmentPath(String url) {
        if (url.startsWith("/") || url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        } else if (url.startsWith("../")) {
            url = url.substring(3);
        }
        return "/" + boardName + "/" + url;
    }
}
