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

package nya.miku.wishmaster.chans.haruhichan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractVichanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class HaruhiModule extends AbstractVichanModule {
    private static final String CHAN_NAME = "haruhichan.ru";
    private static final String DOMAIN_NAME = "haruhichan.ru";
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "SOS団", " ", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Анимация", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mu", "Музач", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "d", "О борде", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "izm", "Харухизм", " ", false)
    };
    private static final Pattern ATTACHMENT_EMBEDDED_IFRAME = Pattern.compile("<iframe[^>]*src=\"([^\">]*)\"[^>]*>");
    
    public HaruhiModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Харухичан";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_haruhichan, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return DOMAIN_NAME;
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.allowEmails = false;
        return model;
    }
    
    @Override
    protected ThreadModel mapThreadModel(JSONObject opPost, String boardName) {
        ThreadModel model = super.mapThreadModel(opPost, boardName);
        if (model.attachmentsCount >= 0) model.attachmentsCount += opPost.optInt("omitted_images", 0);
        return model;
    }
    
    @Override
    protected PostModel mapPostModel(JSONObject object, String boardName) {
        PostModel model = super.mapPostModel(object, boardName);
        
        String embedString = object.optString("embed", "");
        if (!embedString.equals("")) {
            Matcher linkMatcher = ATTACHMENT_EMBEDDED_IFRAME.matcher(embedString);
            if (linkMatcher.find()) {
                AttachmentModel embed = new AttachmentModel();
                embed.type = AttachmentModel.TYPE_OTHER_NOTFILE;
                embed.path = linkMatcher.group(1);
                if (embed.path.startsWith("//")) embed.path = (useHttps() ? "https:" : "http:") + embed.path;
                if (embed.path.contains("youtube.com/embed/")) {
                    embed.path = embed.path.replace("youtube.com/embed/", "youtube.com/watch?v=");
                    embed.thumbnail = "http://img.youtube.com/vi/" + embed.path.substring(embed.path.indexOf('=') + 1) + "/default.jpg";
                }
                embed.size = -1;
                if (model.attachments != null) {
                    AttachmentModel[] attachments = new AttachmentModel[model.attachments.length + 1];
                    for (int i=0; i<model.attachments.length; ++i) attachments[i] = model.attachments[i];
                    attachments[model.attachments.length] = embed;
                    model.attachments = attachments;
                } else {
                    model.attachments = new AttachmentModel[] { embed };
                }
            }
        }
        
        return model;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String superResult = super.sendPost(model, listener, task);
        return model.sage ? null : superResult;
    }
    
    @Override
    protected String getSendPostEmail(SendPostModel model) {
        return model.sage ? "sage" : "noko";
    }
    
    @Override
    protected String getDeleteFormValue(DeletePostModel model) {
        return "Удалить";
    }
    
    @Override
    protected String getReportFormValue(DeletePostModel model) {
        return "Пожаловаться";
    }
}
