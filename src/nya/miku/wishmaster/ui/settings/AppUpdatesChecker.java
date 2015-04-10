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

package nya.miku.wishmaster.ui.settings;

import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.PriorityThreadFactory;
import nya.miku.wishmaster.http.client.ExtendedHttpClient;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.org_json.JSONObject;
import nya.miku.wishmaster.ui.tabs.UrlHandler;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.widget.Toast;

public class AppUpdatesChecker {
    private static final String URL = "https://raw.githubusercontent.com/miku-nyan/Overchan-Android/master/version.json";
    
    public static void checkForUpdates(final Activity activity) {
        
        final Handler handler = new Handler();
        final CancellableTask task = new CancellableTask.BaseCancellableTask();
        final ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setMessage("Checking");
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                task.cancel();
            }
        });
        progressDialog.show();
        PriorityThreadFactory.LOW_PRIORITY_FACTORY.newThread(new Runnable() {
            @Override
            public void run() {
                JSONObject response;
                try {
                    ExtendedHttpClient httpClient = new ExtendedHttpClient(false, true, null);
                    HttpRequestModel request = HttpRequestModel.builder().setGET().build();
                    response = HttpStreamer.getInstance().getJSONObjectFromUrl(URL, request, httpClient, null, task, false);
                } catch (Exception e) {
                    response = null;
                }
                process(response);
            }
            private void process(final JSONObject result) {
                if (task.isCancelled()) return;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (task.isCancelled()) return;
                        progressDialog.dismiss();
                        try {
                            if (result == null) throw new Exception();
                            int newVersion = result.getInt("versionCode");
                            int currentVersion = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionCode;
                            if (newVersion > currentVersion) {
                                final String newVersionUrl = result.getString("url");
                                String newVersionName = result.getString("description");
                                DialogInterface.OnClickListener onClickYes = new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        UrlHandler.launchExternalBrowser(activity, newVersionUrl);
                                    }
                                };
                                new AlertDialog.Builder(activity).
                                        setTitle("Доступно обновление").
                                        setMessage("Доступна новая версия: "+newVersionName).
                                        setPositiveButton(android.R.string.yes, onClickYes).
                                        setNegativeButton(android.R.string.no, null).
                                        show();
                            } else {
                                Toast.makeText(activity, "Обновление не требуется", Toast.LENGTH_LONG).show();
                            }
                        } catch (Exception e) {
                            Toast.makeText(activity, "Не удалось проверить обновления", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }
}
