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

package nya.miku.wishmaster.chans.inach;

import android.annotation.SuppressLint;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BadgeIconModel;
import nya.miku.wishmaster.api.util.ReplacingReader;
import nya.miku.wishmaster.api.util.WakabaReader;
import nya.miku.wishmaster.common.Logger;

@SuppressLint("SimpleDateFormat")
public class InachReader extends WakabaReader {
    private static final String TAG = "InachReader";
    
    static final DateFormat DATE_FORMAT;
    static {
        DateFormatSymbols inachSymbols = new DateFormatSymbols();
        inachSymbols.setShortWeekdays(new String[] { "", "Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб" });
        DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy (EEE) HH:mm:ss", inachSymbols);
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
    }
    
    private static final Pattern BADGE_ICON_PATTERN = Pattern.compile("<img src=\"(.*?)\"(?: title=\"(.*?))?\">", Pattern.DOTALL);
    
    private static final char[] EMBED_FILTER_OPEN = "<div id=\"video_".toCharArray();
    private static final char[] EMBED_FILTER_CLOSE = "</div>".toCharArray();
    private int embedFilterCurPos = 0;
    
    private static final char[] DATE_FILTER_OPEN = "<span style=\"display: table-cell; vertical-align: middle;\">&nbsp;".toCharArray();
    private static final char[] DATE_FILTER_CLOSE = "</span>".toCharArray();
    private int dateFilterCurPos = 0;
    
    public InachReader(InputStream in) {
        super(new ReplacingReader(new ReplacingReader(new BufferedReader(new InputStreamReader(in)),
                        "' style='display: table-cell; vertical-align: middle;'", "'"),
                        "<span style='display: table-cell; vertical-align: middle;' ", "<span ")  {
            private boolean inTag = false;
            @Override
            public int read() throws IOException {
                int ch = super.read();
                switch (ch) {
                    case '<': inTag = true; break;
                    case '>': inTag = false; break;
                    case '\'': if (inTag) return '\"'; 
                }
                return ch;
            }
        }, DATE_FORMAT);
    }
    
    @Override
    protected void customFilters(int ch) throws IOException {
        if (ch == DATE_FILTER_OPEN[dateFilterCurPos]) {
            ++dateFilterCurPos;
            if (dateFilterCurPos == DATE_FILTER_OPEN.length) {
                String date = readUntilSequence(DATE_FILTER_CLOSE);
                if (date.endsWith("&nbsp;")) date = date.substring(0, date.length() - 6);
                date = date.trim();
                if (date.length() > 0) {
                    Matcher badgeIcon = BADGE_ICON_PATTERN.matcher(date);
                    if (badgeIcon.matches()) {
                        BadgeIconModel iconModel = new BadgeIconModel();
                        iconModel.source = badgeIcon.group(1);
                        iconModel.description = badgeIcon.group(2);
                        int currentIconsCount = currentPost.icons == null ? 0 : currentPost.icons.length;
                        BadgeIconModel[] newIconsArray = new BadgeIconModel[currentIconsCount + 1];
                        for (int i=0; i<currentIconsCount; ++i) newIconsArray[i] = currentPost.icons[i];
                        newIconsArray[currentIconsCount] = iconModel;
                        currentPost.icons = newIconsArray;
                    } else {
                        try {
                            currentPost.timestamp = DATE_FORMAT.parse(date).getTime();
                        } catch (Exception e) {
                            Logger.e(TAG, "cannot parse date; make sure you choose the right DateFormat for this chan", e);
                        }
                    }
                }
                
                dateFilterCurPos = 0;
            }
        } else {
            if (dateFilterCurPos != 0) dateFilterCurPos = ch == DATE_FILTER_OPEN[0] ? 1 : 0;
        }
        
        if (ch == EMBED_FILTER_OPEN[embedFilterCurPos]) {
            ++embedFilterCurPos;
            if (embedFilterCurPos == EMBED_FILTER_OPEN.length) {
                parseVideoAttachment(readUntilSequence(EMBED_FILTER_CLOSE));
                embedFilterCurPos = 0;
            }
        } else {
            if (embedFilterCurPos != 0) embedFilterCurPos = ch == EMBED_FILTER_OPEN[0] ? 1 : 0;
        }
    }
    
    private void parseVideoAttachment(String html) {
        int index = html.indexOf('_');
        if (index == -1) return;
        String id = html.substring(0, index);
        if (id.equals("image")) return;
        id = html.substring(0, html.lastIndexOf('_'));
        AttachmentModel attachment = new AttachmentModel();
        attachment.size = -1;
        attachment.type = AttachmentModel.TYPE_OTHER_NOTFILE;
        attachment.path = "http://youtube.com/watch?v=" + id;
        attachment.thumbnail = "http://img.youtube.com/vi/" + id + "/default.jpg";
        
        ++currentThread.attachmentsCount;
        currentAttachments.add(attachment);
    }
    
}
