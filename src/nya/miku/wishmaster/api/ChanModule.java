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

package nya.miku.wishmaster.api;

import java.io.OutputStream;

import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;

import android.graphics.drawable.Drawable;
import android.preference.PreferenceGroup;

/**
 * Интерфейс модуля, осуществляющего все взаимодействия с чаном (имиджбордой)
 * @author miku-nyan
 *
 */

public interface ChanModule {
    
    /**
     * Получить (внутреннее) название имиджборды, напр. "2ch.hk"
     */
    String getChanName();
    
    /**
     * Получить отображаемое (пользователю) название имиджборды, напр. "Два.ч (.hk)"
     */
    String getDisplayingName();
    
    /**
     * Получить значок имиджборды (объект Drawable)
     */
    Drawable getChanFavicon();
    
    /**
     * Добавить параметры (отдельные для этой имиджборды), на экран настроек
     * @param preferenceGroup экран настроек
     */
    void addPreferencesOnScreen(PreferenceGroup preferenceGroup);
    
    /**
     * Функция должна вернуть пароль для удаления по умолчанию (если на чане есть доски, где можно удалять посты/файлы, иначе можно вернуть null).
     * В этом случае этот пароль должен настраиваться в параметрах (среди параметров, добавляемых {@link #addPreferencesOnScreen(PreferenceGroup)}).
     * При первом запуске (когда параметр не настроен), пароль необходимо сгенерировать случайно и сохранить в параметры. 
     */
    String getDefaultPassword();
    
    /**
     * Получить (или обновить) список досок чана (в виде упрощённых моделей {@link SimpleBoardModel})
     * @param listener интерфейс отслеживания прогресса (может принимать null)
     * @param task интерфейс отменяемой задачи
     * @param oldBoardsList старый список досок (может принимать null)
     * @return список упрощённых моделей досок
     */
    SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception;
    
    /**
     * Получить полную модель конкретной доски 
     * @param shortName название доски (напр, "b", "int")
     * @param listener интерфейс отслеживания прогресса (может принимать null)
     * @param task интерфейс отменяемой задачи
     * @return полная модель доски
     */
    BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception;
    
    /**
     * Получить (или обновить) список тредов с заданной страницы
     * @param boardName название доски (напр, "b", "int")
     * @param page страница
     * @param listener интерфейс отслеживания прогресса (может принимать null)
     * @param task интерфейс отменяемой задачи
     * @param oldList старый список тредов (может принимать null). Объекты из этого списка не должны быть изменены
     * @return список (массив) тредов страницы
     */
    ThreadModel[] getThreadsList(String boardName, int page, ProgressListener listener, CancellableTask task, ThreadModel[] oldList) throws Exception;
    
