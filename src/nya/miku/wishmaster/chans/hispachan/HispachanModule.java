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

package nya.miku.wishmaster.chans.hispachan;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractKusabaModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;

@SuppressLint("SimpleDateFormat")
public class HispachanModule extends AbstractKusabaModule {
    private static final String CHAN_NAME = "hispachan";
    private static final String[] DOMAINS = { "www.hispachan.org", "hispachan.org" };
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "g", "General", "General", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Anime y Manga", "Intereses", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "c", "Ciencia y Matemáticas ", "Intereses", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "co", "Cómics y Animación", "Intereses", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "di", "Dibujo y Arte", "Intereses", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fun", "Funposting", "Intereses", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "hu", "Humanidades", "Intereses", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mcs", "Música, Cine y Series", "Intereses", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mlp", "My Little Pony", "Intereses", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "pol", "Política", "Intereses", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "t", "Tecnología y Programación", "Intereses", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "v", "Videojuegos", "Intereses", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "s", "Chicas Sexy", "Sexy", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "lgbt", "LGBT", "Sexy", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "h", "Hentai", "Sexy", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "arg", "Argentina", "Regional", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "cl", "Chile", "Regional", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "col", "Colombia", "Regional", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "esp", "España", "Regional", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mex", "México", "Regional", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "pe", "Perú", "Regional", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ve", "Venezuela", "Regional", true)
    };
    private static final String[] ATTACHMENT_FORMATS = new String[] { "gif", "jpg", "png", "pdf", "swf", "webm" };
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yy HH:mm");
    
    public HispachanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Hispachan";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_hispachan, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return DOMAINS[0];
    }
    
    @Override
    protected String[] getAllDomains() {
        return DOMAINS;
    }
    
    @Override
    protected boolean canCloudflare() {
        return true;
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    protected DateFormat getDateFormat() {
        return DATE_FORMAT;
    }
    
    @Override
    protected int getKusabaFlags() {
        return ~(KusabaReader.FLAG_HANDLE_EMBEDDED_POST_POSTPROCESS|KusabaReader.FLAG_OMITTED_STRING_REMOVE_HREF);
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.defaultUserName = shortName.equals("fun") ? "Hanonymouz" : "Anónimo";
        model.allowNames = false;
        model.allowEmails = false;
        model.allowCustomMark = true;
        model.customMarkDescription = "Spoiler";
        model.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        model.markType = BoardModel.MARK_BBCODE;
        return model;
    }
    
    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        PostModel[] result = super.getPostsList(boardName, threadNumber, listener, task, oldList);
        if (result != null && result.length > 0) result[0].number = threadNumber;
        return result;
    }
    
}
