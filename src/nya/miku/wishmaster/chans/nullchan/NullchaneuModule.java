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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.CryptoUtils;
import nya.miku.wishmaster.api.util.WakabaReader;

public class NullchaneuModule extends AbstractInstant0chan {
    private static final String CHAN_NAME = "0chan.eu";
    private static final String DEFAULT_DOMAIN = "0chan.ru.net";
    private static final String PREF_KEY_DOMAIN = "PREF_KEY_DOMAIN";
    
    public NullchaneuModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Øчан (0chan.ru.net)";
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
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.defaultUserName = "Аноним";
        model.catalogAllowed = false;
        return model;
    }
    
    @Override
    protected WakabaReader getKusabaReader(InputStream stream, UrlPageModel urlModel) {
        if ((urlModel != null) && (urlModel.chanName != null) && urlModel.chanName.equals("expand")) {
            stream = new SequenceInputStream(new ByteArrayInputStream("<form id=\"delform\">".getBytes()), stream);
        }
        return new NulleuReader(stream, canCloudflare());
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        String captchaUrl = getUsingUrl() + "myata.php?" + Math.random();
        CaptchaModel captcha = downloadCaptcha(captchaUrl, listener, task);
        captcha.type = CaptchaModel.TYPE_NORMAL;
        return captcha;
    }
    
    private static class NulleuReader extends Instant0chanReader {
        private static final Pattern PATTERN_EMBEDDED = Pattern.compile("<param name=\"movie\"(?:[^>]*)value=\"([^\"]+)\"(?:[^>]*)>", Pattern.DOTALL);
        private static final char[] USERID_FILTER = "<span class=\"hand\"".toCharArray();
        private int curUserIdPos = 0;
        
        public NulleuReader(InputStream stream, boolean canCloudflare) {
            super(stream, canCloudflare);
        }
        
        @Override
        protected void postprocessPost(PostModel post) {
            super.postprocessPost(post);
            
            Matcher matcher = PATTERN_EMBEDDED.matcher(post.comment);
            if (matcher.find()) {
                String url = matcher.group(1);
                if (url.contains("youtube.com/v/")) {
                    String id = url.substring(url.indexOf("v/") + 2);
                    AttachmentModel attachment = new AttachmentModel();
                    attachment.type = AttachmentModel.TYPE_OTHER_NOTFILE;
                    attachment.size = -1;
                    attachment.path = "http://www.youtube.com/watch?v=" + id;
                    attachment.thumbnail = "http://img.youtube.com/vi/" + id + "/default.jpg";
                    int oldCount = post.attachments != null ? post.attachments.length : 0;
                    AttachmentModel[] attachments = new AttachmentModel[oldCount + 1];
                    for (int i=0; i<oldCount; ++i) attachments[i] = post.attachments[i];
                    attachments[oldCount] = attachment;
                    post.attachments = attachments;
                }
            }
        }
        
        @Override
        protected void customFilters(int ch) throws IOException {
            super.customFilters(ch);
            if (ch == USERID_FILTER[curUserIdPos]) {
                ++curUserIdPos;
                if (curUserIdPos == USERID_FILTER.length) {
                    skipUntilSequence(">".toCharArray());
                    String id = readUntilSequence("</span>".toCharArray());
                    if (!id.isEmpty()) {
                        currentPost.name += (" ID:" + id);
                        if (!id.equalsIgnoreCase("Heaven")) {
                            currentPost.color = CryptoUtils.hashIdColor(id);
                        }
                    }
                    curUserIdPos = 0;
                }
            } else {
                if (curUserIdPos != 0) curUserIdPos = ch == USERID_FILTER[0] ? 1 : 0;
            }
        }
    }
    
}
