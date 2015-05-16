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

package nya.miku.wishmaster.http;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Random;

import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.common.IOUtils;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;

/**
 * Билдер модифицированных Multipart HttpEntity.
 * Позволяет отслеживать текущий прогресс передачи, или прервать поток.
 * По-другому генерируется boundary (как браузеры, начиная с дефисов, для совместимости с некоторыми бордами).
 * @author miku-nyan
 *
 */

/* Google пометила все классы и интерфейсы пакета org.apache.http как "deprecated" в API 22 (Android 5.1)
 * На самом деле используется актуальная версия apache-hc httpclient 4.3.5.1-android
 * Подробности: https://issues.apache.org/jira/browse/HTTPCLIENT-1632 */
@SuppressWarnings("deprecation")

public class ExtendedMultipartBuilder {
    private static final int RANDOMHASH_TAIL_SIZE = 6;
    
    private static Random random = new Random();
    private static Random getRandom() {
        if (random == null) random = new Random();
        return random;
    }
    
    private final MultipartEntityBuilder builder;
    private ProgressListener listener = null;
    private CancellableTask task = null;
    
    public ExtendedMultipartBuilder() {
        builder = MultipartEntityBuilder.create().setMode(HttpMultipartMode.BROWSER_COMPATIBLE).setBoundary(generateBoundary()).
                setCharset(Charset.forName("UTF-8"));
    }
    
    public static ExtendedMultipartBuilder create() {
        return new ExtendedMultipartBuilder();
    }
    
    public ExtendedMultipartBuilder addPart(String key, ContentBody body) {
        builder.addPart(key, body);
        return this;
    }
    
    /**
     * Добавить строку в кодировке UTF-8
     * @param key имя (ключ)
     * @param value строка
     * @return этот объект
     */
    public ExtendedMultipartBuilder addString(String key, String value) {
        return addPart(key, new StringBody(value, ContentType.create("text/plain", "UTF-8")));
    }
    
    private ExtendedMultipartBuilder addFile(String key, File value, final int randomTail) {
        return addPart(key, new FileBody(value) {
            @Override
            public long getContentLength() {
                return super.getContentLength() + randomTail;
            }
            @Override
            public void writeTo(OutputStream out) throws IOException {
                super.writeTo(out);
                if (randomTail > 0) {
                    byte[] buf = new byte[randomTail];
                    getRandom().nextBytes(buf);
                    out.write(buf);
                }
            }
        });
    }
    
    /**
     * Добавить файл
     * @param key имя (ключ)
     * @param file прикрепляемый файл
     * @param uniqueHash если true, добавит в конец файла несколько рандомных байтов, чтобы создать уникальный хэш
     * @return этот объект
     */
    public ExtendedMultipartBuilder addFile(String key, File file, boolean uniqueHash) {
        return addFile(key, file, uniqueHash ? RANDOMHASH_TAIL_SIZE : 0);
    }
    
    /**
     * Добавить файл
     * @param key имя (ключ)
     * @param file прикрепляемый файл
     * @return этот объект
     */
    public ExtendedMultipartBuilder addFile(String key, File file) {
        return addFile(key, file, false);
    }
    
    /**
     * Установить отслеживатель прогресса и отменяемую задачу
     * @param listener интерфейс отслеживания прогресса
     * @param task задача, отмена которой прервёт поток
     * @return этот объект
     */
    public ExtendedMultipartBuilder setDelegates(ProgressListener listener, CancellableTask task) {
        this.listener = listener;
        this.task = task;
        return this;
    }
    
    public HttpEntity build() {
        return new HttpEntityWrapper(builder.build(), listener, task);
    }
    
    protected String generateBoundary() {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i=0; i<27; ++i) stringBuilder.append("-");
        int length = 26 + getRandom().nextInt(4);
        for (int i=0; i<length; ++i) stringBuilder.append(Integer.toString(getRandom().nextInt(10)));
        return stringBuilder.toString();
    }
    
    private static class HttpEntityWrapper implements HttpEntity {
        
        private final HttpEntity entity;
        private final ProgressListener listener;
        private final CancellableTask task;
        
        private HttpEntityWrapper(HttpEntity entity, ProgressListener listener, CancellableTask task) {
            this.entity = entity;
            this.listener = listener;
            this.task = task;
        }
        
        @Override
        public void consumeContent() throws IOException {
            entity.consumeContent();
        }

        @Override
        public InputStream getContent() throws IOException, IllegalStateException {
            return entity.getContent();
        }

        @Override
        public Header getContentEncoding() {
            return entity.getContentEncoding();
        }

        @Override
        public long getContentLength() {
            return entity.getContentLength();
        }

        @Override
        public Header getContentType() {
            return entity.getContentType();
        }

        @Override
        public boolean isChunked() {
            return entity.isChunked();
        }

        @Override
        public boolean isRepeatable() {
            return entity.isRepeatable();
        }

        @Override
        public boolean isStreaming() {
            return entity.isStreaming();
        }

        @Override
        public void writeTo(OutputStream outstream) throws IOException {
            if (listener != null) listener.setMaxValue(this.getContentLength());
            entity.writeTo(IOUtils.modifyOutputStream(outstream, listener, task));
            if (listener != null) listener.setIndeterminate();
        }
        
    }
    
}
