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

package nya.miku.wishmaster.ui.theme;

import nya.miku.wishmaster.R;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.content.res.ResourcesCompat;
import android.util.TypedValue;

public class ThemeUtils {
    
    private ThemeUtils() {}
    
    private static void resolveAttribute(Theme theme, int attrId, TypedValue outValue, boolean resolveRefs) {
        if (CustomThemeHelper.resolveAttribute(attrId, outValue)) return;
        if (!theme.resolveAttribute(attrId, outValue, resolveRefs)) outValue.type = TypedValue.TYPE_NULL;
    }
    
    private static int getThemeColor(TypedValue tmp, Theme theme, int attrId, int defaultValue) {
        if (tmp == null) tmp = new TypedValue();
        resolveAttribute(theme, attrId, tmp, true);
        if (tmp.type >= TypedValue.TYPE_FIRST_COLOR_INT && tmp.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return tmp.data;
        } else {
            return defaultValue;
        }
    }
    
    /**
     * Получить значение атрибута в теме (создаётся объект TypedValue)
     * @param theme тема
     * @param attrId id атрибута (R.attr.[...])
     * @param resolveRefs если true, ссылки (ресурсы) будут разрешены; если false, значение может быть типа TYPE_REFERENCE.
     * В любом случае оно не будет типа TYPE_ATTRIBUTE.
     * @return объект TypedValue
     */
    public static TypedValue resolveAttribute(Theme theme, int attrId, boolean resolveRefs) {
        TypedValue typedValue = new TypedValue();
        resolveAttribute(theme, attrId, typedValue, resolveRefs);
        return typedValue;
    }
    
    /**
     * Получить ID ресурса для данной темы из аттрибута
     * @param theme тема
     * @param attrId id атрибута (R.attr.[...])
     * @return ID ресурса
     */
    public static int getThemeResId(Theme theme, int attrId) {
        return resolveAttribute(theme, attrId, true).resourceId;
    }
    
    /**
     * Получить цвет данной темы из атрибута
     * @param theme тема
     * @param attrId id атрибута (R.attr.[...])
     * @param defaultValue значение по умолчанию, если получить не удалось
     * @return цвет в виде int
     */
    public static int getThemeColor(Theme theme, int attrId, int defaultValue) {
        return getThemeColor(null, theme, attrId, defaultValue);
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
                int color = getThemeColor(theme, android.R.attr.textColorPrimary, Color.TRANSPARENT);
                if (color != Color.TRANSPARENT) {
                    drawable.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
                }
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
        public final int subscriptionBackground;
        public final int quoteForeground;
        public final int spoilerForeground;
        public final int spoilerBackground;
        public final int urlLinkForeground;
        public final int refererForeground;
        public final int subjectForeground;
        
        private ThemeColors(int indexForeground, int indexBumpLimit, int numberForeground, int nameForeground, int opForeground, int sageForeground,
                int tripForeground, int subscriptionBackground, int quoteForeground, int spoilerForeground, int spoilerBackground,
                int urlLinkForeground, int refererForeground, int subjectForeground) {
            this.indexForeground = indexForeground;
            this.indexOverBumpLimit = indexBumpLimit;
            this.numberForeground = numberForeground;
            this.nameForeground = nameForeground;
            this.opForeground = opForeground;
            this.sageForeground = sageForeground;
            this.tripForeground = tripForeground;
            this.subscriptionBackground = subscriptionBackground;
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
                TypedValue tmp = new TypedValue();
                int indexColor = ThemeUtils.getThemeColor(tmp, theme, R.attr.postIndexForeground, Color.parseColor("#4F7942"));
                int overBumpLimitColor = ThemeUtils.getThemeColor(tmp, theme, R.attr.postIndexOverBumpLimit, Color.parseColor("#C41E3A"));
                int numberColor = ThemeUtils.getThemeColor(tmp, theme, R.attr.postNumberForeground, Color.BLACK);
                int nameColor = ThemeUtils.getThemeColor(tmp, theme, R.attr.postNameForeground, Color.BLACK);
                int opColor = ThemeUtils.getThemeColor(tmp, theme, R.attr.postOpForeground, Color.parseColor("#008000"));
                int sageColor = ThemeUtils.getThemeColor(tmp, theme, R.attr.postSageForeground, Color.parseColor("#993333"));
                int tripColor = ThemeUtils.getThemeColor(tmp, theme, R.attr.postTripForeground, Color.parseColor("#228854"));
                int subscriptionBackground = Color.LTGRAY; //TODO
                int quoteForeground = ThemeUtils.getThemeColor(tmp, theme, R.attr.postQuoteForeground, Color.parseColor("#789922"));
                int spoilerForeground = ThemeUtils.getThemeColor(tmp, theme, R.attr.spoilerForeground, Color.BLACK);
                int spoilerBackground = ThemeUtils.getThemeColor(tmp, theme, R.attr.spoilerBackground, Color.parseColor("#BBBBBB"));
                int urlLinkForeground = ThemeUtils.getThemeColor(tmp, theme, R.attr.urlLinkForeground, Color.parseColor("#0000EE"));
                int refererForeground = ThemeUtils.getThemeColor(tmp, theme, R.attr.refererForeground, Color.parseColor("#FF0000"));
                int subjectForeground = ThemeUtils.getThemeColor(tmp, theme, R.attr.postTitleForeground, Color.BLACK);
                instance = new ThemeColors(
                        indexColor,
                        overBumpLimitColor,
                        numberColor,
                        nameColor,
                        opColor,
                        sageColor,
                        tripColor,
                        subscriptionBackground,
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
