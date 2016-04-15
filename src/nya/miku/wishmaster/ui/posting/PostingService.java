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

package nya.miku.wishmaster.ui.posting;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.common.Async;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.http.interactive.InteractiveException;
import nya.miku.wishmaster.ui.MainActivity;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

/**
 * Сервис постинга (отправки сообщений)
 * @author miku-nyan
 */
public class PostingService extends Service {
    private static final String TAG = "PostingService";
    
    public static final String EXTRA_PAGE_HASH = "Hash";
    public static final String EXTRA_BOARD_MODEL = "BoardModel";
    public static final String EXTRA_SEND_POST_MODEL = "SendPostModel";
    public static final String EXTRA_IS_POSTING_THREAD = "IsPostingThread";
    public static final String EXTRA_RETURN_FROM_SERVICE = "ReturnFromService";
    public static final String EXTRA_RETURN_REASON = "ReturnReason";
    public static final String EXTRA_RETURN_REASON_ERROR = "ReturnReasonError";
    public static final String EXTRA_RETURN_REASON_INTERACTIVE_EXCEPTION = "ReturnReasonInteractiveException";
    public static final String EXTRA_TARGET_URL = "TargetUrl";
    public static final String EXTRA_BROADCAST_PROGRESS_STATUS = "BroadcastProgress";
    public static final String BROADCAST_ACTION_PROGRESS = "nya.miku.wishmaster.BROADCAST_ACTION_POSTING_PROGRESS";
    public static final String BROADCAST_ACTION_STATUS = "nya.miku.wishmaster.BROADCAST_ACTION_POSTING_STATUS";
    
    public static final int BROADCAST_STATUS_SUCCESS = 201;
    public static final int BROADCAST_STATUS_ERROR = 202;
    
    public static final int REASON_INTERACTIVE_EXCEPTION = 1;
    public static final int REASON_ERROR = 2;
    
    public static final int POSTING_NOTIFICATION_ID = 10;
    
    private static volatile boolean nowPosting = false;
    
    private PostingServiceBinder binder;
    private NotificationManager notificationManager;
    
    private PostingTask currentTask;
    
    public static boolean isNowPosting() {
        return nowPosting;
    }
    
    @Override
    public void onCreate() {
      super.onCreate();
      binder = new PostingServiceBinder();
      notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
      Logger.d(TAG, "created posting service");
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.d(TAG, "destroyed posting service");
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
        if (intent == null) {
            nowPosting = false;
            stopSelf(startId);
            return;
        }
        currentTask = new PostingTask(
                startId,
                intent.getStringExtra(EXTRA_PAGE_HASH),
                (SendPostModel) intent.getSerializableExtra(EXTRA_SEND_POST_MODEL),
                (BoardModel) intent.getSerializableExtra(EXTRA_BOARD_MODEL));
        Async.runAsync(currentTask);
    }
    
    public class PostingTask extends CancellableTask.BaseCancellableTask implements Runnable {
        private final int startId;
        private final String hash;
        private final SendPostModel sendPostModel;
        private final BoardModel boardModel;
        
        private long maxProgressValue = 100;
        private int curProgress = -1;
        
        public PostingTask(int startId, String hash, SendPostModel sendPostModel, BoardModel boardModel) {
            this.startId = startId;
            this.hash = hash;
            this.sendPostModel = sendPostModel;
            this.boardModel = boardModel;
        }
        
        public int getCurrentProgress() {
            return curProgress;
        }
        
