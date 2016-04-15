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

package nya.miku.wishmaster.common;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class Async {
    
    /** Фабрика потоков с низким приоритетом */
    public static final ThreadFactory LOW_PRIORITY_FACTORY = new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        }
    };
    
    private static final ExecutorService LOW_PRIORITY_EXECUTOR = Executors.newCachedThreadPool(LOW_PRIORITY_FACTORY);
    
    /** выполнить задание асинхронно в потоке с низким приоритетом */
    public static void runAsync(Runnable task) {
        LOW_PRIORITY_EXECUTOR.execute(task);
    }
}
