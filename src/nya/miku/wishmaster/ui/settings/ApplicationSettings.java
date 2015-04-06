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

import java.io.File;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.common.CompatibilityImpl;
import nya.miku.wishmaster.ui.FavoritesFragment;
import nya.miku.wishmaster.ui.downloading.DownloadingService;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Environment;

public class ApplicationSettings {
    private final SharedPreferences preferences;
    private final Resources resources;
    private final boolean isTablet;
    
    public ApplicationSettings(SharedPreferences preferences, Resources resources) {
        this.preferences = preferences;
        this.resources = resources;
        this.isTablet = (resources.getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
        
        if (preferences.getString(resources.getString(R.string.pref_key_download_dir), "").equals("")) {
            preferences.edit().putString(resources.getString(R.string.pref_key_download_dir), getDefaultDownloadDir().getAbsolutePath()).commit();
        }
    }
    
    private File getDefaultDownloadDir() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
            return CompatibilityImpl.getDefaultDownloadDir();
        }
        return new File(Environment.getExternalStorageDirectory(), "/Download/");
    }
    
    public boolean isHidePersonalData() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_hide_personal), true);
    }
    
    public String getDefaultName() {
        return preferences.getString(resources.getString(R.string.pref_key_name), "");
    }
    
    public String getDefaultEmail() {
        return preferences.getString(resources.getString(R.string.pref_key_email), "");
    }
    
    public boolean isRandomHash() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_random_hash), false);
    }
    
    public boolean isLocalTime() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_local_time), true);
    }
    
    public boolean isDisplayDate() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_show_date), true);
    }
    
    public boolean isDownloadThumbnails() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_download_thumbs), true);
    }
    
    public boolean isPopupLinks() {
        return true;
    }
    
    public int getItemHeight() {
        int defaultValue = 400;
        String maxHeightStr = preferences.getString(resources.getString(R.string.pref_key_cut_posts), null);
        if (maxHeightStr == null) return defaultValue;

        try {
            int maxHeight = Integer.parseInt(maxHeightStr);
            return maxHeight;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    public int getTheme() {
        String defaultThemeValue = resources.getString(R.string.pref_theme_value_default);
        String defaultFontSizeValue = resources.getString(R.string.pref_font_size_value_default);
        String theme = preferences.getString(resources.getString(R.string.pref_key_theme), defaultThemeValue);
        String fontSize = preferences.getString(resources.getString(R.string.pref_key_font_size), defaultFontSizeValue);
        if (theme.equals(resources.getString(R.string.pref_theme_value_futaba))) {
            if (fontSize.equals(resources.getString(R.string.pref_font_size_value_medium))) return R.style.Futaba_Medium;
            else if (fontSize.equals(resources.getString(R.string.pref_font_size_value_large))) return R.style.Futaba_Large;
            else if (fontSize.equals(resources.getString(R.string.pref_font_size_value_huge))) return R.style.Futaba_Huge;
        } else if (theme.equals(resources.getString(R.string.pref_theme_value_photon))) {
            if (fontSize.equals(resources.getString(R.string.pref_font_size_value_medium))) return R.style.Photon_Medium;
            else if (fontSize.equals(resources.getString(R.string.pref_font_size_value_large))) return R.style.Photon_Large;
            else if (fontSize.equals(resources.getString(R.string.pref_font_size_value_huge))) return R.style.Photon_Huge;
        } else if (theme.equals(resources.getString(R.string.pref_theme_value_neutron))) {
            if (fontSize.equals(resources.getString(R.string.pref_font_size_value_medium))) return R.style.Neutron_Medium;
            else if (fontSize.equals(resources.getString(R.string.pref_font_size_value_large))) return R.style.Neutron_Large;
            else if (fontSize.equals(resources.getString(R.string.pref_font_size_value_huge))) return R.style.Neutron_Huge;
        }
        return R.style.Futaba_Medium;
    }
    
    public long getMaxCacheSize() {
        long defaultValue = 50 * 1024 * 1024;
        String maxCacheSizeStr = preferences.getString(resources.getString(R.string.pref_key_cache_maxsize), null);
        if (maxCacheSizeStr == null) return defaultValue;
        
        try {
            long maxCacheSize = Integer.parseInt(maxCacheSizeStr) * 1024 * 1024;
            return maxCacheSize;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    public boolean isRealTablet() {
        return isTablet;
    }
    
    public float getRootViewWeight() {
        if (!isRealTablet()) return 1.0f;
        String key =
                preferences.getString(resources.getString(R.string.pref_key_sidepanel), resources.getString(R.string.pref_sidepanel_value_default));
        if (key.equals(resources.getString(R.string.pref_sidepanel_value_25percent))) return 0.75f;
        if (key.equals(resources.getString(R.string.pref_sidepanel_value_30percent))) return 0.70f;
        if (key.equals(resources.getString(R.string.pref_sidepanel_value_35percent))) return 0.65f;
        if (key.equals(resources.getString(R.string.pref_sidepanel_value_40percent))) return 0.60f;
        if (key.equals(resources.getString(R.string.pref_sidepanel_value_45percent))) return 0.55f;
        if (key.equals(resources.getString(R.string.pref_sidepanel_value_50percent))) return 0.50f;
        return 1.0f;
    }
    
    public boolean showSidePanel() {
        return getRootViewWeight() != 1.0f;
    }
    
    public boolean isReduceNames() {
        return !isRealTablet();
    }
    
    public File getDownloadDirectory() {
        String dir = preferences.getString(resources.getString(R.string.pref_key_download_dir), null);
        if (dir == null || dir.length() == 0) {
            return getDefaultDownloadDir();
        }
        return new File(dir);
    }
    
    public int getDownloadThreadMode() {
        return preferences.getInt(resources.getString(R.string.pref_key_download_thread_mode), DownloadingService.MODE_DOWNLOAD_THUMBS);
    }
    
    public void saveDownloadThreadMode(int mode) {
        preferences.edit().putInt(resources.getString(R.string.pref_key_download_thread_mode), mode).commit();
    }
    
    public String getAutohideRulesJson() {
        return preferences.getString(resources.getString(R.string.pref_key_autohide_json), "[]");
    }
    
    public void saveAutohideRulesJson(String json) {
        preferences.edit().putString(resources.getString(R.string.pref_key_autohide_json), json).commit();
    }
    
    public int getLastFavoritesPage() {
        return preferences.getInt(resources.getString(R.string.pref_key_last_favorites_page), FavoritesFragment.PAGE_ALL);
    }
    
    public void saveLastFavoritesPage(int page) {
        preferences.edit().putInt(resources.getString(R.string.pref_key_last_favorites_page), page).commit();
    }
    
    public String getDownloadThreadFormat() {
        String defaultFormat = resources.getString(R.string.pref_download_format_value_default);
        String format = preferences.getString(resources.getString(R.string.pref_key_download_format), defaultFormat);
        if (format.equals(resources.getString(R.string.pref_download_format_value_directory))) {
            return "";
        } else if (format.equals(resources.getString(R.string.pref_download_format_value_zip))) {
            return ".zip";
        } else if (format.equals(resources.getString(R.string.pref_download_format_value_mhtml))) {
            return ".mhtml";
        } else {
            return "";
        }
    }
    
    public boolean useScaleImageView() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_gallery_scaleimageview), true);
    }
    
    public boolean useNativeGif() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_gallery_nativegif), true);
    }
    
    public boolean useInternalVideoPlayer() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_gallery_videoplayer), true);
    }
    
    public boolean useInternalAudioPlayer() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_gallery_audioplayer), true);
    }
    
    public boolean showNSFWBoards() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_show_nsfw_boards), false);
    }
    
    public boolean isAutoupdateEnabled() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_enable_autoupdate), false);
    }
    
    public boolean isAutoupdateBackground() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_autoupdate_background), false);
    }
    
    public boolean isAutoupdateNotification() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_autoupdate_notification), false);
    }
    
    public int getAutoupdateDelay() {
        int defaultValue = 60;
        String autoupdateDelayStr = preferences.getString(resources.getString(R.string.pref_key_autoupdate_delay), null);
        if (autoupdateDelayStr == null) return defaultValue;
        
        try {
            int delay = Integer.parseInt(autoupdateDelayStr);
            return delay;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    public StaticSettingsContainer getStaticSettings() {
        StaticSettingsContainer container = new StaticSettingsContainer();
        updateStaticSettings(container);
        return container;
    }
    
    public class StaticSettingsContainer {
        public int theme;
        public int itemHeight;
        public boolean downloadThumbnails;
        public boolean isDisplayDate;
        public boolean isLocalTime;
    }

    public void updateStaticSettings(StaticSettingsContainer container) {
        container.theme = getTheme();
        container.itemHeight = getItemHeight();
        container.downloadThumbnails = isDownloadThumbnails();
        container.isDisplayDate = isDisplayDate();
        container.isLocalTime = isLocalTime();
    }
    
}
