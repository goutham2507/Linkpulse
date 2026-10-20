package com.linkpulse.shortener_service.util;

public final class Base62 {

    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length();   // 62

    private Base62() { }

    public static String encode(long number) {
        if (number < 0) {
            throw new IllegalArgumentException("number must be >= 0");
        }
        if (number == 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        while (number > 0) {
            sb.append(ALPHABET.charAt((int) (number % BASE)));
            number /= BASE;
        }
        return sb.reverse().toString();
    }

    public static long decode(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("code must not be empty");
        }
        long result = 0;
        for (char c : code.toCharArray()) {
            int value = ALPHABET.indexOf(c);
            if (value == -1) {
                throw new IllegalArgumentException("invalid character: " + c);
            }
            result = result * BASE + value;
        }
        return result;
    }
}
