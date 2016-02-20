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
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class DownloadingErrorReportActivity extends Activity implements View.OnClickListener {
    private TextView textView;
    private NotificationManager notificationManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.downloading_error_title);
        setContentView(R.layout.downloading_error_report_layout);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        textView = (TextView) findViewById(R.id.downloading_error_report_view);
        findViewById(R.id.downloading_error_report_ok_btn).setOnClickListener(this);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        updateErrorInfo();
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        updateErrorInfo();
    }

    private void updateErrorInfo() {
        String report = getSharedPreferences(DownloadingService.SHARED_PREFERENCES_NAME, MODE_PRIVATE).
                getString(DownloadingService.PREF_ERROR_REPORT, "");
        textView.setText(report);
        notificationManager.cancel(DownloadingService.ERROR_REPORT_NOTIFICATION_ID);
    }

    @Override
    public void onClick(View v) {
        finish();
    }
}
