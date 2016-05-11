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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.SequenceInputStream;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.message.BasicNameValuePair;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import android.text.InputType;
import android.text.TextUtils;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractKusabaModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.ReplacingReader;
import nya.miku.wishmaster.api.util.WakabaReader;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;

public class NullchanccModule extends AbstractKusabaModule {
    private static final String TAG = "NullchanccModule";
    
    private static final String CHAN_NAME = "0chan.cc";
    private static final String DEFAULT_DOMAIN = "0chan.cc";
    private static final String DOMAINS_HINT = "0chan.cc, 31.220.3.61";
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Бред", "all", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "d", "Рисунки", "all", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "r", "Реквесты", "all", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "0", "О Нульчане", "all", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "e", "Радиоэлектроника", "geek", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "t", "Технологии", "geek", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "hw", "Железо", "geek", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "s", "Софт", "geek", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "c", "Быдлокодинг", "geek", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "vg", "Видеоигры", "geek", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "8", "8-bit и pixel art", "geek", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "bg", "Настольные игры", "geek", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "wh", "Warhammer", "geek", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Аниме", "other", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "au", "Автомобили", "other", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "bo", "Книги", "other", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "co", "Комиксы", "other", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "cook", "Лепка супов", "other", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "f", "Flash", "other", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fa", "Мода и стиль", "other", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fl", "Иностранные языки", "other", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "m", "Музыка", "other", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "med", "Медицина", "other", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ne", "Кошки", "other", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ph", "Фотографии", "other", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "tv", "Кино и сериалы", "other", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "wp", "Обои", "other", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "war", "Вооружение", "other", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "h", "Хентай", "adult", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "g", "Девушки", "adult", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fur", "Фурри", "adult", true)
        };
    private static final String PREF_KEY_ONLY_NEW_POSTS = "PREF_KEY_ONLY_NEW_POSTS";
    private static final String PREF_KEY_DOMAIN = "PREF_KEY_DOMAIN";
    private static final Pattern PATTERN_EMBEDDED = Pattern.compile("<div (?:[^>]*)data-id=\"([^\"]*)\"(?:[^>]*)>", Pattern.DOTALL);
    
    public NullchanccModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Øчан (0chan.cc)";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_0chan, null);
    }
    
    @Override
    protected String getUsingDomain() {
        String domain = preferences.getString(getSharedKey(PREF_KEY_DOMAIN), DEFAULT_DOMAIN);
        return TextUtils.isEmpty(domain) ? DEFAULT_DOMAIN : domain;
    }
    
    @Override
    protected String[] getAllDomains() {
        if (!getChanName().equals(CHAN_NAME) || getUsingDomain().equals(DEFAULT_DOMAIN))
            return super.getAllDomains();
        return new String[] { DEFAULT_DOMAIN, getUsingDomain() };
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    protected boolean canCloudflare() {
        return true;
    }
    
    private void addDomainPreference(PreferenceGroup group) {
        if (!getChanName().equals(CHAN_NAME)) return;
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
    
    private void addOnlyNewPostsPreference(PreferenceGroup group) {
        Context context = group.getContext();
        CheckBoxPreference onlyNewPostsPreference = new CheckBoxPreference(context);
        onlyNewPostsPreference.setTitle(R.string.pref_only_new_posts);
        onlyNewPostsPreference.setSummary(R.string.pref_only_new_posts_summary);
        onlyNewPostsPreference.setKey(getSharedKey(PREF_KEY_ONLY_NEW_POSTS));
        onlyNewPostsPreference.setDefaultValue(true);
        group.addItemFromInflater(onlyNewPostsPreference);
    }
    
    private boolean loadOnlyNewPosts() {
        return preferences.getBoolean(getSharedKey(PREF_KEY_ONLY_NEW_POSTS), true);
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        addOnlyNewPostsPreference(preferenceGroup);
        addDomainPreference(preferenceGroup);
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
        model.requiredFileForNewThread = !shortName.equals("0");
        model.allowReport = BoardModel.REPORT_SIMPLE;
        model.allowNames = !shortName.equals("b");
        model.allowEmails = false;
        return model;
    }
    
    @SuppressLint("SimpleDateFormat")
    @Override
    protected WakabaReader getWakabaReader(InputStream stream, UrlPageModel urlModel) {
        Reader reader;
        if (urlModel != null && urlModel.chanName != null && urlModel.chanName.equals("expand")) {
            stream = new SequenceInputStream(new ByteArrayInputStream("<form id=\"delform\">".getBytes()), stream);
            reader = new BufferedReader(new InputStreamReader(stream));
        } else {
            if (getChanName().equals(CHAN_NAME)) {
                reader = new ReplacingReader(new BufferedReader(new InputStreamReader(stream)), "<form id=\"delform20\"", "<form id=\"delform\"");
            } else {
                reader = new BufferedReader(new InputStreamReader(stream));
            }
        }
        return new WakabaReader(reader, null, true) {
            private final DateFormat dateFormat;
            {
                DateFormatSymbols symbols = new DateFormatSymbols();
                symbols.setShortMonths(new String[] { "Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек"});
                dateFormat = new SimpleDateFormat("yyyy MMM dd HH:mm:ss", symbols);
                dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+3"));
            }
            @Override
            protected void parseDate(String date) {
                if (date.length() > 0) {
                    date = date.replace("&#35;", "");
                    date = date.replaceAll("(?:[^\\d]*)(\\d(?:.*))", "$1");
                    try {
                        currentPost.timestamp = dateFormat.parse(date).getTime();
                    } catch (Exception e) {
                        Logger.e(TAG, "cannot parse date", e);
                    }
                }
            }
            @Override
            protected void parseOmittedString(String omitted) {
                if (omitted.indexOf('>') != -1) omitted = omitted.substring(omitted.indexOf('>'));
                super.parseOmittedString(omitted);
            }
            @Override
            protected void postprocessPost(PostModel post) {
                Matcher matcher = PATTERN_EMBEDDED.matcher(post.comment);
                while (matcher.find()) {
                    String id = matcher.group(1);
                    String div = matcher.group(0).toLowerCase(Locale.US);
                    String url = null;
                    if (div.contains("youtube")) {
                        url = "http://www.youtube.com/watch?v=" + id;
                    } else if (div.contains("vimeo")) {
                        url = "http://vimeo.com/" + id;
                    } else if (div.contains("coub")) {
                        url = "http://coub.com/view/" + id;
                    }
                    if (url != null) {
                        AttachmentModel attachment = new AttachmentModel();
                        attachment.type = AttachmentModel.TYPE_OTHER_NOTFILE;
                        attachment.path = url;
                        attachment.thumbnail = div.contains("youtube") ? ("http://img.youtube.com/vi/" + id + "/default.jpg") : null;
                        int oldCount = post.attachments != null ? post.attachments.length : 0;
                        AttachmentModel[] attachments = new AttachmentModel[oldCount + 1];
                        for (int i=0; i<oldCount; ++i) attachments[i] = post.attachments[i];
                        attachments[oldCount] = attachment;
                        post.attachments = attachments;
                    }
                }
            }
        };
    }
    
    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        if (loadOnlyNewPosts() && oldList != null && oldList.length > 0) {
            String url = getUsingUrl() + "expand.php?after=" + oldList[oldList.length-1].number + "&board=" + boardName + "&threadid=" + threadNumber;
            UrlPageModel object = new UrlPageModel();
            object.chanName = "expand";
            ThreadModel[] page = readWakabaPage(url, listener, task, true, object);
            if (page != null && page.length > 0) {
                PostModel[] posts = new PostModel[oldList.length + page[0].posts.length];
                for (int i=0; i<oldList.length; ++i) posts[i] = oldList[i];
                for (int i=0; i<page[0].posts.length; ++i) posts[oldList.length + i] = page[0].posts[i];
                return posts;
            } else {
                return oldList;
            }
        }
        return super.getPostsList(boardName, threadNumber, listener, task, oldList);
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        String captchaUrl = getUsingUrl() + "captcha.php?" + Math.random();
        CaptchaModel captchaModel = downloadCaptcha(captchaUrl, listener, task);
        captchaModel.type = CaptchaModel.TYPE_NORMAL_DIGITS;
        return captchaModel;
    }
    
    @Override
    protected void setSendPostEntity(SendPostModel model, ExtendedMultipartBuilder postEntityBuilder) throws Exception {
        postEntityBuilder.
                addString("board", model.boardName).
                addString("replythread", model.threadNumber == null ? "0" : model.threadNumber).
                addString("name", model.name);
        if (model.sage) postEntityBuilder.addString("em", "sage");
        postEntityBuilder.
                addString("captcha", model.captchaAnswer).
                addString("subject", model.subject).
                addString("message", model.comment).
                addString("postpassword", model.password);
        setSendPostEntityAttachments(model, postEntityBuilder);
        postEntityBuilder.addString("embed", "");
        
        postEntityBuilder.addString("redirecttothread", "1");
    }
    
    @Override
    protected List<? extends NameValuePair> getReportFormAllValues(DeletePostModel model) {
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("board", model.boardName));
        pairs.add(new BasicNameValuePair("post[]", model.postNumber));
        pairs.add(new BasicNameValuePair("reportpost", "Отправить"));
        return pairs;
    }
    
    @Override
    protected String getDeleteFormValue(DeletePostModel model) {
        return "Удалить";
    }
    
    @Override
    public String fixRelativeUrl(String url) {
        if (useHttps()) url = url.replace("http://0chan.cc", "https://0chan.cc");
        return super.fixRelativeUrl(url);
    }
    
}
