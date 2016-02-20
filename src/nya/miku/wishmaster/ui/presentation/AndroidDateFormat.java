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

package nya.miku.wishmaster.ui.presentation;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import nya.miku.wishmaster.common.MainApplication;

public class AndroidDateFormat {
    
    private static String pattern = null;
    
    public static String getPattern() {
        return pattern;
    }
    
    public static void initPattern() {
        if (pattern != null) return;
        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainApplication.getInstance());
        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(MainApplication.getInstance());
        if (dateFormat instanceof SimpleDateFormat && timeFormat instanceof SimpleDateFormat) {
            pattern = ((SimpleDateFormat) dateFormat).toPattern() + " " + ((SimpleDateFormat) timeFormat).toPattern();
        }
    }
}
