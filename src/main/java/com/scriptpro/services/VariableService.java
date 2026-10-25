package com.scriptpro.services;

import com.scriptpro.models.VariableEntry;
import com.scriptpro.repositories.VariableRepository;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Gestion des variables dynamiques {@code {{NOM}}} : détection dans un script,
 * substitution avant exécution et masquage des valeurs sensibles.
 *
 * @author LM-Code
 */
public final class VariableService {
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([A-Za-z0-9_-]+)}}");
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]+");

    private final VariableRepository variableRepository;

    public VariableService(VariableRepository variableRepository) {
        this.variableRepository = variableRepository;
    }

    public List<VariableEntry> findAll() {
        return variableRepository.findAll();
    }

    public Map<String, VariableEntry> findAllByName() {
        return findAll().stream().collect(Collectors.toMap(VariableEntry::getName, variable -> variable));
    }

    public VariableEntry save(VariableEntry variable) {
        String name = variable.getName() == null ? "" : variable.getName().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Le nom de la variable est obligatoire.");
        }
        if (!VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Le nom ne peut contenir que des lettres, chiffres, « _ » et « - » (ex. NOM_SERVEUR).");
        }
        return variableRepository.save(variable);
    }

    public void delete(VariableEntry variable) {
        if (variable.getId() != null && !variable.isBuiltIn()) {
            variableRepository.delete(variable.getId());
        }
    }

    public Set<String> extractVariables(String content) {
        Set<String> variables = new LinkedHashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(content == null ? "" : content);
        while (matcher.find()) {
            variables.add(matcher.group(1).toUpperCase(Locale.ROOT));
        }
        return variables;
    }

    public String applyVariables(String content, Map<String, String> values) {
        Matcher matcher = VARIABLE_PATTERN.matcher(content == null ? "" : content);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1).toUpperCase(Locale.ROOT));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value == null ? matcher.group(0) : value));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /** Valeurs saisies pour des variables déclarées sensibles (non vides uniquement). */
    public List<String> secretValues(Map<String, String> values) {
        Map<String, VariableEntry> known = findAllByName();
        return values.entrySet().stream()
                .filter(entry -> known.containsKey(entry.getKey()) && known.get(entry.getKey()).isSecret())
                .map(Map.Entry::getValue)
                .filter(value -> value != null && !value.isEmpty())
                .distinct()
                .toList();
    }

    /**
     * Remplace chaque valeur sensible par {@link VariableEntry#MASK}. Les valeurs les plus
     * longues sont traitées en premier pour qu'un secret contenu dans un autre soit aussi masqué.
     */
    public static String mask(String text, Collection<String> secrets) {
        if (text == null || text.isEmpty() || secrets.isEmpty()) {
            return text;
        }
        String masked = text;
        List<String> ordered = secrets.stream()
                .filter(secret -> secret != null && !secret.isEmpty())
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        for (String secret : ordered) {
            masked = masked.replace(secret, VariableEntry.MASK);
        }
        return masked;
    }
}
