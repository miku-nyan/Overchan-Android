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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

import nya.miku.wishmaster.api.interfaces.CancellableTask;

/**
 * Абстрактный класс инкапсулирует работу с архивом (ZIP/MHTML) для создания/модификации.<br>
 * Если файл существует, все файлы, кроме отдельного списка, будут скопированы в новый архив
 * @author miku-nyan
 *
 */
public abstract class WriteableContainer implements Closeable {
    
    /**
     * Скопировать все файлы из исходного архива (в случае, если на месте сохраняемого файла уже был старый архив) 
     * @param doNotCopy список (массив) файлов, которые не будут скопированы из исходного архива
     * @param task отменяемая задача
     */
    public abstract void transfer(String[] doNotCopy, CancellableTask task) throws IOException;
    
    /**
     * Открыть поток для записи файла в архив
     * @param filename имя файла внутри архива
     * @return поток
     * @throws IOException в случае, если файл в архиве уже существует или поток уже открыт для другого файла 
     */
    public abstract OutputStream openStream(String filename) throws IOException;
    
    /**
     * Проверить наличие файла в архиве
     * @param filename имя файла
     */
    public abstract boolean hasFile(String filename);
    
    /**
     * Завершить работу с архивом
     */
    public abstract void close() throws IOException;
    
    /**
     * Отменить действия с архивом, закрыть поток и удалить временный файл
     */
    public abstract void cancel();
    
    /**
     * Получить экземпляр класса в зависимости от расширения файла (поддерживаются zip, mht, mhtml) 
     * @param file создаваемый/модифицируемый файл-архив
     * @return созданный объект
     */
    public static WriteableContainer obtain(File file) throws IOException {
        String filename = file.getName().toLowerCase(Locale.US);
        if (filename.endsWith(".zip")) {
            return new WriteableZip(file);
        } else if (filename.endsWith(".mht") || filename.endsWith(".mhtml") ) {
            return new WriteableMHTML(file);
        } else {
            return new WriteableDirContainer(!file.isDirectory() && file.exists() ? file.getParentFile() : file);
        }
    }
    
}
