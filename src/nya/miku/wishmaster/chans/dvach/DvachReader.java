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

package nya.miku.wishmaster.chans.dvach;

import android.annotation.SuppressLint;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;

import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BadgeIconModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.util.WakabaReader;

@SuppressLint("SimpleDateFormat")
public class DvachReader extends WakabaReader {
    
    private static final Pattern COUNTRYBALL_PATTERN = Pattern.compile("url\\('(.*?)'");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d+ \\w+ \\d+ \\d\\d:\\d\\d:\\d\\d)$");
    private static final DateFormat DATE_FORMAT;
    static {
        DateFormatSymbols dvachSymbols = new DateFormatSymbols();
        dvachSymbols.setMonths(new String[] { "Янв", "Фев", "Мар", "Апр", "Мая", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек" });
        DATE_FORMAT = new SimpleDateFormat("dd MMM yyyy HH:mm:ss", dvachSymbols);
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
    }
    private static final char[] TRIP_FILTER = "<span class=\"postertrip \">".toCharArray();
    private static final char[] TINATRIP_FILTER = "<span class=\"postertrip vip\">".toCharArray();
    private static final char[] NUM_FILTER = "<a id=\"".toCharArray();
    private static final char[] LABELOPEN_FILTER = "<label>".toCharArray();
    private static final char[] LABELCLOSE_FILTER = "</label>".toCharArray();
    private static final char[] COUNTRYBALL_FILTER = "<div class=\"countryball\"".toCharArray();
    private int curTripPos = 0;
    private int curTinaTripPos = 0;
    private int curNumPos = 0;
    private int curLabelOpenPos = 0;
    private int curLabelClosePos = 0;
    private int curCountryBallPos = 0;
    private StringBuilder dateBuf = new StringBuilder();
    private boolean inDate = false;
    
    public DvachReader(InputStream in) {
        super(in);
    }
    
    @Override
    protected void customFilters(int ch) throws IOException {
        if (inDate) dateBuf.append((char) ch);
        
        if (ch == TRIP_FILTER[curTripPos]) {
            ++curTripPos;
            if (curTripPos == TRIP_FILTER.length) {
                currentPost.trip = StringEscapeUtils.unescapeHtml4(readUntilSequence("</span>".toCharArray()).replaceAll("<[^>]*>", "")).trim();
                curTripPos = 0;
            }
        } else {
            if (curTripPos != 0) curTripPos = ch == TRIP_FILTER[0] ? 1 : 0;
        }
        
        if (ch == TINATRIP_FILTER[curTinaTripPos]) {
            ++curTinaTripPos;
            if (curTinaTripPos == TINATRIP_FILTER.length) {
                currentPost.trip =
                        StringEscapeUtils.unescapeHtml4(readUntilSequence("</span>".toCharArray()).replaceAll("<[^>]*>", "")).trim() + '\u2655';
                curTinaTripPos = 0;
            }
        } else {
            if (curTinaTripPos != 0) curTinaTripPos = ch == TINATRIP_FILTER[0] ? 1 : 0;
        }
        
        if (ch == NUM_FILTER[curNumPos]) {
            ++curNumPos;
            if (curNumPos == NUM_FILTER.length) {
                currentPost.number = readUntilSequence("\"".toCharArray());
                curNumPos = 0;
            }
        } else {
            if (curNumPos != 0) curNumPos = ch == NUM_FILTER[0] ? 1 : 0;
        }
        
        if (ch == LABELOPEN_FILTER[curLabelOpenPos]) {
            ++curLabelOpenPos;
            if (curLabelOpenPos == LABELOPEN_FILTER.length) {
                inDate = true;
                dateBuf.setLength(0);
                curLabelOpenPos = 0;
            }
        } else {
            if (curLabelOpenPos != 0) curLabelOpenPos = ch == LABELOPEN_FILTER[0] ? 1 : 0;
        }
        
        if (ch == LABELCLOSE_FILTER[curLabelClosePos]) {
            ++curLabelClosePos;
            if (curLabelClosePos == LABELCLOSE_FILTER.length) {
                inDate = false;
                parseDvachDate(dateBuf.toString());
                curLabelClosePos = 0;
            }
        } else {
            if (curLabelClosePos != 0) curLabelClosePos = ch == LABELCLOSE_FILTER[0] ? 1 : 0;
        }
        
        if (ch == COUNTRYBALL_FILTER[curCountryBallPos]) {
            ++curCountryBallPos;
            if (curCountryBallPos == COUNTRYBALL_FILTER.length) {
                parseCountryBall(readUntilSequence(">".toCharArray()));
                curCountryBallPos = 0;
            }
        } else {
            if (curCountryBallPos != 0) curCountryBallPos = ch == COUNTRYBALL_FILTER[0] ? 1 : 0;
        }
    }
    
    private void parseCountryBall(String countryBall) {
        Matcher countryBallMatcher = COUNTRYBALL_PATTERN.matcher(countryBall);
        if (countryBallMatcher.find()) {
            BadgeIconModel badge = new BadgeIconModel();
            badge.source = countryBallMatcher.group(1);
            if (badge.source.endsWith(".png") && badge.source.indexOf('/') != -1)
                badge.description = badge.source.substring(badge.source.lastIndexOf('/') + 1, badge.source.length() - 4);
            currentPost.icons = new BadgeIconModel[] { badge };
        }
    }

    @Override
    protected void parseDate(String date) {} //turn off default implementation from WakabaReader
    
    private void parseDvachDate(String date) {
        date = date.substring(0, date.length() - 8).trim();
        Matcher dateMatcher = DATE_PATTERN.matcher(date);
        if (dateMatcher.find()) {
            try {
                currentPost.timestamp = DATE_FORMAT.parse(dateMatcher.group(1)).getTime();
            } catch (ParseException e) {}
        }
    }
    
    @Override
    protected void postprocessPost(PostModel post) {
        post.comment = post.comment.replaceAll("<span class=\"red italic\">(.*?)</span>", "<font color=\"red\"><em>$1</em></font>");
        if (post.attachments != null)
            for (AttachmentModel attachment : post.attachments)
                attachment.originalName = null;
    }
    
    @Override
    protected void parseAttachment(String html) {
        super.parseAttachment(html.replace("kbps", ""));
    }
    
    @Override
    public ThreadModel[] readWakabaPage() throws IOException {
        ThreadModel[] result = super.readWakabaPage();
        if (result != null)
            for (ThreadModel model : result)
                model.attachmentsCount = -1;
        return result;
    }
}
