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

package nya.miku.wishmaster.ui.tabs;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

import org.apache.commons.lang3.tuple.Pair;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.CancellableTask.BaseCancellableTask;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.PageLoaderFromChan;
import nya.miku.wishmaster.cache.PagesCache;
import nya.miku.wishmaster.cache.SerializablePage;
import nya.miku.wishmaster.common.Async;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.http.interactive.InteractiveException;
import nya.miku.wishmaster.ui.MainActivity;
import nya.miku.wishmaster.ui.downloading.BackgroundThumbDownloader;
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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

/**
 * Сервис автообновления
 * @author miku-nyan
 *
 */
public class TabsTrackerService extends Service {
    private static final String TAG = "TabsTrackerService";
    
    public static final String EXTRA_UPDATE_IMMEDIATELY = "UpdateImmediately";
    public static final String EXTRA_CLEAR_SUBSCRIPTIONS = "ClearSubscriptions";
    public static final String BROADCAST_ACTION_NOTIFY = "nya.miku.wishmaster.BROADCAST_ACTION_TRACKER_NOTIFY";
    public static final String BROADCAST_ACTION_CLEAR_SUBSCRIPTIONS = "nya.miku.wishmaster.BROADCAST_ACTION_CLEAR_SUBSCRIPTIONS";
    public static final int TRACKER_NOTIFICATION_UPDATE_ID = 40;
    public static final int TRACKER_NOTIFICATION_SUBSCRIPTIONS_ID = 50;
    
    /** true, если сервис сейчас работает */
    private static boolean running = false;
    /** если true, в заголовке уведомления будет написано "есть новые сообщения" */
    private static boolean unread = false;
    /** если true, выведется уведомление об ответе на отслеживаемые посты */
    private static boolean subscriptions = false;
    /** список тредов, в которых есть ответы на отслеживаемые посты (пары: url, заголовок вкладки) */
    private static List<Pair<String, String>> subscriptionsData = null;
    /** ID вкладки, которая обновляется в данный момент или -1 */
    private static long currentUpdatingTabId = -1;
    
    /** true, если сервис сейчас работает */
    public static boolean isRunning() {
        return running;
    }
    
    /** добавить тред, в котором есть ответы на отслеживаемые посты (будет выведено уведомление) */
    public static void addSubscriptionNotification(String url, String tabTitle) {
        List<Pair<String, String>> list = subscriptionsData;
        if (list == null) list = new ArrayList<>();
        int index = -1;
        for (int i=0; i<list.size(); ++i) {
            Pair<String, String> pair = list.get(i);
            if (url == null) {
                if (pair.getLeft() == null && tabTitle.equals(pair.getRight())) {
                    index = i;
                    break;
                }
            } else {
                if (url.equals(pair.getLeft())) {
                    index = i;
                    break;
                }
            }
        }
        Pair<String, String> newPair = Pair.of(url, tabTitle);
        if (index == -1) list.add(newPair); else list.set(index, newPair);
        subscriptionsData = list;
        subscriptions = true;
    }
    
    /** установить флаг непрочитанных сообщений: в заголовке уведомления об автообновлении будет написано "есть новые сообщения" */
    public static void setUnread() {
        unread = true;
    }
    
    /** очистить состояние уведомления об автообновлении: убрать надпись "есть новые сообщения" */
    public static void clearUnread() {
        unread = false;
    }
    
    /** очистить список тредов, в которых есть ответы на отслеживаемые посты, в уведомлении об отслеживаемых
     *  (при этом, если уведомление об отслеживаемых на данный момент не было создано, оно не будет создано) */
    public static void clearSubscriptions() {
        subscriptions = false;
        subscriptionsData = null;
    }
    
    /** получить ID вкладки, которая обновляется в данный момент; вернёт -1, если обновление не выполняется в данный момент */
    public static long getCurrentUpdatingTabId() {
        return currentUpdatingTabId;
    }
    
    private ApplicationSettings settings;
    private TabsState tabsState;
    private TabsSwitcher tabsSwitcher;
    private PagesCache pagesCache;
    private NotificationManager notificationManager;
    private BroadcastReceiver broadcastReceiver;
    private boolean isForeground = false;
    