        @Override
        public void run() {
            if (sendPostModel == null) {
                Logger.e(TAG, "sendPostModel == null");
                return;
            }
            
            Logger.d(TAG, "start; nowPosting = true");
            nowPosting = true;
            
            Intent intentToProgressDialog = new Intent(PostingService.this, PostingProgressActivity.class);
            intentToProgressDialog.putExtra(EXTRA_IS_POSTING_THREAD, sendPostModel.threadNumber == null);
            PendingIntent pIntentToProgressDialog =
                    PendingIntent.getActivity(PostingService.this, 0, intentToProgressDialog, PendingIntent.FLAG_CANCEL_CURRENT);
            
            final String notifTitle = sendPostModel.threadNumber == null ? getString(R.string.posting_thread) : getString(R.string.posting_post);
            String notifText = sendPostModel.threadNumber == null ?
                    getString(R.string.posting_thread_format, sendPostModel.chanName, sendPostModel.boardName) :
                    getString(R.string.posting_post_format, sendPostModel.chanName, sendPostModel.boardName, sendPostModel.threadNumber);
            
            final NotificationCompat.Builder progressNotifBuilder = new NotificationCompat.Builder(PostingService.this).
                    setSmallIcon(android.R.drawable.stat_sys_upload).
                    setTicker(notifTitle).
                    setContentTitle(notifTitle).
                    setContentText(notifText).
                    setContentIntent(pIntentToProgressDialog).
                    setOngoing(true).
                    setProgress(100, 0, true);
            
            notificationManager.notify(POSTING_NOTIFICATION_ID, progressNotifBuilder.build());
            
            boolean success = false;
            String targetUrl = null;
            try {
                targetUrl = MainApplication.getInstance().getChanModule(sendPostModel.chanName).sendPost(sendPostModel, new ProgressListener() {
                    @Override
                    public void setProgress(long value) {
                        int newProgress = (int)(100 * (double)value / maxProgressValue);
                        if (newProgress == curProgress) return;
                        curProgress = newProgress;
                        progressNotifBuilder.setProgress(100, newProgress, false);
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                            progressNotifBuilder.setContentTitle("("+newProgress+"%) "+notifTitle);
                        }
                        notificationManager.notify(POSTING_NOTIFICATION_ID, progressNotifBuilder.build());
                        Intent broadcastIntent = new Intent(BROADCAST_ACTION_PROGRESS);
                        broadcastIntent.putExtra(EXTRA_BROADCAST_PROGRESS_STATUS, newProgress);
                        sendBroadcast(broadcastIntent);
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
                            progressNotifBuilder.setContentTitle(notifTitle);
                        }
                        notificationManager.notify(POSTING_NOTIFICATION_ID, progressNotifBuilder.build());
                        curProgress = -1;
                        Intent broadcastIntent = new Intent(BROADCAST_ACTION_PROGRESS);
                        broadcastIntent.putExtra(EXTRA_BROADCAST_PROGRESS_STATUS, curProgress);
                        sendBroadcast(broadcastIntent);
                    }
                }, this);
                success = true;
            } catch (Exception e) {
                Logger.e(TAG, "exception while posting", e);
                if (!isCancelled()) {
                    Intent broadcastIntent = new Intent(BROADCAST_ACTION_STATUS);
                    broadcastIntent.putExtra(EXTRA_BROADCAST_PROGRESS_STATUS, BROADCAST_STATUS_ERROR);
                    
                    Intent intentToPostingForm = new Intent(PostingService.this, PostFormActivity.class);
                    intentToPostingForm.putExtra(EXTRA_PAGE_HASH, hash);
                    intentToPostingForm.putExtra(EXTRA_SEND_POST_MODEL, sendPostModel);
                    intentToPostingForm.putExtra(EXTRA_BOARD_MODEL, boardModel);
                    intentToPostingForm.putExtra(EXTRA_RETURN_FROM_SERVICE, true);
                    
                    broadcastIntent.putExtra(EXTRA_PAGE_HASH, hash);
                    broadcastIntent.putExtra(EXTRA_SEND_POST_MODEL, sendPostModel);
                    broadcastIntent.putExtra(EXTRA_BOARD_MODEL, boardModel);
                    
                    String errorMessage = null;
                    if (e instanceof InteractiveException) {
                        errorMessage = getString(R.string.posting_error_interactive_format, ((InteractiveException) e).getServiceName());
                        InteractiveException cfException = (InteractiveException) e;
                        intentToPostingForm.putExtra(EXTRA_RETURN_REASON, REASON_INTERACTIVE_EXCEPTION);
                        intentToPostingForm.putExtra(EXTRA_RETURN_REASON_INTERACTIVE_EXCEPTION, cfException);
                        broadcastIntent.putExtra(EXTRA_RETURN_REASON, REASON_INTERACTIVE_EXCEPTION);
                        broadcastIntent.putExtra(EXTRA_RETURN_REASON_INTERACTIVE_EXCEPTION, cfException);
                    } else {
                        errorMessage = e.getMessage() == null ? getString(R.string.posting_error_default) : e.getMessage();
                        intentToPostingForm.putExtra(EXTRA_RETURN_REASON, REASON_ERROR);
                        intentToPostingForm.putExtra(EXTRA_RETURN_REASON_ERROR, errorMessage);
                        broadcastIntent.putExtra(EXTRA_RETURN_REASON, REASON_ERROR);
                        broadcastIntent.putExtra(EXTRA_RETURN_REASON_ERROR, errorMessage);
                    }
                    PendingIntent pIntentToPostingForm =
                            PendingIntent.getActivity(PostingService.this, 0, intentToPostingForm, PendingIntent.FLAG_CANCEL_CURRENT);
                    NotificationCompat.Builder errorNotifBuilder = new NotificationCompat.Builder(PostingService.this).
                            setSmallIcon(android.R.drawable.stat_notify_error).
                            setTicker(e instanceof InteractiveException ? errorMessage : getString(R.string.posting_error)).
                            setContentTitle(getString(R.string.posting_error)).
                            setContentText(errorMessage).
                            setContentIntent(pIntentToPostingForm).
                            setOngoing(false).
                            setAutoCancel(true);
                    notificationManager.notify(POSTING_NOTIFICATION_ID, errorNotifBuilder.build());
                    sendBroadcast(broadcastIntent);
                }
            }
            
