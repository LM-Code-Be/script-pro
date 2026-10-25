package com.scriptpro.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Décodage des sorties de console Windows.
 *
 * <p>ScriptPro demande aux interpréteurs d'écrire en UTF-8, mais certains programmes
 * (anciens utilitaires, {@code ping}, {@code ipconfig}…) écrivent toujours dans la page de
 * code « OEM » de la console (850 sur un Windows français). Chaque ligne est donc d'abord
 * décodée en UTF-8 strict, puis avec la page OEM si l'UTF-8 n'est pas valide : les accents
 * restent lisibles dans les deux cas.</p>
 *
 * @author LM-Code
 */
public final class ConsoleCharsets {
    private static final Pattern OEM_CODE_PAGE = Pattern.compile("OEMCP\\s+REG_SZ\\s+(\\d+)");
    private static volatile Charset oemCharset;

    private ConsoleCharsets() {
    }

    /** Décode une ligne : UTF-8 si possible, sinon page de code de repli. */
    public static String decode(byte[] bytes, int length, Charset fallback) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, 0, length))
                    .toString();
        } catch (CharacterCodingException notUtf8) {
            return new String(bytes, 0, length, fallback);
        }
    }

    /**
     * Page de code OEM du poste (ex. IBM850), lue une seule fois dans le registre Windows.
     * Sur un autre système, ou en cas d'échec, l'encodage natif de Java est utilisé.
     */
    public static Charset oem() {
        Charset charset = oemCharset;
        if (charset == null) {
            charset = detectOem();
            oemCharset = charset;
        }
        return charset;
    }

    private static Charset detectOem() {
        Charset nativeCharset = nativeCharset();
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return nativeCharset;
        }
        try {
            Process process = new ProcessBuilder("reg", "query",
                    "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Nls\\CodePage", "/v", "OEMCP")
                    .redirectErrorStream(true)
                    .start();
            process.getOutputStream().close();
            String output;
            try (InputStream inputStream = process.getInputStream()) {
                output = new String(inputStream.readAllBytes(), StandardCharsets.US_ASCII);
            }
            process.waitFor(5, TimeUnit.SECONDS);
            Matcher matcher = OEM_CODE_PAGE.matcher(output);
            if (matcher.find()) {
                return Charset.forName("IBM" + matcher.group(1));
            }
        } catch (IOException | RuntimeException exception) {
            // Registre illisible ou page de code inconnue de la JVM : on garde l'encodage natif.
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        return nativeCharset;
    }

    private static Charset nativeCharset() {
        try {
            return Charset.forName(System.getProperty("native.encoding", "UTF-8"));
        } catch (RuntimeException exception) {
            return Charset.defaultCharset();
        }
    }
}
