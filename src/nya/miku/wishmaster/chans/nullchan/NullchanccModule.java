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
import android.annotation.SuppressLint;
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
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.ReplacingReader;
import nya.miku.wishmaster.api.util.WakabaReader;

public class NullchanccModule extends AbstractInstant0chan {
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
    private static final String PREF_KEY_DOMAIN = "PREF_KEY_DOMAIN";
    
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
        BoardModel model = super.getBoard(shortName, listener, task);
        model.defaultUserName = "Аноним";
        return model;
    }
    
    @SuppressLint("SimpleDateFormat")
    @Override
    protected WakabaReader getKusabaReader(InputStream stream, UrlPageModel urlModel) {
        Reader reader;
        if (urlModel != null && urlModel.chanName != null && urlModel.chanName.equals("expand")) {
            stream = new SequenceInputStream(new ByteArrayInputStream("<form id=\"delform\">".getBytes()), stream);
            reader = new BufferedReader(new InputStreamReader(stream));
        } else {
            reader = new ReplacingReader(new BufferedReader(new InputStreamReader(stream)), "<form id=\"delform20\"", "<form id=\"delform\"");
        }
        return new Instant0chanReader(reader, canCloudflare());
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        CaptchaModel captcha = super.getNewCaptcha(boardName, threadNumber, listener, task);
        captcha.type = CaptchaModel.TYPE_NORMAL_DIGITS;
        return captcha;
    }
    
    @Override
    public String fixRelativeUrl(String url) {
        if (useHttps()) url = url.replace("http://0chan.cc", "https://0chan.cc");
        return super.fixRelativeUrl(url);
    }
    
}
