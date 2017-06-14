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

package nya.miku.wishmaster.chans.nullchan;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringEscapeUtils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.EditTextPreference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import android.text.InputType;
import android.text.TextUtils;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class NullchanoneModule extends AbstractInstant0chan {
    private static final String CHAN_NAME = "0chan.one";
    private static final String CHAN_DOMAIN = "0chan.one";
    
    private static final String PREF_KEY_DOMAIN = "PREF_KEY_DOMAIN";
    
    public NullchanoneModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Овернульч";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_0chan, null);
    }
    
    @Override
    protected String getUsingDomain() {
        String domain = preferences.getString(getSharedKey(PREF_KEY_DOMAIN), CHAN_DOMAIN);
        return TextUtils.isEmpty(domain) ? CHAN_DOMAIN : domain;
    }
    
    @Override
    protected String[] getAllDomains() {
        if (!getChanName().equals(CHAN_NAME) || CHAN_DOMAIN.equals(getUsingDomain())) {
            return super.getAllDomains();
        }
        return new String[] { CHAN_DOMAIN, getUsingDomain() };
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    protected boolean useHttpsDefaultValue() {
        return false;
    }
    
    @Override
    protected boolean canCloudflare() {
        return true;
    }
    
    private void addDomainPreference(PreferenceGroup group) {
        Context context = group.getContext();
        EditTextPreference domainPref = new EditTextPreference(context);
        domainPref.setTitle(R.string.pref_domain);
        domainPref.setDialogTitle(R.string.pref_domain);
        domainPref.setKey(getSharedKey(PREF_KEY_DOMAIN));
        domainPref.getEditText().setHint(CHAN_DOMAIN);
        domainPref.getEditText().setSingleLine();
        domainPref.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        group.addPreference(domainPref);
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        addDomainPreference(preferenceGroup);
        super.addPreferencesOnScreen(preferenceGroup);
    }
    
    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception {
        String url = getUsingUrl() + "boards10.json";
        try {
            JSONArray json = downloadJSONArray(url, oldBoardsList != null, listener, task);
            if (json == null) return oldBoardsList;
            List<SimpleBoardModel> list = new ArrayList<>();
            for (int i=0; i<json.length(); ++i) {
                String currentCategory = json.getJSONObject(i).optString("name");
                JSONArray boards = json.getJSONObject(i).getJSONArray("boards");
                for (int j=0; j<boards.length(); ++j) {
                    SimpleBoardModel model = new SimpleBoardModel();
                    model.chan = getChanName();
                    model.boardName = boards.getJSONObject(j).getString("dir");
                    model.boardDescription = boards.getJSONObject(j).optString("desc", model.boardName);
                    model.boardCategory = currentCategory;
                    list.add(model);
                }
            }
            return list.toArray(new SimpleBoardModel[list.size()]);
        } catch (Exception e) {
            return new SimpleBoardModel[0];
        }
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.catalogAllowed = true;
        return model;
    }
    
    @Override
    public ThreadModel[] getCatalog(String boardName, int catalogType, ProgressListener listener, CancellableTask task, ThreadModel[] oldList) throws Exception {
        String url = getUsingUrl() + boardName + "/catalog.json";
        JSONArray response = downloadJSONArray(url, oldList != null, listener, task);
        if (response == null) return oldList;
        ThreadModel[] threads = new ThreadModel[response.length()];
        for (int i = 0; i < threads.length; ++i) {
            threads[i] = mapCatalogThreadModel(response.getJSONObject(i), boardName);
        }
        return threads;
    }
    
    private ThreadModel mapCatalogThreadModel(JSONObject json, String boardName) {
        ThreadModel model = new ThreadModel();
        model.threadNumber = json.optString("id", null);
        if (model.threadNumber == null) throw new RuntimeException();
        model.postsCount = json.optInt("reply_count", -2) + 1;
        model.attachmentsCount = json.optInt("images", -2) + 1;
        model.isClosed = json.optInt("locked", 0) != 0;
        model.isSticky = json.optInt("stickied", 0) != 0;
        
        PostModel opPost = new PostModel();
        opPost.number = model.threadNumber;
        opPost.name = StringEscapeUtils.unescapeHtml4(RegexUtils.removeHtmlSpanTags(json.optString("name")));
        opPost.subject = StringEscapeUtils.unescapeHtml4(json.optString("subject"));
        opPost.comment = json.optString("message");
        opPost.trip = json.optString("tripcode");
        opPost.timestamp = json.optLong("timestamp") * 1000;
        opPost.parentThread = model.threadNumber;
        
        String ext = json.optString("file_type", "");
        if (!ext.isEmpty()) {
            AttachmentModel attachment = new AttachmentModel();
            switch (ext) {
                case "jpg":
                case "jpeg":
                case "png":
                    attachment.type = AttachmentModel.TYPE_IMAGE_STATIC;
                    break;
                case "gif":
                    attachment.type = AttachmentModel.TYPE_IMAGE_GIF;
                    break;
                case "mp3":
                case "ogg":
                    attachment.type = AttachmentModel.TYPE_AUDIO;
                    break;
                case "webm":
                case "mp4":
                    attachment.type = AttachmentModel.TYPE_VIDEO;
                    break;
                case "you":
                    attachment.type = AttachmentModel.TYPE_OTHER_NOTFILE;
                    break;
                default:
                    attachment.type = AttachmentModel.TYPE_OTHER_FILE;
            }
            attachment.width = json.optInt("image_w", -1);
            attachment.height = json.optInt("image_h", -1);
            attachment.size = -1;
            String fileName = json.optString("file", "");
            if (!fileName.isEmpty()) {
                if (ext.equals("you")) {
                    attachment.thumbnail = (useHttps() ? "https" : "http")
                            + "://img.youtube.com/vi/" + fileName + "/default.jpg";
                    attachment.path = (useHttps() ? "https" : "http")
                            + "://youtube.com/watch?v=" + fileName;
                } else {
                    attachment.thumbnail = "/" + boardName + "/thumb/" + fileName + "s." + ext;
                    attachment.path = "/" + boardName + "/src/" + fileName + "." + ext;
                }
                opPost.attachments = new AttachmentModel[] { attachment };
            }
        }
        model.posts = new PostModel[] { opPost };
        return model;
        
    }
    
}
