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

package nya.miku.wishmaster.ui.presentation;

import nya.miku.wishmaster.R;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.content.res.ResourcesCompat;
import android.util.TypedValue;

public class ThemeUtils {
    
    private ThemeUtils() {}
    
    /**
     * Получить ID ресурса для данной темы из аттрибута
     * @param theme тема
     * @param attrId id атрибута (R.attr.[...])
     * @return ID ресурса
     */
    public static int getThemeResId(Theme theme, int attrId) {
        TypedValue typedValue = new TypedValue();
        theme.resolveAttribute(attrId, typedValue, true);
        return typedValue.resourceId;
    }
    
    /**
     * Получить цвет данной темы из атрибута
     * @param theme тема
     * @param styleableId id (R.styleable.[...])
     * @param defaultValue значение по умолчанию, если получить не удалось
     * @return цвет в виде int
     */
    public static int getThemeColor(Theme theme, int styleableId, int defaultValue) {
        TypedArray typedArray = theme.obtainStyledAttributes(R.styleable.Theme);
        int color = typedArray.getColor(styleableId, defaultValue);
        typedArray.recycle();
        return color;
    }
    
    /**
     * Получить значок (drawable) для Action Bar. В случае Android 5.0 и выше значок перекрашивается в цвет android:attr/textColorPrimary
     * @param theme тема
     * @param resources ресурсы
     * @param attrId id атрибута (R.attr.[...])
     * @return объект Drawable со значком, или null, если не удалось получить
     */
    public static Drawable getActionbarIcon(Theme theme, Resources resources, int attrId) {
        try {
            int id = getThemeResId(theme, attrId);
            Drawable drawable = ResourcesCompat.getDrawable(resources, id, theme);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                TypedValue typedValue = new TypedValue();
                theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true);
                if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT)
                    drawable.setColorFilter(typedValue.data, PorterDuff.Mode.SRC_ATOP);
            }
            return drawable;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Объект, содержащий цвета, нужные при программной обработке (парсинг html, создание заголовков постов)
     * @author miku-nyan
     *
     */
    public static class ThemeColors {
        private static ThemeColors instance = null;
        private static Theme currentTheme = null;
        
        public final int indexForeground;
        public final int indexOverBumpLimit;
        public final int numberForeground;
        public final int nameForeground;
        public final int opForeground;
        public final int sageForeground;
        public final int tripForeground;
        public final int quoteForeground;
        public final int spoilerForeground;
        public final int spoilerBackground;
        public final int urlLinkForeground;
        public final int refererForeground;
        public final int subjectForeground;
        
        private ThemeColors(int indexForeground, int indexBumpLimit, int numberForeground, int nameForeground, int opForeground, int sageForeground,
                int tripForeground, int quoteForeground, int spoilerForeground, int spoilerBackground, int urlLinkForeground, int refererForeground,
                int subjectForeground) {
            this.indexForeground = indexForeground;
            this.indexOverBumpLimit = indexBumpLimit;
            this.numberForeground = numberForeground;
            this.nameForeground = nameForeground;
            this.opForeground = opForeground;
            this.sageForeground = sageForeground;
            this.tripForeground = tripForeground;
            this.quoteForeground = quoteForeground;
            this.spoilerForeground = spoilerForeground;
            this.spoilerBackground = spoilerBackground;
            this.urlLinkForeground = urlLinkForeground;
            this.refererForeground = refererForeground;
            this.subjectForeground = subjectForeground;
        }
        
        /**
         * Получить экземпляр с цветами заданной темы
         * @param theme тема
         * @return объект ThemeColors
         */
        public static ThemeColors getInstance(Theme theme) {
            if (instance == null || currentTheme != theme) {
                currentTheme = theme;
                int indexColor = ThemeUtils.getThemeColor(theme, R.styleable.Theme_postIndexForeground, Color.parseColor("#4F7942"));
                int overBumpLimitColor = ThemeUtils.getThemeColor(theme, R.styleable.Theme_postIndexOverBumpLimit, Color.parseColor("#C41E3A"));
                int numberColor = ThemeUtils.getThemeColor(theme, R.styleable.Theme_postNumberForeground, Color.BLACK);
                int nameColor = ThemeUtils.getThemeColor(theme, R.styleable.Theme_postNameForeground, Color.BLACK);
                int opColor = ThemeUtils.getThemeColor(theme, R.styleable.Theme_postOpForeground, Color.parseColor("#008000"));
                int sageColor = ThemeUtils.getThemeColor(theme, R.styleable.Theme_postSageForeground, Color.parseColor("#993333"));
                int tripColor = ThemeUtils.getThemeColor(theme, R.styleable.Theme_postTripForeground, Color.parseColor("#228854"));
                int quoteForeground = ThemeUtils.getThemeColor(theme, R.styleable.Theme_postQuoteForeground, Color.parseColor("#789922"));
                int spoilerForeground = ThemeUtils.getThemeColor(theme, R.styleable.Theme_spoilerForeground, Color.BLACK);
                int spoilerBackground = ThemeUtils.getThemeColor(theme, R.styleable.Theme_spoilerBackground, Color.parseColor("#BBBBBB"));
                int urlLinkForeground = ThemeUtils.getThemeColor(theme, R.styleable.Theme_urlLinkForeground, Color.parseColor("#0000EE"));
                int refererForeground = ThemeUtils.getThemeColor(theme, R.styleable.Theme_refererForeground, Color.parseColor("#FF0000"));
                int subjectForeground = ThemeUtils.getThemeColor(theme, R.styleable.Theme_postTitleForeground, Color.BLACK);
                instance = new ThemeColors(
                        indexColor,
                        overBumpLimitColor,
                        numberColor,
                        nameColor,
                        opColor,
                        sageColor,
                        tripColor,
                        quoteForeground,
                        spoilerForeground,
                        spoilerBackground,
                        urlLinkForeground,
                        refererForeground,
                        subjectForeground);
            }
            return instance;
        }
    }
}
