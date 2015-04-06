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

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CryptoUtils {
    private CryptoUtils() {}
    
    private static final Pattern CF_EMAIL =
            Pattern.compile("<a class=\"__cf_email__\"(?:.*?)data-cfemail=\"([^\"]*)\"(?:.*?)</script>", Pattern.DOTALL);
    
    private static Random random = null;
    
    /**
     * Вычислить MD5 контрольную сумму, представить в виде hex-строки
     * @param str строка, от которой вычисляется хэш
     * @return строка с hex-представлением значения MD5
     */
    public static String computeMD5(String str) {
        if (str == null) str = "";
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.reset();
            messageDigest.update(str.getBytes());
            byte[] digest = messageDigest.digest();
            BigInteger bigInt = new BigInteger(1, digest);
            String md5Hex = bigInt.toString(16);
            if (md5Hex.length() < 32){
                char[] head = new char[32 - md5Hex.length()];
                Arrays.fill(head, '0');
                md5Hex = new StringBuilder(32).append(head).append(md5Hex).toString();
            }
            return md5Hex;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Сгенерировать пароль, состоящий из букв латинского алфавита и цифр, длиной от 10 до 15 символов
     * @return строка с паролем
     */
    public static String genPassword() {
        if (random == null) random = new Random();
        return genPassword(10 + random.nextInt(6));
    }
    
    /**
     * Сгенерировать пароль, состоящий из букв латинского алфавита и цифр
     * @param len длина
     * @return строка с паролем
     */
    public static String genPassword(int len) {
        if (random == null) random = new Random();
        StringBuilder builder = new StringBuilder(len);
        for (int i=0; i<len; ++i) {
            char ch;
            int c = random.nextInt(62);
            if (c < 10) {
                ch = Character.forDigit(c, 10);
            } else if (c < 37) {
                c -= 10;
                ch = (char) ('a' + c);
            } else {
                c -= 36;
                ch = (char) ('A' + c);
            }
            builder.append(ch);
        }
        return builder.toString();
    }
    
    /**
     * Декодировать строку, закодированную Cloudflare Email Protection
     */
    public static String decodeCloudflareEmail(String encoded) {
        try {
            StringBuilder sb = new StringBuilder();
            int r = Integer.parseInt(encoded.substring(0, 2), 16);
            for(int n=2; n<encoded.length(); n+=2) {
                sb.append((char)(Integer.parseInt(encoded.substring(n, n+2), 16) ^ r));
            }
            return sb.toString();
        } catch (Exception e) {
            return encoded;
        }
    }
    
    /**
     * Восстановить все адреса E-mail, XMPP и др., закодированные Cloudflare Email Protection в комментарии,
     * вырезать скрипты и закодированные строки, заменить на обычный (читабельный, не требующий js) текст.
     * @param commentBody тело комментария (HTML)
     * @return исправленное тело комментария 
     */
    public static String fixCloudflareEmails(String commentBody) {
        Matcher matcher = CF_EMAIL.matcher(commentBody);
        if (!matcher.find()) return commentBody;
        
        StringBuffer sb = new StringBuffer();
        do {
            String found = matcher.group(1);
            matcher.appendReplacement(sb, decodeCloudflareEmail(found));
        } while (matcher.find());
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    
}
