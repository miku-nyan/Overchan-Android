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

package nya.miku.wishmaster.chans.hispachan;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;

import android.annotation.SuppressLint;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BadgeIconModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.api.util.WakabaReader;

@SuppressLint("SimpleDateFormat")
public class HispachanReader extends WakabaReader {
    
    private static final DateFormat DATE_FORMAT;
    static {
        DATE_FORMAT = new SimpleDateFormat("dd/MM/yy HH:mm");
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    
    private static final char[] FILE_SIZE_FILTER = "span style=\"font-size: 85%;\">".toCharArray();
    private static final char[] PX_SIZE_FILTER = "span class=\"moculto\">".toCharArray();
    private static final char[] FILE_NAME_FILTER = "span class=\"nombrefile\">".toCharArray();
    private static final char[] SPAN_CLOSE_FILTER = "</span>".toCharArray();
    
    private static final Pattern ATTACHMENT_SIZE_PATTERN = Pattern.compile("([\\.\\d]+) ?([km])?b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ATTACHMENT_PX_SIZE_PATTERN = Pattern.compile("(\\d+)[x×](\\d+)"); // \u0078 \u00D7
    private static final Pattern ATTACHMENT_NAME_PATTERN = Pattern.compile("[\\s,]*([^<]+)");
    private static final Pattern BADGE_ICON_PATTERN = Pattern.compile("<img [^>]*src=\"(.+?)\"(?:.*?title=\"(.+?)\")?");
    
    public HispachanReader(InputStream in, boolean canCloudflare) {
        super(in, DATE_FORMAT, canCloudflare);
    }
    
    private int curPxSizePos = 0;
    private int curFilesizePos = 0;
    private int curFilenamePos = 0;
    private int currentAttachmentWidth =-1;
    private int currentAttachmentHeight = -1;
    private int currentAttachmentSize = -1;
    private String currentAttachmentName = null;
    
    @Override
    protected void customFilters(int ch) throws IOException {
        super.customFilters(ch);
        
        if (ch == FILE_SIZE_FILTER[curFilesizePos]) {
            ++curFilesizePos;
            if (curFilesizePos == FILE_SIZE_FILTER.length) {
                currentAttachmentSize = -1;
                Matcher byteSizeMatcher = ATTACHMENT_SIZE_PATTERN.matcher(readUntilSequence("<".toCharArray()));
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
                        currentAttachmentSize = value;
                    } catch (NumberFormatException e) {}
                }
                curFilesizePos = 0;
            }
        } else {
            if (curFilesizePos != 0) curFilesizePos = ch == FILE_SIZE_FILTER[0] ? 1 : 0;
        }
        
        if (ch == PX_SIZE_FILTER[curPxSizePos]) {
            ++curPxSizePos;
            if (curPxSizePos == PX_SIZE_FILTER.length) {
                currentAttachmentWidth = -1;
                currentAttachmentHeight = -1;
                Matcher pxMatcher = ATTACHMENT_PX_SIZE_PATTERN.matcher(readUntilSequence(SPAN_CLOSE_FILTER));
                if (pxMatcher.find()) {
                    try {
                        currentAttachmentWidth = Integer.parseInt(pxMatcher.group(1));
                        currentAttachmentHeight = Integer.parseInt(pxMatcher.group(2));
                    } catch (NumberFormatException e) {}
                }
                curPxSizePos = 0;
            }
        } else {
            if (curPxSizePos != 0) curPxSizePos = ch == PX_SIZE_FILTER[0] ? 1 : 0;
        }
        
        if (ch == FILE_NAME_FILTER[curFilenamePos]) {
            ++curFilenamePos;
            if (curFilenamePos == FILE_NAME_FILTER.length) {
                currentAttachmentName = null;
                Matcher originalNameMatcher = ATTACHMENT_NAME_PATTERN.matcher(readUntilSequence(SPAN_CLOSE_FILTER));
                if (originalNameMatcher.find()) {
                    String originalName = originalNameMatcher.group(1).trim();
                    if (originalName != null && originalName.length() > 0) {
                        currentAttachmentName = StringEscapeUtils.unescapeHtml4(originalName);
                    }
                }
                curFilenamePos = 0;
            }
        } else {
            if (curFilenamePos != 0) curFilenamePos = ch == FILE_NAME_FILTER[0] ? 1 : 0;
        }
    }
    
    @Override
    protected void parseThumbnail(String imgTag) {
        super.parseThumbnail(imgTag);
        if (imgTag.contains("/css/locked.gif")) currentThread.isClosed = true;
        if (imgTag.contains("/css/sticky.gif")) currentThread.isSticky = true;
        
        if (currentAttachments.size() > 0) {
            AttachmentModel attachment = currentAttachments.get(currentAttachments.size() - 1);
            attachment.width = currentAttachmentWidth;
            attachment.height = currentAttachmentHeight;
            attachment.size = currentAttachmentSize;
            attachment.originalName = currentAttachmentName;
        }
    }
    
    @Override
    protected void parseNameEmail(String raw) {
        super.parseNameEmail(raw);
        
        Matcher iconMatcher = BADGE_ICON_PATTERN.matcher(raw);
        if (iconMatcher.find()) {
            BadgeIconModel iconModel = new BadgeIconModel();
            iconModel.source = iconMatcher.group(1);
            iconModel.description = StringEscapeUtils.unescapeHtml4(iconMatcher.group(2));
            int currentIconsCount = currentPost.icons == null ? 0 : currentPost.icons.length;
            BadgeIconModel[] newIconsArray = new BadgeIconModel[currentIconsCount + 1];
            for (int i=0; i<currentIconsCount; ++i) newIconsArray[i] = currentPost.icons[i];
            newIconsArray[currentIconsCount] = iconModel;
            currentPost.icons = newIconsArray;
        }
    }
    
    @Override
    protected void postprocessPost(PostModel post) {
        super.postprocessPost(post);
        
        post.name = RegexUtils.removeHtmlTags(post.name);
        if (post.name.contains("(OP)")) {
            post.name = post.name.replace("(OP)", "");
            post.op = true;
        }
        post.name = post.name.replace("\u00A0", " ").trim();
    }
    
}
