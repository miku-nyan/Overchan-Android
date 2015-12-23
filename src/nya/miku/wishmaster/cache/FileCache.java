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
import java.util.LinkedList;
import java.util.ListIterator;

import org.apache.commons.lang3.tuple.Pair;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.provider.BaseColumns;
import nya.miku.wishmaster.common.Logger;

/**
 * Общий файловый кэш (LRU)
 * @author miku-nyan
 *
 */
public class FileCache {
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
    
    private final FileCacheDB database;
    
    private final File directory;
    private long maxSize;
    private long maxPagesSize;
    
    private volatile long size;
    private volatile long pagesSize;
    
    private volatile boolean initialized = false;
    private final Object initLock = new Object();
    
    /**
     * Конструктор
     * @param directory директория кэша
     * @param maxSize максимальный размер в байтах (0 - неограниченный)
     * @param dbContext контекст для доступа к базе данных
     */
    public FileCache(File directory, final long maxSize, Context dbContext) {
        this.directory = directory;
        this.database = new FileCacheDB(dbContext);
        makeDir();
        makeNomedia();
        
        Thread initThread = new Thread() {
            @Override
            public void run() {
                long[] sizeInDB = database.getSize();
                if (sizeInDB[0] != 0) {
                    FileCache.this.size = sizeInDB[0];
                    FileCache.this.pagesSize = sizeInDB[1];
                } else {
                    resetCache();
                }
                
                setMaxSizeValues(maxSize);
                synchronized (initLock) {
                    initialized = true;
                    initLock.notifyAll();
                    Logger.d(TAG, "File Cache initialized");
                }
            }
        };
        initThread.start();
    }
    
    private void ensureInitialized() {
        if (initialized) return;
        synchronized (initLock) {
            while (!initialized) {
                try {
                    initLock.wait();
                } catch (Exception e) {
                    Logger.e(TAG, e);
                }
            }
        }
    }
    
    /**
     * Установить максимальный размер кэша
     * @param maxSize максимальный размер в байтах (0 - неограниченный)
     */
    public void setMaxSize(long maxSize) {
        ensureInitialized();
        setMaxSizeValues(maxSize);
        trim();
    }
    
    private void setMaxSizeValues(long maxSize) {
        this.maxSize = maxSize;
        this.maxPagesSize = (long) (maxSize * PAGES_QUOTE);
    }
    
    /**
     * Получить текущий размер кэша
     * @return текущий размер в байтах
     */
    public long getCurrentSize() {
        ensureInitialized();
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
        ensureInitialized();
        for (File f : filesOfDir(directory)) {
            if (!isUndeletable(f)) f.delete();
        }
        resetCache();
    }
    
    /**
     * Получить файл из кэша
     * @param fileName имя файла
     * @return полученный файл, если файл существует, или null, если файл отсутствует
     */
    public File get(String fileName) {
        ensureInitialized();
        synchronized (this) {
            File file = pathToFile(fileName);
            if (file.exists() && !file.isDirectory()) {
                file.setLastModified(System.currentTimeMillis());
                database.touch(fileName);
                return file;
            }
            return null;
        }
    }
    
    /**
     * Получить файл из кэша немедленно (даже если кэш не инициализирован), но время доступа не обновляется, ни в базе данных, ни в файловой системе.
     */
    File getImmediately(String fileName) {
        File file = pathToFile(fileName);
        if (file.exists() && !file.isDirectory()) return file;
        return null;
    }
    
    /**
     * Создать объект для нового файла (если файл с таким именем уже присутствует в кэше, он удаляется).
     * По окончании действий с файлом (после окончания записи) необходимо вызвать метод {@link #put(File)}, чтобы учесть размер нового файла.
     * Действия при работе с файлами (при необходимости) нужно синхронизировать дополнительно.  
     * @param fileName имя файла
     * @return объект типа {@link File}
     */
    public File create(String fileName) {
        ensureInitialized();
        synchronized (this) {
            makeDir();
            File file = pathToFile(fileName);
            if (file.exists()) {
                delete(file, false);
            }
            database.put(fileName, -1);
            return file;
        }
    }
    
