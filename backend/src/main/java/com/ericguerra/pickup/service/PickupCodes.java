package com.ericguerra.pickup.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class PickupCodes {
    private static final SecureRandom RANDOM = new SecureRandom();

    private PickupCodes() {}

    public static String generate() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    public static String hash(String orderNo, String code) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest((orderNo + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Constant-time compare so a wrong code doesn't leak how many leading chars matched. */
    public static boolean matches(String orderNo, String code, String expectedHash) {
        return MessageDigest.isEqual(hash(orderNo, code).getBytes(StandardCharsets.UTF_8), expectedHash.getBytes(StandardCharsets.UTF_8));
    }
}
