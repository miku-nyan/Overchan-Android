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

package nya.miku.wishmaster.chans.sevenchan;

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
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.api.util.WakabaReader;
import nya.miku.wishmaster.common.CryptoUtils;

public class SevenchanReader extends WakabaReader {
    
    private static final char[] P_OPEN = "<p>".toCharArray();
    private static final char[] P_CLOSE = "</p>".toCharArray();
    private static final char[] SPAN_CLOSE = "</span>".toCharArray();
    
    private StringBuilder commentBuffer = new StringBuilder();
    private boolean inDate = false;
    private StringBuilder dateBuffer = new StringBuilder();
    private String lastThumbnail = null;
    private String lastAdminMark = null;
    
    private static final DateFormat DATE_FORMAT, DATE_FORMAT_ALT;
    static {
        DATE_FORMAT = new SimpleDateFormat("yy/MM/dd(EEE)HH:mm", Locale.US);
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone(SevenchanModule.TIMEZONE));
        DATE_FORMAT_ALT = new SimpleDateFormat("yy/MM/dd HH:mm:ss", Locale.US);        
        DATE_FORMAT_ALT.setTimeZone(TimeZone.getTimeZone(SevenchanModule.TIMEZONE));
    }
    
    private static final Pattern DATE_PATTERN = Pattern.compile("((?:[^\\s]+\\) )?[^\\s]+)\\s*<span class=\"reflink\">$");
    private static final Pattern ATTACHMENT_SIZE_PATTERN = Pattern.compile("([\\.\\d]+) ?([km])?b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ATTACHMENT_PX_SIZE_PATTERN = Pattern.compile("(\\d+)[x×](\\d+)"); // \u0078 \u00D7
    private static final Pattern ATTACHMENT_ORIGINAL_NAME_PATTERN = Pattern.compile("\\s*,?([^<\\)]*)");
    private static final Pattern EMBEDDED_PATTERN =
            Pattern.compile("<a href=\"javascript:;\" onmousedown=\"if\\(document.getElementById\\('(.*?)'\\)(?:[^\"]*?)\">");
    
    private static final char[] NUMBER_FILTER = "<input type=\"checkbox\" name=\"post[]\" value=\"".toCharArray();
    private static final char[] SUBJECT_FILTER = "<span class=\"subject\">".toCharArray();    
    private static final char[] ATTACHMENT_FILTER = "<p class=\"file_size\">".toCharArray();
    private static final char[] ATTACHMENT_MULTI_FIRST_FILTER = "<span class=\"multithumbfirst\">".toCharArray();
    private static final char[] ATTACHMENT_MULTI_FILTER = "<span class=\"multithumb\">".toCharArray();
    private static final char[] COMMENT_FILTER = "<p class=\"message\">".toCharArray();
    private static final char[] DATE_START_FILTER = "<input type=\"checkbox\" name=\"post[]\"".toCharArray();
    private static final char[] DATE_END_FILTER = "<span class=\"reflink\">".toCharArray();
    private static final char[] ADMIN_FILTER = "<span title=\"7chan administrator\" class=\"capcode\">".toCharArray();
    
    private int curNumberPos = 0;
    private int curSubjectPos = 0;
    private int curAttachmentPos = 0;
    private int curAttachmentMultiFirstPos = 0;
    private int curAttachmentMultiPos = 0;
    private int curCommentPos = 0;
    private int curDateStartPos = 0;
    private int curDateEndPos = 0;
    private int curAdminPos = 0;
    
    public SevenchanReader(InputStream in) {
        super(in, DATE_FORMAT);
    }
    
    @Override
    protected void customFilters(int ch) throws IOException {
        if (inDate) dateBuffer.append((char) ch);
        
        if (ch == NUMBER_FILTER[curNumberPos]) {
            ++curNumberPos;
            if (curNumberPos == NUMBER_FILTER.length) {
                currentPost.number = readUntilSequence("\"".toCharArray());
                curNumberPos = 0;
            }
        } else {
            if (curNumberPos != 0) curNumberPos = ch == NUMBER_FILTER[0] ? 1 : 0;
        }
        
        if (ch == SUBJECT_FILTER[curSubjectPos]) {
            ++curSubjectPos;
            if (curSubjectPos == SUBJECT_FILTER.length) {
                currentPost.subject = StringEscapeUtils.unescapeHtml4(readUntilSequence(SPAN_CLOSE)).trim();
                curSubjectPos = 0;
            }
        } else {
            if (curSubjectPos != 0) curSubjectPos = ch == SUBJECT_FILTER[0] ? 1 : 0;
        }
        
        if (ch == ATTACHMENT_FILTER[curAttachmentPos]) {
            ++curAttachmentPos;
            if (curAttachmentPos == ATTACHMENT_FILTER.length) {
                parseAttachment(readUntilSequence(P_CLOSE));
                curAttachmentPos = 0;
            }
        } else {
            if (curAttachmentPos != 0) curAttachmentPos = ch == ATTACHMENT_FILTER[0] ? 1 : 0;
        }
        
        if (ch == ATTACHMENT_MULTI_FIRST_FILTER[curAttachmentMultiFirstPos]) {
            ++curAttachmentMultiFirstPos;
            if (curAttachmentMultiFirstPos == ATTACHMENT_MULTI_FIRST_FILTER.length) {
                parseAttachment(readUntilSequence(SPAN_CLOSE));
                curAttachmentMultiFirstPos = 0;
            }
        } else {
            if (curAttachmentMultiFirstPos != 0) curAttachmentMultiFirstPos = ch == ATTACHMENT_MULTI_FIRST_FILTER[0] ? 1 : 0;
        }
        
        if (ch == ATTACHMENT_MULTI_FILTER[curAttachmentMultiPos]) {
            ++curAttachmentMultiPos;
            if (curAttachmentMultiPos == ATTACHMENT_MULTI_FILTER.length) {
                parseAttachment(readUntilSequence(SPAN_CLOSE));
                curAttachmentMultiPos = 0;
            }
        } else {
            if (curAttachmentMultiPos != 0) curAttachmentMultiPos = ch == ATTACHMENT_MULTI_FILTER[0] ? 1 : 0;
        }
        
        if (ch == COMMENT_FILTER[curCommentPos]) {
            ++curCommentPos;
            if (curCommentPos == COMMENT_FILTER.length) {
                currentPost.comment = readPostComment();
                if (lastAdminMark != null) {
                    currentPost.trip = lastAdminMark + (currentPost.trip == null ? "" : currentPost.trip);
                    lastAdminMark = null;
                }
                finalizePost();
                curCommentPos = 0;
            }
        } else {
            if (curCommentPos != 0) curCommentPos = ch == COMMENT_FILTER[0] ? 1 : 0;
        }
        
        if (ch == DATE_START_FILTER[curDateStartPos]) {
            ++curDateStartPos;
            if (curDateStartPos == DATE_START_FILTER.length) {
                inDate = true;
                dateBuffer.setLength(0);
                curDateStartPos = 0;
            }
        } else {
            if (curDateStartPos != 0) curDateStartPos = ch == DATE_START_FILTER[0] ? 1 : 0;
        }
        
        if (ch == DATE_END_FILTER[curDateEndPos]) {
            ++curDateEndPos;
            if (curDateEndPos == DATE_END_FILTER.length) {
                Matcher m = DATE_PATTERN.matcher(dateBuffer.toString().trim());
                if (m.find()) {
                    String date = m.group(1);
                    parseDate(date);
                    if (currentPost.timestamp == 0) {
                        try {
                            date = StringEscapeUtils.unescapeHtml4(date);
                            date = new StringBuilder().
                                    append((char)(date.charAt(2) - 65248)).
                                    append((char)(date.charAt(3) - 65248)).
                                    append('/').
                                    append((char)(date.charAt(5) - 65248)).
                                    append((char)(date.charAt(6) - 65248)).
                                    append('/').
                                    append((char)(date.charAt(8) - 65248)).
                                    append((char)(date.charAt(9) - 65248)).
                                    append(' ').
                                    append((char)(date.charAt(15) - 65248)).
                                    append((char)(date.charAt(16) - 65248)).
                                    append(':').
                                    append((char)(date.charAt(18) - 65248)).
                                    append((char)(date.charAt(19) - 65248)).
                                    append(':').
                                    append((char)(date.charAt(21) - 65248)).
                                    append((char)(date.charAt(22) - 65248)).toString();
                            currentPost.timestamp = DATE_FORMAT_ALT.parse(date).getTime();
                        } catch (Exception e) {}
                    }
                }
                inDate = false;
                dateBuffer.setLength(0);
                curDateEndPos = 0;
            }
        } else {
            if (curDateEndPos != 0) curDateEndPos = ch == DATE_END_FILTER[0] ? 1 : 0;
        }
        
        if (ch == ADMIN_FILTER[curAdminPos]) {
            ++curAdminPos;
            if (curAdminPos == ADMIN_FILTER.length) {
                lastAdminMark = StringEscapeUtils.unescapeHtml4(readUntilSequence(SPAN_CLOSE)).trim();
                curAdminPos = 0;
            }
        } else {
            if (curAdminPos != 0) curAdminPos = ch == ADMIN_FILTER[0] ? 1 : 0;
        }        
        
    }
    
    @Override
    protected void parseAttachment(String html) {
        int before = currentAttachments.size();
        super.parseAttachment(html);
        if (currentAttachments.size() > before) {
            currentAttachments.get(currentAttachments.size() - 1).thumbnail = lastThumbnail;
            lastThumbnail = null;
        }
    }
    
    @Override
    protected void parseThumbnail(String imgTag) {
        if (imgTag.contains("class=\"multithumbfirst\"") || imgTag.contains("class=\"multithumb\"")) {
            if (currentAttachments.size() > 0) {
                AttachmentModel attachment = currentAttachments.get(currentAttachments.size() - 1);
                int start, end;
                if ((start = imgTag.indexOf("src=\"")) != -1 && (end = imgTag.indexOf('\"', start + 5)) != -1)
                    attachment.thumbnail = imgTag.substring(start + 5, end);
                
                Matcher byteSizeMatcher = ATTACHMENT_SIZE_PATTERN.matcher(imgTag);
                while (byteSizeMatcher.find()) {
                    try {
                        String digits = byteSizeMatcher.group(1);
                        int multiplier = 1;
                        String prefix = byteSizeMatcher.group(2);
                        if (prefix != null) {
                            if (prefix.equalsIgnoreCase("k")) multiplier = 1024;
                            else if (prefix.equalsIgnoreCase("m")) multiplier = 1024 * 1024;
                        }
                        int value = Math.round(Float.parseFloat(digits) / 1024 * multiplier);
                        attachment.size = value;
                    } catch (NumberFormatException e) {}
                }
                
                Matcher pxSizeMatcher = ATTACHMENT_PX_SIZE_PATTERN.matcher(imgTag);
                int indexEndPxSize = -1;
                while (pxSizeMatcher.find()) {
                    try {
                        int width = Integer.parseInt(pxSizeMatcher.group(1));
                        int height = Integer.parseInt(pxSizeMatcher.group(2));
                        attachment.width = width;
                        attachment.height = height;
                        indexEndPxSize = pxSizeMatcher.end();
                    } catch (NumberFormatException e) {}
                }
                
                if (indexEndPxSize != -1) {
                    Matcher originalNameMatcher = ATTACHMENT_ORIGINAL_NAME_PATTERN.matcher(imgTag);
                    if (originalNameMatcher.find(indexEndPxSize)) {
                        String originalName = originalNameMatcher.group(1).trim();
                        if (originalName != null && originalName.length() > 0) {
                            attachment.originalName = StringEscapeUtils.unescapeHtml4(originalName);
                        }
                    }
                }
                
            }
        } else if (imgTag.contains("/css/locked.gif")) {
            currentThread.isClosed = true;
        } else if (imgTag.contains("/css/sticky.gif")) {
            currentThread.isSticky = true;
        } else {
            int start, end;
            if ((start = imgTag.indexOf("src=\"")) != -1 && (end = imgTag.indexOf('\"', start + 5)) != -1)
                lastThumbnail = imgTag.substring(start + 5, end);
        }
    }
    
    @Override
    protected String readPostComment() throws IOException {
        commentBuffer.setLength(0);
        int len1 = P_OPEN.length;
        int len2 = P_CLOSE.length;
        int pos1 = 0;
        int pos2 = 0;
        int tagCounter = 1;
        int curChar;
        while ((curChar = _in.read()) != -1) {
            commentBuffer.append((char) curChar);
            
            if (curChar == P_OPEN[pos1]) {
                ++pos1;
                if (pos1 == len1) {
                    ++tagCounter;
                    pos1 = 0;
                }
            } else {
                if (pos1 != 0) pos1 = curChar == P_OPEN[0] ? 1 : 0;
            }
            
            if (curChar == P_CLOSE[pos2]) {
                ++pos2;
                if (pos2 == len2) {
                    --tagCounter;
                    if (tagCounter == 0) break;
                    pos2 = 0;
                }
            } else {
                if (pos2 != 0) pos2 = curChar == P_CLOSE[0] ? 1 : 0;
            }
        }
        int buflen = commentBuffer.length();
        if (buflen > len2) {
            commentBuffer.setLength(buflen - len2); 
            return RegexUtils.replaceAll(CryptoUtils.fixCloudflareEmails(commentBuffer.toString()),
                    EMBEDDED_PATTERN, "<a href=\"http://youtube\\.com/watch?v=$1\">");
        } else {
            return "";
        }
    }
}
