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

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.common.Async;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;

import java.util.List;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

public class DownloadingErrorReportActivity extends Activity implements View.OnClickListener {
    private static final String TAG = "DownloadingErrorReportActivity";
    
    private TextView textView;
    private NotificationManager notificationManager;
    private String errorItemsData;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.downloading_error_title);
        setContentView(R.layout.downloading_error_report_layout);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        textView = (TextView) findViewById(R.id.downloading_error_report_view);
        findViewById(R.id.downloading_error_report_ok_btn).setOnClickListener(this);
        findViewById(R.id.downloading_error_report_retry_btn).setOnClickListener(this);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        updateErrorInfo();
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        updateErrorInfo();
    }

    private void updateErrorInfo() {
        SharedPreferences prefs = getSharedPreferences(DownloadingService.SHARED_PREFERENCES_NAME, MODE_PRIVATE);
        String report = prefs.getString(DownloadingService.PREF_ERROR_REPORT, "");
        textView.setText(report);
        notificationManager.cancel(DownloadingService.ERROR_REPORT_NOTIFICATION_ID);
        errorItemsData = prefs.getString(DownloadingService.PREF_ERROR_ITEMS, null);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.downloading_error_report_retry_btn) Async.runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    List<DownloadingService.DownloadingQueueItem> items = DownloadingService.deserializeErrorItems(errorItemsData);
                    for (DownloadingService.DownloadingQueueItem item : items) {
                        if (!DownloadingService.isInQueue(item)) {
                            Intent downloadIntent = new Intent(DownloadingErrorReportActivity.this, DownloadingService.class);
                            downloadIntent.putExtra(DownloadingService.EXTRA_DOWNLOADING_ITEM, item);
                            startService(downloadIntent);
                        }
                    }
                } catch (Exception e) {
                    Logger.e(TAG, e);
                    Async.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainApplication.getInstance(), R.string.error_unknown, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
        finish();
    }
}
