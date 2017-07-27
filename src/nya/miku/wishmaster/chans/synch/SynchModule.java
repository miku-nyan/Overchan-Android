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

package nya.miku.wishmaster.chans.synch;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.ListPreference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractVichanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.LazyPreferences;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class SynchModule extends AbstractVichanModule {
    
    private static final String CHAN_NAME = "syn-ch";
    private static final String DOMAINS_HINT = "syn-ch.com, syn-ch.org, syn-ch.ru, syn-ch.com.ua";
    private static final String[] DOMAINS = new String[] { "syn-ch.com", "syn-ch.org", "syn-ch.ru", "syn-ch.com.ua" };
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Бардак", "Основные", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "d", "Майдан", "Основные", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Аниме", "Тематические", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "anon", "Anonymous", "Тематические", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mlp", "My Little Pony", "Тематические", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mu", "Музыка", "Тематические", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "po", "Politics", "Тематические", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "r34", "Rule 34", "Тематические", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "tv", "Кино и сериалы", "Тематические", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "vg", "Видеоигры", "Тематические", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "diy", "Хобби", "Тематические", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "old", "Чулан", "Остальные", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "test", "Старая школа", "Остальные", false),
    };
    private static final String[] ATTACHMENT_FORMATS = new String[] {
        "jpg", "png", "bmp", "svg", "swf", "mp3", "m4a", "flac", "zip", "rar", "tar", "gz", "txt", "pdf", "torrent", "webm"
    };
    private static final String PREF_KEY_DOMAIN = "PREF_KEY_DOMAIN";
    
    public SynchModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Син.ч";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_synch, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return preferences.getString(getSharedKey(PREF_KEY_DOMAIN), DOMAINS[0]);
    }
    
    @Override
    protected String[] getAllDomains() {
        return DOMAINS;
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
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        Context context = preferenceGroup.getContext();
        ListPreference domainPref = new LazyPreferences.ListPreference(context);
        domainPref.setKey(getSharedKey(PREF_KEY_DOMAIN));
        domainPref.setTitle(R.string.pref_domain);
        domainPref.setSummary(resources.getString(R.string.pref_domain_summary, DOMAINS_HINT));
        domainPref.setDialogTitle(R.string.pref_domain);
        domainPref.setEntries(DOMAINS);
        domainPref.setEntryValues(DOMAINS);
        domainPref.setDefaultValue(DOMAINS[0]);
        preferenceGroup.addPreference(domainPref);
        super.addPreferencesOnScreen(preferenceGroup);
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.timeZoneId = "GMT+3";
        model.defaultUserName = "Аноним";
        model.allowCustomMark = true;
        model.customMarkDescription = "Spoiler";
        model.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        return model;
    }
    
    @Override
    protected AttachmentModel mapAttachment(JSONObject object, String boardName, boolean isSpoiler) {
        AttachmentModel model = super.mapAttachment(object, boardName, isSpoiler);
        if (model != null) {
            if (model.type == AttachmentModel.TYPE_VIDEO) model.thumbnail = null;
            if (model.thumbnail != null) model.thumbnail = model.thumbnail.replace("//", "/").replaceAll("^/\\w+", "");
            if (model.path != null) model.path = model.path.replace("//", "/").replaceAll("^/\\w+", "");
        }
        return model;
    }
    
    @Override
    protected String getDeleteFormValue(DeletePostModel model) {
        return "Удалить";
    }
    
    @Override
    protected String getReportFormValue(DeletePostModel model) {
        return "Пожаловаться";
    }
    
    @Override
    public String fixRelativeUrl(String url) {
        if (url.startsWith("/src/") | url.startsWith("/thumb/")) return "http://cdn.syn-ch.com" + url;
        return super.fixRelativeUrl(url);
    }
}
