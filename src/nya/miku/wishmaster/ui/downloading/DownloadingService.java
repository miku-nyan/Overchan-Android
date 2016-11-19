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

package nya.miku.wishmaster.ui.downloading;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BadgeIconModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.PageLoaderFromChan;
import nya.miku.wishmaster.cache.BitmapCache;
import nya.miku.wishmaster.cache.FileCache;
import nya.miku.wishmaster.cache.SerializablePage;
import nya.miku.wishmaster.common.Async;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.containers.WriteableContainer;
import nya.miku.wishmaster.http.interactive.InteractiveException;
import nya.miku.wishmaster.lib.base64.Base64;
import nya.miku.wishmaster.lib.base64.Base64OutputStream;
import nya.miku.wishmaster.ui.Attachments;
import nya.miku.wishmaster.ui.settings.ApplicationSettings;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.res.ResourcesCompat;

public class DownloadingService extends Service {
    private static final String TAG = "DownloadingService";
    
    public static final String EXTRA_DOWNLOADING_ITEM = "DownloadingItem";
    public static final String EXTRA_DOWNLOADING_REPORT = "DownloadingReport";
    
    public static final int REPORT_NONE = 0;
    public static final int REPORT_OK = 1;
    public static final int REPORT_ERROR = 2;
    
    public static final String BROADCAST_UPDATED = "nya.miku.wishmaster.BROADCAST_ACTION_DOWNLOADING_UPDATED";
    
    public static final String SHARED_PREFERENCES_NAME = "downloading_last_error_report";
    public static final String PREF_ERROR_REPORT = "LAST_ERROR_REPORT";
    public static final String PREF_ERROR_ITEMS = "LAST_ERROR_ITEMS";
    
    /** путь и имя файла с основным (сериализованным) объектом сохранённой страницы внутри архива */
    public static final String MAIN_OBJECT_FILE = "data/serialized.bin";
    /** имя файла со значком favicon сохраняемой HTML страницы внутри архива */
    public static final String FAVICON_FILE = "favicon.png";
    /** формат расположения файлов-превью внутри архива сохранённой страницы (%s соответствует хэшу вложения) */
    public static final String THUMBNAIL_FILE_FORMAT = "thumbnails/%s.png";
    /** формат расположения файлов-иконок (флаги/полит.предпочтения) внутри архива сохранённой страницы (%s соответствует хэшу иконки) */
    public static final String ICON_FILE_FORMAT = "icons/%s.png";
    /** название папки (внутри архива сохранённой страницы) с оригиналами файлов-вложений */
    public static final String ORIGINALS_FOLDER = "originals";
    
    public static final int MODE_ONLY_CACHE = 1;
    public static final int MODE_DOWNLOAD_THUMBS = 2;
    public static final int MODE_DOWNLOAD_ALL = 3;
    
    public static final int DOWNLOADING_NOTIFICATION_ID = 20;
    public static final int ERROR_REPORT_NOTIFICATION_ID = 30;
    
    private volatile boolean nowTaskRunning = false;
    
    private NotificationCompat.Builder progressNotifBuilder;
    
    private Queue<DownloadingQueueItem> downloadingQueue;
    private DownloadingTask currentTask;
    private DownloadingServiceBinder binder;
    private NotificationManager notificationManager;
    private ApplicationSettings settings;
    private FileCache fileCache;
    private DownloadingLocker downloadingLocker;
    private BitmapCache bitmapCache;
    
    private boolean isForeground = false;
    
    private static DownloadingTask sCurrentTask;
    private static Queue<DownloadingQueueItem> sQueue;
    
    public static boolean isInQueue(DownloadingQueueItem item) {
        DownloadingTask currentTask = sCurrentTask;
        if (currentTask != null && currentTask.getCurrentItem() != null && currentTask.getCurrentItem().equals(item)) {
            return true;
        }
        return sQueue != null && sQueue.contains(item);
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        downloadingQueue = new LinkedBlockingQueue<DownloadingQueueItem>();
        sQueue = downloadingQueue;
        binder = new DownloadingServiceBinder(this);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        settings = MainApplication.getInstance().settings;
        fileCache = MainApplication.getInstance().fileCache;
        downloadingLocker = MainApplication.getInstance().downloadingLocker;
        bitmapCache = MainApplication.getInstance().bitmapCache;
        Logger.d(TAG, "created downloading service");
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        sCurrentTask = null;
        Logger.d(TAG, "destroyed downloading service");
    }
    
