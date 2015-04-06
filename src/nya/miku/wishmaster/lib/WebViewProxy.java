package nya.miku.wishmaster.lib;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import nya.miku.wishmaster.common.Logger;

import android.content.Context;

/**
 * Костыль для установки прокси в WebView (рефлексивный).<br>
 * http://stackoverflow.com/questions/4488338/webview-android-proxy<br>
 * Изменения:<ul>
 * <li>в случае host == null происходит сброс настроек прокси</li></ul>
 */
public class WebViewProxy {
    private static final String TAG = "WebViewProxy";
    
    /**
     * Установить или сбросить HTTP прокси. Устанавливается глобально на весь класс android.webkit.Network,
     * так что необходимо не забывать сбрасывать настройки прокси после использования, если они нужны не всегда.
     * @param сontext контекст для получения экземпляра android.webkit.Network
     * @param host адрес прокси сервера или NULL, если необходимо сбросить настройки
     * @param port порт прокси сервера
     * @return true, если операция выполнена успешно
     */
    @SuppressWarnings("all")
    public static boolean setProxy(Context сontext, String host, int port) {
        Logger.d(TAG, "Setting proxy with <= 3.2 API.");

        org.apache.http.HttpHost proxyServer = host != null ? new org.apache.http.HttpHost(host, port) : null;
        // Getting network
        Class networkClass = null;
        Object network = null;
        try {
            networkClass = Class.forName("android.webkit.Network");
            if (networkClass == null) {
                Logger.e(TAG, "failed to get class for android.webkit.Network");
                return false;
            }
            Method getInstanceMethod = networkClass.getMethod("getInstance", Context.class);
            if (getInstanceMethod == null) {
                Logger.e(TAG, "failed to get getInstance method");
            }
            network = getInstanceMethod.invoke(networkClass, new Object[]{сontext});
        } catch (Exception ex) {
            Logger.e(TAG, "error getting network: " + ex);
            return false;
        }
        if (network == null) {
            Logger.e(TAG, "error getting network: network is null");
            return false;
        }
        Object requestQueue = null;
        try {
            Field requestQueueField = networkClass.getDeclaredField("mRequestQueue");
            requestQueue = getFieldValueSafely(requestQueueField, network);
        } catch (Exception ex) {
            Logger.e(TAG, "error getting field value");
            return false;
        }
        if (requestQueue == null) {
            Logger.e(TAG, "Request queue is null");
            return false;
        }
        Field proxyHostField = null;
        try {
            Class requestQueueClass = Class.forName("android.net.http.RequestQueue");
            proxyHostField = requestQueueClass.getDeclaredField("mProxyHost");
        } catch (Exception ex) {
            Logger.e(TAG, "error getting proxy host field");
            return false;
        }

        boolean temp = proxyHostField.isAccessible();
        try {
            proxyHostField.setAccessible(true);
            proxyHostField.set(requestQueue, proxyServer);
        } catch (Exception ex) {
            Logger.e(TAG, "error setting proxy host");
        } finally {
            proxyHostField.setAccessible(temp);
        }

        Logger.d(TAG, "Setting proxy with <= 3.2 API successful!");
        return true;
    }

    private static Object getFieldValueSafely(Field field, Object classInstance) throws IllegalArgumentException, IllegalAccessException {
        boolean oldAccessibleValue = field.isAccessible();
        field.setAccessible(true);
        Object result = field.get(classInstance);
        field.setAccessible(oldAccessibleValue);
        return result;
    }
}
