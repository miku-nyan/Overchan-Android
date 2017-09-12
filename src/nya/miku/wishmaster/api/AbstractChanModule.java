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

package nya.miku.wishmaster.api;

import java.io.InputStream;
import java.io.OutputStream;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.CryptoUtils;
import nya.miku.wishmaster.api.util.LazyPreferences;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.client.ExtendedHttpClient;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONObject;
import cz.msebera.android.httpclient.HttpHost;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.cookie.Cookie;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.Preference.OnPreferenceChangeListener;
import android.text.InputFilter;
import android.text.InputType;

public abstract class AbstractChanModule implements HttpChanModule {
    private static final String TAG = "AbstractChanModule";
    
    private static final String preferenceKeySplit = "_";
    
    protected static final String DEFAULT_PROXY_HOST = "127.0.0.1";
    protected static final String DEFAULT_PROXY_PORT = "8118";
    
    protected static final String PREF_KEY_USE_PROXY = "PREF_KEY_USE_PROXY";
    protected static final String PREF_KEY_PROXY_HOST = "PREF_KEY_PROXY_HOST";
    protected static final String PREF_KEY_PROXY_PORT = "PREF_KEY_PROXY_PORT";
    protected static final String PREF_KEY_PASSWORD = "PREF_KEY_PASSWORD";
    protected static final String PREF_KEY_USE_HTTPS = "PREF_KEY_USE_HTTPS";
    protected static final String PREF_KEY_ONLY_NEW_POSTS = "PREF_KEY_ONLY_NEW_POSTS";
    protected static final String PREF_KEY_CAPTCHA_AUTO_UPDATE = "PREF_KEY_CAPTCHA_AUTO_UPDATE";

    /**
     * Основной HTTP-клиент
     */
    protected ExtendedHttpClient httpClient;
    /**
     * Объект ресурсов
     */
    protected final Resources resources;
    /**
     * Объект общих параметров.
     * При установке/чтении параметров (в т.ч. добавлении на экран настроек), во избежание конфликта между параметрами разных модулей,
     * используйте ключи, полученные методом {@link #getSharedKey(String)}
     */
    protected final SharedPreferences preferences;
    
    /**
     * Слушатель, следящий за изменением настроек, при изменении которых необходимо обновить (создать новый) HTTP клиент
     */
    private OnPreferenceChangeListener updateHttpListener = new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            boolean useProxy = preferences.getBoolean(getSharedKey(PREF_KEY_USE_PROXY), false);
            String proxyHost = preferences.getString(getSharedKey(PREF_KEY_PROXY_HOST), DEFAULT_PROXY_HOST);
            String proxyPort = preferences.getString(getSharedKey(PREF_KEY_PROXY_PORT), DEFAULT_PROXY_PORT);
            
            if (preference.getKey().equals(getSharedKey(PREF_KEY_USE_PROXY))) {
                useProxy = (boolean)newValue;
                updateHttpClient(useProxy, proxyHost, proxyPort);
                return true;
            } else if (preference.getKey().equals(getSharedKey(PREF_KEY_PROXY_HOST))) {
                if (!proxyHost.equals((String)newValue)) {
                    proxyHost = (String)newValue;
                    updateHttpClient(useProxy, proxyHost, proxyPort);
                }
                return true;
            } else if (preference.getKey().equals(getSharedKey(PREF_KEY_PROXY_PORT))) {
                if (!proxyPort.equals((String)newValue)) {
                    proxyPort = (String)newValue;
                    updateHttpClient(useProxy, proxyHost, proxyPort);
                }
                return true;
            }
            
