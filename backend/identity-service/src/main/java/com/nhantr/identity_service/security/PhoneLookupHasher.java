package com.nhantr.identity_service.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class PhoneLookupHasher {

    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("phoneNumber is required.");
        }

        String compact = raw.trim().replaceAll("\\s+", "");
        String canonical;
        if (compact.matches("^0[0-9]{9}$")) {
            canonical = "+84" + compact.substring(1);
        } else if (compact.matches("^84[0-9]{9}$")) {
            canonical = "+" + compact;
        } else if (compact.matches("^\\+84[0-9]{9}$")) {
            canonical = compact;
        } else if (compact.matches("^\\+[0-9]{8,15}$") && !compact.startsWith("+84")) {
            canonical = compact;
        } else {
            throw new IllegalArgumentException("phoneNumber must be a complete international number.");
        }
        return canonical;
    }

    public String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalize(raw).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
