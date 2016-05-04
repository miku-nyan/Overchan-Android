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

package nya.miku.wishmaster.ui.settings;

import java.io.FileInputStream;
import java.util.ArrayList;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.Async;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.http.client.ExtendedHttpClient;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.FileDialogActivity;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.ui.CompatibilityUtils;

public class CustomThemeListActivity extends ListActivity {
    private static final int REQUEST_CODE_SELECT_CUSTOM_THEME = 1;
    
    private static final String TAG = "CustomThemeListActivity";
    
    private static final String URL_PATH = "https://raw.githubusercontent.com/miku-nyan/Overchan-Themes/master/themes/";
    private static final String URL_INDEX = URL_PATH + "index.json";
    
    private ApplicationSettings settings;
    private ArrayAdapter<String> adapter;
    private ArrayList<String> names, files;
    
    private ExtendedHttpClient httpClient = new ExtendedHttpClient(null);
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settings = MainApplication.getInstance().settings;
        settings.getTheme().setToPreferencesActivity(this);
        super.onCreate(savedInstanceState);
        setTitle(R.string.custom_themes_title);
        names = new ArrayList<String>();
        names.add(getString(R.string.custom_themes_local));
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, names);
        setListAdapter(adapter);
        
        final CancellableTask task = new CancellableTask.BaseCancellableTask();
        final ProgressDialog progressDialog = showProgressDialog(task);
        Async.runAsync(new Runnable() {
            @Override
            public void run() {
                JSONArray r;
                try {
                    HttpRequestModel request = HttpRequestModel.DEFAULT_GET;
                    r = HttpStreamer.getInstance().getJSONArrayFromUrl(URL_INDEX, request, httpClient, null, task, false);
                } catch (Exception e) {
                    r = null;
                }
                final JSONArray response = r;
                
                if (task.isCancelled()) return;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (task.isCancelled()) return;
                        progressDialog.dismiss();
                        try {
                            if (response == null) throw new Exception();
                            files = new ArrayList<String>(response.length() + 1);
                            files.add(null);
                            for (int i=0; i<response.length(); ++i) {
                                JSONArray current = response.getJSONArray(i);
                                files.add(current.getString(1));
                                names.add(current.getString(0));
                            }
                        } catch (Exception e) {
                            Logger.e(TAG, e);
                            Toast.makeText(CustomThemeListActivity.this, R.string.error_connection, Toast.LENGTH_LONG).show();
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        });
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        if (position == 0) {
            if (!CompatibilityUtils.hasAccessStorage(this)) return;
            Intent selectFile = new Intent(this, FileDialogActivity.class);
            selectFile.putExtra(FileDialogActivity.CAN_SELECT_DIR, false);
            selectFile.putExtra(FileDialogActivity.START_PATH, Environment.getExternalStorageDirectory().getAbsolutePath());
            selectFile.putExtra(FileDialogActivity.SELECTION_MODE, FileDialogActivity.SELECTION_MODE_OPEN);
            selectFile.putExtra(FileDialogActivity.FORMAT_FILTER, new String[] { "json" });
            startActivityForResult(selectFile, REQUEST_CODE_SELECT_CUSTOM_THEME);
            return;
        }
        final String url = URL_PATH + files.get(position);
        final CancellableTask task = new CancellableTask.BaseCancellableTask();
        final ProgressDialog progressDialog = showProgressDialog(task);
        Async.runAsync(new Runnable() {
            @Override
            public void run() {
                String r;
                try {
                    HttpRequestModel request = HttpRequestModel.DEFAULT_GET;
                    r = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, null, task, false);
                } catch (Exception e) {
                    r = null;
                }
                final String response = r;
                
                if (task.isCancelled()) return;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (task.isCancelled()) return;
                        progressDialog.dismiss();
                        try {
                            MainApplication.getInstance().settings.setCustomTheme(response);
                        } catch (Exception e) {
                            Logger.e(TAG, e);
                            Toast.makeText(CustomThemeListActivity.this,
                                    e.getMessage() != null ? e.getMessage() : e.toString(), Toast.LENGTH_LONG).show();
                        }
                        finish();
                    }
                });
            }
        });
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == REQUEST_CODE_SELECT_CUSTOM_THEME) {
            String path = data.getStringExtra(FileDialogActivity.RESULT_PATH);
            if (path != null) {
                FileInputStream is = null;
                try {
                    is = new FileInputStream(path);
                    byte[] buf = new byte[8192];
                    int size = 0;
                    while (size < buf.length) {
                        int count = is.read(buf, size, buf.length - size);
                        if (count == -1) break;
                        size += count;
                    }
                    MainApplication.getInstance().settings.setCustomTheme(new String(buf, 0, size, "UTF-8"));
                } catch (Exception e) {
                    Logger.e(TAG, e);
                    Toast.makeText(this, e.getMessage() != null ? e.getMessage() : e.toString(), Toast.LENGTH_LONG).show();
                } finally {
                    IOUtils.closeQuietly(is);
                }
                finish();
            }
        }
    }
    
    private ProgressDialog showProgressDialog(final CancellableTask task) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.custom_themes_loading));
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                task.cancel();
            }
        });
        progressDialog.show();
        return progressDialog;
    }
}
