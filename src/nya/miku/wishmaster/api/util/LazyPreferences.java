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

package nya.miku.wishmaster.api.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Настройки, которые не сохраняют значения по умолчанию в SharedPreferences
 * @author miku-nyan
 *
 */
public class LazyPreferences {
    public static class CheckBoxPreference extends android.preference.CheckBoxPreference {
        private Object defaultValue;
        
        public CheckBoxPreference(Context context) {
            super(context);
        }
        
        @Override
        public void setDefaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            super.setDefaultValue(defaultValue);
        }
        
        @Override
        protected boolean persistBoolean(boolean value) {
            if (shouldPersist() && defaultValue != null && defaultValue.equals(value)) {
                SharedPreferences prefs = getSharedPreferences();
                String key = getKey();
                if (prefs.contains(key)) prefs.edit().remove(key).commit();
                return true;
            }
            return super.persistBoolean(value);
        }
    }
    
    public static class EditTextPreference extends android.preference.EditTextPreference {
        private Object defaultValue;
        
        public EditTextPreference(Context context) {
            super(context);
        }
        
        @Override
        public void setDefaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            super.setDefaultValue(defaultValue);
        }
        
        @Override
        protected boolean persistString(String value) {
            if (shouldPersist() && defaultValue != null && defaultValue.equals(value)) {
                SharedPreferences prefs = getSharedPreferences();
                String key = getKey();
                if (prefs.contains(key)) prefs.edit().remove(key).commit();
                return true;
            }
            return super.persistString(value);
        }
    }
    
    public static class ListPreference extends android.preference.ListPreference {
        private Object defaultValue;
        
        public ListPreference(Context context) {
            super(context);
        }
        
        @Override
        public void setDefaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            super.setDefaultValue(defaultValue);
        }
        
        @Override
        protected boolean persistString(String value) {
            if (shouldPersist() && defaultValue != null && defaultValue.equals(value)) {
                SharedPreferences prefs = getSharedPreferences();
                String key = getKey();
                if (prefs.contains(key)) prefs.edit().remove(key).commit();
                return true;
            }
            return super.persistString(value);
        }
    }
    
}
