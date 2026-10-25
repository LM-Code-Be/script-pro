package com.scriptpro.services;

import com.scriptpro.models.Script;
import com.scriptpro.models.ScriptLanguage;
import com.scriptpro.repositories.CategoryRepository;
import com.scriptpro.repositories.ScriptRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Règles métier de la bibliothèque : recherche, validation, duplication, archivage.
 *
 * @author LM-Code
 */
public final class ScriptService {
    private final ScriptRepository scriptRepository;
    private final CategoryRepository categoryRepository;

    public ScriptService(ScriptRepository scriptRepository, CategoryRepository categoryRepository) {
        this.scriptRepository = scriptRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<Script> findActiveScripts() {
        return scriptRepository.findAll(false);
    }

    public List<Script> findAllScripts() {
        return scriptRepository.findAll(true);
    }

    public List<Script> findFavorites() {
        return findActiveScripts().stream()
                .filter(Script::isFavorite)
                .toList();
    }

    /** Recherche un script par identifiant, y compris parmi les scripts archivés. */
    public Optional<Script> findById(Long id) {
        return id == null ? Optional.empty() : scriptRepository.findById(id);
    }

    /**
     * Recherche multicritère dans les scripts actifs.
     * Le texte libre porte sur le nom, la description, le contenu, la catégorie et les tags.
     *
     * @param tag tag exact à filtrer, ou vide/{@code null} pour ne pas filtrer
     */
    public List<Script> search(String text, ScriptLanguage language, String category, String tag, boolean favoritesOnly) {
        String normalizedText = normalize(text);
        String normalizedCategory = category == null ? "" : category.trim();
        String normalizedTag = normalize(tag).trim();
        return findActiveScripts().stream()
                .filter(script -> normalizedText.isBlank() || normalize(script.getName()).contains(normalizedText)
                        || normalize(script.getDescription()).contains(normalizedText)
                        || normalize(script.getContent()).contains(normalizedText)
                        || normalize(script.getCategory()).contains(normalizedText)
                        || normalize(script.getTagsText()).contains(normalizedText))
                .filter(script -> language == null || script.getLanguage() == language)
                .filter(script -> normalizedCategory.isBlank() || Objects.equals(script.getCategory(), normalizedCategory))
                .filter(script -> normalizedTag.isBlank() || script.getTags().contains(normalizedTag))
                .filter(script -> !favoritesOnly || script.isFavorite())
                .sorted(Comparator.comparing(Script::getUpdatedAt).reversed())
                .toList();
    }

    public Script save(Script script) {
        validate(script);
        categoryRepository.saveIfMissing(script.getCategory());
        return scriptRepository.save(script);
    }

    public Script duplicate(Script script) {
        return save(script.copyForDuplicate());
    }

    public void archive(Script script) {
        if (script.getId() != null) {
            scriptRepository.archive(script.getId(), true);
        }
    }

    public void delete(Script script) {
        if (script.getId() != null) {
            scriptRepository.delete(script.getId());
        }
    }

    public List<String> categories() {
        return categoryRepository.findAllNames();
    }

    /** Tous les tags utilisés par les scripts actifs, triés alphabétiquement. */
    public List<String> tags() {
        TreeSet<String> tags = new TreeSet<>();
        findActiveScripts().forEach(script -> tags.addAll(script.getTags()));
        return List.copyOf(tags);
    }

    private void validate(Script script) {
        if (script.getName() == null || script.getName().isBlank()) {
            throw new IllegalArgumentException("Le nom du script est obligatoire.");
        }
        if (script.getCategory() == null || script.getCategory().isBlank()) {
            script.setCategory("Général");
        }
        if (script.getLanguage() == null) {
            script.setLanguage(ScriptLanguage.POWERSHELL);
        }
        if (script.getContent() == null) {
            script.setContent("");
        }
        if (script.getAuthor() == null || script.getAuthor().isBlank()) {
            script.setAuthor(System.getProperty("user.name", "Administrateur"));
        }
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
