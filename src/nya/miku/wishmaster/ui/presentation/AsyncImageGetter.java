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

package nya.miku.wishmaster.ui.presentation;

import java.lang.ref.WeakReference;
import java.util.concurrent.Executor;

import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.util.CryptoUtils;
import nya.miku.wishmaster.cache.BitmapCache;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.ui.settings.ApplicationSettings.StaticSettingsContainer;
import nya.miku.wishmaster.ui.settings.Wifi;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.view.View;

/**
 * Обработчик-згрузчик картинок в тэгах &lt;img&gt;.
 * Может использоваться для загрузки картинок внутри постов (например, смайлики на 1 апреля).
 * @author miku-nyan
 *
 */
public class AsyncImageGetter implements HtmlParser.ImageGetter {
    private static final Bitmap EMPTY_BMP = Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8);
    
    private final Resources res;
    private final BitmapCache bmpCache;
    private final ChanModule chan;
    private Executor executor;
    private CancellableTask task;
    private WeakReference<View> view;
    private Handler handler;
    private StaticSettingsContainer staticSettings;
    private final int maxSize;
    
    /**
     * Конструктор
     * @param res объект ресурсов
     * @param maxSizeRes ID ресурса (dimen) с максимальным размером картинки (по большей из сторон)
     * @param bmpCache объект кэша картинок (Bitmap Cache)
     * @param chan объект ChanModule для текущего чана
     * @param executor асинхронный исполнитель
     * @param task объект отменяемой задачи
     * @param view видждет (контейнер), обновляемый после успешной загрузки. (сохраняется слабая ссылка во избежание утечки памяти)
     * @param handler Handler основного UI потока
     */
    public AsyncImageGetter(Resources res, int maxSizeRes, BitmapCache bmpCache, ChanModule chan,
            Executor executor, CancellableTask task, View view, Handler handler, StaticSettingsContainer staticSettings) {
        this.res = res;
        this.bmpCache = bmpCache;
        this.chan = chan;
        this.executor = executor;
        this.task = task;
        this.view = new WeakReference<View>(view);
        this.handler = handler;
        this.staticSettings = staticSettings;
        this.maxSize = res.getDimensionPixelSize(maxSizeRes);
    }
    
    /**
     * Установить новые значения ссылок на объекты
     * @param executor асинхронный исполнитель
     * @param task объект отменяемой задачи
     * @param view видждет (контейнер), обновляемый после успешной загрузки. (сохраняется слабая ссылка во избежание утечки памяти)
     * @param handler Handler основного UI потока
     */
    public void setObjects(Executor executor, CancellableTask task, View view, Handler handler, StaticSettingsContainer staticSettings) {
        this.executor = executor;
        this.task = task;
        this.view = new WeakReference<View>(view);
        this.handler = handler;
        this.staticSettings = staticSettings;
    }
    
    @Override
    public Drawable getDrawable(String source) {
        String hash = CryptoUtils.computeMD5(chan.getChanName().concat(source));
        Bitmap fromCache = bmpCache.getFromCache(hash);
        if (fromCache != null) {
            Drawable drawable = new BitmapDrawable(res, fromCache);
            drawable.setBounds(0, 0, Math.min(drawable.getIntrinsicWidth(), maxSize), Math.min(drawable.getIntrinsicHeight(), maxSize));
            return drawable;
        }
        
        MutableBmpDrawable drawable = new MutableBmpDrawable(MainApplication.getInstance().resources, EMPTY_BMP);
        drawable.setBounds(0, 0, maxSize, maxSize);
        final boolean canDownload;
        switch (staticSettings.downloadThumbnails) {
            case ALWAYS: canDownload = true; break;
            case WIFI_ONLY: canDownload = Wifi.isConnected(); break;
            default: canDownload = false; break;
        }
        if (canDownload) {
            executor.execute(new Downloader(hash, source, drawable, task));
        }
        return drawable;
    }
    
    private class Downloader implements Runnable {
        private final String hash;
        private final String url;
        private final MutableBmpDrawable drawable;
        private final CancellableTask task;
        public Downloader(String hash, String url, MutableBmpDrawable drawable, CancellableTask task) {
            this.hash = hash;
            this.url = url;
            this.drawable = drawable;
            this.task = task;
        }
        @Override
        public void run() {
            Bitmap fromInternet = bmpCache.download(hash, url, maxSize, chan, task);
            if (fromInternet != null) {
                Drawable newDrawable = new BitmapDrawable(res, fromInternet);
                int left = Math.max(0, maxSize - newDrawable.getIntrinsicWidth());
                int top = Math.max(0, maxSize - newDrawable.getIntrinsicHeight());
                newDrawable.setBounds(left, top, maxSize, maxSize);
                drawable.setDrawable(newDrawable);
                if (task != null && task.isCancelled()) return;
                if (handler == null || view.get() == null) return;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        View v = view.get();
                        if (v == null) return;
                        v.invalidate();    
                    }
                });
            }
        }
    }
    
    private class MutableBmpDrawable extends BitmapDrawable {
        private Drawable drawable;
        public void setDrawable(Drawable drawable){
            this.drawable = drawable;
        }
        public MutableBmpDrawable(Resources res, Bitmap bitmap) {
            super(res, bitmap);
        }
        @Override
        public void draw(Canvas canvas){
            if (drawable != null) {
                drawable.draw(canvas);
            } else {
                super.draw(canvas);
            }
        }
    }
}
