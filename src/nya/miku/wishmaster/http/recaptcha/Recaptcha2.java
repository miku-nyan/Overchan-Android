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

package nya.miku.wishmaster.http.recaptcha;

import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;

import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * Модель рекапчи 2.0, работает в режиме fallback (noscript).<br>
 * Чтобы получить новую капчу, используйте метод {@link #obtain(String, CancellableTask, HttpClient, String)}.<br>
 * Чтобы проверить капчу, используйте метод {@link #checkCaptcha(String, CancellableTask)}.
 * @author miku-nyan
 *
 */

/* Google пометила все классы и интерфейсы пакета org.apache.http как "deprecated" в API 22 (Android 5.1)
 * На самом деле используется актуальная версия apache-hc httpclient 4.3.5.1-android
 * Подробности: https://issues.apache.org/jira/browse/HTTPCLIENT-1632 */
@SuppressWarnings("deprecation")

public class Recaptcha2 {
    // сюда передавать открытый ключ
    private static final String RECAPTCHA_FALLBACK_URL = "://www.google.com/recaptcha/api/fallback?k=";
    // сюда передавать значение Challenge
    private static final String RECAPTCHA_IMAGE_URL = "://www.google.com/recaptcha/api2/payload?c=";
    
    /** картинка с капчей */
    public Bitmap bitmap;
    /** значение challenge */
    public String challenge;
    
    private HttpClient httpClient;
    private String scheme;
    private String publicKey;
    
    /**
     * Получить новую рекапчу
     * @param publicKey открытый ключ
     * @param task задача, которую можно отменить
     * @param httpClient клиент, используемый для выполнения запроса
     * @param scheme протокол (http или https), если null, по умолчанию "http"
     * @return модель рекапчи
     */
    public static Recaptcha2 obtain(String publicKey, CancellableTask task, HttpClient httpClient, String scheme) throws RecaptchaException {
        try {
            if (scheme == null) scheme = "http";
            Recaptcha2 recaptcha = new Recaptcha2();
            recaptcha.httpClient = httpClient;
            recaptcha.scheme = scheme;
            recaptcha.publicKey = publicKey;
            String response = HttpStreamer.getInstance().getStringFromUrl(scheme + RECAPTCHA_FALLBACK_URL + publicKey,
                    HttpRequestModel.builder().setGET().build(), httpClient, null, task, false);
            Matcher matcher = Pattern.compile("name=\"c\" value=\"([\\w-]+)").matcher(response);
            if (matcher.find()) {
                recaptcha.challenge = matcher.group(1);
                HttpResponseModel responseModel = HttpStreamer.getInstance().getFromUrl(scheme + RECAPTCHA_IMAGE_URL +
                        recaptcha.challenge + "&k=" + publicKey, HttpRequestModel.builder().setGET().build(), httpClient, null, task);
                try {
                    InputStream imageStream = responseModel.stream;
                    recaptcha.bitmap = BitmapFactory.decodeStream(imageStream);
                } finally {
                    responseModel.release();
                }
                return recaptcha;
            } else throw new RecaptchaException("can't parse recaptcha challenge answer");
        } catch (Exception e) {
            if (e instanceof RecaptchaException) {
                throw (RecaptchaException) e;
            } else {
                throw new RecaptchaException(e);
            }
        }
    }
    
    /**
     * Проверить капчу
     * @param answer ответ на капчу
     * @param task отменяемая задача
     * @return в случае успешной проверки - хэш, который дальше нужно передать как параметр "g-recaptcha-response"
     * @throws RecaptchaException если не удалось выполнить проверку
     */
    public String checkCaptcha(String answer, CancellableTask task) throws RecaptchaException {
        try {
            HttpEntity postEntity = ExtendedMultipartBuilder.create().
                    addString("c", this.challenge).
                    addString("response", answer).build();
            String response = HttpStreamer.getInstance().getStringFromUrl(scheme + RECAPTCHA_FALLBACK_URL + publicKey,
                    HttpRequestModel.builder().setPOST(postEntity).build(), httpClient, null, task, false);
            
            String hash = "";
            Matcher matcher = Pattern.compile("fbc-verification-token(?:.*?)<textarea[^>]*>([^<]*)<", Pattern.DOTALL).matcher(response);
            if (matcher.find()) hash = matcher.group(1);
            
            if (hash.length() > 0) {
                return hash;
            } else {
                throw new RecaptchaException("RECAPTCHA: probably the incorrect answer (hash is empty)");
            }
        } catch (Exception e) {
            if (e instanceof RecaptchaException) {
                throw (RecaptchaException)e;
            } else {
                throw new RecaptchaException(e);
            }
        }
    }
    
    private Recaptcha2() {}
}
