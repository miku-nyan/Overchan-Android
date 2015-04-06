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

import com.esotericsoftware.kryo.serializers.TaggedFieldSerializer.Tag;

import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;

/**
 * Модель вкладки
 * @author miku-nyan
 *
 */
public class TabModel implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final int TYPE_NORMAL = 0;
    public static final int TYPE_LOCAL = 1;
    
    /** тип вкладки */
    @Tag(0) public int type;
    /** уникальный идентификатор (id) вкладки */
    @Tag(1) public long id;
    
    /** заголовок вкладки */
    @Tag(2) public String title;
    /** модель адреса страницы */
    @Tag(3) public UrlPageModel pageModel;
    /** URL страницы для открытия в веб-браузере или null если не доступно */
    @Tag(4) public String webUrl;
    /** унакальный хэш страницы. Для локальных страниц - md5 хэш полного пути имени файла ({@link #localFilePath}),
     *  для обычных - хэш {@link pageModel} ({@link ChanModels#hashUrlPageModel(UrlPageModel)}) */
    @Tag(5) public String hash;
    /** элемент (номер поста, имя доски) на который требуется проскроллить сразу */
    @Tag(6) public String startItemNumber;
    /** дополнительно смещение относительно элемента, на который требуется проскроллить */
    @Tag(7) public int startItemTop = 0;
    /** позиция первого непрочитанного сообщения */
    @Tag(8) public int firstUnreadPosition = 0;
    /** требуется ли загружать из интернета при открытии, даже если она доступна в кэше */
    @Tag(9) public boolean forceUpdate = false;
    
    /** полный путь к zip-архиву с сохранённым тредом (только если type == TYPE_LOCAL) */
    @Tag(10) public String localFilePath;
    
    /** включено ли автообновление вкладки в фоне, если сервис активен (только при type == TYPE_NORMAL и для страниц-тредов) */
    @Tag(11) public boolean autoupdateBackground;
    /** количество непрочитанных сообщений после обновления */
    @Tag(12) public int unreadPostsCount;
    /** true, если автообновление завершилось ошибкой */
    @Tag(13) public boolean autoupdateError;
    
    // "Новая вкладка", "История", "Избранное" - скрытые вкладки,
    // не отображаются в панели, не имеют отдельных объектов TabModel.
    // Определяются виртуальной позицией - отрицательное число,
    // которое нужно передать адаптеру в setSelectedItem(int) 
    // вместо позиции вкладки в listView, чтобы переключиться на такую вкладку
    public static final int POSITION_NEWTAB = -1;
    public static final int POSITION_FAVORITES = -2;
    public static final int POSITION_HISTORY = -3;
}
