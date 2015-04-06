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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Класс для работы с обычной директорией как с архивом-контейнером (для чтения)
 * @author miku-nyan
 *
 */
public class ReadableDirContainer extends ReadableContainer {
    
    private final File directory;
    
    public ReadableDirContainer(File file) {
        directory = file;
    }
    
    @Override
    public boolean hasFile(String filename) {
        File file = new File(directory, filename);
        return !file.isDirectory() && file.exists();
    }

    @Override
    public InputStream openStream(String filename) throws IOException {
        if (!hasFile(filename)) throw new FileNotFoundException();
        return new FileInputStream(new File(directory, filename));
    }

    @Override
    public void close() throws IOException {}

}
