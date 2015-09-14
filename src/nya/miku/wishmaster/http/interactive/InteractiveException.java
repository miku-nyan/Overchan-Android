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

package nya.miku.wishmaster.http.interactive;

import nya.miku.wishmaster.api.interfaces.CancellableTask;
import android.app.Activity;

/**
 * Базовый класс для интерактивных исключений (таких как cloudflare-проверка, необходимость ввести капчу и т.д.)
 * @author miku-nyan
 *
 */
public abstract class InteractiveException extends Exception {
    private static final long serialVersionUID = 1L;
    
    /**
     * Интерфейс-callback обработчика интерактивных исключений
     * @author miku-nyan
     *
     */
    public interface Callback {
        /**
         * Вызывается в UI потоке, в случае успешной проверки
         * (возможно, неуспешной, но без ошибок, можно делать новую попытку)
         */
        void onSuccess();
        /**
         * Вызывается в UI потоке, в случае ошибки при проверке
         */
        void onError(String message);
    }
    
    /**
     * Получить название сервиса, являющегося причиной исключения
     */
    public abstract String getServiceName();
    
    /**
     * Обработать исключение. Метод вызывается асинхронно, не из UI потока
     * @param activity активность, используется для создания диалогов, доступа к UI потоку, доступа к ресурсам
     * @param task отмняемая задача
     * @param callback интерфейс Callback
     */
    public abstract void handle(Activity activity, CancellableTask task, Callback callback);
}
