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

package nya.miku.wishmaster.ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.tuple.Triple;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.cache.BitmapCache;
import nya.miku.wishmaster.cache.FileCache;
import nya.miku.wishmaster.common.CompatibilityImpl;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.common.PriorityThreadFactory;
import nya.miku.wishmaster.containers.ReadableContainer;
import nya.miku.wishmaster.http.interactive.InteractiveException;
import nya.miku.wishmaster.http.streamer.HttpRequestException;
import nya.miku.wishmaster.lib.gallery.FixedSubsamplingScaleImageView;
import nya.miku.wishmaster.lib.gallery.Jpeg;
import nya.miku.wishmaster.lib.gallery.TouchGifView;
import nya.miku.wishmaster.lib.gallery.WebViewFixed;
import nya.miku.wishmaster.lib.gifdrawable.GifDrawable;
import nya.miku.wishmaster.ui.downloading.DownloadingLocker;
import nya.miku.wishmaster.ui.downloading.DownloadingService;
import nya.miku.wishmaster.ui.presentation.BoardFragment;
import nya.miku.wishmaster.ui.presentation.PresentationModel;
import nya.miku.wishmaster.ui.presentation.ThemeUtils;
import nya.miku.wishmaster.ui.settings.ApplicationSettings;
import nya.miku.wishmaster.ui.tabs.TabModel;
import nya.miku.wishmaster.ui.tabs.TabsState;
import nya.miku.wishmaster.ui.tabs.TabsSwitcher;
import nya.miku.wishmaster.ui.tabs.UrlHandler;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

public class GalleryActivity extends Activity implements View.OnClickListener {
    private static final String TAG = "GalleryActivity";
    
    public static final String EXTRA_ATTACHMENT = "attachment";
    public static final String EXTRA_BOARDMODEL = "boardmodel";
    public static final String EXTRA_PAGEHASH = "pagehash";
    public static final String EXTRA_LOCALFILENAME = "localfilename";
    
    private DownloadingLocker downloadingLocker;
    private LayoutInflater inflater;
    private CancellableTask tnDownloadingTask;
    private Executor tnDownloadingExecutor;
    
    private BoardModel boardModel;
    private ReadableContainer localFile;
    
    private ProgressBar progressBar;
    private ViewPager viewPager;
    private TextView navigationInfo;
    private SparseArray<View> instantiatedViews;
    
    private ChanModule chan;
    private ApplicationSettings settings;
    private FileCache fileCache;
    private BitmapCache bitmapCache;
    private List<Triple<AttachmentModel, String, String>> attachments = null;
    private int currentPosition = 0;
    private int previousPosition = -1;
    
    private String customSubdir = null;
    
    private boolean firstScroll = true;
    
    private Menu menu;
    private boolean currentLoaded;
    
    private static class ProgressHandler extends Handler {
        private final WeakReference<GalleryActivity> reference;
        
        public ProgressHandler(GalleryActivity activity) {
            reference = new WeakReference<GalleryActivity>(activity);
        }
        
