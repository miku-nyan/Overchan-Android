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

package nya.miku.wishmaster.cache;

import java.io.Serializable;

import com.esotericsoftware.kryo.serializers.TaggedFieldSerializer.Tag;

import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;

/**
 * Сериализуемая модель страницы.
 * @author miku-nyan
 *
 */
public class SerializablePage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    @Tag(0) public UrlPageModel pageModel;
    @Tag(1) public BoardModel boardModel;
    
    //в зависимости от pageModel.type только одно из полей (posts и threads) содержит данные, другое null
    @Tag(2) public PostModel[] posts;
    @Tag(3) public ThreadModel[] threads;
    
}
