package com.scriptpro.models;

/**
 * Commande sensible détectée dans un script avant son exécution.
 *
 * @param rule       description lisible de la règle déclenchée (ex. « Suppression récursive de fichiers »)
 * @param lineNumber numéro de ligne dans le script, à partir de 1
 * @param excerpt    contenu de la ligne concernée, sans espaces de début/fin
 * @author LM-Code
 */
public record SecurityWarning(String rule, int lineNumber, String excerpt) {
}
