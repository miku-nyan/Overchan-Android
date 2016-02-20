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

package nya.miku.wishmaster.containers;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.lib.base64.Base64;
import nya.miku.wishmaster.lib.base64.Base64InputStream;

/**
 * Класс инкапсулирует работу с MHTML-файлом для чтения
 * @author miku-nyan
 *
 */
public class ReadableMHTML extends ReadableContainer {
    private final File file;
    private final Map<String, Long> positions;
    
    private static final int SEARCH_META_BUF_SIZE = 8192;
    
    @SuppressWarnings("resource")
    public ReadableMHTML(File file) throws IOException {
        this.file = file;
        this.positions = new HashMap<String, Long>();
        
        byte[] metadataFilter = ("Content-Location: " + WriteableMHTML.BASE_URL + WriteableMHTML.METADATA_FILE).getBytes("UTF-8");
        int buflen = SEARCH_META_BUF_SIZE + metadataFilter.length - 1;
        byte[] buf = new byte[buflen];
        
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            boolean found = false;
            boolean inhead = false;
            for (int tail = 1; !found; ++tail) {
                long position = raf.length() - ((long)SEARCH_META_BUF_SIZE * tail);
                if (position < 0) {
                    if (inhead) throw new IOException("metadata not found");
                    position = 0;
                    inhead = true;
                }
                raf.seek(position);
                raf.read(buf, 0, tail == 1 ? SEARCH_META_BUF_SIZE : buflen);
                
                int curpos = 0;
                for (int i=0; i<buflen; ++i) {
                    if (buf[i] == metadataFilter[curpos]) ++curpos; else curpos = 0;
                    if (curpos == metadataFilter.length) {
                        raf.seek(position+i);
                        found = true;
                        break;
                    }
                }
            }
            InputStream bis = new BufferedInputStream(new RAFInputStream(raf));
            int r;
            boolean eol = false;
            while ((r = bis.read()) != -1) {
                if (r == '\n') {
                    if (eol) break;
                    eol = true;
                } else if (r != '\r') {
                    eol = false;
                }
            }
            DataInputStream dataStream = new DataInputStream(new Base64InputStream(new InputStreamUntilClearLine(bis), Base64.NO_CLOSE));
            if (dataStream.readLong() != WriteableMHTML.METADATA_MAGIC) throw new IOException("wrong metadata");
            
            List<Long> metadata = new ArrayList<Long>();
            
            while(true){
                try {
                    long meta = dataStream.readLong();
                    metadata.add(meta);
                } catch (EOFException e) {
                    IOUtils.closeQuietly(dataStream);
                    break;
                }
            }
            
            for (Long filePos : metadata) {
                raf.seek(filePos);
                bis = new BufferedInputStream(new RAFInputStream(raf));
                byte[] fnfilter = ("Content-Location: " + WriteableMHTML.BASE_URL).getBytes("UTF-8");
                int curpos = 0;
                while ((r = bis.read()) != -1) {
                    if (r == fnfilter[curpos]) ++curpos; else curpos = 0;
                    if (curpos == fnfilter.length) break;
                }
                ByteArrayOutputStream fnbuf = new ByteArrayOutputStream();
                while ((r = bis.read()) != -1) {
                    if (r == '\r' || r == '\n') break; else fnbuf.write(r);
                }
                positions.put(fnbuf.toString("UTF-8"), filePos);
            }
            
        } finally {
            if (raf != null) raf.close();
        }
    }

    @Override
    public boolean hasFile(String filename) {
        return positions.containsKey(filename);
    }

    @Override
    public InputStream openStream(String filename) throws IOException {
        if (!positions.containsKey(filename)) throw new FileNotFoundException();
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        raf.seek(positions.get(filename));
        InputStream bif = new BufferedInputStream(new RAFInputStream(raf));
        int r;
        boolean eol = false;
        while ((r = bif.read()) != -1) {
            if (r == '\n') {
                if (eol) break;
                eol = true;
            } else if (r != '\r') {
                eol = false;
            }
        }
        return new RAFClosingInputStream(new Base64InputStream(new InputStreamUntilClearLine(bif), Base64.NO_CLOSE), raf);
    }

    @Override
    public void close() throws IOException {
        //сам объект не содержит ресурсов, которые следует закрывать, файловые дескрипторы открываются в методе openStream
    }
    
    /**
     * Обёртка входного потока (InputStream), чтение до тех пор, пока не встретится пустая строка.
     * Читает по 1 байту, рекомендуется использовать с буфером ({@link BufferedInputStream}).
     * @author miku
     *
     */
    private class InputStreamUntilClearLine extends InputStream {
        private boolean terminated = false;
        private boolean eol = false;
        private final InputStream in;
        public InputStreamUntilClearLine(InputStream in) {
            this.in = in;
        }
        @Override
        public int read() throws IOException {
            if (terminated) return -1;
            int r = in.read();
            if (r == '\n') {
                if (eol) terminated = true;
                eol = true;
            } else if (r != '\r') {
                eol = false;
            }
            return r;
        }   
    }
    
    /**
     * Входной поток (InputStream), оборачивается RandomAccessFile.
     * Метод {@link #close()} НЕ ЗАКРЫВАЕТ сам файл.
     * @author miku
     *
     */
    private class RAFInputStream extends InputStream {
        private final RandomAccessFile raf;
        public RAFInputStream(RandomAccessFile raf) {
            this.raf = raf;
        }
        @Override
        public int read() throws IOException {
            return raf.read();
        }
        @Override
        public int read(byte[] buffer) throws IOException {
            return raf.read(buffer);
        }
        @Override
        public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
            return raf.read(buffer, byteOffset, byteCount);
        }
        @Override
        public long skip(long byteCount) throws IOException {
            return raf.skipBytes((int) byteCount);
        }
    }
    
    /**
     * Обёртка входного потока (InputStream), который оборачивает RandomAccessFile.
     * При закрытии ({@link #close()}) соответствующий RandomAccessFile закрывается.
     * @author miku
     *
     */
    private class RAFClosingInputStream extends InputStream {
        private final InputStream in;
        private final RandomAccessFile raf;
        public RAFClosingInputStream(InputStream in, RandomAccessFile raf) {
            this.in = in;
            this.raf = raf;
        }
        @Override
        public int read() throws IOException {
            return in.read();
        }
        @Override
        public int read(byte[] buffer) throws IOException {
            return in.read(buffer);
        }
        @Override
        public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
            return in.read(buffer, byteOffset, byteCount);
        }
        @Override
        public int available() throws IOException {
            return in.available();
        }
        @Override
        public long skip(long byteCount) throws IOException {
            return in.skip(byteCount);
        }
        @Override
        public void mark(int readlimit) {
            in.mark(readlimit);
        }
        @Override
        public boolean markSupported() {
            return in.markSupported();
        }
        @Override
        public void close() throws IOException {
            try {
                in.close();
            } finally {
                raf.close();
            }
        }
    }
}
