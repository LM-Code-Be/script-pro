package com.scriptpro.controllers;

import com.scriptpro.ui.UiFactory;
import javafx.scene.control.Accordion;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import static com.scriptpro.ui.UiFactory.label;
import static com.scriptpro.ui.UiFactory.wrappedLabel;

/**
 * Guide d'utilisation intégré et FAQ.
 *
 * @author LM-Code
 */
final class DocumentationView {
    private final ViewContext context;

    DocumentationView(ViewContext context) {
        this.context = context;
    }

    void show() {
        VBox page = new VBox(18);
        page.getStyleClass().add("documentation-page");

        VBox hero = UiFactory.card();
        hero.getStyleClass().add("documentation-hero");
        Label title = label("ScriptPro centralise les scripts IT, leur exécution et leur traçabilité.", "hero-title");
        title.setWrapText(true);
        Label text = wrappedLabel("L'application est conçue pour les administrateurs systèmes, techniciens support et équipes IT qui veulent stocker, retrouver, exécuter et documenter leurs scripts depuis une interface locale, rapide et contrôlée.");
        hero.getChildren().addAll(new HBox(12, UiFactory.icon("fas-shield-alt"), title), text);

        HBox metrics = new HBox(14,
                metric("Bibliothèque", "Scripts classés, tagués, filtrés et exportables", "fas-folder-open"),
                metric("Éditeur", "RichTextFX, variables et console en direct", "fas-code"),
                metric("Traçabilité", "Historique SQLite, relance et exports PDF", "fas-history")
        );

        Accordion accordion = new Accordion();
        accordion.getStyleClass().add("documentation-accordion");
        TitledPane firstPane = pane("Rôle de l'application", "fas-info-circle",
                "ScriptPro sert de coffre opérationnel pour les scripts PowerShell, CMD, Batch, Bash et Python.",
                "Chaque script conserve son nom, son langage, sa catégorie, ses tags, sa description, son auteur, ses dates, son état favori et son contenu.",
                "Les données sont stockées localement dans SQLite afin de rester disponibles sans serveur externe.");
        accordion.getPanes().addAll(
                firstPane,
                pane("Dashboard", "fas-tachometer-alt",
                        "Le Dashboard affiche les KPI : scripts, favoris, exécutions du jour, échecs, catégories et historique.",
                        "Les blocs Activité récente et Derniers scripts utilisés permettent de reprendre rapidement le travail.",
                        "Utilisez les boutons Ouvrir pour basculer directement vers l'éditeur du script concerné."),
                pane("Bibliothèque", "fas-folder-open",
                        "La Bibliothèque est l'écran principal de gestion CRUD : nouveau script, modification, duplication, archivage, suppression, import et export.",
                        "Les filtres permettent de cibler un langage, une catégorie, un tag, les favoris ou une date de modification. La recherche porte aussi sur les tags.",
                        "Les exports disponibles couvrent le fichier source, le PDF du script sélectionné et le PDF de la bibliothèque filtrée."),
                pane("Éditeur", "fas-code",
                        "L'Éditeur propose une zone de code avec numéros de ligne, coloration syntaxique, recherche (occurrence suivante), remplacement et sauvegarde.",
                        "Tags : saisissez des mots-clés séparés par des virgules (ex. « réseau, urgence ») pour retrouver vos scripts plus vite.",
                        "Avant exécution, ScriptPro sauvegarde le script, vérifie que l'interpréteur est installé, détecte les commandes sensibles et demande les variables dynamiques.",
                        "La console affiche la sortie en direct ; les lignes d'erreur sont préfixées par [ERR]. Le bouton Arrêter interrompt le script et tous les programmes qu'il a lancés.",
                        "Raccourcis clavier : Ctrl+S sauvegarder · F5 exécuter · Maj+F5 arrêter · Ctrl+F rechercher."),
                pane("Exécutions et Historique", "fas-terminal",
                        "Exécutions liste les runs récents avec résultat, durée et code retour.",
                        "Historique ajoute les filtres par date, nom de script et résultat pour auditer rapidement une intervention.",
                        "Le bouton Relancer rouvre le script dans l'éditeur et l'exécute à nouveau (dans sa version actuelle).",
                        "Le bouton Exporter PDF génère un journal lisible, daté et signé LM-Code."),
                pane("Favoris, Modèles et Variables", "fas-star",
                        "Favoris donne un accès rapide aux scripts utilisés fréquemment.",
                        "Modèles propose des bases PowerShell, CMD, Bash et Python prêtes à adapter puis à enregistrer dans la bibliothèque.",
                        "Variables gère les placeholders comme {{IP}}, {{USERNAME}}, {{HOSTNAME}}, {{PATH}} et {{SERVICE}} avant exécution.",
                        "Une variable « Sensible » est saisie dans un champ masqué et sa valeur est remplacée par •••••• dans la console et l'historique."),
                pane("Import, export et sauvegarde", "fas-file-export",
                        "Import accepte les fichiers .ps1, .bat, .cmd, .sh et .py, en UTF-8 comme en ANSI (anciens scripts Windows).",
                        "Export source recrée le fichier script avec l'extension adaptée au langage.",
                        "Export PDF produit une fiche professionnelle pour un script, une bibliothèque filtrée ou un historique d'exécution.",
                        "Paramètres > Sauvegarde permet de copier toute la base dans un fichier .db, puis de la restaurer. Avant chaque restauration, la base actuelle est mise de côté dans le dossier « sauvegardes »."),
                pane("Sécurité", "fas-shield-alt",
                        "ScriptPro analyse chaque ligne avant exécution et signale les commandes sensibles : suppressions récursives (rm -rf, Remove-Item -Recurse, rd /s), formatage et partitionnement, arrêt de la machine, registre, comptes utilisateurs, désactivation de l'antivirus ou du pare-feu, code téléchargé et exécuté…",
                        "La confirmation indique la ligne concernée et la règle déclenchée. Vous pouvez ajouter vos propres motifs (expressions régulières) dans les Paramètres.",
                        "Ce contrôle est un garde-fou, pas une garantie : relisez toujours un script d'origine inconnue.",
                        "Toutes les exécutions sont journalisées avec utilisateur, date, résultat, durée, sortie et erreurs."),
                pane("FAQ", "fas-question-circle",
                        "Où sont les données ? Dans scriptpro.db à la racine du projet.",
                        "Comment lancer l'application ? Depuis le dossier du projet avec mvn clean javafx:run.",
                        "« Interpréteur introuvable » ? Installez le langage concerné (Python depuis python.org avec « Add to PATH », Git for Windows pour Bash), puis cliquez sur « Actualiser la détection » dans les Paramètres.",
                        "Dans quel dossier tournent les scripts ? Dans le dossier de travail défini dans les Paramètres (par défaut, votre dossier personnel).",
                        "Pourquoi des warnings Java au lancement ? Java 24 signale les appels natifs de JavaFX et SQLite. Ce n'est pas bloquant.",
                        "Peut-on utiliser l'application hors ligne ? Oui, après téléchargement des dépendances Maven et création de la base locale.",
                        "Que faire si un export échoue ? Vérifiez le dossier cible, les droits d'écriture et le nom du fichier PDF.")
        );
        accordion.setExpandedPane(firstPane);

        page.getChildren().addAll(hero, metrics, accordion);
        context.setView("documentation", "Documentation", "Guide complet, FAQ et bonnes pratiques d'utilisation", page);
    }

    private VBox metric(String title, String description, String iconLiteral) {
        VBox card = UiFactory.card();
        card.getStyleClass().add("doc-metric-card");
        card.getChildren().addAll(new HBox(10, UiFactory.icon(iconLiteral), label(title, "doc-metric-title")),
                wrappedLabel(description));
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private TitledPane pane(String title, String iconLiteral, String... paragraphs) {
        VBox content = new VBox(10);
        content.getStyleClass().add("doc-pane-content");
        for (String paragraph : paragraphs) {
            content.getChildren().add(wrappedLabel(paragraph));
        }
        TitledPane pane = new TitledPane(title, content);
        pane.setGraphic(UiFactory.icon(iconLiteral));
        pane.getStyleClass().add("doc-pane");
        return pane;
    }
}
