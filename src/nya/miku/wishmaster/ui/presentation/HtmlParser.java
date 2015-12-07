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

//исходная версия - android-22

package nya.miku.wishmaster.ui.presentation;

import android.graphics.Color;
import nya.miku.wishmaster.ui.ResourcesCompat23;
import nya.miku.wishmaster.ui.presentation.ClickableURLSpan.URLSpanClickListener;
import nya.miku.wishmaster.ui.presentation.ThemeUtils.ThemeColors;

import org.ccil.cowan.tagsoup.HTMLSchema;
import org.ccil.cowan.tagsoup.Parser;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.ParagraphStyle;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.TextAppearanceSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Парсер HTML в Spanned-строки для отображения в TextView.<br>
 * Основан на исходном коде {@link android.text.Html} (использует TagSoup) с изменениями
 * @author miku-nyan
 *
 */
public class HtmlParser {
    
    /**
     * Создать Spanned строку из html текста.
     * @param subject тема (заголовок) сообщения, будет вставлена в начале итоговой Spanned строки.
     * Может принимать null или пустую строку, тогда заголовок вставлен не будет 
     * @param source исходный html текст. Поддерживаются тэги, поддерживаемые {@link android.text.Html#fromHtml(String)}, а также:<ul>
     * <li><b>&lt;ol&gt;</b>, <b>&lt;ul&gt;</b>, <b>&lt;li&gt;</b> - списки</li>
     * <li><b>&lt;s&gt;</b>, <b>&lt;strike&gt;</b>, <b>&lt;del&gt;</b> - перечёркнутый текст</li>
     * <li><b>&lt;code&gt;</b> - отображается моноширинным шрифтом</li>
     * <li><b>&lt;blockquote class="unkfunc"&gt;</b> - форумная цитата (отображается цветом выбранной темы оформления), выделяется абзацами</li>
     * <li><b>&lt;span class="unkfunc"&gt;</b>, <b>&lt;span class="quote"&gt;</b> - аналогично предыдущему, не выделяется абзацами</li>
     * <li><b>&lt;span class="spoiler"&gt;</b> - спойлер, затемнённый текст (отображается цветами выбранной темы оформления)</li>
     * <li><b>&lt;span class="s"&gt;</b> - перечёркнутый текст</li>
     * <li><b>&lt;span class="u"&gt;</b> - подчёркнутый текст</li>
     * <li><b>&lt;font style="..."&gt;</b> и <b>&lt;span style="..."&gt;</b> - CSS-стиль, поддерживаются color и background-color</li>
     * <li><b>&lt;aibquote&gt;</b> и <b>&lt;aibspoiler&gt;</b> - аналогично &lt;span class="unkfunc"&gt; и &lt;span class="spoiler"&gt;.
     * Может использоваться, если форум выдаёт текст в нестандартном виде, в этом случае можно заменять (при загрузке и парсинге ответа)
     * выделение цитат и спойлеров на данные псевдотэги, для однозначного соответствия</li></ul>
     * @param spanClickListener обработчик нажатий на ссылки
     * @param imageGetter обработчик загрузки изображений (тэг &lt;img&gt; внутри html текста)
     * @param themeColors объект {@link ThemeUtils.ThemeColors} для текущей темы оформления
     * @param openSpoilers отображать спойлеры открытыми
     * @param referer ссылка на этот текущий пост (для задания referer у ссылок)
     * @return объект SpannableStringBuilder
     */
    public static SpannableStringBuilder createSpanned(String subject, String source,
            URLSpanClickListener spanClickListener, ImageGetter imageGetter, ThemeColors themeColors, boolean openSpoilers, String referer) {
        SpannableStringBuilder spanned = fromHtml(subject, source, themeColors, imageGetter, openSpoilers);
        replaceUrls(spanned, spanClickListener, themeColors, referer);
        if (!openSpoilers) fixSpoilerSpans(spanned, themeColors);
        return spanned;
    }
    
    /** 
     * Заменить ссылки (URLSpan) на ClickableURLSpan со своим обработчиком нажатия
     * @param listener обработчик нажатий на ссылки 
     */
    private static void replaceUrls(SpannableStringBuilder builder, URLSpanClickListener listener, ThemeColors themeColors, String referer) {
        URLSpan[] spans = builder.getSpans(0, builder.length(), URLSpan.class);
        if (spans.length > 0) {
            for (URLSpan span : spans) {
                ClickableURLSpan.replaceURLSpan(builder, span, themeColors.urlLinkForeground).setOnClickListener(listener).setReferer(referer);
            }
        }
    }
    
