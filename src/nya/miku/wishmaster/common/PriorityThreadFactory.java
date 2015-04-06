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

package nya.miku.wishmaster.common;

import java.util.concurrent.ThreadFactory;

/**
 * Создание фабрик потоков с заданным приоритетом
 * @author miku-nyan
 *
 */
public class PriorityThreadFactory {
    /**
     * Получить фабрику потоков с заданным приоритетом.
     * @param priority приоритет создаваемых потоков (должен быть между {@link Thread#MIN_PRIORITY} и {@link Thread#MAX_PRIORITY})
     * @return фабрика потоков
     */
    public static ThreadFactory getFactory(final int priority) {
        if (priority < Thread.MIN_PRIORITY || priority > Thread.MAX_PRIORITY) {
            throw new IllegalArgumentException("Priority should be between Thread.MIN_PRIORITY and Thread.MAX_PRIORITY");
        }
        
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setPriority(priority);
                return t;
            }
        };
    }
    
    public static final ThreadFactory LOW_PRIORITY_FACTORY = getFactory(Thread.MIN_PRIORITY);
}
