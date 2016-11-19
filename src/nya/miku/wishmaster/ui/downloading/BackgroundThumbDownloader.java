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

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.cache.BitmapCache;
import nya.miku.wishmaster.cache.SerializablePage;
import nya.miku.wishmaster.common.Async;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.ui.settings.ApplicationSettings;

public class BackgroundThumbDownloader implements Runnable {
    private static final Executor EXECUTOR = Executors.newSingleThreadExecutor(Async.LOW_PRIORITY_FACTORY);

    private static final String TAG = "NotLazyDownloader";
    
    private final SerializablePage page;
    private final CancellableTask task;
    private final ApplicationSettings settings;
    private final BitmapCache cache;
    private final int maxSize;
    
    private ChanModule chan = null;
    
    public static void download(SerializablePage page, CancellableTask task) {
        if (MainApplication.getInstance().settings.isLazyDownloading()) return;
        if (task == null || task.isCancelled()) return;
        EXECUTOR.execute(new BackgroundThumbDownloader(page, task));
    }
    
    private BackgroundThumbDownloader(SerializablePage page, CancellableTask task) {
        this.page = page;
        this.task = task;
        this.settings = MainApplication.getInstance().settings;
        this.cache = MainApplication.getInstance().bitmapCache;
        this.maxSize = settings.getPostThumbnailSize();
    }
    
    @Override
    public void run() {
        if (task == null || task.isCancelled() || settings.isLazyDownloading()) return;
        if (page == null || page.boardModel == null || page.boardModel.chan == null) return;
        chan = MainApplication.getInstance().getChanModule(page.boardModel.chan);
        if (chan == null) return;
        
        try {
            if (page.posts != null)
                for (PostModel post : page.posts)
                    if (post.attachments != null)
                        for (AttachmentModel attachment : post.attachments)
                            load(attachment);
            if (page.threads != null)
                for (ThreadModel thread : page.threads)
                    if (thread.posts != null)
                        for (PostModel post : thread.posts)
                            if (post.attachments != null)
                                for (AttachmentModel attachment : post.attachments)
                                    load(attachment);
        } catch (InterruptedException e) {
            Logger.e(TAG, e);
        }
    }
    
    private void load(AttachmentModel attachment) throws InterruptedException {
        if (attachment.thumbnail == null) return;
        if (task.isCancelled() || settings.isLazyDownloading()) throw new InterruptedException();
        String hash = ChanModels.hashAttachmentModel(attachment);
        if (cache.isInCache(hash)) return;
        cache.download(hash, attachment.thumbnail, maxSize, chan, task);
        if (task.isCancelled() || settings.isLazyDownloading()) throw new InterruptedException();
    }
}
