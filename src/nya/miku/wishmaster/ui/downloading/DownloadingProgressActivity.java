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
import nya.miku.wishmaster.ui.downloading.DownloadingService.DownloadingServiceBinder;
import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class DownloadingProgressActivity extends Activity implements View.OnClickListener {
    private boolean bound = false;
    private Intent bindingIntent;
    private ServiceConnection serviceConnection;
    private DownloadingServiceBinder binder;
    private BroadcastReceiver broadcastReceiver;
    private IntentFilter intentFilter;
    
    private ProgressBar progressBar = null;
    private TextView currentItemView = null;
    private boolean isIndeterminate = true;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setTitle(R.string.downloading_title_simple);
        setContentView(R.layout.downloading_progress_layout);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        
        findViewById(R.id.downloading_cancel_button).setOnClickListener(this);
        findViewById(R.id.downloading_hide_button).setOnClickListener(this);
        progressBar = (ProgressBar) findViewById(android.R.id.progress);
        progressBar.setMax(100);
        progressBar.setIndeterminate(true);
        currentItemView = (TextView) findViewById(R.id.downloading_dialog_current_item);
        
        bindingIntent = new Intent(this, DownloadingService.class);
        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceDisconnected(ComponentName name) {
                bound = false;
                binder = null;
            }
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                bound = true;
                binder = (DownloadingServiceBinder) service;
                updateStatus();
            }
        };
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int report = intent.getIntExtra(DownloadingService.EXTRA_DOWNLOADING_REPORT, DownloadingService.REPORT_NONE);
                switch (report) {
                    case DownloadingService.REPORT_OK:
                        finish();
                        break;
                    case DownloadingService.REPORT_ERROR:
                        startActivity(new Intent(DownloadingProgressActivity.this, DownloadingErrorReportActivity.class));
                        finish();
                        break;
                    default:
                        updateStatus();
                        break;
                }
            }
        };
        intentFilter = new IntentFilter();
        intentFilter.addAction(DownloadingService.BROADCAST_UPDATED);
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(broadcastReceiver, intentFilter);
        bindService(bindingIntent, serviceConnection, Service.BIND_AUTO_CREATE);
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(broadcastReceiver);
        if (bound) unbindService(serviceConnection);
        bound = false;
    }
    
    
    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.downloading_cancel_button) {
            if (bound) {
                binder.cancel();
            } else {
                Toast.makeText(this, "Internal Error: downloading service not bound!", Toast.LENGTH_LONG).show();
            }
        }
        finish();
    }
    
    private void updateStatus() {
        if (!bound || binder == null) return;
        setProgressBar(binder.getCurrentProgress());
        int count = binder.getQueueSize() + 1;
        String curItem = binder.getCurrentItemName();
        currentItemView.setText(getString(R.string.downloading_dialog_current_format, count, curItem == null ? "" : curItem));
    }
    
    private void setProgressBar(int progress) {
        if (progress == -1) {
            if (!isIndeterminate) {
                progressBar.setIndeterminate(true);
                isIndeterminate = true;
            }
        } else {
            if (isIndeterminate) {
                progressBar.setIndeterminate(false);
                isIndeterminate = false;
            }
            progressBar.setProgress(progress);
        }
    }
}
