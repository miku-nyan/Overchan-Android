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

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.ui.HistoryFragment;
import nya.miku.wishmaster.ui.theme.ThemeUtils;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.support.v4.content.res.ResourcesCompat;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class TabsAdapter extends ArrayAdapter<TabModel> {
    
    private final LayoutInflater inflater;
    private final Context context;
    private final TabsState tabsState;
    private final TabsIdStack tabsIdStack;
    private final TabSelectListener selectListener;
    
    private int selectedItem;
    private int draggingItem = -1;
    
    private final View.OnClickListener onCloseClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            closeTab((Integer) v.getTag());
        }
    };
    
    /**
     * Конструктор адаптера.
     * После создания и привязки к объекту списка необходимо дополнительно установить позицию текущей вкладки ({@link #setSelectedItem(int)})
     * @param context контекст активности для получения темы (стиля) и инфлатера
     * @param tabsState объект состояния вкладок
     * @param selectListener интерфейс {@link TabSelectListener}, слушающий событие выбора (переключения) вкладки
     */
    public TabsAdapter(Context context, TabsState tabsState, TabSelectListener selectListener) {
        super(context, 0, tabsState.tabsArray);
        this.inflater = LayoutInflater.from(context);
        this.context = context;
        this.tabsState = tabsState;
        this.tabsIdStack = tabsState.tabsIdStack;
        this.selectListener = selectListener;
    }
    
    /**
     * Выбрать текущую вкладку (и переключиться на неё). Объект состояния вкладок будет сериализован
     * @param position позиция вкладки в списке
     */
    public void setSelectedItem(int position) {
        setSelectedItem(position, true, true);
    }
    
    /**
     * Выбрать текущую вкладку (и переключиться на неё)
     * @param position позиция вкладки в списке
     * @param serialize если true, сериализовать объект состояния вкладок
     */
    public void setSelectedItem(int position, boolean serialize) {
        setSelectedItem(position, serialize, true);
    }
    
    /**
     * Выбрать текущую вкладку (с возможностью переключения на неё)
     * @param position позиция вкладки в списке
     * @param serialize если true, сериализовать объект состояния вкладок
     * @param switchTo если true, переключиться на выбранную вкладку
     */
    public void setSelectedItem(int position, boolean serialize, boolean switchTo) {
        selectedItem = position;
        tabsState.position = position;
        if (position >= 0) {
            tabsIdStack.addTab(getItem(position).id);
        }
        notifyDataSetChanged(serialize);
        if (switchTo) selectListener.onTabSelected(position);
    }
    
    /**
     * Выбрать текущую вкладку (и переключиться на неё) с поиском по ID вкладки
     * @param id ID вкладки
     */
    public void setSelectedItemId(long id) {
        for (int i=0; i<getCount(); ++i) {
            if (getItem(i).id == id) {
                setSelectedItem(i);
                break;
            }
        }
    }
    
    /**
     * Установить или убрать маркер перемещения вкладки
     * @param position позиция вкладки в списке или -1, если необходимо убрать маркер перемещения
     */
    public void setDraggingItem(int position) {
        draggingItem = position;
        notifyDataSetChanged(false);
    }
    
    /**
     * @return Возвращает позицию текущей выбранной вкладки
     */
    public int getSelectedItem() {
        return selectedItem;
    }
    
    /**
     * @return Возвращает позицию текущей перемещаемой вкладки (с маркером перемещения)
     * или -1, если перемещение не активно в данный момент
     */
    public int getDraggingItem() {
        return draggingItem;
    }
    
    /**
     * Закрыть вкладку
     * @param position позиция вкладки в списке
     */
    public void closeTab(int position) {
        setDraggingItem(-1);
        if (position >= getCount()) return;
        HistoryFragment.setLastClosed(tabsState.tabsArray.get(position));
        tabsIdStack.removeTab(getItem(position).id);
        remove(getItem(position), false);
        if (position == selectedItem) {
            if (!tabsIdStack.isEmpty()) {
                setSelectedItemId(tabsIdStack.getCurrentTab());
            } else {
                if (getCount() == 0) {
                    setSelectedItem(TabModel.POSITION_NEWTAB);
                } else {
                    if (getCount() <= position) --position;
                    setSelectedItem(position); //serialize
                }
            }
        } else {
            if (position < selectedItem) --selectedItem;
            setSelectedItem(selectedItem, true, MainApplication.getInstance().settings.scrollToActiveTab()); //serialize
        }
    }
    
    /**
     * Метод для обработки нажатия клавиши "Назад"
     * @return
     */
    public boolean back() {
        if (selectedItem < 0) {
            if (!tabsIdStack.isEmpty()) {
                setSelectedItemId(tabsIdStack.getCurrentTab());
                return true;
            }
        } else {
            if (MainApplication.getInstance().settings.doNotCloseTabs()) {
                tabsIdStack.removeTab(getItem(selectedItem).id);
                if (tabsIdStack.isEmpty()) {
                    setSelectedItem(TabModel.POSITION_NEWTAB);
                } else {
                    setSelectedItemId(tabsIdStack.getCurrentTab());
                }
            } else {
                closeTab(selectedItem);
            }
            return true;
        }
        return false;
    }
    
    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        View view = convertView == null ? inflater.inflate(R.layout.sidebar_tabitem, parent, false) : convertView;
        View dragHandler = view.findViewById(R.id.tab_drag_handle);
        ImageView favIcon = (ImageView)view.findViewById(R.id.tab_favicon);
        TextView title = (TextView)view.findViewById(R.id.tab_text_view);
        ImageView closeBtn = (ImageView)view.findViewById(R.id.tab_close_button);
        
        dragHandler.getLayoutParams().width = position == draggingItem ? ViewGroup.LayoutParams.WRAP_CONTENT : 0;
        dragHandler.setLayoutParams(dragHandler.getLayoutParams());
                
        if (position == selectedItem) {
            TypedValue typedValue = ThemeUtils.resolveAttribute(context.getTheme(), R.attr.sidebarSelectedItem, true);
            if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                view.setBackgroundColor(typedValue.data);
            } else {
                view.setBackgroundResource(typedValue.resourceId); 
            }
        } else {
            view.setBackgroundColor(Color.TRANSPARENT);
        }
        
        TabModel model = this.getItem(position);
        
        switch (model.type) {
            case TabModel.TYPE_NORMAL:
            case TabModel.TYPE_LOCAL:
                closeBtn.setVisibility(View.VISIBLE);
                String titleText = model.title;
                if (model.unreadPostsCount > 0 || model.autoupdateError) {
                    StringBuilder titleStringBuilder = new StringBuilder();
                    if (model.unreadSubscriptions) titleStringBuilder.append("[*] ");
                    if (model.autoupdateError) titleStringBuilder.append("[X] ");
                    if (model.unreadPostsCount > 0) titleStringBuilder.append('[').append(model.unreadPostsCount).append("] ");
                    titleText = titleStringBuilder.append(titleText).toString();
                }
                title.setText(titleText);
                ChanModule chan = MainApplication.getInstance().getChanModule(model.pageModel.chanName);
                Drawable icon = chan != null ? chan.getChanFavicon() :
                    ResourcesCompat.getDrawable(context.getResources(), android.R.drawable.ic_delete, null);
                if (icon != null) {
                    if (model.type == TabModel.TYPE_LOCAL) {
                        Drawable[] layers = new Drawable[] {
                                icon, ResourcesCompat.getDrawable(context.getResources(), R.drawable.favicon_overlay_local, null) };
                        icon = new LayerDrawable(layers);
                    }
                    /* XXX
                    Была идея помечать вкладки на автообновлении дополнительным значком (небольшой overlay поверх favicon в левом верхнем углу),
                    чтобы сразу видеть в списке вкладок, какие будут обновлены, а где автообновление отключено (по умолчанию включено везде).
                    Но в таком виде это выглядит плохо, возможно, стоит запилить-поискать какую-нибудь лёгкую иконку, чтобы не загромождать интерфейс.
                    Или придумать другой способ отображать это, или вообще это всё не нужно.
                    
                    else if (model.type == TabModel.TYPE_NORMAL && model.pageModel != null &&
                            model.pageModel.type == UrlPageModel.TYPE_THREADPAGE && model.autoupdateBackground &&
                            MainApplication.getInstance().settings.isAutoupdateEnabled() &&
                            MainApplication.getInstance().settings.isAutoupdateBackground()) {
                        Drawable[] layers = new Drawable[] {
                                icon, ResourcesCompat.getDrawable(context.getResources(), R.drawable.favicon_overlay_autoupdate, null) };
                        icon = new LayerDrawable(layers);
                    }
                    */
                    favIcon.setImageDrawable(icon);
                    favIcon.setVisibility(View.VISIBLE);
                } else {
                    favIcon.setVisibility(View.GONE);
                }
                break;
            default:
                closeBtn.setVisibility(View.GONE);
                title.setText(R.string.error_deserialization);
                favIcon.setVisibility(View.GONE);
        }
        
        closeBtn.setTag(position);
        closeBtn.setOnClickListener(onCloseClick);
        return view;
    }
    
    @Override
    public void notifyDataSetChanged() {
        notifyDataSetChanged(true);
    }
    
    /**
     * @param serialize если true, сериализовать объект состояния вкладок
     */
    public void notifyDataSetChanged(boolean serialize) {
        super.notifyDataSetChanged();
        if (serialize) MainApplication.getInstance().serializer.serializeTabsState(tabsState);
    }
    
    @Override
    public void add(TabModel object) {
        add(object, true);
    }
    
    /**
     * Добавить объект в конец массива
     * @param object объект
     * @param serialize если true, сериализовать объект состояния вкладок
     */
    public void add(TabModel object, boolean serialize) {
        setNotifyOnChange(false);
        super.add(object);
        notifyDataSetChanged(serialize);
    }
    
    @Override
    public void remove(TabModel object) {
        remove(object, true);
    }
    
    /**
     * Удалить объект из массива
     * @param object объект
     * @param serialize если true, сериализовать объект состояния вкладок
     */
    public void remove(TabModel object, boolean serialize) {
        setNotifyOnChange(false);
        super.remove(object);
        notifyDataSetChanged(serialize);
    }
    
    @Override
    public void insert(TabModel object, int index) {
        insert(object, index, true);
    }
    
    /**
     * Вставить объект в массив на заданную позицию (индекс)
     * @param object объект
     * @param index индекс, на который объект должен быть вставлен
     * @param serialize если true, сериализовать объект состояния вкладок
     */
    public void insert(TabModel object, int index, boolean serialize) {
        setNotifyOnChange(false);
        super.insert(object, index);
        notifyDataSetChanged(serialize);
    }
    
    /**
     * Интерфейс, слушающий событие выбора (переключения) вкладки.
     * @author miku-nyan
     *
     */
    public static interface TabSelectListener {
        /** Вызывается при переключении вкладки.
         *  Переключение может быть на ту же самую (открытую в данный момент) вкладку, например, при изменении её позиции в списке. */
        public void onTabSelected(int position);
    }
}