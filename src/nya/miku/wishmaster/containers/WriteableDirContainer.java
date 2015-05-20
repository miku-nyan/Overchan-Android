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

package nya.miku.wishmaster.containers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

import nya.miku.wishmaster.api.interfaces.CancellableTask;

/**
 * Класс для работы с директорией как с архивом-контейнером (для записи/модификации)
 * @author miku-nyan
 *
 */
public class WriteableDirContainer extends WriteableContainer {
    
    private Set<String> files;
    private File directory;
    private boolean transfered = false;
    
    public WriteableDirContainer(File file) throws IOException {
        if (!file.mkdirs() && !file.isDirectory()) throw new IOException("cannot make directory");
        directory = file;
        files = new HashSet<String>();
    }
    
    @Override
    public void transfer(String[] doNotCopy, CancellableTask task) throws IOException {
        try {
            if (doNotCopy == null) {
                transfered = true;
                return;
            }
            for (String file : doNotCopy) {
                if (task.isCancelled()) throw new IOException();
                if (!files.contains(file)) {
                    File rem = new File(directory, file);
                    if (rem.exists() && !rem.delete()) throw new IOException();
                }
            }
            transfered = true;
        } catch (IOException e) {
            cancel();
            throw e;
        }
    }
    
    @Override
    public OutputStream openStream(String filename) throws IOException {
        if (hasFile(filename)) throw new IllegalStateException("file already exists");
        files.add(filename);
        if (filename.contains("/")) {
            int pathPosition = filename.lastIndexOf('/');
            String subdirname = filename.substring(0, pathPosition);
            String name = filename.substring(pathPosition + 1);
            if (name.length() == 0) throw new IllegalArgumentException("empty file name");
            File subdir = new File(directory, subdirname);
            if (!subdir.mkdirs() && !subdir.isDirectory()) throw new IOException("cannot make directory");
            return new FileOutputStream(new File(subdir, name));
        }
        return new FileOutputStream(new File(directory, filename));
    }
    
    @Override
    public boolean hasFile(String filename) {
        if (files.contains(filename)) return true;
        if (transfered) {
            File file = new File(directory, filename);
            return !file.isDirectory() && file.exists();
        }
        return false;
    }
    
    @Override
    public void close() throws IOException {}
    
    @Override
    public void cancel() {}

}
