package com.scriptpro.models;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Script stocké dans la bibliothèque ScriptPro.
 *
 * <p>En plus de sa catégorie (une seule par script), un script peut porter plusieurs
 * tags libres, par exemple « active-directory » ou « urgence ».</p>
 *
 * @author LM-Code
 */
public class Script {
    private Long id;
    private String name;
    private String category;
    private ScriptLanguage language;
    private String description;
    private String content;
    private String author;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean favorite;
    private boolean archived;
    private List<String> tags = new ArrayList<>();

    public Script() {
        Instant now = Instant.now();
        this.category = "Général";
        this.language = ScriptLanguage.POWERSHELL;
        this.description = "";
        this.content = "";
        this.author = System.getProperty("user.name", "Administrateur");
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Script(Long id, String name, String category, ScriptLanguage language, String description,
                  String content, String author, Instant createdAt, Instant updatedAt,
                  boolean favorite, boolean archived) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.language = language;
        this.description = description;
        this.content = content;
        this.author = author;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.favorite = favorite;
        this.archived = archived;
    }

    public Script copyForDuplicate() {
        Script duplicate = new Script();
        duplicate.setName(name + " - copie");
        duplicate.setCategory(category);
        duplicate.setLanguage(language);
        duplicate.setDescription(description);
        duplicate.setContent(content);
        duplicate.setAuthor(author);
        duplicate.setTags(tags);
        duplicate.setFavorite(false);
        return duplicate;
    }

    public String exportFileName() {
        String safeName = name == null || name.isBlank() ? "script" : name;
        safeName = safeName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return safeName + language.getExtension();
    }

    /**
     * Découpe une saisie libre (« réseau, Urgence ,  ad ») en liste de tags normalisés :
     * minuscules, sans espaces superflus, sans doublons et dans l'ordre de saisie.
     */
    public static List<String> parseTags(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        Arrays.stream(text.split("[,;]"))
                .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
                .filter(tag -> !tag.isEmpty())
                .forEach(unique::add);
        return new ArrayList<>(unique);
    }

    /** Représentation texte des tags, telle qu'affichée et stockée en base (« a, b, c »). */
    public String getTagsText() {
        return String.join(", ", tags);
    }

    public void setTagsText(String text) {
        this.tags = parseTags(text);
    }

    public List<String> getTags() {
        return List.copyOf(tags);
    }

    public void setTags(List<String> tags) {
        this.tags = tags == null ? new ArrayList<>() : parseTags(String.join(",", tags));
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public ScriptLanguage getLanguage() {
        return language;
    }

    public void setLanguage(ScriptLanguage language) {
        this.language = language;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }
}
