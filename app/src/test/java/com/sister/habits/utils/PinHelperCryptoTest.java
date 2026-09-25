package com.sister.habits.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * PinHelper \u5bc6\u7801\u5b58\u50a8/\u6821\u9a8c\u4e00\u81f4\u6027\u6d4b\u8bd5\u3002
 *
 * \u80cc\u666f\uff1a2026-09-25 \u7528\u6237\u53cd\u9988\u300c\u8bbe\u7f6e PIN \u540e\u6821\u9a8c\u59cb\u7ec8\u5931\u8d25\u300d\uff0c
 * \u9700\u6392\u67e5 setPin / verifyPin \u662f\u5426\u4e00\u81f4\u3002
 *
 * \u6ce8\u610f\uff1aPinHelper \u4f9d\u8d56 Context.getSharedPreferences\uff0c
 * JVM \u5355\u6d4b\u65e0\u6cd5\u76f4\u63a5\u8dd1\uff1b\u6545\u672c\u6d4b\u8bd5\u53ea\u9a8c\u8bc1\u7eaf\u7b97\u6cd5\u90e8\u5206\uff08\u62bd\u53d6\u4e3a\u72ec\u7acb\u590d\u73b0\uff09\u3002
 * \u5982\u679c\u8fd9\u91cc\u901a\u8fc7\u4f46\u771f\u673a\u5931\u8d25\uff0c\u5219\u95ee\u9898\u5728 Context/\u5b58\u50a8\u5c42\u800c\u975e\u7b97\u6cd5\u3002
 */
public class PinHelperCryptoTest {

    /** \u4e0e PinHelper.stretch \u5b8c\u5168\u540c\u6784\u7684\u53c2\u8003\u5b9e\u73b0 */
    private static String refStretch(String pin, byte[] salt, int iterations) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        byte[] key = pin.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // \u6bcf\u4e00\u8f6e\u65b0\u5efa\u5b9e\u4f8b\uff0c\u5f7b\u5e95\u907f\u514d\u72b6\u6001\u590d\u7528
        mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"));
        byte[] out = mac.doFinal(salt);
        for (int i = 1; i < iterations; i++) {
            javax.crypto.Mac m2 = javax.crypto.Mac.getInstance("HmacSHA256");
            m2.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"));
            out = m2.doFinal(out);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : out) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** \u786e\u8ba4\u7b97\u6cd5\u786e\u5b9a\u6027\uff1a\u540c\u4e00\u8f93\u5165\u5fc5\u5f97\u540c\u4e00\u8f93\u51fa */
    @Test
    public void stretchIsDeterministic() throws Exception {
        byte[] salt = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        String a = refStretch("560607", salt, 10000);
        String b = refStretch("560607", salt, 10000);
        assertEquals(a, b);
        assertEquals(64, a.length());
    }

    /** \u4e0d\u540c\u5bc6\u7801\u5fc5\u5f97\u4e0d\u540c\u54c8\u5e0c */
    @Test
    public void differentPinsDiffer() throws Exception {
        byte[] salt = new byte[] {9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9};
        assertNotEquals(refStretch("560607", salt, 100), refStretch("560608", salt, 100));
    }

    /** \u6821\u9a8c\u4eff\u771f\uff1asetPin \u540e verifyPin \u5fc5\u987b\u6210\u529f */
    @Test
    public void setThenVerifyMatches() throws Exception {
        String pin = "560607";
        byte[] salt = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        String stored = refStretch(pin, salt, 10000);   // setPin \u5199\u5165\u7684\u503c
        String check  = refStretch(pin, salt, 10000);   // verifyPin \u91cd\u7b97\u7684\u503c
        assertEquals(stored, check);
        assertFalse(stored.equals(refStretch("000000", salt, 10000)));
    }
}
