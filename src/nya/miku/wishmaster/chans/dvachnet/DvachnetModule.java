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

package nya.miku.wishmaster.chans.dvachnet;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.message.BasicNameValuePair;
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
import nya.miku.wishmaster.api.AbstractWakabaModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class DvachnetModule extends AbstractWakabaModule {
    
    static final String CHAN_NAME = "dva-ch.net";
    private static final String DEFAULT_DOMAIN = "dva-ch.com";
    private static final String DOMAINS_HINT = "dva-ch.com, 2ch.rip";
    private static final String[] DOMAINS = new String[] { DEFAULT_DOMAIN, "2ch.rip" };
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Бред", "Обсуждения", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "d", "Дискуссии о Два.ч ", "Обсуждения", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "dg", "Общие рассуждения", "Обсуждения", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "au", "Автомобили", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "bg", "Настольные игры", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "bi", "Велосипеды", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "bo", "Книги", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "c", "Мультипликация", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "dev", "Девчач", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "di", "Столовая", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "em", "Эмиграция", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ew", "Конец света", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "f", "Flash", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fa", "Мода", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fi", "Фигурки", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fl", "Иностранные языки", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "hi", "История", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "hr", "Высокое разрешение", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "me", "Медицина", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mo", "Мотоциклы", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mu", "Музыка", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ne", "Кошки", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "p", "Фото", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "pa", "Живопись", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ph", "Философия", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "po", "Политика", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "pr", "Программирование", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "psy", "Психология", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "r", "Просьбы", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "s", "Программы", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "sci", "Наука", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "sn", "Паранормальные явления", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "sp", "Спорт", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "t", "Технологии", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "td", "Трёхмерная графика", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "tr", "Транспорт", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "trv", "Путешествия", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "tv", "ТВ и кино", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "un", "Образование", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "vg", "Видеоигры", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "w", "Оружие", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "wh", "Warhammer", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "wm", "Военная техника", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "wp", "Обои", "Тематика", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Аниме", "Аниме", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "aa", "Аниме арт", "Аниме", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fd", "Фэндом", "Аниме", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ja", "Японофилия", "Аниме", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ma", "Манга", "Аниме", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fg", "Трапы", "Взрослым", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "g", "Девушки", "Взрослым", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ga", "Геи", "Взрослым", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "h", "Хентай", "Взрослым", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ho", "Прочий хентай", "Взрослым", true)
    };
    private static final String PREF_KEY_DOMAIN = "PREF_KEY_DOMAIN";
    private static final Pattern ERROR_PATTERN = Pattern.compile("<h1[^>]*>(.*?)</h1>");
    
    private Map<String, BoardModel> boardsMap = new HashMap<>();
    
    private String captchaId = "";
    
    public DvachnetModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Два.ч (dva-ch.com)";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_dvach, null);
    }
    
    @Override
    protected String getUsingDomain() {
        String domain = preferences.getString(getSharedKey(PREF_KEY_DOMAIN), DEFAULT_DOMAIN);
        return TextUtils.isEmpty(domain) ? DEFAULT_DOMAIN : domain;
    }
    
    @Override
    protected String[] getAllDomains() {
        String domain = getUsingDomain();
        for (String d : DOMAINS) if (domain.equals(d)) return DOMAINS;
        String[] domains = new String[DOMAINS.length + 1];
        for (int i=0; i<DOMAINS.length; ++i) domains[i] = DOMAINS[i];
        domains[DOMAINS.length] = domain;
        return domains;
    }
    
    @Override
    protected boolean canCloudflare() {
        return true;
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    private void addDomainPreference(PreferenceGroup group) {
        Context context = group.getContext();
        EditTextPreference domainPref = new EditTextPreference(context);
        domainPref.setTitle(R.string.pref_domain);
        domainPref.setSummary(resources.getString(R.string.pref_domain_summary, DOMAINS_HINT));
        domainPref.setDialogTitle(R.string.pref_domain);
        domainPref.setKey(getSharedKey(PREF_KEY_DOMAIN));
        domainPref.getEditText().setHint(DEFAULT_DOMAIN);
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
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        if (boardsMap.containsKey(shortName)) return boardsMap.get(shortName);
        
        try {
            JSONObject json = downloadJSONObject(getUsingUrl() + shortName + "/index.json", false, listener, task);
            SimpleBoardModel simpleModel = getBoardsMap(listener, task).get(shortName);
            if (simpleModel == null) simpleModel = ChanModels.obtainSimpleBoardModel(CHAN_NAME, shortName, shortName, "", false);
            BoardModel board = DvachnetJsonMapper.mapBoardModel(json, simpleModel);
            boardsMap.put(shortName, board);
            return board;
        } catch (Exception e) {
            return DvachnetJsonMapper.getDefaultBoardModel(shortName, shortName, "", false);
        }
    }
    
    @Override
    public ThreadModel[] getThreadsList(String boardName, int page, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        JSONObject json = downloadJSONObject(getUsingUrl() + boardName + "/" + (page == 0 ? "index" : Integer.toString(page)) + ".json",
                oldList != null, listener, task);
        if (json == null) return oldList;
        try {
            SimpleBoardModel simpleModel = getBoardsMap(listener, task).get(boardName);
            if (simpleModel == null) simpleModel = ChanModels.obtainSimpleBoardModel(CHAN_NAME, boardName, boardName, "", false);
            BoardModel board = DvachnetJsonMapper.mapBoardModel(json, simpleModel);
            boardsMap.put(boardName, board);
        } catch (Exception e) {}
        
        JSONArray threadsJson = json.getJSONArray("threads");
        ThreadModel[] threads = new ThreadModel[threadsJson.length()];
        for (int i=0; i<threads.length; ++i) {
            JSONObject thread = threadsJson.getJSONObject(i);
            threads[i] = new ThreadModel();
            threads[i].postsCount = thread.optInt("posts_count", -1);
            threads[i].attachmentsCount = thread.optInt("files_count", -1);
            JSONArray postsJson = thread.getJSONArray("posts");
            threads[i].posts = new PostModel[postsJson.length()];
            for (int j=0; j<threads[i].posts.length; ++j) {
                threads[i].posts[j] = DvachnetJsonMapper.mapPostModel(postsJson.getJSONObject(j));
            }
            if (threads[i].postsCount != -1) threads[i].postsCount += threads[i].posts.length;
            if (threads[i].attachmentsCount != -1) {
                int attachments = 0;
                for (PostModel post : threads[i].posts) attachments += (post.attachments != null ? post.attachments.length : 0);
                threads[i].attachmentsCount += attachments;
            }
            threads[i].isSticky = postsJson.getJSONObject(0).optInt("sticky") == 1;
            threads[i].isClosed = postsJson.getJSONObject(0).optInt("closed") == 1;
        }
        return threads;
    }
    
    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        JSONObject json = downloadJSONObject(getUsingUrl() + boardName + "/res/" + threadNumber + ".json",
                oldList != null, listener, task);
        if (json == null) return oldList;
        try {
            SimpleBoardModel simpleModel = getBoardsMap(listener, task).get(boardName);
            if (simpleModel == null) simpleModel = ChanModels.obtainSimpleBoardModel(CHAN_NAME, boardName, boardName, "", false);
            BoardModel board = DvachnetJsonMapper.mapBoardModel(json, simpleModel);
            boardsMap.put(boardName, board);
        } catch (Exception e) {}
        
        JSONArray postsJson = json.getJSONArray("threads").getJSONObject(0).getJSONArray("posts");
        PostModel[] posts = new PostModel[postsJson.length()];
        for (int i=0; i<posts.length; ++i) {
            posts[i] = DvachnetJsonMapper.mapPostModel(postsJson.getJSONObject(i));
        }
        
        return oldList != null ? ChanModels.mergePostsLists(Arrays.asList(oldList), Arrays.asList(posts)) : posts;
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        HttpRequestModel get = HttpRequestModel.DEFAULT_GET;
        captchaId = HttpStreamer.getInstance().getStringFromUrl(getUsingUrl() + "cgi/captcha?task=get_id", get, httpClient, listener, task, false);
        String captchaUrl = getUsingUrl() + "cgi/captcha?task=get_image&id=" + captchaId;
        return downloadCaptcha(captchaUrl, listener, task);
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "cgi/posting";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("task", "post").
                addString("board", model.boardName).
                addString("parent", model.threadNumber == null ? "0" : model.threadNumber).
                addString("name", model.name).
                addString("email", model.sage ? "sage" : model.email).
                addString("subject", model.subject).
                addString("comment", model.comment).
                addString("captcha_id", captchaId).
                addString("captcha_value", model.captchaAnswer).
                addString("password", model.password).
                addString("json", "1");
        if (model.attachments != null && model.attachments.length > 0)
            postEntityBuilder.addFile("image", model.attachments[0], model.randomHash);
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 302) {
                for (Header header : response.headers) {
                    if (header != null && HttpHeaders.LOCATION.equalsIgnoreCase(header.getName())) {
                        if (header.getValue() == null || header.getValue().trim().length() == 0) return null;
                        return fixRelativeUrl(header.getValue());
                    }
                }
            } else if (response.statusCode == 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                Matcher errorMatcher = ERROR_PATTERN.matcher(htmlResponse);
                if (errorMatcher.find()) {
                    throw new Exception(errorMatcher.group(1).trim());
                }
                JSONObject jsonResponse = new JSONObject(htmlResponse);
                if (jsonResponse.optString("message_title").equalsIgnoreCase("Ошибка")) {
                    throw new Exception(jsonResponse.optString("message", "Ошибка"));
                }
            } else throw new Exception(response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
        }
        
        return null;
    }
    
    @Override
    public String deletePost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "cgi/delete";
        
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("board", model.boardName));
        pairs.add(new BasicNameValuePair("delete_" + model.postNumber, model.postNumber));
        pairs.add(new BasicNameValuePair("task", "delete"));
        pairs.add(new BasicNameValuePair("password", model.password));
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntity(pairs, "UTF-8")).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 302) {
                return null;
            }
            throw new Exception(response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
        }
    }
}
