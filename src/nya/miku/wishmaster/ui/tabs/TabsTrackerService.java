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

import java.util.concurrent.locks.LockSupport;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.CancellableTask.BaseCancellableTask;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.PageLoaderFromChan;
import nya.miku.wishmaster.cache.PagesCache;
import nya.miku.wishmaster.cache.SerializablePage;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.common.PriorityThreadFactory;
import nya.miku.wishmaster.http.cloudflare.InteractiveException;
import nya.miku.wishmaster.ui.MainActivity;
import nya.miku.wishmaster.ui.presentation.BoardFragment;
import nya.miku.wishmaster.ui.presentation.PresentationModel;
import nya.miku.wishmaster.ui.settings.ApplicationSettings;
import nya.miku.wishmaster.ui.settings.Wifi;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

/**
 * Сервис автообновления
 * @author miku-nyan
 *
 */
public class TabsTrackerService extends Service {
    private static final String TAG = "TabsTrackerService";
    
    public static final String BROADCAST_ACTION_NOTIFY = "nya.miku.wishmaster.BROADCAST_ACTION_TRACKER_NOTIFY";
    public static final int TRACKER_NOTIFICATION_ID = 40;
    
    /** true, если сервис сейчас работает */
    public static boolean running = false;
    /** если true, в заголовке уведомления будет написано "есть новые сообщения" */
    public static boolean unread = false;
    /** ID вкладки, которая обновляется в данный момент или -1 */
    public static long currentUpdatingTabId = -1;
    
    private Handler handler;
    
    private TabsState tabsState;
    private TabsSwitcher tabsSwitcher;
    private PagesCache pagesCache;
    private NotificationManager notificationManager;
    private boolean isForeground = false;
    
    private int timerDelay;
    private boolean enableNotification;
    private boolean backgroundTabs;
    
    private CancellableTask task = null;
    private CancellableTask updatingTask = null;
    
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
    public void onCreate() {
        Logger.d(TAG, "TabsTrackerService creating");
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        handler = new Handler();
        tabsState = MainApplication.getInstance().tabsState;
        tabsSwitcher = MainApplication.getInstance().tabsSwitcher;
        pagesCache = MainApplication.getInstance().pagesCache;
    }
    
