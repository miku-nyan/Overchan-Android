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

package nya.miku.wishmaster.cache;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.ListIterator;

import org.apache.commons.lang3.tuple.Pair;

import nya.miku.wishmaster.common.Logger;

/**
 * Общий файловый кэш (LRU)
 * @author miku-nyan
 *
 */
public class FileCache { //TODO database
    private static final String TAG = "FileCache";
    
    public static final String PREFIX_ORIGINALS = "orig_";
    /*package*/ static final String PREFIX_BITMAPS = "thumb_";
    
    /*package*/ static final String PREFIX_PAGES = "page_"; //не удаляются, если в совокупности занимают менее 10%
    /*package*/ static final String PREFIX_DRAFTS = "draft_";
    
    /*package*/ static final String PREFIX_BOARDS = "boards_"; //не удаляются никогда
    /** имя файла для состояния вкладок */
    /*package*/ static final String TABS_FILENAME = "tabsstate"; //не удаляется никогда
    /** имя файла для состояния вкладок (копия) */
    /*package*/ static final String TABS_FILENAME_2 = "tabsstate_2"; //не удаляется никогда
    
    private static final String NOMEDIA = ".nomedia";
    
    private static final float PAGES_QUOTE = 0.1f;
    
    private final File directory;
    private long maxSize;
    private long maxPagesSize;

    private volatile long size;
    private volatile long pagesSize;
    
    /**
     * Конструктор
     * @param directory директория кэша
     * @param maxSize максимальный размер в байтах (0 - неограниченный)
     */
    public FileCache(File directory, long maxSize) {
        this.directory = directory;
        makeDir();
        makeNomedia();
        calculateSize();
        setMaxSize(maxSize);
    }
    
    /**
     * Установить максимальный размер кэша
     * @param maxSize максимальный размер в байтах (0 - неограниченный)
     */
    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
        this.maxPagesSize = (long) (maxSize * PAGES_QUOTE);
        trim();
    }
    
    /**
     * Получить текущий размер кэша
     * @return текущий размер в байтах
     */
    public long getCurrentSize() {
        return size;
    }
    
    /**
     * Получить текущий размер кэша в мегабайтах
     * @return текущий размер в мегабайтах
     */
    public double getCurrentSizeMB() {
        return (double) getCurrentSize() / (1024 * 1024);
    }
    
    /**
     * Очистить кэш (удалить все файлы)
     */
    public void clearCache() {
        for (File f : allFilesOfDir(directory)) {
            if (!isUndeletable(f)) f.delete();
        }
        calculateSize();
    }
    
    /**
     * Получить файл из кэша
     * @param fileName имя файла
     * @return полученный файл, если файл существует, или null, если файл отсутствует
     */
    public synchronized File get(String fileName) {
        File file = pathToFile(fileName);
        if (file.exists() && !file.isDirectory()) {
            file.setLastModified(System.currentTimeMillis());
            return file;
        }
        return null;
    }
    
    /**
     * Создать объект для нового файла (если файл с таким именем уже присутствует в кэше, он удаляется).
     * По окончании действий с файлом (после окончания записи) необходимо вызвать метод {@link #put(File)}, чтобы учесть размер нового файла.
     * Действия при работе с файлами (при необходимости) нужно синхронизировать дополнительно.  
     * @param fileName имя файла
     * @return объект типа {@link File}
     */
    public synchronized File create(String fileName) {
        makeDir();
        File file = pathToFile(fileName);
        if (file.exists()) {
            delete(file);
        }
        return file;
    }
    
    /**
     * Учитывает размер созданного файла, добавляет к размеру кэша, в случае необходимости удаляются устаревшие файлы. 
     * @param file объект типа {@link File}
     */
    public synchronized void put(File file) {
        size += file.length();
        if (isPageFile(file)) pagesSize += file.length();
        trim();
    }
    
    /**
     * Удалить файл из кэша
     * @param file объект типа {@link File}
     * @return true, если файл удалён успешно, false в противном случае
     */
    public synchronized boolean delete(File file) {
        size -= file.length();
        if (isPageFile(file)) pagesSize -= file.length();
        if (file.delete()) {
            return true;
        } else {
            calculateSize();
            return false;
        }
    }
    
    private File pathToFile(String fileName) {
        return new File(directory, fileName);
    }
    
    private void makeDir() {
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                Logger.e(TAG, "Unable to create file cache dir " + directory.getPath());
            }
        }
    }
    
    private void makeNomedia() {
        try {
            pathToFile(NOMEDIA).createNewFile();
        } catch (Exception e) {
            Logger.e(TAG, "couldn't create .nomedia file", e);
        }
    }
    
    private synchronized void trim() {
        if (maxSize == 0 || size <= maxSize) return;
        
        LinkedList<Pair<File, Long>> files = new LinkedList<>();
        for (File file : allFilesOfDir(directory)) {
            if (!isUndeletable(file)) {
                long age = file.lastModified();
                if (age != 0L) files.add(Pair.of(file, age));
            }
        }
        Collections.sort(files, new Comparator<Pair<File, Long>>() {
            @Override
            public int compare(Pair<File, Long> lhs, Pair<File, Long> rhs) {
                return lhs.getRight().compareTo(rhs.getRight());
            }
        });
        
        while (size > maxSize) {
            File oldest = null;
            for (ListIterator<Pair<File, Long>> it = files.listIterator(); it.hasNext();) {
                File file = it.next().getLeft();
                if (isPageFile(file) && pagesSize < maxPagesSize) continue;
                it.remove();
                oldest = file;
                break;
            }
            if (oldest == null) {
                Logger.e(TAG, "No files to trim");
                break;
            } else {
                Logger.d(TAG, "Deleting " + oldest.getPath());
                if (!delete(oldest)) {
                    Logger.e(TAG, "Cannot delete cache file: " + oldest.getPath());
                    break;
                }
            }
        }
    }
    
    private synchronized void calculateSize() {
        size = 0;
        pagesSize = 0;
        for (File file : allFilesOfDir(directory)) {
            size += file.length();
            if (isPageFile(file)) pagesSize += file.length();
        }
    }

    private boolean isUndeletable(File file) {
        String filename = file.getName();
        return filename.equals(TABS_FILENAME) || filename.equals(TABS_FILENAME_2) || filename.startsWith(PREFIX_BOARDS) || filename.equals(NOMEDIA); 
    }
    
    private boolean isPageFile(File file) {
        String filename = file.getName();
        return filename.startsWith(PREFIX_PAGES) || filename.startsWith(PREFIX_DRAFTS);
    }
    
    private Iterable<File> allFilesOfDir(File directory) {
        LinkedList<File> list = new LinkedList<File>();
        addDir(list, directory);
        return list;
    }
    
    private void addDir(LinkedList<File> list, File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    addDir(list, file);
                } else {
                    list.add(file);
                }
            }
        }
    }
    
}
