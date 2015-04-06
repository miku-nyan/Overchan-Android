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

package nya.miku.wishmaster.ui.posting;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.ui.posting.PostingService.PostingServiceBinder;
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
import android.widget.Toast;

public class PostingProgressActivity extends Activity implements View.OnClickListener {
    private boolean bound = false;
    private Intent bindingIntent;
    private ServiceConnection serviceConnection;
    private PostingServiceBinder binder;
    private BroadcastReceiver broadcastReceiver;
    private IntentFilter intentFilter;
    
    private ProgressBar progressBar = null;
    private boolean isIndeterminate = true;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.posting_progress_layout);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        
        findViewById(R.id.posting_cancel_button).setOnClickListener(this);
        findViewById(R.id.posting_hide_button).setOnClickListener(this);
        progressBar = (ProgressBar) findViewById(android.R.id.progress);
        progressBar.setMax(100);
        progressBar.setIndeterminate(true);
        
        handleIntent(getIntent());
        
        bindingIntent = new Intent(this, PostingService.class);
        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceDisconnected(ComponentName name) {
                bound = false;
                binder = null;
            }
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                bound = true;
                binder = (PostingServiceBinder) service;
                setProgressBar(binder.getCurrentProgress());
            }
        };
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int progress = intent.getIntExtra(PostingService.EXTRA_BROADCAST_PROGRESS_STATUS, -1);
                if (progress == PostingService.BROADCAST_STATUS_ERROR || progress == PostingService.BROADCAST_STATUS_SUCCESS
                        || !PostingService.isNowPosting()) {
                    finish();
                    return;
                }
                setProgressBar(progress);
            }
        };
        intentFilter = new IntentFilter();
        intentFilter.addAction(PostingService.BROADCAST_ACTION_PROGRESS);
        intentFilter.addAction(PostingService.BROADCAST_ACTION_STATUS);
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(broadcastReceiver, intentFilter);
        bindService(bindingIntent, serviceConnection, Service.BIND_AUTO_CREATE);
        if (!PostingService.isNowPosting()) finish();
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
        if (!PostingService.isNowPosting()) {
            finish();
            return;
        }
        if (v.getId() == R.id.posting_cancel_button) {
            if (bound) {
                binder.cancel();
            } else {
                Toast.makeText(this, "Internal Error: posting service not bound!", Toast.LENGTH_LONG).show();
            }
        }
        finish();
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }
    
    private void handleIntent(Intent intent) {
        setTitle(intent.getBooleanExtra(PostingService.EXTRA_IS_POSTING_THREAD, false) ? R.string.posting_thread : R.string.posting_post);
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
