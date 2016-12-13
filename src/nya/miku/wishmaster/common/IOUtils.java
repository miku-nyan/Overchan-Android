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

package nya.miku.wishmaster.common;

import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;

public class IOUtils {
    private static final String TAG = "IOUtils"; 
    
    private IOUtils() {}
    
    /**
     * Копирование данных из потока from в поток to. (буфер 8КБ)
     */
    public static void copyStream(InputStream from, OutputStream to) throws IOException {
        byte data[] = new byte[8192];
        int count;

        while ((count = from.read(data)) != -1) {
            to.write(data, 0, count);
        }
    }
    
    /**
     *  Безопасное (тихое) закрытие {@link Closeable} объекта (исключение поглощается и пишется в log).
     */
    public static void closeQuietly(Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
        }
    }
    
    /**
     * Проверить, если исключение возникло по причине отсутствия свободного места на диске
     * @param e исключение
     */
    public static boolean isENOSPC(Exception e) {
        return isENOSPC(e.getMessage());
    }
    
    /**
     * Проверить, если исключение возникло по причине отсутствия свободного места на диске
     * @param exMessage сообщение исключения ({@link Exception#getMessage()})
     */
    private static boolean isENOSPC(String exMessage) {
        return (exMessage != null && (exMessage.toUpperCase(Locale.US).contains("ENOSPC") || exMessage.equals("No space left on device")));
    }
    
    /**
     * Модифицирует входной поток, добавляет отображение прогресса в ProgressListener и возможность отмены задачи CancellableTask.
     * Может принимать null в качестве одного (или нескольких) параметров, в этом случае данный объект просто не будут привязан.
     * @param in исходный поток
     * @param listener интерфейс отслеживания прогресса
     * @param task задача, отмена которой прервёт поток
     * @return модифицированный поток
     */
    public static InputStream modifyInputStream(InputStream in, ProgressListener listener, CancellableTask task) {
        if (in != null) {
            if (listener != null) in = new ProgressInputStream(in, listener);
            if (task != null) in = new CancellableInputStream(in, task);
        }
        return in;
    }
    
    /**
     * Модифицирует выходной поток, добавляет отображение прогресса в ProgressListener и возможность отмены загрузки CancellableTask.
     * Может принимать null в качестве одного (или нескольких) параметров, в этом случае данный объект просто не будут привязан.
     * @param out исходный поток
     * @param listener интерфейс отслеживания прогресса
     * @param task задача, отмена которой прервёт поток
     * @return модифицированный поток
     */
    public static OutputStream modifyOutputStream(OutputStream out, ProgressListener listener, CancellableTask task) {
        if (out != null) {
            if (listener != null) out = new ProgressOutputStream(out, listener);
            if (task != null) out = new CancellableOutputStream(out, task);
        }
        return out;
    }
    
    /**
     * Модификация входного потока, возможность отмены передачи
     * @author miku-nyan
     *
     */
    public static class CancellableInputStream extends FilterInputStream {
        private final CancellableTask task;

        /**
         * Конструктор класса
         * @param in исходный поток
         * @param task задача, отмена которой прервёт поток
         */
        public CancellableInputStream(InputStream in, CancellableTask task) {
            super(in);
            this.task = task;
        }

        @Override
        public int read() throws IOException {
            if (checkCancelled()) throw new InterruptedStreamException();
            return super.read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            if (checkCancelled()) throw new InterruptedStreamException();
            return super.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (checkCancelled()) throw new InterruptedStreamException();
            return super.read(b, off, len);
        }

        private boolean checkCancelled() {
            return task != null && task.isCancelled();
        }
    }
    
    /**
     * Модификация выходного потока, возможность отмены передачи
     * @author miku-nyan
     *
     */
    public static class CancellableOutputStream extends FilterOutputStream {
        private final CancellableTask task;

        /**
         * Конструктор класса
         * @param out исходный поток
         * @param task задача, отмена которой прервёт поток
         */
        public CancellableOutputStream(OutputStream out, CancellableTask task) {
            super(out);
            this.task = task;
        }

        @Override
        public void write(int b) throws IOException {
            if (checkCancelled()) throw new InterruptedStreamException();;
            super.write(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            if (checkCancelled()) throw new InterruptedStreamException();;
            super.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (checkCancelled()) throw new InterruptedStreamException();;
            super.write(b, off, len);
        }

        private boolean checkCancelled() {
            return task != null && task.isCancelled();
        }
    }
    
    /**
     * Модификация входного потока, передача текущего прогресса передачи слушателю listener
     * @author miku-nyan
     *
     */
    public static class ProgressInputStream extends FilterInputStream {
        private final ProgressListener listener;
        private volatile long totalNumBytesRead;

        /**
         * Конструктор класса
         * @param in исходный поток
         * @param listener интерфейс отслеживания прогресса
         */
        public ProgressInputStream(InputStream in, ProgressListener listener) {
            super(in);
            this.listener = listener;
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b != -1) updateProgress(1);
            return b;
        }

        @Override
        public int read(byte[] b) throws IOException {
            return (int) updateProgress(in.read(b));
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return (int) updateProgress(in.read(b, off, len));
        }

        @Override
        public long skip(long n) throws IOException {
            return updateProgress(in.skip(n));
        }

        private long updateProgress(long numBytesRead) {
            if (numBytesRead > 0) {
                totalNumBytesRead += numBytesRead;
                listener.setProgress(totalNumBytesRead);
            }
            return numBytesRead;
        }
    }
    
    /**
     * Модификация выходного потока, передача текущего прогресса передачи слушателю listener
     * @author miku-nyan
     *
     */
    public static class ProgressOutputStream extends FilterOutputStream {
        private final ProgressListener listener;
        private volatile long totalNumBytesWrite;

        /**
         * Конструктор класса
         * @param out исходный поток
         * @param listener интерфейс отслеживания прогресса
         */
        public ProgressOutputStream(OutputStream out, ProgressListener listener) {
            super(out);
            this.listener = listener;
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            updateProgress(1);
        }

        @Override
        public void write(byte[] b) throws IOException {
            out.write(b);
            updateProgress(b.length);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            updateProgress(len);
        }
        
        private void updateProgress(long numBytesWrite) {
            if (numBytesWrite > 0) {
                totalNumBytesWrite += numBytesWrite;
                listener.setProgress(totalNumBytesWrite);
            }
        }
    }
    
    /**
     * Исключение возбуждается, если задача, взаимодействующая с потоком, была отменена
     * @author miku-nyan
     *
     */
    public static class InterruptedStreamException extends IOException {
        private static final long serialVersionUID = 1L;
    }

    public static class CountingOutputStream extends FilterOutputStream {
        private long size = 0;

        public CountingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] buffer) throws IOException {
            out.write(buffer, 0, buffer.length);
            size += buffer.length;
        }

        @Override
        public void write(byte[] buffer, int offset, int count) throws IOException {
            out.write(buffer, offset, count);
            size += count;
        }

        @Override
        public void write(int oneByte) throws IOException {
            out.write(oneByte);
            size++;
        }

        public long getSize() {
            return this.size;
        }

    }
}
