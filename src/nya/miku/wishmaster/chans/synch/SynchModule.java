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

package nya.miku.wishmaster.chans.synch;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntityHC4;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.ListPreference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.chans.AbstractVichanModule;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.org_json.JSONObject;

@SuppressWarnings("deprecation") // https://issues.apache.org/jira/browse/HTTPCLIENT-1632

public class SynchModule extends AbstractVichanModule {
    
    private static final String CHAN_NAME = "syn-ch";
    private static final String[] DOMAINS = new String[] { "syn-ch.com", "syn-ch.org", "syn-ch.ru", "syn-ch.com.ua" };
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Бардак", "Основные", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "d", "Майдан", "Основные", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "r", "Random", "Основные", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Аниме", "Тематические", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "g", "Девушки", "Тематические", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mlp", "My Little Pony", "Тематические", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mu", "Музыка", "Тематические", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "r34", "Rule 34", "Тематические", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "s", "Программы", "Тематические", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "tv", "Кино и сериалы", "Тематические", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "vg", "Видеоигры", "Тематические", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "wh", "Warhammer", "Тематические", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "int", "Le int", "Остальные", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "old", "Чулан", "Остальные", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "test", "Старая школа", "Остальные", false),
    };
    private static final String[] ATTACHMENT_FORMATS = new String[] {
        "jpg", "png", "bmp", "svg", "swf", "mp3", "m4a", "flac", "zip", "rar", "tar", "gz", "txt", "pdf", "torrent", "webm"
    };
    private static final Pattern ERROR_PATTERN = Pattern.compile("<h2 [^>]*>(.*?)</h2>");
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
        ListPreference domainPref = new ListPreference(context);
        domainPref.setKey(getSharedKey(PREF_KEY_DOMAIN));
        domainPref.setTitle(R.string.synch_prefs_domain);
        domainPref.setSummary(R.string.synch_prefs_domain_summary);
        domainPref.setDialogTitle(R.string.synch_prefs_domain);
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
        model.readonlyBoard = false;
        model.requiredFileForNewThread = true;
        model.allowDeletePosts = true;
        model.allowDeleteFiles = true;
        model.allowNames = true;
        model.allowSubjects = true;
        model.allowSage = true;
        model.allowEmails = true;
        model.ignoreEmailIfSage = true;
        model.allowCustomMark = true;
        model.customMarkDescription = "Spoiler";
        model.allowRandomHash = true;
        model.allowIcons = false; //TODO?
        model.attachmentsMaxCount = 1;
        model.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        model.markType = BoardModel.MARK_BBCODE;
        return model;
    }
    
    @Override
    protected ThreadModel mapThreadModel(JSONObject opPost, String boardName) {
        ThreadModel model = super.mapThreadModel(opPost, boardName);
        if (model.attachmentsCount >= 0) model.attachmentsCount += opPost.optInt("omitted_images", 0);
        return model;
    }
    
    @Override
    protected AttachmentModel mapAttachment(JSONObject object, String boardName, boolean isSpoiler) {
        AttachmentModel model = super.mapAttachment(object, boardName, isSpoiler);
        if (model != null) {
            if (model.thumbnail != null) model.thumbnail = model.thumbnail.replace("//", "/").replaceAll("^/\\w+", "");
            if (model.path != null) model.path = model.path.replace("//", "/").replaceAll("^/\\w+", "");
        }
        return model;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        UrlPageModel urlModel = new UrlPageModel();
        urlModel.chanName = CHAN_NAME;
        urlModel.boardName = model.boardName;
        if (model.threadNumber == null) {
            urlModel.type = UrlPageModel.TYPE_BOARDPAGE;
            urlModel.boardPage = UrlPageModel.DEFAULT_FIRST_PAGE;
        } else {
            urlModel.type = UrlPageModel.TYPE_THREADPAGE;
            urlModel.threadNumber = model.threadNumber;
        }
        String referer = buildUrl(urlModel);
        List<Pair<String, String>> fields = VichanAntiBot.getFormValues(referer, task, httpClient);
        
        if (task != null && task.isCancelled()) throw new Exception("interrupted");
        
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().
                setCharset(Charset.forName("UTF-8")).setDelegates(listener, task);
        for (Pair<String, String> pair : fields) {
            String val;
            switch (pair.getKey()) {
                case "name": val = model.name; break;
                case "email": val = model.sage ? "sage" : model.email; break;
                case "subject": val = model.subject; break;
                case "body": val = model.comment; break;
                case "password": val = model.password; break;
                case "spoiler": val = model.custommark ? "on" : ""; break;
                default: val = pair.getValue();
            }
            if (pair.getKey().equals("file") && model.attachments != null && model.attachments.length > 0) {
                postEntityBuilder.addFile(pair.getKey(), model.attachments[0]);
            } else {
                postEntityBuilder.addString(pair.getKey(), val);
            }
        }
        
        String url = getUsingUrl() + "post.php";
        Header[] customHeaders = new Header[] { new BasicHeader(HttpHeaders.REFERER, referer) };
        HttpRequestModel request =
                HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setCustomHeaders(customHeaders).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, listener, task);
            if (response.statusCode == 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                Matcher errorMatcher = ERROR_PATTERN.matcher(htmlResponse);
                if (errorMatcher.find()) throw new Exception(errorMatcher.group(1));
            } else if (response.statusCode == 303) {
                for (Header header : response.headers) {
                    if (header != null && HttpHeaders.LOCATION.equalsIgnoreCase(header.getName())) {
                        return fixRelativeUrl(header.getValue());
                    }
                }
            }
            throw new Exception(response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
        }
    }
    
    @Override
    public String deletePost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "post.php";
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("board", model.boardName));
        pairs.add(new BasicNameValuePair("delete_" + model.postNumber, "on"));
        if (model.onlyFiles) pairs.add(new BasicNameValuePair("file", "on"));
        pairs.add(new BasicNameValuePair("password", model.password));
        pairs.add(new BasicNameValuePair("delete", "Удалить"));
        pairs.add(new BasicNameValuePair("reason", ""));
        
        UrlPageModel refererPage = new UrlPageModel();
        refererPage.type = UrlPageModel.TYPE_THREADPAGE;
        refererPage.chanName = CHAN_NAME;
        refererPage.boardName = model.boardName;
        refererPage.threadNumber = model.threadNumber;
        Header[] customHeaders = new Header[] { new BasicHeader(HttpHeaders.REFERER, buildUrl(refererPage)) };
        HttpRequestModel rqModel = HttpRequestModel.builder().
                setPOST(new UrlEncodedFormEntityHC4(pairs, "UTF-8")).setCustomHeaders(customHeaders).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, listener, task);
            if (response.statusCode == 200 || response.statusCode == 303) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                Matcher errorMatcher = ERROR_PATTERN.matcher(htmlResponse);
                if (errorMatcher.find()) throw new Exception(errorMatcher.group(1));
                return null;
            }
            throw new Exception(response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
        }
    }
    
    @Override
    public String fixRelativeUrl(String url) {
        if (url.startsWith("?/")) url = url.substring(1);
        if (url.startsWith("/src/") | url.startsWith("/thumb/")) return "http://cdn.syn-ch.com" + url;
        return super.fixRelativeUrl(url);
    }
}
