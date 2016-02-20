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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;

/**
 * Класс инкапсулирует работу с ZIP архивом (создание/модификация).<br>
 * Если файл существует, все файлы, кроме отдельного списка, будут скопированы в новый архив
 * @author miku-nyan
 *
 */
public class WriteableZip extends WriteableContainer {
    private static final String TAG = "WriteableZip";
    
    private Set<String> files;
    private File outputFile;
    private FileOutputStream outputFileStream;
    private ZipOutputStream output;
    private String filePath;
    private boolean streamIsOpened;
    private boolean objectCancelled = false;
    
    public WriteableZip(File file) throws IOException {
        filePath = file.getAbsolutePath();
        files = new HashSet<String>();
        outputFile = new File(filePath + ".tmp");
        outputFileStream = new FileOutputStream(outputFile);
        output = new ZipOutputStream(outputFileStream);
        output.setMethod(ZipOutputStream.DEFLATED);
        output.setLevel(Deflater.NO_COMPRESSION);
        streamIsOpened = false;
    }
    
    @Override
    public void transfer(String[] doNotCopy, CancellableTask task) throws IOException {
        if (streamIsOpened) throw new IllegalStateException("stream is already opened");
        if (doNotCopy == null) doNotCopy = new String[0];
        File sourceFile = new File(filePath);
        if (sourceFile.exists()) {
            ZipFile source = null;
            try {
                source = new ZipFile(sourceFile);
                for (Enumeration<? extends ZipEntry> entries = source.entries(); entries.hasMoreElements(); ) {
                    if (task.isCancelled()) {
                        cancel();
                        throw new InterruptedException();
                    }
                    
                    ZipEntry current = entries.nextElement();
                    
                    boolean skip = false;
                    for (String exclfile : doNotCopy) {
                        if (current.getName().equalsIgnoreCase(exclfile)) {
                            skip = true;
                            break;
                        }
                    }
                    if (skip || hasFile(current.getName())) continue;
                    
                    files.add(current.getName());
                    output.putNextEntry(new ZipEntry(current.getName()));
                    InputStream in = IOUtils.modifyInputStream(source.getInputStream(current), null, task);
                    try {
                        IOUtils.copyStream(in, output);
                    } catch (IOException e) {
                        Logger.e(TAG, "cannot transfer file "+current.getName(), e);
                    } finally {
                        IOUtils.closeQuietly(in);
                        output.closeEntry();
                    }
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) throw new IOException();
                Logger.e(TAG, e);
                if (e instanceof IOException && IOUtils.isENOSPC(e)) {
                    cancel();
                    throw (IOException) e;
                }
            } finally {
                if (source != null) {
                    try {
                        source.close();
                    } catch (Exception e) {
                        Logger.e(TAG, e);
                    }
                }
            }
        }
    }
    
    @Override
    public boolean hasFile(String filename) {
        return files.contains(filename);
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
    public void close() throws IOException {
        if (objectCancelled) return;
        try {
            output.close();
        } catch (Exception e) {
            Logger.e(TAG, e);
            outputFileStream.close();
            outputFile.delete();
            throw e;
        }
        File old = new File(filePath);
        old.delete();
        if (!outputFile.renameTo(old)) throw new IOException("cannot rename temp file");
    }
    
    @Override
    public OutputStream openStream(String filename) throws IOException {
        if (files.contains(filename)) throw new IllegalStateException("file already exists");
        if (streamIsOpened) throw new IllegalStateException("stream is already opened");
        files.add(filename);
        output.putNextEntry(new ZipEntry(filename));
        streamIsOpened = true;
        return new InzipOutputStream();
    }
        
    private class InzipOutputStream extends OutputStream {
        @Override
        public void close() throws IOException {
            streamIsOpened = false;
            output.closeEntry();
        }
        @Override
        public void flush() throws IOException {
            output.flush();
        }
        @Override
        public void write(int oneByte) throws IOException {
            output.write(oneByte);
        }
        @Override
        public void write(byte[] buffer) throws IOException {
            output.write(buffer);
        }
        @Override
        public void write(byte[] buffer, int offset, int count) throws IOException {
            output.write(buffer, offset, count);
        }
    }
}