    private void notifyForeground(int id, Notification notification) {
        if (!isForeground) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ECLAIR) {
                try {
                    getClass().getMethod("setForeground", new Class[] { boolean.class }).invoke(this, Boolean.TRUE);
                } catch (Exception e) {
                    Logger.e(TAG, "cannot invoke setForeground(true)", e);
                }
                notificationManager.notify(id, notification);
            } else {
                ForegroundCompat.startForeground(this, id, notification);
            }
            isForeground = true;
        } else {
            notificationManager.notify(id, notification);
        }
    }
    
    private void cancelForeground(int id) {
        if (isForeground) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ECLAIR) {
                notificationManager.cancel(id);
                try {
                    getClass().getMethod("setForeground", new Class[] { boolean.class }).invoke(this, Boolean.FALSE);
                } catch (Exception e) {
                    Logger.e(TAG, "cannot invoke setForeground(false)", e);
                }
            } else {
                ForegroundCompat.stopForeground(this);
            }
            isForeground = false;
        } else {
            notificationManager.cancel(id);
        }
    }
    
    @TargetApi(Build.VERSION_CODES.ECLAIR)
    private static class ForegroundCompat {
        static void startForeground(Service service, int id, Notification notification) {
            service.startForeground(id, notification);
        }
        static void stopForeground(Service service) {
            service.stopForeground(true);
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    @SuppressLint("InlinedApi")
    public int onStartCommand(Intent intent, int flags, int startId) {
        onStart(intent, startId);
        return Service.START_REDELIVER_INTENT;
    }
    
    @Override
    public void onStart(Intent intent, int startId) {
        if (intent != null) {
            DownloadingQueueItem item = (DownloadingQueueItem) intent.getSerializableExtra(EXTRA_DOWNLOADING_ITEM);
            if (item != null) downloadingQueue.add(item);
        }
        if (currentTask == null || !nowTaskRunning) {
            Logger.d(TAG, "starting downloading task");
            nowTaskRunning = true;
            currentTask = new DownloadingTask(startId);
            sCurrentTask = currentTask;
            Async.runAsync(currentTask);
        } else {
            Logger.d(TAG, "item added to download queue");
            if (progressNotifBuilder != null) {
                progressNotifBuilder.setContentTitle(getString(R.string.downloading_title, downloadingQueue.size() + 1));
                notifyForeground(DOWNLOADING_NOTIFICATION_ID, progressNotifBuilder.build());
            }
            sendBroadcast(new Intent(BROADCAST_UPDATED));
            currentTask.setStartId(startId);
        }
    }
    
    public class DownloadingTask extends CancellableTask.BaseCancellableTask implements Runnable {
        private int startId;
        private long maxProgressValue = 100;
        private int curProgress = -1;
        private String currentItemName;
        private DownloadingQueueItem currentItem;
        private StringBuilder errorReport;
        private ArrayList<DownloadingQueueItem> errorItems;
        
        public DownloadingTask(int startId) {
            setStartId(startId);
        }
        
        public void setStartId(int startId) {
            this.startId = startId;
        }
        
        public int getCurrentProgress() {
            return curProgress;
        }
        
        public String getCurrentItemName() {
            return currentItemName;
        }
        
        public DownloadingQueueItem getCurrentItem() {
            return currentItem;
        }
        
        @Override
        public void run() {
            errorReport = new StringBuilder();
            errorItems = new ArrayList<>();
            Intent intentToProgressDialog = new Intent(DownloadingService.this, DownloadingProgressActivity.class);
            PendingIntent pIntentToProgressDialog =
                    PendingIntent.getActivity(DownloadingService.this, 0, intentToProgressDialog, PendingIntent.FLAG_CANCEL_CURRENT);
            progressNotifBuilder = new NotificationCompat.Builder(DownloadingService.this).
                    setSmallIcon(android.R.drawable.stat_sys_download).
                    setTicker(getString(R.string.downloading_start_ticker)).
                    setContentIntent(pIntentToProgressDialog).
                    setOngoing(true).
                    setCategory(NotificationCompat.CATEGORY_PROGRESS).
                    setProgress(100, 0, true);
            
            while (!isCancelled() && !downloadingQueue.isEmpty()) {
                DownloadingQueueItem item = downloadingQueue.poll();
                currentItem = item;
                progressNotifBuilder.setContentTitle(downloadingQueue.size() > 0 ?
                        getString(R.string.downloading_title, downloadingQueue.size() + 1) : getString(R.string.downloading_title_simple));
                
                if (item.type == DownloadingQueueItem.TYPE_ATTACHMENT) {
                    final String filename = Attachments.getAttachmentLocalFileName(item.attachment, item.boardModel);
                    if (filename == null) continue;
                    String elementName = getString(R.string.downloading_element_format, item.chanName,
                            Attachments.getAttachmentLocalShortName(item.attachment, item.boardModel));
                    currentItemName = elementName;
                    
                    curProgress = -1;
                    progressNotifBuilder.setContentText(filename).setProgress(100, 0, true);
                    notifyForeground(DOWNLOADING_NOTIFICATION_ID, progressNotifBuilder.build());
                    sendBroadcast(new Intent(BROADCAST_UPDATED));
                    
                    ProgressListener listener = new ProgressListener() {
                        @Override
                        public void setProgress(long value) {
                            int newProgress = (int)(100 * (double)value / maxProgressValue);
                            if (newProgress == curProgress) return;
                            curProgress = newProgress;
                            progressNotifBuilder.setProgress(100, newProgress, false);
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                                progressNotifBuilder.setContentText("("+newProgress+"%) "+filename);
                            }
                            notifyForeground(DOWNLOADING_NOTIFICATION_ID, progressNotifBuilder.build());
                            sendBroadcast(new Intent(BROADCAST_UPDATED));
                        }
                        @Override
                        public void setMaxValue(long value) {
                            if (value > 0) maxProgressValue = value;
                        }
                        @Override
                        public void setIndeterminate() {
                            if (curProgress == -1) return;
                            progressNotifBuilder.setProgress(100, 0, true);
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                                progressNotifBuilder.setContentText(filename);
                            }
                            notifyForeground(DOWNLOADING_NOTIFICATION_ID, progressNotifBuilder.build());
                            sendBroadcast(new Intent(BROADCAST_UPDATED));
                            curProgress = -1;
                        }
                    };
                    
                    File directory = new File(settings.getDownloadDirectory(), item.chanName);
                    if (item.subdirectory != null && item.subdirectory.length() > 0) directory = new File(directory, item.subdirectory);
                    if (!directory.mkdirs() && !directory.isDirectory()) {
                        addError(item, elementName, getString(R.string.downloading_error_mkdir));
                        continue;
                    }
                    File target = new File(directory, filename);
                    if (target.exists()) {
                        addError(item, elementName, getString(R.string.downloading_error_file_exists));
                        continue;
                    }
                    File fromCache = fileCache.get(FileCache.PREFIX_ORIGINALS + ChanModels.hashAttachmentModel(item.attachment) +
                            Attachments.getAttachmentExtention(item.attachment));
                    if (fromCache != null) {
                        String fromCacheFilename = fromCache.getAbsolutePath();
                        while (downloadingLocker.isLocked(fromCacheFilename)) downloadingLocker.waitUnlock(fromCacheFilename);
                        if (isCancelled()) continue;
                        boolean success = false;
                        InputStream is = null;
                        OutputStream os = null;
                        try {
                            if (listener != null) listener.setMaxValue(fromCache.length());
                            is = IOUtils.modifyInputStream(new FileInputStream(fromCache), listener, this);
                            os = new FileOutputStream(target);
                            IOUtils.copyStream(is, os);
                            success = true;
                        } catch (Exception e) {
                            if (!isCancelled()) {
                                addError(item, elementName,
                                        getString(IOUtils.isENOSPC(e) ? R.string.error_no_space : R.string.downloading_error_copy));
                            }
                        } finally {
                            IOUtils.closeQuietly(is);
                            IOUtils.closeQuietly(os);
                            if (!success) target.delete();
                            else notifyMediaScanner(target);
                        }
                    } else {
                        String targetFilename = target.getAbsolutePath();
                        while (!downloadingLocker.lock(targetFilename)) downloadingLocker.waitUnlock(targetFilename);
                        if (isCancelled()) {
                            downloadingLocker.unlock(targetFilename);
                            continue;
                        }
                        boolean success = false;
                        FileOutputStream out = null;
                        try {
                            out = new FileOutputStream(target);
                            MainApplication.getInstance().getChanModule(item.chanName).downloadFile(item.attachment.path, out, listener, this);
                            success = true;
                        } catch (Exception e) {
                            Logger.e(TAG, e);
                            if (!isCancelled()) addError(item, elementName, e instanceof InteractiveException ?
                                    getString(R.string.downloading_error_interactive_format, ((InteractiveException) e).getServiceName()) :
                                        getMessageOrENOSPC(e));
                        } finally {
                            IOUtils.closeQuietly(out);
                            if (!success) target.delete();
                            else notifyMediaScanner(target);
                            downloadingLocker.unlock(targetFilename);
                        }
                    }
                    
                } else if (item.type == DownloadingQueueItem.TYPE_THREAD) {
                    String filename = item.boardModel.boardName + "-" + item.threadUrlPage.threadNumber + settings.getDownloadThreadFormat();
                    String htmlname = item.chanName + "_" + item.boardModel.boardName + "_" + item.threadUrlPage.threadNumber + ".html";
                    String elementName = getString(R.string.downloading_element_format, item.chanName,
                            getString(R.string.downloading_thread_format, item.boardModel.boardName, item.threadUrlPage.threadNumber));
                    currentItemName = elementName;
                    
                    curProgress = -1;
                    progressNotifBuilder.setContentText(elementName).setProgress(100, 0, true);
                    notifyForeground(DOWNLOADING_NOTIFICATION_ID, progressNotifBuilder.build());
                    sendBroadcast(new Intent(BROADCAST_UPDATED));
                    
                    File directory = new File(settings.getDownloadDirectory(), item.chanName);
                    if (!directory.mkdirs() && !directory.isDirectory()) {
                        addError(item, elementName, getString(R.string.downloading_error_mkdir));
                        continue;
                    }
                    
                    WriteableContainer zip = null;
                    File zipFile = new File(directory, filename);
                    try {
                        try {
                            zip = WriteableContainer.obtain(zipFile);
                        } catch (Exception e) {
                            throw new Exception(getString(IOUtils.isENOSPC(e) ? R.string.error_no_space : R.string.downloading_error_mkfile));
                        }
                        final SerializablePage page = getSerializablePage(item);
                        if (isCancelled()) throw new Exception();
                        
                        HtmlBuilder htmlBuilder = null;
                        try {
                            htmlBuilder = new HtmlBuilder(zip.openStream(htmlname), 
                                    new HtmlBuilder.RefsGetter() {
                                        final ChanModule chan = MainApplication.getInstance().getChanModule(page.boardModel.chan);
                                        
                                        @Override
                                        public String getFavicon() {
                                            return HtmlBuilder.DATA_DIR + "/" + FAVICON_FILE;
                                        }
                                        
                                        @Override
                                        public String getThumbnail(AttachmentModel attachment) {
                                            if (attachment.isSpoiler) return getFavicon(); //TODO запилить картинку спойлера
                                            return attachment.thumbnail == null ? null : String.format(Locale.US, THUMBNAIL_FILE_FORMAT,
                                                    ChanModels.hashAttachmentModel(attachment));
                                        }
                                        
                                        @Override
                                        public String getOriginal(AttachmentModel attachment) {
                                            String chanRef = chan.fixRelativeUrl(attachment.path != null ? attachment.path : attachment.thumbnail);
                                            if (attachment.type != AttachmentModel.TYPE_OTHER_NOTFILE) {
                                                String filename = Attachments.getAttachmentLocalFileName(attachment, page.boardModel);
                                                if (filename != null && filename.length() != 0) {
                                                    //TODO проверять, когда вложение отсутствует и в папке с загрузками, и в кэше, отдавать ссылку
                                                    return ORIGINALS_FOLDER + "/" + filename;
                                                } else {
                                                    return chanRef;
                                                }
                                            } else return chanRef;
                                        }
                                        
                                        @Override
                                        public String getIcon(BadgeIconModel icon) {
                                            return String.format(Locale.US, ICON_FILE_FORMAT,
                                                    ChanModels.hashBadgeIconModel(icon, chan.getChanName()));
                                        }
                                    });
                            htmlBuilder.write(page);
                        } catch (Exception e) {
                            Logger.e(TAG, e);
                            throw new Exception(getString(IOUtils.isENOSPC(e) ? R.string.error_no_space : R.string.downloading_error_save_html));
                        } finally {
                            IOUtils.closeQuietly(htmlBuilder);
                        }
                        
                        String pageTitle = HtmlBuilder.buildTitle(page);
                        
                        try {
                            MainApplication.getInstance().serializer.savePage(zip.openStream(MAIN_OBJECT_FILE), pageTitle, page.pageModel, page);
                        } catch (Exception e) {
                            Logger.e(TAG, e);
                            throw new Exception(getString(IOUtils.isENOSPC(e) ? R.string.error_no_space : R.string.downloading_error_serialize));
                        }
                        
                        for (String asset : HtmlBuilder.ASSETS) {
                            if (zip.hasFile(asset)) continue;
                            InputStream in = null;
                            OutputStream out = null;
                            try {
                                in = getAssets().open(asset);
                                out = zip.openStream(HtmlBuilder.DATA_DIR + "/" + asset);
                                IOUtils.copyStream(in, out);
                            } catch (Exception e) {
                                Logger.e(TAG, e);
                                if (!isCancelled()) {
                                    if (IOUtils.isENOSPC(e)) {
                                        throw new Exception(getString(R.string.error_no_space));
                                    } else {
                                        addError(item, asset, getString(R.string.downloading_error_copy));
                                    }
                                }
                            } finally {
                                IOUtils.closeQuietly(in);
                                IOUtils.closeQuietly(out);
                            }
                        }
                        
                        OutputStream faviconStream = null;
                        try {
                            faviconStream = zip.openStream(HtmlBuilder.DATA_DIR + "/" + FAVICON_FILE);
                            Drawable favicon = new LayerDrawable(new Drawable[] {
                                    MainApplication.getInstance().getChanModule(item.chanName).getChanFavicon(),
                                    ResourcesCompat.getDrawable(getResources(), R.drawable.favicon_overlay_local, null)
                            });
                            Bitmap bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
                            favicon.setBounds(0, 0, 32, 32);
                            favicon.draw(new Canvas(bmp));
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, faviconStream);
                        } catch (Exception e) {
                            Logger.e(TAG, e);
                            if (!isCancelled()) {
                                if (IOUtils.isENOSPC(e)) {
                                    throw new Exception(getString(R.string.error_no_space));
                                } else {
                                    addError(item, FAVICON_FILE, getString(R.string.downloading_error_copy));
                                }
                            }
                        } finally {
                            IOUtils.closeQuietly(faviconStream);
                        }
                        
                        try {
                            zip.transfer(null, this);
                        } catch (Exception e) {
                            Logger.e(TAG, e);
                            throw new Exception(getString(IOUtils.isENOSPC(e) ? R.string.error_no_space : R.string.downloading_error_copy));
                        }
                        
                        List<AttachmentModel> attachments = new ArrayList<AttachmentModel>();
                        List<BadgeIconModel> icons = new ArrayList<BadgeIconModel>();
                        Set<String> iconsHashes = new HashSet<String>();
                        int threadsCount = page.threads == null ? 0 : page.threads.length;
                        for (int i=-1; i<threadsCount; ++i) {
                            PostModel[] posts = i == -1 ? page.posts : page.threads[i].posts;
                            if (posts == null) continue;
                            for (PostModel postModel : page.posts) {
                                if (postModel.attachments != null) {
                                    for (AttachmentModel attachment : postModel.attachments) {
                                        attachments.add(attachment);
                                    }
                                }
                                if (postModel.icons != null) {
                                    for (BadgeIconModel icon : postModel.icons) {
                                        String iconHash = ChanModels.hashBadgeIconModel(icon, item.chanName);
                                        if (iconsHashes.contains(iconHash)) continue;
                                        icons.add(icon);
                                        iconsHashes.add(iconHash);
                                    }
                                }
                            }
                        }
                        
                        for (int i=0; i<icons.size(); ++i) {
                            if (isCancelled()) throw new Exception();
                            BadgeIconModel icon = icons.get(i);
                            if (icon.source == null || icon.source.length() == 0) continue;
                            String hash = ChanModels.hashBadgeIconModel(icon, item.chanName);
                            String curElementName = icon.source.substring(icon.source.lastIndexOf('/') + 1);
                            if (!zip.hasFile(String.format(Locale.US, ICON_FILE_FORMAT, hash))) {
                                Bitmap bmp = bitmapCache.getFromCache(hash);
                                if (bmp == null && item.downloadingThreadMode == MODE_ONLY_CACHE) continue;
                                if (bmp == null) bmp = bitmapCache.download(hash, icon.source,
                                        getResources().getDimensionPixelSize(R.dimen.post_badge_size),
                                        MainApplication.getInstance().getChanModule(item.chanName), this);
                                if (isCancelled()) throw new Exception();
                                if (bmp != null) {
                                    OutputStream out = null;
                                    try {
                                        out = zip.openStream(String.format(Locale.US, ICON_FILE_FORMAT, hash));
                                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                                    } catch (Exception e) {
                                        Logger.e(TAG, e);
                                        if (!isCancelled()) {
                                            if (IOUtils.isENOSPC(e)) {
                                                throw new Exception(getString(R.string.error_no_space));
                                            } else {
                                                addError(item, curElementName, getString(R.string.downloading_error_copy));
                                            }
                                        }
                                    } finally {
                                        IOUtils.closeQuietly(out);
                                    }
                                } else {
                                    if (!isCancelled()) addError(item, curElementName, getString(R.string.downloading_error_download));
                                }
                            }
                        }
                        
                        for (int i=0; i<attachments.size(); ++i) {
                            if (isCancelled()) throw new Exception();
                            
                            AttachmentModel attachment = attachments.get(i);
                            String curFile = Attachments.getAttachmentLocalFileName(attachment, item.boardModel);
                            if (curFile == null) continue;
                            String curElementName = getString(R.string.downloading_element_format, item.chanName,
                                    Attachments.getAttachmentLocalShortName(attachment, item.boardModel));
                            String curThumbElementName = getString(R.string.downloading_thumbnail_format, curElementName);
                            String curHash = ChanModels.hashAttachmentModel(attachment);
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                                progressNotifBuilder.setContentText("("+i+"/"+attachments.size()+") "+elementName);
                            }
                            curProgress = Math.round(100f * i / attachments.size());
                            progressNotifBuilder.setProgress(attachments.size(), i, false);
                            notifyForeground(DOWNLOADING_NOTIFICATION_ID, progressNotifBuilder.build());
                            sendBroadcast(new Intent(BROADCAST_UPDATED));
                            
                            if (attachment.type != AttachmentModel.TYPE_OTHER_NOTFILE && !zip.hasFile(ORIGINALS_FOLDER+"/"+curFile)) {
                                File cur = new File(directory, curFile);
                                if (!cur.exists() || cur.isDirectory() || cur.length() == 0) {
                                    cur = fileCache.get(FileCache.PREFIX_ORIGINALS + ChanModels.hashAttachmentModel(attachment) +
                                            Attachments.getAttachmentExtention(attachment));
                                    if (cur != null) {
                                        String curFilename = cur.getAbsolutePath();
                                        while (downloadingLocker.isLocked(curFilename)) downloadingLocker.waitUnlock(curFilename);
                                        if (isCancelled()) throw new Exception();
                                    }
                                    if (cur == null && item.downloadingThreadMode == MODE_DOWNLOAD_ALL) {
                                        cur = fileCache.create(FileCache.PREFIX_ORIGINALS + ChanModels.hashAttachmentModel(attachment) +
                                                Attachments.getAttachmentExtention(attachment));
                                        String curFilename = cur.getAbsolutePath();
                                        while (!downloadingLocker.lock(curFilename)) downloadingLocker.waitUnlock(curFilename);
                                        if (isCancelled()) {
                                            fileCache.abort(cur);
                                            downloadingLocker.unlock(curFilename);
                                            throw new Exception();
                                        }
                                        FileOutputStream out = null;
                                        boolean success = true;
                                        try {
                                            out = new FileOutputStream(cur);
                                            MainApplication.getInstance().getChanModule(item.chanName).downloadFile(attachment.path, out, null, this);
                                            fileCache.put(cur);
                                        } catch (Exception e) {
                                            Logger.e(TAG, e);
                                            if (!isCancelled()) {
                                                if (IOUtils.isENOSPC(e)) {
                                                    throw new Exception(getString(R.string.error_no_space));
                                                } else {
                                                    addError(item, curElementName, e instanceof InteractiveException ?
                                                            getString(R.string.downloading_error_interactive_format,
                                                                    ((InteractiveException) e).getServiceName()) : getMessageOrENOSPC(e));
                                                }
                                            }
                                            success = false;
                                        } finally {
                                            if (out != null) IOUtils.closeQuietly(out);
                                            if (!success && cur != null) {
                                                fileCache.abort(cur);
                                                cur = null;
                                            }
                                            downloadingLocker.unlock(curFilename);
                                        }
                                    }
                                }
                                if (isCancelled()) throw new Exception();
                                if (cur != null) {
                                    InputStream in = null;
                                    OutputStream out = null;
                                    try {
                                        in = IOUtils.modifyInputStream(new FileInputStream(cur), null, this);
                                        out = zip.openStream(ORIGINALS_FOLDER+"/"+curFile);
                                        IOUtils.copyStream(in, out);
                                    } catch (Exception e) {
                                        Logger.e(TAG, e);
                                        if (!isCancelled()) {
                                            if (IOUtils.isENOSPC(e)) {
                                                throw new Exception(getString(R.string.error_no_space));
                                            } else {
                                                addError(item, curElementName, getString(R.string.downloading_error_copy));
                                            }
                                        }
                                    } finally {
                                        IOUtils.closeQuietly(in);
                                        IOUtils.closeQuietly(out);
                                    }
                                }
                            }
                            
                            if (isCancelled()) throw new Exception();
                            
                            if (!zip.hasFile(String.format(Locale.US, THUMBNAIL_FILE_FORMAT, curHash))) {
                                Bitmap bmp = bitmapCache.getFromCache(curHash);
                                if (bmp == null && (attachment.thumbnail == null || attachment.thumbnail.length() == 0 ||
                                        item.downloadingThreadMode == MODE_ONLY_CACHE)) continue;
                                if (bmp == null) bmp = bitmapCache.download(curHash, attachment.thumbnail,
                                        settings.getPostThumbnailSize(),
                                        MainApplication.getInstance().getChanModule(item.chanName), this);
                                if (isCancelled()) throw new Exception();
                                if (bmp != null) {
                                    OutputStream out = null;
                                    try {
                                        out = zip.openStream(String.format(Locale.US, THUMBNAIL_FILE_FORMAT, curHash));
                                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                                    } catch (Exception e) {
                                        Logger.e(TAG, e);
                                        if (!isCancelled()) {
                                            if (IOUtils.isENOSPC(e)) {
                                                throw new Exception(getString(R.string.error_no_space));
                                            } else {
                                                addError(item, curThumbElementName, getString(R.string.downloading_error_copy));
                                            }
                                        }
                                    } finally {
                                        IOUtils.closeQuietly(out);
                                    }
                                } else {
                                    if (!isCancelled()) addError(item, curThumbElementName, getString(R.string.downloading_error_download));
                                }
                            }
                        }
                        try {
                            MainApplication.getInstance().
                                    database.addSavedThread(item.chanName, pageTitle, zipFile.getAbsolutePath());
                        } catch (Exception e) {
                            Logger.e(TAG, "database exception", e);
                        }
                        
                    } catch (Exception e) {
                        Logger.e(TAG, e);
                        if (!isCancelled()) addError(item, elementName, getMessageOrENOSPC(e));
                        if (zip != null) zip.cancel();
                    } finally {
                        try {
                            if (zip != null) zip.close();
                        } catch (Exception e) {
                            if (!isCancelled()) addError(item, elementName, getString(R.string.downloading_error_save_container));
                        }
                    }
                }
            }
            currentItem = null;
            currentItemName = null;
            
            nowTaskRunning = false;
            if (!isCancelled()) {
                while (errorReport.length() > 0 && errorReport.charAt(errorReport.length()-1) == '\n') {
                    errorReport.setLength(errorReport.length()-1);
                }
                if (errorReport.length() == 0) {
                    progressNotifBuilder.setTicker(getString(R.string.downloading_success_ticker)).
                        setSmallIcon(android.R.drawable.stat_sys_download_done);
                    notifyForeground(DOWNLOADING_NOTIFICATION_ID, progressNotifBuilder.build());
                    Intent broadcast = new Intent(BROADCAST_UPDATED);
                    broadcast.putExtra(EXTRA_DOWNLOADING_REPORT, REPORT_OK);
                    sendBroadcast(broadcast);
                } else {
                    Intent intentToErrorReport = new Intent(DownloadingService.this, DownloadingErrorReportActivity.class);
                    PendingIntent pIntentToErrorReport =
                            PendingIntent.getActivity(DownloadingService.this, 0, intentToErrorReport, PendingIntent.FLAG_CANCEL_CURRENT);
                    notificationManager.notify(ERROR_REPORT_NOTIFICATION_ID, new NotificationCompat.Builder(DownloadingService.this).
                            setSmallIcon(android.R.drawable.stat_notify_error).
                            setTicker(getString(R.string.downloading_error_ticker)).
                            setContentTitle(getString(R.string.downloading_error_title)).
                            setContentText(getString(R.string.downloading_error_ticker)).
                            setContentIntent(pIntentToErrorReport).
                            setOngoing(false).
                            setAutoCancel(true).
                            setCategory(NotificationCompat.CATEGORY_ERROR).
                            build());
                    Intent broadcast = new Intent(BROADCAST_UPDATED);
                    broadcast.putExtra(EXTRA_DOWNLOADING_REPORT, REPORT_ERROR);
                    getSharedPreferences(SHARED_PREFERENCES_NAME, MODE_PRIVATE).edit().
                            putString(PREF_ERROR_REPORT, errorReport.toString()).
                            putString(PREF_ERROR_ITEMS, serializeErrorItems(errorItems)).
                            commit();
                    sendBroadcast(broadcast);
                }
            }
            errorReport.setLength(0);
            errorReport.trimToSize();
            errorItems.clear();
            errorItems.trimToSize();
            Logger.d(TAG, "stopped downloading task");
            cancelForeground(DOWNLOADING_NOTIFICATION_ID);
            stopSelf(startId);
        }
        
        public SerializablePage getSerializablePage(DownloadingQueueItem item) throws Exception {
            if (item.type != DownloadingQueueItem.TYPE_THREAD) throw new Exception();
            SerializablePage page = MainApplication.getInstance().pagesCache.getSerializablePage(ChanModels.hashUrlPageModel(item.threadUrlPage));
            if (isCancelled()) {
                throw new Exception();
            }
            if (page != null) {
                SerializablePage p = new SerializablePage(); //prevent concurrent modification
                p.pageModel = page.pageModel;
                p.boardModel = page.boardModel;
                p.posts = page.posts;
                p.threads = page.threads;
                return p;
            }
            page = new SerializablePage();
            page.pageModel = item.threadUrlPage;
            class LoaderCallback implements PageLoaderFromChan.PageLoaderCallback {
                public volatile String reason = null;
                @Override
                public void onSuccess() {
                    reason = null;
                }
                @Override
                public void onError(String message) {
                    reason = message;
                }
                @Override
                public void onInteractiveException(InteractiveException e) {
                    reason = getString(R.string.downloading_error_interactive_format, e.getServiceName());
                }
            }
            LoaderCallback cb = new LoaderCallback();
            new PageLoaderFromChan(page, cb, MainApplication.getInstance().getChanModule(item.chanName), this).run();
            if (isCancelled()) {
                throw new Exception();
            }
            if (cb.reason != null) {
                throw new Exception(cb.reason);
            }
            return page;
        }
        
        private void addError(DownloadingQueueItem item, String element, String error) {
            if (error == null) error = getString(R.string.downloading_error_unknown);
            errorReport.append(element).append('\n').append(error).append("\n\n");
            if (errorItems.size() > 0 && errorItems.get(errorItems.size()-1).equals(item)) return;
            //одинаковые item могут идти только подряд (вложения одного треда)
            errorItems.add(item);
        }
        
        private String getMessageOrENOSPC(Exception e) {
            if (IOUtils.isENOSPC(e)) return getString(R.string.error_no_space);
            return e.getMessage();
        }
        
        private void notifyMediaScanner(File file) {
            try {
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
        }
    }
    
    private static String serializeErrorItems(ArrayList<DownloadingQueueItem> list) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(new GZIPOutputStream(new Base64OutputStream(baos, Base64.DEFAULT)));
            oos.writeObject(list);
            oos.close();
            return baos.toString("US-ASCII");
        } catch (Exception e) {
            Logger.e(TAG, e);
            return "";
        }
    }
    
    @SuppressWarnings("unchecked")
    public static ArrayList<DownloadingQueueItem> deserializeErrorItems(String data) {
        try {
            ObjectInputStream ois = new ObjectInputStream(new GZIPInputStream(new ByteArrayInputStream(Base64.decode(data, Base64.DEFAULT))));
            return (ArrayList<DownloadingQueueItem>) ois.readObject();
        } catch (Exception e) {
            Logger.e(TAG, e);
            return null;
        }
    }
    
    /**
     * Класс-элемент очереди загрузок
     * @author miku-nyan
     *
     */
    public static class DownloadingQueueItem implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public static final int TYPE_ATTACHMENT = 1;
        public static final int TYPE_THREAD = 2;
        
        public final int type;
        public final AttachmentModel attachment;
        public final String subdirectory;
        public final String chanName;
        public final BoardModel boardModel;
        public final UrlPageModel threadUrlPage;
        public final int downloadingThreadMode;
        
        /**
         * Конструктор элемента загрузки - файла-вложения
         * @param attachment модель вложения
         * @param subdirectory название подпапки, в которую требуется загрузить вложение (если в общую папку - null)
         * @param boardModel модель доски, с которой скачивается вложение
         */
        public DownloadingQueueItem(AttachmentModel attachment, String subdirectory, BoardModel boardModel) {
            this.type = TYPE_ATTACHMENT;
            this.attachment = attachment;
            if (attachment == null) throw new NullPointerException();
            this.subdirectory = subdirectory;
            this.chanName = boardModel.chan;
            this.boardModel = boardModel;
            this.threadUrlPage = null;
            this.downloadingThreadMode = -1;
        }
        
        /**
         * Конструктор элемента загрузки - файла-вложения
         * @param attachment модель вложения
         * @param boardModel модель доски, с которой скачивается вложение
         */
        public DownloadingQueueItem(AttachmentModel attachment, BoardModel boardModel) {
            this(attachment, null, boardModel);
        }
        
        /**
         * Конструктор элемента загрузки - страница-весь тред
         * @param threadUrlPage модель адреса треда
         * @param downloadingThreadMode режим загрузки (загружать вложения, только минитюры, или только из кэша).
         * см. {@link DownloadingService#MODE_DOWNLOAD_ALL}, {@link DownloadingService#MODE_DOWNLOAD_THUMBS},
         * {@link DownloadingService#MODE_ONLY_CACHE} 
         */
        public DownloadingQueueItem(UrlPageModel threadUrlPage, BoardModel boardModel, int downloadingThreadMode) {
            this.type = TYPE_THREAD;
            this.attachment = null;
            this.subdirectory = null;
            this.chanName = threadUrlPage.chanName;
            this.boardModel = boardModel;
            this.threadUrlPage = threadUrlPage;
            this.downloadingThreadMode = downloadingThreadMode; 
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof DownloadingQueueItem) {
                DownloadingQueueItem cmp = (DownloadingQueueItem) o;
                if (cmp.type != type) return false;
                switch (type) {
                    case TYPE_ATTACHMENT:
                        if (!stringsEqual(cmp.subdirectory, subdirectory)) return false;
                        if (cmp.attachment == null) return attachment == null;
                        return ChanModels.hashAttachmentModel(cmp.attachment).equals(ChanModels.hashAttachmentModel(attachment));
                    case TYPE_THREAD:
                        if (cmp.threadUrlPage == null) return threadUrlPage == null;
                        return ChanModels.hashUrlPageModel(cmp.threadUrlPage).equals(ChanModels.hashUrlPageModel(threadUrlPage));
                }
            }
            return false;
        }
        
        private static boolean stringsEqual(String s1, String s2) {
            if (s1 == s2) return true;
            if (s1 == null) return s2 == null;
            return s1.equals(s2);
        }
        
        @Override
        public int hashCode() {
            return 0;
        }
    }
    
    public static class DownloadingServiceBinder extends Binder {
        private final WeakReference<DownloadingService> service;
        private DownloadingServiceBinder(DownloadingService service) {
            this.service = new WeakReference<>(service);
        }
        public void cancel() {
            DownloadingService service = this.service.get();
            if (service == null) return;
            if (service.currentTask != null) service.currentTask.cancel();
            if (!service.downloadingQueue.isEmpty()) service.downloadingQueue.clear();
        }
        public int getCurrentProgress() {
            DownloadingService service = this.service.get();
            if (service == null) return -1;
            if (service.currentTask == null) return -1; 
            return service.currentTask.getCurrentProgress();
        }
        public int getQueueSize() {
            DownloadingService service = this.service.get();
            if (service == null) return 0;
            if (service.downloadingQueue == null) return 0;
            return service.downloadingQueue.size();
        }
        public String getCurrentItemName() {
            DownloadingService service = this.service.get();
            if (service == null) return null;
            if (service.currentTask == null) return null;
            return service.currentTask.getCurrentItemName();
        }
    }
}
