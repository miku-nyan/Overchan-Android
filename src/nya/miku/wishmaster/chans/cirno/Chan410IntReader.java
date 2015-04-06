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

package nya.miku.wishmaster.chans.cirno;

import java.io.IOException;
import java.io.InputStream;

import nya.miku.wishmaster.api.models.BadgeIconModel;

/**
 * Парсер страниц борды 410chan.org/int, обрабатывается значок-флаг страны.
 * @author miku-nyan
 *
 */
public class Chan410IntReader extends Chan410Reader {
    
    private static final char[] COUNTRY_ICON_FILTER = "<span title=\"".toCharArray();
    private int curPos = 0;
    
    public Chan410IntReader(InputStream in) {
        super(in, DateFormats.INT_410_DATE_FORMAT);
    }
    
    @Override
    protected void customFilters(int ch) throws IOException {
        if (ch == COUNTRY_ICON_FILTER[curPos]) {
            ++curPos;
            if (curPos == COUNTRY_ICON_FILTER.length) {
                BadgeIconModel iconModel = new BadgeIconModel();
                iconModel.description = readUntilSequence("\"".toCharArray());
                String htmlIcon = readUntilSequence("</span>".toCharArray());
                int start, end;
                if ((start = htmlIcon.indexOf("src=\"")) != -1 && (end = htmlIcon.indexOf('\"', start + 5)) != -1) {
                    iconModel.source = htmlIcon.substring(start + 5, end);
                }
                int currentIconsCount = currentPost.icons == null ? 0 : currentPost.icons.length;
                BadgeIconModel[] newIconsArray = new BadgeIconModel[currentIconsCount + 1];
                for (int i=0; i<currentIconsCount; ++i) newIconsArray[i] = currentPost.icons[i];
                newIconsArray[currentIconsCount] = iconModel;
                currentPost.icons = newIconsArray;
                
                curPos = 0;
            }
        } else {
            if (curPos != 0) curPos = ch == COUNTRY_ICON_FILTER[0] ? 1 : 0;
        }
    }
}
