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

package nya.miku.wishmaster.ui;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.ArrayAdapter;
import nya.miku.wishmaster.ui.tabs.UrlHandler;

public class ReverseImageSearch {
    public static final List<ReverseImageService> SERVICES = Arrays.asList(
            new ReverseImageService("Google", "http://www.google.com/searchbyimage?image_url=%s"),
            new ReverseImageService("Yandex", "http://www.yandex.ru/images/search?img_url=%s&rpt=imageview"),
            new ReverseImageService("TinEye", "http://www.tineye.com/search?url=%s"),
            new ReverseImageService("ImgOps", "http://imgops.com/%s")
    );
    
    public static void openDialog(final Context activity, final String url) {
        new AlertDialog.Builder(activity).setAdapter(
                new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, SERVICES),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        UrlHandler.launchExternalBrowser(activity, SERVICES.get(which).format(url));
                    }
                }).show();
    }
    
    public static class ReverseImageService {
        private final String name;
        private final String urlFormat;
        private ReverseImageService(String name, String urlFormat) {
            this.name = name;
            this.urlFormat = urlFormat;
        }
        
        @Override
        public String toString() {
            return name;
        }
        
        public String format(String url) {
            return String.format(Locale.US, urlFormat, url);
        }
    }
}