    private int timerDelay;
    private boolean enableNotification;
    private boolean backgroundTabs;
    
    private boolean immediately = false;
    
    private CancellableTask task = null;
    
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
        settings = MainApplication.getInstance().settings;
        tabsState = MainApplication.getInstance().tabsState;
        tabsSwitcher = MainApplication.getInstance().tabsSwitcher;
        pagesCache = MainApplication.getInstance().pagesCache;
        registerReceiver(broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Logger.d(TAG, "received BROADCAST_ACTION_CLEAR_SUBSCRIPTIONS");
                clearSubscriptions();
            }
        }, new IntentFilter(BROADCAST_ACTION_CLEAR_SUBSCRIPTIONS));
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
        enableNotification = settings.isAutoupdateNotification();
        immediately = intent != null && intent.getBooleanExtra(EXTRA_UPDATE_IMMEDIATELY, false);
        backgroundTabs = settings.isAutoupdateBackground();
        timerDelay = settings.getAutoupdateDelay();
        if (running) {
            Logger.d(TAG, "TabsTrackerService service already running");
            return;
        }
        clearUnread();
        clearSubscriptions();
        TrackerLoop loop = new TrackerLoop();
        task = loop;
        Async.runAsync(loop);
        running = true;
    }
    
    private void doUpdate(final CancellableTask task) {
        if (backgroundTabs || immediately) {
            int tabsArrayLength = tabsState.tabsArray.size();
            TabModel[] tabsArray = new TabModel[tabsArrayLength]; //avoid of java.util.ConcurrentModificationException
            for (int i=0; i<tabsArrayLength; ++i) tabsArray[i] = tabsState.tabsArray.get(i);
            for (final TabModel tab : tabsArray) {
                if (task.isCancelled()) return;
                if (settings.isAutoupdateWifiOnly() && !Wifi.isConnected() && !immediately) return;
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
                    new PageLoaderFromChan(serializablePage, new PageLoaderFromChan.PageLoaderCallback() {
                        @Override
                        public void onSuccess() {
                            BackgroundThumbDownloader.download(serializablePage, task);
                            MainApplication.getInstance().subscriptions.checkOwnPost(serializablePage, oldCount);
                            tab.autoupdateError = false;
                            int newCount = serializablePage.posts != null ? serializablePage.posts.length : 0;
                            if (oldCount != newCount) {
                                if (oldCount != 0) tab.unreadPostsCount += (newCount - oldCount);
                                setUnread();
                                if (MainApplication.getInstance().subscriptions.checkSubscriptions(serializablePage, oldCount)) {
                                    addSubscriptionNotification(tab.webUrl, tab.title);
                                    tab.unreadSubscriptions = true;
                                }
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
                    }, chan, task).run();
                }
            }
            currentUpdatingTabId = -1;
        }
        if (task.isCancelled()) return;
        if (settings.isAutoupdateWifiOnly() && !Wifi.isConnected() && !immediately) return;
        if (tabsSwitcher.currentFragment instanceof BoardFragment) {
            TabModel tab = tabsState.findTabById(tabsSwitcher.currentId);
            if (tab != null && tab.pageModel != null && tab.type == TabModel.TYPE_NORMAL && tab.pageModel.type == UrlPageModel.TYPE_THREADPAGE) {
                Async.runOnUiThread(new Runnable() {
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
        
        @Override
        public void run() {
            while (true) {
                if (isCancelled()) {
                    cancelForeground(TRACKER_NOTIFICATION_UPDATE_ID);
                    return;
                }
                Notification subscriptionsNotification = getSubscriptionsNotification();
                if (subscriptionsNotification != null)
                    notificationManager.notify(TRACKER_NOTIFICATION_SUBSCRIPTIONS_ID, subscriptionsNotification);
                
                if (++timerCounter > timerDelay || immediately) {
                    timerCounter = 0;
                    if (enableNotification) {
                        notifyForeground(TRACKER_NOTIFICATION_UPDATE_ID, getUpdateNotification(-1));
                    }
                    if (!settings.isAutoupdateWifiOnly() || Wifi.isConnected() || immediately) {
                        doUpdate(this);
                        immediately = false;
                    }
                    
                    if (isCancelled()) {
                        cancelForeground(TRACKER_NOTIFICATION_UPDATE_ID);
                        return;
                    } else {
                        sendBroadcast(new Intent(BROADCAST_ACTION_NOTIFY));
                    }
                    
                    if (!settings.isAutoupdateEnabled()) stopSelf();
                    
                } else {
                   if (enableNotification) {
                       int remainingTime = timerDelay - timerCounter + 1;
                       notifyForeground(TRACKER_NOTIFICATION_UPDATE_ID, getUpdateNotification(remainingTime));
                   }
                }
                
                LockSupport.parkNanos(1000000000);
            }
        }
        
        //если secondsRemaining == -1, текст будет "выполняется обновление"
        private Notification getUpdateNotification(int secondsRemaining) {
            return notifUpdate.
                    setContentTitle(getString(unread ? R.string.tabs_tracker_title_unread : R.string.tabs_tracker_title)).
                    setContentText(secondsRemaining == -1 ? getString(R.string.tabs_tracker_updating) :
                        getResources().getQuantityString(R.plurals.tabs_tracker_timer, secondsRemaining, secondsRemaining)).
                    build();
        }
        
        private Notification getSubscriptionsNotification() {
            if (!subscriptions) return null;
            subscriptions = false;
            List<Pair<String, String>> list = subscriptionsData;
            if (list == null || list.size() == 0) return null;
            String url = list.get(0).getLeft();
            Intent activityIntent = new Intent(TabsTrackerService.this, MainActivity.class).putExtra(EXTRA_CLEAR_SUBSCRIPTIONS, true);
            if (url != null) activityIntent.setData(Uri.parse(url));
            NotificationCompat.InboxStyle style = list.size() == 1 ? null : new NotificationCompat.InboxStyle().
                    addLine(getString(R.string.subscriptions_notification_text_format, list.get(0).getRight())).
                    addLine(getString(R.string.subscriptions_notification_text_format, list.get(1).getRight()));
            if (list.size() > 2) style.setSummaryText(getString(R.string.subscriptions_notification_text_more, list.size() - 2));
            
            return notifSubscription.
                    setContentText(list.size() > 1 ?
                            getString(R.string.subscriptions_notification_text_multiple) :
                                getString(R.string.subscriptions_notification_text_format, list.get(0).getRight())).
                    setStyle(style).
                    setContentIntent(PendingIntent.getActivity(TabsTrackerService.this, 0, activityIntent, PendingIntent.FLAG_CANCEL_CURRENT)).
                    build();
        }
        
        private NotificationCompat.Builder notifUpdate = new NotificationCompat.Builder(TabsTrackerService.this).
                setSmallIcon(R.drawable.ic_launcher).
                setCategory(NotificationCompat.CATEGORY_SERVICE).
                setContentIntent(PendingIntent.getActivity(
                        TabsTrackerService.this, 0, new Intent(TabsTrackerService.this, MainActivity.class), PendingIntent.FLAG_CANCEL_CURRENT));
        
        
        private NotificationCompat.Builder notifSubscription = new NotificationCompat.Builder(TabsTrackerService.this).
                setSmallIcon(R.drawable.ic_launcher).
                setDefaults(NotificationCompat.DEFAULT_ALL).
                setOngoing(false).
                setAutoCancel(true).
                setOnlyAlertOnce(true).
                setCategory(NotificationCompat.CATEGORY_MESSAGE).
                setContentTitle(getString(R.string.subscriptions_notification_title)).
                setDeleteIntent(PendingIntent.getBroadcast(
                        TabsTrackerService.this, 0, new Intent(BROADCAST_ACTION_CLEAR_SUBSCRIPTIONS), PendingIntent.FLAG_CANCEL_CURRENT));
        
    }
    
    @Override
    public void onDestroy() {
        Logger.d(TAG, "TabsTrackerService destroying");
        if (task != null) task.cancel();
        running = false;
        unregisterReceiver(broadcastReceiver);
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
}
