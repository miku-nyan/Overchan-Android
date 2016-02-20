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

package nya.miku.wishmaster.ui.tabs;

import java.io.Serializable;
import java.util.LinkedList;

import com.esotericsoftware.kryo.serializers.TaggedFieldSerializer.Tag;

/**
 * Стек ID открытых вкладок
 * @author miku-nyan
 *
 */
public class TabsIdStack implements Serializable {
    private static final long serialVersionUID = 1L;
    @Tag(0) private LinkedList<Long> tabsList = new LinkedList<Long>(); 
    
    /** @return true, если стек пуст, false в противном случае */
    public boolean isEmpty() {
        return tabsList.isEmpty();
    }
    
    /** @return возвращает ID на вершине стека или -1, если стек пуст */
    public long getCurrentTab() {
        if (isEmpty()) {
            return -1;
        }
        return tabsList.getLast();
    }
    
    /** удаляет ID вкладки из стека (если такой ID отсутствует, ошибки не происходит) */
    public void removeTab(long id) {
        int index = tabsList.indexOf(id);
        if (index != -1) {
            tabsList.remove(index);
        }
    }
    
    /** добавляет или переносит вкладку с заданным ID на вершину стека */
    public void addTab(long id) {
        if (getCurrentTab() == id) return;
        removeTab(id);
        tabsList.add(id);
    }
}
