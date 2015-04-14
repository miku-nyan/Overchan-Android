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

import android.annotation.SuppressLint;

import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

@SuppressLint("SimpleDateFormat")
public class DateFormats {
    static final DateFormat IICHAN_DATE_FORMAT;
    static final DateFormat RU_410_DATE_FORMAT;
    static final DateFormat INT_410_DATE_FORMAT;
    static final DateFormat MIKUBA_DATE_FORMAT;
    static final DateFormat NOWERE_DATE_FORMAT;
    
    static {
        DateFormatSymbols iichanSymbols = new DateFormatSymbols();
        iichanSymbols.setShortWeekdays(new String[] { "", "Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб" });
        iichanSymbols.setMonths(new String[] {
                    "января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря" });
        IICHAN_DATE_FORMAT = new SimpleDateFormat("EEE dd MMMM yyyy HH:mm:ss", iichanSymbols);
        IICHAN_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
        
        DateFormatSymbols ru410Symbols = new DateFormatSymbols();
        ru410Symbols.setShortWeekdays(new String[] { "", "Пнд", "Втр", "Срд", "Чтв", "Птн", "Сбт", "Вск" });
        RU_410_DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy (EEE) HH:mm:ss", ru410Symbols);
        RU_410_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
        
        DateFormatSymbols int410Symbols = new DateFormatSymbols();
        int410Symbols.setShortWeekdays(new String[] { "", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" });
        INT_410_DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy (EEE) HH:mm:ss", int410Symbols);
        INT_410_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
        
        MIKUBA_DATE_FORMAT = new SimpleDateFormat("EEE dd MMM yyyy HH:mm:ss", Locale.US);
        MIKUBA_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
        
        NOWERE_DATE_FORMAT = new SimpleDateFormat("yy/MM/dd(EEE)HH:mm", Locale.US);
        NOWERE_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
    }
}
