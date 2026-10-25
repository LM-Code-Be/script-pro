package com.scriptpro.services;

import com.scriptpro.models.Script;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests sans base de données : variables {@code {{NOM}}}, tags et lecture des fichiers importés.
 *
 * @author LM-Code
 */
class VariableAndImportTest {
    @TempDir
    Path directory;

    /** Les méthodes testées n'utilisent pas le dépôt : on peut passer {@code null}. */
    private final VariableService variableService = new VariableService(null);

    @Test
    void extractsVariablesInUpperCaseWithoutDuplicates() {
        assertEquals(Set.of("IP", "HOSTNAME"),
                variableService.extractVariables("ping {{ip}}\nnslookup {{HOSTNAME}} {{IP}}"));
    }

    @Test
    void appliesVariablesAndKeepsUnknownOnes() {
        String result = variableService.applyVariables("ping {{IP}} {{INCONNUE}} C:\\{{PATH}}",
                Map.of("IP", "10.0.0.1", "PATH", "$Temp\\dossier"));
        // Les caractères spéciaux ($, \) de la valeur doivent être recopiés tels quels.
        assertEquals("ping 10.0.0.1 {{INCONNUE}} C:\\$Temp\\dossier", result);
    }

    @Test
    void masksLongestSecretFirst() {
        assertEquals("•••••• et ••••••", VariableService.mask("abc123 et abc", List.of("abc", "abc123")));
    }

    @Test
    void parsesTags() {
        assertEquals(List.of("réseau", "urgence", "ad"), Script.parseTags(" Réseau, urgence ;; AD, réseau "));
        assertEquals(List.of(), Script.parseTags("  "));
    }

    @Test
    void importsUtf8WithBomAndAnsiFiles() throws Exception {
        Path utf8 = directory.resolve("utf8.ps1");
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "Write-Output 'Opération réussie'".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, withBom, 0, bom.length);
        System.arraycopy(body, 0, withBom, bom.length, body.length);
        Files.write(utf8, withBom);
        assertEquals("Write-Output 'Opération réussie'", ImportExportService.readScriptFile(utf8));

        Path ansi = directory.resolve("ansi.bat");
        Files.write(ansi, "echo Opération réussie".getBytes(Charset.forName("windows-1252")));
        assertEquals("echo Opération réussie", ImportExportService.readScriptFile(ansi));
    }
}
