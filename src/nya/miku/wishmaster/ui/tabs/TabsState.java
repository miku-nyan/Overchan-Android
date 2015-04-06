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

package nya.miku.wishmaster.ui.tabs;

import java.io.Serializable;
import java.util.ArrayList;

import com.esotericsoftware.kryo.serializers.TaggedFieldSerializer.Tag;

/**
 * Объект, хранящий всю информацию о состоянии вкладок (список вкладок, стек и позицию)
 * @author miku-nyan
 *
 */
public class TabsState implements Serializable {
    private static final long serialVersionUID = 1L;
    
    @Tag(0) public ArrayList<TabModel> tabsArray;
    @Tag(1) public TabsIdStack tabsIdStack;
    @Tag(2) public int position;
    
    /**
     * Получить объект с информацией о состоянии вкладок по умолчанию
     * @return
     */
    public static TabsState obtainDefault() {
        TabsState state = new TabsState();
        state.tabsIdStack = new TabsIdStack();
        state.tabsArray = new ArrayList<TabModel>();
        state.position = TabModel.POSITION_NEWTAB;
        return state;
    }
    
    /**
     * Найти вкладку с заданным id
     * @param id идентификатор вкладки
     * @return модель вкладки или null если вкладка отсутствует
     */
    public TabModel findTabById(long id) {
        for (TabModel model : tabsArray) {
            if (model.id == id) {
                return model;
            }
        }
        return null;
    }
}
