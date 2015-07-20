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

package nya.miku.wishmaster.chans.chan420;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.WakabaUtils;
import nya.miku.wishmaster.chans.AbstractChanModule;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONObject;

//@SuppressWarnings("deprecation")
//https://issues.apache.org/jira/browse/HTTPCLIENT-1632
public class Chan420Module extends AbstractChanModule {
    
    static final String CHAN_NAME = "420chan.org";
    private static final String PREF_KEY_USE_HTTPS = "PREF_KEY_USE_HTTPS";
    
    private Map<String, BoardModel> boardsMap = null;
    
    public Chan420Module(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "420chan";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_420chan, null);
    }
    
    private JSONObject downloadJSONObject(String url, boolean checkIfModidied, ProgressListener listener, CancellableTask task) throws Exception {
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModidied).build();
        JSONObject object = HttpStreamer.getInstance().getJSONObjectFromUrl(url, rqModel, httpClient, listener, task, false);
        if (task != null && task.isCancelled()) throw new Exception("interrupted");
        if (listener != null) listener.setIndeterminate();
        return object;
    }
    
    private JSONArray downloadJSONArray(String url, boolean checkIfModidied, ProgressListener listener, CancellableTask task) throws Exception {
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModidied).build();
        JSONArray array = HttpStreamer.getInstance().getJSONArrayFromUrl(url, rqModel, httpClient, listener, task, false);
        if (task != null && task.isCancelled()) throw new Exception("interrupted");
        if (listener != null) listener.setIndeterminate();
        return array;
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        Context context = preferenceGroup.getContext();
        addPasswordPreference(preferenceGroup);
        CheckBoxPreference httpsPref = new CheckBoxPreference(context);
        httpsPref.setTitle(R.string.pref_use_https);
        httpsPref.setSummary(R.string.pref_use_https_summary);
        httpsPref.setKey(getSharedKey(PREF_KEY_USE_HTTPS));
        httpsPref.setDefaultValue(true);
        preferenceGroup.addPreference(httpsPref);
        addUnsafeSslPreference(preferenceGroup, getSharedKey(PREF_KEY_USE_HTTPS));
        addProxyPreferences(preferenceGroup);
    }
    
    private boolean useHttps() {
        return preferences.getBoolean(getSharedKey(PREF_KEY_USE_HTTPS), true);
    }
    
    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception {
        String catsUrl = (useHttps() ? "https://" : "http://") + "api.420chan.org/categories.json";
        String boardsUrl = (useHttps() ? "https://" : "http://") + "api.420chan.org/boards.json";
        JSONObject catsJson = downloadJSONObject(catsUrl, (oldBoardsList != null && boardsMap != null), listener, task);
        JSONObject boardsJson = downloadJSONObject(boardsUrl, (oldBoardsList != null && boardsMap != null), listener, task);
        if (catsJson == null && boardsJson == null) return oldBoardsList;
        if (catsJson == null) catsJson = downloadJSONObject(catsUrl, (oldBoardsList != null && boardsMap != null), listener, task);
        if (boardsJson == null) boardsJson = downloadJSONObject(boardsUrl, (oldBoardsList != null && boardsMap != null), listener, task);
        
        List<SimpleBoardModel> list = Chan420JsonMapper.mapBoards(catsJson, boardsJson);
        Map<String, BoardModel> newMap = new HashMap<String, BoardModel>();
        for (SimpleBoardModel board : list) {
            BoardModel model = Chan420JsonMapper.getDefaultBoardModel(board.boardName);
            model.boardDescription = board.boardDescription;
            model.boardCategory = board.boardCategory;
            model.nsfw = board.nsfw;
            newMap.put(model.boardName, model);
        }
        
        boardsMap = newMap;
        return list.toArray(new SimpleBoardModel[list.size()]);
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        if (boardsMap == null) {
            try {
                getBoardsList(listener, task, null);
            } catch (Exception e) {}
        }
        if (boardsMap != null && boardsMap.containsKey(shortName)) return boardsMap.get(shortName);
        return Chan420JsonMapper.getDefaultBoardModel(shortName);
    }
    
    @Override
    public ThreadModel[] getThreadsList(String boardName, int page, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        String url = (useHttps() ? "https://" : "http://") + "api.420chan.org/" + boardName + "/catalog.json";
        JSONArray response = downloadJSONArray(url, oldList != null, listener, task);
        if (response == null) return oldList; //if not modified
        List<ThreadModel> threads = new ArrayList<>();
        for (int i=0, len=response.length(); i<len; ++i) {
            JSONArray curArray = response.getJSONObject(i).getJSONArray("threads");
            for (int j=0, clen=curArray.length(); j<clen; ++j) {
                JSONObject curThreadJson = curArray.getJSONObject(j);
                ThreadModel curThread = new ThreadModel();
                curThread.threadNumber = Long.toString(curThreadJson.getLong("no"));
                curThread.postsCount = curThreadJson.optInt("replies", -2) + 1;
                curThread.attachmentsCount = curThreadJson.optInt("images", -2) + 1;
                curThread.isSticky = curThreadJson.optInt("sticky") == 1;
                curThread.isClosed = curThreadJson.optInt("closed") == 1;
                curThread.posts = new PostModel[] { Chan420JsonMapper.mapPostModel(curThreadJson, boardName) };
                threads.add(curThread);
            }
        }
        return threads.toArray(new ThreadModel[threads.size()]);
    }
    
    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        String url = (useHttps() ? "https://" : "http://") + "api.420chan.org/" + boardName + "/res/" + threadNumber + ".json";
        JSONObject response = downloadJSONObject(url, oldList != null, listener, task);
        if (response == null) return oldList; //if not modified
        JSONArray posts = response.getJSONArray("posts");
        PostModel[] result = new PostModel[posts.length()];
        for (int i=0, len=posts.length(); i<len; ++i) {
            result[i] = Chan420JsonMapper.mapPostModel(posts.getJSONObject(i), boardName);
        }
        if (oldList != null) {
            result = ChanModels.mergePostsLists(Arrays.asList(oldList), Arrays.asList(result));
        }
        return result;
    }
    
    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        if (!model.chanName.equals(getChanName())) throw new IllegalArgumentException("wrong chan");
        String usingUrl = (useHttps() ? "https://" : "http://") + (model.type == UrlPageModel.TYPE_INDEXPAGE ? "" : "boards.") + "420chan.org/";
        return WakabaUtils.buildUrl(model, usingUrl).replace(".html", ".php");
    }
    
    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        return WakabaUtils.parseUrl(url.replace("boards.420chan.org", "420chan.org").replace(".php", ".html"), getChanName(), "420chan.org");
    }

}
