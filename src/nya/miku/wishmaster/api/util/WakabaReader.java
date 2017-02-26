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

package nya.miku.wishmaster.api.util;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;

import android.graphics.Color;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.common.Logger;

/**
 * Поточный парсер HTML-страниц основанных на вакабе имиджборд (или с аналогичной структурой).<br>
 * Для тонкой настройки под конкретную борды, можно наследоваться от этого класса и переопределить необходимые protected-методы.<br>
 * Примечание. Методы данного класса не потокобезопасны.
 * @author miku-nyan
 *
 */
public class WakabaReader implements Closeable {
    private static final String TAG = "WakabaReader";
    
    private static final class DateFormatHolder {
        private static final DateFormat DEFAULT_WAKABA_DATEFORMAT;
        static {
            DEFAULT_WAKABA_DATEFORMAT = new SimpleDateFormat("yy/MM/dd(EEE)HH:mm", Locale.US);
            DEFAULT_WAKABA_DATEFORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        }
    }
    
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("<a[^>]*href=\"([^\"]*)\"[^>]*>(.*)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ATTACHMENT_SIZE_PATTERN =
            Pattern.compile("([,\\.\\d]+) ?([кkмm])?i?[бb]", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ATTACHMENT_PX_SIZE_PATTERN = Pattern.compile("(\\d+)[x×х](\\d+)"); // \u0078 \u00D7 \u0445
    private static final Pattern ATTACHMENT_ORIGINAL_NAME_PATTERN = Pattern.compile("\\s*,?([^<\\)]*)");
    
    private static final char[] DATA_START = "<form id=\"delform\"".toCharArray();
    
    private static final char[] BLOCKQUOTE_OPEN = "<blockquote".toCharArray();
    private static final char[] BLOCKQUOTE_CLOSE = "</blockquote>".toCharArray();
    
    private static final int FILTER_PAGE_END = 0;
    private static final int FILTER_THREAD_END = 1;
    private static final int FILTER_ATTACHMENT = 2;
    private static final int FILTER_ATTACHMENT_THUMBNAIL = 3;
    private static final int FILTER_POSTNUMBER = 4;
    private static final int FILTER_SUBJECT_OP = 5;
    private static final int FILTER_SUBJECT = 6;
    private static final int FILTER_POSTERNAME_OP = 7;
    private static final int FILTER_POSTERNAME = 8;
    private static final int FILTER_TRIPCODE = 9;
    private static final int FILTER_ENDDATE = 10;
    private static final int FILTER_OMITTEDPOSTS = 11;
    private static final int FILTER_START_COMMENT = 12;
    
    //ни для каких двух фильтров (открывающих) префикс одного не должен совпадать с суффиксом другого
    /** только эти фильтры обрабатываются по умолчанию */
    public static final char[][] FILTERS_OPEN = {
        "</form>".toCharArray(),
        "<hr".toCharArray(),
        "<span class=\"filesize\">".toCharArray(),
        "<img".toCharArray(),
        "<a name=\"".toCharArray(),
        "<span class=\"filetitle\">".toCharArray(),
        "<span class=\"replytitle\">".toCharArray(),
        "<span class=\"postername\">".toCharArray(),
        "<span class=\"commentpostername\">".toCharArray(),
        "<span class=\"postertrip\">".toCharArray(),
        "</label>".toCharArray(),
        "<span class=\"omittedposts\">".toCharArray(),
        "<blockquote".toCharArray()
    };
    
    private static final char[][] FILTERS_CLOSE = {
        null,
        null,
        "</span>".toCharArray(),
        ">".toCharArray(),
        "\"".toCharArray(),
        "</span>".toCharArray(),
        "</span>".toCharArray(),
        "</span>".toCharArray(),
        "</span>".toCharArray(),
        "</span>".toCharArray(),
        null,
        "</span>".toCharArray(),
        ">".toCharArray()
    };
    
    protected final Reader _in;
    protected final DateFormat dateFormat;
    protected final boolean canCloudflare;
    
    private StringBuilder readBuffer = new StringBuilder();
    private List<ThreadModel> threads;
    /** Тред, который читается в данный момент.<br>
     *  При обработке данных не записывайте ничего в поле {@link ThreadModel#posts}: массив формируется (будет перезаписан) в конце чтения треда.
     *  Вместо этого, данные о посте следует записывать в {@link #currentPost} и {@link #currentAttachments}. */
    protected ThreadModel currentThread;
    private List<PostModel> postsBuf;
    /** Пост, который читается в данный момент.<br>
     *  При обработке данных не записывайте ничего в поле {@link PostModel#attachments}: массив будет перезаписан в конце чтения поста.
     *  Вместо этого, вложения следует записывать в список {@link #currentAttachments}. */
    protected PostModel currentPost;
    private boolean inDate;
    private StringBuilder dateBuffer = new StringBuilder();
    private StringBuilder commentBuffer = new StringBuilder();
    private StringBuilder omittedDigitsBuffer = new StringBuilder();
    /** Список вложений для поста, который читается в данный момент.<br>
     *  В конце чтения поста будет записан как массив в {@link PostModel#attachments} */
    protected List<AttachmentModel> currentAttachments;
    
    public WakabaReader(Reader reader, DateFormat dateFormat, boolean canCloudflare) {
        _in = reader;
        this.canCloudflare = canCloudflare;
        this.dateFormat = dateFormat != null ? dateFormat : DateFormatHolder.DEFAULT_WAKABA_DATEFORMAT;
    }
    
    public WakabaReader(Reader reader, DateFormat dateFormat) {
        this(reader, dateFormat, false);
    }
    
    public WakabaReader(Reader reader) {
        this(reader, null);
    }
    
    public WakabaReader(InputStream in, DateFormat dateFormat, boolean canCloudflare) {
        this(new BufferedReader(new InputStreamReader(in)), dateFormat, canCloudflare);
    }
    
    public WakabaReader(InputStream in, DateFormat dateFormat) {
        this(in, dateFormat, false);
    }
    
    public WakabaReader(InputStream in) {
        this(in, null);
    }
    
    private void initThreadModel() {
        currentThread = new ThreadModel();
        currentThread.postsCount = 0;
        currentThread.attachmentsCount = 0;
        postsBuf = new ArrayList<PostModel>();
    }
    
    private void initPostModel() {
        currentPost = new PostModel();
        currentAttachments = new ArrayList<AttachmentModel>();
        inDate = false;
        dateBuffer.setLength(0);
    }
    
    /**
     * Завершить чтение текущего треда (последующие прочитанные сообщения будут добавляться в новый тред)
     */
    protected final void finalizeThread() {
        if (postsBuf.size() > 0) {
            currentThread.posts = postsBuf.toArray(new PostModel[postsBuf.size()]);
            currentThread.threadNumber = currentThread.posts[0].number;
            for (PostModel post : currentThread.posts) post.parentThread = currentThread.threadNumber;
            threads.add(currentThread);
            initThreadModel();
        }
    }
    
    /**
     * Завершить чтение текущего поста
     */
    protected final void finalizePost() {
        if (currentPost.number != null && currentPost.number.length() > 0) {
            ++currentThread.postsCount;
            currentPost.attachments = currentAttachments.toArray(new AttachmentModel[currentAttachments.size()]);
            if (currentPost.name == null) currentPost.name = "";
            if (currentPost.subject == null) currentPost.subject = "";
            if (currentPost.comment == null) currentPost.comment = "";
            if (currentPost.email == null) currentPost.email = "";
            if (currentPost.trip == null) currentPost.trip = "";
            if (canCloudflare) {
                currentPost.comment = CryptoUtils.fixCloudflareEmails(currentPost.comment);
                currentPost.subject = CryptoUtils.fixCloudflareEmails(currentPost.subject);
                if (currentPost.email.startsWith("/cdn-cgi/l/email-protection#"))
                    currentPost.email = CryptoUtils.decodeCloudflareEmail(currentPost.email.substring(28));
            }
            postprocessPost(currentPost);
            postsBuf.add(currentPost);
        }
        initPostModel();
    }
    
    /**
     * Метод для переопределения, вызывается, когда чтение поста завершено (и вся обработка данным классом).<br>
     * Может использоваться, чтобы произвести какую-либо дополнительную постобработку, в зависимости от имиджборды.<br>
     * Эта реализация не делает ничего (пустой метод).
     */
    protected void postprocessPost(PostModel post) {}
    
    public ThreadModel[] readWakabaPage() throws IOException {
        threads = new ArrayList<ThreadModel>();
        initThreadModel();
        initPostModel();
        
        skipUntilSequence(DATA_START);
        readData();
        
        return threads.toArray(new ThreadModel[threads.size()]);
    }
    
    private void readData() throws IOException {
        int filtersCount = FILTERS_OPEN.length;
        int[] pos = new int[filtersCount];
        int[] len = new int[filtersCount];
        for (int i=0; i<filtersCount; ++i) len[i] = FILTERS_OPEN[i].length;
        
        int curChar;
        while ((curChar = _in.read()) != -1) {
            if (inDate) dateBuffer.append((char) curChar);
            for (int i=0; i<filtersCount; ++i) {
                if (curChar == FILTERS_OPEN[i][pos[i]]) {
                    ++pos[i];
                    if (pos[i] == len[i]) {
                        if (i == FILTER_PAGE_END) {
                            finalizeThread();
                            return;
                        }
                        handleFilter(i);
                        pos[i] = 0;
                    }
                } else {
                    if (pos[i] != 0) pos[i] = curChar == FILTERS_OPEN[i][0] ? 1 : 0;
                }
            }
            customFilters(curChar);
        }
        finalizeThread();
    }
    
    /**
     * Если требуется обрабатывать дополнительные фильтры (не заданные в этом классе), можно переопределить этот метод.
     * Он вызывается каждый раз, когда читается один символ, пока парсер ищет очередной фильтр.<br>
     * Когда нужная последовательность прочитана, для дальнейшего чтения можно использовать Reader напрямую ({@link #_in}),
     * или методы {@link #readUntilSequence(char[])} и {@link #skipUntilSequence(char[])}.<br>
     * Обрабаботанные данные можно сохранять в {@link #currentPost}, {@link #currentAttachments} и {@link #currentThread}.<br>
     * См. также пример {@link nya.miku.wishmaster.chans.cirno.Chan410IntReader}, который читает также значок флага страны
     * (в том виде, как он реализован на борде 410chan.org/int)<br>
     * Также, при реализации своих фильтров необходимо учитывать, что из всего множества фильтров ({@link #FILTERS_OPEN} этого класса и создаваемых)
     * ни для каких двух префикс одного не должен совпадать с постфиксом другого.<br> 
     * Эта реализация не делает ничего (пустой метод).
     */
    protected void customFilters(int ch) throws IOException {}
    
    private void handleFilter(int filterIndex) throws IOException {
        if (inDate && filterIndex != FILTER_ENDDATE) dateBuffer.setLength(0);
        switch (filterIndex) {
            case FILTER_THREAD_END:
                finalizeThread();
                break;
            case FILTER_ATTACHMENT:
                parseAttachment(readUntilSequence(FILTERS_CLOSE[filterIndex]));
                break;
            case FILTER_ATTACHMENT_THUMBNAIL:
                parseThumbnail(readUntilSequence(FILTERS_CLOSE[filterIndex]));
                break;
            case FILTER_POSTNUMBER:
                currentPost.number = readUntilSequence(FILTERS_CLOSE[filterIndex]).trim();
                break;
            case FILTER_SUBJECT_OP:
            case FILTER_SUBJECT:
                currentPost.subject = StringEscapeUtils.unescapeHtml4(readUntilSequence(FILTERS_CLOSE[filterIndex])).trim();
                break;
            case FILTER_POSTERNAME_OP:
            case FILTER_POSTERNAME:
                parseNameEmail(readUntilSequence(FILTERS_CLOSE[filterIndex]));
                inDate = true;
                break;
            case FILTER_TRIPCODE:
                currentPost.trip = StringEscapeUtils.unescapeHtml4(RegexUtils.removeHtmlTags(readUntilSequence(FILTERS_CLOSE[filterIndex]))).trim();
                inDate = true;
                break;
            case FILTER_ENDDATE:
                if (dateBuffer.length() > FILTERS_OPEN[FILTER_ENDDATE].length) {
                    String date = dateBuffer.substring(0, dateBuffer.length() - FILTERS_OPEN[FILTER_ENDDATE].length).trim();
                    parseDate(date);
                }
                inDate = false;
                dateBuffer.setLength(0);
                break;
            case FILTER_OMITTEDPOSTS:
                parseOmittedString(readUntilSequence(FILTERS_CLOSE[filterIndex]));
                break;
            case FILTER_START_COMMENT:
                skipUntilSequence(FILTERS_CLOSE[filterIndex]);
                currentPost.comment = readPostComment();
                finalizePost();
                break;
        }
    }
    
    /**
     * Метод для чтения комментария поста, вызывается после того, как был прочитан тэг &lt;blockquote&gt;.<br>
     * В этой реализации просто читаются и сохраняются все символы до того, как встретится соответствующий закрывающий &lt;/blockquote&gt;
     * (с учётом того, что могут быть вложенные тэги blockquote).
     * @return комментарий поста в виде HTML (в соответствии с описанием {@link PostModel#comment})
     */
    protected String readPostComment() throws IOException {
        commentBuffer.setLength(0);
        int len1 = BLOCKQUOTE_OPEN.length;
        int len2 = BLOCKQUOTE_CLOSE.length;
        int pos1 = 0;
        int pos2 = 0;
        int tagCounter = 1;
        int curChar;
        while ((curChar = _in.read()) != -1) {
            commentBuffer.append((char) curChar);
            
            if (curChar == BLOCKQUOTE_OPEN[pos1]) {
                ++pos1;
                if (pos1 == len1) {
                    ++tagCounter;
                    pos1 = 0;
                }
            } else {
                if (pos1 != 0) pos1 = curChar == BLOCKQUOTE_OPEN[0] ? 1 : 0;
            }
            
            if (curChar == BLOCKQUOTE_CLOSE[pos2]) {
                ++pos2;
                if (pos2 == len2) {
                    --tagCounter;
                    if (tagCounter == 0) break;
                    pos2 = 0;
                }
            } else {
                if (pos2 != 0) pos2 = curChar == BLOCKQUOTE_CLOSE[0] ? 1 : 0;
            }
        }
        int buflen = commentBuffer.length();
        if (buflen > len2) {
            commentBuffer.setLength(buflen - len2);
            return commentBuffer.toString();
        } else {
            return "";
        }
    }
    
    /**
     * Метод для парсинга строки "omitted posts" (сколько постов и вложений в треде пропущено при просмотре списка тредов),
     * прибавляет значения к полям {@link #currentThread}: {@link ThreadModel#postsCount} и {@link ThreadModel#attachmentsCount}.<br>
     * Эта реализация ищет первое и последнее десятичное целое число: первое рассматривается как число постов, последнее как число вложений.
     * Если найдено только одно число, оно рассматривается как число постов, число вложений считается равным нулю.
     * @param omitted строка вида "20 posts and 4 images omitted. Click Reply to view.", в зависимости от имиджборды
     */
    protected void parseOmittedString(String omitted) {
        int postsOmitted = -1;
        int filesOmitted = -1;
        try {
            int len = omitted.length();
            for (int i=0; i<=len; ++i) {
                char ch = i == len ? ' ' : omitted.charAt(i);
                if (ch >= '0' && ch <= '9') {
                    omittedDigitsBuffer.append(ch);
                } else {
                    if (omittedDigitsBuffer.length() > 0) {
                        int parsedValue = Integer.parseInt(omittedDigitsBuffer.toString());
                        omittedDigitsBuffer.setLength(0);
                        if (postsOmitted == -1) postsOmitted = parsedValue;
                        else filesOmitted = parsedValue;
                    }
                }
            }
        } catch (NumberFormatException e) {}
        if (postsOmitted > 0) currentThread.postsCount += postsOmitted;
        if (filesOmitted > 0) currentThread.attachmentsCount += filesOmitted;
    }
    
    /**
     * Метод для парсинга информации о вложении, принимает HTML-строку, содержимое тэга &lt;span class="filesize"&gt;.
     * Если получен корректрый результат, вложение нужно добавить к списку {@link #currentAttachments}
     * и увеличивается значение {link {@link ThreadModel#attachmentsCount}) объекта {@link #currentThread}.<br>
     * Эта реализация обрабатывает вложение без ссылки на миниатюру
     * (основную информацию, ссылка на оригинал, размер в байтах, размеры в пикселях, оригинальное имя, если есть).
     */
    protected void parseAttachment(String html) {
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
        
        Matcher byteSizeMatcher = ATTACHMENT_SIZE_PATTERN.matcher(html);
        while (byteSizeMatcher.find()) {
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
                
                char nextChar = ' ';
                int index = byteSizeMatcher.end();
                while (index < html.length() && nextChar <= ' ') nextChar = html.charAt(index++);
                if (nextChar == ',') break;
            } catch (NumberFormatException e) {}
        }
        
        Matcher pxSizeMatcher = ATTACHMENT_PX_SIZE_PATTERN.matcher(html);
        int indexEndPxSize = -1;
        while (pxSizeMatcher.find()) {
            try {
                int width = Integer.parseInt(pxSizeMatcher.group(1));
                int height = Integer.parseInt(pxSizeMatcher.group(2));
                attachment.width = width;
                attachment.height = height;
                indexEndPxSize = pxSizeMatcher.end();
                
                char nextChar = ' ';
                int index = pxSizeMatcher.end();
                while (index < html.length() && nextChar <= ' ') nextChar = html.charAt(index++);
                if (nextChar == ',') break;
            } catch (NumberFormatException e) {}
        }
        
        if (indexEndPxSize != -1) {
            Matcher originalNameMatcher = ATTACHMENT_ORIGINAL_NAME_PATTERN.matcher(html);
            if (originalNameMatcher.find(indexEndPxSize)) {
                String originalName = originalNameMatcher.group(1).trim();
                if (originalName != null && originalName.length() > 0) {
                    attachment.originalName = StringEscapeUtils.unescapeHtml4(originalName);
                }
            }
        }
        
        ++currentThread.attachmentsCount;
        currentAttachments.add(attachment);
    }
    
    /**
     * Метод для парсинга ссылки на миниатюру вложения (принимает аттрибуты тэга &lt;img (/)&gt;, вызывается, когда встречается тэг img).
     * В стандартной вакабе должен вызываться после обработки основной информации о вложении (уже распарсен методом {@link #parseAttachment(String)}).
     * Эта реализация сохраняет ссылку (содержимое) аттрибута src в последний объект объект списка {@link #currentAttachments}, если список не пуст
     * и если ссылка в этом объекте ещё не сохранена (т.е. сохраняется только первая встретившаяся после тэга &lt;span class="filesize"&gt;). 
     */
    protected void parseThumbnail(String imgTag) {
        int currentAttachmentsCount = currentAttachments.size();
        if (currentAttachmentsCount > 0 && currentAttachments.get(currentAttachmentsCount - 1).thumbnail == null) {
            int start, end;
            if ((start = imgTag.indexOf("src=\"")) != -1 && (end = imgTag.indexOf('\"', start + 5)) != -1) {
                currentAttachments.get(currentAttachmentsCount - 1).thumbnail = imgTag.substring(start + 5, end);
            }
        }
    }
    
    /**
     * Метод для парсинга имени и адреса e-mail постера, принимает (HTML-строку)
     * содержимое тэга &lt;span class="postername"&gt; или &lt;span class="commentpostername"&gt;.<br>
     * Сохраняется в {@link #currentPost}, поля {@link PostModel#name}, {@link PostModel#email} и (при необходимости) {@link PostModel#sage}.<br>
     * Эта реализация парсит ссылку вида &lt;a href="mailto:email"&gt;&lt;/a&gt;.<br>
     * Если email содержит sage, значение {@link PostModel#sage} устанавливается как true.
     */
    protected void parseNameEmail(String raw) {
        Matcher emailMatcher = EMAIL_PATTERN.matcher(raw);
        if (emailMatcher.find()) {
            currentPost.email = emailMatcher.group(1).trim();
            if (currentPost.email.startsWith("mailto:")) currentPost.email = currentPost.email.substring(7);
            if (currentPost.email.toLowerCase(Locale.US).contains("sage")) currentPost.sage = true;
            currentPost.name = StringEscapeUtils.unescapeHtml4(emailMatcher.group(2)).trim();
        } else {
            currentPost.name = StringEscapeUtils.unescapeHtml4(raw).trim();
        }
        if (currentPost.name.contains("<span class=\"adminname\">")) currentPost.color = Color.RED;
        if (currentPost.name.startsWith("<")) currentPost.name = RegexUtils.removeHtmlTags(currentPost.name);
    }
    
    /**
     * Метод для парсинга даты, принимает строку - то (в стандартной вакабе), что идёт после тэгов
     * &lt;span class="postername"&gt;, &lt;span class="commentpostername"&gt; или &lt;span class="postertrip"&gt;
     * и до закрывающегося тэга &lt;/label&gt;.<br>
     * Сохраняется в {@link #currentPost}, поле {@link PostModel#timestamp}.<br>
     * Эта реализация пытается распарсить дату имеющимся объектом {@link DateFormat}
     * (переданным конструктору класса или по умолчанию, если был передан null), в случае исключения выводится сообщение в лог.
     * @param date строка с датой
     */
    protected void parseDate(String date) {
        date = RegexUtils.removeHtmlTags(date).trim();
        if (date.length() > 0) {
            try {
                currentPost.timestamp = dateFormat.parse(date).getTime();
            } catch (Exception e) {
                Logger.e(TAG, "cannot parse date; make sure you choose the right DateFormat for this chan", e);
            }
        }
    }
    
    /**
     * Метод пропускает (читает все символы без сохранения), пока не встретится заданная последовательность символов.
     * @param sequence массив символов
     */
    protected void skipUntilSequence(char[] sequence) throws IOException {
        int len = sequence.length;
        if (len == 0) return;
        int pos = 0;
        int curChar;
        while ((curChar = _in.read()) != -1) {
            if (curChar == sequence[pos]) {
                ++pos;
                if (pos == len) break;
            } else {
                if (pos != 0) pos = curChar == sequence[0] ? 1 : 0;
            }
        }
    }
    
    /**
     * Метод читает и сохраняет все символы, пока не встретится заданная последовательность символов.
     * @param sequence массив символов
     * @return строка с прочитанными (сохранёнными) символами
     */
    protected String readUntilSequence(char[] sequence) throws IOException {
        int len = sequence.length;
        if (len == 0) return "";
        readBuffer.setLength(0);
        int pos = 0;
        int curChar;
        while ((curChar = _in.read()) != -1) {
            readBuffer.append((char) curChar);
            if (curChar == sequence[pos]) {
                ++pos;
                if (pos == len) break;
            } else {
                if (pos != 0) pos = curChar == sequence[0] ? 1 : 0;
            }
        }
        int buflen = readBuffer.length();
        if (buflen >= len) {
            readBuffer.setLength(buflen - len);
            return readBuffer.toString();
        } else {
            return "";
        }
    }
    
    @Override
    public void close() throws IOException {
        _in.close();
    }
}
