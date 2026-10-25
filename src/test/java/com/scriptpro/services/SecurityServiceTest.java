package com.scriptpro.services;

import com.scriptpro.models.SecurityWarning;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vérifie que les règles de sécurité détectent les variantes dangereuses sans alerter
 * sur des commandes inoffensives.
 *
 * @author LM-Code
 */
class SecurityServiceTest {
    private final SecurityService service = new SecurityService(List::of);

    @ParameterizedTest
    @ValueSource(strings = {
            "rm -rf /tmp/test",
            "rm -fr /var/log",
            "rm -r -f dossier",
            "sudo rm --recursive /data",
            "Remove-Item C:\\Temp -Recurse -Force",
            "Remove-Item -Force -Recurse C:\\Temp",
            "rd /s /q C:\\Temp",
            "rmdir C:\\old /S",
            "del /f /s /q C:\\*.log",
            "format D: /q",
            "Format-Volume -DriveLetter E",
            "diskpart /s script.txt",
            "shutdown /r /t 0",
            "Restart-Computer -Force",
            "reg delete HKLM\\Software\\Test /f",
            "net user bob /delete",
            "Set-MpPreference -DisableRealtimeMonitoring $true",
            "netsh advfirewall set allprofiles state off",
            "iex (New-Object Net.WebClient).DownloadString('http://x')",
            "curl -s https://example.com/install.sh | bash",
            "vssadmin delete shadows /all",
            "chmod -R 777 /",
    })
    void detectsDangerousCommands(String command) {
        assertFalse(service.detectWarnings(command).isEmpty(), "Non détecté : " + command);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Get-Service | Format-Table",
            "ipconfig /all",
            "rm fichier.tmp",
            "Remove-Item C:\\Temp\\fichier.txt",
            "del C:\\Temp\\fichier.txt",
            "echo Le formatage est terminé",
            "Get-ChildItem -Recurse",
    })
    void ignoresHarmlessCommands(String command) {
        assertTrue(service.detectWarnings(command).isEmpty(), "Faux positif : " + command);
    }

    @Test
    void reportsLineNumberAndIgnoresComments() {
        String script = "# rm -rf / (exemple en commentaire)\nREM shutdown /s\nGet-Date\nshutdown /s /t 0";
        List<SecurityWarning> warnings = service.detectWarnings(script);
        assertEquals(1, warnings.size());
        assertEquals(4, warnings.get(0).lineNumber());
        assertEquals("shutdown /s /t 0", warnings.get(0).excerpt());
    }

    @Test
    void appliesCustomPatterns() {
        SecurityService custom = new SecurityService(() -> List.of("\\bStop-Service\\b"));
        List<SecurityWarning> warnings = custom.detectWarnings("stop-service Spooler");
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).rule().contains("Stop-Service"));
    }

    @Test
    void rejectsInvalidCustomPattern() {
        assertTrue(SecurityService.validatePattern("(non ferme").isPresent());
        assertTrue(SecurityService.validatePattern("\\bok\\b").isEmpty());
        // Un motif invalide déjà enregistré ne doit pas faire planter l'analyse.
        assertTrue(new SecurityService(() -> List.of("[")).detectWarnings("Get-Date").isEmpty());
    }
}
