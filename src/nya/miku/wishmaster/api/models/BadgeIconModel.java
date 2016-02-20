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
 * Модель иконки бэйджа (флаг страны, значок политических предпочтений и т.д.)
 * @author miku-nyan
 *
 */
public class BadgeIconModel implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * Путь (абсолютный или относительный на имиджборде) к картинке значка.
     */
    @Tag(0) public String source;
    /**
     * Описание иконки. Может принимать null.
     */
    @Tag(1) public String description;
}
