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

package nya.miku.wishmaster.ui.presentation;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.tuple.Triple;

import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.cache.SerializablePage;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.ui.Database;
import nya.miku.wishmaster.ui.Database.IsHiddenDelegate;
import nya.miku.wishmaster.ui.presentation.ClickableURLSpan.URLSpanClickListener;
import nya.miku.wishmaster.ui.presentation.FlowTextHelper.FloatingModel;
import nya.miku.wishmaster.ui.presentation.HtmlParser.ImageGetter;
import nya.miku.wishmaster.ui.settings.AutohideActivity;
import android.content.res.Resources.Theme;

/**
 * Объект - готовая к показу (без ресурсоёмких вычислений) модель страницы.<br>
 * Список готовых к показу объектов {@link PresentationItemModel} - {@link #presentationList} передаётся адаптеру для отображения постов.
 * @author miku-nyan
 *
 */
public class PresentationModel {
    private static final String TAG = "PresentationModel";
    
    /** Исходная страница - объект {@link SerializablePage} */
    public final SerializablePage source;
    /** обработчик ссылок URL внутри постов, ссылок на другие посты, а также ссылок на e-mail (mailto) */
    public final URLSpanClickListener spanClickListener;
    /** загрузчик картинок, содержащихся в тексте постов (например, смайлики) */
    public final ImageGetter imageGetter;
    private final Theme theme;
    private FloatingModel[] floatingModels;
    private final DateFormat dateFormat;
    private final boolean reduceNames;
    private final IsHiddenDelegate isHiddenDelegate;
    private final List<AutohideActivity.CompiledAutohideRule> autohideRules;
    
    /**
     * Примерный размер данного объекта в памяти в байтах.
     * Неизменяемый параметр, измеряется один раз - при создании объекта.
     * После обновления страницы, перед помещением в LRU кэш необходимь создать новый объект,
     * с помощью конструктора {@link #PresentationModel(PresentationModel)}
     */
    public final int size;
    
    /**
     * Список готовых к показу объектов для загрузки в адаптер.
     * Необходимо обновить (построить) данный список методом {@link #updateViewModels()} перед использованием,
     * или при обновлении содержания (постов) в {@link #source}
     */
    public List<PresentationItemModel> presentationList = null;
    
    private ArrayList<Triple<AttachmentModel, String, String>> attachments = null;
    private Object lock = new Object();
    
    private HashMap<String, Integer> postNumbersMap = new HashMap<String, Integer>();
    
    private volatile boolean notReady;
    
    /**
     * Конструктор
     * @param source исходная страница - объект {@link SerializablePage}
     * @param localTime определяет, будет ли использоваться локальное время телефона (если true), или часовой пояс имиджборды (если false)
     * @param reduceNames если true, имена пользователя по умолчанию (напр. Аноним) не будут показываться
     * @param spanClickListener обработчик ссылок URL внутри постов, ссылок на другие посты, а также ссылок на e-mail (mailto)
     * @param imageGetter загрузчик картинок, содержащихся в тексте постов (например, смайлики)
     * @param theme текущая тема
     * @param floatingModels массив из двух моделей обтекания картинка текстом. Первый элемент - обычная 
     * превьюшка, второй - со строкой с дополнительной информацией о вложении (gif, видео, аудио). 
     * Допустимо значение null, если обтекание не нужно вообще.
     */
    public PresentationModel(SerializablePage source, boolean localTime, boolean reduceNames,
            URLSpanClickListener spanClickListener, ImageGetter imageGetter, Theme theme, FloatingModel[] floatingModels) {
        if (source.pageModel.type == UrlPageModel.TYPE_OTHERPAGE) throw new IllegalArgumentException(); 
        this.source = source;
        this.spanClickListener = spanClickListener;
        this.imageGetter = imageGetter;
        this.theme = theme;
        this.floatingModels = floatingModels;
        this.reduceNames = reduceNames;
        Database database = MainApplication.getInstance().database;
        this.isHiddenDelegate = source.pageModel.type == UrlPageModel.TYPE_THREADPAGE ?
                database.getCachedIsHiddenDelegate(source.pageModel.chanName, source.pageModel.boardName, source.pageModel.threadNumber) :
                database.getDefaultIsHiddenDelegate();
        this.autohideRules = new ArrayList<AutohideActivity.CompiledAutohideRule>();
        try {
            JSONArray autohideJson = new JSONArray(MainApplication.getInstance().settings.getAutohideRulesJson());
            for (int i=0; i<autohideJson.length(); ++i) {
                AutohideActivity.AutohideRule rule = AutohideActivity.AutohideRule.fromJson(autohideJson.getJSONObject(i));
                if (rule.matches(source.pageModel.chanName, source.pageModel.boardName, source.pageModel.threadNumber)) {
                    this.autohideRules.add(new AutohideActivity.CompiledAutohideRule(rule));
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "error while processing regex autohide rules", e);
        }
        AndroidDateFormat.initPattern();
        String datePattern = AndroidDateFormat.getPattern();
        DateFormat dateFormat = datePattern == null ?
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) : new SimpleDateFormat(datePattern, Locale.US);
        dateFormat.setTimeZone(localTime ? TimeZone.getDefault() : TimeZone.getTimeZone(source.boardModel.timeZoneId));
        this.dateFormat = dateFormat;
        this.size = getSerializablePageSize(source);
    }
    
