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

import java.io.Serializable;

import nya.miku.wishmaster.api.ChanModule;

import com.esotericsoftware.kryo.serializers.TaggedFieldSerializer.Tag;

/**
 * Модель адреса страницы имиджборды.<br>
 * Для конкретной страницы не обязательно должно существовать представление в виде URL-адреса (например, если параметры передаются POST запросом).
 * @author miku-nyan
 *
 */
public class UrlPageModel implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /** Тип страницы, одно из константных значений: {@link #TYPE_INDEXPAGE}, {@link #TYPE_BOARDPAGE},
     *  {@link #TYPE_CATALOGPAGE}, {@link #TYPE_THREADPAGE}, {@link #TYPE_SEARCHPAGE}, {@link #TYPE_OTHERPAGE} */
    @Tag(0) public int type;
    
    /** Название имиджборды (то, что возвращает {@link ChanModule#getChanName()} модуль данной имиджборды) */
    @Tag(1) public String chanName;
    /** Название доски (код доски), например: "b", "int".<br>
     *  Только в случае, если {@link #type} не равно {@link #TYPE_INDEXPAGE} или {@link #TYPE_OTHERPAGE}.*/
    @Tag(2) public String boardName;
    
    /** Номер страницы доски.<br>
     *  Только в случае, если {@link #type} равно {@link #TYPE_BOARDPAGE}.<br>
     *  Если модель передаётся в метод {@link ChanModule#buildUrl(UrlPageModel)} (и только в этом случае)
     *  может принимать значение {@link #DEFAULT_FIRST_PAGE}. */
    @Tag(3) public int boardPage;
    /** Тип отображения каталога, индекс в массиве {@link BoardModel#catalogTypeDescriptions}.<br>
     *  Только в случае, если {@link #type} равно {@link #TYPE_CATALOGPAGE}.*/
    @Tag(4) public int catalogType;
    /** Номер треда.<br>
     *  Только в случае, если {@link #type} равно {@link #TYPE_THREADPAGE}.*/
    @Tag(5) public String threadNumber;
    /** Номер поста, до которого требуется проскроллить страницу (якорь).<br>
     *  Только в случае, если {@link #type} равно {@link #TYPE_THREADPAGE}.<br>
     *  Может принимать значение null.*/
    @Tag(6) public String postNumber;
    /** Поисковой запрос по доске.<br>
     *  Только в случае, если {@link #type} равно {@link #TYPE_SEARCHPAGE}. */
    @Tag(7) public String searchRequest;
    /** Другой адрес на данной имиджборде, который не принадлежит ни одому из допустимых типов страниц.<br>
     *  Только в случае, если {@link #type} равно {@link #TYPE_OTHERPAGE}. */
    @Tag(8) public String otherPath;
    
    /** Константное значение для обозначения типа страницы - главная страница (список досок) */
    public static final int TYPE_INDEXPAGE = 0;
    /** Константное значение для обозначения типа страницы - страница доски (список тредов) */
    public static final int TYPE_BOARDPAGE = 1;
    /** Константное значение для обозначения типа страницы - каталог (список всех тредов доски) */
    public static final int TYPE_CATALOGPAGE = 2;
    /** Константное значение для обозначения типа страницы - страница треда */
    public static final int TYPE_THREADPAGE = 3;
    /** Константное значение для обозначения типа страницы - результаты поиска */
    public static final int TYPE_SEARCHPAGE = 4;
    /** Константное значение для обозначения типа страницы - ни один из допустимых типов */
    public static final int TYPE_OTHERPAGE = 5;
    
    /** Константное значение для обозначения номера первой страницы при передаче {@link ChanModule#buildUrl(UrlPageModel)} */
    public static final int DEFAULT_FIRST_PAGE = Integer.MIN_VALUE;
}
