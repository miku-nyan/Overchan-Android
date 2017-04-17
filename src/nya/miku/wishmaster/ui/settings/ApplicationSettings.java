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

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.lib.org_json.JSONException;
import nya.miku.wishmaster.lib.org_json.JSONObject;
import nya.miku.wishmaster.ui.CompatibilityImpl;
import nya.miku.wishmaster.ui.FavoritesFragment;
import nya.miku.wishmaster.ui.downloading.DownloadingService;
import nya.miku.wishmaster.ui.theme.GenericThemeEntry;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Environment;

public class ApplicationSettings {
    private final SharedPreferences preferences;
    private final Resources resources;
    private final boolean isTablet;
    private final boolean isSFW;
    
    public ApplicationSettings(SharedPreferences preferences, Resources resources) {
        this.preferences = preferences;
        this.resources = resources;
        this.isTablet = (resources.getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
        this.isSFW = !R.class.getPackage().getName().endsWith(".wishmaster");
    }
    
    public File getDefaultDownloadDir() {
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

    public boolean scrollToActiveTab() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_scroll_to_active_tab), true);
    }

    public enum DownloadThumbnailsMode {
        ALWAYS,
        WIFI_ONLY,
        NEVER
    }
    
    public DownloadThumbnailsMode isDownloadThumbnails() {
        String defaultMode = resources.getString(R.string.pref_download_thumbs_value_default);
        String format = preferences.getString(resources.getString(R.string.pref_key_download_thumbs), defaultMode);
        if (format.equals(resources.getString(R.string.pref_download_thumbs_value_always))) {
            return DownloadThumbnailsMode.ALWAYS;
        } else if (format.equals(resources.getString(R.string.pref_download_thumbs_value_wifi_only))) {
            return DownloadThumbnailsMode.WIFI_ONLY;
        } else if (format.equals(resources.getString(R.string.pref_download_thumbs_value_never))) {
            return DownloadThumbnailsMode.NEVER;
        } else {
            return DownloadThumbnailsMode.ALWAYS;
        }
    }
    
    public boolean isLazyDownloading() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_download_lazy), true);
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
    
    public boolean openSpoilers() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_open_spoilers), true);
    }
    
    public boolean repliesOnlyQuantity() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_replies_only_quantity), false);
    }
    
    public boolean swipeToHideThread() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_swipe_to_hide_thread), false);
    }
    
    public boolean showHiddenItems() {
        return true;
        //return !preferences.getBoolean(resources.getString(R.string.pref_key_hide_completely), false);
    }
    
    public boolean hideActionBar() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_hide_actionbar_on_scroll), false);
    }
    
    public GenericThemeEntry getTheme() {
        if (isCustomTheme()) {
            try {
                String jsonData = preferences.getString(resources.getString(R.string.pref_key_custom_theme_json), null);
                if (jsonData != null) return GenericThemeEntry.customTheme(jsonData, getFontSizeStyle());
            } catch (Exception e) {
            }
        }
        return GenericThemeEntry.standartTheme(getThemeId(), getFontSizeStyle());
    }
    
    public void setCustomTheme(String jsonData) {
        String normalized = GenericThemeEntry.customTheme(jsonData, getFontSizeStyle()).toJsonString();
        preferences.edit().
                putString(resources.getString(R.string.pref_key_theme), resources.getString(R.string.pref_theme_value_custom)).
                putString(resources.getString(R.string.pref_key_custom_theme_json), normalized).
                commit();
    }
    
    private boolean isCustomTheme() {
        String defaultThemeValue = resources.getString(R.string.pref_theme_value_default);
        String theme = preferences.getString(resources.getString(R.string.pref_key_theme), defaultThemeValue);
        return theme.equals(resources.getString(R.string.pref_theme_value_custom));
    }
    
    private int getThemeId() {
        String defaultThemeValue = resources.getString(R.string.pref_theme_value_default);
        String theme = preferences.getString(resources.getString(R.string.pref_key_theme), defaultThemeValue);
        if (theme.equals(resources.getString(R.string.pref_theme_value_futaba))) return R.style.Theme_Futaba;
        if (theme.equals(resources.getString(R.string.pref_theme_value_photon))) return R.style.Theme_Photon;
        if (theme.equals(resources.getString(R.string.pref_theme_value_neutron))) return R.style.Theme_Neutron;
        if (theme.equals(resources.getString(R.string.pref_theme_value_gurochan))) return R.style.Theme_Gurochan;
        if (theme.equals(resources.getString(R.string.pref_theme_value_tomorrow))) return R.style.Theme_Tomorrow;
        if (theme.equals(resources.getString(R.string.pref_theme_value_mikuba))) return R.style.Theme_Mikuba;
        if (theme.equals(resources.getString(R.string.pref_theme_value_dark_mint))) return R.style.Theme_Dark_Mint;
        return R.style.Theme_Futaba;
    }
    
    private int getFontSizeStyle() {
        String defaultFontSizeValue = resources.getString(R.string.pref_font_size_value_default);
        String fontSize = preferences.getString(resources.getString(R.string.pref_key_font_size), defaultFontSizeValue);
        if (fontSize.equals(resources.getString(R.string.pref_font_size_value_small))) return R.style.FontSize_Small;
        if (fontSize.equals(resources.getString(R.string.pref_font_size_value_medium))) return R.style.FontSize_Medium;
        if (fontSize.equals(resources.getString(R.string.pref_font_size_value_large))) return R.style.FontSize_Large;
        if (fontSize.equals(resources.getString(R.string.pref_font_size_value_huge))) return R.style.FontSize_Huge;
        return R.style.FontSize_Small;
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
    
    public boolean isTabsPanelOnRight() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_sidepanel_right), false);
    }
    
    public float getRootViewWeight() {
        if (!isRealTablet()) return 1.0f;
        boolean isHidden = preferences.getBoolean(resources.getString(R.string.pref_key_sidepanel_hide), false);
        if (isHidden) return 1.0f;
        return 1.0f - getTabPanelTabletWeight();
    }
    
    public float getTabPanelTabletWeight() {
        String key = preferences.getString(resources.getString(R.string.pref_key_sidepanel_width),
                resources.getString(R.string.pref_sidepanel_width_value_default));
        if (key.equals(resources.getString(R.string.pref_sidepanel_width_value_15percent))) return 0.15f;
        if (key.equals(resources.getString(R.string.pref_sidepanel_width_value_20percent))) return 0.20f;
        if (key.equals(resources.getString(R.string.pref_sidepanel_width_value_25percent))) return 0.25f;
        if (key.equals(resources.getString(R.string.pref_sidepanel_width_value_30percent))) return 0.30f;
        if (key.equals(resources.getString(R.string.pref_sidepanel_width_value_35percent))) return 0.35f;
        if (key.equals(resources.getString(R.string.pref_sidepanel_width_value_40percent))) return 0.40f;
        if (key.equals(resources.getString(R.string.pref_sidepanel_width_value_45percent))) return 0.45f;
        if (key.equals(resources.getString(R.string.pref_sidepanel_width_value_50percent))) return 0.50f;
        return 0.30f;
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
    
    public String getQuickAccessListJson() {
        return preferences.getString(resources.getString(R.string.pref_key_quickaccess_json), "[{}]");
    }
    
    public void saveQuickAccessListJson(String json) {
        preferences.edit().putString(resources.getString(R.string.pref_key_quickaccess_json), json).commit();
    }
    
    public String getChansOrderJson() {
        return preferences.getString(resources.getString(R.string.pref_key_chans_order_json), "[]");
    }
    
    public void saveChansOrderJson(String json) {
        preferences.edit().putString(resources.getString(R.string.pref_key_chans_order_json), json).commit();
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
    
    public boolean fullscreenGallery() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) return false;
        return preferences.getBoolean(resources.getString(R.string.pref_key_gallery_fullscreen), false);
    }
    
    public boolean swipeToCloseGallery() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_gallery_swipe_to_close), true);
    }
    
    public boolean scrollThreadFromGallery() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_gallery_scroll_thread), false);
    }
    
    public boolean useScaleImageView() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_gallery_scaleimageview), true);
    }
    
    public boolean useNativeGif() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_gallery_nativegif), true);
    }
    
    public boolean fallbackWebView() {
        return false;
    }
    
    public boolean useInternalVideoPlayer() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_gallery_videoplayer), true);
    }
    
    public boolean doNotDownloadVideos() {
        return !useInternalVideoPlayer() && preferences.getBoolean(resources.getString(R.string.pref_key_do_not_download_videos), false);
    }
    
    public boolean useInternalAudioPlayer() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_gallery_audioplayer), true);
    }
    
    public boolean isSFWRelease() {
        return isSFW;
    }
    
    public boolean useFakeBrowser() {
        return isSFWRelease();
    }
    
    public boolean enableAppUpdateCheck() {
        return !isSFWRelease();
    }
    
    public boolean showAllChansList() {
        if (isSFWRelease()) return false;
        return preferences.getBoolean(resources.getString(R.string.pref_key_show_all_chans_list), false);
    }
    
    public boolean showNSFWBoards() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_show_nsfw_boards), false);
    }
    
    public boolean maskPictures() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_mask_pictures), false);
    }
    
    public void setMaskPictures(boolean value) {
        preferences.edit().putBoolean(resources.getString(R.string.pref_key_mask_pictures), value).commit();
    }
    
    public boolean isUnlockedChan(String chanName) {
        if (chanName == null) return false;
        if (!isSFWRelease()) return true;
        return preferences.getBoolean(resources.getString(R.string.pref_key_unlocked_chan_format, chanName), false);
    }
    
    public void unlockChan(String chanName, boolean value) {
        if (!isSFWRelease() || chanName == null) return;
        preferences.edit().putBoolean(resources.getString(R.string.pref_key_unlocked_chan_format, chanName), value).commit();
    }
    
    public boolean askExternalLinks() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_external_links_confirmation), true);
    }
    
    public boolean doNotCloseTabs() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_do_not_close_tabs), false);
    }
    
    public boolean scrollVolumeButtons() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_scroll_volume_buttons), false);
    }
    
    public boolean preferencesSubmenu() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_preferences_submenu), true);
    }
    
    public boolean isPinnedMarkup() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_pin_markup), false);
    }
    
    public boolean isAutoupdateEnabled() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_enable_autoupdate), false);
    }
    
    public void setAutoupdateEnabled(boolean value) {
        preferences.edit().putBoolean(resources.getString(R.string.pref_key_enable_autoupdate), value).commit();
    }
    
    public boolean isAutoupdateWifiOnly() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_autoupdate_only_wifi), false);
    }
    
    public boolean isAutoupdateBackground() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_autoupdate_background), true);
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
    
    public boolean isSubscriptionsEnabled() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_subscriptions_enabled), true);
    }
    
    public boolean subscribeThreads() {
        return isSubscriptionsEnabled() && preferences.getBoolean(resources.getString(R.string.pref_key_subscribe_threads), false);
    }
    
    public boolean highlightSubscriptions() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_highlight_subscriptions), true);
    }
    
    public boolean subscribeOwnPosts() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_subscribe_own), true);
    }
    
    public boolean subscriptionsClear() {
        return preferences.getBoolean(resources.getString(R.string.pref_key_last_clear_subscriptions), false);
    }
    
    public void setSubscriptionsClear(boolean value) {
        preferences.edit().putBoolean(resources.getString(R.string.pref_key_last_clear_subscriptions), value).commit();
    }
    
    public StaticSettingsContainer getStaticSettings() {
        StaticSettingsContainer container = new StaticSettingsContainer();
        updateStaticSettings(container);
        return container;
    }
    
    public class StaticSettingsContainer {
        public int itemHeight;
        public DownloadThumbnailsMode downloadThumbnails;
        public boolean isDisplayDate;
        public boolean isLocalTime;
        public boolean repliesOnlyQuantity;
        public boolean showHiddenItems;
        public boolean maskPictures;
        public boolean hideActionBar;
        public boolean scrollVolumeButtons;
    }

    public void updateStaticSettings(StaticSettingsContainer container) {
        container.itemHeight = getItemHeight();
        container.downloadThumbnails = isDownloadThumbnails();
        container.isDisplayDate = isDisplayDate();
        container.isLocalTime = isLocalTime();
        container.repliesOnlyQuantity = repliesOnlyQuantity();
        container.showHiddenItems = showHiddenItems();
        container.maskPictures = maskPictures();
        container.hideActionBar = hideActionBar();
        container.scrollVolumeButtons = scrollVolumeButtons();
    }

    public Map<String, Object> getSharedPreferences(){
        return new HashMap<String, Object>(preferences.getAll());
    }

    public void setSharedPreferences(JSONObject json){
        Map<String, Object> preferencesMap = getSharedPreferences();
        SharedPreferences.Editor editor  = preferences.edit();
        for (Map.Entry<String, Object> entry : preferencesMap.entrySet()) {
            String className = entry.getValue().getClass().getSimpleName();
            try {
                switch (className){
                    case "Boolean":
                        editor.putBoolean(entry.getKey(), json.getBoolean(entry.getKey()));
                        break;
                    case "Integer":
                        editor.putInt(entry.getKey(), json.getInt(entry.getKey()));
                        break;
                    case "Long":
                        editor.putLong(entry.getKey(), json.getLong(entry.getKey()));
                        break;
                    case "Float":
                        editor.putFloat(entry.getKey(), (float) json.getDouble(entry.getKey()));
                        break;
                    case "String":
                        editor.putString(entry.getKey(), json.getString(entry.getKey()));
                        break;
                    default:
                        throw new UnsupportedOperationException("Not implemented!");
                }
            } catch (JSONException e) {
                continue;
            } catch (UnsupportedOperationException e) {
                continue;
            }
        }
        editor.commit();
    }
    
    public boolean getImportOverwrite(){
        return preferences.getBoolean(resources.getString(R.string.pref_key_settings_import_overwrite), false);
    }
    
    public int getPostThumbnailSize(){
        double scale = 1.0;
        String defaultThumbnailScale = resources.getString(R.string.pref_post_thumbnail_scale_value_default);
        String thumbnailScale = preferences.getString(resources.getString(R.string.pref_key_post_thumbnail_scale), defaultThumbnailScale);
        if (thumbnailScale.equals(resources.getString(R.string.pref_post_thumbnail_scale_value_50percent)))  scale = 0.5;
        if (thumbnailScale.equals(resources.getString(R.string.pref_post_thumbnail_scale_value_100percent))) scale = 1.0;
        if (thumbnailScale.equals(resources.getString(R.string.pref_post_thumbnail_scale_value_150percent))) scale = 1.5;
        if (thumbnailScale.equals(resources.getString(R.string.pref_post_thumbnail_scale_value_200percent))) scale = 2.0;
        int result = (int) (resources.getDimensionPixelSize(R.dimen.post_thumbnail_size) * scale);
        return result;
    }    
}