    /**
     * Конструктор создаёт такой же объект (с теми же ссылками), с обновлённым значением {@link #size}
     * @param model исходный объект
     */
    public PresentationModel(PresentationModel model) {
        source = model.source;
        spanClickListener = model.spanClickListener;
        imageGetter = model.imageGetter;
        theme = model.theme;
        floatingModels = model.floatingModels;
        dateFormat = model.dateFormat;
        reduceNames = model.reduceNames;
        isHiddenDelegate = model.isHiddenDelegate;
        autohideRules = model.autohideRules;
        size = getSerializablePageSize(source);
        presentationList = model.presentationList;
        attachments = model.attachments;
        postNumbersMap = model.postNumbersMap;
        notReady = model.notReady;
    }
    
    public interface RebuildCallback {
        void onRebuild();
    }
    
    /**
     * Определение примерного размера данной страницы в памяти в байтах
     */
    private static int getSerializablePageSize(SerializablePage page) {
        int size = 32, noPresentationSize = 0;
        if (page.posts != null) {
            size += (12 + (page.posts.length * 4));
            for (PostModel post : page.posts) size += ChanModels.getPostModelSize(post);
        }
        if (page.threads != null) {
            size += (12 + (page.threads.length * 4));
            for (ThreadModel threadModel : page.threads) {
                size += (32 + (threadModel.threadNumber == null ? 0 : (40 + (threadModel.threadNumber.length() * 2))));
                size += (12 + (threadModel.posts.length * 4));
                if (threadModel.posts.length > 0) size += ChanModels.getPostModelSize(threadModel.posts[0]);
                for (int i=1; i<threadModel.posts.length; ++i) noPresentationSize += ChanModels.getPostModelSize(threadModel.posts[i]);
            }
        }
        
        return size * 3 + noPresentationSize;
    }
    
    /**
     * Обновляет (или перестраивает) презентационные модели
     * @param showIndex добавлять индекс (номер по порядку) в заголовок элемента
     * @param task отменяемая задача
     * @param rebuildCallback интерфейс (делегат) вызвающий метод в случае, если список перестраивается
     */
    public synchronized void updateViewModels(boolean showIndex, CancellableTask task, RebuildCallback rebuildCallback) {
        PostModel[] posts = source.posts;
        if (posts == null) {
            posts = new PostModel[source.threads.length];
            for (int i=0; i<source.threads.length; ++i) {
                posts[i] = source.threads[i].posts[0];
            }
        }
        try {
            updateViewModels(posts, showIndex, task, rebuildCallback);
        } catch (OutOfMemoryError oom) {
            MainApplication.freeMemory();
            Logger.e(TAG, oom);
            if (task != null && task.isCancelled()) return;
            try {
                updateViewModels(posts, showIndex, task, rebuildCallback);
            } catch (OutOfMemoryError oom1) {
                MainApplication.freeMemory();
                Logger.e(TAG, oom1);
            }
        }
    }
    
