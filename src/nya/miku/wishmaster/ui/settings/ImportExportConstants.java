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

public class ImportExportConstants {
    public static final String JSON_KEY_VERSION = "version";
    public static final String JSON_KEY_HISTORY = "history";
    public static final String JSON_KEY_FAVORITES = "favorites";
    public static final String JSON_KEY_HIDDEN = "hidden";
    public static final String JSON_KEY_SUBSCRIPTIONS = "subscriptions";
    public static final String JSON_KEY_PREFERENCES = "preferences";
    public static final String JSON_KEY_TABS = "tabs";
    public static final String[] exclude = {
            "PREF_KEY_CLOUDFLARE_COOKIE",
            "PREF_KEY_CLOUDFLARE_COOKIE_DOMAIN",
            "PREF_KEY_USE_PROXY",
            "PREF_KEY_PROXY_HOST",
            "PREF_KEY_PROXY_PORT",
            "PREF_KEY_PASSWORD",
            "PREF_KEY_USE_HTTPS",
            "PREF_KEY_ONLY_NEW_POSTS",
            "PREF_KEY_CAPTCHA_AUTO_UPDATE",
            "PREF_KEY_CACHE_MAXSIZE",
            "PREF_KEY_SETTINGS_IMPORT_OVERWRITE",
    };
}
