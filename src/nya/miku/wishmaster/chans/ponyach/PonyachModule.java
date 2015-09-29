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

package nya.miku.wishmaster.chans.ponyach;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntityHC4;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.cookie.BasicClientCookieHC4;
import org.apache.http.message.BasicNameValuePair;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractWakabaModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.WakabaReader;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.common.PriorityThreadFactory;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.interactive.InteractiveException;
import nya.miku.wishmaster.http.streamer.HttpRequestException;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;

@SuppressLint("SimpleDateFormat")
@SuppressWarnings("deprecation") //https://issues.apache.org/jira/browse/HTTPCLIENT-1632
public class PonyachModule extends AbstractWakabaModule {
    private static final String TAG = "PonyachModule";
    private static final String CHAN_NAME = "ponyach";
    private static final String DEFAULT_DOMAIN = "ponyach.ru";
    private static final String[] DOMAINS = new String[] { DEFAULT_DOMAIN, "ponychan.ru", "ponya.ch", "ponyach.cf", "ponyach.ga", "ponyach.ml" };
    
    private static final DateFormat DATE_FORMAT;
    static {
        DateFormatSymbols symbols = new DateFormatSymbols();
        symbols.setMonths(new String[] {
                "Янв", "Фев", "Мар", "Апр", "Май", "Июнь", "Июль", "Авг", "Снт", "Окт", "Ноя", "Дек" });
        DATE_FORMAT = new SimpleDateFormat("dd MMMM yyyy HH:mm:ss", symbols);
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
    }
    
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "/b/ - was never good", "", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "d", "Жалобы и предложения", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "tea", "Чайная комната", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "test", "Полигон", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "vg", "игры", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "oc", "Ориджинал контент", "", false),
            //ChanModels.obtainSimpleBoardModel(CHAN_NAME, "r34", "r34", "", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "rf", "Убежище", "", true),
        };
    
    private static final String PREF_KEY_DOMAIN = "PREF_KEY_DOMAIN";
    private static final String PREF_KEY_CAPTCHA_LEVEL = "PREF_KEY_CAPTCHA_LEVEL";
    private static final String PREF_KEY_PHPSESSION_COOKIE = "PREF_KEY_PHPSESSION_COOKIE";
    private static final String PHPSESSION_COOKIE_NAME = "PHPSESSID";
    private static final String CAPTCHATYPE_COOKIE_NAME = "captcha_type";
    
    private static final Pattern ERROR_PATTERN = Pattern.compile("<h2[^>]*>(.*?)</h2>", Pattern.DOTALL);
    
    public PonyachModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Поня.ч";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_ponyach, null);
    }
    
    @Override
    protected String[] getAllDomains() {
        return DOMAINS;
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    protected String getUsingDomain() {
        String domain = preferences.getString(getSharedKey(PREF_KEY_DOMAIN), DEFAULT_DOMAIN);
        return TextUtils.isEmpty(domain) ? DEFAULT_DOMAIN : domain;
    }
    
    @Override
    protected void initHttpClient() {
        super.initHttpClient();
        loadPhpCookies();
    }
    
    
    private void loadPhpCookies() {
        loadPhpCookies(getUsingDomain());
    }

    private void loadPhpCookies(String usingDomain) {
        String phpSessionCookie = preferences.getString(getSharedKey(PREF_KEY_PHPSESSION_COOKIE), null);
        if (phpSessionCookie != null) {
            BasicClientCookieHC4 c = new BasicClientCookieHC4(PHPSESSION_COOKIE_NAME, phpSessionCookie);
            c.setDomain(usingDomain);
            httpClient.getCookieStore().addCookie(c);
        }
    }
    
    private void savePhpCookies() {
        for (Cookie cookie : httpClient.getCookieStore().getCookies()) {
            if (cookie.getName().equalsIgnoreCase(PHPSESSION_COOKIE_NAME) && cookie.getDomain().contains(getUsingDomain())) {
                preferences.edit().putString(getSharedKey(PREF_KEY_PHPSESSION_COOKIE), cookie.getValue()).commit();
            }
        }
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        final Context context = preferenceGroup.getContext();
        ListPreference captchaLevel = new ListPreference(context);
        captchaLevel.setTitle(R.string.ponyach_prefs_captcha);
        captchaLevel.setDialogTitle(R.string.ponyach_prefs_captcha);
        captchaLevel.setKey(getSharedKey(PREF_KEY_CAPTCHA_LEVEL));
        captchaLevel.setEntryValues(new String[] { "3", "2", "1" });
        captchaLevel.setEntries(new String[] { "Easy", "Easy++", "Medium" });
        captchaLevel.setDefaultValue("1");
        preferenceGroup.addPreference(captchaLevel);
        
        EditTextPreference passcodePref = new EditTextPreference(context);
        passcodePref.setTitle(R.string.ponyach_prefs_passcode);
        passcodePref.setDialogTitle(R.string.ponyach_prefs_passcode);
        passcodePref.getEditText().setFilters(new InputFilter[] { new InputFilter.LengthFilter(6) });
        passcodePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final String newPasscode = (String) newValue;
                final CancellableTask passAuthTask = new CancellableTask.BaseCancellableTask();
                final ProgressDialog passAuthProgressDialog = new ProgressDialog(context);
                passAuthProgressDialog.setMessage("Logging in");
                passAuthProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        passAuthTask.cancel();
                    }
                });
                passAuthProgressDialog.setCanceledOnTouchOutside(false);
                passAuthProgressDialog.show();
                PriorityThreadFactory.LOW_PRIORITY_FACTORY.newThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (passAuthTask.isCancelled()) return;
                            String url = getUsingUrl() + "passcode.php";
                            List<BasicNameValuePair> pairs = Collections.singletonList(new BasicNameValuePair("passcode_just_set", newPasscode));
                            HttpRequestModel request = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntityHC4(pairs, "UTF-8")).build();
                            HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, null, passAuthTask, false);
                            savePhpCookies();
                        } catch (final Exception e) {
                            if (context instanceof Activity) {
                                ((Activity) context).runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        String message = e.getMessage() == null ? resources.getString(R.string.error_unknown) : e.getMessage();
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        } finally {
                            passAuthProgressDialog.dismiss();
                        }
                    }
                }).start();
                return false;
            }
        });
        preferenceGroup.addPreference(passcodePref);
        
        ListPreference domainPref = new ListPreference(context);
        domainPref.setTitle(R.string.ponyach_prefs_domain);
        domainPref.setDialogTitle(R.string.ponyach_prefs_domain);
        domainPref.setKey(getSharedKey(PREF_KEY_DOMAIN));
        domainPref.setEntryValues(DOMAINS);
        domainPref.setEntries(DOMAINS);
        domainPref.setDefaultValue(DEFAULT_DOMAIN);
        domainPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                loadPhpCookies((String) newValue);
                return true;
            }
        });
        preferenceGroup.addPreference(domainPref);
        
        CheckBoxPreference httpsPref = new CheckBoxPreference(context);
        httpsPref.setTitle(R.string.pref_use_https);
        httpsPref.setSummary(R.string.pref_use_https_summary);
        httpsPref.setKey(getSharedKey(PREF_KEY_USE_HTTPS));
        httpsPref.setDefaultValue(useHttpsDefaultValue());
        preferenceGroup.addPreference(httpsPref);
        
        addUnsafeSslPreference(preferenceGroup, getSharedKey(PREF_KEY_USE_HTTPS));
        addProxyPreferences(preferenceGroup);
        captchaLevel.setSummary(captchaLevel.getEntry());
        domainPref.setSummary(domainPref.getEntry());
    }
    
    @Override
    protected WakabaReader getWakabaReader(InputStream stream, UrlPageModel urlModel) {
        return new WakabaReader(stream, DATE_FORMAT) {
            private final Pattern aHrefPattern = Pattern.compile("<a\\s+href=\"(.*?)\"", Pattern.DOTALL);
            private final Pattern attachmentSizePattern = Pattern.compile("([\\d\\.]+)KB");
            private final Pattern attachmentPxSizePattern = Pattern.compile("(\\d+)x(\\d+)");
            private final char[] dateFilter = "<span class=\"mobile_date dast-date\">".toCharArray();
            private final char[] attachmentFilter = "<span class=\"filesize fs_".toCharArray();
            private ArrayList<AttachmentModel> myAttachments = new ArrayList<>();
            private int curDatePos = 0;
            private int curAttachmentPos = 0;
            @Override
            protected void customFilters(int ch) throws IOException {
                if (ch == dateFilter[curDatePos]) {
                    ++curDatePos;
                    if (curDatePos == dateFilter.length) {
                        parseDate(readUntilSequence("</span>".toCharArray()).trim());
                        curDatePos = 0;
                    }
                } else {
                    if (curDatePos != 0) curDatePos = ch == dateFilter[0] ? 1 : 0;
                }
                
                if (ch == attachmentFilter[curAttachmentPos]) {
                    ++curAttachmentPos;
                    if (curAttachmentPos == attachmentFilter.length) {
                        skipUntilSequence(">".toCharArray());
                        myParseAttachment(readUntilSequence("</span>".toCharArray()));
                        curAttachmentPos = 0;
                    }
                } else {
                    if (curAttachmentPos != 0) curAttachmentPos = ch == attachmentFilter[0] ? 1 : 0;
                }
            }
            @Override
            protected void parseDate(String date) {
                date = date.substring(date.indexOf(' ') + 1);
                super.parseDate(date);
            }
            private void myParseAttachment(String html) {
                Matcher aHrefMatcher = aHrefPattern.matcher(html);
                if (aHrefMatcher.find()) {
                    AttachmentModel attachment = new AttachmentModel();
                    attachment.path = aHrefMatcher.group(1);
                    attachment.thumbnail = attachment.path.replaceAll("/src/(\\d+)/(?:.*?)\\.(.*?)$", "/thumb/$1s.$2");
                    if (attachment.thumbnail.equals(attachment.path)) {
                        attachment.thumbnail = null; 
                    } else {
                        attachment.thumbnail = attachment.thumbnail.replace(".webm", ".png");
                    }
                    
                    String ext = attachment.path.substring(attachment.path.lastIndexOf('.') + 1);
                    switch (ext) {
                        case "jpg":
                        case "jpeg":
                        case "png":
                            attachment.type = AttachmentModel.TYPE_IMAGE_STATIC;
                            break;
                        case "gif":
                            attachment.type = AttachmentModel.TYPE_IMAGE_GIF;
                            break;
                        case "webm":
                        case "mp4":
                            attachment.type = AttachmentModel.TYPE_VIDEO;
                            break;
                        default:
                            attachment.type = AttachmentModel.TYPE_OTHER_FILE;
                    }
                    
                    Matcher sizeMatcher = attachmentSizePattern.matcher(html);
                    if (sizeMatcher.find()) {
                        try {
                            attachment.size = Math.round(Float.parseFloat(sizeMatcher.group(1)));
                        } catch (Exception e) {
                            attachment.size = -1;
                        }
                        try {
                            Matcher pxSizeMatcher = attachmentPxSizePattern.matcher(html);
                            if (!pxSizeMatcher.find(sizeMatcher.end())) throw new Exception();
                            attachment.width = Integer.parseInt(pxSizeMatcher.group(1));
                            attachment.height = Integer.parseInt(pxSizeMatcher.group(2));
                        } catch (Exception e) {
                            attachment.width = -1;
                            attachment.height = -1;
                        }
                    } else {
                        attachment.size = -1;
                        attachment.width = -1;
                        attachment.height = -1;
                    }
                    
                    myAttachments.add(attachment);
                }
            }
            @Override
            protected void postprocessPost(PostModel post) {
                post.attachments = myAttachments.toArray(new AttachmentModel[myAttachments.size()]);
                myAttachments.clear();
            }
        };
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel board = super.getBoard(shortName, listener, task);
        board.timeZoneId = "GMT+3";
        board.defaultUserName = "Аноним";
        board.uniqueAttachmentNames = false;
        board.readonlyBoard = false;
        board.requiredFileForNewThread = true;
        board.allowDeletePosts = false;
        board.allowDeleteFiles = false;
        board.allowReport = BoardModel.REPORT_NOT_ALLOWED;
        board.allowNames = true;
        board.allowSubjects = true;
        board.allowSage = true;
        board.allowEmails = true;
        board.ignoreEmailIfSage = true;
        board.allowCustomMark = false;
        board.allowRandomHash = true;
        board.allowIcons = false;
        board.attachmentsMaxCount = 5;
        board.markType = BoardModel.MARK_BBCODE;
        return board;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        if (HttpStreamer.getInstance().getStringFromUrl(getUsingUrl() + "haikaptcha.php?m=isndn",
                HttpRequestModel.builder().setGET().build(), httpClient, null, task, false).equals("1")) {
            throw new HaikuCaptchaException();
        }
        
        String url = getUsingUrl() + "board.php";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("board", model.boardName).
                addString("replythread", model.threadNumber == null ? "0" : model.threadNumber).
                addString("name", model.name).
                addString("em", model.sage ? "sage" : model.email).
                addString("subject", model.subject).
                addString("message", model.comment);
        
        int filesCount = model.attachments != null ? model.attachments.length : 0;
        for (int i=0; i<filesCount; ++i) postEntityBuilder.addFile("upload[]", model.attachments[i], model.randomHash);
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 302) {
                for (Header header : response.headers) {
                    if (header != null && HttpHeaders.LOCATION.equalsIgnoreCase(header.getName())) {
                        return fixRelativeUrl(header.getValue());
                    }
                }
            } else if (response.statusCode == 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                Matcher errorMatcher = ERROR_PATTERN.matcher(htmlResponse);
                if (errorMatcher.find()) throw new Exception(errorMatcher.group(1));
            }
            throw new Exception(response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
            savePhpCookies();
        }
    }
    
    private static class HaikuCaptchaException extends InteractiveException {
        private static final long serialVersionUID = 1L;
        
        private static final Pattern IMG_PATTERN = Pattern.compile("<img(.*?)>");
        private static final Pattern SRC_PATTERN = Pattern.compile("src=\"(.*?)\"");
        private static final Pattern HAIKU_PATTERN = Pattern.compile("onclick=\"haiku\\('(.*?)'\\);\"");
        
        @Override
        public String getServiceName() {
            return "Haiku Captcha";
        }
        
        private Bitmap getBitmap(String url, CancellableTask task) throws HttpRequestException {
            PonyachModule thisModule = ((PonyachModule) MainApplication.getInstance().getChanModule(CHAN_NAME));
            String baseUrl = thisModule.getUsingUrl();
            if (url.startsWith("/")) url = baseUrl + url.substring(1);
            HttpClient httpClient = thisModule.httpClient;
            HttpRequestModel requestModel = HttpRequestModel.builder().setGET().build();
            HttpResponseModel responseModel = HttpStreamer.getInstance().getFromUrl(url, requestModel, httpClient, null, task);
            try {
                InputStream imageStream = responseModel.stream;
                return BitmapFactory.decodeStream(imageStream);
            } finally {
                responseModel.release();
            }
        }
        
        private void setCaptchaTypeCookie() {
            PonyachModule thisModule = ((PonyachModule) MainApplication.getInstance().getChanModule(CHAN_NAME));
            String level = thisModule.preferences.getString(thisModule.getSharedKey(PREF_KEY_CAPTCHA_LEVEL), "1");
            BasicClientCookieHC4 cookie = new BasicClientCookieHC4(CAPTCHATYPE_COOKIE_NAME, level);
            cookie.setDomain(thisModule.getUsingDomain());
            thisModule.httpClient.getCookieStore().addCookie(cookie);
        }
        
        @Override
        public void handle(final Activity activity, final CancellableTask task, final Callback callback) {
            try {
                setCaptchaTypeCookie();
                String haiku = HttpStreamer.getInstance().getStringFromUrl(
                        ((PonyachModule) MainApplication.getInstance().getChanModule(CHAN_NAME)).getUsingUrl() + "haikaptcha.php?m=get",
                        HttpRequestModel.builder().setGET().build(),
                        ((PonyachModule) MainApplication.getInstance().getChanModule(CHAN_NAME)).httpClient,
                        null, task, false);
                //Logger.d(TAG, "haiku response: " + haiku);
                if (task.isCancelled()) throw new Exception();
                String captcha = null;
                final List<Pair<String, String>> answers = new ArrayList<>();
                Matcher imgMatcher = IMG_PATTERN.matcher(haiku);
                while (imgMatcher.find()) {
                    String img = imgMatcher.group(1);
                    Matcher srcMatcher = SRC_PATTERN.matcher(img);
                    if (srcMatcher.find()) {
                        String src = srcMatcher.group(1);
                        if (img.contains("onclick=\"haiku()\"")) {
                            captcha = src;
                        } else {
                            Matcher haikuMatcher = HAIKU_PATTERN.matcher(img);
                            if (haikuMatcher.find()) {
                                answers.add(Pair.of(src, haikuMatcher.group(1)));
                            }
                        }
                    }
                }
                if (captcha == null || answers.isEmpty()) throw new Exception();
                
                final Bitmap captchaBmp = getBitmap(captcha, task);
                if (task.isCancelled()) throw new Exception();
                final Bitmap[] answersBmp = new Bitmap[answers.size()];
                for (int i=0; i<answersBmp.length; ++i) {
                    answersBmp[i] = getBitmap(answers.get(i).getLeft(), task);
                    if (task.isCancelled()) throw new Exception();
                }
                
                activity.runOnUiThread(new Runnable() {
                    @SuppressLint("InlinedApi")
                    @Override
                    public void run() {
                        final Dialog dialog = new Dialog(activity);
                        
                        LinearLayout mainLayout = new LinearLayout(activity);
                        mainLayout.setOrientation(LinearLayout.VERTICAL);
                        final ImageView captchaView = new ImageView(activity);
                        captchaView.setLayoutParams(new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                        captchaView.setImageBitmap(captchaBmp);
                        captchaView.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                dialog.dismiss();
                                if (task.isCancelled()) return;
                                PriorityThreadFactory.LOW_PRIORITY_FACTORY.newThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        handle(activity, task, callback);
                                    }
                                }).start();
                            }
                        });
                        mainLayout.addView(captchaView);
                        
                        LinearLayout answersLayout = new LinearLayout(activity);
                        answersLayout.setOrientation(LinearLayout.HORIZONTAL);
                        answersLayout.setWeightSum(answersBmp.length);
                        answersLayout.setLayoutParams(new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                        for (int i=0; i<answersBmp.length; ++i) {
                            ImageView answer = new ImageView(activity);
                            answer.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                            answer.setImageBitmap(answersBmp[i]);
                            answer.setTag(answers.get(i).getRight());
                            answer.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(final View v) {
                                    dialog.dismiss();
                                    if (task.isCancelled()) return;
                                    PriorityThreadFactory.LOW_PRIORITY_FACTORY.newThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            String checkUrl = ((PonyachModule) MainApplication.getInstance().getChanModule(CHAN_NAME)).getUsingUrl() +
                                                    "haikaptcha.php?m=chk&a=" + (String)v.getTag();
                                            HttpRequestModel request = HttpRequestModel.builder().setGET().build();
                                            String response = null;
                                            try {
                                                response = HttpStreamer.getInstance().getStringFromUrl(checkUrl, request,
                                                        ((PonyachModule) MainApplication.getInstance().getChanModule(CHAN_NAME)).httpClient,
                                                        null, task, false);
                                            } catch (Exception e) {
                                                Logger.e(TAG, e);
                                            }
                                            if (task.isCancelled()) return;
                                            try {
                                                if (response != null) {
                                                    Matcher m = Pattern.compile("haiku_wait\\((\\d+)\\)").matcher(response);
                                                    if (m.find()) {
                                                        Integer time = Integer.parseInt(m.group(1));
                                                        Logger.d(TAG, "incorrect");
                                                        Logger.d(TAG, "response: " + response);
                                                        Logger.d(TAG, "waiting " + time + " seconds");
                                                        Thread.sleep(time * 1000);
                                                        throw new Exception("try again");
                                                    }
                                                    ((PonyachModule) MainApplication.getInstance().getChanModule(CHAN_NAME)).savePhpCookies();
                                                    activity.runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            callback.onSuccess();
                                                        }
                                                    });
                                                } else throw new Exception("response == null");
                                            } catch (Exception e) {
                                                Logger.e(TAG, e);
                                                if (task.isCancelled()) return;
                                                handle(activity, task, callback);
                                            }
                                        }
                                    }).start();
                                }
                            });
                            answersLayout.addView(answer);
                        }
                        mainLayout.addView(answersLayout);
                        
                        ScrollView dlgLayout = new ScrollView(activity);
                        dlgLayout.addView(mainLayout);
                        
                        dialog.setTitle("Haiku Captcha");
                        dialog.setContentView(dlgLayout);
                        dialog.setCanceledOnTouchOutside(false);
                        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                if (!task.isCancelled()) callback.onError("Cancelled");
                            }
                        });
                        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                        dialog.show();
                    }
                });
                
            } catch (Exception e) {
                Logger.e(TAG, e);
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError("Error");
                    }
                });
            }
            
        }
        
    }
}
