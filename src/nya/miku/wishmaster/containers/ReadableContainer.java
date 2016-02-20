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

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Абстрактный класс инкапсулирует работу с архивом для чтения
 * @author miku-nyan
 *
 */
public abstract class ReadableContainer implements Closeable {
   
    /**
     * Проверить наличие файла в архиве
     * @param filename имя файла
     */
    public abstract boolean hasFile(String filename);
    
    /**
     * Открыть поток файла для чтения. Если файл отсутствует, возбуждается исключение {@link FileNotFoundException}
     * @param filename имя файла
     * @return поток
     */
    public abstract InputStream openStream(String filename) throws IOException;
    
    /**
     * Завершить работу с архивом
     */
    public abstract void close() throws IOException;
    
    /**
     * Получить экземпляр класса в зависимости от расширения файла (поддерживаются zip, mht, mhtml) 
     * @param file файл с архивом
     * @return созданный объект
     */
    public static ReadableContainer obtain(File file) throws IOException {
        if (!file.isDirectory() && file.exists()) {
            String filename = file.getName().toLowerCase(Locale.US);
            if (filename.endsWith(".zip")) {
                return new ReadableZip(file);
            } else if (filename.endsWith(".mht") || filename.endsWith(".mhtml") ) {
                return new ReadableMHTML(file);
            } else {
                throw new IllegalArgumentException("only zip, mhtml and directories");
            }
        } else if (file.isDirectory()) {
            return new ReadableDirContainer(file);
        } else {
            throw new FileNotFoundException();
        }
    }

}
