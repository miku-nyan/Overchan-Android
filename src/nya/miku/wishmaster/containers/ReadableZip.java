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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Класс инкапсулирует работу с ZIP архивом для чтения
 * @author miku-nyan
 *
 */
public class ReadableZip extends ReadableContainer {
    private final ZipFile zipFile;
    private final Map<String, ZipEntry> files;
    
    public ReadableZip(File file) throws IOException {
        zipFile = new ZipFile(file);
        files = new HashMap<String, ZipEntry>();
        for (Enumeration<? extends ZipEntry> entries = zipFile.entries(); entries.hasMoreElements(); ) {
            ZipEntry current = entries.nextElement();
            files.put(current.getName(), current);
        }
    }
    
    @Override
    public boolean hasFile(String filename) {
        return files.containsKey(filename);
    }
    
    @Override
    public InputStream openStream(String filename) throws IOException {
        ZipEntry entry = files.get(filename);
        if (entry == null) throw new FileNotFoundException();
        return zipFile.getInputStream(entry);
    }
    
    @Override
    public void close() throws IOException {
        zipFile.close();
    }
    
    
}