            if (success && !isCancelled()) {
                MainApplication.getInstance().draftsCache.remove(hash);
                Intent intentSuccess = new Intent(PostingService.this, MainActivity.class);
                if (targetUrl == null) {
                    UrlPageModel model = new UrlPageModel();
                    model.chanName = sendPostModel.chanName;
                    model.boardName = sendPostModel.boardName;
                    if (sendPostModel.threadNumber != null) {
                        model.type = UrlPageModel.TYPE_THREADPAGE;
                        model.threadNumber = sendPostModel.threadNumber;
                    } else {
                        model.type = UrlPageModel.TYPE_BOARDPAGE;
                        model.boardPage = boardModel.firstPage;
                    }
                    targetUrl = MainApplication.getInstance().getChanModule(sendPostModel.chanName).buildUrl(model);
                }
                intentSuccess.setData(Uri.parse(targetUrl));
                PendingIntent pIntentSuccess = PendingIntent.getActivity(PostingService.this, 0, intentSuccess, PendingIntent.FLAG_CANCEL_CURRENT);
                NotificationCompat.Builder successNotifBuilder = new NotificationCompat.Builder(PostingService.this).
                        setSmallIcon(android.R.drawable.stat_sys_upload_done).
                        setTicker(getString(R.string.posting_success)).
                        setContentTitle(getString(R.string.posting_success)).
                        setContentText(
                                getString(sendPostModel.threadNumber == null ? R.string.posting_success_thread : R.string.posting_success_post)).
                        setContentIntent(pIntentSuccess).
                        setOngoing(false).
                        setAutoCancel(true);
                notificationManager.notify(POSTING_NOTIFICATION_ID, successNotifBuilder.build());
                Intent broadcastIntent = new Intent(BROADCAST_ACTION_STATUS);
                broadcastIntent.putExtra(EXTRA_BROADCAST_PROGRESS_STATUS, BROADCAST_STATUS_SUCCESS);
                broadcastIntent.putExtra(EXTRA_TARGET_URL, targetUrl);
                sendBroadcast(broadcastIntent);
            } else if (isCancelled()) {
                notificationManager.notify(POSTING_NOTIFICATION_ID, new NotificationCompat.Builder(PostingService.this).
                        setSmallIcon(android.R.drawable.ic_delete).
                        setTicker(getString(R.string.posting_cancelled)).
                        setContentTitle(getString(R.string.posting_cancelled)).
                        setContentText(getString(R.string.posting_cancelled)).
                        setContentIntent(PendingIntent.getActivity(PostingService.this, 0, new Intent(), 0)).
                        build());
                notificationManager.cancel(POSTING_NOTIFICATION_ID);
            }
            
            Logger.d(TAG, "stop; nowPosting = false");
            nowPosting = false;
            stopSelf(startId);
        }
    }
    
    public class PostingServiceBinder extends Binder {
        public void cancel() {
            if (currentTask != null) currentTask.cancel();
        }
        public int getCurrentProgress() {
            if (currentTask == null) return -1;
            return currentTask.getCurrentProgress();
        }
    }
}
