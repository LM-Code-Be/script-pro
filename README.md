# ScriptPro

ScriptPro est une application desktop JavaFX pour gérer, exécuter et historiser des scripts IT PowerShell, CMD, Batch, Bash et Python.

## Prérequis

- Windows 10/11
- Java 21 ou supérieur
- Maven 3.9 ou supérieur

Installation rapide avec Scoop :

```powershell
scoop install openjdk maven
```

## Télécharger le projet

1. Télécharger le projet en ZIP ou cloner le dépôt.
2. Extraire le dossier sur le PC utilisateur.
3. Ouvrir PowerShell dans le dossier `ScriptPro`.

## Lancer l'application

```powershell
mvn clean javafx:run
```

Si `mvn` n'est pas reconnu avec Scoop :

```powershell
& "$HOME\scoop\apps\maven\current\bin\mvn.cmd" clean javafx:run
```

## Premier lancement

- La base locale `scriptpro.db` est créée automatiquement.
- Des catégories, variables et scripts de démonstration sont préchargés.
- Les exports peuvent être enregistrés dans le dossier choisi depuis l'application.

## Fonctionnalités clés

- Bibliothèque CRUD avec recherche, filtres, favoris, import et export PDF.
- Éditeur RichTextFX avec coloration, variables `{{IP}}`, `{{USERNAME}}`, `{{HOSTNAME}}`, `{{PATH}}`, `{{SERVICE}}`.
- Exécution via PowerShell, CMD, Batch, Bash ou Python.
- Historique complet avec sortie, erreurs, code retour, durée et export PDF.
- Documentation intégrée avec guide d'utilisation et FAQ.

Développé par Michael pour LM-Code — `lm-code.be` — 2026.