    /**
     * Получить (или обновить) список тредов каталога.<br>
     * Если на данной имиджборде нет досок, предоставляющих функцию каталога, может бросать {@link UnsupportedOperationException}.
     * @param boardName название доски (напр, "b", "int")
     * @param catalogType тип каталога (индекс в {@link BoardModel#catalogTypeDescriptions})
     * @param listener интерфейс отслеживания прогресса (может принимать null)
     * @param task интерфейс отменяемой задачи
     * @param oldList старый список тредов (может принимать null). Объекты из этого списка не должны быть изменены
     * @return список (массив) тредов каталога
     */
    ThreadModel[] getCatalog(String boardName, int catalogType, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception;
    
    /**
     * Получить (или обновить) список постов заданного треда.<br>
     * В случае, если список каждый раз загружается с нуля (а не только последние посты), рекомендуется использовать метод
     * {@link ChanModels#mergePostsLists(java.util.List, java.util.List)} для объединения старого и нового списков (если старый список не равен null),
     * таким образом не будут потеряны удалённые посты (которые присутствовали в старом списке, но отсутствуют в новом),
     * к ним лишь будет добавлена отметка о том, что сообщение удалено.
     * @param boardName название доски (напр, "b", "int")
     * @param threadNumber номер треда
     * @param listener интерфейс отслеживания прогресса (может принимать null)
     * @param task интерфейс отменяемой задачи
     * @param oldList старый список постов (может принимать null).
     * Объекты из этого списка не должны быть изменены (исключение: поле {@link PostModel#deleted})
     * @return список (массив) постов треда
     */
    PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception;
    
    /**
     * Поиск по доске.<br>
     * Если на данной имиджборде нет досок, предоставляющих функцию поиска, может бросать {@link UnsupportedOperationException}.
     * @param boardName название доски (напр, "b", "int")
     * @param searchRequest модель поискового запроса
     * @param listener интерфейс отслеживания прогресса (может принимать null)
     * @param task интерфейс отменяемой задачи
     * @return список постов, удовлетворяющих поисковому запросу
     */
    PostModel[] search(String boardName, String searchRequest, ProgressListener listener, CancellableTask task) throws Exception;

    /**
     * Поиск по доске.<br>
     * Если на данной имиджборде нет досок, предоставляющих функцию поиска, может бросать {@link UnsupportedOperationException}.
     * @param boardName название доски (напр, "b", "int")
     * @param searchRequest модель поискового запроса
     * @param page страница
     * @param listener интерфейс отслеживания прогресса (может принимать null)
     * @param task интерфейс отменяемой задачи
     * @return список постов, удовлетворяющих поисковому запросу
     */
    PostModel[] search(String boardName, String searchRequest,  int page, ProgressListener listener, CancellableTask task) throws Exception;

    /**
     * Получить новую капчу для отправки поста (в виде модели {@link CaptchaModel}).<br>
     * Если на данной имиджборде все доски Read-Only ({@link BoardModel#readonlyBoard}), может бросать {@link UnsupportedOperationException}.
     * @param boardName название доски (напр, "b", "int")
     * @param threadNumber номер треда или null, если создаётся новый тред
     * @param listener интерфейс отслеживания прогресса (может принимать null)
     * @param task интерфейс отменяемой задачи
     * @return модель капчи или null, если капча не требуется
     */
    CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception;
    
    /**
     * Отправить пост.<br>
     * Если на данной имиджборде все доски Read-Only ({@link BoardModel#readonlyBoard}), может бросать {@link UnsupportedOperationException}.
     * @param model модель отправки поста
     * @param listener интерфейс отслеживания прогресса (может принимать null)
     * @param task интерфейс отменяемой задачи
     * @return в случае успеха должен вернуть null или URL страницы пренаправления
     */
    String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception;
    
    /**
     * Удалить пост или прикреплённые к посту файлы.<br>
     * Если на данной имиджборде ни на одной доске нельзя удалять свои посты или файлы, может бросать {@link UnsupportedOperationException}.
     * @param model модель удаления поста
     * @param listener интерфейс отслеживания прогресса (может принимать null)
     * @param task интерфейс отменяемой задачи
     * @return в случае успеха должен вернуть null или URL страницы пренаправления
     */
    String deletePost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception;
    
    /**
     * Пожаловаться на сообщение модератору.<br>
     * Если на данной имиджборде ни на одной доске эта функция не поддерживается, может бросать {@link UnsupportedOperationException}.
     * @param model модель жалобы модератору
     * @param listener интерфейс отслеживания прогресса (может принимать null)
     * @param task интерфейс отменяемой задачи
     * @return в случае успеха должен вернуть null или URL страницы пренаправления
     */
    String reportPost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception;
    
    /**
     * Скачать файл с данного чана в поток
     * @param url абсолютный или относительный (на данной имиджборде) URL адрес
     * @param out поток для записи результата
     * @param listener интерфейс отслеживания прогресса (может принимать null)
     * @param task интерфейс отменяемой задачи
     */
    void downloadFile(String url, OutputStream out, ProgressListener listener, CancellableTask task) throws Exception;
    
    /**
     * Создать строку URL адреса из объекта модели.<br>
     * Полученный URL не обязательно должен относиться к этой же имиджборде (если какая-либо доска этого чана является ссылкой на другой чан).<br>
     * Также необходимо учитывать, что при передаче в этот метод значение номера страницы ({@link UrlPageModel#boardPage}) может равняться
     * {@link UrlPageModel#DEFAULT_FIRST_PAGE}. В этом случае необходимо подставить на это место номер первой страницы на этом доске.
     * @param model модель адреса
     * @return строка URL
     * @throws IllegalArgumentException в случае, если модель адреса некорректна, не относится к данной имиджборде,
     * или не существует доступного URL для данной страницы (например, в случае, если доступ осуществляется POST-запросом).<br>
     * При этом, для моделей адресов типов {@link UrlPageModel#TYPE_INDEXPAGE} и {@link UrlPageModel#TYPE_BOARDPAGE},
     * если модель корректна и принадлежит данной имиджборде, URL должен существовать обязательно!
     */
    String buildUrl(UrlPageModel model) throws IllegalArgumentException;
    
    /**
     * Создать модель из строки URL адреса (распарсить URL). Адрес обязательно должен относиться к данной имиджборде.
     * @param url строка с адресом
     * @return модель адреса
     * @throws IllegalArgumentException в случае, если URL некорректный или не принадлежит имиджборде
     */
    UrlPageModel parseUrl(String url) throws IllegalArgumentException;
    
    /**
     * Получить абсолютный URL-адрес с данной имиджборды
     * @param url абсолютный или относительный (на данной имиджборде) адрес
     * @return абсолютный адрес
     */
    String fixRelativeUrl(String url);
    
}
