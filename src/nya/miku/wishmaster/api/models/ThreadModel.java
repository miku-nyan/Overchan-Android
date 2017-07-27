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

import com.esotericsoftware.kryo.serializers.TaggedFieldSerializer.Tag;

/**
 * Модель треда для предпросмотра в списке тредов
 * @author miku-nyan
 *
 */
public class ThreadModel implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /** Номер треда */
    @Tag(0) public String threadNumber;
    
    /** Количество постов в треде. Может принимать значение -1, если не удалось получить число, или имиджборда не предоставляет такую информацию. */
    @Tag(1) public int postsCount = -1;
    /** Общее число вложений в треде. Может принимать -1, если не удалось получить значение, или имиджборда не предоставляет такую информацию */
    @Tag(2) public int attachmentsCount = -1;
    
    /** Массив постов, сохранённых в этой модели (предоставляемых имиджбордой для предпросмотра треда).
     *  Нулевой элемент должен быть ОП-постом. */
    @Tag(3) public PostModel[] posts;
    
    /** Отметка о прикреплённом треде (должно принимать true, если тред закреплён). */
    @Tag(4) public boolean isSticky;
    /** Отметка о закрытом треде (должно принимать true, если обсуждение закрыто). */
    @Tag(5) public boolean isClosed;
    /** Отметка о цикличном треде (должно принимать true, если старые посты вытесняются новыми при превышении лимита). */
    @Tag(6) public boolean isCyclical;
    
}