    /**
     * Исправить расположение SpoilerSpan и ForegroundColorSpan цвета ссылок для корректной работы ссылок под спойлерами
     */
    private static void fixSpoilerSpans(SpannableStringBuilder builder, ThemeColors themeColors) {
        SpoilerSpan[] spoilers = builder.getSpans(0, builder.length(), SpoilerSpan.class);
        for (SpoilerSpan span : spoilers) {
            int start = builder.getSpanStart(span);
            int end = builder.getSpanEnd(span);
            builder.removeSpan(span);
            builder.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        ClickableURLSpan[] urls = builder.getSpans(0, builder.length(), ClickableURLSpan.class);
        for (ClickableURLSpan span : urls) {
            int start = builder.getSpanStart(span);
            int end = builder.getSpanEnd(span);
            builder.setSpan(new ForegroundColorSpan(themeColors.urlLinkForeground), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
    
    /**
     * Обработчик-загрузчик картинок в HTML тэге &lt;img&gt;.
     */
    public static interface ImageGetter {
        /**
         * Метод вызывается, когда HTML парсер встречает тэг &lt;img&gt;.
         * @param source строка из аттрибута "src"
         * @return должен вернуть представление картинки как Drawable или <code>null</code> для замены на общую картинку (ошибки).
         * Убедитесь, что был вызван setBounds() на Drawable (если его bounds не были установлены)
         */
        public Drawable getDrawable(String source);
    }

    private HtmlParser() { }

    /**
     * Lazy initialization holder for HTML parser. This class will
     * a) be preloaded by the zygote, or b) not loaded until absolutely
     * necessary.
     */
    private static class HtmlParserHolder {
        private static final HTMLSchema schema = new HTMLSchema();
    }

    /**
     * Returns displayable styled text from the provided HTML string.
     * Any &lt;img&gt; tags in the HTML will use the specified ImageGetter
     * to request a representation of the image (use null if you don't
     * want this) and the specified TagHandler to handle unknown tags
     * (specify null if you don't want this).
     *
     * <p>This uses TagSoup to handle real HTML, including all of the brokenness found in the wild.
     */
    private static SpannableStringBuilder fromHtml(String subject, String source, ThemeColors colors, ImageGetter imageGetter, boolean openSpoilers) {
        Parser parser = new Parser();
        try {
            parser.setProperty(Parser.schemaProperty, HtmlParserHolder.schema);
        } catch (org.xml.sax.SAXNotRecognizedException e) {
            // Should not happen.
            throw new RuntimeException(e);
        } catch (org.xml.sax.SAXNotSupportedException e) {
            // Should not happen.
            throw new RuntimeException(e);
        }

        HtmlToSpannedConverter converter = new HtmlToSpannedConverter(subject, source, colors, imageGetter, openSpoilers, parser);
        return converter.convert();
    }

}

class HtmlToSpannedConverter implements ContentHandler {
    
    private static final float[] HEADER_SIZES = {
        1.5f, 1.4f, 1.3f, 1.2f, 1.1f, 1f,
    };
    
    private static final Pattern CSS_STYLE_COLOR_RGB_PATTERN = Pattern.compile(".*?color: ?rgb\\((\\d+), ?(\\d+), ?(\\d+)\\).*");
    private static final Pattern CSS_STYLE_COLOR_COMMON_PATTERN = Pattern.compile(".*?color: ?(#?\\w+).*");

    private String mSource;
    private XMLReader mReader;
    private SpannableStringBuilder mSpannableStringBuilder;
    //костыли для правильной обработки (обрезки) <p>...</p> в начале и в конце
    private int mStartLength = 0; //длина subject + '\n'
    private int[] mLastPTagLength = new int[] {-1, -1}; //2 целых числа {before, after}, длина до и после обработки последнего тэга (</p>)
    private LinkedList<Object> mListTags = new LinkedList<>();
    private ThemeColors mColors;
    private boolean mOpenSpoilers;
    private HtmlParser.ImageGetter mImageGetter;
    
    public HtmlToSpannedConverter(String subject, String source, ThemeColors colors, HtmlParser.ImageGetter imageGetter, boolean openSpoilers,
            Parser parser) {
        mSource = source;
        mSpannableStringBuilder = new SpannableStringBuilder();
        if (!TextUtils.isEmpty(subject)) {
            mSpannableStringBuilder.append(subject);
            int len = mSpannableStringBuilder.length();
            mSpannableStringBuilder.setSpan(new RelativeSizeSpan(1.25f), 0, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            mSpannableStringBuilder.setSpan(new StyleSpan(Typeface.BOLD), 0, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (colors != null) {
                mSpannableStringBuilder.setSpan(new ForegroundColorSpan(colors.subjectForeground), 0, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            mSpannableStringBuilder.append('\n');
            mStartLength = mSpannableStringBuilder.length();
        }
        mColors = colors;
        mOpenSpoilers = openSpoilers;
        mImageGetter = imageGetter;
        mReader = parser;
    }

    public SpannableStringBuilder convert() {
        mReader.setContentHandler(this);
        try {
            mReader.parse(new InputSource(new StringReader(mSource)));
        } catch (IOException e) {
            // We are reading from a string. There should not be IO problems.
            throw new RuntimeException(e);
        } catch (SAXException e) {
            // TagSoup doesn't throw parse exceptions.
            throw new RuntimeException(e);
        }

        // Fix flags and range for paragraph-type markup.
        Object[] obj = mSpannableStringBuilder.getSpans(0, mSpannableStringBuilder.length(), ParagraphStyle.class);
        for (int i = 0; i < obj.length; i++) {
            int start = mSpannableStringBuilder.getSpanStart(obj[i]);
            int end = mSpannableStringBuilder.getSpanEnd(obj[i]);

            // If the last line of the range is blank, back off by one.
            if (end - 2 >= 0) {
                if (mSpannableStringBuilder.charAt(end - 1) == '\n' &&
                    mSpannableStringBuilder.charAt(end - 2) == '\n') {
                    end--;
                }
            }

            if (end == start) {
                mSpannableStringBuilder.removeSpan(obj[i]);
            } else {
                mSpannableStringBuilder.setSpan(obj[i], start, end, Spannable.SPAN_PARAGRAPH);
            }
        }

        if (mLastPTagLength[0] != mLastPTagLength[1] && mLastPTagLength[1] == mSpannableStringBuilder.length()) {
            mSpannableStringBuilder.delete(mLastPTagLength[0], mLastPTagLength[1]);
        }
        return mSpannableStringBuilder;
    }

    private void handleStartTag(String tag, Attributes attributes) {
        if (tag.equalsIgnoreCase("br")) {
            // We don't need to handle this. TagSoup will ensure that there's a </br> for each <br>
            // so we can safely emite the linebreaks when we handle the close tag.
        } else if (tag.equalsIgnoreCase("p")) {
            handleP(mSpannableStringBuilder, mStartLength, mLastPTagLength);
        } else if (tag.equalsIgnoreCase("div")) {
            handleP(mSpannableStringBuilder, mStartLength, mLastPTagLength);
        } else if (tag.equalsIgnoreCase("strong")) {
            start(mSpannableStringBuilder, new Bold());
        } else if (tag.equalsIgnoreCase("b")) {
            start(mSpannableStringBuilder, new Bold());
        } else if (tag.equalsIgnoreCase("em")) {
            start(mSpannableStringBuilder, new Italic());
        } else if (tag.equalsIgnoreCase("cite")) {
            start(mSpannableStringBuilder, new Italic());
        } else if (tag.equalsIgnoreCase("dfn")) {
            start(mSpannableStringBuilder, new Italic());
        } else if (tag.equalsIgnoreCase("i")) {
            start(mSpannableStringBuilder, new Italic());
        } else if (tag.equalsIgnoreCase("s")) {
            start(mSpannableStringBuilder, new Strike());
        } else if (tag.equalsIgnoreCase("strike")) {
            start(mSpannableStringBuilder, new Strike());
        } else if (tag.equalsIgnoreCase("del")) {
            start(mSpannableStringBuilder, new Strike());
        } else if (tag.equalsIgnoreCase("big")) {
            start(mSpannableStringBuilder, new Big());
        } else if (tag.equalsIgnoreCase("small")) {
            start(mSpannableStringBuilder, new Small());
        } else if (tag.equalsIgnoreCase("font")) {
            startFont(mSpannableStringBuilder, attributes);
        } else if (tag.equalsIgnoreCase("blockquote")) {
            String classAttr = attributes.getValue("", "class");
            handleP(mSpannableStringBuilder, mStartLength, mLastPTagLength);
            start(mSpannableStringBuilder, new Blockquote(classAttr != null && classAttr.equals("unkfunc")));
        } else if (tag.equalsIgnoreCase("tt")) {
            start(mSpannableStringBuilder, new Monospace());
        } else if (tag.equalsIgnoreCase("code")) {
            start(mSpannableStringBuilder, new Monospace());
        } else if (tag.equalsIgnoreCase("ul")) {
            mListTags.addFirst(new UlTag());
        } else if (tag.equalsIgnoreCase("ol")) {
            mListTags.addFirst(new OlTag());
        } else if (tag.equalsIgnoreCase("li")) {
            handleLi(mSpannableStringBuilder, mListTags.peek(), mListTags.size());
        } else if (tag.equalsIgnoreCase("tr")) {
            handleTr(mSpannableStringBuilder, true);
        } else if (tag.equalsIgnoreCase("td")) {
            handleTd(mSpannableStringBuilder, true);
        } else if (tag.equalsIgnoreCase("a")) {
            startA(mSpannableStringBuilder, attributes);
        } else if (tag.equalsIgnoreCase("u")) {
            start(mSpannableStringBuilder, new Underline());
        } else if (tag.equalsIgnoreCase("sup")) {
            start(mSpannableStringBuilder, new Super());
        } else if (tag.equalsIgnoreCase("sub")) {
            start(mSpannableStringBuilder, new Sub());
        } else if (tag.length() == 2 &&
                   Character.toLowerCase(tag.charAt(0)) == 'h' &&
                   tag.charAt(1) >= '1' && tag.charAt(1) <= '6') {
            handleP(mSpannableStringBuilder, mStartLength, mLastPTagLength);
            start(mSpannableStringBuilder, new Header(tag.charAt(1) - '1'));
        } else if (tag.equalsIgnoreCase("img")) {
            startImg(mSpannableStringBuilder, attributes, mImageGetter);
        } else if (tag.equalsIgnoreCase("span")) {
            startSpan(mSpannableStringBuilder, attributes);
        } else if (tag.equalsIgnoreCase("aibquote")) {
            start(mSpannableStringBuilder, new Aibquote());
        } else if (tag.equalsIgnoreCase("aibspoiler")) {
            start(mSpannableStringBuilder, new Aibspoiler());
        }/* else if (mTagHandler != null) {
            mTagHandler.handleTag(true, tag, mSpannableStringBuilder, mReader);
        }*/
    }

    private void handleEndTag(String tag) {
        if (tag.equalsIgnoreCase("br")) {
            handleBr(mSpannableStringBuilder);
        } else if (tag.equalsIgnoreCase("p")) {
            handleP(mSpannableStringBuilder, mStartLength, mLastPTagLength);
        } else if (tag.equalsIgnoreCase("div")) {
            handleP(mSpannableStringBuilder, mStartLength, mLastPTagLength);
        } else if (tag.equalsIgnoreCase("strong")) {
            end(mSpannableStringBuilder, Bold.class, new StyleSpan(Typeface.BOLD));
        } else if (tag.equalsIgnoreCase("b")) {
            end(mSpannableStringBuilder, Bold.class, new StyleSpan(Typeface.BOLD));
        } else if (tag.equalsIgnoreCase("em")) {
            end(mSpannableStringBuilder, Italic.class, new StyleSpan(Typeface.ITALIC));
        } else if (tag.equalsIgnoreCase("cite")) {
            end(mSpannableStringBuilder, Italic.class, new StyleSpan(Typeface.ITALIC));
        } else if (tag.equalsIgnoreCase("dfn")) {
            end(mSpannableStringBuilder, Italic.class, new StyleSpan(Typeface.ITALIC));
        } else if (tag.equalsIgnoreCase("i")) {
            end(mSpannableStringBuilder, Italic.class, new StyleSpan(Typeface.ITALIC));
        } else if (tag.equalsIgnoreCase("s")) {
            end(mSpannableStringBuilder, Strike.class, new StrikethroughSpan());
        } else if (tag.equalsIgnoreCase("strike")) {
            end(mSpannableStringBuilder, Strike.class, new StrikethroughSpan());
        } else if (tag.equalsIgnoreCase("del")) {
            end(mSpannableStringBuilder, Strike.class, new StrikethroughSpan());    
        } else if (tag.equalsIgnoreCase("big")) {
            end(mSpannableStringBuilder, Big.class, new RelativeSizeSpan(1.25f));
        } else if (tag.equalsIgnoreCase("small")) {
            end(mSpannableStringBuilder, Small.class, new RelativeSizeSpan(0.8f));
        } else if (tag.equalsIgnoreCase("font")) {
            endFont(mSpannableStringBuilder);
        } else if (tag.equalsIgnoreCase("blockquote")) {
            handleP(mSpannableStringBuilder, mStartLength, mLastPTagLength);
            endBlockquote(mSpannableStringBuilder, mColors);
        } else if (tag.equalsIgnoreCase("tt")) {
            end(mSpannableStringBuilder, Monospace.class, new TypefaceSpan("monospace"));
        } else if (tag.equalsIgnoreCase("code")) {
            end(mSpannableStringBuilder, Monospace.class, new TypefaceSpan("monospace"));
        } else if (tag.equalsIgnoreCase("ul")) {
            if (!mListTags.isEmpty()) mListTags.removeFirst();
        } else if (tag.equalsIgnoreCase("ol")) {
            if (!mListTags.isEmpty()) mListTags.removeFirst();
        } else if (tag.equalsIgnoreCase("li")) {
            //обрабатывается только открывающийся <li>
        } else if (tag.equalsIgnoreCase("tr")) {
            handleTr(mSpannableStringBuilder, false);
        } else if (tag.equalsIgnoreCase("td")) {
            handleTd(mSpannableStringBuilder, false);
        } else if (tag.equalsIgnoreCase("a")) {
            endA(mSpannableStringBuilder);
        } else if (tag.equalsIgnoreCase("u")) {
            end(mSpannableStringBuilder, Underline.class, new UnderlineSpan());
        } else if (tag.equalsIgnoreCase("sup")) {
            end(mSpannableStringBuilder, Super.class, new SuperscriptSpan());
        } else if (tag.equalsIgnoreCase("sub")) {
            end(mSpannableStringBuilder, Sub.class, new SubscriptSpan());
        } else if (tag.length() == 2 &&
                Character.toLowerCase(tag.charAt(0)) == 'h' &&
                tag.charAt(1) >= '1' && tag.charAt(1) <= '6') {
            handleP(mSpannableStringBuilder, mStartLength, mLastPTagLength);
            endHeader(mSpannableStringBuilder);
        } else if (tag.equalsIgnoreCase("span")) {
            endSpan(mSpannableStringBuilder, mColors, mOpenSpoilers);
        } else if (tag.equalsIgnoreCase("aibquote")) {
            end(mSpannableStringBuilder, Aibquote.class, new ForegroundColorSpan(mColors != null ? mColors.quoteForeground : Color.GREEN));
        } else if (tag.equalsIgnoreCase("aibspoiler")) {
            endAibspoiler(mSpannableStringBuilder, mColors, mOpenSpoilers);
        }/* else if (mTagHandler != null) {
            mTagHandler.handleTag(false, tag, mSpannableStringBuilder, mReader);
        }*/
    }

    private static void handleP(SpannableStringBuilder text, int startLength, int[] lastPTagLengthRefs) {
        lastPTagLengthRefs[0] = text.length();
        int len = text.length() - startLength;

        if (len >= 1 && text.charAt(text.length() - 1) == '\n') {
            if (len >= 2 && text.charAt(text.length() - 2) == '\n') {
                lastPTagLengthRefs[1] = text.length();
                return;
            }

            text.append("\n");
            lastPTagLengthRefs[1] = text.length();
            return;
        }

        if (len != 0) {
            text.append("\n\n");
        }
        lastPTagLengthRefs[1] = text.length();
    }

    private static void handleBr(SpannableStringBuilder text) {
        text.append("\n");
    }
    
    private static void handleLi(SpannableStringBuilder text, Object tag, int level) {
        if (tag == null) return;
        
        int len = text.length();
        if (len >= 1 && text.charAt(len - 1) != '\n') text.append("\n");
        for (int i=1; i<level; ++i) text.append("\t");
        if (tag instanceof OlTag) text.append(Integer.toString(((OlTag) tag).curIndex++) + ". ");
        else if (tag instanceof UlTag) text.append("\u2022 ");
    }
    
    private static void handleTd(SpannableStringBuilder text, boolean open) {
        if (!open) text.append(" | ");
    }
    
    private static void handleTr(SpannableStringBuilder text, boolean open) {
        text.append(open ? "| " : "\n");
    }

    private static Object getLast(Spanned text, Class<?> kind) {
        /*
         * This knows that the last returned object from getSpans()
         * will be the most recently added.
         */
        Object[] objs = text.getSpans(0, text.length(), kind);

        if (objs.length == 0) {
            return null;
        } else {
            return objs[objs.length - 1];
        }
    }

    private static void start(SpannableStringBuilder text, Object mark) {
        int len = text.length();
        text.setSpan(mark, len, len, Spannable.SPAN_MARK_MARK);
    }

    private static void end(SpannableStringBuilder text, Class<?> kind, Object repl) {
        int len = text.length();
        Object obj = getLast(text, kind);
        int where = text.getSpanStart(obj);

        text.removeSpan(obj);

        if (where != len) {
            text.setSpan(repl, where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void startImg(SpannableStringBuilder text, Attributes attributes, HtmlParser.ImageGetter img) {
        String src = attributes.getValue("", "src");
        Drawable d = null;

        if (img != null) {
            d = img.getDrawable(src);
        }

        if (d == null) {
            d = ResourcesCompat.getDrawable(Resources.getSystem(), android.R.drawable.ic_menu_report_image, null);
            d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        }

        int len = text.length();
        text.append("\uFFFC");

        text.setSpan(new ImageSpan(d, src), len, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    
    private static void startFont(SpannableStringBuilder text, Attributes attributes) {
        String color = attributes.getValue("", "color");
        String face = attributes.getValue("", "face");
        String style = attributes.getValue("", "style");

        int len = text.length();
        text.setSpan(new Font(color, face, style), len, len, Spannable.SPAN_MARK_MARK);
    }

    private static void endFont(SpannableStringBuilder text) {
        int len = text.length();
        Object obj = getLast(text, Font.class);
        int where = text.getSpanStart(obj);

        text.removeSpan(obj);

        if (where != len) {
            Font f = (Font) obj;

            if (!TextUtils.isEmpty(f.mColor)) {
                if (f.mColor.startsWith("@")) {
                    Resources res = Resources.getSystem();
                    String name = f.mColor.substring(1);
                    int colorRes = res.getIdentifier(name, "color", "android");
                    if (colorRes != 0) {
                        ColorStateList colors = ResourcesCompat23.getColorStateList(res, colorRes);
                        text.setSpan(new TextAppearanceSpan(null, 0, 0, colors, null), where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                } else {
                    int c = ColorHidden.getHtmlColor(f.mColor);
                    if (c != -1) {
                        text.setSpan(new ForegroundColorSpan(c | 0xFF000000), where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }

            if (f.mFace != null) {
                text.setSpan(new TypefaceSpan(f.mFace), where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            
            if (f.mStyle != null) {
                List<Object> styleSpans = parseStyleAttributes(f.mStyle);
                for (Object span : styleSpans) {
                    text.setSpan(span, where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
    }

    private static void startA(SpannableStringBuilder text, Attributes attributes) {
        String href = attributes.getValue("", "href");

        int len = text.length();
        text.setSpan(new Href(href), len, len, Spannable.SPAN_MARK_MARK);
    }

    private static void endA(SpannableStringBuilder text) {
        int len = text.length();
        Object obj = getLast(text, Href.class);
        int where = text.getSpanStart(obj);

        text.removeSpan(obj);

        if (where != len) {
            Href h = (Href) obj;

            if (h.mHref != null) {
                text.setSpan(new URLSpan(h.mHref), where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private static void endHeader(SpannableStringBuilder text) {
        int len = text.length();
        Object obj = getLast(text, Header.class);

        int where = text.getSpanStart(obj);

        text.removeSpan(obj);

        // Back off not to change only the text, not the blank line.
        while (len > where && text.charAt(len - 1) == '\n') {
            len--;
        }

        if (where != len) {
            Header h = (Header) obj;

            text.setSpan(new RelativeSizeSpan(HEADER_SIZES[h.mLevel]), where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new StyleSpan(Typeface.BOLD), where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
    
    private static void endAibspoiler(SpannableStringBuilder text, ThemeColors colors, boolean openSpoilers) {
        int len = text.length();
        Object obj = getLast(text, Aibspoiler.class);
        int where = text.getSpanStart(obj);
        text.removeSpan(obj);
        
        if (where != len && colors != null) {
            if (openSpoilers) {
                text.setSpan(new ForegroundColorSpan(colors.spoilerForeground), where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new BackgroundColorSpan(colors.spoilerBackground), where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                text.setSpan(new SpoilerSpan(colors.spoilerForeground, colors.spoilerBackground), where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }
    
    private static void endBlockquote(SpannableStringBuilder text, ThemeColors colors) {
        int len = text.length();
        Object obj = getLast(text, Blockquote.class);
        int where = text.getSpanStart(obj);
        text.removeSpan(obj);
        
        if (where != len) {
            Blockquote b = (Blockquote) obj;
            if (b.mIsUnkfunc) {
                if (colors != null) {
                    text.setSpan(new ForegroundColorSpan(colors.quoteForeground), where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else {
                text.setSpan(new QuoteSpan(), where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }
    
    private static void startSpan(SpannableStringBuilder text, Attributes attributes) {
        String style = attributes.getValue("", "style");
        String classAttr = attributes.getValue("", "class");
        boolean isAibquote = classAttr != null && (classAttr.equals("unkfunc") || classAttr.equals("quote"));
        boolean isAibspoiler = classAttr != null && classAttr.equals("spoiler");
        boolean isUnderline = classAttr != null && classAttr.equals("u");
        boolean isStrike = classAttr != null && classAttr.equals("s");
        
        int len = text.length();
        text.setSpan(new Span(style, isAibquote, isAibspoiler, isUnderline, isStrike), len, len, Spannable.SPAN_MARK_MARK);
    }
    
    private static void endSpan(SpannableStringBuilder text, ThemeColors colors, boolean openSpoilers) {
        int len = text.length();
        Object obj = getLast(text, Span.class);
        int where = text.getSpanStart(obj);
        text.removeSpan(obj);
        
        if (where != len) {
            Span s = (Span) obj;
            
            if (s.mStyle != null) {
                List<Object> styleSpans = parseStyleAttributes(s.mStyle);
                for (Object span : styleSpans) {
                    text.setSpan(span, where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            
            if (colors != null && s.mIsAibquote) {
                text.setSpan(new ForegroundColorSpan(colors.quoteForeground), where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            
            if (colors != null && s.mIsAibspoiler) {
                if (openSpoilers) {
                    text.setSpan(new ForegroundColorSpan(colors.spoilerForeground), where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    text.setSpan(new BackgroundColorSpan(colors.spoilerBackground), where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    text.setSpan(new SpoilerSpan(colors.spoilerForeground, colors.spoilerBackground), where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            
            if (s.mIsUnderline) {
                text.setSpan(new UnderlineSpan(), where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            
            if (s.mIsStrike) {
                text.setSpan(new StrikethroughSpan(), where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            
        }
    }
    
    private static List<Object> parseStyleAttributes(String style) {
        if (TextUtils.isEmpty(style)) return Collections.emptyList();
        int foregroundColor = 0, backgroundColor = 0;
        String[] cssStyle = style.split("[;]");
        for (String s : cssStyle) {
            int color = parseColor(s);
            if (color != 0) {
                if (s.toLowerCase(Locale.US).indexOf("background") != -1) backgroundColor = color; else foregroundColor = color;
            }
        }
        if (foregroundColor == 0 && backgroundColor == 0) {
            return Collections.emptyList();
        } else if (backgroundColor == 0) {
            return Collections.singletonList((Object) new ForegroundColorSpan(foregroundColor));
        } else if (foregroundColor == 0) {
            return Collections.singletonList((Object) new BackgroundColorSpan(backgroundColor));
        } else {
            List<Object> spans = new ArrayList<Object>(2);
            spans.add(new ForegroundColorSpan(foregroundColor));
            spans.add(new BackgroundColorSpan(backgroundColor));
            return spans;
        }
    }
    
    private static int parseColor(String css) {
        if (TextUtils.isEmpty(css)) return 0;
        try {
            Matcher m = CSS_STYLE_COLOR_RGB_PATTERN.matcher(css);
            if (m.find() && m.groupCount() == 3) {
                int n1 = Integer.parseInt(m.group(1));
                int n2 = Integer.parseInt(m.group(2));
                int n3 = Integer.parseInt(m.group(3));
                return Color.rgb(n1, n2, n3);
            }
            m = CSS_STYLE_COLOR_COMMON_PATTERN.matcher(css);
            if (m.find() && m.groupCount() == 1) {
                return Color.parseColor(m.group(1));
            }
        } catch (Exception e) { /*исключение во время парсинга (некорректное значение или неизвестный цвет)*/ }
        return 0;
    }

    public void setDocumentLocator(Locator locator) {
    }

    public void startDocument() throws SAXException {
    }

    public void endDocument() throws SAXException {
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
    }

    public void endPrefixMapping(String prefix) throws SAXException {
    }

    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        handleStartTag(localName, attributes);
    }

    public void endElement(String uri, String localName, String qName) throws SAXException {
        handleEndTag(localName);
    }

    public void characters(char ch[], int start, int length) throws SAXException {
        StringBuilder sb = new StringBuilder();

        /*
         * Ignore whitespace that immediately follows other whitespace;
         * newlines count as spaces.
         */

        for (int i = 0; i < length; i++) {
            char c = ch[i + start];

            if (c == ' ' || c == '\n') {
                char pred;
                int len = sb.length();

                if (len == 0) {
                    len = mSpannableStringBuilder.length();

                    if (len == 0) {
                        pred = '\n';
                    } else {
                        pred = mSpannableStringBuilder.charAt(len - 1);
                    }
                } else {
                    pred = sb.charAt(len - 1);
                }

                if (pred != ' ' && pred != '\n') {
                    sb.append(' ');
                }
            } else {
                sb.append(c);
            }
        }

        mSpannableStringBuilder.append(sb);
    }

    public void ignorableWhitespace(char ch[], int start, int length) throws SAXException {
    }

    public void processingInstruction(String target, String data) throws SAXException {
    }

    public void skippedEntity(String name) throws SAXException {
    }

    private static class Bold { }
    private static class Italic { }
    private static class Underline { }
    private static class Big { }
    private static class Small { }
    private static class Monospace { }
    private static class Super { }
    private static class Sub { }
    private static class Strike { }
    private static class Aibquote { }
    private static class Aibspoiler { }

    private static class Font {
        public String mColor;
        public String mFace;
        public String mStyle;

        public Font(String color, String face, String style) {
            mColor = color;
            mFace = face;
            mStyle = style;
        }
    }

    private static class Href {
        public String mHref;

        public Href(String href) {
            mHref = href;
        }
    }

    private static class Header {
        private int mLevel;

        public Header(int level) {
            mLevel = level;
        }
    }
    
    private static class Blockquote {
        public boolean mIsUnkfunc;
        
        public Blockquote(boolean isUnkfunc) {
            mIsUnkfunc = isUnkfunc;
        }
    }
    
    private static class Span {
        private String mStyle;
        private boolean mIsAibquote;
        private boolean mIsAibspoiler;
        private boolean mIsUnderline;
        private boolean mIsStrike;
        
        public Span(String style, boolean isAibquote, boolean isAibspoiler, boolean isUnderline, boolean isStrike) {
            mStyle = style;
            mIsAibquote = isAibquote;
            mIsAibspoiler = isAibspoiler;
            mIsUnderline = isUnderline;
            mIsStrike = isStrike;
        }
    }
    
    private static class UlTag {}
    private static class OlTag {
        public int curIndex = 1;
    }
}

//скрытый метод android.graphics.Color#getHtmlColor
class ColorHidden {
    /**
     * Converts an HTML color (named or numeric) to an integer RGB value.
     *
     * @param color Non-null color string.
     *
     * @return A color value, or {@code -1} if the color string could not be interpreted.
     */
    public static int getHtmlColor(String color) {
        Integer i = sColorNameMap.get(color.toLowerCase(Locale.US));
        if (i != null) {
            return i;
        } else {
            try {
                return xmlUtils_convertValueToInt(color, -1);
            } catch (NumberFormatException nfe) {
                return -1;
            }
        }
    }

    private static final HashMap<String, Integer> sColorNameMap;

    static {
        sColorNameMap = new HashMap<String, Integer>();
        sColorNameMap.put("black", Color.BLACK);
        sColorNameMap.put("darkgray", Color.DKGRAY);
        sColorNameMap.put("gray", Color.GRAY);
        sColorNameMap.put("lightgray", Color.LTGRAY);
        sColorNameMap.put("white", Color.WHITE);
        sColorNameMap.put("red", Color.RED);
        sColorNameMap.put("green", Color.GREEN);
        sColorNameMap.put("blue", Color.BLUE);
        sColorNameMap.put("yellow", Color.YELLOW);
        sColorNameMap.put("cyan", Color.CYAN);
        sColorNameMap.put("magenta", Color.MAGENTA);
        sColorNameMap.put("aqua", 0xFF00FFFF);
        sColorNameMap.put("fuchsia", 0xFFFF00FF);
        sColorNameMap.put("darkgrey", Color.DKGRAY);
        sColorNameMap.put("grey", Color.GRAY);
        sColorNameMap.put("lightgrey", Color.LTGRAY);
        sColorNameMap.put("lime", 0xFF00FF00);
        sColorNameMap.put("maroon", 0xFF800000);
        sColorNameMap.put("navy", 0xFF000080);
        sColorNameMap.put("olive", 0xFF808000);
        sColorNameMap.put("purple", 0xFF800080);
        sColorNameMap.put("silver", 0xFFC0C0C0);
        sColorNameMap.put("teal", 0xFF008080);
    }
    
    public static final int xmlUtils_convertValueToInt(CharSequence charSeq, int defaultValue) {
        if (null == charSeq)
            return defaultValue;

        String nm = charSeq.toString();

        // XXX This code is copied from Integer.decode() so we don't
        // have to instantiate an Integer!

        @SuppressWarnings("unused")
        int value;
        int sign = 1;
        int index = 0;
        int len = nm.length();
        int base = 10;

        if ('-' == nm.charAt(0)) {
            sign = -1;
            index++;
        }

        if ('0' == nm.charAt(index)) {
            //  Quick check for a zero by itself
            if (index == (len - 1))
                return 0;

            char    c = nm.charAt(index + 1);

            if ('x' == c || 'X' == c) {
                index += 2;
                base = 16;
            } else {
                index++;
                base = 8;
            }
        }
        else if ('#' == nm.charAt(index))
        {
            index++;
            base = 16;
        }

        return Integer.parseInt(nm.substring(index), base) * sign;
    }
    
}
