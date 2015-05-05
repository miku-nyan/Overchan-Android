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

package nya.miku.wishmaster.chans;

import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.WakabaReader;
import nya.miku.wishmaster.common.Logger;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v4.content.res.ResourcesCompat;

public class WakabaFactory {
    private static final String TAG = "WakabaFactory";
    
    public static ChanModule createChanModule(SharedPreferences preferences, Resources resources, String url) {
        return createChanModule(preferences, resources, url, (DateFormat)null);
    }
    
    public static ChanModule createChanModule(SharedPreferences preferences, Resources resources, String url, String pattern) {
        return createChanModule(preferences, resources, url, new SimpleDateFormat(pattern, Locale.US));
    }
    
    private static ChanModule createChanModule(SharedPreferences preferences, Resources resources, String url, final DateFormat df) {
        Uri uri = Uri.parse(url);
        final String name = uri.getHost();
        final boolean https = uri.getScheme() != null && uri.getScheme().equalsIgnoreCase("https");
        return new AbstractWakabaModule(preferences, resources) {
            private final List<DateFormat> dateFormats;
            {
                dateFormats = Arrays.asList(new DateFormat[] {
                        df,
                        new SimpleDateFormat("yy/MM/dd(EEE)HH:mm", Locale.US),
                        new SimpleDateFormat("EEE dd MMM yyyy HH:mm:ss", Locale.US),
                        new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US) {
                            private static final long serialVersionUID = 1L;
                            public Date parse(String string) throws ParseException {
                                return super.parse(string.replaceAll(" ?\\(.*?\\)", ""));
                            }
                        },
                        new SimpleDateFormat("EEE yy/MM/dd HH:mm", Locale.US),
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                });
            }
            
            @Override
            public String getDisplayingName() {
                return name;
            }
            
            @Override
            public String getChanName() {
                return name;
            }
            
            @Override
            public Drawable getChanFavicon() {
                return ResourcesCompat.getDrawable(resources, R.drawable.favicon_cirno, null);
            }
            
            @Override
            protected String getUsingDomain() {
                return name;
            }
            
            @Override
            protected SimpleBoardModel[] getBoardsList() {
                return new SimpleBoardModel[0];
            }
            
            @Override
            protected boolean canHttps() {
                return true;
            }
            
            protected boolean useHttpsDefaultValue() {
                return https;
            };
            
            @Override
            protected boolean canCloudflare() {
                return true;
            }
            
            @SuppressWarnings("serial")
            @SuppressLint("SimpleDateFormat")
            @Override
            protected WakabaReader getWakabaReader(InputStream stream, UrlPageModel urlModel) {
                return new WakabaReader(stream, new SimpleDateFormat() { // universal date format
                    @Override
                    public Date parse(String string) throws ParseException {
                        for (DateFormat df : dateFormats) {
                            try {
                                return df.parse(string);
                            } catch (Exception e) {}
                        }
                        Logger.d(TAG, "couldn't parse: '"+ string + "'");
                        return new Date(0);
                    }
                });
            }
        };
    }
}
