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

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BadgeIconModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.CryptoUtils;
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.api.util.WakabaReader;

public class NullchaneuModule extends AbstractInstant0chan {
    private static final String CHAN_NAME = "0chan.eu";
    private static final String DEFAULT_DOMAIN = "0chan.eu";
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
    
    public NullchaneuModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Øчан (0chan.eu)";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_0chan, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return DEFAULT_DOMAIN;
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    protected boolean canCloudflare() {
        return true;
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
	
    @Override
    public String fixRelativeUrl(String url) {
        if (useHttps()) url = url.replace("http://0chan.eu", "https://0chan.eu");
        return super.fixRelativeUrl(url);
    }
    
    private static class NulleuReader extends Instant0chanReader {
        private static final Pattern PATTERN_EMBEDDED = Pattern.compile("<param name=\"movie\"(?:[^>]*)value=\"([^\"]+)\"(?:[^>]*)>", Pattern.DOTALL);
        private static final Pattern PATTERN_COUNTRYBALL = Pattern.compile("class=\"_country_\"(?:.*)src=\"(.+)\"", Pattern.DOTALL);
        private static final Pattern PATTERN_TABULATION = Pattern.compile("^\\t+", Pattern.MULTILINE);
        private static final char[] USERID_FILTER = "<span class=\"hand\"".toCharArray();
        
        private int curUserIdPos = 0;
        
        public NulleuReader(InputStream stream, boolean canCloudflare) {
            super(stream, canCloudflare);
        }
        
        @Override
        protected void postprocessPost(PostModel post) {
            super.postprocessPost(post);
            //TODO: Remove indents in post by html parser
            post.comment = RegexUtils.replaceAll(post.comment, PATTERN_TABULATION , "");
            
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
        
        @Override
        protected void parseThumbnail(String imgTag) {
            super.parseThumbnail(imgTag);
            Matcher matcher = PATTERN_COUNTRYBALL.matcher(imgTag);
            if (matcher.find()) {
                BadgeIconModel iconModel = new BadgeIconModel();
                iconModel.source = matcher.group(1);
                int currentIconsCount = currentPost.icons == null ? 0 : currentPost.icons.length;
                BadgeIconModel[] newIconsArray = new BadgeIconModel[currentIconsCount + 1];
                for (int i=0; i<currentIconsCount; ++i) newIconsArray[i] = currentPost.icons[i];
                newIconsArray[currentIconsCount] = iconModel;
                currentPost.icons = newIconsArray;
            }
        }
    }
    
}
