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

package nya.miku.wishmaster.http.recaptcha;

import java.io.InputStream;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;

import cz.msebera.android.httpclient.client.HttpClient;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * Модель рекапчи. Чтобы получить новую капчу, используйте метод obtain.
 * @author miku-nyan
 *
 */

public class Recaptcha {
    // сюда передавать значение Challenge
    private static final String RECAPTCHA_IMAGE_URL = "://www.google.com/recaptcha/api/image?c=";
    
    private static interface ChallengeGetter { String get(String key, CancellableTask task, HttpClient httpClient, String scheme) throws Exception; }
    private static final ChallengeGetter[] GETTERS = new ChallengeGetter[] {
        new ChallengeGetter() {
            @Override
            public String get(String publicKey, CancellableTask task, HttpClient httpClient, String scheme) throws Exception {
                return RecaptchaAjax.getChallenge(publicKey, task, httpClient, scheme);
            }
        },
        new ChallengeGetter() {
            @Override
            public String get(String publicKey, CancellableTask task, HttpClient httpClient, String scheme) throws Exception {
                return RecaptchaNoscript.getChallenge(publicKey, task, httpClient, scheme);
            }
        },
    };
    
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
        Exception lastException = null;
        if (scheme == null) scheme = "http";
        Recaptcha recaptcha = new Recaptcha();
        for (ChallengeGetter getter : GETTERS) {
            try {
                recaptcha.challenge = getter.get(publicKey, task, httpClient, scheme);
                HttpResponseModel responseModel = null;
                try {
                    responseModel = HttpStreamer.getInstance().getFromUrl(scheme + RECAPTCHA_IMAGE_URL + recaptcha.challenge,
                            HttpRequestModel.builder().setGET().build(), httpClient, null, task);
                    InputStream imageStream = responseModel.stream;
                    recaptcha.bitmap = BitmapFactory.decodeStream(imageStream);
                } finally {
                    if (responseModel != null) responseModel.release();
                }
                if (recaptcha.bitmap != null) return recaptcha;
            } catch (Exception e) {
                lastException = e;
            }
        }
        if (lastException != null) {
            if (lastException instanceof RecaptchaException) {
                throw (RecaptchaException)lastException;
            } else {
                throw new RecaptchaException(lastException);
            }
        } else {
            throw new RecaptchaException("Can't get recaptcha");
        }
    }
    
    private Recaptcha() {}
}
