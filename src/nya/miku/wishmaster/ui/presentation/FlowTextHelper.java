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

import android.graphics.Point;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.view.View;
import android.widget.TextView;

/**
 * Обтеканием текстом картинки. Методы работают на Android >= 2.2, на более ранних версиях просто ничего не делается.
 * @author miku-nyan
 *
 */
public class FlowTextHelper {
    public static final boolean IS_AVAILABLE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO;
    
    private static boolean flowText(SpannableStringBuilder string, int width, int height, int textFullWidth, TextPaint textPaint) {
        if (IS_AVAILABLE && textFullWidth > width) {
            return FlowTextHelperImpl.flowText(string, width, height, textFullWidth, textPaint);
        }
        return false;
    }
    
    /**
     * Установить обтекание для Spanned текста (SpannableStringBuilder)
     * @param string текст
     * @param floatingModel модель обтекания текстом
     */
    public static boolean flowText(SpannableStringBuilder string, FloatingModel floatingModel) {
        return flowText(string, floatingModel.width, floatingModel.height, floatingModel.textFullWidth, floatingModel.textPaint);
    }
    
    /**
     * Установить обтекание для Spanned текста (SpannableStringBuilder) для вывода на TextView с шириной отличной от указанной (измеренной) в floatingModel
     * @param string текст
     * @param floatingModel модель обтекания текстом
     * @param textFullWidth ширина TextView
     */
    public static boolean flowText(SpannableStringBuilder string, FloatingModel floatingModel, int textFullWidth) {
        return flowText(string, floatingModel.width, floatingModel.height, textFullWidth, floatingModel.textPaint);
    }
    
    /**
     * Установить положение разметки для обтекания
     * @param thumbnailView обтекаемый объект
     * @param messageView обтекающий текст
     */
    public static void setFloatLayoutPosition(View thumbnailView, TextView messageView) {
        if (IS_AVAILABLE) {
            FlowTextHelperImpl.setFloatLayoutPosition(thumbnailView, messageView);
        }
    }
    
    /**
     * Установить положение разметки по умолчанию
     * @param thumbnailView обтекаемый объект
     * @param messageView обтекающий текст
     */
    public static void setDefaultLayoutPosition(View thumbnailView, TextView messageView) {
        if (IS_AVAILABLE) {
            FlowTextHelperImpl.setDefaultLayoutPosition(thumbnailView, messageView);
        }
    }
    
    /**
     * Класс модели обтекания текстом
     * @author miku-nyan
     *
     */
    public static class FloatingModel {
        private final int width;
        private final int height;
        private final int textFullWidth;
        private final TextPaint textPaint;
        
        /**
         * Конструктор модели обтекания текстом
         * @param width ширина обтекаемого объекта
         * @param height высота обтекаемого объекта
         * @param textFullWidth ширина textview
         * @param textPaint кисть (объект TextPaint) textview
         */
        public FloatingModel(int width, int height, int textFullWidth, TextPaint textPaint) {
            this.width = width;
            this.height = height;
            this.textFullWidth = textFullWidth;
            this.textPaint = textPaint;
        }
        
        /**
         * Конструктор модели обтекания текстом
         * @param thumbnailViewSize размер обтекаемого объекта (картинки-миниатюры), со всеми отступами
         * @param textFullWidth ширина textview
         * @param textPaint кисть (объект TextPaint) TextView
         */
        public FloatingModel(Point thumbnailViewSize, int textFullWidth, TextPaint textPaint) {
            this.width = thumbnailViewSize.x;
            this.height = thumbnailViewSize.y;
            this.textFullWidth = textFullWidth;
            this.textPaint = textPaint;
        }
        
        @Override
        public boolean equals(Object o) {
            if (o instanceof FloatingModel) {
                FloatingModel f = (FloatingModel) o;
                return (f.width == width && f.height == height && f.textFullWidth == textFullWidth);
            }
            return false;
        }
    }
    
    /**
     * Получить позицию в spanned-строке, до которой установлено обтекание
     * @param spanned
     * @return позиция или -1, если обтекания нет
     */
    public static int getFloatingPosition(Spanned spanned) {
        if (IS_AVAILABLE) {
            return FlowTextHelperImpl.getFloatingPosition(spanned);
        }
        return -1;
    }
}