    /**
     * Установить новые модели обтекания картинки текстом (и перестроить текст комментария в случае необходимости)
     * @param floatingModels массив из двух моделей обтекания картинки текстом
     */
    public void setFloatingModels(FloatingModel[] models) {
        if (!FlowTextHelper.IS_AVAILABLE) return;
        if (models == null || models.length != 2 || models[0] == null || models[1] == null) return;
        if (floatingModels == null || floatingModels.length != 2 || floatingModels[0] == null || floatingModels[1] == null) return;
        if (models[0].equals(floatingModels[0]) && models[1].equals(floatingModels[1])) return;
        
        try {
            this.floatingModels = models;
            int size = presentationList.size();
            for (int i=0; i<size; ++i) presentationList.get(i).changeFloatingModels(models);
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
    }
    
    private synchronized void updateViewModels(PostModel[] posts, boolean showIndex, CancellableTask task, RebuildCallback rebuildCallback) {
        if (task == null) task = CancellableTask.NOT_CANCELLABLE;
        synchronized (lock) {
            notReady = true;
        }
        if (presentationList == null) {
            presentationList = new ArrayList<PresentationItemModel>(posts.length); // нужен ли синхронизированный лист(?)
            attachments = new ArrayList<Triple<AttachmentModel, String, String>>();
        }
        
        if (task.isCancelled()) return;
        
        boolean rebuild = false;
        if (posts.length < presentationList.size()) {
            rebuild = true;
            Logger.d(TAG, "rebuild: new list is shorter");
        } else {
            for (int i=0, size=presentationList.size(); i<size; ++i) {
                if (!presentationList.get(i).sourceModel.number.equals(posts[i].number) ||
                        ChanModels.hashPostModel(posts[i]) != presentationList.get(i).sourceModelHash) {
                    rebuild = true;
                    Logger.d(TAG, "rebuild: changed item "+i);
                    break;
                }
                presentationList.get(i).sourceModel.deleted = posts[i].deleted;
            }
        }
        
        if (task.isCancelled()) return;
        
        if (rebuild) {
            if (rebuildCallback != null) rebuildCallback.onRebuild();
            presentationList.clear();
            postNumbersMap.clear();
            attachments.clear();
        }
        
        final boolean openSpoilers = MainApplication.getInstance().settings.openSpoilers();
        
        long t0 = System.currentTimeMillis();
        
        int threads = Runtime.getRuntime().availableProcessors();
        boolean smpMode = threads > 1;
        SMPHandler smpHandler = smpMode ? new SMPHandler(threads, task) : null;
        if (smpMode) for (int i=presentationList.size(); i<posts.length; ++i) smpHandler.enqueue(posts[i], openSpoilers, showIndex ? i+1 : -1);
        
        for (int i=presentationList.size(); i<posts.length; ++i) {
            if (task.isCancelled()) return;
            PresentationItemModel model = smpMode ? smpHandler.dequeue() : createItemModel(posts[i], openSpoilers, showIndex ? i+1 : -1);
            
            if (task.isCancelled()) return;
            postNumbersMap.put(posts[i].number, i);
            if (source.pageModel.type == UrlPageModel.TYPE_THREADPAGE) {
                for (String ref : model.referencesTo) {
                    Integer postPosition = postNumbersMap.get(ref);
                    if (postPosition != null && postPosition < presentationList.size()) {
                        presentationList.get(postPosition).addReferenceFrom(model.sourceModel.number);
                    }
                }
            }
            presentationList.add(model);
            for (int j=0; j<model.attachmentHashes.length; ++j) {
                attachments.add(Triple.of(posts[i].attachments[j], model.attachmentHashes[j], posts[i].number));
            }
        }
        
        Logger.d(TAG, "time: " + (System.currentTimeMillis() - t0) + "; " + threads + " threads");
        
        if (source.pageModel.type == UrlPageModel.TYPE_THREADPAGE) {
            for (PresentationItemModel model : presentationList) {
                if (task.isCancelled()) return;
                model.buildReferencesString();
            }
        }
                
        if (source.threads != null) {
            for (int i=0; i<source.threads.length; ++i) {
                if (task.isCancelled()) return;
                presentationList.get(i).buildPostsCountString(source.threads[i].postsCount, source.threads[i].attachmentsCount);
                presentationList.get(i).buildStickyClosedString(source.threads[i].isSticky, source.threads[i].isClosed);
            }
        }
        notReady = false;
    }
    
    private PresentationItemModel createItemModel(PostModel post, boolean openSpoilers, int index) {
        PresentationItemModel model = new PresentationItemModel(
                post,
                source.pageModel.chanName,
                source.pageModel.boardName,
                source.pageModel.type == UrlPageModel.TYPE_THREADPAGE ? source.pageModel.threadNumber : null,
                dateFormat,
                spanClickListener,
                imageGetter,
                ThemeUtils.ThemeColors.getInstance(theme),
                openSpoilers,
                floatingModels);
        
        model.buildSpannedHeader(index, source.boardModel.bumpLimit, reduceNames ? source.boardModel.defaultUserName : null,
                source.pageModel.type == UrlPageModel.TYPE_SEARCHPAGE ? post.parentThread : null);
        
        if (source.pageModel.type == UrlPageModel.TYPE_THREADPAGE) {
            model.hidden = isHiddenDelegate.
                    isHidden(source.pageModel.chanName, source.pageModel.boardName, source.pageModel.threadNumber, post.number);
        } else if (source.pageModel.type == UrlPageModel.TYPE_BOARDPAGE || source.pageModel.type == UrlPageModel.TYPE_CATALOGPAGE) {
            model.hidden = isHiddenDelegate.isHidden(source.pageModel.chanName, source.pageModel.boardName, post.number, null);
        }
        if (!model.hidden && ( //автоскрытие
                source.pageModel.type == UrlPageModel.TYPE_THREADPAGE ||
                source.pageModel.type == UrlPageModel.TYPE_BOARDPAGE ||
                source.pageModel.type == UrlPageModel.TYPE_CATALOGPAGE)) {
            for (AutohideActivity.CompiledAutohideRule rule : autohideRules) {
                if (
                        (rule.inComment && model.spannedComment != null && rule.pattern.matcher(model.spannedComment).find()) ||
                        (rule.inSubject && post.subject != null && rule.pattern.matcher(post.subject).find()) ||
                        (rule.inName &&
                                (post.name != null && rule.pattern.matcher(post.name).find()) ||
                                (post.trip != null && rule.pattern.matcher(post.trip).find()))) {
                    model.hidden = true;
                    model.autohideReason = rule.regex;
                }
            }
        }
        return model;
    }
    
    private class SMPHandler {
        private final CancellableTask task;
        private final Executor executor;
        private LinkedList<SMPTask> queue;
        
        private SMPHandler(int threadsCount, CancellableTask task) {
            this.task = task;
            this.executor = Executors.newFixedThreadPool(threadsCount);
            this.queue = new LinkedList<>();
        }
        
        private void enqueue(final PostModel post, final boolean openSpoilers, final int index) {
            final SMPTask smpTask = new SMPTask();
            queue.add(smpTask);
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (task.isCancelled()) return;
                    PresentationItemModel model = createItemModel(post, openSpoilers, index);
                    smpTask.result = model;
                    smpTask.isDone = true;
                }
            });
        }
        
        private PresentationItemModel dequeue() {
            if (task.isCancelled() || queue.isEmpty()) return null;
            SMPTask smpTask = queue.removeFirst();
            while (!smpTask.isDone && !task.isCancelled()) Thread.yield();
            if (task.isCancelled()) return null;
            return smpTask.result;
        }
    }
    
    private static class SMPTask {
        private volatile boolean isDone = false;
        private PresentationItemModel result = null;
    }
    
    /**
     * Возвращает true, если построение модели не было завершено (было прервано или происходит в данный момент)
     */
    public boolean isNotReady() {
        return notReady;
    }
    
    /**
     * Установить значение notReady как true (например, при фоновом автообновлении, чтобы при отображении построить модель)
     */
    public void setNotReady() {
        notReady = true;
    }
    
    /**
     * Получить список вложений на данной странице, в виде {@link Triple} из модели вложения, хэша вложения и номера поста, к которому относится файл
     * @return список или null, если модель не построена или обновляется в данный момент
     */
    public List<Triple<AttachmentModel, String, String>> getAttachments() {
        if (notReady || attachments == null) return null;
        synchronized (lock) {
            if (notReady) return null;
            return new ArrayList<Triple<AttachmentModel, String, String>>(attachments);
        }
    }
    
    /**
     * Получить копию списка готовых постов ({@link #presentationList}), в случае, если в данный момент не происходит обновление модели
     * (в этом случае метод вернёт null)
     */
    public List<PresentationItemModel> getSafePresentationList() {
        if (notReady || presentationList == null) return null;
        synchronized (lock) {
            if (notReady) return null;
            return new ArrayList<PresentationItemModel>(presentationList);
        }
    }
    
}
