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
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.DateFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.lib.MailDateFormat;
import nya.miku.wishmaster.lib.base64.Base64;
import nya.miku.wishmaster.lib.base64.Base64OutputStream;

/**
 * Класс инкапсулирует работу с MHTML-файлом (создание/модификация).<br>
 * Если файл существует, все файлы, кроме отдельного списка, будут скопированы в новый веб-архив
 * @author miku-nyan
 *
 */
public class WriteableMHTML extends WriteableContainer {
    private static final String TAG = "WriteableMHTML";
    
    private static final String MHT_FROM = "Saved by wishmaster";
    private static final String MHT_SUBJ = "Saved thread";
    private static final DateFormat MHT_DATE_FORMAT = new MailDateFormat();
    
    /** файл, содержащий позиции (в байтах от начала, тип long) всех файлов в контейнере */
    /*package*/ static final String METADATA_FILE = ".metadata";
    /** контрольное значение метаданных */
    /*package*/ static final long METADATA_MAGIC = 39;
    /** основной путь (URL), откуда были "загружены" все файлы */
    /*package*/ static final String BASE_URL = "http://mhtml/";
    
    private Set<String> files;
    private File outputFile;
    private FileOutputStream outputFileStream;
    private CountingOutputStream output;
    private String filePath;
    private String boundary;
    
    private Base64OutputStream base64Stream;
    private boolean base64StreamOpened;
    private boolean objectCancelled = false;
    
    private ByteArrayOutputStream endFileBuffer;
    private DataOutputStream endMetaDataStream;
    
    public WriteableMHTML(File file) throws IOException {
        filePath = file.getAbsolutePath();
        files = new HashSet<String>();
        files.add(METADATA_FILE);
        outputFile = new File(filePath + ".tmp");
        outputFileStream = new FileOutputStream(outputFile);
        output = new CountingOutputStream(new BufferedOutputStream(outputFileStream));
        boundary = genBoundary();
        
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Writer w = new OutputStreamWriter(buf, "UTF-8");
        w.write(String.format(Locale.US, "From: <%s>\r\n", MHT_FROM));
        w.write(String.format(Locale.US, "Subject: %s\r\n", MHT_SUBJ));
        w.write(String.format(Locale.US, "Date: %s\r\n", MHT_DATE_FORMAT.format(System.currentTimeMillis())));
        w.write("MIME-Version: 1.0\r\n");
        w.write("Content-Type: multipart/related;\r\n");
        w.write("\ttype=\"text/html\";\r\n");
        w.write(String.format(Locale.US, "\tboundary=\"%s\"\r\n\r\n", boundary));
        w.write("This is a multi-part message in MIME format.\r\n\r\n");
        w.close();
        output.write(buf.toByteArray());
        
        endFileBuffer = new ByteArrayOutputStream();
        endFileBuffer.write(("--" + boundary + "\r\nContent-Type: application/octet-stream\r\n"
                + "Content-Transfer-Encoding: base64\r\nContent-Location: " + BASE_URL + METADATA_FILE + "\r\n\r\n").getBytes("UTF-8"));
        endMetaDataStream = new DataOutputStream(new Base64OutputStream(endFileBuffer, Base64.NO_CLOSE | Base64.CRLF));
        endMetaDataStream.writeLong(METADATA_MAGIC);
        
        base64StreamOpened = false;
    }
    
