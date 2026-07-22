package com.photoizer.crm.shared.logging;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public final class SensitiveDataMask {

    private static final List<Pattern> SENSITIVE_PATTERNS = Arrays.asList(
        Pattern.compile("\\b\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}\\b"),
        Pattern.compile("\\b\\d{11}\\b"),
        Pattern.compile("\\(\\d{2}\\)\\s?\\d{4,5}-?\\d{4}\\b"),
        Pattern.compile("\\b[\\w.+-]+@[\\w-]+\\.[\\w.]+\\b")
    );

    private static final int MAX_STRING_LENGTH = 500;

    private SensitiveDataMask() {
    }

    public static String mask(String input) {
        if (input == null) {
            return null;
        }
        if (input.length() > MAX_STRING_LENGTH) {
            input = input.substring(0, MAX_STRING_LENGTH) + "...";
        }
        var result = input;
        for (var pattern : SENSITIVE_PATTERNS) {
            result = pattern.matcher(result).replaceAll(mask -> {
                var raw = mask.group();
                if (raw.contains("@")) {
                    return maskEmail(raw);
                }
                return maskGeneric(raw);
            });
        }
        return result;
    }

    public static String maskArgs(Object[] args) {
        if (args == null) {
            return "null";
        }
        var masked = Arrays.stream(args)
            .map(arg -> {
                if (arg instanceof String s) {
                    return mask(s);
                }
                return String.valueOf(arg);
            })
            .toList();
        var joined = String.join(", ", masked);
        if (joined.length() > MAX_STRING_LENGTH) {
            joined = joined.substring(0, MAX_STRING_LENGTH) + "...";
        }
        return joined;
    }

    private static String maskEmail(String email) {
        var at = email.indexOf('@');
        if (at <= 1) {
            return "***@" + email.substring(at + 1);
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String maskGeneric(String value) {
        var len = value.length();
        if (len <= 2) {
            return "***";
        }
        var visible = Math.min(2, len / 4);
        return value.substring(0, visible) + "***" + value.substring(len - visible);
    }
}
