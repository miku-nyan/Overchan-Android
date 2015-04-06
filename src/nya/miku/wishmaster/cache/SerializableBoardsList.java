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

package nya.miku.wishmaster.cache;

import java.io.Serializable;

import com.esotericsoftware.kryo.serializers.TaggedFieldSerializer.Tag;

import nya.miku.wishmaster.api.models.SimpleBoardModel;

/**
 * Сериализуемая модель списка досок
 * @author miku-nyan
 *
 */
public class SerializableBoardsList implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /** Название имиджборды (модуля) */
    @Tag(0) public String chanName;
    /** Массив упрощённых моделей досок */
    @Tag(1) public SimpleBoardModel[] boards;
}
