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

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.common.CompatibilityImpl;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.ui.BoardsListFragment;
import nya.miku.wishmaster.ui.tabs.TabsTrackerService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.widget.Toast;

//отсутствует альтернатива, поддерживающая API 4 
@SuppressWarnings("deprecation")

public class PreferencesActivity extends PreferenceActivity {
    private SharedPreferences.OnSharedPreferenceChangeListener sharedPreferenceChangeListener;
    private SharedPreferences sharedPreferences;
    
    private static final int[] KEYS_AUTOUPDATE = new int[] {
            R.string.pref_key_enable_autoupdate,
            R.string.pref_key_autoupdate_delay,
            R.string.pref_key_autoupdate_notification,
            R.string.pref_key_autoupdate_background };
    
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB_MR2 || Build.VERSION.SDK_INT < Build.VERSION_CODES.ECLAIR_0_1 ?
                MainApplication.getInstance().settings.getTheme() : R.style.Neutron_Medium);
        setTitle(R.string.preferences);
        
        super.onCreate(savedInstanceState);
        PreferenceScreen rootScreen = getPreferenceManager().createPreferenceScreen(this);
        setPreferenceScreen(rootScreen);
        
        PreferenceScreen chansCat = getPreferenceManager().createPreferenceScreen(this);
        chansCat.setTitle(R.string.pref_cat_chans);
        rootScreen.addPreference(chansCat);
        for (ChanModule chan : MainApplication.getInstance().chanModulesList) {
            PreferenceScreen curScreen = getPreferenceManager().createPreferenceScreen(this);
            curScreen.setTitle(chan.getDisplayingName());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                CompatibilityImpl.setIcon(curScreen, chan.getChanFavicon());
            }
            chansCat.addPreference(curScreen);
            chan.addPreferencesOnScreen(curScreen);
        }
        
        addPreferencesFromResource(R.xml.preferences);
        
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
        
        updateListSummary(R.string.pref_key_theme);
        updateListSummary(R.string.pref_key_font_size);
        updateListSummary(R.string.pref_key_download_format);
        
        final Preference clearCachePreference = getPreferenceManager().findPreference(getString(R.string.pref_key_clear_cache));
        clearCachePreference.setSummary(getString(R.string.pref_clear_cache_summary, MainApplication.getInstance().fileCache.getCurrentSizeMB()));
        clearCachePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                MainApplication.getInstance().fileCache.clearCache();
                clearCachePreference.setSummary(getString(R.string.pref_clear_cache_summary,
                        MainApplication.getInstance().fileCache.getCurrentSizeMB()));
                return true;
            }
        });
        
        String versionName = "";
        try {
            versionName += getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {}
        Preference aboutPreference = getPreferenceManager().findPreference(getString(R.string.pref_key_about_updateapp));
        aboutPreference.setTitle(getString(R.string.pref_about_updateapp_title, versionName));
        aboutPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                AppUpdatesChecker.checkForUpdates(PreferencesActivity.this);
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
        
        sharedPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                Preference preference = getPreferenceManager().findPreference(key);
                if (preference instanceof ListPreference) {
                    updateListSummary(key);
                } else {
                    for (int autoupdateKey : KEYS_AUTOUPDATE) {
                        if (getString(autoupdateKey).equals(key)) {
                            if (TabsTrackerService.running)
                                stopService(new Intent(PreferencesActivity.this, TabsTrackerService.class));
                            if (MainApplication.getInstance().settings.isAutoupdateEnabled())
                                startService(new Intent(PreferencesActivity.this, TabsTrackerService.class));
                        }
                    }
                    if (getString(R.string.pref_key_show_nsfw_boards).equals(key)) {
                        if (MainApplication.getInstance().tabsSwitcher.currentFragment instanceof BoardsListFragment) {
                            ((BoardsListFragment) MainApplication.getInstance().tabsSwitcher.currentFragment).updateList();
                        }
                    }
                }
            }
        };
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD_MR1) {
            Preference p = getPreferenceManager().findPreference(getString(R.string.pref_key_gallery_scaleimageview));
            ((PreferenceGroup) getPreferenceManager().findPreference(getString(R.string.pref_key_gallery_screen))).removePreference(p);
        }
        
        if (!MainApplication.getInstance().settings.isRealTablet()) {
            Preference p = getPreferenceManager().findPreference(getString(R.string.pref_key_sidepanel));
            ((PreferenceGroup) getPreferenceManager().findPreference(getString(R.string.pref_key_cat_appearance))).removePreference(p);
        } else {
            updateListSummary(R.string.pref_key_sidepanel);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        sharedPreferences.registerOnSharedPreferenceChangeListener(this.sharedPreferenceChangeListener);
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
}
