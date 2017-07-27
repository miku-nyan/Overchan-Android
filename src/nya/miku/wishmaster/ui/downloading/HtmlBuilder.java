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

package nya.miku.wishmaster.ui.downloading;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BadgeIconModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.cache.SerializablePage;
import nya.miku.wishmaster.common.MainApplication;
import org.apache.commons.lang3.StringEscapeUtils;

import android.content.res.Resources;
import android.graphics.Color;
import android.text.Html;

/**
 * Построение (компиляция) HTML-страницы.<br>
 * Строится на основе разметки страниц, генерируемых чистым движком wakaba.<br> 
 * Полученная страница должна быть совместима с юзерскриптом freedollchan.
 * @author miku-nyan
 *
 */
public class HtmlBuilder implements Closeable {
    
    /** Имена файлов (css и js), которые будут использоваться веб-страницей */
    public static final String[] ASSETS = new String[] {
        "futaba.css", "photon.css", "burichan.css", "gurochan.css", "dollscript.js", "wakaba3.js"
    };
    
    /** Папка в которой должны будут лежать необходимые для веб-страницы файлы ({@link #ASSETS}) */
    public static final String DATA_DIR = "data";
    
    private static final String DOLLSCRIPT = "dollscript.js";
    private static final String WAKABA3JS = "wakaba3.js";
    
    private static final String[] CSS = new String[] { "Futaba", "Photon", "Burichan", "Gurochan" };
    private static final String[] CSS_LINKS = new String[] { "futaba.css", "photon.css", "burichan.css", "gurochan.css" };
    