    @Override
    public OutputStream openStream(String filename) throws IOException {
        if (files.contains(filename)) throw new IllegalStateException("file already exists: "+filename);
        if (base64StreamOpened) throw new IllegalStateException("stream is already opened");
        files.add(filename);
        endMetaDataStream.writeLong(output.getCount());
        
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Writer w = new OutputStreamWriter(buf, "UTF-8");
        w.write("--");
        w.write(boundary);
        w.write("\r\nContent-Type: ");
        String filename_l = filename.toLowerCase(Locale.US);
        if (filename_l.endsWith(".html") || filename_l.endsWith(".html")) {
            w.write("text/html;\r\n\tcharset=\"utf-8\"");
        } else if (filename_l.endsWith(".css")) {
            w.write("text/css");
        } else if (filename_l.endsWith(".js")) {
            w.write("text/javascript");
        } else if (filename_l.endsWith(".jpg") || filename_l.endsWith(".jpeg")) {
            w.write("image/jpeg");
        } else if (filename_l.endsWith(".png")) {
            w.write("image/png");
        } else if (filename_l.endsWith(".gif")) {
            w.write("image/gif");
        } else if (filename_l.endsWith(".webm")) {
            w.write("video/webm");
        } else if (filename_l.endsWith(".mp4")) {
            w.write("video/mp4");
        } else if (filename_l.endsWith(".ogg")) {
            w.write("audio/ogg");
        } else if (filename_l.endsWith(".mp3")) {
            w.write("audio/mpeg");
        } else {
            w.write("application/octet-stream");
        }
        w.write("\r\nContent-Transfer-Encoding: base64\r\nContent-Location: ");
        w.write(BASE_URL);
        w.write(filename);
        w.write("\r\n\r\n");
        
        w.close();
        output.write(buf.toByteArray());
        
        base64Stream = new Base64OutputStream(output, Base64.NO_CLOSE | Base64.CRLF);
        base64StreamOpened = true;
        return new MHTMLOutputStream();
    }
    
    @Override
    public void close() throws IOException {
        if (objectCancelled) return;
        try {
            endMetaDataStream.close();
            endFileBuffer.write(("\r\n\r\n--" + boundary + "--\r\n").getBytes("UTF-8"));
            endFileBuffer.writeTo(output);
        } finally {
            try {
                output.close();
            } catch (Exception e) {
                Logger.e(TAG, e);
                outputFileStream.close();
                outputFile.delete();
                throw e;
            }
        }
        File old = new File(filePath);
        old.delete();
        if (!outputFile.renameTo(old)) throw new IOException("cannot rename temp file");
    }
    
    @Override
    public void cancel() {
        try {
            output.close();
            outputFile.delete();
        } catch (Exception e) {
            Logger.e(TAG, e);
            try {
                outputFileStream.close();
                outputFile.delete();
            } catch (Exception e1) {
                Logger.e(TAG, e1);
            }
        }
        objectCancelled = true;
    }
    
    @Override
    public boolean hasFile(String arg) {
        return files.contains(arg);
    }
    