    @SuppressLint("InlinedApi")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        onStart(intent, startId);
        return Service.START_STICKY;
    }
    
    @Override
    public void onStart(Intent intent, int startId) {
        Logger.d(TAG, "TabsTrackerService starting");
        if (task != null) task.cancel();
        ApplicationSettings settings = MainApplication.getInstance().settings;
        enableNotification = settings.isAutoupdateNotification();
        backgroundTabs = settings.isAutoupdateBackground();
        timerDelay = settings.getAutoupdateDelay();
        TrackerLoop loop = new TrackerLoop();
        task = loop;
        PriorityThreadFactory.LOW_PRIORITY_FACTORY.newThread(loop).start();
        running = true;
    }
    
    private void doUpdate(CancellableTask task) {
        if (backgroundTabs) {
            int tabsArrayLength = tabsState.tabsArray.size();
            TabModel[] tabsArray = new TabModel[tabsArrayLength]; //avoid of java.util.ConcurrentModificationException
            for (int i=0; i<tabsArrayLength; ++i) tabsArray[i] = tabsState.tabsArray.get(i);
            for (final TabModel tab : tabsArray) {
                if (task.isCancelled()) return;
                if (tab.type == TabModel.TYPE_NORMAL && tab.pageModel.type == UrlPageModel.TYPE_THREADPAGE && tab.autoupdateBackground) {
                    if (tabsSwitcher.currentId != null && tabsSwitcher.currentId.equals(tab.id)) continue;
                    final String hash = tab.hash;
                    ChanModule chan = MainApplication.getInstance().getChanModule(tab.pageModel.chanName);
                    currentUpdatingTabId = tab.id;
                    final PresentationModel presentationModel = pagesCache.getPresentationModel(hash);
                    final SerializablePage serializablePage;
                    if (presentationModel != null) {
                        serializablePage = presentationModel.source;
                    } else {
                        SerializablePage pageFromFilecache = pagesCache.getSerializablePage(hash);
                        if (pageFromFilecache != null) {
                            serializablePage = pageFromFilecache;
                        } else {
                            serializablePage = new SerializablePage();
                            serializablePage.pageModel = tab.pageModel;
                        }
                    }
                    
                    final int oldCount = serializablePage.posts != null ? serializablePage.posts.length : 0;
                    updatingTask = new PageLoaderFromChan(serializablePage, new PageLoaderFromChan.PageLoaderCallback() {
                        @Override
                        public void onSuccess() {
                            tab.autoupdateError = false;
                            int newCount = serializablePage.posts != null ? serializablePage.posts.length : 0;
                            if (oldCount != newCount) {
                                if (oldCount != 0) tab.unreadPostsCount += (newCount - oldCount);
                                unread = true;
                            }
                            if (presentationModel != null) {
                                presentationModel.setNotReady();
                                pagesCache.putPresentationModel(hash, presentationModel);
                            } else {
                                pagesCache.putSerializablePage(hash, serializablePage);
                            }
                        }
                        @Override
                        public void onInteractiveException(InteractiveException e) {
                            tab.autoupdateError = true;
                        }
                        @Override
                        public void onError(String message) {
                            tab.autoupdateError = true;
                        }
                    }, chan);
                    ((PageLoaderFromChan) updatingTask).run();
                }
            }
            currentUpdatingTabId = -1;
            updatingTask = null;
        }
        if (task.isCancelled()) return;
        if (tabsSwitcher.currentFragment instanceof BoardFragment) {
            TabModel tab = tabsState.findTabById(tabsSwitcher.currentId);
            if (tab != null && tab.pageModel != null && tab.type == TabModel.TYPE_NORMAL && tab.pageModel.type == UrlPageModel.TYPE_THREADPAGE) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ((BoardFragment) tabsSwitcher.currentFragment).updateSilent();
                        } catch (Exception e) {
                            Logger.e(TAG, e);
                        }
                    }
                });
            }
        }
    }
    
    private class TrackerLoop extends BaseCancellableTask implements Runnable {
        private int timerCounter = 0;
        private NotificationCompat.Builder notifBuilder = new NotificationCompat.Builder(TabsTrackerService.this).
                setSmallIcon(R.drawable.ic_launcher).
                setContentIntent(PendingIntent.getActivity(
                        TabsTrackerService.this, 0, new Intent(TabsTrackerService.this, MainActivity.class), PendingIntent.FLAG_CANCEL_CURRENT));
        
        @Override
        public void run() {
            while (true) {
                if (isCancelled()) {
                    cancelForeground(TRACKER_NOTIFICATION_ID);
                    return;
                }
                notifBuilder.setContentTitle(getString(unread ? R.string.tabs_tracker_title_unread : R.string.tabs_tracker_title));
                if (++timerCounter > timerDelay) {
                    timerCounter = 0;
                    if (enableNotification) {
                        notifyForeground(TRACKER_NOTIFICATION_ID, notifBuilder.setContentText(getString(R.string.tabs_tracker_updating)).build());
                    }
                    if (!MainApplication.getInstance().settings.isAutoupdateWifiOnly() || Wifi.isConnected()) doUpdate(this);
                    
                    if (isCancelled()) {
                        cancelForeground(TRACKER_NOTIFICATION_ID);
                        return;
                    } else {
                        sendBroadcast(new Intent(BROADCAST_ACTION_NOTIFY));
                    }
                    
                } else {
                   if (enableNotification) {
                       int remainingTime = timerDelay - timerCounter + 1;
                       String message = getResources().getQuantityString(R.plurals.tabs_tracker_timer, remainingTime, remainingTime);;
                       notifyForeground(TRACKER_NOTIFICATION_ID, notifBuilder.setContentText(message).build());
                   }
                }
                
                LockSupport.parkNanos(1000000000);
            }
        }
        
        @Override
        public void cancel() {
            if (updatingTask != null) updatingTask.cancel();
            super.cancel();
        }
        
    }
    
    @Override
    public void onDestroy() {
        Logger.d(TAG, "TabsTrackerService destroying");
        if (task != null) task.cancel();
        running = false;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
}