    /**
     * Учитывает размер созданного файла, добавляет к размеру кэша, в случае необходимости удаляются устаревшие файлы. 
     * @param file объект типа {@link File}
     */
    public void put(File file) {
        ensureInitialized();
        synchronized (this) {
            size += file.length();
            if (isPageFile(file)) pagesSize += file.length();
            database.put(file.getName(), file.length());
            trim();
        }
    }
    
    /**
     * Удалить файл из кэша
     * @param file объект типа {@link File}
     * @return true, если файл удалён успешно, false в противном случае
     */
    public boolean delete(File file) {
        return delete(file, true);
    }
    
    public boolean delete(File file, boolean removeFromDB) {
        ensureInitialized();
        synchronized (this) {
            size -= file.length();
            if (isPageFile(file)) pagesSize -= file.length();
            if (file.delete()) {
                if (removeFromDB) database.remove(file.getName());
                return true;
            } else {
                resetCache();
                return false;
            }
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
        for (int i=0; i<3; ++i) {
            if (maxSize == 0 || size <= maxSize) return;
            
            LinkedList<Pair<String, Long>> files = database.getFilesForTrim(size - maxSize);
            
            while (size > maxSize) {
                File oldest = null;
                for (ListIterator<Pair<String, Long>> it = files.listIterator(); it.hasNext();) {
                    File file = pathToFile(it.next().getLeft());
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
    }
    
    private synchronized void resetCache() {
        database.resetDB();
        database.insertFiles(filesOfDir(directory));
        long[] sizeInDB = database.getSize();
        size = sizeInDB[0];
        pagesSize = sizeInDB[1];
    }

    private boolean isUndeletable(File file) {
        return isUndeletable(file.getName()); 
    }
    
    private static boolean isUndeletable(String filename) {
        return filename.equals(TABS_FILENAME) || filename.equals(TABS_FILENAME_2) || filename.startsWith(PREFIX_BOARDS) || filename.equals(NOMEDIA);
    }
    
    private static boolean isPageFile(File file) {
        return isPageFile(file.getName());
    }
    
    private static boolean isPageFile(String filename) {
        //the same condition must be in method FileCacheDB.getSize() (SQL) for the sum calculation
        return filename.startsWith(PREFIX_PAGES) || filename.startsWith(PREFIX_DRAFTS);
    }
    
    private File[] filesOfDir(File directory) {
        File[] files = directory.listFiles();
        if (files == null) return new File[0];
        return files;
    }
    
    private static class FileCacheDB {
        private static final int DB_VERSION = 1000;
        private static final String DB_NAME = "filecache.db";
        
        private static final String TABLE_NAME = "files";
        private static final String COL_FILENAME = "name";
        private static final String COL_FILESIZE = "size";
        private static final String COL_TIMESTAMP = "time";
        
        private final DBHelper dbHelper; 
        public FileCacheDB(Context context) {
            dbHelper = new DBHelper(context);
        }
        
        public boolean isExists(String filename) {
            Cursor c = dbHelper.getReadableDatabase().query(TABLE_NAME, null, COL_FILENAME + " = ?",
                    new String[] { filename }, null, null, null);
            boolean result = false;
            if (c != null && c.moveToFirst()) result = true;
            if (c != null) c.close();
            return result;
        }
        
        public void touch(String filename) {
            ContentValues cv = new ContentValues();
            cv.put(COL_TIMESTAMP, System.currentTimeMillis());
            dbHelper.getWritableDatabase().update(TABLE_NAME, cv, COL_FILENAME + " = ?", new String[] { filename });
        }
        
        public void put(String filename, long size) {
            ContentValues cv = new ContentValues();
            cv.put(COL_FILENAME, filename);
            cv.put(COL_FILESIZE, size);
            cv.put(COL_TIMESTAMP, System.currentTimeMillis());
            
            if (isExists(filename)) {
                dbHelper.getWritableDatabase().update(TABLE_NAME, cv, COL_FILENAME + " = ?", new String[] { filename });
            } else {
                dbHelper.getWritableDatabase().insert(TABLE_NAME, null, cv);
            }
        }
        
        public void insertFiles(File[] files) {
            SQLiteDatabase database = dbHelper.getWritableDatabase();
            SQLiteStatement statement = database.compileStatement("INSERT INTO " + TABLE_NAME +
                    " (" + COL_FILENAME + ", " + COL_FILESIZE + ", " + COL_TIMESTAMP + ") VALUES (?, ?, ?)");
            database.beginTransaction();
            try {
                for (File file : files) {
                    statement.bindString(1, file.getName());
                    statement.bindLong(2, file.length());
                    statement.bindLong(3, file.lastModified());
                    statement.executeInsert();
                }
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }
        
        public LinkedList<Pair<String, Long>> getFilesForTrim(long size) {
            LinkedList<Pair<String, Long>> list = new LinkedList<>();
            Cursor c = dbHelper.getReadableDatabase().query(TABLE_NAME, null, null, null, null, null, COL_TIMESTAMP, "1000");
            if (c != null) {
                if (c.moveToFirst()) {
                    int nameIndex = c.getColumnIndex(COL_FILENAME);
                    int timeIndex = c.getColumnIndex(COL_TIMESTAMP);
                    int sizeIndex = c.getColumnIndex(COL_FILESIZE);
                    long tSize = 0;
                    do {
                        String filename = c.getString(nameIndex);
                        if (!isUndeletable(filename)) {
                            list.add(Pair.of(filename, c.getLong(timeIndex)));
                            if (!isPageFile(filename)) tSize += c.getLong(sizeIndex);
                            if (tSize >= size) break;
                        }
                    } while (c.moveToNext());
                }
                c.close();
            }
            return list;
        }
        
        public long[] getSize() {
            long[] result = new long[] { 0, 0 };
            Cursor c = dbHelper.getReadableDatabase().query(TABLE_NAME, null, COL_FILESIZE + " = -1", null, null, null, null);
            if (c != null) {
                if (c.moveToFirst()) {
                    c.close();
                    resetDB();
                    return result;
                }
                c.close();
            }
            
            c = dbHelper.getReadableDatabase().rawQuery("SELECT SUM(" + COL_FILESIZE + ") FROM " + TABLE_NAME, null);
            if (c != null) {
                if (c.moveToFirst()) result[0] = c.getInt(0);
                c.close();
            }
            
            c = dbHelper.getReadableDatabase().rawQuery("SELECT SUM(" + COL_FILESIZE + ") FROM " + TABLE_NAME +
                    " WHERE " + COL_FILENAME + " LIKE '" + PREFIX_PAGES + "%' OR " + COL_FILENAME + " LIKE '" + PREFIX_DRAFTS + "%'", null);
            if (c != null) {
                if (c.moveToFirst()) result[1] = c.getInt(0);
                c.close();
            }
            
            return result;
        }
        
        public void remove(String filename) {
            dbHelper.getWritableDatabase().delete(TABLE_NAME, COL_FILENAME + " = ?", new String[] { filename });
        }
        
        public void resetDB() {
            dbHelper.resetDB();
        }
        
        private static class DBHelper extends SQLiteOpenHelper implements BaseColumns {
            public DBHelper(Context context) {
                super(context, DB_NAME, null, DB_VERSION);
            }
            
            @Override
            public void onCreate(SQLiteDatabase db) {
                db.execSQL(createTable(TABLE_NAME,
                        new String[] { COL_FILENAME, COL_FILESIZE, COL_TIMESTAMP },
                        new String[] { "text", "integer", "integer" }));
            }
            
            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                if (oldVersion < newVersion) {
                    db.execSQL(dropTable(TABLE_NAME));
                    onCreate(db);
                }
            }
            
            @Override
            public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                onUpgrade(db, oldVersion, newVersion);
            }
            
            private static String createTable(String tableName, String[] columns, String[] types) {
                StringBuilder sql = new StringBuilder(110).append("create table ").append(tableName).append(" (").
                        append(_ID).append(" integer primary key autoincrement,");
                for (int i=0; i<columns.length; ++i) {
                    sql.append(columns[i]).append(' ').append(types == null ? "text" : types[i]).append(',');
                }
                sql.setCharAt(sql.length()-1, ')');
                return sql.append(';').toString();
            }
            
            private static String dropTable(String tableName) {
                return "DROP TABLE IF EXISTS " + tableName;
            }
            
            private void resetDB() {
                SQLiteDatabase db = getWritableDatabase();
                db.execSQL(dropTable(TABLE_NAME));
                onCreate(db);
            }
            
        }
    }
    
}