    @Override
    public void transfer(String[] doNotCopy, CancellableTask task) throws IOException {
        if (base64StreamOpened) throw new IllegalStateException("stream is already opened");
        if (doNotCopy == null) doNotCopy = new String[0];
        File sourceFile = new File(filePath);
        if (sourceFile.exists()) {
            InputStream in = null;
            try {
                in = new BufferedInputStream(IOUtils.modifyInputStream(new FileInputStream(sourceFile), null, task));
                String srcBoundary = readBoundary(in);
                while (true) {
                    if (task.isCancelled()) {
                        cancel();
                        throw new InterruptedException();
                    }
                    
                    String header = readNextFileHeader(in, srcBoundary);
                    if (header == null) break;
                    
                    String fnfilter = "Content-Location: " + BASE_URL;
                    int fnpos = header.indexOf(fnfilter);
                    if (fnpos == -1) break; else fnpos += fnfilter.length();
                    int fnposEnd = header.indexOf('\r', fnpos);
                    if (fnposEnd == -1) break;
                    
                    String filename = header.substring(fnpos, fnposEnd);
                    if (filename.equals(METADATA_FILE)) break;
                    boolean skip = false;
                    for (String exclfile : doNotCopy) {
                        if (filename.equalsIgnoreCase(exclfile)) {
                            skip = true;
                            break;
                        }
                    }
                    if (skip || hasFile(filename)) continue;
                    
                    endMetaDataStream.writeLong(output.getCount());
                    
                    output.write("--".getBytes("UTF-8"));
                    output.write(boundary.getBytes("UTF-8"));
                    output.write(header.getBytes("UTF-8"));
                    
                    int r;
                    boolean eol = false;
                    byte[] buf = new byte[8192];
                    int bufpos = 0;
                    while ((r = in.read()) != -1) {
                        buf[bufpos++] = (byte) r;
                        if (bufpos == 8192) {
                            output.write(buf, 0, 8192);
                            bufpos = 0;
                        }
                        if (r == '\n') {
                            if (eol) break;
                            eol = true;
                        } else if (r != '\r') {
                            eol = false;
                        }
                    }
                    if (bufpos > 0) output.write(buf, 0, bufpos);
                    
                    files.add(filename);
                }
                
            } catch (Exception e) {
                if (e instanceof InterruptedException) throw new IOException();
                Logger.e(TAG, e);
                if (e instanceof IOException && IOUtils.isENOSPC(e)) {
                    cancel();
                    throw (IOException) e;
                }
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Exception e) {
                        Logger.e(TAG, e);
                    }
                }
            }
        }
    }
    
    private String readBoundary(InputStream input) throws IOException {
        if (!readFilter(input, "boundary=\"".getBytes("UTF-8"))) return null;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int r;
        while ((r = input.read()) != -1) {
            if (r == '\"') break;
            buf.write(r);
        }
        return buf.toString("UTF-8");
    }
    
    private String readNextFileHeader(InputStream input, String boundary) throws IOException {
        byte[] filter = ("--" + boundary).getBytes("UTF-8");
        if (!readFilter(input, filter)) return null;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int r;
        boolean eol = false;
        while ((r = input.read()) != -1) {
            buf.write(r);
            if (r == '\n') {
                if (eol) break;
                eol = true;
            } else if (r != '\r') {
                eol = false;
            }
        }
        return buf.toString("UTF-8");
    }
    
    private boolean readFilter(InputStream input, byte[] filter) throws IOException {
        int r;
        int curPos = 0;
        boolean found = false;
        while ((r = input.read()) != -1) {
            if (r == filter[curPos]) ++curPos;
            else curPos = 0;
            if (curPos == filter.length) {
                found = true;
                break;
            }
        }
        return found;
    }
    
    private String genBoundary() {
        Random random = new Random();
        return "----=_NextPart_000_0000_" + toHex(random.nextInt()) + "." + toHex(random.nextInt());
    }
    
    private String toHex(int i) {
        String t = "00000000" + Integer.toHexString(i);
        return t.substring(t.length() - 8).toUpperCase(Locale.US);
    }
    
    private class MHTMLOutputStream extends OutputStream {
        @Override
        public void close() throws IOException {
            base64StreamOpened = false;
            base64Stream.close();
            output.write(new byte[] { '\r', '\n', '\r', '\n' });
        }
        @Override
        public void flush() throws IOException {
            base64Stream.flush();
        }
        @Override
        public void write(int oneByte) throws IOException {
            base64Stream.write(oneByte);
        }
        @Override
        public void write(byte[] buffer) throws IOException {
            base64Stream.write(buffer);
        }
        @Override
        public void write(byte[] buffer, int offset, int count) throws IOException {
            base64Stream.write(buffer, offset, count);
        }
    }
    
    private class CountingOutputStream extends FilterOutputStream {
        private long count = 0;
        public CountingOutputStream(OutputStream out) {
            super(out);
        }
        @Override
        public void write(int oneByte) throws IOException {
            ++count;
            out.write(oneByte);
        }
        @Override
        public void write(byte[] buffer) throws IOException {
            count += buffer.length;
            out.write(buffer);
        }
        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            count += length;
            out.write(buffer, offset, length);
        }
        public long getCount() {
            return count;
        }
    }
}
