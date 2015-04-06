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
 * Упрощённая модель доски (только код, название, категория и является ли доска NSFW)
 * @author miku-nyan
 *
 */
public class SimpleBoardModel implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /** Название имиджборды (то, что возвращает {@link ChanModule#getChanName()} модуль данной имиджборды). */
    @Tag(0) public String chan;
    /** Код доски (короткое название), например: "b", "s", "int". */
    @Tag(1) public String boardName;
    /** Описание доски (полное название), например: "Бред", "Программы", "International". */
    @Tag(2) public String boardDescription;
    /** Категория, к которой относится доска (в случае деления досок по категориям). Может принимать null. */
    @Tag(3) public String boardCategory;
    /** Должно принимать true, если на доске содержится контент 18+ или доска является немодерируемой. */
    @Tag(4) public boolean nsfw;

    public SimpleBoardModel() {}
    public SimpleBoardModel(BoardModel model) {
        this.chan = model.chan;
        this.boardName = model.boardName;
        this.boardDescription = model.boardDescription;
        this.boardCategory = model.boardCategory;
        this.nsfw = model.nsfw;
    }
}
