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

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.common.Async;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.lib.FileDialogActivity;
import nya.miku.wishmaster.ui.BoardsListFragment;
import nya.miku.wishmaster.ui.CompatibilityImpl;
import nya.miku.wishmaster.ui.CompatibilityUtils;
import nya.miku.wishmaster.ui.NewTabFragment;
import nya.miku.wishmaster.ui.tabs.TabsTrackerService;
import nya.miku.wishmaster.ui.tabs.UrlHandler;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.InputType;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.Toast;

import java.io.File;

//отсутствует альтернатива, поддерживающая API 4 
@SuppressWarnings("deprecation")

public class PreferencesActivity extends PreferenceActivity {
    private static final int REQUEST_CODE_SELECT_SETTINGS_FILE = 1;
    
    private static final String TAG = "PreferencesActivity";
    
    public static boolean needUpdateChansScreen = false;
    
    private SharedPreferences.OnSharedPreferenceChangeListener sharedPreferenceChangeListener;
    private SharedPreferences sharedPreferences;
    
    private static final int[] KEYS_AUTOUPDATE = new int[] {
            R.string.pref_key_enable_autoupdate,
            R.string.pref_key_autoupdate_delay,
            R.string.pref_key_autoupdate_notification,
            R.string.pref_key_autoupdate_background };
    
