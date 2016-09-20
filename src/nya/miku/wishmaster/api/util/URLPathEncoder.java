package nya.miku.wishmaster.api.util;

import android.net.Uri;

import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class URLPathEncoder {
    private static final String UNRESERVED = "_-!.~\'()*";
    private static final String PUNCTUATION = ",;:$&+=";
    private static final String FILE_AND_QUERY_ENCODER = "/@?";
    private static final String ALLOWED_CHARACTERS = UNRESERVED + PUNCTUATION + FILE_AND_QUERY_ENCODER;
    
    public URLPathEncoder(){    }
    
    public String encode(String url) throws MalformedURLException, NoSuchFieldException, IllegalAccessException, URISyntaxException {
        URL mUrl = new URL(url);
        try {
            URI.create(url);
        } catch (IllegalArgumentException e) {
            Field file_field = URL.class.getDeclaredField("file");
            file_field.setAccessible(true);
            file_field.set(mUrl, Uri.encode(mUrl.getFile(), ALLOWED_CHARACTERS));
            Field path_field = URL.class.getDeclaredField("path");
            path_field.setAccessible(true);
            path_field.set(mUrl, Uri.encode(mUrl.getPath(), ALLOWED_CHARACTERS));
        }
        return mUrl.toURI().toString();
    }
}
