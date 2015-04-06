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

package nya.miku.wishmaster.api.models;

import android.graphics.Bitmap;

/**
 * Модель капчи
 * @author miku-nyan
 *
 */
public class CaptchaModel {
    /**
     * Тип капчи, одно из константных значений: {@link #TYPE_NORMAL}, {@link #TYPE_NORMAL_DIGITS}
     */
    public int type;
    /**
     * Картинка с капчей, объект Bitmap
     */
    public Bitmap bitmap;
    
    /**
     * Константное значение для обозначения типа капчи - обычная капча (допустимы все символы)
     */
    public static final int TYPE_NORMAL = 0;
    /**
     * Константное значение для обозначения типа капчи - числовая капча (допустимы только десятичные цифры)
     */
    public static final int TYPE_NORMAL_DIGITS = 1;
}
