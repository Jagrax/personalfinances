package ar.com.personalfinances.util;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class CmdEncrypt {

    private static final SecureRandom random = new SecureRandom();

    public static String cmdEncrypt(String encryptionSeed, String username, String password) {

        // ===== RSA params =====
        BigInteger e = new BigInteger("010001", 16);
        BigInteger n = new BigInteger(
                "A6EF15DBE9527262B0A888B6089A4AABED76142A511A6DD46E1D370F5A13EF33CBE56FF956848D7AEDB0E95FED440392B931D83A932755B9663B2F987C6E7948FA6B09C789D31854AA3B30B2B10E522E4A9F8F8F27EE4E0AE8F559EF170F447FC8B2E3EE118319CA73E377097517FFC182756C34A0020C725AF11B0D94F65255",
                16
        );

        int digitSize = (n.bitLength() + 7) / 8; // bytes
        int chunkSize = digitSize - 11;

        // ===== payload =====
        String payload = encryptionSeed + "\\" + base64(base64(username) + "\\" + base64(password));

        byte[] data = payload.getBytes(StandardCharsets.UTF_8);

        StringBuilder out = new StringBuilder();

        for (int i = 0; i < data.length; i += chunkSize) {

            int len = Math.min(chunkSize, data.length - i);

            byte[] block = pkcs1Pad(data, i, len, digitSize);

            BigInteger m = new BigInteger(1, block);
            BigInteger c = m.modPow(e, n);

            out.append(c.toString(16)).append(" ");
        }

        return out.substring(0, out.length() - 1);
    }

    // ===== PKCS#1 v1.5 (JS compatible) =====
    private static byte[] pkcs1Pad(byte[] data, int offset, int len, int size) {

        byte[] block = new byte[size];

        int i = size - 1;

        // Copy data at the end (NORMAL ORDER)
        for (int j = offset + len - 1; j >= offset; j--) {
            block[i--] = data[j];
        }

        block[i--] = 0x00;

        // Random non-zero padding
        while (i > 1) {
            block[i--] = (byte) (random.nextInt(254) + 1);
        }

        block[0] = 0x00;
        block[1] = 0x02;

        return block;
    }

    // ===== Base64 helpers =====
    private static String base64(String s) {
        return Base64.getEncoder().encodeToString(
                s.getBytes(StandardCharsets.UTF_8)
        );
    }
}