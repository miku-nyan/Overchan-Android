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

package nya.miku.wishmaster.ui.presentation;

import java.text.DateFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.ui.presentation.ClickableURLSpan.URLSpanClickListener;
import nya.miku.wishmaster.ui.presentation.FlowTextHelper.FloatingModel;
import nya.miku.wishmaster.ui.presentation.HtmlParser.ImageGetter;
import nya.miku.wishmaster.ui.theme.ThemeUtils;
import nya.miku.wishmaster.ui.theme.ThemeUtils.ThemeColors;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Parcel;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

/**
 * Элемент из PresentationModel (подготовленный к показу пост или ОП-пост треда).<br>
 * Конструктор вызывать асинхронно (вне основного потока UI)
 * @author miku-nyan
 *
 */
public class PresentationItemModel {
    static final Pattern REPLY_LINK_FULL_PATTERN =
            Pattern.compile("<a.+?>(?:>>|&gt;&gt;)(\\w+)(?:.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    
    public static final String ALL_REFERENCES_URI = "references://all?from=";
    public static final String POST_REFERER = "refpost://";
    
    private Resources resources = MainApplication.getInstance().resources;
    private URLSpanClickListener spanClickListener;
    private ImageGetter imageGetter;
    private ThemeColors themeColors;
    private boolean openSpoilers;
    private FloatingModel[] floatingModels;
    private String chanName;
    private ChanModule chanModule;
    private String boardName;
    private String threadNumber;
    
    /** Исходная модель поста */
    public PostModel sourceModel;
    /** Хэш исходной модели поста */
    public int sourceModelHash;
    /** Удален ли пост */
    public boolean isDeleted;
    /** Дата и время поста (строковое представление) */
    public String dateString;
    /** Строка с количеством постов и вложений (для заголовка треда).
     *  Необходимо предварительно построить ({@link #buildPostsCountString(int, int)}) */
    public String postsCountString;
    /** Строка с информацией о состоянии треда: прикреплен, закрыт, зациклен (для заголовка треда).
     *  Необходимо предварительно построить ({@link #buildThreadConditionString(boolean, boolean, boolean)}) */
    public String threadConditionString;
    /** spannable строка с комментарием поста */
    public Spanned spannedComment;
    /** spanned строка со ссылками на данный пост.
     *  Необходимо предварительно добавить ссылки ({@link #addReferenceFrom(String)}) и построить ({@link #buildReferencesString()}).
     *  Построение возможно, только если элемент - пост со страницы треда. */
    public Spanned referencesString;
    /** spanned строка со ссылкой на список ссылок на данный пост (т.е. количество ответов).
     *  Необходимо предварительно добавить ссылки ({@link #addReferenceFrom(String)}) и построить ({@link #buildReferencesString()}).
     *  Построение возможно, только если элемент - пост со страницы треда. */
    public Spanned referencesQuantityString;
    /** spanned строка с заголовком поста.
     *  Если нужна, необходимо предварительно построить (метод {@link #buildSpannedHeader(int, int, String, String, boolean)}) */
    public Spanned spannedHeader;
    
    /** список ссылок из этого поста */
    public Set<String> referencesTo = new HashSet<String>();
    private List<String> referencesFrom = new LinkedList<String>();
    
    /** массив хэш-сумм вложений */
    public String[] attachmentHashes;
    /** общее описание всех значков бейджа (название страны, полит.предпочтения и т.д.). Может быть null */
    public String badgeTitle;
    /** массив хэшей картинок со значками бейджа (флаг страны, полит.предпочтения и т.д.). Может быть null */
    public String[] badgeHashes;
    /** true, если активно обтекание картинки-превью текстом комментария */
    public boolean floating;
    /** true, если элемент является скрытым (скрытый пост/скрытый тред).
     *  Поле заполняется в PresentationModel, не заполняется автоматически в конструкторе этого класса (PresentationItemModel).
     *  По умолчанию false. */
    public boolean hidden = false;
    /** Причина автоскрытия (строка с регулярным выражением), если элемент был скрыт по правилу автоскрытия (только если {@link #hidden} равно true).
     *  Поле заполняется в PresentationModel, не заполняется автоматически в конструкторе этого класса (PresentationItemModel).
     *  По умолчанию null. */
    public String autohideReason = null;
    
    /**
     * Конструктор
     * @param source исходная модель {@link PostModel}
     * @param chanName название чана (внутреннее название модуля)
     * @param boardName название доски (короткое)
     * @param threadNumber номер треда, если создаваемый элемент - пост со страницы треда. В противном случае - null
     * @param dateFormat объект {@link java.text.DateFormat}, которым будет отформатирована дата/время поста
     * @param spanClickListener обработчик нажатий на ссылки
     * @param imageGetter загрузчик картинок, содержищихся в теле поста (например, смайлики на 1 апреля)
     * @param themeColors объект {@link ThemeUtils.ThemeColors}, содержащий цвета текущей темы
     * @param openSpoilers отображать спойлеры открытыми
     * @param floatingModels массив из двух моделей обтекания картинки текстом
     * Первый элемент - обычная превьюшка, второй - со строкой с дополнительной информацией о вложении (gif, видео, аудио).
     * Допустимо значение null, если обтекание не нужно вообще.
     */
    public PresentationItemModel(PostModel source, String chanName, String boardName, String threadNumber, DateFormat dateFormat,
            URLSpanClickListener spanClickListener, ImageGetter imageGetter, ThemeColors themeColors, boolean openSpoilers,
            FloatingModel[] floatingModels, String[] subscriptions) {
        this.sourceModel = source;
        this.sourceModelHash = ChanModels.hashPostModel(source);
        this.isDeleted = source.deleted;
        this.chanName = chanName;
        this.chanModule = MainApplication.getInstance().getChanModule(chanName);
        this.boardName = boardName;
        this.threadNumber = threadNumber;
        this.spanClickListener = spanClickListener;
        this.imageGetter = imageGetter;
        this.themeColors = themeColors;
        this.openSpoilers = openSpoilers;
        this.floatingModels = floatingModels;
        
        this.spannedComment = HtmlParser.createSpanned(source.subject, source.comment, spanClickListener, imageGetter, themeColors, openSpoilers,
                POST_REFERER + source.number);
        this.dateString = source.timestamp != 0 ? dateFormat.format(source.timestamp) : "";
        parseReferences();
        if (subscriptions != null) findSubscriptions(subscriptions);
        parseBadge();
        computeThumbnailsHash();
        if (floatingModels != null) {
            tryFlow(floatingModels);
        }
    }
    
    /**
     * Класс-контейнер для дополнительной spanned-строки с комментарием и флагом обтекания этой строки
     */
    public class SpannedCommentContainer {
        public final Spanned spanned;
        public final boolean floating;
        public SpannedCommentContainer(Spanned spanned, boolean floating) {
            this.spanned = spanned;
            this.floating = floating;
        }
    }
    
    /**
     * Установить новые модели обтекания картинки текстом (и перестроить текст комментария в случае необходимости)
     * @param floatingModels массив из двух моделей обтекания картинки текстом
     */
    public void changeFloatingModels(FloatingModel[] floatingModels) {
        this.floatingModels = floatingModels;
        if (floating) {
            this.spannedComment = HtmlParser.createSpanned(sourceModel.subject, sourceModel.comment, spanClickListener, imageGetter,
                    themeColors, openSpoilers, POST_REFERER + sourceModel.number);
            tryFlow(floatingModels);
        }
    }
    
    /**
     * Получить spanned-строку с комментарием поста для вывода на TextView с заданной шириной (для всплывающих диалогов) с корректным обтеканием
     * @param textFullWidth ширина текста TextView
     * @return объект класса {@link SpannedCommentContainer}
     */
    public SpannedCommentContainer getSpannedCommentForCustomWidth(int textFullWidth) {
        return getSpannedCommentForCustomWidth(textFullWidth, floatingModels);
    }
    
    /**
     * Получить spanned-строку с комментарием поста для вывода на TextView с заданной шириной (для всплывающих диалогов) с корректным обтеканием
     * @param textFullWidth ширина текста TextView
     * @param floatingModels массив из двух моделей обтекания картинки текстом
     * @return объект класса {@link SpannedCommentContainer}
     */
    public SpannedCommentContainer getSpannedCommentForCustomWidth(int textFullWidth, FloatingModel[] floatingModels) {
        if (sourceModel.attachments == null || sourceModel.attachments.length != 1 || floatingModels == null) {
            return new SpannedCommentContainer(spannedComment, false);
        }
        
        boolean flow;
        SpannableStringBuilder builder = HtmlParser.createSpanned(sourceModel.subject, sourceModel.comment, spanClickListener, imageGetter,
                themeColors, openSpoilers, POST_REFERER + sourceModel.number);
        int attachmentType = sourceModel.attachments[0].type;
        if (attachmentType == AttachmentModel.TYPE_IMAGE_STATIC || attachmentType == AttachmentModel.TYPE_OTHER_NOTFILE) {
            flow = FlowTextHelper.flowText(builder, floatingModels[0], textFullWidth);
        } else {
            flow = FlowTextHelper.flowText(builder, floatingModels[1], textFullWidth);
        }
        return new SpannedCommentContainer(builder, flow);
    }
    
    private void tryFlow(FloatingModel[] floatingModels) {
        if (sourceModel.attachments != null && sourceModel.attachments.length == 1) {
            int attachmentType = sourceModel.attachments[0].type;
            if (attachmentType == AttachmentModel.TYPE_IMAGE_STATIC || attachmentType == AttachmentModel.TYPE_OTHER_NOTFILE) {
                floating = FlowTextHelper.flowText((SpannableStringBuilder)spannedComment, floatingModels[0]);
            } else {
                floating = FlowTextHelper.flowText((SpannableStringBuilder)spannedComment, floatingModels[1]);
            }
        } else {
            floating = false;
        }
    }
    
    private void parseBadge() {
        if (sourceModel.icons == null || sourceModel.icons.length == 0) return;
        badgeHashes = new String[sourceModel.icons.length];
        StringBuilder titleBuilder = new StringBuilder();
        boolean firstTitle = true;
        for (int i=0; i<sourceModel.icons.length; ++i) {
            badgeHashes[i] = ChanModels.hashBadgeIconModel(sourceModel.icons[i], chanName);
            if (sourceModel.icons[i].description != null && sourceModel.icons[i].description.length() != 0) {
                if (!firstTitle) titleBuilder.append("; ");
                titleBuilder.append(sourceModel.icons[i].description);
                firstTitle = false;
            }
        }
        if (titleBuilder.length() != 0) {
            badgeTitle = titleBuilder.toString();
        }
    }
    
    private void computeThumbnailsHash() {
        int attachmentsCount = sourceModel.attachments != null ? sourceModel.attachments.length : 0;
        attachmentHashes = new String[attachmentsCount];
        for (int i=0; i<attachmentsCount; ++i) {
            AttachmentModel attachment = sourceModel.attachments[i];
            attachmentHashes[i] = ChanModels.hashAttachmentModel(attachment);
        }
    }
    
    private void parseReferences() {
        String comment = sourceModel.comment;
        if (comment == null) return;
        Matcher m = REPLY_LINK_FULL_PATTERN.matcher(comment);
        while (m.find()) referencesTo.add(m.group(1));
    }
    
    /**
     * Построить заголовок поста.
     * @param index индекс (номер поста по порядку), или -1, если не требуется выводить индекс
     * @param bumpLimit бамп лимит на данной доске (индексы начиная с этого значения будут отображаться красным) 
     * @param defaultName имя пользователя на данной доске по умолчанию (будет скрываться)
     * @param threadNumber номер треда, только если страница является результатом поиска (в противном случае - null)
     */
    public void buildSpannedHeader(int index, int bumpLimit, String defaultName, String threadNumber, boolean isSubscribed) {
        String opMark = resources.getString(R.string.postitem_op_mark);
        String sageMark = resources.getString(R.string.postitem_sage_mark);
        
        int positionStart;
        int positionEnd;
        
        SpannableStringBuilder builder = null;
        if (index != -1) {
            builder = new SpannableStringBuilder(Integer.toString(index));
            positionStart = 0;
            positionEnd = builder.length();
            int indexColor = index < bumpLimit ? themeColors.indexForeground : themeColors.indexOverBumpLimit;
            builder.setSpan(new ForegroundColorSpan(indexColor), positionStart, positionEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new StyleSpan(Typeface.BOLD), positionStart, positionEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            
            positionStart = positionEnd + 1;
            builder.append(" ");
        } else {
            builder = new SpannableStringBuilder();
            positionStart = 0;
        }
        
        String numberString = threadNumber == null || sourceModel.number.equals(sourceModel.parentThread) ? sourceModel.number :
                resources.getString(R.string.postitem_post_number_search_header, sourceModel.number, threadNumber);
        positionEnd = positionStart + numberString.length();
        builder.append(numberString);
        builder.setSpan(new ForegroundColorSpan(themeColors.numberForeground), positionStart, positionEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (isSubscribed) builder.setSpan(new SubscriptionSpan(themeColors.subscriptionBackground),
                positionStart, positionEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        
        if (sourceModel.op) {
            positionStart = positionEnd + 1;
            positionEnd = positionStart + opMark.length();
            builder.append(" ").append(opMark);
            builder.setSpan(new ForegroundColorSpan(themeColors.opForeground), positionStart, positionEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        
        if (sourceModel.sage) {
            positionStart = positionEnd + 1;
            positionEnd = positionStart + sageMark.length();
            builder.append(" ").append(sageMark);
            builder.setSpan(new ForegroundColorSpan(themeColors.sageForeground), positionStart, positionEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new RelativeSizeSpan(0.8f), positionStart, positionEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        
        if (sourceModel.color != Color.TRANSPARENT) {
            positionStart = positionEnd + 1;
            positionEnd = positionStart + 1;
            builder.append(" \u25A0");
            builder.setSpan(new ForegroundColorSpan(Color.TRANSPARENT), positionStart, positionEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new BackgroundColorSpan(sourceModel.color), positionStart, positionEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        
        String name = sourceModel.name;
        if (defaultName != null && name.startsWith(defaultName)) {
            int trim = defaultName.length();
            while (trim < name.length() && (name.charAt(trim) == ' ' || name.charAt(trim) == '\u00A0')) ++trim;
            if (name.startsWith("ID:", trim)) {
                trim += 3;
                while (trim < name.length() && (name.charAt(trim) == ' ' || name.charAt(trim) == '\u00A0')) ++trim;
            }
            name = name.substring(trim);
        }
        if (sourceModel.email != null && !sourceModel.email.equals("") && !(name.equals("") && sourceModel.sage)) {
            if (name.equals("")) name = sourceModel.name.equals("") ? sourceModel.email : sourceModel.name;
            positionStart = positionEnd + 1;
            positionEnd = positionStart + name.length();
            builder.append(" ").append(name);
            ClickableURLSpan mailLinkSpan = new ClickableURLSpan("mailto:"+sourceModel.email); 
            builder.setSpan(mailLinkSpan, positionStart, positionEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new ForegroundColorSpan(themeColors.urlLinkForeground), positionStart, positionEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            mailLinkSpan.setOnClickListener(spanClickListener);
        } else {
            if (!name.equals("")) {
                positionStart = positionEnd + 1;
                positionEnd = positionStart + name.length();
                builder.append(" ").append(name);
                builder.setSpan(new ForegroundColorSpan(themeColors.nameForeground), positionStart, positionEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        
        if (sourceModel.trip != null && !sourceModel.trip.equals("")) {
            positionStart = positionEnd + 1;
            positionEnd = positionStart + sourceModel.trip.length();
            builder.append(" ").append(sourceModel.trip);
            builder.setSpan(new ForegroundColorSpan(themeColors.tripForeground), positionStart, positionEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        spannedHeader = builder;
    }
    
    /**
     * Добавить к данному посту ссылку из другого поста.
     * При этом текущая spannable строка со ссылками обнуляется (необходимо будет построить заново). 
     * @param postNumber номер поста
     */
    public void addReferenceFrom(String postNumber) {
        referencesFrom.add(postNumber);
        referencesString = null;
        referencesQuantityString = null;
    }
    
    /**
     * Построить spannable строку со ссылками на этот пост.
     */
    public void buildReferencesString() {
        if (threadNumber == null || referencesFrom.isEmpty()) {
            referencesString = null;
            referencesQuantityString = null;
            return;
        }
        
        SpannableStringBuilder builder = new SpannableStringBuilder(resources.getString(R.string.postitem_replies));
        builder.append(" ");
        String prefix = ", ";
        boolean first = true;
        int positionStart;
        int positionEnd = builder.length();
        
        for (String reference : referencesFrom) {
            if (!first) {
                builder.append(prefix);
                positionEnd += prefix.length();
            }
            first = false;
            
            UrlPageModel urlModel = new UrlPageModel();
            urlModel.type = UrlPageModel.TYPE_THREADPAGE;
            urlModel.chanName = chanName;
            urlModel.boardName = boardName;
            urlModel.threadNumber = threadNumber;
            urlModel.postNumber = reference;
            
            String refUrl = chanModule.buildUrl(urlModel);
            builder.append(">>").append(reference);
            positionStart = positionEnd;
            positionEnd = builder.length();
            ClickableURLSpan refLinkSpan = new ClickableURLSpan(refUrl); 
            builder.setSpan(refLinkSpan, positionStart, positionEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new ForegroundColorSpan(themeColors.urlLinkForeground), positionStart, positionEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            refLinkSpan.setOnClickListener(spanClickListener);
            refLinkSpan.setReferer(POST_REFERER + sourceModel.number);
        }
        builder.setSpan(new StyleSpan(Typeface.ITALIC), 0, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        referencesString = builder;
        
        int repliesQuantity = referencesFrom.size();
        builder = new SpannableStringBuilder(resources.getQuantityString(R.plurals.postitem_replies_quantity, repliesQuantity, repliesQuantity));
        ClickableURLSpan refQuantityLinkSpan = new ClickableURLSpan(ALL_REFERENCES_URI + sourceModel.number);
        builder.setSpan(refQuantityLinkSpan, 0, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new ForegroundColorSpan(themeColors.urlLinkForeground), 0, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        refQuantityLinkSpan.setOnClickListener(spanClickListener);
        referencesQuantityString = builder;
    }
    
    
    
    /**
     * Построить строку с количеством постов и вложений
     * @param postsCount количество постов
     * @param attachmentsCount количество вложений
     */
    public void buildPostsCountString(int postsCount, int attachmentsCount) {
        if (postsCount > 0) {
            postsCountString = resources.getQuantityString(R.plurals.postitem_posts_quantity, postsCount, postsCount);
            if (attachmentsCount > 0) {
                postsCountString += ", " + resources.getQuantityString(R.plurals.postitem_files_quantity, attachmentsCount, attachmentsCount);
            }
        } else {
            if (attachmentsCount > 0) {
                postsCountString = resources.getQuantityString(R.plurals.postitem_files_quantity, attachmentsCount, attachmentsCount);
            } else {
                postsCountString = null;
            }
        }
    }
    
    /**
     * Построить строку с информацией о состоянии треда: прикреплен, закрыт, зациклен (для заголовка треда).
     * @param isSticky является ли тред прикреплённым
     * @param isClosed является ли тред закрытым для обсуждения
     * @param isCyclical является ли тред цикличным
     */
    public void buildThreadConditionString(boolean isSticky, boolean isClosed, boolean isCyclical) {
        StringBuilder condition = new StringBuilder();
        if (isSticky) condition.append(resources.getString(R.string.postitem_sticky_thread)).append(", ");
        if (isClosed) condition.append(resources.getString(R.string.postitem_closed_thread)).append(", ");
        if (isCyclical) condition.append(resources.getString(R.string.postitem_cyclical_thread));
        if (condition.length() > 0) {
            threadConditionString = condition.toString();
            if (threadConditionString.endsWith(", "))
                threadConditionString = threadConditionString.substring(0, threadConditionString.length() - 2);
        }
    }
    
    private void findSubscriptions(String[] subscriptions) {
        char[] buf = null;
        SpannableStringBuilder spanned = (SpannableStringBuilder) spannedComment;
        for (String subscription : subscriptions) {
            if (!referencesTo.contains(subscription)) continue;
            ClickableURLSpan[] spans = spanned.getSpans(0, spanned.length(), ClickableURLSpan.class);
            if (spans == null || spans.length == 0) continue;
            char[] search = (">>" + subscription).toCharArray();
            if (buf == null || buf.length != search.length) buf = new char[search.length];
            for (ClickableURLSpan span : spans) {
                int startIndex = spanned.getSpanStart(span);
                if (startIndex + buf.length > spanned.length()) continue;
                spanned.getChars(startIndex, startIndex + buf.length, buf, 0);
                if (Arrays.equals(search, buf)) {
                    spanned.setSpan(new SubscriptionSpan(themeColors.subscriptionBackground),
                            startIndex, startIndex + buf.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
    }
    
    /**
     * В случае необходимости перестроить комментарий и заголовок поста (если был построен) после добавления подписки на пост newSubscription.
     * Если этот содержит ссылки на пост newSubscription, они будут выделены в тексте комментария;
     * если этот пост сам является newSubscription, он будет выделен в заголовке.
     * @param newSubscription пост, на который добавлена подписка
     */
    public void onSubscribe(String newSubscription) {
        if (spannedHeader != null && newSubscription.equals(sourceModel.number)) {
            SpannableStringBuilder spanned = (SpannableStringBuilder) spannedHeader;
            int start = spanned.toString().indexOf(newSubscription);
            if (start >= 0) spanned.setSpan(new SubscriptionSpan(themeColors.subscriptionBackground),
                    start, start + newSubscription.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        
        if (!referencesTo.contains(newSubscription)) return;
        SpannableStringBuilder spanned = (SpannableStringBuilder) spannedComment;
        ClickableURLSpan[] spans = spanned.getSpans(0, spanned.length(), ClickableURLSpan.class);
        if (spans == null || spans.length == 0) return;
        char[] search = (">>" + newSubscription).toCharArray();
        char[] buf = new char[search.length];
        for (ClickableURLSpan span : spans) {
            int startIndex = spanned.getSpanStart(span);
            if (startIndex + buf.length > spanned.length()) continue;
            spanned.getChars(startIndex, startIndex + buf.length, buf, 0);
            if (Arrays.equals(search, buf)) {
                spanned.setSpan(new SubscriptionSpan(themeColors.subscriptionBackground),
                        startIndex, startIndex + buf.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }
    
    /**
     * В случае необходимости перестроить комментарий и заголовок поста (если был построен) после удаления подписки на пост oldSubscription.
     * Если этот содержит выделенные ссылки на пост oldSubscription в тексте комментария, выделение будет снято;
     * если этот пост сам является oldSubscription, выделение будет снято в заголовке.
     * @param oldSubscription пост, на который удалена подписка
     */
    public void onUnsubscribe(String oldSubscription) {
        if (spannedHeader != null && oldSubscription.equals(sourceModel.number)) {
            SpannableStringBuilder spanned = (SpannableStringBuilder) spannedHeader;
            for (SubscriptionSpan span : spanned.getSpans(0, spanned.length(), SubscriptionSpan.class))
                spanned.removeSpan(span);
        }
        
        if (!referencesTo.contains(oldSubscription)) return;
        SpannableStringBuilder spanned = (SpannableStringBuilder) spannedComment;
        SubscriptionSpan[] spans = spanned.getSpans(0, spanned.length(), SubscriptionSpan.class);
        if (spans == null || spans.length == 0) return;
        char[] search = (">>" + oldSubscription).toCharArray();
        char[] buf = new char[search.length];
        for (SubscriptionSpan span : spans) {
            int startIndex = spannedComment.getSpanStart(span);
            if (startIndex + buf.length > spanned.length()) continue;
            spanned.getChars(startIndex, startIndex + buf.length, buf, 0);
            if (Arrays.equals(search, buf)) spanned.removeSpan(span);
        }
    }
    
    private static class SubscriptionSpan extends BackgroundColorSpan {
        public SubscriptionSpan(int color) {
            super(color);
        }
        public SubscriptionSpan(Parcel src) {
            super(src);
        }
    }
}
