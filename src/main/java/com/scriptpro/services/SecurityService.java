package com.scriptpro.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SecurityService {
    private static final List<String> DANGEROUS_PATTERNS = List.of(
            "rm -rf",
            "format ",
            "diskpart",
            "del /f /s",
            "shutdown",
            "remove-item -recurse",
            "stop-computer",
            "restart-computer"
    );

    public List<String> detectWarnings(String content) {
        String normalized = content == null ? "" : content.toLowerCase(Locale.ROOT);
        List<String> warnings = new ArrayList<>();
        for (String pattern : DANGEROUS_PATTERNS) {
            if (normalized.contains(pattern)) {
                warnings.add(pattern);
            }
        }
        return warnings;
    }
}
