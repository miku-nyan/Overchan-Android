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

package nya.miku.wishmaster.api.util;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.cache.SerializablePage;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.http.interactive.InteractiveException;
import nya.miku.wishmaster.lib.org_json.JSONException;

/**
 * Загрузчик АИБ-страниц, загружает или обновляет объект {@link SerializablePage} с чана напрямую.
 * Имплементирует {@link Runnable}, может вызываться асинхронно.
 * @author miku-nyan
 *
 */
public class PageLoaderFromChan implements Runnable {
    private static final String TAG = "PageLoaderFromChan";
    
    private boolean oomFlag = false;
    
    private final SerializablePage page;
    private final PageLoaderCallback callback;
    private final ChanModule chan;
    private final CancellableTask task;
    
    /**
     * Конструктор
     * @param page объект {@link SerializablePage}.
     * Для загрузки страницы с нуля, нужно создать новый объект типа {@link SerializablePage},
     * заполнить поле {@link SerializablePage#pageModel} и передать этот объект.
     * @param callback реализация интерфейса {@link PageLoaderCallback},
     * его методы будут вызваны после завершения загрузки или в случае ошибки
     * @param chan объект модуля имиджборды
     * @param task интерфейс отменяемой задачи
     */
    public PageLoaderFromChan(SerializablePage page, PageLoaderCallback callback, ChanModule chan, CancellableTask task) {
        this.page = page;
        this.callback = callback;
        this.chan = chan;
        this.task = task != null ? task : new CancellableTask.BaseCancellableTask();
    }
    
    @Override
    public void run() {
        try {
            UrlPageModel urlPage = page.pageModel;
            switch (urlPage.type) {
                case UrlPageModel.TYPE_BOARDPAGE:
                    ThreadModel[] threads = chan.getThreadsList(urlPage.boardName, urlPage.boardPage, null, task, page.threads);
                    page.threads = threads;
                    break;
                case UrlPageModel.TYPE_THREADPAGE:
                    PostModel[] posts = chan.getPostsList(urlPage.boardName, urlPage.threadNumber, null, task, page.posts);
                    page.posts = posts;
                    break;
                case UrlPageModel.TYPE_CATALOGPAGE:
                    ThreadModel[] catalog = chan.getCatalog(urlPage.boardName, urlPage.catalogType, null, task, page.threads);
                    page.threads = catalog;
                    break;
                case UrlPageModel.TYPE_SEARCHPAGE:
                    PostModel[] searchResult = null;
                    try {
                        searchResult = chan.search(urlPage.boardName, urlPage.searchRequest, urlPage.boardPage, null, task);
                    } catch (UnsupportedOperationException e) {
                        searchResult = chan.search(urlPage.boardName, urlPage.searchRequest, null, task);
                    }
                    page.posts = searchResult;
                    break;
                default:
                    throw new Exception("wrong type of board page");
            }
            if (task.isCancelled()) return;
            page.boardModel = chan.getBoard(urlPage.boardName, null, task);
            if (callback != null) callback.onSuccess();
        } catch (Exception e) {
            Logger.e(TAG, e);
            if (task.isCancelled()) return;
            if (callback != null) {
                if (e instanceof InteractiveException) {
                    callback.onInteractiveException((InteractiveException) e);
                } else if (e instanceof JSONException) {
                    callback.onError(MainApplication.getInstance().resources.getString(R.string.error_parse));
                } else {
                    callback.onError(e.getMessage());
                }
            }
        } catch (OutOfMemoryError oom) {
            MainApplication.freeMemory();
            Logger.e(TAG, oom);
            if (task.isCancelled()) return;
            if (!oomFlag) {
                oomFlag = true;
                run();
            } else if (callback != null) {
                callback.onError(MainApplication.getInstance().resources.getString(R.string.error_out_of_memory));
            }
        }
    }
    
    public interface PageLoaderCallback {
        public void onSuccess();
        public void onError(String message);
        public void onInteractiveException(InteractiveException e);
    }
}