    protected void onCreate(Bundle savedInstanceState) {
        MainApplication.getInstance().settings.getTheme().setToPreferencesActivity(this);
        setTitle(R.string.preferences);
        
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
        
        updateChansScreen((PreferenceScreen) getPreferenceManager().findPreference(getString(R.string.pref_key_cat_chans)));
        updateListSummary(R.string.pref_key_theme);
        updateListSummary(R.string.pref_key_font_size);
        updateListSummary(R.string.pref_key_download_thumbs);
        updateListSummary(R.string.pref_key_download_format);
        
        final Preference clearSubscriptionsPreference = getPreferenceManager().findPreference(getString(R.string.pref_key_clear_subscriptions));
        int subscriptionsCount = (int) MainApplication.getInstance().subscriptions.getCurrentCount();
        clearSubscriptionsPreference.setSummary(getResources().getQuantityString(R.plurals.pref_clear_subscriptions_summary,
                subscriptionsCount, subscriptionsCount));
        if (subscriptionsCount > 0) {
            clearSubscriptionsPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    MainApplication.getInstance().subscriptions.reset();
                    int count = (int) MainApplication.getInstance().subscriptions.getCurrentCount();
                    clearSubscriptionsPreference.setSummary(getResources().getQuantityString(R.plurals.pref_clear_subscriptions_summary,
                            count, count));
                    MainApplication.getInstance().settings.setSubscriptionsClear(true);
                    return true;
                }
            });
        }
        
        ((EditTextPreference) getPreferenceManager().findPreference(getString(R.string.pref_key_download_dir))).
                getEditText().setHint(MainApplication.getInstance().settings.getDefaultDownloadDir().getAbsolutePath());
        
        final Preference clearCachePreference = getPreferenceManager().findPreference(getString(R.string.pref_key_clear_cache));
        clearCachePreference.setSummary(getString(R.string.pref_clear_cache_summary, MainApplication.getInstance().fileCache.getCurrentSizeMB()));
        clearCachePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            final ProgressDialog progressDlg = ProgressDialog.show(PreferencesActivity.this, null, getString(R.string.dialog_wait));
                            Async.runAsync(new Runnable() {
                                @Override
                                public void run() {
                                    MainApplication.getInstance().fileCache.clearCache();
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            progressDlg.dismiss();
                                            clearCachePreference.setSummary(getString(R.string.pref_clear_cache_summary,
                                                    MainApplication.getInstance().fileCache.getCurrentSizeMB()));
                                        }
                                    });
                                }
                            });
                        }
                    }
                };
                new AlertDialog.Builder(PreferencesActivity.this).
                        setMessage(R.string.pref_clear_cache_confirmation).
                        setPositiveButton(android.R.string.yes, dialogClickListener).
                        setNegativeButton(android.R.string.no, null).
                        show();
                return true;
            }
        });
        final Preference exportPreference = getPreferenceManager().findPreference(getString(R.string.pref_key_settings_export));
        exportPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                SettingsExporter.Export(MainApplication.getInstance().settings.getDownloadDirectory(), PreferencesActivity.this);
                return true;
            }
        });
        final Preference importPreference = getPreferenceManager().findPreference(getString(R.string.pref_key_settings_import));
        importPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (!CompatibilityUtils.hasAccessStorage(PreferencesActivity.this)) return true;
                Intent selectFile = new Intent(PreferencesActivity.this, FileDialogActivity.class);
                selectFile.putExtra(FileDialogActivity.CAN_SELECT_DIR, false);
                selectFile.putExtra(FileDialogActivity.START_PATH, MainApplication.getInstance().settings.getDownloadDirectory().toString());
                selectFile.putExtra(FileDialogActivity.SELECTION_MODE, FileDialogActivity.SELECTION_MODE_OPEN);
                selectFile.putExtra(FileDialogActivity.FORMAT_FILTER, new String[] { "json" });
                startActivityForResult(selectFile, REQUEST_CODE_SELECT_SETTINGS_FILE);
                return true;
            }
        });
        Preference aboutPreference = getPreferenceManager().findPreference(getString(R.string.pref_key_about_version));
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            aboutPreference.setSummary(versionName);
        } catch (Exception e) {}
        if (MainApplication.getInstance().settings.enableAppUpdateCheck()) {
            aboutPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    AppUpdatesChecker.checkForUpdates(PreferencesActivity.this);
                    return true;
                }
            });
        }
        
        Preference licensePreference = getPreferenceManager().findPreference(getString(R.string.pref_key_about_license));
        licensePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                UrlHandler.launchExternalBrowser(PreferencesActivity.this, "https://www.gnu.org/licenses/gpl-3.0.html");
                return true;
            }
        });
        
        Preference certificatesPreference = getPreferenceManager().findPreference(getString(R.string.pref_key_ssl_certificates));
        if (CertificatesActivity.hasCertificates()) {
            certificatesPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(PreferencesActivity.this, CertificatesActivity.class));
                    return true;
                }
            });
        } else {
            ((PreferenceGroup) getPreferenceManager().findPreference(getString(R.string.pref_key_cat_advanced))).
                    removePreference(certificatesPreference);
        }

        ListPreference thumbnailScalePreference = 
                (ListPreference) getPreferenceManager().findPreference(getString(R.string.pref_key_post_thumbnail_scale));
        thumbnailScalePreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Toast.makeText(PreferencesActivity.this, R.string.pref_key_post_thumbnail_scale_toast, Toast.LENGTH_LONG).show();
                return true;
            }
        });
        
        getPreferenceManager().findPreference(getString(R.string.pref_key_autohide)).setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(PreferencesActivity.this, AutohideActivity.class));
                return true;
            }
        });
        
        getPreferenceManager().findPreference(getString(R.string.pref_key_cache_maxsize)).setOnPreferenceChangeListener(
                new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                int newSize;
                try {
                    newSize = Integer.parseInt(newValue.toString());
                } catch (NumberFormatException e) {
                    newSize = 50;
                }
                MainApplication.getInstance().fileCache.setMaxSize(newSize * 1024 * 1024);
                clearCachePreference.setSummary(getString(R.string.pref_clear_cache_summary,
                        MainApplication.getInstance().fileCache.getCurrentSizeMB()));
                return true;
            }
        });
        
        getPreferenceManager().findPreference(getString(R.string.pref_key_autoupdate_delay)).setOnPreferenceChangeListener(
                new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                String newValueStr = newValue.toString();
                if (newValueStr.length() == 0) return true;
                try {
                    int intVal = Integer.parseInt(newValueStr);
                    if (intVal < 30) throw new NumberFormatException();
                    return true;
                } catch (NumberFormatException e) {
                    Toast.makeText(PreferencesActivity.this, R.string.pref_autoupdate_delay_incorrect, Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });
        
        getPreferenceManager().findPreference(getString(R.string.pref_key_theme)).setOnPreferenceChangeListener(
                new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        if (getString(R.string.pref_theme_value_custom).equals(newValue)) {
                            startActivity(new Intent(PreferencesActivity.this, CustomThemeListActivity.class));
                            return false;
                        }
                        return true;
                    }
        });
        
        sharedPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                Preference preference = getPreferenceManager().findPreference(key);
                if (preference instanceof ListPreference) {
                    updateListSummary(key);
                } else {
                    for (int autoupdateKey : KEYS_AUTOUPDATE) {
                        if (getString(autoupdateKey).equals(key)) {
                            if (TabsTrackerService.isRunning())
                                stopService(new Intent(PreferencesActivity.this, TabsTrackerService.class));
                            if (MainApplication.getInstance().settings.isAutoupdateEnabled())
                                startService(new Intent(PreferencesActivity.this, TabsTrackerService.class));
                        }
                    }
                    if (getString(R.string.pref_key_show_nsfw_boards).equals(key)) {
                        if (MainApplication.getInstance().tabsSwitcher.currentFragment instanceof BoardsListFragment) {
                            ((BoardsListFragment) MainApplication.getInstance().tabsSwitcher.currentFragment).updateList();
                        }
                    } else if (getString(R.string.pref_key_show_all_chans_list).equals(key)) {
                        if (MainApplication.getInstance().tabsSwitcher.currentFragment instanceof NewTabFragment) {
                            ((NewTabFragment) MainApplication.getInstance().tabsSwitcher.currentFragment).updateList();
                        }
                    }
                }
            }
        };
        
        if (MainApplication.getInstance().settings.isSFWRelease()) {
            Preference p = getPreferenceManager().findPreference(getString(R.string.pref_key_show_all_chans_list));
            ((PreferenceGroup) getPreferenceManager().findPreference(getString(R.string.pref_key_cat_advanced))).removePreference(p);
        }
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD_MR1) {
            Preference p = getPreferenceManager().findPreference(getString(R.string.pref_key_gallery_scaleimageview));
            ((PreferenceGroup) getPreferenceManager().findPreference(getString(R.string.pref_key_gallery_screen))).removePreference(p);
        }
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            Preference p = getPreferenceManager().findPreference(getString(R.string.pref_key_gallery_fullscreen));
            ((PreferenceGroup) getPreferenceManager().findPreference(getString(R.string.pref_key_gallery_screen))).removePreference(p);
        }
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            Preference p = getPreferenceManager().findPreference(getString(R.string.pref_key_hide_actionbar_on_scroll));
            ((PreferenceGroup) getPreferenceManager().findPreference(getString(R.string.pref_key_cat_appearance))).removePreference(p);
        }
        
        if (!MainApplication.getInstance().settings.isRealTablet()) {
            Preference pHide = getPreferenceManager().findPreference(getString(R.string.pref_key_sidepanel_hide));
            Preference pWidth = getPreferenceManager().findPreference(getString(R.string.pref_key_sidepanel_width));
            Preference pRight = getPreferenceManager().findPreference(getString(R.string.pref_key_sidepanel_right));
            PreferenceGroup appearanceGroup = (PreferenceGroup) getPreferenceManager().findPreference(getString(R.string.pref_key_cat_appearance));
            appearanceGroup.removePreference(pHide);
            appearanceGroup.removePreference(pWidth);
            appearanceGroup.removePreference(pRight);
        } else {
            updateListSummary(R.string.pref_key_sidepanel_width);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        sharedPreferences.registerOnSharedPreferenceChangeListener(this.sharedPreferenceChangeListener);
        if (needUpdateChansScreen) {
            updateChansScreen((PreferenceScreen) getPreferenceManager().findPreference(getString(R.string.pref_key_cat_chans)));
        }
        
        ListPreference themePreference = ((ListPreference) getPreferenceManager().findPreference(getString(R.string.pref_key_theme)));
        String currentValue = sharedPreferences.getString(getString(R.string.pref_key_theme), "");
        if (!currentValue.equals("") && !currentValue.equals(themePreference.getValue())) {
            themePreference.setValue(currentValue);
            updateListSummary(R.string.pref_key_theme);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this.sharedPreferenceChangeListener);
    }
    
    private void updateListSummary(int prefKeyId) {
        this.updateListSummary(this.getString(prefKeyId));
    }

    private void updateListSummary(String prefKey) {
        ListPreference preference = (ListPreference) getPreferenceManager().findPreference(prefKey);
        preference.setSummary(preference.getEntry());
    }
    
    private void updateChansScreen(final PreferenceScreen chansCat) {
        needUpdateChansScreen = false;
        chansCat.removeAll();
        
        Preference enterUrl = new Preference(this);
        enterUrl.setTitle(R.string.pref_chans_enter_url);
        enterUrl.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final EditText inputField = new EditText(PreferencesActivity.this);
                inputField.setSingleLine();
                inputField.setHint(R.string.pref_chans_enter_url_hint);
                inputField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
                
                DialogInterface.OnClickListener dlgOnClick = new DialogInterface.OnClickListener() {
                    private boolean openPreference(String key) {
                        try {
                            ListAdapter adapter = chansCat.getRootAdapter();
                            for (int i=0, size=adapter.getCount(); i<size; ++i) {
                                Object object = adapter.getItem(i);
                                if (!(object instanceof Preference)) continue;
                                Preference preference = (Preference) object;
                                if (key.equals(preference.getKey())) {
                                    chansCat.onItemClick(null, null, i, 0);
                                    return true;
                                }
                            }
                        } catch (Exception e) {
                            Logger.e(TAG, e);
                        }
                        return false;
                    }
                    
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        String url = inputField.getText().toString();
                        if (url == null || url.length() == 0) return;
                        UrlPageModel model = UrlHandler.getPageModel(url);
                        if (model == null || model.chanName == null) {
                            Toast.makeText(PreferencesActivity.this, R.string.pref_chans_enter_url_incorrect, Toast.LENGTH_LONG).show();
                        } else {
                            final String key = "chan_preference_screen_" + model.chanName;
                            if (openPreference(key)) return;
                            updateChansScreen(chansCat);
                            //need wait for Root Adapter synchronization (it will be called from UI thread)
                            Async.runAsync(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        Thread.sleep(200);
                                        PreferencesActivity.this.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                openPreference(key);
                                            }
                                        });
                                    } catch (Exception e) {
                                        Logger.e(TAG, e);
                                    }
                                }
                            });
                        }
                    }
                };
                
                new AlertDialog.Builder(PreferencesActivity.this).
                        setTitle(R.string.pref_chans_enter_url).
                        setView(inputField).
                        setPositiveButton(android.R.string.ok, dlgOnClick).
                        create().
                        show();
                return true;
            }
        });
        chansCat.addPreference(enterUrl);
        
        ApplicationSettings settings = MainApplication.getInstance().settings;
        int visibleChansCount = 0;
        for (ChanModule chan : MainApplication.getInstance().chanModulesList) {
            if (!settings.isUnlockedChan(chan.getChanName())) continue;
            PreferenceScreen curScreen = getPreferenceManager().createPreferenceScreen(this);
            curScreen.setTitle(chan.getDisplayingName());
            curScreen.setKey("chan_preference_screen_" + chan.getChanName());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                CompatibilityImpl.setIcon(curScreen, chan.getChanFavicon());
            }
            chansCat.addPreference(curScreen);
            chan.addPreferencesOnScreen(curScreen);
            ++visibleChansCount;
        }
        
        if (visibleChansCount >= 2) {
            Preference rearrange = new Preference(this);
            rearrange.setTitle(R.string.pref_chans_rearrange);
            rearrange.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(PreferencesActivity.this, ChansSortActivity.class));
                    return true;
                }
            });
            chansCat.addPreference(rearrange);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == REQUEST_CODE_SELECT_SETTINGS_FILE) {
            final String path = data.getStringExtra(FileDialogActivity.RESULT_PATH);
            DialogInterface.OnClickListener onClickYes = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    SettingsImporter.Import(new File(path), MainApplication.getInstance().settings.getImportOverwrite(), PreferencesActivity.this);
                }
            };
            if (MainApplication.getInstance().settings.getImportOverwrite()) {
                new AlertDialog.Builder(this).
                        setTitle(R.string.app_import_overwrite_warning).
                        setMessage(R.string.app_import_overwrite_warning_message).
                        setPositiveButton(android.R.string.yes, onClickYes).
                        setNegativeButton(android.R.string.no, null).
                        show();
            } else {
                SettingsImporter.Import(new File(path), MainApplication.getInstance().settings.getImportOverwrite(), this);
            }
        }
    }
    
}
