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

package nya.miku.wishmaster.ui.theme;

import java.util.Locale;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.util.SparseIntArray;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class GenericThemeEntry {
    private static final int BASE_THEME_LIGHT = R.style.Theme_Futaba;
    private static final int BASE_THEME_DARK = R.style.Theme_Neutron;
    
    private static GenericThemeEntry cachedInstance = null;
    private static String cachedJsonString = null;
    
    public final int themeId;
    public final int fontSizeStyleId;
    public final SparseIntArray customAttrs;
    
    private GenericThemeEntry(int themeId, int fontSizeStyleId, SparseIntArray customAttrs) {
        this.themeId = themeId;
        this.fontSizeStyleId = fontSizeStyleId;
        this.customAttrs = customAttrs;
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o instanceof GenericThemeEntry) {
            GenericThemeEntry other = (GenericThemeEntry) o;
            return other.themeId == this.themeId &&
                    other.fontSizeStyleId == this.fontSizeStyleId &&
                    sparseIntArrayEquals(other.customAttrs, this.customAttrs); 
        } else {
            return false;
        }
    }
    
    public static GenericThemeEntry standartTheme(int themeId, int fontSizeStyleId) {
        return new GenericThemeEntry(themeId, fontSizeStyleId, null);
    }
    
    public static GenericThemeEntry customTheme(String jsonData, int fontSizeStyleId) {
        GenericThemeEntry cached = cachedInstance;
        if (cached != null && cached.fontSizeStyleId == fontSizeStyleId && jsonData.equals(cachedJsonString)) return cached;
        
        SparseIntArray customAttrs = new SparseIntArray();
        int themeId = parseTheme(jsonData, customAttrs);
        GenericThemeEntry theme = new GenericThemeEntry(themeId, fontSizeStyleId, customAttrs);
        cachedInstance = theme;
        cachedJsonString = jsonData;
        return theme;
    }
    
    public void setTo(Context context, int... applyStyles) {
        setBaseStyle(context, themeId, fontSizeStyleId);
        if (customAttrs != null) {
            Resources.Theme theme = context.getTheme();
            if (themeId == BASE_THEME_LIGHT) theme.applyStyle(R.style.Custom_Theme_Light, true);
            else if (themeId == BASE_THEME_DARK) theme.applyStyle(R.style.Custom_Theme_Dark, true);
        }
        if (applyStyles != null) {
            Resources.Theme theme = context.getTheme();
            for (int i : applyStyles) {
                theme.applyStyle(i, true);
            }
        }
        CustomThemeHelper.setCustomTheme(context, customAttrs);
    }
    
    public void setToPreferencesActivity(Context context) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB_MR2 || Build.VERSION.SDK_INT < Build.VERSION_CODES.ECLAIR_0_1) {
            setTo(context);
        } else {
            setBaseStyle(context, BASE_THEME_DARK, fontSizeStyleId);
        }
    }
    
    private static void setBaseStyle(Context context, int themeId, int fontSizeStyleId) {
        context.setTheme(themeId);
        context.getTheme().applyStyle(fontSizeStyleId, true);
    }
    
    public String toJsonString() {
        if (customAttrs == null) throw new IllegalStateException("this is not a custom theme");
        JSONObject theme = new JSONObject();
        theme.put("baseTheme", themeId == BASE_THEME_DARK ? "dark" : "light");
        for (int i=0, size=customAttrs.size(); i<size; ++i) {
            switch (customAttrs.keyAt(i)) {
                case android.R.attr.textColorPrimary: theme.put("textColorPrimary", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.materialPrimary: theme.put("materialPrimary", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.materialPrimaryDark: theme.put("materialPrimaryDark", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.materialNavigationBar: theme.put("materialNavigationBar", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.activityRootBackground: theme.put("activityRootBackground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.sidebarBackground: theme.put("sidebarBackground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.sidebarSelectedItem: theme.put("sidebarSelectedItem", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.listSeparatorBackground: theme.put("listSeparatorBackground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.postUnreadOverlay: theme.put("postUnreadOverlay", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.postBackground: theme.put("postBackground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.postForeground: theme.put("postForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.postIndexForeground: theme.put("postIndexForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.postIndexOverBumpLimit: theme.put("postIndexOverBumpLimit", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.postNumberForeground: theme.put("postNumberForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.postNameForeground: theme.put("postNameForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.postOpForeground: theme.put("postOpForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.postSageForeground: theme.put("postSageForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.postTripForeground: theme.put("postTripForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.postTitleForeground: theme.put("postTitleForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.postQuoteForeground: theme.put("postQuoteForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.spoilerForeground: theme.put("spoilerForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.spoilerBackground: theme.put("spoilerBackground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.urlLinkForeground: theme.put("urlLinkForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.refererForeground: theme.put("refererForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.itemInfoForeground: theme.put("itemInfoForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.searchHighlightBackground: theme.put("searchHighlightBackground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.subscriptionBackground: theme.put("subscriptionBackground", colorToString(customAttrs.valueAt(i))); break;
                default: Logger.e("TAG", "unknown attribute: " + customAttrs.keyAt(i));
            }
        }
        return theme.toString();
    }
    
    private static int parseTheme(String customThemeJson, SparseIntArray attrs) {
        JSONObject theme = new JSONObject(customThemeJson);
        String baseTheme = null;
        for (String key : theme.keySet()) {
            switch (key.toLowerCase(Locale.US)) {
                case "basetheme":
                    if (baseTheme != null) throwDefinedMoreThenOnce("baseTheme");
                    String value = theme.getString(key);
                    switch (value.toLowerCase(Locale.US)) {
                        case "light": baseTheme = "light"; break;
                        case "dark": baseTheme = "dark"; break;
                        default: throw new IllegalArgumentException("Illegal value for baseTheme: " + value);
                    }
                    break;
                case "textcolorprimary": parseColor(key, theme.getString(key), android.R.attr.textColorPrimary, attrs); break;
                case "materialprimary": parseColor(key, theme.getString(key), R.attr.materialPrimary, attrs); break;
                case "materialprimarydark": parseColor(key, theme.getString(key), R.attr.materialPrimaryDark, attrs); break;
                case "materialnavigationbar": parseColor(key, theme.getString(key), R.attr.materialNavigationBar, attrs); break;
                case "activityrootbackground": parseColor(key, theme.getString(key), R.attr.activityRootBackground, attrs); break;
                case "sidebarbackground": parseColor(key, theme.getString(key), R.attr.sidebarBackground, attrs); break;
                case "sidebarselecteditem": parseColor(key, theme.getString(key), R.attr.sidebarSelectedItem, attrs); break;
                case "listseparatorbackground": parseColor(key, theme.getString(key), R.attr.listSeparatorBackground, attrs); break;
                case "postunreadoverlay": parseColor(key, theme.getString(key), R.attr.postUnreadOverlay, attrs); break;
                case "postbackground": parseColor(key, theme.getString(key), R.attr.postBackground, attrs); break;
                case "postforeground": parseColor(key, theme.getString(key), R.attr.postForeground, attrs); break;
                case "postindexforeground": parseColor(key, theme.getString(key), R.attr.postIndexForeground, attrs); break;
                case "postindexoverbumplimit": parseColor(key, theme.getString(key), R.attr.postIndexOverBumpLimit, attrs); break;
                case "postnumberforeground": parseColor(key, theme.getString(key), R.attr.postNumberForeground, attrs); break;
                case "postnameforeground": parseColor(key, theme.getString(key), R.attr.postNameForeground, attrs); break;
                case "postopforeground": parseColor(key, theme.getString(key), R.attr.postOpForeground, attrs); break;
                case "postsageforeground": parseColor(key, theme.getString(key), R.attr.postSageForeground, attrs); break;
                case "posttripforeground": parseColor(key, theme.getString(key), R.attr.postTripForeground, attrs); break;
                case "posttitleforeground": parseColor(key, theme.getString(key), R.attr.postTitleForeground, attrs); break;
                case "postquoteforeground": parseColor(key, theme.getString(key), R.attr.postQuoteForeground, attrs); break;
                case "spoilerforeground": parseColor(key, theme.getString(key), R.attr.spoilerForeground, attrs); break;
                case "spoilerbackground": parseColor(key, theme.getString(key), R.attr.spoilerBackground, attrs); break;
                case "urllinkforeground": parseColor(key, theme.getString(key), R.attr.urlLinkForeground, attrs); break;
                case "refererforeground": parseColor(key, theme.getString(key), R.attr.refererForeground, attrs); break;
                case "iteminfoforeground": parseColor(key, theme.getString(key), R.attr.itemInfoForeground, attrs); break;
                case "searchhighlightbackground": parseColor(key, theme.getString(key), R.attr.searchHighlightBackground, attrs); break;
                case "subscriptionBackground": parseColor(key, theme.getString(key), R.attr.subscriptionBackground, attrs); break;
                default: throw new IllegalArgumentException("Unknown key: " + key);
            }
        }
        if (baseTheme == null) throw new IllegalArgumentException("Base theme not set");
        return baseTheme.equals("light") ? BASE_THEME_LIGHT : BASE_THEME_DARK;
    }
    
    private static void parseColor(String key, String value, int attrId, SparseIntArray array) {
        if (array.indexOfKey(attrId) >= 0) throwDefinedMoreThenOnce(key);
        try {
            array.put(attrId, Color.parseColor(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown color: "+value);
        }
    }
    
    private static String colorToString(int color) {
        if (Color.alpha(color) == 0xFF) return String.format("#%06X", color & 0xFFFFFF);
        return String.format("#%08X", color);
    }
    
    private static void throwDefinedMoreThenOnce(String key) {
        throw new IllegalArgumentException(String.format("Key %s is defined more than once", key));
    }
    
    private static boolean sparseIntArrayEquals(SparseIntArray a, SparseIntArray b) {
        if (a == b) return true;
        if (a == null) return b == null;
        if (b == null) return false;
        int size = a.size();
        if (size != b.size()) return false;
        for (int i=0; i<size; ++i) {
            if (a.keyAt(i) != b.keyAt(i)) return false;
            if (a.valueAt(i) != b.valueAt(i)) return false; 
        }
        return true;
    }
}
