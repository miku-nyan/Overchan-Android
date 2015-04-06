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

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.ui.FavoritesFragment;
import nya.miku.wishmaster.ui.HistoryFragment;
import nya.miku.wishmaster.ui.NewTabFragment;
import nya.miku.wishmaster.ui.presentation.BoardFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

/**
 * Переключение вкладок (фрагментов)
 * @author miku-nyan
 *
 */
public class TabsSwitcher {
    /** текущий ID или виртуальная позиция скрытой вкладки */
    public Long currentId = null;
    public Fragment currentFragment;
    
    /**
     * Переключиться на вкладку (обычную) tabModel
     * @param tabModel вкладка
     * @param fragmentManager менеджер фрагментов
     */
    public void switchTo(TabModel tabModel, FragmentManager fragmentManager) {
        if (currentId != null && currentId.equals(Long.valueOf(tabModel.id))) {
            if (tabModel.forceUpdate && currentFragment != null && currentFragment instanceof BoardFragment) {
                ((BoardFragment) currentFragment).update();
            }
            return;
        }
        currentFragment = BoardFragment.newInstance(tabModel.id);
        currentId = tabModel.id;
        replace(fragmentManager, currentFragment);
    }
    
    /**
     * Переключиться на скрытую (такие как "Новая вкладка", "Избранное", "История") вкладку
     * @param virtualPosition виртуальная позиция вкладки
     * (см. {@link TabModel#POSITION_NEWTAB}, {@link TabModel#POSITION_FAVORITES}, {@link TabModel#POSITION_HISTORY})
     * @param fragmentManager менеджер фрагментов
     */
    public void switchTo(int virtualPosition, FragmentManager fragmentManager) {
        if (currentId != null && currentId.equals(Long.valueOf(virtualPosition))) return;
        Fragment newFragment = null;
        switch (virtualPosition) {
            case TabModel.POSITION_NEWTAB:
                newFragment = new NewTabFragment();
                break;
            case TabModel.POSITION_HISTORY:
                newFragment = new HistoryFragment();
                break;
            case TabModel.POSITION_FAVORITES:
                newFragment = new FavoritesFragment();
                break;
            default:
                newFragment = new NewTabFragment();
        }
        currentFragment = newFragment;
        currentId = (long) virtualPosition;
        replace(fragmentManager, newFragment);
    }
    
    private void replace(FragmentManager fragmentManager, Fragment newFragment) {
        fragmentManager.beginTransaction().setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right).
                replace(R.id.main_fragment_container, newFragment).commit();
    }
}
