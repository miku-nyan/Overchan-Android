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

package nya.miku.wishmaster.api.models;

import java.io.Serializable;

import nya.miku.wishmaster.api.ChanModule;

import com.esotericsoftware.kryo.serializers.TaggedFieldSerializer.Tag;

/**
 * Модель удаления поста, прикреплённых файлов к посту или жалобы модератору
 * @author miku-nyan
 *
 */
public class DeletePostModel implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /** Название имиджборды (то, что возвращает {@link ChanModule#getChanName()} модуль данной имиджборды). */
    @Tag(0) public String chanName;
    /** Название доски (код доски), например: "b", "int". */
    @Tag(1) public String boardName;
    /** Номер треда, к которому относится сообщение */
    @Tag(2) public String threadNumber;
    /** Номер поста */
    @Tag(3) public String postNumber;
    /** Пароль для удаления поста (только при удалении) */
    @Tag(4) public String password;
    /** Должно принимать true, если удаляются только прикреплённые файлы, или false, если удаляется весь пост. (только при удалении) */
    @Tag(5) public boolean onlyFiles;
    /** Дополнительный комментарий для модератора (только при жалобе) */
    @Tag(6) public String reportReason;
}
