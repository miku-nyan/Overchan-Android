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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;

import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.containers.ReadableContainer;
import nya.miku.wishmaster.lib.base64.Base64;
import nya.miku.wishmaster.ui.downloading.DownloadingService;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.support.v4.util.LruCache;
import android.widget.ImageView;

/**
 * Трёхуровневый (Интернет -> Файловая система -> Оперативная память) кэш для картинок (Bitmap Cache)
 * @author miku-nyan
 *
 */
public class BitmapCache {
    
    private static final String TAG = "BitmapCache";
    
    private final LruCache<String, Bitmap> lru;
    private final FileCache fileCache;
    private final Set<String> currentDownloads;
    
    private static final Bitmap EMPTY_BMP = Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8);
    
    /**
     * Конструктор
     * @param maxSize максимальный размер кэша в памяти в байтах
     * @param fileCache объект файлового кэша
     */
    public BitmapCache(int maxSize, FileCache fileCache) {
        this.fileCache = fileCache;
        this.lru = new LruCache<String, Bitmap>(maxSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes() * value.getHeight();
            }
        };
        this.currentDownloads = new HashSet<String>();
    }
    
    /**
     * Попытаться получить картинку из кэша в памяти
     * @param hash хэш (уникальный для картинки)
     * @return Bitmap с картинкой, или null, если отсутствует в памяти
     */
    public Bitmap getFromMemory(String hash) {
        return lru.get(hash);
    }
    
    /**
     * Очистить LRU-кэш в памяти (вызывать в случае нехватки памяти)
     */
    public void clearLru() {
        lru.evictAll();
    }
    
    /**
     * Проверить, существует ли картинка в кэше
     * @param hash хэш (уникальный для картинки)
     * @return true, если картинка в наличии
     */
    public boolean isInCache(String hash) {
        if (getFromMemory(hash) != null) return true;
        File file = fileCache.get(FileCache.PREFIX_BITMAPS + hash);
        if (file != null && file.exists()) return true;
        return false;
    }
    
    /**
     * Попытаться получить картинку из кэша (сначала берётся из памяти, в случае отсутствия - из файлового кэша) 
     * @param hash хэш (уникальный для картинки)
     * @return Bitmap с картинкой, или null, если отсутствует и в памяти, и в файловом кэше
     */
    public Bitmap getFromCache(String hash) {
        Bitmap fromLru = getFromMemory(hash);
        if (fromLru != null) return fromLru;
        
        synchronized (currentDownloads) {
            if (currentDownloads.contains(hash)) return null;
        }
        
        InputStream fileStream = null;
        Bitmap bmp = null;
        try {
            File file = fileCache.get(FileCache.PREFIX_BITMAPS + hash);
            if (file == null || !file.exists()) {
                return null;
            }
            fileStream = new FileInputStream(file);
            bmp = BitmapFactory.decodeStream(fileStream);
        } catch (Exception e) {
            Logger.e(TAG, e);
        } catch (OutOfMemoryError oom) {
            MainApplication.freeMemory();
            Logger.e(TAG, oom);
        } finally {
            IOUtils.closeQuietly(fileStream);
        }
        if (bmp != null) lru.put(hash, bmp);
        return bmp;
    }
    
    /**
     * Попытаться получить картинку из локального контейнера-архива (сохранённого треда)
     * @param hash хэш (уникальный для картинки)
     * @param container объект-архив - источник картинок
     * @return Bitmap с картинкой, или null, если отсутствует в контейнере
     */
    public Bitmap getFromContainer(String hash, ReadableContainer container) {
        Bitmap bmp = getFromMemory(hash);
        if (bmp != null) return bmp;
        if (container == null) return null;
        
        for (String fileFormatInContainer : new String[] { DownloadingService.THUMBNAIL_FILE_FORMAT, DownloadingService.ICON_FILE_FORMAT } ) {
            String filenameInContainer = String.format(Locale.US, fileFormatInContainer, hash);
            if (bmp == null && container.hasFile(filenameInContainer)) {
                InputStream is = null;
                try {
                    is = container.openStream(filenameInContainer);
                    bmp = BitmapFactory.decodeStream(is);
                    if (bmp != null) lru.put(hash, bmp);
                } catch (Exception e) {
                    Logger.e(TAG, e);
                    bmp = null;
                } catch (OutOfMemoryError oom) {
                    MainApplication.freeMemory();
                    Logger.e(TAG, oom);
                    bmp = null;
                } finally {
                    IOUtils.closeQuietly(is);
                }
            }
        }
        return bmp;
    }
    
    /**
     * Загрузить и поместить в кэш картинку из интернета
     * @param hash хэш (уникальный для картинки)
     * @param url адрес URL (абсолютный или относительный путь)
     * @param maxSize максимальный размер в пикселях, до которого картинка будет уменьшена, или 0, если требуется оставить как есть
     * @param chan модуль чана для загрузки
     * @param task отменяемая задача
     * @return Bitmap с картинкой, или null, если загрузить не удалось
     */
    public Bitmap download(String hash, String url, int maxSize, ChanModule chan, CancellableTask task) {
        if (hash == null) {
            Logger.e(TAG, "received null hash; url: "+url);
            return null;
        }
        
        synchronized (currentDownloads) {
            while (currentDownloads.contains(hash)) {
                try {
                    currentDownloads.wait();
                } catch (Exception e) {
                    Logger.e(TAG, e);
                }
            }
            currentDownloads.add(hash);
        }
        
        try {
            Bitmap bmp = getFromCache(hash);
            if (bmp != null) return bmp;
            try {
                class BufOutputStream extends ByteArrayOutputStream {
                    public BufOutputStream() {
                        super(1024);
                    }
                    public InputStream toInputStream() {
                        return new ByteArrayInputStream(buf, 0, count);
                    }
                }
                BufOutputStream data = new BufOutputStream();
                chan.downloadFile(url, data, null, task);
                bmp = BitmapFactory.decodeStream(data.toInputStream());
            } catch (Exception e) {
                Logger.e(TAG, e);
                if (url.startsWith("data:image")) {
                    try {
                        byte[] data = Base64.decode(url.substring(url.indexOf("base64,") + 7), Base64.DEFAULT);
                        bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                    } catch (Exception e1) {
                        Logger.e(TAG, e1);
                    }
                }
            }
            if (bmp == null || (task != null && task.isCancelled())) {
                return null;
            }
            
            if (maxSize > 0) { //ебучий шакал
                double scale = (double)maxSize / Math.max(bmp.getWidth(), bmp.getHeight());
                    int width = (int) (bmp.getWidth() * scale);
                    int height = (int) (bmp.getHeight() * scale);
                    if (Math.min(width, height) > 0) {
                        Bitmap scaled = Bitmap.createScaledBitmap(bmp, width, height, true);
                        if (!scaled.equals(bmp)) bmp.recycle(); // see createScaledBitmap docs
                        bmp = scaled;
                    }
            }
            
            OutputStream fileStream = null;
            File file = null;
            boolean success = true;
            try {
                lru.put(hash, bmp);
                file = fileCache.create(FileCache.PREFIX_BITMAPS + hash);
                fileStream = new FileOutputStream(file);
                if (bmp.isRecycled() || !bmp.compress(Bitmap.CompressFormat.PNG, 100, fileStream)) {
                    throw new Exception();
                }
                fileStream.close();
                fileCache.put(file);
                fileStream = null;
            } catch (Exception e) {
                success = false;
                Logger.e(TAG, e);
            } finally {
                IOUtils.closeQuietly(fileStream);
                if (!success && file != null) fileCache.abort(file);
            }
            return bmp;
            
        } catch (OutOfMemoryError oom) {
            MainApplication.freeMemory();
            Logger.e(TAG, oom);
            return null;
            
        } finally {
            synchronized (currentDownloads) {
                currentDownloads.remove(hash);
                currentDownloads.notifyAll();
            }
        }
    }
    
    /**
     * Получить картинку асинхронно и установить в ImageView.<br>
     * На ImageView устанавливается тэг:<ul>
     * <li>строка с хэшем картинки, во время загрузки (прямо сейчас, в данный момент)</li>
     * <li>объект {@link Boolean#TRUE}, когда картинка загружена успешно</li>
     * <li>объект {@link Boolean#FALSE}, когда картинка не была загружена (в случае ошибки или downloadFromInternet == false)</li></ul>
     * @param hash хэш (уникальный для картинки)
     * @param url адрес URL (абсолютный или относительный)
     * @param maxSize максимальный размер в пикселях, до которого картинка будет уменьшена, или 0, если требуется оставить как есть
     * @param chan модуль чана для скачивания картинки из интернета
     * @param zipFile объект-архив - источник картинок для сохранённого треда (может принимать null)
     * @param task отменяемая задача
     * @param imageView объект {@link ImageView}, куда будет выведена картинка
     * @param executor асинхронный исполнитель
     * @param handler Handler UI потока
     * @param downloadFromInternet загружать ли картинку из интернета
     * @param defaultResId ID ресурса с картинкой ошибки, если картинка не загружена (не удалось или downloadFromInternet == false),
     * или 0 - если отображать ошибку не нужно
     */
    public void asyncGet(String hash, String url, int maxSize, ChanModule chan, ReadableContainer zipFile, CancellableTask task,
            ImageView imageView, Executor executor, Handler handler, boolean downloadFromInternet, int defaultResId) {
        if (hash == null) {
            Logger.e(TAG, "received null hash for url: " + url);
            imageView.setTag(Boolean.FALSE);
            imageView.setImageResource(defaultResId);
            return;
        }
        Bitmap fromLru = getFromMemory(hash);
        if (fromLru != null) {
            imageView.setTag(Boolean.TRUE);
            imageView.setImageBitmap(fromLru);
            return;
        } else {
            imageView.setImageBitmap(EMPTY_BMP);
        }
        class ImageDownloader implements Runnable {
            private final String hash;
            private final String url;
            private final int maxSize;
            private final ChanModule chan;
            private final ReadableContainer zipFile;
            private final CancellableTask task;
            private final ImageView imageView;
            private final Handler handler;
            private final boolean downloadFromInternet;
            private final int defaultResId;
            public ImageDownloader(String hash, String url, int maxSize, ChanModule chan, ReadableContainer zipFile, CancellableTask task,
                    ImageView imageView, Handler handler, boolean downloadFromInternet, int defaultResId) {
                this.hash = hash;
                this.url = url;
                this.maxSize = maxSize;
                this.chan = chan;
                this.zipFile = zipFile;
                this.task = task;
                this.imageView = imageView;
                this.handler = handler;
                this.downloadFromInternet = downloadFromInternet;
                this.defaultResId = defaultResId;
            }
            
            private Bitmap bmp;
            
            @Override
            public void run() {
                bmp = getFromCache(hash);
                if (bmp == null && zipFile != null) bmp = getFromContainer(hash, zipFile);
                if (bmp == null && downloadFromInternet) {
                    bmp = download(hash, url, maxSize, chan, task);
                }
                if (task != null && task.isCancelled()) return;
                if (imageView.getTag() == null || !imageView.getTag().equals(hash)) return;
                if (bmp == null) {
                    if (defaultResId == 0) {
                        imageView.setTag(Boolean.FALSE);
                        return;
                    }
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (imageView.getTag() == null || !imageView.getTag().equals(hash)) return;
                            if (bmp != null) {
                                imageView.setTag(Boolean.TRUE);
                                imageView.setImageBitmap(bmp);
                            } else {
                                imageView.setTag(Boolean.FALSE);
                                imageView.setImageResource(defaultResId);
                            }
                        } catch (OutOfMemoryError oom) {
                            MainApplication.freeMemory();
                            Logger.e(TAG, oom);
                        }
                    }
                });
            }
        }
        if (task != null && task.isCancelled()) return;
        imageView.setTag(hash);
        executor.execute(new ImageDownloader(hash, url, maxSize, chan, zipFile, task, imageView, handler, downloadFromInternet, defaultResId));
    }
    
}
