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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.List;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.Async;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONObject;
import nya.miku.wishmaster.ui.Database;
import nya.miku.wishmaster.ui.presentation.Subscriptions;
import nya.miku.wishmaster.ui.tabs.TabModel;

import static nya.miku.wishmaster.ui.settings.ImportExportConstants.*;

public class SettingsExporter {
    private static final String TAG = "SettingsExporter";
    
    @SuppressWarnings("unchecked")
    private static <T> T[] ListToArray(List<T> list, Class<?> itemClass) {
        return (T[]) list.toArray((T[]) Array.newInstance(itemClass, list.size()));
    }

    /**
     * Преобразует информацию о вкладках в формат JSON
     *
     * @return
     */
    private static JSONArray getPagesJSON(){
        List<TabModel> list = MainApplication.getInstance().tabsState.tabsArray;
        JSONArray result = new JSONArray();
        String[] fields = {"type", "chanName", "boardName", "boardPage", "catalogType", "threadNumber", "postNumber", "searchRequest", "otherPath"};
        for (TabModel tab : list){
            JSONObject page = new JSONObject(tab.pageModel, fields);
            page.put("title", tab.title);
            result.put(page);
        }
        return result;
    }

    /**
     * Экспорт с сохранением в файл
     * @param dir директория куда будет сохранен файл
     * @param activity
     */
    public static void Export(final File dir, final Activity activity) {
        Export(dir, activity, null);
    }

    /**
     * Экспорт без сохранения в файл. Передает результат экспорта через callback
     *
     * @param activity
     * @param callback интерфейс для обратного вызова по завершению экспорта
     */
    public static void Export(final Activity activity, final exportComplete callback) {
        Export(null, activity, callback);
    }

    /**
     * @param dir      директория куда будет сохранен файл, может быть null
     * @param activity
     * @param callback интерфейс для обратного вызова по завершению экспорта, может быть null
     */
    private static void Export(final File dir, final Activity activity, final exportComplete callback) {
        final CancellableTask task = new CancellableTask.BaseCancellableTask();
        final ProgressDialog progressDialog = new ProgressDialog(activity);
        final Database database = MainApplication.getInstance().database;
        progressDialog.setMessage(activity.getString(R.string.app_settings_exporting));
        progressDialog.setIndeterminate(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(6);
        progressDialog.setProgress(0);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                task.cancel();
            }
        });
        progressDialog.show();
        Async.runAsync(new Runnable() {
            @Override
            public void run() {
                String filename = "";
                try {
                    filename = Export();
                } catch (Exception e){
                    showMessage(e.getMessage());
                    Logger.e(TAG, e);
                    return;
                }
                showMessage(activity.getString(R.string.app_settings_export_completed) + "\n" + filename);
            }

            private String Export() throws Exception {
                JSONObject json = new JSONObject();
                json.put(JSON_KEY_VERSION, MainApplication.getInstance().getPackageManager().getPackageInfo(MainApplication.getInstance().getPackageName(), 0).versionCode);
                json.put(JSON_KEY_HISTORY,
                        new JSONArray(
                                ListToArray(
                                        database.getHistory(),
                                        Database.HistoryEntry.class
                                )
                        )
                );
                if (task.isCancelled()) throw new Exception("Interrupted");
                updateProgress();
                json.put(JSON_KEY_FAVORITES,
                        new JSONArray(
                                ListToArray(
                                        database.getFavorites(),
                                        Database.FavoritesEntry.class
                                )
                        )
                );
                if (task.isCancelled()) throw new Exception("Interrupted");
                updateProgress();
                json.put(JSON_KEY_HIDDEN,
                        new JSONArray(
                                ListToArray(
                                        database.getHidden(),
                                        Database.HiddenEntry.class
                                )
                        )
                );
                if (task.isCancelled()) throw new Exception("Interrupted");
                updateProgress();
                json.put(JSON_KEY_SUBSCRIPTIONS,
                        new JSONArray(
                                ListToArray(
                                        MainApplication.getInstance().subscriptions.getSubscriptions(),
                                        Subscriptions.SubscriptionEntry.class
                                )
                        )
                );
                if (task.isCancelled()) throw new Exception("Interrupted");
                updateProgress();
                json.put(JSON_KEY_PREFERENCES, new JSONObject(MainApplication.getInstance().settings.getSharedPreferences()));
                updateProgress();
                json.put(JSON_KEY_TABS, getPagesJSON());
                if (task.isCancelled()) throw new Exception("Interrupted");
                updateProgress();
                if (dir != null) {
                    File filename = new File(dir, "Overchan_settings_" + System.currentTimeMillis() + ".json");
                    saveToFile(filename, json.toString());
                    return filename.toString();
                } else {
                    if (callback != null) {
                        callback.onExportComplete(json.toString());
                    }
                }
                return "";
            }

            private void saveToFile(final File filename, final String s) throws IOException {
                FileOutputStream outputStream = new FileOutputStream(filename);
                outputStream.write(s.getBytes());
                outputStream.close();
            }
            
            private void updateProgress() {
                if (task.isCancelled()) return;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (task.isCancelled()) return;
                        try {
                            progressDialog.incrementProgressBy(1);
                        } catch (Exception e) {
                            Logger.e(TAG, e);
                            return;
                        }
                    }
                });
            }

            private void showMessage(final String message) {
                if (task.isCancelled()) return;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (task.isCancelled()) return;
                        try {
                            progressDialog.dismiss();
                        } catch (Exception e) {
                            Logger.e(TAG, e);
                            return;
                        }
                        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    /**
     * Интерфейс для реализации обратного вызова по завершению экспорта
     */
    public interface exportComplete {
        /**
         * @param json результат экспорта
         */
        void onExportComplete(String json);
    }
}
