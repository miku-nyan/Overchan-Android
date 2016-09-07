package nya.miku.wishmaster.chans.arhivach;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;

import java.io.Closeable;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.LazyPreferences;
import nya.miku.wishmaster.api.util.UrlPathUtils;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;

/**
 * Created by Kalaver <Kalaver@users.noreply.github.com> on 23.06.2015.
 */

public class ArhivachModule extends AbstractChanModule {
    //private static final String TAG = "ArhivachModule";
    
    static final String CHAN_NAME = "Arhivach.org";
    private static final String DEFAULT_DOMAIN = "arhivach.org";
    private static final String ONION_DOMAIN = "arhivachovtj2jrp.onion";
    private static final String[] DOMAINS = new String[] { DEFAULT_DOMAIN, ONION_DOMAIN };

    private static final String PREF_KEY_USE_ONION = "PREF_KEY_USE_ONION";
    
    public ArhivachModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Архивач";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_arhivach, null);
    }
    
    private String getUsingDomain() {
        return preferences.getBoolean(getSharedKey(PREF_KEY_USE_ONION), false) ? ONION_DOMAIN : DEFAULT_DOMAIN;
    }
    
    private String getUsingUrl() {
        return (useHttps() ? "https://" : "http://") + getUsingDomain() + "/";
    }
    
    private boolean useHttps() {
        return !preferences.getBoolean(getSharedKey(PREF_KEY_USE_ONION), false) && useHttps(false);
    }
    
    @Override   
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        Context context = preferenceGroup.getContext();
        CheckBoxPreference httpsPref = addHttpsPreference(preferenceGroup, false);
        CheckBoxPreference onionPref = new LazyPreferences.CheckBoxPreference(context);
        onionPref.setTitle(R.string.pref_use_onion);
        onionPref.setSummary(R.string.pref_use_onion_summary);
        onionPref.setKey(getSharedKey(PREF_KEY_USE_ONION));
        onionPref.setDefaultValue(false);
        onionPref.setDisableDependentsState(true);
        preferenceGroup.addPreference(onionPref);
        httpsPref.setDependency(getSharedKey(PREF_KEY_USE_ONION));
        addProxyPreferences(preferenceGroup);
    }
    
    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception {
        return ArhivachBoards.getBoardsList();
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        return ArhivachBoards.getBoard(shortName);
    }
    
    private ThreadModel[] readBoardPage(String url, ProgressListener listener, CancellableTask task, boolean checkIfModified, boolean isThread)
            throws Exception {
        HttpResponseModel responseModel = null;
        Closeable in = null;
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModified).build();
        try {
            responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, listener, task);
            if (responseModel.statusCode == 200) {
                in = isThread ? new ArhivachThreadReader(responseModel.stream) : new ArhivachBoardReader(responseModel.stream);
                if (task != null && task.isCancelled()) throw new Exception("interrupted");
                return isThread ? ((ArhivachThreadReader) in).readPage() : ((ArhivachBoardReader) in).readPage() ;
            } else {
                if (responseModel.notModified()) return null;
                throw new HttpWrongStatusCodeException(responseModel.statusCode, responseModel.statusCode + " - " + responseModel.statusReason);
            }
        } catch (Exception e) {
            if (responseModel != null) HttpStreamer.getInstance().removeFromModifiedMap(url);
            throw e;
        } finally {
            IOUtils.closeQuietly(in);
            if (responseModel != null) responseModel.release();
        }
    }
    
    @Override
    public ThreadModel[] getThreadsList(String boardName, int page, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        UrlPageModel urlModel = new UrlPageModel();
        urlModel.chanName = CHAN_NAME;
        urlModel.type = UrlPageModel.TYPE_BOARDPAGE;
        urlModel.boardName = boardName;
        urlModel.boardPage = page;
        String url = buildUrl(urlModel);

        ThreadModel[] threads = readBoardPage(url, listener, task, oldList != null, false);
        if (threads == null) {
            return oldList;
        } else {
            return threads;
        }
    }
    
    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        UrlPageModel urlModel = new UrlPageModel();
        urlModel.chanName = CHAN_NAME;
        urlModel.type = UrlPageModel.TYPE_THREADPAGE;
        urlModel.boardName = boardName;
        urlModel.threadNumber = threadNumber;
        String url = buildUrl(urlModel);

        ThreadModel[] threads = readBoardPage(url, listener, task, oldList != null, true);
        if (threads == null) {
            return oldList;
        } else {
            if (threads.length == 0) throw new Exception("Unable to parse response");
            return oldList == null ? threads[0].posts : ChanModels.mergePostsLists(Arrays.asList(oldList), Arrays.asList(threads[0].posts));
        }
    }
    
    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        if (!model.chanName.equals(CHAN_NAME)) throw new IllegalArgumentException("wrong chan");
        StringBuilder url = new StringBuilder(getUsingUrl());
        switch (model.type) {
            case UrlPageModel.TYPE_INDEXPAGE:
                url.append("index").append("/");
                break;
            case UrlPageModel.TYPE_BOARDPAGE:
                if (!model.boardName.equals("")) throw new IllegalArgumentException("wrong board name");
                url.append("index").append("/");
                if (model.boardPage > 1) url.append((model.boardPage - 1) * 25).append("/");
                break;
            case UrlPageModel.TYPE_THREADPAGE:
                if (!model.boardName.equals("")) throw new IllegalArgumentException("wrong board name");
                url.append("thread/").append(model.threadNumber).append("/");
                if (model.postNumber != null && model.postNumber.length() != 0) url.append("#").append(model.postNumber);
                break;
            default:
                throw new IllegalArgumentException("wrong page type");
        }
        return url.toString();
    }
    
    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        String urlPath  = UrlPathUtils.getUrlPath(url, DOMAINS);
        if (urlPath == null) throw new IllegalArgumentException("wrong domain");
        UrlPageModel model = new UrlPageModel();
        model.chanName = getChanName();
        try {
            if (urlPath.contains("thread/")) {
                model.type = UrlPageModel.TYPE_THREADPAGE;
                Matcher matcher = Pattern.compile("thread/(\\d+)/?(.*)").matcher(urlPath);
                if (!matcher.find()) throw new Exception();
                model.boardName = "";
                model.threadNumber = matcher.group(1);
                if (matcher.group(2).startsWith("#")) {
                    String post = matcher.group(2).substring(1);
                    if (!post.equals(""))
                        model.postNumber = post;
                }
            } else if (urlPath.contains("index") || urlPath.indexOf("/")==0 || urlPath.length()==0) {
                model.type = UrlPageModel.TYPE_BOARDPAGE;
                model.boardName = "";
                Matcher matcher = Pattern.compile("index/(\\d+)/?(.*)").matcher(urlPath);
                String page = "";
                if (matcher.find())
                    page = matcher.group(1);
                if (!page.equals(""))
                    model.boardPage = (Integer.parseInt(page) / 25) + 1;
                else
                    model.boardPage = 1;
            }
        } catch (Exception e) {
            model.type = UrlPageModel.TYPE_OTHERPAGE;
            model.otherPath = urlPath;
        }
        
        return model;
    }
}
