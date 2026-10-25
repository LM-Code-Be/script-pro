package com.scriptpro.services;

import com.scriptpro.models.VariableEntry;
import com.scriptpro.repositories.VariableRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class VariableService {
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([A-Za-z0-9_-]+)}}");

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
            variables.add(matcher.group(1).toUpperCase());
        }
        return variables;
    }

    public String applyVariables(String content, Map<String, String> values) {
        Matcher matcher = VARIABLE_PATTERN.matcher(content == null ? "" : content);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String value = values.get(matcher.group(1).toUpperCase());
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value == null ? matcher.group(0) : value));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