        @Override
        public void handleMessage(Message msg) {
            GalleryActivity activity = reference.get();
            if (activity == null) return;
            int progress = msg.arg1;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (progress != Window.PROGRESS_END) {
                    if (activity.progressBar.getVisibility() == View.GONE) activity.progressBar.setVisibility(View.VISIBLE);
                    activity.progressBar.setProgress(progress);
                } else {
                    if (activity.progressBar.getVisibility() == View.VISIBLE) activity.progressBar.setVisibility(View.GONE);
                }
            } else {
                activity.setProgress(progress);
            }
        }
    }
    
    private ProgressListener progressListener = new ProgressListener() {
        private long maxValue = Window.PROGRESS_END;
        private Handler progressHandler = new ProgressHandler(GalleryActivity.this);
        
        @Override
        public void setProgress(final long value) {
            progressHandler.obtainMessage(0, (int)(Window.PROGRESS_END * value / maxValue), 0).sendToTarget();
        }
        
        @Override
        public void setMaxValue(long value) {
            if (value > 0) maxValue = value;
        }
        
        @Override
        public void setIndeterminate() {
        }
        
    };
    
    private void hideProgress() {
        progressListener.setMaxValue(1);
        progressListener.setProgress(1);
    }
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settings = MainApplication.getInstance().settings;
        setTheme(settings.getTheme());
        getTheme().applyStyle(R.style.Transparent, true);
        
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_PROGRESS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) CompatibilityImpl.setActionBarNoIcon(this);
        
        downloadingLocker = MainApplication.getInstance().downloadingLocker;
        inflater = getLayoutInflater();
        instantiatedViews = new SparseArray<View>();
        tnDownloadingTask = new CancellableTask.BaseCancellableTask();
        tnDownloadingExecutor = Executors.newFixedThreadPool(4, PriorityThreadFactory.LOW_PRIORITY_FACTORY);
        fileCache = MainApplication.getInstance().fileCache;
        bitmapCache = MainApplication.getInstance().bitmapCache;
        
        AttachmentModel attachment = (AttachmentModel) getIntent().getSerializableExtra(EXTRA_ATTACHMENT);
        boardModel = (BoardModel) getIntent().getSerializableExtra(EXTRA_BOARDMODEL);
        if (boardModel == null) return;
        String pagehash = getIntent().getStringExtra(EXTRA_PAGEHASH);
        String localFilename = getIntent().getStringExtra(EXTRA_LOCALFILENAME);
        if (localFilename != null) {
            try {
                localFile = ReadableContainer.obtain(new File(localFilename));
            } catch (Exception e) {
                Logger.e(TAG, "cannot open local file", e);
            }
        }
        
        chan = MainApplication.getInstance().getChanModule(boardModel.chan);
        PresentationModel presentationModel = MainApplication.getInstance().pagesCache.getPresentationModel(pagehash);
        if (presentationModel != null) {
            boolean isThread = presentationModel.source.pageModel.type == UrlPageModel.TYPE_THREADPAGE;
            customSubdir = BoardFragment.getCustomSubdir(presentationModel.source.pageModel);
            List<Triple<AttachmentModel, String, String>> list = presentationModel.getAttachments();
            presentationModel = null;
            if (list != null) {
                int index = -1;
                String attachmentHash = ChanModels.hashAttachmentModel(attachment);
                for (int i=0; i<list.size(); ++i) {
                    if (list.get(i).getMiddle().equals(attachmentHash)) {
                        index = i;
                        break;
                    }
                }
                if (index != -1) {
                    if (isThread) {
                        attachments = list;
                        currentPosition = index;
                    } else {
                        int leftOffset = 0, rightOffset = 0;
                        String threadNumber = list.get(index).getRight();
                        int it = index; while (it > 0 && list.get(--it).getRight().equals(threadNumber)) ++leftOffset;
                        it = index; while (it < (list.size()-1) && list.get(++it).getRight().equals(threadNumber)) ++rightOffset;
                        attachments = list.subList(index - leftOffset, index + rightOffset + 1);
                        currentPosition = leftOffset;
                    }
                }
            }
        }
        if (attachments == null) {
            attachments = Collections.singletonList(Triple.of(attachment, ChanModels.hashAttachmentModel(attachment), (String)null));
            currentPosition = 0;
        }
        
        setContentView(R.layout.gallery_layout);
        progressBar = (ProgressBar) findViewById(android.R.id.progress);
        progressBar.setMax(Window.PROGRESS_END);
        viewPager = (ViewPager) findViewById(R.id.gallery_viewpager);
        navigationInfo = (TextView) findViewById(R.id.gallery_navigation_info);
        for (int id : new int[] { R.id.gallery_navigation_previous, R.id.gallery_navigation_next }) findViewById(id).setOnClickListener(this);
        viewPager.setAdapter(new GalleryAdapter());
        viewPager.setCurrentItem(currentPosition);
        viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                currentPosition = position;
                updateItem();
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tnDownloadingTask != null) tnDownloadingTask.cancel();
        if (instantiatedViews != null) {
            for (int i=0; i<instantiatedViews.size(); ++i) {
                View v = instantiatedViews.valueAt(i);
                if (v != null) {
                    Object tag = v.getTag();
                    if (tag != null && tag instanceof GalleryItemViewTag) {
                        recycleTag((GalleryItemViewTag) tag, true);
                    }
                }
            }
        }
        if (localFile != null) {
            try {
                if (localFile != null) localFile.close();
            } catch (Exception e) {
                Logger.e(TAG, "cannot close local file", e);
            }
        }
    }
    
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.gallery_navigation_previous:
                if (currentPosition > 0) {
                    viewPager.setCurrentItem(--currentPosition);
                    updateItem();
                }
                break;
            case R.id.gallery_navigation_next:
                if (currentPosition < attachments.size() - 1) {
                    viewPager.setCurrentItem(++currentPosition);
                    updateItem();
                }
                break;
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;
        MenuItem itemUpdate = menu.add(Menu.NONE, R.id.menu_update, 1, R.string.menu_update);
        MenuItem itemSave = menu.add(Menu.NONE, R.id.menu_save_attachment, 2, R.string.menu_save_attachment);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            itemUpdate.setIcon(ThemeUtils.getThemeResId(getTheme(), R.attr.actionRefresh));
            itemSave.setIcon(ThemeUtils.getThemeResId(getTheme(), R.attr.actionSave));
            CompatibilityImpl.setShowAsActionIfRoom(itemUpdate);
            CompatibilityImpl.setShowAsActionIfRoom(itemSave);
        } else {
            itemUpdate.setIcon(R.drawable.ic_menu_refresh);
            itemSave.setIcon(android.R.drawable.ic_menu_save);
        }
        menu.add(Menu.NONE, R.id.menu_open_external, 3, R.string.menu_open).setIcon(R.drawable.ic_menu_set_as);
        menu.add(Menu.NONE, R.id.menu_share, 4, R.string.menu_share).setIcon(android.R.drawable.ic_menu_share);
        menu.add(Menu.NONE, R.id.menu_share_link, 5, R.string.menu_share_link).setIcon(android.R.drawable.ic_menu_share);
        menu.add(Menu.NONE, R.id.menu_search_google, 6, R.string.menu_search_google).setIcon(android.R.drawable.ic_menu_search);
        menu.add(Menu.NONE, R.id.menu_open_browser, 7, R.string.menu_open_browser).setIcon(R.drawable.ic_menu_browser);
        updateMenu();
        
        return true;
    }
    
    private void updateMenu() {
        if (this.menu == null) return;
        View current = instantiatedViews.get(currentPosition);
        if (current == null) {
            Logger.e(TAG, "VIEW == NULL");
            return;
        }
        GalleryItemViewTag tag = (GalleryItemViewTag) current.getTag();
        boolean externalVideo = tag.attachmentModel.type == AttachmentModel.TYPE_VIDEO && settings.doNotDownloadVideos();
        menu.findItem(R.id.menu_update).setVisible(!currentLoaded);
        menu.findItem(R.id.menu_save_attachment).setVisible(externalVideo ||
                (currentLoaded && tag.attachmentModel.type != AttachmentModel.TYPE_OTHER_NOTFILE));
        menu.findItem(R.id.menu_open_external).setVisible(currentLoaded && (tag.attachmentModel.type == AttachmentModel.TYPE_OTHER_FILE ||
                tag.attachmentModel.type == AttachmentModel.TYPE_AUDIO || tag.attachmentModel.type == AttachmentModel.TYPE_VIDEO));
        menu.findItem(R.id.menu_open_external).setTitle(tag.attachmentModel.type != AttachmentModel.TYPE_OTHER_FILE ?
                R.string.menu_open_player : R.string.menu_open);
        menu.findItem(R.id.menu_share).setVisible(currentLoaded && tag.attachmentModel.type != AttachmentModel.TYPE_OTHER_NOTFILE);
        menu.findItem(R.id.menu_search_google).setVisible(
                tag.attachmentModel.type == AttachmentModel.TYPE_IMAGE_STATIC || tag.attachmentModel.type == AttachmentModel.TYPE_IMAGE_GIF);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_update:
                updateItem();
                return true;
            case R.id.menu_save_attachment:
                downloadAttachment();
                return true;
            case R.id.menu_open_external:
                openExternal();
                return true;
            case R.id.menu_share:
                share();
                return true;
            case R.id.menu_share_link:
                shareLink();
                return true;
            case R.id.menu_search_google:
                openGoogle();
                return true;
            case R.id.menu_open_browser:
                openBrowser();
                return true;
        }
        return false;
    }
    
    private GalleryItemViewTag getCurrentTag() {
        View current = instantiatedViews.get(currentPosition);
        if (current == null) {
            Logger.e(TAG, "VIEW == NULL");
            return null;
        }
        return (GalleryItemViewTag) current.getTag();
    }
    
    private void downloadAttachment() {
        GalleryItemViewTag tag = getCurrentTag();
        if (tag == null) return;
        DownloadingService.DownloadingQueueItem queueItem = new DownloadingService.DownloadingQueueItem(tag.attachmentModel, boardModel);
        String fileName = Attachments.getAttachmentLocalFileName(tag.attachmentModel, boardModel);
        String itemName = Attachments.getAttachmentLocalShortName(tag.attachmentModel, boardModel);
        if (DownloadingService.isInQueue(queueItem)) {
            Toast.makeText(this, getString(R.string.notification_download_already_in_queue, itemName), Toast.LENGTH_LONG).show();
        } else {
            if (new File(new File(settings.getDownloadDirectory(), chan.getChanName()), fileName).exists()) {
                Toast.makeText(this, getString(R.string.notification_download_already_exists, fileName), Toast.LENGTH_LONG).show();
            } else {
                Intent downloadIntent = new Intent(this, DownloadingService.class);
                downloadIntent.putExtra(DownloadingService.EXTRA_DOWNLOADING_ITEM, queueItem);
                startService(downloadIntent);
            }
        }
    }
    
    private void openExternal() {
        GalleryItemViewTag tag = getCurrentTag();
        if (tag == null) return;
        String mime;
        switch (tag.attachmentModel.type) {
            case AttachmentModel.TYPE_VIDEO:
                mime = "video/*";
                break;
            case AttachmentModel.TYPE_AUDIO:
                mime = "audio/*";
                break;
            default:
                mime = "*/*";
                break;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(tag.file), mime);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
    
    private void share() {
        GalleryItemViewTag tag = getCurrentTag();
        if (tag == null) return;
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        String extension = Attachments.getAttachmentExtention(tag.attachmentModel);
        switch (tag.attachmentModel.type) {
            case AttachmentModel.TYPE_IMAGE_GIF:
                shareIntent.setType("image/gif");
                break;
            case AttachmentModel.TYPE_IMAGE_STATIC:
                if (extension.equalsIgnoreCase(".png")) {
                    shareIntent.setType("image/png");
                } else if (extension.equalsIgnoreCase(".jpg") || extension.equalsIgnoreCase(".jpg")) {
                    shareIntent.setType("image/jpeg");
                } else {
                    shareIntent.setType("image/*");
                }
                break;
            case AttachmentModel.TYPE_VIDEO:
                if (extension.equalsIgnoreCase(".mp4")) {
                    shareIntent.setType("video/mp4");
                } else if (extension.equalsIgnoreCase(".webm")) {
                    shareIntent.setType("video/webm");
                } else if (extension.equalsIgnoreCase(".avi")) {
                    shareIntent.setType("video/avi");
                } else if (extension.equalsIgnoreCase(".mov")) {
                    shareIntent.setType("video/quicktime");
                } else if (extension.equalsIgnoreCase(".mkv")) {
                    shareIntent.setType("video/x-matroska");
                } else if (extension.equalsIgnoreCase(".flv")) {
                    shareIntent.setType("video/x-flv");
                } else if (extension.equalsIgnoreCase(".wmv")) {
                    shareIntent.setType("video/x-ms-wmv");
                } else {
                    shareIntent.setType("video/*");
                }
                break;
            case AttachmentModel.TYPE_AUDIO:
                if (extension.equalsIgnoreCase(".mp3")) {
                    shareIntent.setType("audio/mpeg");
                } else if (extension.equalsIgnoreCase(".mp4")) {
                    shareIntent.setType("audio/mp4");
                } else if (extension.equalsIgnoreCase(".ogg")) {
                    shareIntent.setType("audio/ogg");
                } else if (extension.equalsIgnoreCase(".webm")) {
                    shareIntent.setType("audio/webm");
                } else if (extension.equalsIgnoreCase(".flac")) {
                    shareIntent.setType("audio/flac");
                } else if (extension.equalsIgnoreCase(".wav")) {
                    shareIntent.setType("audio/vnd.wave");
                } else {
                    shareIntent.setType("audio/*");
                }
                break;
            case AttachmentModel.TYPE_OTHER_FILE:
                shareIntent.setType("application/octet-stream");
                break;
        }
        Logger.d(TAG, shareIntent.getType());
        shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(tag.file));
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)));
    }
    
    private void shareLink() {
        GalleryItemViewTag tag = getCurrentTag();
        if (tag == null) return;
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, chan.fixRelativeUrl(tag.attachmentModel.path));
        shareIntent.putExtra(Intent.EXTRA_TEXT, chan.fixRelativeUrl(tag.attachmentModel.path));
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)));
    }
    
    private void openGoogle() {
        GalleryItemViewTag tag = getCurrentTag();
        if (tag == null) return;
        String googleUrl = "http://www.google.com/searchbyimage?image_url=" + chan.fixRelativeUrl(tag.attachmentModel.path);
        UrlHandler.launchExternalBrowser(this, googleUrl);
    }
    
    private void openBrowser() {
        GalleryItemViewTag tag = getCurrentTag();
        if (tag == null) return;
        UrlHandler.launchExternalBrowser(this, chan.fixRelativeUrl(tag.attachmentModel.path));
    }
    
    private class GalleryAdapter extends PagerAdapter {
        private boolean firstTime = true;
        
        @Override
        public int getCount() {
            return attachments.size();
        }
        
        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }
        
        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View v = inflater.inflate(R.layout.gallery_item, container, false);
            GalleryItemViewTag tag = new GalleryItemViewTag();
            tag.attachmentModel = attachments.get(position).getLeft();
            tag.attachmentHash = attachments.get(position).getMiddle();
            tag.thumbnailView = (ImageView) v.findViewById(R.id.gallery_thumbnail_preview);
            
            int tnWidth = Math.min(container.getMeasuredWidth(), tag.attachmentModel.width * 2);
            if (tnWidth > 0) tag.thumbnailView.getLayoutParams().width = tnWidth;
            
            tag.layout = (FrameLayout) v.findViewById(R.id.gallery_item_layout);
            tag.errorView = v.findViewById(R.id.gallery_error);
            tag.errorText = (TextView) tag.errorView.findViewById(R.id.frame_error_text);
            tag.errorText.setTextColor(Color.WHITE);
            tag.loadingView = v.findViewById(R.id.gallery_loading);
            v.setTag(tag);
            instantiatedViews.put(position, v);
            
            String hash = tag.attachmentHash;
            Bitmap bmp = bitmapCache.getFromMemory(hash);
            if (bmp != null) {
                tag.thumbnailView.setImageBitmap(bmp);
            } else {
                tnDownloadingExecutor.execute(new AsyncThumbnailDownloader(position, hash, tag.attachmentModel.thumbnail));
            }
            container.addView(v);
            if (firstTime) {
                updateItem();
                firstTime = false;
            }
            return v;
        }
        
        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            View v = (View) object;
            GalleryItemViewTag tag = (GalleryItemViewTag) v.getTag();
            if (tag != null) recycleTag(tag, true);
            container.removeView(v);
            instantiatedViews.delete(position);
        }
        
        private class AsyncThumbnailDownloader implements Runnable {
            private final int position;
            private final String hash;
            private final String url;
            
            public AsyncThumbnailDownloader(int position, String hash, String url) {
                this.position = position;
                this.hash = hash;
                this.url = url;
            }
            
            @Override
            public void run() {
                Bitmap bmp = bitmapCache.getFromCache(hash);
                if (bmp == null && localFile != null) {
                    bmp = bitmapCache.getFromContainer(hash, localFile);
                }
                if (bmp == null && url != null && url.length() != 0) {
                    bmp = bitmapCache.download(hash, url, getResources().getDimensionPixelSize(R.dimen.post_thumbnail_size), chan, tnDownloadingTask);
                }
                if (tnDownloadingTask.isCancelled()) return;
                if (bmp != null) {
                    View v = instantiatedViews.get(position);
                    if (v != null) {
                        final ImageView tnView = ((GalleryItemViewTag) v.getTag()).thumbnailView;
                        final Bitmap bmpSet = bmp;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (tnView != null) {
                                    tnView.setImageBitmap(bmpSet);
                                }
                            }
                        });
                    }
                }
            }
        }
    }
    
    private void tryScrollParent(String attachmentHash) {
        try {
            TabsState tabsState = MainApplication.getInstance().tabsState;
            TabsSwitcher tabsSwitcher = MainApplication.getInstance().tabsSwitcher;
            if (tabsSwitcher.currentFragment instanceof BoardFragment) {
                TabModel tab = tabsState.findTabById(tabsSwitcher.currentId);
                if (tab != null && tab.pageModel != null && tab.pageModel.type == UrlPageModel.TYPE_THREADPAGE) {
                    ((BoardFragment) tabsSwitcher.currentFragment).scrollToItem(attachmentHash);
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
    }
    
    private void updateItem() {
        AttachmentModel attachment = attachments.get(currentPosition).getLeft();
        if (settings.scrollThreadFromGallery() && !firstScroll) tryScrollParent(attachments.get(currentPosition).getRight());
        firstScroll = false;
        String navText = attachment.size == -1 ? (currentPosition + 1) + "/" + attachments.size() :
                (currentPosition + 1) + "/" + attachments.size() + " (" + Attachments.getAttachmentSizeString(attachment, getResources()) + ")";
        navigationInfo.setText(navText);
        setTitle(Attachments.getAttachmentDisplayName(attachment));
        
        if (previousPosition != -1) {
            View previous = instantiatedViews.get(previousPosition);
            if (previous != null) {
                GalleryItemViewTag tag = (GalleryItemViewTag) previous.getTag();
                tag.thumbnailView.setVisibility(View.VISIBLE);
                tag.layout.setVisibility(View.GONE);
                tag.errorView.setVisibility(View.GONE);
                tag.loadingView.setVisibility(View.GONE);
                recycleTag(tag, true);
            }
        }
        previousPosition = currentPosition;
        
        GalleryItemViewTag tag = getCurrentTag();
        if (tag == null) return;
        currentLoaded = false;
        updateMenu();
        tag.downloadingTask = new AttachmentGetter(tag);
        tag.loadingView.setVisibility(View.VISIBLE);
        hideProgress();
        PriorityThreadFactory.LOW_PRIORITY_FACTORY.newThread((Runnable) tag.downloadingTask).start();
    }
    
    private class AttachmentGetter extends CancellableTask.BaseCancellableTask implements Runnable {
        private final GalleryItemViewTag tag;
        public AttachmentGetter(GalleryItemViewTag tag) {
            this.tag = tag;
        }
        
        @Override
        public void run() {
            if (tag.attachmentModel.type == AttachmentModel.TYPE_OTHER_NOTFILE ||
                    (settings.doNotDownloadVideos() && tag.attachmentModel.type == AttachmentModel.TYPE_VIDEO)) {
                setExternalLink(tag);
                return;
            } else if (tag.attachmentModel.path == null || tag.attachmentModel.path.length() == 0) {
                showError(tag, getString(R.string.gallery_error_incorrect_attachment));
                return;
            }
            File file = fileCache.get(FileCache.PREFIX_ORIGINALS + tag.attachmentHash + Attachments.getAttachmentExtention(tag.attachmentModel));
            if (file != null) {
                String filename = file.getAbsolutePath();
                while (downloadingLocker.isLocked(filename)) Thread.yield();
                if (isCancelled()) return;
            }
            if (file == null || !file.exists() || file.isDirectory() || file.length() == 0) {
                File dir = new File(settings.getDownloadDirectory(), chan.getChanName());
                file = new File(dir, Attachments.getAttachmentLocalFileName(tag.attachmentModel, boardModel));
                String filename = file.getAbsolutePath();
                while (downloadingLocker.isLocked(filename)) Thread.yield();
                if (isCancelled()) return;
            }
            if (customSubdir != null) {
                if (file == null || !file.exists() || file.isDirectory() || file.length() == 0) {
                    File dir = new File(settings.getDownloadDirectory(), chan.getChanName());
                    dir = new File(dir, customSubdir);
                    file = new File(dir, Attachments.getAttachmentLocalFileName(tag.attachmentModel, boardModel));
                    String filename = file.getAbsolutePath();
                    while (downloadingLocker.isLocked(filename)) Thread.yield();
                    if (isCancelled()) return;
                }
            }
            if (!file.exists() || file.isDirectory() || file.length() == 0) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tag.loadingView.setVisibility(View.VISIBLE);
                    }
                });
                file = fileCache.create(FileCache.PREFIX_ORIGINALS + tag.attachmentHash + Attachments.getAttachmentExtention(tag.attachmentModel));
                String filename = file.getAbsolutePath();
                while (!downloadingLocker.lock(filename)) Thread.yield();
                InputStream fromLocal = null;
                OutputStream out = null;
                boolean success = false;
                try {
                    out = new FileOutputStream(file);
                    String localName = DownloadingService.ORIGINALS_FOLDER + "/" +
                            Attachments.getAttachmentLocalFileName(tag.attachmentModel, boardModel);
                    if (localFile != null && localFile.hasFile(localName)) {
                        fromLocal = IOUtils.modifyInputStream(localFile.openStream(localName), null, this);
                        IOUtils.copyStream(fromLocal, out);
                    } else {
                        chan.downloadFile(tag.attachmentModel.path, out, progressListener, this);
                    }
                    fileCache.put(file);
                    success = true;
                } catch (final Exception e) {
                    if (isCancelled()) return;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isCancelled()) return;
                            hideProgress();
                        }
                    });
                    if (e instanceof InteractiveException) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (isCancelled()) return;
                                String cfMessage = getString(R.string.error_interactive_dialog_format, ((InteractiveException)e).getServiceName());
                                final ProgressDialog cfProgressDialog = ProgressDialog.show(
                                        GalleryActivity.this, null, cfMessage, true, true, new DialogInterface.OnCancelListener() {
                                    @Override
                                    public void onCancel(DialogInterface dialog) {
                                        String m = getString(R.string.error_interactive_cancelled_format, ((InteractiveException)e).getServiceName());
                                        showError(tag, m);
                                        AttachmentGetter.this.cancel();
                                    }
                                });
                                PriorityThreadFactory.LOW_PRIORITY_FACTORY.newThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        ((InteractiveException) e).handle(GalleryActivity.this, AttachmentGetter.this,
                                                new InteractiveException.Callback() {
                                            @Override
                                            public void onSuccess() {
                                                if (isCancelled()) return;
                                                cfProgressDialog.dismiss();
                                                updateItem();
                                            }
                                            @Override
                                            public void onError(String message) {
                                                if (isCancelled()) return;
                                                cfProgressDialog.dismiss();
                                                showError(tag, message);
                                            }
                                        });
                                    }
                                }).start();
                            }
                        });
                    } else if (IOUtils.isENOSPC(e)) {
                        showError(tag, getString(R.string.error_no_space));
                    } else if (e instanceof HttpRequestException) {
                        if (((HttpRequestException) e).isSslException()) {
                            showError(tag, getString(R.string.error_ssl));
                        } else {
                            showError(tag, getString(R.string.error_connection));
                        }
                    } else {
                        showError(tag, e.getMessage());
                    }
                    return;
                } finally {
                    IOUtils.closeQuietly(fromLocal);
                    IOUtils.closeQuietly(out);
                    if (file != null && !success) file.delete();
                    downloadingLocker.unlock(filename);
                }
            }
            if (isCancelled()) return;
            tag.file = file;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (isCancelled()) return;
                    hideProgress();
                    currentLoaded = true;
                    updateMenu();
                }
            });
            switch (tag.attachmentModel.type) {
                case AttachmentModel.TYPE_IMAGE_STATIC:
                    setStaticImage(tag, file);
                    break;
                case AttachmentModel.TYPE_IMAGE_GIF:
                    setGif(tag, file);
                    break;
                case AttachmentModel.TYPE_VIDEO:
                    setVideo(tag, file);
                    break;
                case AttachmentModel.TYPE_AUDIO:
                    setAudio(tag, file);
                    break;
                case AttachmentModel.TYPE_OTHER_FILE:
                    setOtherFile(tag, file);
                    break;
            }
        }
        
    }
    
    private void showError(final GalleryItemViewTag tag, final String message) {
        if (tag.downloadingTask.isCancelled()) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (tag.downloadingTask.isCancelled()) return;
                hideProgress();
                tag.layout.setVisibility(View.GONE);
                recycleTag(tag, true);
                tag.thumbnailView.setVisibility(View.GONE);
                tag.loadingView.setVisibility(View.GONE);
                tag.errorView.setVisibility(View.VISIBLE);
                tag.errorText.setText(fixErrorMessage(message));
            }
            private String fixErrorMessage(String message) {
                if (message == null || message.length() == 0) {
                    return getString(R.string.error_unknown);
                }
                if (message.equals(getString(R.string.error_ssl))) message += getString(R.string.error_ssl_help);
                return message;
            }
        });
    }
    
    private void recycleTag(GalleryItemViewTag tag, boolean cancelTask) {
        if (tag.layout != null) {
            for (int i=0; i<tag.layout.getChildCount(); ++i) {
                View v = tag.layout.getChildAt(i);
                if (v instanceof FixedSubsamplingScaleImageView) {
                    ((FixedSubsamplingScaleImageView) v).recycle();
                } else if (v != null && v.getId() == R.id.gallery_video_container) {
                    try {
                        ((VideoView) v.findViewById(R.id.gallery_video_view)).stopPlayback();
                    } catch (Exception e) {
                        Logger.e(TAG, "cannot release videoview", e);
                    }
                } else if (v != null) {
                    Object gifTag = v.getTag();
                    if (gifTag != null && gifTag instanceof GifDrawable) {
                        ((GifDrawable) gifTag).recycle();
                    }
                }
            }
            tag.layout.removeAllViews();
        }
        
        if (cancelTask && tag.downloadingTask != null) tag.downloadingTask.cancel();
        if (tag.timer != null) tag.timer.cancel();
        if (tag.audioPlayer != null) {
            try {
                tag.audioPlayer.release();
            } catch (Exception e) {
                Logger.e(TAG, "cannot release audio mediaplayer", e);
            } finally {
                tag.audioPlayer = null;
            }
        }
        
        System.gc();
    }
    
    private void setStaticImage(final GalleryItemViewTag tag, final File file) {
        if (!settings.useScaleImageView() || Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD_MR1 || Jpeg.isNonStandardGrayscaleImage(file)) {
            setWebView(tag, file);
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    FixedSubsamplingScaleImageView iv = new FixedSubsamplingScaleImageView(GalleryActivity.this);
                    iv.setInitCallback(new FixedSubsamplingScaleImageView.InitedCallback() {
                        @Override
                        public void onInit() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    tag.thumbnailView.setVisibility(View.GONE);
                                    tag.loadingView.setVisibility(View.GONE);
                                }
                            });
                        }
                    });
                    iv.setImageFile(file.getAbsolutePath(), new FixedSubsamplingScaleImageView.FailedCallback() {
                        @Override
                        public void onFail() {
                            setWebView(tag, file);
                        }
                    });
                    if (tag.downloadingTask.isCancelled()) return;
                    tag.layout.setVisibility(View.VISIBLE);
                    tag.layout.addView(iv);
                } catch (Throwable t) {
                    System.gc();
                    Logger.e(TAG, t);
                    if (tag.downloadingTask.isCancelled()) return;
                    setWebView(tag, file);
                }
            }
        });
    }
    
    private void setGif(final GalleryItemViewTag tag, final File file) {
        if (!settings.useNativeGif()) {
            setWebView(tag, file);
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ImageView iv = Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO ?
                        new ImageView(GalleryActivity.this) : new TouchGifView(GalleryActivity.this);
                try {
                    GifDrawable drawable = new GifDrawable(file);
                    iv.setTag(drawable);
                    iv.setImageDrawable(drawable);
                } catch (Throwable e) {
                    System.gc();
                    Logger.e(TAG, "cannot init GifDrawable", e);
                    if (tag.downloadingTask.isCancelled()) return;
                    setWebView(tag, file);
                    return;
                }
                
                if (tag.downloadingTask.isCancelled()) return;
                
                tag.thumbnailView.setVisibility(View.GONE);
                tag.loadingView.setVisibility(View.GONE);
                
                tag.layout.setVisibility(View.VISIBLE);
                tag.layout.addView(iv);
            }
        });
    }
    
    private void setVideo(final GalleryItemViewTag tag, final File file) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setOnClickView(tag, getString(R.string.gallery_tap_to_play), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!settings.useInternalVideoPlayer()) {
                            openExternal();
                        } else {
                            recycleTag(tag, false);
                            tag.thumbnailView.setVisibility(View.GONE);
                            View videoContainer = inflater.inflate(R.layout.gallery_videoplayer, tag.layout);
                            final VideoView videoView = (VideoView)videoContainer.findViewById(R.id.gallery_video_view);
                            final TextView durationView = (TextView)videoContainer.findViewById(R.id.gallery_video_duration);
                            
                            videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                                @Override
                                public void onPrepared(final MediaPlayer mp) {
                                    mp.setLooping(true);
                                    
                                    durationView.setText("00:00 / " + formatMediaPlayerTime(mp.getDuration()));
                                    
                                    tag.timer = new Timer();
                                    tag.timer.schedule(new TimerTask() {
                                        @Override
                                        public void run() {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        durationView.setText(formatMediaPlayerTime(mp.getCurrentPosition()) + " / " +
                                                                formatMediaPlayerTime(mp.getDuration()));
                                                    } catch (Exception e) {
                                                        Logger.e(TAG, e);
                                                        tag.timer.cancel();
                                                    }
                                                }
                                            });
                                        }
                                    }, 1000, 1000);
                                    
                                    videoView.start();
                                }
                            });
                            videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                                @Override
                                public boolean onError(MediaPlayer mp, int what, int extra) {
                                    Logger.e(TAG, "(Video) Error code: " + what);
                                    if (tag.timer != null) tag.timer.cancel();
                                    showError(tag, getString(R.string.gallery_error_play));
                                    return true;
                                }
                            });
                            
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
                                CompatibilityImpl.setVideoViewZOrderOnTop(videoView);
                            }
                            videoView.setVideoPath(file.getAbsolutePath());
                        }
                    }
                    
                });
            }
        });
    }
    
    private void setAudio(final GalleryItemViewTag tag, final File file) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setOnClickView(tag, getString(R.string.gallery_tap_to_play), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!settings.useInternalAudioPlayer()) {
                            openExternal();
                        } else {
                            recycleTag(tag, false);
                            final TextView durationView = new TextView(GalleryActivity.this);
                            durationView.setGravity(Gravity.CENTER);
                            tag.layout.setVisibility(View.VISIBLE);
                            tag.layout.addView(durationView);
                            tag.audioPlayer = new MediaPlayer();
                            tag.audioPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                                @Override
                                public void onPrepared(final MediaPlayer mp) {
                                    mp.setLooping(true);
                                    
                                    durationView.setText(getSpannedText("00:00 / " + formatMediaPlayerTime(mp.getDuration())));
                                    
                                    tag.timer = new Timer();
                                    tag.timer.schedule(new TimerTask() {
                                        @Override
                                        public void run() {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        durationView.setText(getSpannedText(formatMediaPlayerTime(mp.getCurrentPosition()) + " / " +
                                                                formatMediaPlayerTime(mp.getDuration())));
                                                    } catch (Exception e) {
                                                        Logger.e(TAG, e);
                                                        tag.timer.cancel();
                                                    }
                                                }
                                            });
                                        }
                                    }, 1000, 1000);
                                    
                                    mp.start();
                                }
                            });
                            tag.audioPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                                @Override
                                public boolean onError(MediaPlayer mp, int what, int extra) {
                                    Logger.e(TAG, "(Audio) Error code: " + what);
                                    if (tag.timer != null) tag.timer.cancel();
                                    showError(tag, getString(R.string.gallery_error_play));
                                    return true;
                                }
                            });
                            try {
                                tag.audioPlayer.setDataSource(file.getAbsolutePath());
                                tag.audioPlayer.prepareAsync();
                            } catch (Exception e) {
                                Logger.e(TAG, "audio player error", e);
                                if (tag.timer != null) tag.timer.cancel();
                                showError(tag, getString(R.string.gallery_error_play));
                            }
                        }
                    }
                });
            }
        });
    }
    
    private String formatMediaPlayerTime(int milliseconds) {
        int seconds = milliseconds / 1000 % 60;
        int minutes = milliseconds / 60000;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }
    
    private void setOtherFile(final GalleryItemViewTag tag, final File file) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setOnClickView(tag, getString(R.string.gallery_tap_to_open), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openExternal();
                    }
                });
            }
        });
    }
    
    private void setExternalLink(final GalleryItemViewTag tag) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int stringResId = R.string.gallery_tap_to_external_link;
                try {
                    if (settings.doNotDownloadVideos() && tag.attachmentModel.type == AttachmentModel.TYPE_VIDEO)
                        stringResId = R.string.gallery_tap_to_play;
                } catch (Exception e) {}
                setOnClickView(tag, getString(stringResId), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openBrowser();
                    }
                });
            }
        });
    }
    
    private void setOnClickView(GalleryItemViewTag tag, String message, View.OnClickListener handler) {
        tag.thumbnailView.setVisibility(View.VISIBLE);
        tag.loadingView.setVisibility(View.GONE);
        TextView v = new TextView(GalleryActivity.this);
        v.setGravity(Gravity.CENTER);
        v.setText(getSpannedText(message));
        tag.layout.setVisibility(View.VISIBLE);
        tag.layout.addView(v);
        v.setOnClickListener(handler);
    }
    
    private Spanned getSpannedText(String message) {
        message = " " + message + " ";
        SpannableStringBuilder spanned = new SpannableStringBuilder(message);
        for (Object span : new Object[] { new ForegroundColorSpan(Color.WHITE), new BackgroundColorSpan(Color.parseColor("#88000000")) }) { 
            spanned.setSpan(span, 0, message.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return spanned;
    }
    
    private void setWebView(final GalleryItemViewTag tag, final File file) {
        runOnUiThread(new Runnable() {
            private boolean oomFlag = false;
            
            private final ViewGroup.LayoutParams MATCH_PARAMS =
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            
            private void prepareWebView(WebView webView) {
                webView.setBackgroundColor(Color.TRANSPARENT);
                webView.setInitialScale(100);
                webView.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
                    CompatibilityImpl.setScrollbarFadingEnabled(webView, true);
                }

                WebSettings settings = webView.getSettings();
                settings.setBuiltInZoomControls(true);
                settings.setSupportZoom(true);
                settings.setAllowFileAccess(true);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR_MR1) {
                    CompatibilityImpl.setDefaultZoomFAR(settings);
                    CompatibilityImpl.setLoadWithOverviewMode(settings, true);
                }
                settings.setUseWideViewPort(true);
                settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
                    CompatibilityImpl.setBlockNetworkLoads(settings, true);
                }
                
                setScaleWebView(webView);
            }
            
            private void setScaleWebView(final WebView webView) {
                Runnable callSetScaleWebView = new Runnable() {
                    @Override
                    public void run() {
                        setPrivateScaleWebView(webView);
                    }
                };

                Point resolution = new Point(tag.layout.getWidth(), tag.layout.getHeight());
                if (resolution.equals(0, 0)) {
                    // wait until the view is measured and its size is known
                    AppearanceUtils.callWhenLoaded(tag.layout, callSetScaleWebView);
                } else {
                    callSetScaleWebView.run();
                }
            }
            
            private void setPrivateScaleWebView(WebView webView) {
                Point imageSize = getImageSize(file);
                Point resolution = new Point(tag.layout.getWidth(), tag.layout.getHeight());

                //Logger.d(TAG, "Resolution: "+resolution.x+"x"+resolution.y);
                double scaleX = (double)resolution.x / (double)imageSize.x;
                double scaleY = (double)resolution.y / (double)imageSize.y;
                int scale = (int)Math.round(Math.min(scaleX, scaleY) * 100d);
                scale = Math.max(scale, 1);
                //Logger.d(TAG, "Scale: "+(Math.min(scaleX, scaleY) * 100d));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR_MR1) {
                    double picdpi = (getResources().getDisplayMetrics().density * 160d) / scaleX;
                    if (picdpi >= 240) {
                        CompatibilityImpl.setDefaultZoomFAR(webView.getSettings());
                    } else if (picdpi <= 120) {
                        CompatibilityImpl.setDefaultZoomCLOSE(webView.getSettings());
                    } else {
                        CompatibilityImpl.setDefaultZoomMEDIUM(webView.getSettings());
                    }
                }
                
                webView.setInitialScale(scale);
                webView.setPadding(0, 0, 0, 0);
            }
            
            private Point getImageSize(File file) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                return new Point(options.outWidth, options.outHeight);
            }
            
            @Override
            public void run() {
                try {
                    recycleTag(tag, false);
                    WebView webView = new WebViewFixed(GalleryActivity.this);
                    webView.setLayoutParams(MATCH_PARAMS);
                    tag.layout.addView(webView);
                    prepareWebView(webView);
                    webView.loadUrl(Uri.fromFile(file).toString());
                    tag.thumbnailView.setVisibility(View.GONE);
                    tag.loadingView.setVisibility(View.GONE);
                    tag.layout.setVisibility(View.VISIBLE);
                } catch (OutOfMemoryError oom) {
                    MainApplication.freeMemory();
                    Logger.e(TAG, oom);
                    if (!oomFlag) {
                        oomFlag = true;
                        run();
                    } else showError(tag, getString(R.string.error_out_of_memory));
                }
            }
            
        });
    }
    
    private class GalleryItemViewTag {
        public CancellableTask downloadingTask;
        public Timer timer;
        public MediaPlayer audioPlayer;
        public AttachmentModel attachmentModel;
        public String attachmentHash;
        public File file;
        
        public ImageView thumbnailView;
        public FrameLayout layout;
        public View errorView;
        public TextView errorText;
        public View loadingView;
    }
    
}
