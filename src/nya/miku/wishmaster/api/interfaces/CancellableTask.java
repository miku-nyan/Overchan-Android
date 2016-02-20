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

package nya.miku.wishmaster.api.interfaces;

/**
 * Интерфейс задачи, которая может быть отменена.<br>
 * Задачу можно отменить методом {@link #cancel()}.<br>
 * Фоновая задача регулярно проверяет значение методом {@link #isCancelled()}
 * и при получении true должна немедленно завершить работу.
 * @author miku-nyan
 *
 */
public interface CancellableTask {
    
    /**
     * Отменить задачу (запросить отмену)
     */
    void cancel();
    
    /**
     * Должен вернуть true, если задача отменена
     */
    boolean isCancelled();
    
    /** константа-синглтон, всегда возвращает isCancelled() false.
     *  (может использоваться как заглушка, если требуется не-null объект, но возможность отмены не нужна) */
    public static final CancellableTask NOT_CANCELLABLE = new CancellableTask() {
        @Override public boolean isCancelled() { return false; }
        @Override public void cancel() {}
    };
    
    /**
     * Базовая реализация интерфейса
     * @author miku-nyan
     *
     */
    public static class BaseCancellableTask implements CancellableTask {
        private volatile boolean cancelled = false;
        
        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
        
    }
}