    private static final Pattern A_HREF_PATTERN = Pattern.compile("<a\\s+(?:[^>]*?\\s+)?href=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    
    private static final String CSS_FORMAT_1 = "<link rel=\"stylesheet\" type=\"text/css\" href=\"%s\" title=\"%s\" /> ";
    private static final String CSS_FORMAT_2 = "<link rel=\"alternate stylesheet\" type=\"text/css\" href=\"%s\" title=\"%s\" />";
    private static final String CSS_FORMAT_3 = "[<a href=\"javascript:set_stylesheet('%s')\">%s</a>] ";
    
    private static final String HTML_HEADER_1 =
            "<!DOCTYPE html>" +
                "<script type=\"text/javascript\" src=\"";
    private static final String HTML_HEADER_2 = 
                "\"></script>" +
                    "<html>" +
                        "<head>" +
                            "<meta http-equiv=\"Content-Type\" content=\"text/html;charset=utf-8\" />" +
                            "<title>";
    private static final String HTML_HEADER_3 =
                            "</title>" +
                            "<link rel=\"icon\" type=\"image/png\" href=\"";
    private static final String HTML_HEADER_4 =
                            "\" />" +
                            "<style type=\"text/css\"> " + 
                                    "body { margin: 0; padding: 8px; margin-bottom: auto; } " +
                                    "blockquote blockquote { margin-left: 0em } " +
                                    "form { margin-bottom: 0px } " +
                                    "form .trap { display:none } " +
                                    ".postarea { text-align: center } " +
                                    ".postarea table { margin: 0px auto; text-align: left } " +
                                    ".file { border: none; float: left; margin: 2px 20px } " +
                                    ".thumb { border: none; float: left; margin: 2px 20px } " +
                                    ".nothumb { float: left; background: #eee; border: 2px dashed #aaa; text-align: center; " +
                                            "margin: 2px 20px; padding: 1em 0.5em 1em 0.5em; } " +
                                    ".reply blockquote, blockquote :last-child { margin-bottom: 0em } " +
                                    ".reflink a { color: inherit; text-decoration: none } " +
                                    ".reply .filesize { margin-left: 20px } " +
                                    ".userdelete { float: right; text-align: center; white-space: nowrap } " +
                                    ".replypage .replylink { display: none } " +
                            "</style>";
    private static final String HTML_HEADER_5 =
                            "<script type=\"text/javascript\">var style_cookie=\"wakabastyle\";</script>" +
                            "<script type=\"text/javascript\" src=\"";
    private static final String HTML_HEADER_6 = 
                            "\"></script>" +
                        "</head>" +
                        "<body class=\"replypage\">" +
                            "<div class=\"adminbar\"> ";
    private static final String HTML_HEADER_7 =
                            "</div>" +
                            "<div class=\"logo\">";
    private static final String HTML_HEADER_8 =
                            "</div>" +
                            "<hr />" +
                            "<form id=\"delform\" action=\"/wakaba/wakaba.pl\" method=\"post\">";
    
    private static final String HTML_FOOTER =
                            "</form>" +
                            "<p class=\"footer\"> - " +
                                "<a href=\"http://miku-nyan.github.io/Overchan-Android/\">overchan-android</a> + " +
                                "<a href=\"http://wakaba.c3.cx/\">wakaba 3.0.9</a> + " +
                                "<a href=\"http://www.2chan.net/\">futaba</a> + " +
                                "<a href=\"http://www.1chan.net/futallaby/\">futallaby</a> -</p>" +
                        "</body>" +
                    "</html>";
    
    private final Writer buf;
    private final OutputStream _stream;
    private final boolean writeDeleted;
    private final RefsGetter refsGetter;
    private Resources res;
    private ChanModule chan;
    private UrlPageModel pageModel;
    private BoardModel boardModel;
    private DateFormat dateFormat;
    
    /**
     * Конструктор класса
     * @param out поток, в который будет записан HTML
     * @param refsGetter интерфейс для получения ссылок на вложения и картинки
     */
    public HtmlBuilder(OutputStream out, RefsGetter refsGetter) throws IOException {
        this(out, true, refsGetter);
    }
    
    /**
     * Конструктор класса
     * @param out поток, в который будет записан HTML
     * @param writeDeleted записывать удалённые посты
     * @param refsGetter интерфейс для получения ссылок на вложения и картинки
     */
    public HtmlBuilder(OutputStream out, boolean writeDeleted, RefsGetter refsGetter) throws IOException {
        _stream = out;
        buf = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
        
        this.writeDeleted = writeDeleted;
        this.refsGetter = refsGetter;
    }
    
    public void write(SerializablePage page) throws IOException {
        this.res = MainApplication.getInstance().resources;
        this.chan = MainApplication.getInstance().getChanModule(page.boardModel.chan);
        this.pageModel = page.pageModel;
        this.boardModel = page.boardModel;
        this.dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
        this.dateFormat.setTimeZone(TimeZone.getTimeZone(boardModel.timeZoneId));
        
        String logo = (page.boardModel.boardDescription != null ? page.boardModel.boardDescription : page.boardModel.boardName);
        try {
            String url = chan.buildUrl(pageModel);
            logo += " <a href=\"" + url + "\">(" + chan.getChanName() + ")</a>";
        } catch (Exception e) { /* ignore */ }
        
        buildHeader(buildTitle(page), logo);
        
        if (page.posts != null && page.posts.length != 0) {
            ThreadModel thread = new ThreadModel();
            thread.posts = page.posts;
            thread.threadNumber = page.posts[0].number;
            thread.postsCount = -1;
            thread.attachmentsCount = -1;
            buildThread(thread);
        }
        if (page.threads != null) {
            for (ThreadModel thread : page.threads) buildThread(thread);
        }
        buf.write(HTML_FOOTER);
        buf.flush();
    }
    
    public static String buildTitle(SerializablePage page) {
        String title;
        if (page.posts != null && page.posts.length != 0) {
            title = "/" + page.boardModel.boardName + " - ";
            title += page.posts[0].subject != null && page.posts[0].subject.length() != 0 ?
                    page.posts[0].subject : Html.fromHtml(page.posts[0].comment).toString().replace('\n', ' ');
            if (title.length() > 255) title = title.substring(0, 256);
        } else {
            title = page.boardModel.boardDescription != null ? page.boardModel.boardDescription : page.boardModel.boardName;
        }
        return title;
    }
    
    @Override
    public void close() throws IOException {
        try {
            buf.close();
        } catch (IOException e) {
            _stream.close();
            throw e;
        }
    }
    
    private void buildHeader(String pageTitle, String logoTitle) throws IOException {
        buf.write(HTML_HEADER_1);
        buf.write(DATA_DIR + "/" + DOLLSCRIPT);
        buf.write(HTML_HEADER_2);
        buf.write(pageTitle);
        buf.write(HTML_HEADER_3);
        buf.write(refsGetter.getFavicon());
        buf.write(HTML_HEADER_4);
        buf.write(String.format(Locale.US, CSS_FORMAT_1, (DATA_DIR + "/" + CSS_LINKS[0]), CSS[0]));
        for (int i=1; i<CSS.length; ++i) buf.write(String.format(Locale.US, CSS_FORMAT_2, (DATA_DIR + "/" + CSS_LINKS[i]), CSS[i]));
        buf.write(HTML_HEADER_5);
        buf.write(DATA_DIR + "/" + WAKABA3JS);
        buf.write(HTML_HEADER_6);
        for (int i=0; i<CSS.length; ++i) buf.write(String.format(Locale.US, CSS_FORMAT_3, CSS[i], CSS[i]));
        buf.write(HTML_HEADER_7);
        buf.write(logoTitle);
        buf.write(HTML_HEADER_8);
    }
    
    private void buildThread(ThreadModel thread) throws IOException {
        PostModel[] posts = thread.posts;
        if (posts == null || posts.length == 0) return;
        buildPost(posts[0], true);
        for (int i=1; i<posts.length; ++i) buildPost(posts[i], false);
        closeThread();
    }
    
    private void closeThread() throws IOException {
        buf.write("<br clear=\"left\" /><hr /> ");
    }
    
    private void buildPost(PostModel model, boolean isOpPost) throws IOException {
        if (!isOpPost && !writeDeleted && model.deleted) return;
        if (!isOpPost) {
            buf.write("<table><tbody><tr><td class=\"doubledash\">&gt;&gt;</td> <td class=\"reply\" id=\"reply");
            buf.write(model.number);
            buf.write("\"> ");
        }
        buf.write("<a name=\"");
        buf.write(model.number);
        buf.write("\"></a> <label><input type=\"checkbox\" name=\"delete\" value=\"");
        buf.write(model.number);
        buf.write("\" /> <span class=\"");
        buf.write(isOpPost ? "filetitle" : "replytitle");
        buf.write("\">");
        if (model.subject != null) buf.write(StringEscapeUtils.escapeHtml4(model.subject));
        buf.write("</span> <span class=\"");
        if (!isOpPost) buf.write("comment");
        buf.write("postername\">");
        if (model.color != Color.TRANSPARENT) {
            buf.write("<font color=\"");
            buf.write(String.format("#%06X", (0xFFFFFF & model.color)));
            buf.write("\">&#9632;</font>");
        }
        String name = StringEscapeUtils.escapeHtml4(model.name == null ? model.email : model.name);
        if (name != null) {
            if (model.email != null && model.email.length() != 0) {
                buf.write("<a href=\"");
                if (!model.email.contains(":")) buf.write("mailto:");
                buf.write(model.email);
                buf.write("\">");
                buf.write(name);
                buf.write("</a>");
            } else buf.write(name);
        }
        buf.write("</span> ");
        if (model.icons != null) {
            boolean firstIcon = true;
            for (BadgeIconModel icon : model.icons) {
                if (!firstIcon) buf.write("&nbsp;");
                firstIcon = false;
                buf.write("<img hspace=\"3\" src=\"");
                buf.write(refsGetter.getIcon(icon));
                buf.write("\" title=\"");
                buf.write((icon.description != null && icon.description.length() != 0) ?
                        icon.description :
                        (icon.source == null ? "" : icon.source.substring(icon.source.lastIndexOf('/') + 1)));
                buf.write("\" border=\"0\" />");
            }
            buf.write(' ');
        }
        if (model.trip != null && model.trip.length() != 0) {
            buf.write("<span class=\"postertrip\">");
            buf.write(StringEscapeUtils.escapeHtml4(model.trip));
            buf.write("</span> ");
        }
        if (model.op) buf.write("<span class=\"opmark\"># OP</span> ");
        buf.write(StringEscapeUtils.escapeHtml4(dateFormat.format(model.timestamp)));
        buf.write("</label> <span class=\"reflink\">  <a href=\"javascript:insert('&gt;&gt;");
        buf.write(model.number);
        buf.write("')\">No.");
        buf.write(model.number);
        buf.write("</a> </span>");
        if (model.deleted) buf.write("<span class=\"de-post-deleted\"></span>");
        buf.write("&nbsp; "); 
        if (model.attachments != null && model.attachments.length != 0) {
            buf.write("<br />");
            boolean single = model.attachments.length == 1;
            for (AttachmentModel attachment : model.attachments) buildAttachment(attachment, single);
            if (!single) buf.write("<br clear=\"left\" />");
        }
        buf.write("<blockquote>");
        buf.write(fixComment(model.comment));
        buf.write("</blockquote>");
        if (!isOpPost) {
            buf.write("</td></tr></tbody></table>");
        }
    }
    
    private String fixComment(String comment) {
        comment = comment.replaceAll("(?i)<aibquote>", "<span class=\"unkfunc\">").replaceAll("(?i)</aibquote>", "</span>").
                replaceAll("(?i)<aibspoiler>", "<span class=\"spoiler\">").replaceAll("(?i)</aibspoiler>", "</span>");
        Matcher m = A_HREF_PATTERN.matcher(comment);
        if (!m.find()) return comment;
        StringBuffer sb = new StringBuffer();
        do {
            String group = m.group();
            String found = m.group(1);
            int oldPos = m.start(1) - m.start();
            int oldLen = found.length();
            
            String url;
            if (found.startsWith("#")) {
                try {
                    String thisThreadUrl = chan.buildUrl(pageModel);
                    int i = thisThreadUrl.indexOf('#');
                    if (i != -1) thisThreadUrl = thisThreadUrl.substring(0, i);
                    String postNumber = chan.parseUrl(thisThreadUrl + found).postNumber;
                    url = "#" + postNumber != null ? postNumber : pageModel.threadNumber;
                } catch (Exception e) {
                    url = found;
                }
            } else {
                url = chan.fixRelativeUrl(found);
                try {
                    UrlPageModel linkModel = chan.parseUrl(url);
                    if (ChanModels.hashUrlPageModel(linkModel).equals(ChanModels.hashUrlPageModel(pageModel))) {
                        url = "#" + linkModel.postNumber;
                    }
                } catch (Exception e) { /* ignore */ }
            }
            
            m.appendReplacement(sb, url.equals(found) ? group : (group.substring(0, oldPos) + url + group.substring(oldPos + oldLen)));
        } while (m.find());
        m.appendTail(sb);
        return sb.toString();
    }
    
    private void buildAttachment(AttachmentModel model, boolean isSingle) throws IOException {
        int tnWidth, tnHeight;
        if (model.width > 0 && model.height > 0) {
            float scale = 200f / Math.max(model.width, model.height);
            if (scale > 1) scale = 1;
            tnWidth = (int)(scale * model.width);
            tnHeight = (int)(scale * model.height);
        } else {
            tnWidth = -1;
            tnHeight = -1;
        }
        
        String thumbRef = refsGetter.getThumbnail(model);
        String origRef = refsGetter.getOriginal(model);
        
        String filenameDesc;
        if (model.type != AttachmentModel.TYPE_OTHER_NOTFILE) {
            filenameDesc = model.path != null ? model.path : model.thumbnail;
            filenameDesc = filenameDesc.substring(filenameDesc.lastIndexOf('/') + 1);
            try {
                filenameDesc = URLDecoder.decode(filenameDesc, "UTF-8");
            } catch (Exception e) {/*ignore*/}
        } else {
            filenameDesc = res.getString(R.string.html_external);
        }
        
        if (!isSingle) buf.write("<div class=\"file\">");
        buf.write("<span class=\"filesize\">");
        if (model.type != AttachmentModel.TYPE_OTHER_NOTFILE) buf.write(res.getString(R.string.html_file));
        buf.write(" <a target=\"_blank\" href=\"");
        buf.write(origRef);
        buf.write("\">");
        buf.write(filenameDesc);
        buf.write("</a>");
        if (model.type != AttachmentModel.TYPE_OTHER_NOTFILE) {
            buf.write(isSingle ? " - " : "<br />");
            buf.write("(<em>");
            boolean first = true;
            if (model.size != -1) {
                first = false;
                buf.write(String.format(Locale.US, "%d KB", model.size));
            }
            if (model.width > 0 && model.height > 0) {
                if (!first) buf.write(", "); else first = false;
                buf.write(String.format(Locale.US, "%dx%d", model.width, model.height));
            }
            if (model.originalName != null && model.originalName.length() > 0) {
                if (!first) buf.write(", "); else first = false;
                buf.write(model.originalName);
            }
            buf.write("</em>)");
        }
        buf.write("</span> ");
        if (thumbRef != null) {
            if (isSingle) {
                buf.write(" <span class=\"thumbnailmsg\">");
                buf.write(res.getString(R.string.html_thumbnailmsg));
                buf.write("</span>");
            }
            buf.write("<br /><a target=\"_blank\" href=\"");
            buf.write(origRef);
            buf.write("\"> <img src=\"");
            buf.write(thumbRef);
            
            if (tnWidth == -1) {
                buf.write("\" onload=\"with (this) {if (offsetHeight > offsetWidth) style.height = '200px'; else style.width = '200px'}\"");
            } else {
                buf.write(String.format(Locale.US, "\" width=\"%d\" height=\"%d\"", tnWidth, tnHeight));
            }
            buf.write(String.format(Locale.US, " alt=\"%s\" ", filenameDesc));
            
            if (isSingle) buf.write("class=\"thumb\" ");
            buf.write("/></a>");
        }
        if (!isSingle) buf.write("</div>");
    }
    
    public static interface RefsGetter {
        /** Получить местонахождение значка favicon (локальный файл) */
        String getFavicon();
        /** Получить местонахождение оригинала вложения (может быть удалённым как локальным файлом, так и удалённым URL) */
        String getOriginal(AttachmentModel attachment);
        /** Получить метонахождение картинки превью вложения (локльный файл или null) */
        String getThumbnail(AttachmentModel attachment);
        /** Получить местонахождение картинки со значком (локальный файл, не может быть null) */
        String getIcon(BadgeIconModel icon);
    }
    
}
