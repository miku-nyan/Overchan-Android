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

package nya.miku.wishmaster.ui.gallery;

public class GalleryExceptionHandler {
    public static void init() {
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        if (defaultHandler instanceof ExceptionHandler) return;
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(defaultHandler));
    }
    
    private static class ExceptionHandler implements Thread.UncaughtExceptionHandler {
        private final Thread.UncaughtExceptionHandler defaultHandler;
        
        ExceptionHandler(Thread.UncaughtExceptionHandler defaultHandler) {
            this.defaultHandler = defaultHandler;
        }
        
        @Override
        public void uncaughtException(Thread thread, Throwable ex) {
            if (ex instanceof NullPointerException) {
                StackTraceElement top = ex.getStackTrace()[0];
                if ("android.view.SurfaceView".equals(top.getClassName()) && "updateWindow".equals(top.getMethodName())) {
                    System.err.println("CAUGHT EXCEPTION");
                    ex.printStackTrace(System.err);
                    System.exit(0);
                    return;
                }
            }
            defaultHandler.uncaughtException(thread, ex);
        }
    }
}