            return false;
        }
    };
    
    public AbstractChanModule(SharedPreferences preferences, Resources resources) {
        this.preferences = preferences;
        this.resources = resources;
        updateHttpClient(
                preferences.getBoolean(getSharedKey(PREF_KEY_USE_PROXY), false),
                preferences.getString(getSharedKey(PREF_KEY_PROXY_HOST), DEFAULT_PROXY_HOST),
                preferences.getString(getSharedKey(PREF_KEY_PROXY_PORT), DEFAULT_PROXY_PORT));
    }
    
    /**
     * Получить ключ общих параметров, относящийся конкретно к данному модулю (ChanModule)
     * @param key внутренний ключ параметра
     * @return ключ общих параметров
     */
    protected final String getSharedKey(String key) {
        return getChanName() + preferenceKeySplit + key;
    }
    
    /**
     * Обновить (создать новый) HTTP-клиент
     * @param useProxy использовать прокси
     * @param proxyHost адрес прокси-сервера, если useProxy true
     * @param proxyPort порт прокси-сервера, если useProxy true
     */
    private void updateHttpClient(boolean useProxy, String proxyHost, String proxyPort) {
        HttpHost proxy = null;
        if (useProxy) {
            try {
                int port = Integer.parseInt(proxyPort);
                proxy = new HttpHost(proxyHost, port);
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
        }
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
        }
        httpClient = new ExtendedHttpClient(proxy);
        initHttpClient();
    }
    
    /**
     * Метод вызывается после создания (нового) объекта HttpClient, эта реализация не делает ничего (пустой метод).
     * Может быть переопределёт в подклассе, например, устанавливать сохранённые cookies.
     * Необходимо учитывать, что метод вызывается в т.ч. из конструктора, поэтому не следует использовать нестатические поля, определённые в подклассе.
     */
    protected void initHttpClient() {}
    
    /**
     * Добавить в группу параметров (на экран/в категорию) новую категорию настроек прокси-сервера
     * @param group группа, на которую добавляются параметры
     */
    protected void addProxyPreferences(PreferenceGroup group) {
        final Context context = group.getContext();
        PreferenceCategory proxyCat = new PreferenceCategory(context); //категория настроек прокси
        proxyCat.setTitle(R.string.pref_cat_proxy);
        group.addPreference(proxyCat);
        CheckBoxPreference useProxyPref = new LazyPreferences.CheckBoxPreference(context); //чекбокс "использовать ли прокси вообще"
        useProxyPref.setTitle(R.string.pref_use_proxy);
        useProxyPref.setSummary(R.string.pref_use_proxy_summary);
        useProxyPref.setKey(getSharedKey(PREF_KEY_USE_PROXY));
        useProxyPref.setDefaultValue(false);
        useProxyPref.setOnPreferenceChangeListener(updateHttpListener);
        proxyCat.addPreference(useProxyPref);
        EditTextPreference proxyHostPref = new LazyPreferences.EditTextPreference(context); //поле ввода адреса прокси-сервера
        proxyHostPref.setTitle(R.string.pref_proxy_host);
        proxyHostPref.setDialogTitle(R.string.pref_proxy_host);
        proxyHostPref.setSummary(R.string.pref_proxy_host_summary);
        proxyHostPref.setKey(getSharedKey(PREF_KEY_PROXY_HOST));
        proxyHostPref.setDefaultValue(DEFAULT_PROXY_HOST);
        proxyHostPref.getEditText().setSingleLine();
        proxyHostPref.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        proxyHostPref.setOnPreferenceChangeListener(updateHttpListener);
        proxyCat.addPreference(proxyHostPref);
        proxyHostPref.setDependency(getSharedKey(PREF_KEY_USE_PROXY));
        EditTextPreference proxyHostPort = new LazyPreferences.EditTextPreference(context); //поле ввода порта прокси-сервера
        proxyHostPort.setTitle(R.string.pref_proxy_port);
        proxyHostPort.setDialogTitle(R.string.pref_proxy_port);
        proxyHostPort.setSummary(R.string.pref_proxy_port_summary);
        proxyHostPort.setKey(getSharedKey(PREF_KEY_PROXY_PORT));
        proxyHostPort.setDefaultValue(DEFAULT_PROXY_PORT);
        proxyHostPort.getEditText().setSingleLine();
        proxyHostPort.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
        proxyHostPort.setOnPreferenceChangeListener(updateHttpListener);
        proxyCat.addPreference(proxyHostPort);
        proxyHostPort.setDependency(getSharedKey(PREF_KEY_USE_PROXY));
    }
    
    /**
     * Добавить в группу параметров (на экран/в категорию) параметр задания пароля для удаления постов/файлов
     * @param group группа, на которую добавляется параметр
     */
    protected void addPasswordPreference(PreferenceGroup group) {
        final Context context = group.getContext();
        EditTextPreference passwordPref = new EditTextPreference(context) {
            @Override
            protected void showDialog(Bundle state) {
                if (createPassword()) {
                    setText(getDefaultPassword());
                }
                super.showDialog(state);
            }
        };
        passwordPref.setTitle(R.string.pref_password_title);
        passwordPref.setDialogTitle(R.string.pref_password_title);
        passwordPref.setSummary(R.string.pref_password_summary);
        passwordPref.setKey(getSharedKey(PREF_KEY_PASSWORD));
        passwordPref.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        passwordPref.getEditText().setSingleLine();
        passwordPref.getEditText().setFilters(new InputFilter[] { new InputFilter.LengthFilter(255) });
        group.addPreference(passwordPref);
    }
    
    @Override
    public HttpClient getHttpClient() {
        return httpClient;
    }
    
    @Override
    public void saveCookie(Cookie cookie) {
        if (cookie != null) {
            httpClient.getCookieStore().addCookie(cookie);
        }
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        addPasswordPreference(preferenceGroup);
        addProxyPreferences(preferenceGroup);
    }
    
    /**
     * Добавить в группу параметров (на экран/в категорию) настройку выбора HTTPS (защищённого соединения).
     * Для хранения используется ключ общих параметров {@link #PREF_KEY_USE_HTTPS} ({@link #getSharedKey(String)}).
     * См. также: {@link #useHttps(boolean)} - для получения значения параметра.
     * @param group группа, на которую добавляется параметр
     * @param defaultValue значение параметра по умолчанию
     * return объект {@link CheckBoxPreference} с параметром
     */
    protected CheckBoxPreference addHttpsPreference(PreferenceGroup group, boolean defaultValue) {
        final Context context = group.getContext();
        CheckBoxPreference httpsPref = new LazyPreferences.CheckBoxPreference(context);
        httpsPref.setTitle(R.string.pref_use_https);
        httpsPref.setSummary(R.string.pref_use_https_summary);
        httpsPref.setKey(getSharedKey(PREF_KEY_USE_HTTPS));
        httpsPref.setDefaultValue(defaultValue);
        group.addPreference(httpsPref);
        return httpsPref;
    }
    
    /**
     * Определить значение параметра использования HTTPS (защищённого соединения) из ключа общих настроек {@link #PREF_KEY_USE_HTTPS}.
     * Настройка добавляется на экран (в группу) настроек методом {@link #addHttpsPreference(PreferenceGroup, boolean)}.
     * @param defaultValue значение параметра по умолчанию
     * @return значение параметра
     */
    protected boolean useHttps(boolean defaultValue) {
        return preferences.getBoolean(getSharedKey(PREF_KEY_USE_HTTPS), defaultValue);
    }
    
    /**
     * Добавить в группу параметров (на экран/в категорию) настройку выбора использования инкрементальной загрузки (загрузки только новых постов).
     * Для хранения используется ключ общих параметров {@link #PREF_KEY_ONLY_NEW_POSTS} ({@link #getSharedKey(String)}).
     * См. также: {@link #loadOnlyNewPosts(boolean)} - для получения значения параметра.
     * @param group группа, на которую добавляется параметр
     * @param defaultValue значение параметра по умолчанию
     * return объект {@link CheckBoxPreference} с параметром
     */
    protected CheckBoxPreference addOnlyNewPostsPreference(PreferenceGroup group, boolean defaultValue) {
        final Context context = group.getContext();
        CheckBoxPreference onlyNewPostsPref = new LazyPreferences.CheckBoxPreference(context);
        onlyNewPostsPref.setTitle(R.string.pref_only_new_posts);
        onlyNewPostsPref.setSummary(R.string.pref_only_new_posts_summary);
        onlyNewPostsPref.setKey(getSharedKey(PREF_KEY_ONLY_NEW_POSTS));
        onlyNewPostsPref.setDefaultValue(defaultValue);
        group.addPreference(onlyNewPostsPref);
        return onlyNewPostsPref;
    }
    
    /**
     * Определить значение параметра использования инкрементальной загрузки из ключа общих настроек {@link #PREF_KEY_ONLY_NEW_POSTS}.
     * Настройка добавляется на экран (в группу) настроек методом {@link #addOnlyNewPostsPreference(PreferenceGroup, boolean)}.
     * @param defaultValue значение параметра по умолчанию
     * @return значение параметра
     */
    protected boolean loadOnlyNewPosts(boolean defaultValue) {
        return preferences.getBoolean(getSharedKey(PREF_KEY_ONLY_NEW_POSTS), defaultValue);
    }
    
    private boolean createPassword() {
        if (!preferences.contains(getSharedKey(PREF_KEY_PASSWORD))) {
            preferences.edit().putString(getSharedKey(PREF_KEY_PASSWORD), CryptoUtils.genPassword()).commit();
            return true;
        }
        return false;
    }
    
    @Override
    public String getDefaultPassword() {
        createPassword();
        return preferences.getString(getSharedKey(PREF_KEY_PASSWORD), "");
    }
    
    @Override
    public String fixRelativeUrl(String url) {
        if (url == null) return null;
        if ((Uri.parse(url).getScheme() != null) && !Uri.parse(url).getScheme().contains("/")) return url; //RFC 3986
        UrlPageModel model = new UrlPageModel();
        model.chanName = getChanName();
        model.type = UrlPageModel.TYPE_OTHERPAGE;
        model.otherPath = url;
        return buildUrl(model);
    }
    
    @Override
    public ThreadModel[] getCatalog(
            String boardName, int catalogType, ProgressListener listener, CancellableTask task, ThreadModel[] oldList) throws Exception {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public PostModel[] search(String boardName, String searchRequest, ProgressListener listener, CancellableTask task) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public PostModel[] search(String boardName, String searchRequest, int page, ProgressListener listener, CancellableTask task) throws Exception {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        return null;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public String deletePost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public String reportPost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public void downloadFile(String url, OutputStream out, ProgressListener listener, CancellableTask task) throws Exception {
        String fixedUrl = fixRelativeUrl(url);
        HttpStreamer.getInstance().downloadFileFromUrl(fixedUrl, out, HttpRequestModel.DEFAULT_GET, httpClient, listener, task, false);
    }
    
    /**
     * Скачать JSON-объект по ссылке
     * @param url абсолютный URL
     * @param checkIfModidied не загружать, если данные не изменились с прошлого запроса (HTTP 304), в этом случае вернёт null
     * @param listener интерфейс отслеживания прогресса (может принимать null)
     * @param task задача, отмена которой прервёт поток (может принимать null)
     * @return объект JSONObject, или NULL, если страница не была изменена (HTTP 304)
     */
    protected JSONObject downloadJSONObject(String url, boolean checkIfModidied, ProgressListener listener, CancellableTask task) throws Exception {
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModidied).build();
        JSONObject object = HttpStreamer.getInstance().getJSONObjectFromUrl(url, rqModel, httpClient, listener, task, false);
        if (task != null && task.isCancelled()) throw new Exception("interrupted");
        if (listener != null) listener.setIndeterminate();
        return object;
    }
    
    /**
     * Скачать JSON-массив по ссылке
     * @param url абсолютный URL
     * @param checkIfModidied не загружать, если данные не изменились с прошлого запроса (HTTP 304), в этом случае вернёт null
     * @param listener интерфейс отслеживания прогресса (может принимать null)
     * @param task задача, отмена которой прервёт поток (может принимать null)
     * @return объект JSONArray, или NULL, если страница не была изменена (HTTP 304)
     */
    protected JSONArray downloadJSONArray(String url, boolean checkIfModidied, ProgressListener listener, CancellableTask task) throws Exception {
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModidied).build();
        JSONArray array = HttpStreamer.getInstance().getJSONArrayFromUrl(url, rqModel, httpClient, listener, task, false);
        if (task != null && task.isCancelled()) throw new Exception("interrupted");
        if (listener != null) listener.setIndeterminate();
        return array;
    }
    
    /**
     * Загрузить капчу по ссылке.
     * Использется GET-запрос по умолчанию, код состояния HTTP не учитывается (загружается, даже если сервер не вернул HTTP 200).
     * Тип модели капчи устанавливается: {@link CaptchaModel#TYPE_NORMAL} - допустимы все символы (а не только цифры).
     * @param captchaUrl абсолютный URL
     * @param listener интерфейс отслеживания прогресса (может принимать null)
     * @param task задача, отмена которой прервёт поток (может принимать null)
     * @return объект CaptchaModel с загруженной картинкой и типом {@link CaptchaModel#TYPE_NORMAL}
     */
    protected CaptchaModel downloadCaptcha(String captchaUrl, ProgressListener listener, CancellableTask task) throws Exception {
        Bitmap captchaBitmap = null;
        HttpRequestModel requestModel = HttpRequestModel.DEFAULT_GET;
        HttpResponseModel responseModel = HttpStreamer.getInstance().getFromUrl(captchaUrl, requestModel, httpClient, listener, task);
        try {
            InputStream imageStream = responseModel.stream;
            captchaBitmap = BitmapFactory.decodeStream(imageStream);
        } finally {
            responseModel.release();
        }
        CaptchaModel captchaModel = new CaptchaModel();
        captchaModel.type = CaptchaModel.TYPE_NORMAL;
        captchaModel.bitmap = captchaBitmap;
        return captchaModel;
    }

    //TODO: Write the documentation
    protected void addCaptchaAutoUpdatePreference(PreferenceGroup group) {
        final Context context = group.getContext();
        CheckBoxPreference captchaAutoUpdatePreference = new LazyPreferences.CheckBoxPreference(context);
        captchaAutoUpdatePreference.setTitle(R.string.pref_captcha_auto_update);
        captchaAutoUpdatePreference.setKey(getSharedKey(PREF_KEY_CAPTCHA_AUTO_UPDATE));
        captchaAutoUpdatePreference.setDefaultValue(false);
        group.addPreference(captchaAutoUpdatePreference);
    }

    //TODO: Write the documentation
    public boolean getCaptchaAutoUpdatePreference(){
        return false;
    }

}
