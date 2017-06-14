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

package nya.miku.wishmaster.chans.lainchan;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractVichanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.lib.org_json.JSONObject;


public class LainModule extends AbstractVichanModule {
    private static final String CHAN_NAME = "lainchan.org";
    private static final String CHAN_DOMAIN = "lainchan.org";
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "λ", "Programming", " ", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "Δ", "Do It Yourself", " ", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "sec", "Security", " ", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "Ω", "Technology", " ", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "inter", "Games and Interactive Media", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "lit", "Literature", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "music", "Musical and Audible Media", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "vis", "Visual Media", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "hum", "Humanity", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "drg", "Drugs 3.0", "", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "zzz", "Consciousness and Dreams", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "layer", "layer", " ", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "q", "Questions and Complaints", " ", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "r", "Random", " ", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "lambda", "Programming", "Duplicated", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "diy", "Do It Yourself", "Duplicated", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "tech", "Technology", "Duplicated", false),
    };
    
    public LainModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "lainchan";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_lain, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return CHAN_DOMAIN;
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.attachmentsMaxCount = 3;
        model.allowCustomMark = true;
        model.customMarkDescription = "Spoiler";
        return model;
    }
    
    @Override
    protected AttachmentModel mapAttachment(JSONObject object, String boardName, boolean isSpoiler) {
        AttachmentModel attachment = super.mapAttachment(object, boardName, isSpoiler);
        if (attachment != null && attachment.type == AttachmentModel.TYPE_VIDEO) {
            attachment.thumbnail = null;
        }
        return attachment;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = super.sendPost(model, listener, task);
        try {
            url = encodeLainUrl(new String(url.getBytes("ISO-8859-1"), "UTF-8"));
        } catch (Exception e) {}
        return url;
    }
    
    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        return encodeLainUrl(super.buildUrl(model));
    }
    
    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        UrlPageModel model = super.parseUrl(url);
        model.boardName = Uri.decode(model.boardName);
        return model;
    }
    
    private String encodeLainUrl(String url) {
        //TODO: convert url path to percent-encoding
        return url.replace("λ", "%CE%BB").replace("Δ","%CE%94").replace("Ω", "%CE%A9");
    }
}
