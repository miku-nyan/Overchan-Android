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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.lang3.tuple.Pair;

import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BadgeIconModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.common.PriorityThreadFactory;
import nya.miku.wishmaster.lib.KryoOutputHC;
import nya.miku.wishmaster.ui.tabs.TabModel;
import nya.miku.wishmaster.ui.tabs.TabsIdStack;
import nya.miku.wishmaster.ui.tabs.TabsState;
import android.os.Build;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.TaggedFieldSerializer;

/**
 * Сериализация объектов (на основе kryo)
 * @author miku-nyan
 *
 */
public class Serializer {
    private static final String TAG = "Serializer";
    
    private final FileCache fileCache;
    private final Kryo kryo;
    
    private class FileSerializer extends com.esotericsoftware.kryo.Serializer<java.io.File> {
        @Override
        public void write (Kryo kryo, Output output, File object) {
            output.writeString(object.getPath());
        }
        @Override
        public File read (Kryo kryo, Input input, Class<File> type) {
            return new File(input.readString());
        }
    };
    
    private static boolean isHoneycomb() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && Build.VERSION.SDK_INT <= Build.VERSION_CODES.HONEYCOMB_MR2;
    }
    
    /**
     * Конструктор
     * @param fileCache объект файлового кэша
     */
    public Serializer(FileCache fileCache) {
        this.fileCache = fileCache;
        
        this.kryo = new Kryo();
        this.kryo.setReferences(false);
        this.kryo.setDefaultSerializer(TaggedFieldSerializer.class);
        
        this.kryo.register(TabsState.class, 0);
        this.kryo.register(TabModel.class, 1);
        this.kryo.register(TabsIdStack.class, 2);
        
        this.kryo.register(SerializablePage.class, 3);
        this.kryo.register(SerializableBoardsList.class, 4);
        
        this.kryo.register(AttachmentModel.class, 5);
        this.kryo.register(BadgeIconModel.class, 6);
        this.kryo.register(BoardModel.class, 7);
        this.kryo.register(DeletePostModel.class, 8);
        this.kryo.register(PostModel.class, 9);
        this.kryo.register(SendPostModel.class, 10);
        this.kryo.register(SimpleBoardModel.class, 11);
        this.kryo.register(ThreadModel.class, 12);
        this.kryo.register(UrlPageModel.class, 13);
        
        this.kryo.register(AttachmentModel[].class, 14);
        this.kryo.register(BadgeIconModel[].class, 15);
        this.kryo.register(BoardModel[].class, 16);
        this.kryo.register(DeletePostModel[].class, 17);
        this.kryo.register(PostModel[].class, 18);
        this.kryo.register(SendPostModel[].class, 19);
        this.kryo.register(SimpleBoardModel[].class, 20);
        this.kryo.register(ThreadModel[].class, 21);
        this.kryo.register(UrlPageModel[].class, 22);
        
        this.kryo.register(java.util.ArrayList.class, 23);
        this.kryo.register(java.util.LinkedList.class, 24);
        this.kryo.register(java.io.File.class, new FileSerializer(), 25);
        this.kryo.register(java.io.File[].class, 26);
    }
    
    private Object serializeLock = new Object();
    private class SerializeTask implements Runnable {
        private final File file;
        private final Object obj;
        
        public SerializeTask(File file, Object obj) {
            this.file = file;
            this.obj = obj;
        }
        
        @Override
        public void run() {
            synchronized (serializeLock) {
                Output output = null;
                try {
                    output = isHoneycomb() ? new KryoOutputHC(new FileOutputStream(file)) : new Output(new FileOutputStream(file));
                    kryo.writeObject(output, obj);
                } catch (Exception e) {
                    Logger.e(TAG, e);
                } catch (OutOfMemoryError oom) {
                    MainApplication.freeMemory();
                    Logger.e(TAG, oom);
                } finally {
                    IOUtils.closeQuietly(output);
                }
                fileCache.put(file);
            }
        }
    }
    
    public void serialize(File file, Object obj) {
        serialize(file, obj, true);
    }
    
    public void serialize(File file, Object obj, boolean async) {
        Runnable task = new SerializeTask(file, obj);
        if (async) {
            PriorityThreadFactory.LOW_PRIORITY_FACTORY.newThread(task).start();
        } else {
            task.run();
        }
    }
    
    public <T> T deserialize(File file, Class<T> type) {
        if (file == null || !file.exists()) {
            return null;
        }
        
        synchronized (serializeLock) {
            Input input = null;
            try {
                input = new Input(new FileInputStream(file));
                return kryo.readObject(input, type);
            } catch (Exception e) {
                Logger.e(TAG, e);
            } catch (OutOfMemoryError oom) {
                MainApplication.freeMemory();
                Logger.e(TAG, oom);
            } finally {
                IOUtils.closeQuietly(input);
            }
        }
        
        return null;
    }
    
    public void serialize(String fileName, Object object) {
        serialize(fileCache.create(fileName), object);
    }
    
    public <T> T deserialize(String fileName, Class<T> type) {
        return deserialize(fileCache.get(fileName), type);
    }
    
    public void serializeTabsState(final TabsState state) {
        PriorityThreadFactory.LOW_PRIORITY_FACTORY.newThread(new Runnable() {
            @Override
            public void run() {
                serialize(fileCache.create(FileCache.TABS_FILENAME), state, false);
                serialize(fileCache.create(FileCache.TABS_FILENAME_2), state, false);
            }
        }).start();
    }
    
    public TabsState deserializeTabsState() {
        for (String filename : new String[]{ FileCache.TABS_FILENAME, FileCache.TABS_FILENAME_2 }) {
            try {
                TabsState obj = deserialize(filename, TabsState.class); 
                if (obj != null && obj.tabsArray != null && obj.tabsIdStack != null) return obj;
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
        }
        return TabsState.obtainDefault();
    }
    
    public void serializePage(String hash, SerializablePage page) {
        serialize(FileCache.PREFIX_PAGES + hash, page);
    }
    
    public SerializablePage deserializePage(String hash) {
        try {
            return deserialize(FileCache.PREFIX_PAGES + hash, SerializablePage.class);
        } catch (Exception e) {
            Logger.e(TAG, e);
            return null;
        }
    }
    
    public void serializeBoardsList(String hash, SerializableBoardsList boardsList) {
        serialize(FileCache.PREFIX_BOARDS + hash, boardsList);
    }
    
    public SerializableBoardsList deserializeBoardsList(String hash) {
        try {
            return deserialize(FileCache.PREFIX_BOARDS + hash, SerializableBoardsList.class);
        } catch (Exception e) {
            Logger.e(TAG, e);
            return null;
        }
    }
    
    public void serializeDraft(String hash, SendPostModel draft) {
        serialize(FileCache.PREFIX_DRAFTS + hash, draft);
    }
    
    public SendPostModel deserializeDraft(String hash) {
        try {
            return deserialize(FileCache.PREFIX_DRAFTS + hash, SendPostModel.class);
        } catch (Exception e) {
            Logger.e(TAG, e);
            return null;
        }
    }
    
    public void removeDraft(String hash) {
        File file = fileCache.get(FileCache.PREFIX_DRAFTS + hash);
        if (file != null) {
            fileCache.delete(file);
        }
    }
    
    
    public void savePage(OutputStream out, String title, UrlPageModel pageModel, SerializablePage page) {
        synchronized (serializeLock) {
            Output output = null;
            try {
                output = isHoneycomb() ? new KryoOutputHC(out) : new Output(out);
                output.writeString(title);
                kryo.writeObject(output, pageModel);
                kryo.writeObject(output, page);
            } finally {
                IOUtils.closeQuietly(output);
            }
        }
    }
    
    public Pair<String, UrlPageModel> loadPageInfo(InputStream in) {
        synchronized (serializeLock) {
            Input input = null;
            try {
                input = new Input(in);
                String title = input.readString();
                UrlPageModel pageModel = kryo.readObject(input, UrlPageModel.class);
                return Pair.of(title, pageModel);
            } finally {
                IOUtils.closeQuietly(input);
            }
        }
    }
    
    public SerializablePage loadPage(InputStream in) {
        synchronized (serializeLock) {
            Input input = null;
            try {
                input = new Input(in);
                input.readString();
                kryo.readObject(input, UrlPageModel.class);
                return kryo.readObject(input, SerializablePage.class);
            } finally {
                IOUtils.closeQuietly(input);
            }
        }
    }
}
