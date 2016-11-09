package nya.miku.wishmaster.ui.settings;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
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

public class SettingsExporter {
    private static final String TAG = "SettingsExporter";
    
    @SuppressWarnings("unchecked")
    private static <T> T[] ListToArray(List<T> list, Class<?> itemClass) {
        return (T[]) list.toArray((T[]) Array.newInstance(itemClass, list.size()));
    }

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
    
    public static void Export(final File dir, final Activity activity) {
        final CancellableTask task = new CancellableTask.BaseCancellableTask();
        final ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setMessage(activity.getString(R.string.app_settings_exporting));
        progressDialog.setIndeterminate(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(6);
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
                updateProgress(0);
                JSONObject json = new JSONObject();
                json.put("version", MainApplication.getInstance().getPackageManager().getPackageInfo(MainApplication.getInstance().getPackageName(), 0).versionCode);
                json.put("history",
                        new JSONArray(
                                ListToArray(
                                        MainApplication.getInstance().database.getHistory(),
                                        Database.HistoryEntry.class
                                )
                        )
                );
                if (task.isCancelled()) throw new Exception("Interrupted");
                updateProgress(1);
                json.put("favorites",
                        new JSONArray(
                                ListToArray(
                                        MainApplication.getInstance().database.getFavorites(),
                                        Database.FavoritesEntry.class
                                )
                        )
                );
                if (task.isCancelled()) throw new Exception("Interrupted");
                updateProgress(2);
                json.put("hidden",
                        new JSONArray(
                                ListToArray(
                                        MainApplication.getInstance().database.getHidden(),
                                        Database.HiddenEntry.class
                                )
                        )
                );
                if (task.isCancelled()) throw new Exception("Interrupted");
                updateProgress(3);
                json.put("subscriptions",
                        new JSONArray(
                                ListToArray(
                                        MainApplication.getInstance().subscriptions.getSubscriptions(),
                                        Subscriptions.SubscriptionEntry.class
                                )
                        )
                );
                if (task.isCancelled()) throw new Exception("Interrupted");
                updateProgress(4);
                json.put("preferences", new JSONObject(MainApplication.getInstance().settings.getSharedPreferences()));
                updateProgress(5);
                json.put("tabs", getPagesJSON());
                if (task.isCancelled()) throw new Exception("Interrupted");
                updateProgress(6);
                File filename = new File(dir, "Overchan_settings_" + System.currentTimeMillis() + ".json");
                FileOutputStream outputStream = null;
                outputStream = new FileOutputStream(filename);
                outputStream.write(json.toString().getBytes());
                outputStream.close();
                return filename.toString();
            }
            
            private void updateProgress(final int progress) {
                if (task.isCancelled()) return;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (task.isCancelled()) return;
                        try {
                            progressDialog.setProgress(progress);
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
}
