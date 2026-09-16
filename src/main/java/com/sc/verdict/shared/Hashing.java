package com.sc.verdict.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Content hashing for evidence provenance and journal entries. A determination pins hashes, not
 * bytes, and a replay recomputes them; the hash is therefore load-bearing for the replay claim and
 * is a real SHA-256, not a placeholder.
 */
public final class Hashing {

    private Hashing() {}

    public static String sha256Hex(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            var hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Short prefix for human-readable display in the journal and console. */
    public static String shortHash(String hashHex) {
        return hashHex.length() <= 12 ? hashHex : hashHex.substring(0, 12);
    }
}
