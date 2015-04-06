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
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;

import org.apache.http.client.HttpClient;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * Модель рекапчи. Чтобы получить новую капчу, используйте метод obtain.
 * @author miku-nyan
 *
 */

/* Google пометила все классы и интерфейсы пакета org.apache.http как "deprecated" в API 22 (Android 5.1)
 * На самом деле используется актуальная версия apache-hc httpclient 4.3.5.1-android
 * Подробности: https://issues.apache.org/jira/browse/HTTPCLIENT-1632 */
@SuppressWarnings("deprecation")

public class Recaptcha {
    // сюда передавать открытый ключ
    private static final String RECAPTCHA_CHALLENGE_URL = "://www.google.com/recaptcha/api/challenge?k=";
    // сюда передавать значение Challenge
    private static final String RECAPTCHA_IMAGE_URL = "://www.google.com/recaptcha/api/image?c=";
    
    /** картинка с капчей */
    public Bitmap bitmap;
    /** значение challenge */
    public String challenge;
    
    /**
     * Получить новую рекапчу
     * @param publicKey открытый ключ
     * @param task задача, которую можно отменить
     * @param httpClient клиент, используемый для выполнения запроса
     * @param scheme протокол (http или https), если null, по умолчанию "http"
     * @return модель рекапчи
     */
    public static Recaptcha obtain(String publicKey, CancellableTask task, HttpClient httpClient, String scheme) throws RecaptchaException {
        try {
            if (scheme == null) scheme = "http";
            Recaptcha recaptcha = new Recaptcha();
            String response = HttpStreamer.getInstance().getStringFromUrl(scheme + RECAPTCHA_CHALLENGE_URL + publicKey,
                    HttpRequestModel.builder().setGET().build(), httpClient, null, task, false);
            Matcher matcher = Pattern.compile("challenge.?:.?'([\\w-]+)'").matcher(response);
            if (matcher.find()) {
                recaptcha.challenge = matcher.group(1);
                HttpResponseModel responseModel = HttpStreamer.getInstance().getFromUrl(scheme + RECAPTCHA_IMAGE_URL + recaptcha.challenge,
                        HttpRequestModel.builder().setGET().build(), httpClient, null, task);
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
                throw (RecaptchaException)e;
            } else {
                throw new RecaptchaException(e);
            }
        }
    }
    
    private Recaptcha() {}
}
