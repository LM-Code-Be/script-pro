package com.scriptpro.utils;

import com.scriptpro.models.ScriptLanguage;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SyntaxHighlighter {
    private static final String POWERSHELL_KEYWORDS = "\\b(function|param|process|begin|end|if|else|elseif|foreach|for|while|switch|return|try|catch|finally|throw|class|using)\\b";
    private static final String SHELL_KEYWORDS = "\\b(if|then|else|elif|fi|for|while|do|done|case|esac|function|return|export|sudo)\\b";
    private static final String PYTHON_KEYWORDS = "\\b(def|class|if|elif|else|for|while|return|import|from|try|except|finally|with|as|lambda|yield|True|False|None)\\b";
    private static final Pattern STRING_PATTERN = Pattern.compile("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'");
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{[A-Za-z0-9_-]+}}");
    private static final Pattern COMMENT_PATTERN = Pattern.compile("(?m)(#.*$|REM\\s+.*$|::.*$)");

    private SyntaxHighlighter() {
    }

    public static StyleSpans<Collection<String>> compute(String text, ScriptLanguage language) {
        Pattern pattern = Pattern.compile(
                "(?<KEYWORD>" + keywords(language) + ")" +
                        "|(?<STRING>" + STRING_PATTERN.pattern() + ")" +
                        "|(?<VARIABLE>" + VARIABLE_PATTERN.pattern() + ")" +
                        "|(?<COMMENT>" + COMMENT_PATTERN.pattern() + ")",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(text);
        int lastEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastEnd);
            String styleClass = matcher.group("KEYWORD") != null ? "code-keyword" :
                    matcher.group("STRING") != null ? "code-string" :
                            matcher.group("VARIABLE") != null ? "code-variable" :
                                    matcher.group("COMMENT") != null ? "code-comment" : null;
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
        return spansBuilder.create();
    }

    private static String keywords(ScriptLanguage language) {
        return switch (language) {
            case POWERSHELL -> POWERSHELL_KEYWORDS;
            case BASH -> SHELL_KEYWORDS;
            case PYTHON -> PYTHON_KEYWORDS;
            case CMD, BATCH -> "\\b(if|else|for|in|do|echo|set|call|goto|exit|not|exist|errorlevel)\\b";
        };
    }
}
