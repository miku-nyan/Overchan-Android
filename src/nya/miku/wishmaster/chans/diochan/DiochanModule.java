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

package nya.miku.wishmaster.chans.diochan;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;

import org.apache.commons.lang3.StringEscapeUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractKusabaModule;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BadgeIconModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.api.util.WakabaReader;
import nya.miku.wishmaster.common.Logger;

public class DiochanModule extends AbstractKusabaModule {
    public static final String[] DOMAINS = {"www.diochan.com", "diochan.com"};
    private static final String TAG = "DiochanModule";
    private static final DateFormat DATE_FORMAT;
    private static final String CHAN_NAME = "www.diochan.com";
    private static final String DISPLAYING_NAME = "Diochan";
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[]{
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Random", "", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "int", "International", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "s", "Sexy Beautiful Women", "", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "hd", "Help Desk", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "h", "Hentai", "", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Anime", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "v", "Video Games", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "co", "Fumetti e Cartoni", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "film", "Film", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "litness", "La palestra della mente", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ck", "Cucina", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mu", "Musica", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "sci", "Scienza & Tecnologia", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "pol", "Politica", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "scr", "Scrittura", "", false),
    };

    static {
        DATE_FORMAT = new SimpleDateFormat("dd/MM/yy HH:mm");
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+2"));
    }

    public DiochanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }

    @Override
    protected String getUsingDomain() {
        return DOMAINS[0];
    }

    @Override
    public String getChanName() {
        return CHAN_NAME;
    }

    @Override
    public String getDisplayingName() {
        return DISPLAYING_NAME;
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
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_diochan, null);
    }

    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }

    @Override
    protected WakabaReader getKusabaReader(InputStream stream, UrlPageModel urlModel) {
        return new DiochanReader(stream, DATE_FORMAT, canCloudflare(), getKusabaFlags());
    }

    private class DiochanReader extends KusabaReader {
        private final Pattern PATTERN_IFRAME =
                Pattern.compile("<iframe([^>]*)>", Pattern.DOTALL);

        private final Pattern PATTERN_SRC =
                Pattern.compile("src=\"([^\"]+)\"");

        private final Pattern PATTERN_SCRIPT =
                Pattern.compile("<script type=\"text/javascript\">[^<]+</script>", Pattern.DOTALL);
        private final Pattern ATTACHMENT_SIZE_PATTERN =
                Pattern.compile("([,\\.\\d]+) ?([km])[b]", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        private final Pattern ATTACHMENT_PX_SIZE_PATTERN = Pattern.compile("(\\d+)x(\\d+)");
        private final Pattern ATTACHMENT_ORIGINAL_NAME_PATTERN = Pattern.compile("title=\"([^\"]*)\"");
        private final Pattern ATTACHMENT_THUMBNAIL_PATTERN = Pattern.compile("<img src=\"([^\"]*)\"");
        private final char[] COUNTRY_ICON_FILTER = "<span class=\"flags\">".toCharArray();
        private final char[] ATTACHMENT_START_FILTER = "<div class=\"file_reply\">".toCharArray();
        private final char[] ATTACHMENT_END = "</div>".toCharArray();
        private final String FILE_INFO_START = "<span class=\"fileinfo\">";
        private final String FILE_INFO_END = "</span>";
        private int iconCurPos = 0;
        private int attachmentCurPos = 0;

        public DiochanReader(InputStream in, DateFormat dateFormat, boolean canCloudflare, int flags) {
            super(in, dateFormat, canCloudflare, flags);
        }

        public DiochanReader(Reader reader, DateFormat dateFormat, boolean canCloudflare, int flags) {
            super(reader, dateFormat, canCloudflare, flags);
        }

        private void parseDiochanAttachment(String html) throws IOException {
            AttachmentModel attachment = new AttachmentModel();
            attachment.size = -1;

            int startHref, endHref;
            if ((startHref = html.indexOf("href=\"")) != -1 && (endHref = html.indexOf('\"', startHref + 6)) != -1) {
                attachment.path = html.substring(startHref + 6, endHref);
                String pathLower = attachment.path.toLowerCase(Locale.US);
                if (pathLower.endsWith(".jpg") || pathLower.endsWith(".jpeg") || pathLower.endsWith(".png"))
                    attachment.type = AttachmentModel.TYPE_IMAGE_STATIC;
                else if (pathLower.endsWith(".gif"))
                    attachment.type = AttachmentModel.TYPE_IMAGE_GIF;
                else if (pathLower.endsWith(".svg") || pathLower.endsWith(".svgz"))
                    attachment.type = AttachmentModel.TYPE_IMAGE_SVG;
                else if (pathLower.endsWith(".webm") || pathLower.endsWith(".mp4") || pathLower.endsWith(".ogv"))
                    attachment.type = AttachmentModel.TYPE_VIDEO;
                else if (pathLower.endsWith(".mp3") || pathLower.endsWith(".ogg"))
                    attachment.type = AttachmentModel.TYPE_AUDIO;
                else if (pathLower.startsWith("http") && (pathLower.contains("youtube.")))
                    attachment.type = AttachmentModel.TYPE_OTHER_NOTFILE;
                else
                    attachment.type = AttachmentModel.TYPE_OTHER_FILE;
            } else {
                return;
            }
            int fileInfoStart = html.indexOf(FILE_INFO_START);
            int fileInfoEnd = html.indexOf(FILE_INFO_END, fileInfoStart + FILE_INFO_START.length());
            if (fileInfoStart >=0 && fileInfoEnd > 0) {
                String fileInfo = html.substring(fileInfoStart + FILE_INFO_START.length(), fileInfoEnd);
                Matcher byteSizeMatcher = ATTACHMENT_SIZE_PATTERN.matcher(fileInfo);
                if (byteSizeMatcher.find()) {
                    try {
                        String digits = byteSizeMatcher.group(1).replace(',', '.');
                        int multiplier = 1;
                        String prefix = byteSizeMatcher.group(2);
                        if (prefix != null) {
                            if (prefix.equalsIgnoreCase("к") || prefix.equalsIgnoreCase("k")) multiplier = 1024;
                            else if (prefix.equalsIgnoreCase("м") || prefix.equalsIgnoreCase("m")) multiplier = 1024 * 1024;
                        }
                        int value = Math.round(Float.parseFloat(digits) / 1024 * multiplier);
                        attachment.size = value;
                    } catch (NumberFormatException e) {}
                }

                Matcher pxSizeMatcher = ATTACHMENT_PX_SIZE_PATTERN.matcher(fileInfo);
                if (pxSizeMatcher.find()) {
                    try {
                        int width = Integer.parseInt(pxSizeMatcher.group(1));
                        int height = Integer.parseInt(pxSizeMatcher.group(2));
                        attachment.width = width;
                        attachment.height = height;
                    } catch (NumberFormatException e) {}
                }
            }
            
            Matcher originalNameMatcher = ATTACHMENT_ORIGINAL_NAME_PATTERN.matcher(html);
            if (originalNameMatcher.find()) {
                String originalName = originalNameMatcher.group(1).trim();
                if (originalName.length() > 0) {
                    attachment.originalName = StringEscapeUtils.unescapeHtml4(originalName);
                    try {
                        attachment.originalName += attachment.path.substring(attachment.path.lastIndexOf("."));
                    } catch (IndexOutOfBoundsException e) {}
                    
                }
            }

            Matcher thumbnailMatcher = ATTACHMENT_THUMBNAIL_PATTERN.matcher(html);
            if (thumbnailMatcher.find()) {
                String thumbnail = thumbnailMatcher.group(1);
                if (thumbnail.length() > 0) {
                    attachment.thumbnail = StringEscapeUtils.unescapeHtml4(thumbnail);
                }
            }
            
            ++currentThread.attachmentsCount;
            currentAttachments.add(attachment);
        }
        
        @Override
        protected void customFilters(int ch) throws IOException {
            super.customFilters(ch);
            if (ch == COUNTRY_ICON_FILTER[iconCurPos]) {
                ++iconCurPos;
                if (iconCurPos == COUNTRY_ICON_FILTER.length) {
                    BadgeIconModel iconModel = new BadgeIconModel();
                    String htmlIcon = readUntilSequence("</span>".toCharArray());
                    int start, end;
                    if ((start = htmlIcon.indexOf("src=\"")) != -1 && (end = htmlIcon.indexOf('\"', start + 5)) != -1) {
                        iconModel.source = htmlIcon.substring(start + 5, end);
                    }
                    iconModel.description = "";
                    int currentIconsCount = currentPost.icons == null ? 0 : currentPost.icons.length;
                    BadgeIconModel[] newIconsArray = new BadgeIconModel[currentIconsCount + 1];
                    for (int i = 0; i < currentIconsCount; ++i)
                        newIconsArray[i] = currentPost.icons[i];
                    newIconsArray[currentIconsCount] = iconModel;
                    currentPost.icons = newIconsArray;
                    iconCurPos = 0;
                }
            } else {
                if (iconCurPos != 0) iconCurPos = ch == COUNTRY_ICON_FILTER[0] ? 1 : 0;
            }
            if (ch == ATTACHMENT_START_FILTER[attachmentCurPos]) {
                ++attachmentCurPos;
                if (attachmentCurPos == ATTACHMENT_START_FILTER.length) {
                    String html = readUntilSequence(ATTACHMENT_END);
                    parseDiochanAttachment(html);
                    attachmentCurPos = 0;
                }
            } else {
                if (attachmentCurPos != 0) attachmentCurPos = ch == ATTACHMENT_START_FILTER[0] ? 1 : 0;
            }
        }

        @Override
        protected void parseDate(String date) {
            date = RegexUtils.removeHtmlTags(date).trim();
            date = RegexUtils.replaceAll(date, Pattern.compile("\\([A-Za-z]+\\)\\s?"), " ");
            if (date.length() > 0) {
                try {
                    currentPost.timestamp = dateFormat.parse(date).getTime();
                } catch (Exception e) {
                    Logger.e(TAG, "cannot parse date; make sure you choose the right DateFormat for this chan", e);
                }
            }
        }
        
        @Override
        protected void postprocessPost(PostModel post) {
            super.postprocessPost(post);
            post.comment = RegexUtils.replaceAll(post.comment, PATTERN_SCRIPT, "");
            Matcher matcher = PATTERN_IFRAME.matcher(post.comment);
            while (matcher.find()) {
                Matcher srcMatcher = PATTERN_SRC.matcher(matcher.group(1));
                if (!srcMatcher.find()) continue;
                String url = srcMatcher.group(1).replace("youtube.com/embed/", "youtube.com/watch?v=");
                String id = null;
                if (url.contains("youtube") && url.contains("v=")) {
                    id = url.substring(url.indexOf("v=") + 2);
                    if (id.contains("&")) id = id.substring(0, id.indexOf("&"));
                }
                AttachmentModel attachment = new AttachmentModel();
                attachment.type = AttachmentModel.TYPE_OTHER_NOTFILE;
                attachment.size = -1;
                attachment.path = url;
                attachment.thumbnail = id != null ? ("http://img.youtube.com/vi/" + id + "/default.jpg") : null;

                int oldCount = post.attachments != null ? post.attachments.length : 0;
                AttachmentModel[] attachments = new AttachmentModel[oldCount + 1];
                for (int i = 0; i < oldCount; ++i) attachments[i] = post.attachments[i];
                attachments[oldCount] = attachment;
                post.attachments = attachments;
            }
        }
    }
}
