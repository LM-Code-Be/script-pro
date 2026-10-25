package com.scriptpro.services;

import com.scriptpro.models.SecurityWarning;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Analyse un script avant exécution et signale les commandes potentiellement destructrices.
 *
 * <p>Les règles sont des expressions régulières insensibles à la casse, appliquées ligne par
 * ligne. Elles couvrent les variantes d'écriture courantes ({@code rm -fr}, {@code rm -r -f},
 * {@code Remove-Item -Force -Recurse}…). L'utilisateur peut ajouter ses propres motifs dans
 * les paramètres. Il s'agit d'un garde-fou, pas d'une protection absolue : un script peut
 * toujours masquer une commande (variables, encodage base64…).</p>
 *
 * @author LM-Code
 */
public final class SecurityService {

    /** Règle de détection : un libellé pour l'utilisateur et le motif associé. */
    private record Rule(String label, Pattern pattern) {
        static Rule of(String label, String regex) {
            return new Rule(label, compile(regex));
        }
    }

    private static final List<Rule> BUILT_IN_RULES = List.of(
            // Fichiers
            Rule.of("Suppression récursive de fichiers (rm)",
                    "\\brm\\s+(?:\\S+\\s+)*?(?:-[a-z]*r[a-z]*|--recursive)\\b"),
            Rule.of("Suppression récursive de fichiers (Remove-Item)",
                    "\\b(?:Remove-Item|ri)\\b[^\\r\\n]*\\s-r(?:ecurse)?\\b"),
            Rule.of("Suppression de dossier avec sous-dossiers (rd /s)",
                    "\\b(?:rd|rmdir)\\b[^\\r\\n]*\\s/s\\b"),
            Rule.of("Suppression forcée ou récursive (del)",
                    "\\b(?:del|erase)\\b[^\\r\\n]*\\s/[sfq]\\b"),
            Rule.of("Effacement sécurisé de l'espace disque (cipher /w)",
                    "\\bcipher\\b[^\\r\\n]*\\s/w\\b"),
            Rule.of("Droits ouverts à tous en récursif (chmod -R 777)",
                    "\\bchmod\\s+-R\\s+0?777\\b"),
            // Disques et partitions
            Rule.of("Formatage de volume",
                    "\\bformat(?:\\.com)?\\s+[a-z]:|\\bFormat-Volume\\b"),
            Rule.of("Gestion des disques (diskpart, Clear-Disk…)",
                    "\\bdiskpart\\b|\\b(?:Clear-Disk|Initialize-Disk|Remove-Partition)\\b"),
            Rule.of("Création de système de fichiers ou écriture brute sur disque",
                    "\\bmkfs(?:\\.\\w+)?\\b|\\bdd\\b[^\\r\\n]*\\bof=/dev/"),
            // Système
            Rule.of("Arrêt ou redémarrage de la machine",
                    "\\bshutdown\\b|\\b(?:Stop|Restart)-Computer\\b|\\breboot\\b|\\bpoweroff\\b"),
            Rule.of("Modification du démarrage Windows (bcdedit)",
                    "\\bbcdedit\\b"),
            Rule.of("Suppression de clés de registre",
                    "\\breg\\s+delete\\b|\\bRemove-Item\\b[^\\r\\n]*\\bHK(?:LM|CU|CR|U|CC):"),
            Rule.of("Suppression de sauvegardes ou clichés instantanés",
                    "\\b(?:vssadmin|wbadmin)\\s+delete\\b"),
            // Sécurité et comptes
            Rule.of("Suppression de compte utilisateur",
                    "\\bnet\\s+user\\b[^\\r\\n]*\\s/del(?:ete)?\\b|\\bRemove-(?:LocalUser|ADUser)\\b|\\buserdel\\b"),
            Rule.of("Désactivation de l'antivirus Defender",
                    "\\bSet-MpPreference\\b[^\\r\\n]*-Disable\\w*\\s+(?:\\$true|1)\\b"),
            Rule.of("Désactivation du pare-feu",
                    "\\bnetsh\\s+advfirewall\\s+set\\b[^\\r\\n]*\\bstate\\s+off\\b|\\bSet-NetFirewallProfile\\b[^\\r\\n]*-Enabled\\s+(?:\\$?false|0)\\b"),
            // Code téléchargé ou dynamique
            Rule.of("Exécution de code dynamique (Invoke-Expression)",
                    "\\b(?:Invoke-Expression|iex)\\b"),
            Rule.of("Téléchargement exécuté directement (curl | sh)",
                    "\\b(?:curl|wget)\\b[^\\r\\n|]*\\|\\s*(?:sudo\\s+)?(?:ba|z)?sh\\b"),
            Rule.of("Bombe fork",
                    ":\\(\\)\\s*\\{\\s*:\\s*\\|\\s*:\\s*&\\s*}\\s*;\\s*:")
    );

    private final Supplier<List<String>> customPatterns;

    /**
     * @param customPatterns fournit, à chaque analyse, les motifs ajoutés par l'utilisateur
     *                       (lus dans les paramètres pour prendre en compte les changements immédiatement)
     */
    public SecurityService(Supplier<List<String>> customPatterns) {
        this.customPatterns = customPatterns;
    }

    public List<SecurityWarning> detectWarnings(String content) {
        List<Rule> rules = new ArrayList<>(BUILT_IN_RULES);
        for (String regex : customPatterns.get()) {
            // Un motif invalide est refusé à l'enregistrement ; on l'ignore ici par sécurité.
            if (validatePattern(regex).isEmpty()) {
                rules.add(new Rule("Motif personnalisé : " + regex, compile(regex)));
            }
        }

        List<SecurityWarning> warnings = new ArrayList<>();
        String[] lines = (content == null ? "" : content).split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (isComment(line)) {
                continue;
            }
            for (Rule rule : rules) {
                if (rule.pattern().matcher(line).find()) {
                    warnings.add(new SecurityWarning(rule.label(), index + 1, abbreviate(line.strip())));
                }
            }
        }
        return warnings;
    }

    /**
     * Vérifie qu'un motif personnalisé est une expression régulière valide.
     *
     * @return le message d'erreur, ou vide si le motif est correct
     */
    public static Optional<String> validatePattern(String regex) {
        try {
            compile(regex);
            return Optional.empty();
        } catch (PatternSyntaxException exception) {
            return Optional.of(exception.getDescription() + " (position " + exception.getIndex() + ")");
        }
    }

    private static Pattern compile(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /** Les lignes de commentaire (#, REM, ::) ne sont pas exécutées : on ne les signale pas. */
    private static boolean isComment(String line) {
        String trimmed = line.stripLeading().toLowerCase();
        return trimmed.startsWith("#") || trimmed.startsWith("::")
                || trimmed.equals("rem") || trimmed.startsWith("rem ") || trimmed.startsWith("@rem ");
    }

    private static String abbreviate(String line) {
        return line.length() <= 120 ? line : line.substring(0, 117) + "…";
    }
}
