package com.scriptpro.tools;

import com.scriptpro.controllers.MainController;
import com.scriptpro.database.DatabaseManager;
import com.scriptpro.models.ExecutionRecord;
import com.scriptpro.models.ExecutionStatus;
import com.scriptpro.models.Script;
import com.scriptpro.models.ScriptLanguage;
import com.scriptpro.models.VariableEntry;
import com.scriptpro.repositories.CategoryRepository;
import com.scriptpro.repositories.ExecutionHistoryRepository;
import com.scriptpro.repositories.ScriptRepository;
import com.scriptpro.repositories.SettingsRepository;
import com.scriptpro.repositories.VariableRepository;
import com.scriptpro.services.BackupService;
import com.scriptpro.services.ExecutionService;
import com.scriptpro.services.ImportExportService;
import com.scriptpro.services.InterpreterLocator;
import com.scriptpro.services.ScriptRunner;
import com.scriptpro.services.ScriptService;
import com.scriptpro.services.SecurityService;
import com.scriptpro.services.SettingsService;
import com.scriptpro.services.TemplateService;
import com.scriptpro.services.VariableService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Génère les captures d'écran du README à partir d'une base de démonstration
 * entièrement fictive ({@code target/demo/scriptpro-demo.db}). La vraie base
 * {@code scriptpro.db} n'est jamais ouverte.
 *
 * <p>Lancement : voir la section « Captures d'écran » du README. Les images sont
 * écrites dans {@code docs/screenshots/}.</p>
 *
 * @author LM-Code
 */
public final class DemoScreenshots {
    private static final Path DATABASE = Path.of("target", "demo", "scriptpro-demo.db");
    private static final Path OUTPUT = Path.of("docs", "screenshots");

    /** Ordre des boutons du menu latéral, tel que déclaré dans {@link MainController#createView()}. */
    private static final String[] PAGES = {
            "01-dashboard", "02-bibliotheque", "03-editeur", "04-executions", "05-favoris",
            "06-modeles", "07-variables", "08-historique", "09-documentation", "10-parametres"
    };

    /** Chemin affiché à la place du dossier de démo réel (dans target/), plus parlant sur une capture. */
    private static final String DISPLAYED_FOLDER = "D:\\Scripts\\ScriptPro";

    private DemoScreenshots() {
    }

    public static void main(String[] args) {
        // Identité fictive : le nom d'utilisateur est repris dans l'historique et comme auteur.
        System.setProperty("user.name", "s.martin");
        Application.launch(App.class, args);
    }

    /** Application JavaFX qui prépare la démo, parcourt chaque écran puis se ferme. */
    public static final class App extends Application {
        @Override
        public void start(Stage stage) throws Exception {
            Files.deleteIfExists(DATABASE);
            DatabaseManager databaseManager = new DatabaseManager(DATABASE);
            databaseManager.initialize();

            ScriptRepository scriptRepository = new ScriptRepository(databaseManager);
            CategoryRepository categoryRepository = new CategoryRepository(databaseManager);
            ExecutionHistoryRepository historyRepository = new ExecutionHistoryRepository(databaseManager);
            VariableRepository variableRepository = new VariableRepository(databaseManager);
            SettingsService settingsService = new SettingsService(new SettingsRepository(databaseManager));
            ScriptService scriptService = new ScriptService(scriptRepository, categoryRepository);
            VariableService variableService = new VariableService(variableRepository);

            DemoData.fill(databaseManager, scriptService, variableService, settingsService, historyRepository);

            InterpreterLocator interpreterLocator = new InterpreterLocator();
            ExecutionService executionService = new ExecutionService(historyRepository, variableService,
                    settingsService, new ScriptRunner(interpreterLocator));
            MainController controller = new MainController(scriptService, executionService, new TemplateService(),
                    variableService, settingsService, new SecurityService(settingsService::customSecurityPatterns),
                    new ImportExportService(scriptService), historyRepository, interpreterLocator,
                    new BackupService(databaseManager));

            Parent root = controller.createView();
            Scene scene = new Scene(root, 1440, 900);
            scene.getStylesheets().add(DemoScreenshots.class.getResource("/styles/app.css").toExternalForm());
            stage.setTitle("ScriptPro");
            stage.setScene(scene);
            stage.show();

            Files.createDirectories(OUTPUT);
            List<Button> navigation = root.lookupAll(".nav-button").stream()
                    .filter(Button.class::isInstance).map(Button.class::cast).toList();

            // Le parcours tourne hors du thread JavaFX pour pouvoir attendre entre deux écrans.
            Thread tour = new Thread(() -> {
                try {
                    for (int index = 0; index < PAGES.length; index++) {
                        Button button = navigation.get(index);
                        String page = PAGES[index];
                        onFx(() -> {
                            resize(stage, scene, 900);
                            button.fire();
                            hideLocalPaths(root);
                        });
                        pause(1200); // fin des animations d'entrée (cascade, compteurs, barres)
                        if (page.equals("08-historique")) {
                            onFx(() -> selectHistoryRow(root, 7)); // un échec, pour montrer le détail des erreurs
                        }
                        fitWholePage(stage, scene, root);
                        capture(scene, OUTPUT.resolve(page + ".png"));
                    }
                    // Bonus : le tableau de bord en thème sombre.
                    onFx(() -> {
                        resize(stage, scene, 900);
                        controllerToggleTheme(root);
                        navigation.get(0).fire();
                    });
                    pause(1200);
                    fitWholePage(stage, scene, root);
                    capture(scene, OUTPUT.resolve("11-dashboard-sombre.png"));
                    onFx(() -> controllerToggleTheme(root)); // remet le thème clair dans la base de démo
                    pause(300);
                } catch (Exception exception) {
                    exception.printStackTrace();
                } finally {
                    executionService.shutdown();
                    Platform.exit();
                }
            }, "demo-screenshots");
            tour.setDaemon(true);
            tour.start();
        }

        /** Donne à la scène (zone intérieure de la fenêtre) la hauteur demandée. */
        private static void resize(Stage stage, Scene scene, double sceneHeight) {
            stage.setHeight(sceneHeight + stage.getHeight() - scene.getHeight());
        }

        /**
         * Agrandit la fenêtre pour que la page entière tienne sans défilement,
         * afin que la capture montre tout le contenu.
         */
        private static void fitWholePage(Stage stage, Scene scene, Parent root) throws InterruptedException {
            onFx(() -> root.lookupAll(".content-scroll").stream()
                    .filter(ScrollPane.class::isInstance).map(ScrollPane.class::cast).findFirst()
                    .ifPresent(scroll -> {
                        double viewport = scroll.getViewportBounds().getHeight();
                        double needed = scroll.getContent().prefHeight(scroll.getViewportBounds().getWidth());
                        if (needed > viewport + 1) {
                            resize(stage, scene, scene.getHeight() + needed - viewport);
                        }
                    }));
            pause(500);
        }

        /** Remplace, à l'affichage seulement, le chemin du dossier de démo par un chemin d'exemple. */
        private static void hideLocalPaths(Parent root) {
            String demoFolder = DATABASE.resolveSibling("Scripts").toAbsolutePath().toString();
            root.lookupAll(".text-field").stream()
                    .filter(TextField.class::isInstance).map(TextField.class::cast)
                    .filter(field -> field.getText() != null && field.getText().startsWith(demoFolder))
                    .forEach(field -> field.setText(field.getText().replace(demoFolder, DISPLAYED_FOLDER)));
        }

        /** Sélectionne une ligne du tableau d'historique pour afficher son détail. */
        private static void selectHistoryRow(Parent root, int row) {
            root.lookupAll(".table-view").stream()
                    .filter(TableView.class::isInstance).map(TableView.class::cast)
                    .findFirst().ifPresent(table -> table.getSelectionModel().select(row));
        }

        /** Clique sur le bouton « Thème » de la barre du haut. */
        private static void controllerToggleTheme(Parent root) {
            root.lookupAll(".ghost-button").stream()
                    .filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> "Sombre".equals(button.getText()) || "Clair".equals(button.getText()))
                    .findFirst().ifPresent(Button::fire);
        }
    }

    private static void capture(Scene scene, Path file) throws Exception {
        WritableImage[] image = new WritableImage[1];
        onFx(() -> image[0] = scene.snapshot(null));
        BufferedImage buffered = toBuffered(image[0]);
        ImageIO.write(buffered, "png", file.toFile());
        System.out.println("Capture : " + file);
    }

    private static BufferedImage toBuffered(WritableImage image) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = image.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffered.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        return buffered;
    }

    /** Exécute une action sur le thread JavaFX et attend qu'elle soit terminée. */
    private static void onFx(Runnable action) throws InterruptedException {
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                latch.countDown();
            }
        });
        latch.await();
    }

    private static void pause(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    /** Jeu de données fictif mais réaliste : un service informatique de PME. */
    private static final class DemoData {
        private DemoData() {
        }

        static void fill(DatabaseManager databaseManager, ScriptService scriptService, VariableService variableService,
                         SettingsService settingsService, ExecutionHistoryRepository historyRepository) throws Exception {
            // On remplace les scripts d'exemple livrés avec l'application.
            try (Connection connection = databaseManager.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("DELETE FROM favorites");
                statement.execute("DELETE FROM scripts");
            }

            settingsService.set("theme", "light");
            // Le dossier de travail doit exister, sinon l'application revient au dossier personnel.
            Path scriptsFolder = Files.createDirectories(DATABASE.resolveSibling("Scripts")).toAbsolutePath();
            settingsService.set("workingDirectory", scriptsFolder.toString());
            settingsService.set("exportPath", scriptsFolder.resolve("exports").toString());
            settingsService.set("executionTimeout", "120");
            settingsService.setCustomSecurityPatterns(List.of("\\bStop-Service\\b", "\\bDisable-ADAccount\\b"));

            Map<String, VariableEntry> variables = variableService.findAllByName();
            updateVariable(variableService, variables, "IP", "192.168.10.25");
            updateVariable(variableService, variables, "USERNAME", "s.martin");
            updateVariable(variableService, variables, "HOSTNAME", "PC-COMPTA-07");
            updateVariable(variableService, variables, "PATH", "D:\\Partages\\Comptabilite");
            addVariable(variableService, "DOMAINE", "corp.exemple.local", "Domaine Active Directory", false);
            addVariable(variableService, "SERVEUR_SQL", "SRV-SQL-01", "Serveur SQL de production", false);
            addVariable(variableService, "PARTAGE_BACKUP", "\\\\NAS-01\\sauvegardes", "Partage réseau des sauvegardes", false);
            addVariable(variableService, "JOURS_RETENTION", "30", "Durée de conservation des journaux", false);
            addVariable(variableService, "MOT_DE_PASSE_ADMIN", "", "Mot de passe du compte de service (saisi à l'exécution)", true);

            List<Script> scripts = new ArrayList<>();
            scripts.add(script("Purge des journaux IIS", "Maintenance", ScriptLanguage.POWERSHELL,
                    "Supprime les journaux IIS plus anciens que la durée de rétention.", "iis, nettoyage, serveur", false,
                    """
                    # Purge des journaux IIS au-delà de {{JOURS_RETENTION}} jours
                    $limite = (Get-Date).AddDays(-{{JOURS_RETENTION}})
                    $dossier = 'C:\\inetpub\\logs\\LogFiles'

                    $fichiers = Get-ChildItem $dossier -Filter *.log -Recurse |
                        Where-Object { $_.LastWriteTime -lt $limite }

                    $taille = ($fichiers | Measure-Object Length -Sum).Sum / 1MB
                    $fichiers | Remove-Item -WhatIf:$false
                    Write-Output ("{0} fichiers supprimés ({1:N1} Mo libérés)" -f $fichiers.Count, $taille)
                    """));
            scripts.add(script("Inventaire des postes du domaine", "Inventaire", ScriptLanguage.POWERSHELL,
                    "Liste les ordinateurs Active Directory avec leur système et leur dernière connexion.", "ad, inventaire, rapport", true,
                    """
                    Import-Module ActiveDirectory
                    Get-ADComputer -Filter * -Server {{DOMAINE}} -Properties OperatingSystem, LastLogonDate |
                        Sort-Object Name |
                        Select-Object Name, OperatingSystem, LastLogonDate |
                        Export-Csv "{{PATH}}\\inventaire-postes.csv" -NoTypeInformation -Encoding UTF8
                    """));
            scripts.add(script("Diagnostic réseau complet", "Réseau", ScriptLanguage.CMD,
                    "Configuration IP, résolution DNS et test de connectivité vers une cible.", "réseau, dépannage", true,
                    """
                    @echo off
                    echo === Configuration IP ===
                    ipconfig /all
                    echo === Résolution DNS ===
                    nslookup {{HOSTNAME}}
                    echo === Connectivité ===
                    ping -n 4 {{IP}}
                    tracert -d -h 10 {{IP}}
                    """));
            scripts.add(script("Sauvegarde SQL nocturne", "Administration", ScriptLanguage.POWERSHELL,
                    "Sauvegarde complète des bases de production vers le NAS.", "sql, sauvegarde, production", true,
                    """
                    $date = Get-Date -Format 'yyyy-MM-dd'
                    $cible = "{{PARTAGE_BACKUP}}\\$date"
                    New-Item -ItemType Directory -Path $cible -Force | Out-Null

                    foreach ($base in 'Compta', 'RH', 'Stock') {
                        Backup-SqlDatabase -ServerInstance {{SERVEUR_SQL}} -Database $base `
                            -BackupFile "$cible\\$base.bak" -CompressionOption On
                        Write-Output "Base $base sauvegardée"
                    }
                    """));
            scripts.add(script("Services automatiques arrêtés", "Administration", ScriptLanguage.POWERSHELL,
                    "Repère les services en démarrage automatique qui ne tournent pas.", "services, supervision", false,
                    """
                    Get-Service |
                        Where-Object { $_.StartType -eq 'Automatic' -and $_.Status -ne 'Running' } |
                        Select-Object Name, DisplayName, Status
                    """));
            scripts.add(script("Comptes AD inactifs", "Sécurité", ScriptLanguage.POWERSHELL,
                    "Comptes utilisateurs sans connexion depuis 90 jours.", "ad, audit, sécurité", false,
                    """
                    Search-ADAccount -AccountInactive -TimeSpan 90.00:00:00 -UsersOnly -Server {{DOMAINE}} |
                        Select-Object Name, SamAccountName, LastLogonDate |
                        Sort-Object LastLogonDate
                    """));
            scripts.add(script("Espace disque des serveurs Linux", "Maintenance", ScriptLanguage.BASH,
                    "Alerte si une partition dépasse 85 % d'occupation.", "linux, disque, supervision", false,
                    """
                    #!/usr/bin/env bash
                    seuil=85
                    df -h --output=target,pcent | tail -n +2 | while read -r point usage; do
                      valeur=${usage%\\%}
                      if [ "$valeur" -ge "$seuil" ]; then
                        echo "ALERTE : $point à $usage"
                      fi
                    done
                    """));
            scripts.add(script("Rapport des certificats expirant", "Sécurité", ScriptLanguage.PYTHON,
                    "Vérifie la date d'expiration des certificats TLS des sites internes.", "tls, certificats, rapport", true,
                    """
                    import socket, ssl
                    from datetime import datetime

                    sites = ["intranet.exemple.local", "helpdesk.exemple.local", "mail.exemple.local"]
                    for site in sites:
                        contexte = ssl.create_default_context()
                        with socket.create_connection((site, 443), timeout=5) as sock:
                            with contexte.wrap_socket(sock, server_hostname=site) as tls:
                                fin = datetime.strptime(tls.getpeercert()["notAfter"], "%b %d %H:%M:%S %Y %Z")
                                print(f"{site:<28} expire le {fin:%d/%m/%Y} ({(fin - datetime.now()).days} jours)")
                    """));
            scripts.add(script("Nettoyage des profils temporaires", "Maintenance", ScriptLanguage.BATCH,
                    "Vide les dossiers temporaires Windows et utilisateur.", "nettoyage, poste", false,
                    """
                    @echo off
                    echo Nettoyage de %TEMP%
                    del /q "%TEMP%\\*.tmp" 2>nul
                    echo Nettoyage de C:\\Windows\\Temp
                    del /q "C:\\Windows\\Temp\\*.log" 2>nul
                    echo Terminé.
                    """));
            scripts.add(script("Ports ouverts sur le serveur", "Réseau", ScriptLanguage.POWERSHELL,
                    "Liste les ports TCP en écoute avec le processus associé.", "réseau, sécurité, audit", false,
                    """
                    Get-NetTCPConnection -State Listen |
                        Select-Object LocalAddress, LocalPort,
                            @{ Name = 'Processus'; Expression = { (Get-Process -Id $_.OwningProcess).ProcessName } } |
                        Sort-Object LocalPort
                    """));
            scripts.add(script("État de santé du poste", "Inventaire", ScriptLanguage.POWERSHELL,
                    "Résumé rapide : système, mémoire, disques et dernier redémarrage.", "poste, inventaire, support", true,
                    """
                    # Résumé de l'état du poste {{HOSTNAME}}
                    $os = Get-CimInstance Win32_OperatingSystem
                    Write-Output "Système      : $($os.Caption) $($os.Version)"
                    Write-Output ("Mémoire libre: {0:N1} Go" -f ($os.FreePhysicalMemory / 1MB))
                    Write-Output "Démarré le   : $($os.LastBootUpTime)"

                    Get-CimInstance Win32_LogicalDisk -Filter "DriveType=3" | ForEach-Object {
                        Write-Output ("Disque {0}    : {1:N0} Go libres sur {2:N0} Go" -f $_.DeviceID, ($_.FreeSpace / 1GB), ($_.Size / 1GB))
                    }
                    """));
            for (Script script : scripts) {
                scriptService.save(script);
            }

            // Historique : une semaine d'exécutions, dont quelques échecs et un arrêt manuel.
            Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
            Object[][] runs = {
                    {10, "État de santé du poste", ExecutionStatus.SUCCESS, 2_340, 0, HEALTH_OUTPUT, ""},
                    {35, "Diagnostic réseau complet", ExecutionStatus.SUCCESS, 9_870, 0, PING_OUTPUT, ""},
                    {80, "Sauvegarde SQL nocturne", ExecutionStatus.SUCCESS, 184_200, 0, BACKUP_OUTPUT, ""},
                    {140, "Ports ouverts sur le serveur", ExecutionStatus.SUCCESS, 1_120, 0, "LocalPort Processus\n80   w3wp\n443  w3wp\n1433 sqlservr\n3389 svchost", ""},
                    {210, "Rapport des certificats expirant", ExecutionStatus.WARNING, 3_410, 0,
                            "intranet.exemple.local       expire le 14/03/2027 (140 jours)\nhelpdesk.exemple.local       expire le 12/11/2026 (18 jours)",
                            "mail.exemple.local : délai de connexion dépassé"},
                    {60 * 20, "Purge des journaux IIS", ExecutionStatus.SUCCESS, 4_820, 0, "312 fichiers supprimés (1 284,6 Mo libérés)", ""},
                    {60 * 22, "Comptes AD inactifs", ExecutionStatus.SUCCESS, 2_050, 0,
                            "Name            SamAccountName  LastLogonDate\nJulien Petit    j.petit         12/06/2026\nStagiaire 2025  stagiaire25     02/07/2026", ""},
                    {60 * 26, "Espace disque des serveurs Linux", ExecutionStatus.FAILURE, 640, 1, "",
                            "Aucun interpréteur Bash trouvé sur ce poste."},
                    {60 * 30, "Inventaire des postes du domaine", ExecutionStatus.SUCCESS, 12_480, 0, "148 ordinateurs exportés vers inventaire-postes.csv", ""},
                    {60 * 46, "Sauvegarde SQL nocturne", ExecutionStatus.SUCCESS, 179_900, 0, BACKUP_OUTPUT, ""},
                    {60 * 50, "Diagnostic réseau complet", ExecutionStatus.CANCELLED, 21_300, -1, "Traçage de l'itinéraire vers 192.168.10.25...", "Exécution arrêtée par l'utilisateur."},
                    {60 * 52, "Services automatiques arrêtés", ExecutionStatus.SUCCESS, 980, 0, "Name             Status\ngupdate          Stopped\nsppsvc           Stopped", ""},
                    {60 * 70, "Sauvegarde SQL nocturne", ExecutionStatus.FAILURE, 30_150, 1, "Base Compta sauvegardée",
                            "Backup-SqlDatabase : le chemin \\\\NAS-01\\sauvegardes est inaccessible."},
                    {60 * 75, "Nettoyage des profils temporaires", ExecutionStatus.SUCCESS, 1_730, 0, "Nettoyage de C:\\Windows\\Temp\nTerminé.", ""},
                    {60 * 96, "État de santé du poste", ExecutionStatus.SUCCESS, 2_210, 0, HEALTH_OUTPUT, ""},
                    {60 * 118, "Sauvegarde SQL nocturne", ExecutionStatus.SUCCESS, 181_400, 0, BACKUP_OUTPUT, ""},
                    {60 * 125, "Ports ouverts sur le serveur", ExecutionStatus.SUCCESS, 1_090, 0, "LocalPort Processus\n80   w3wp\n443  w3wp", ""},
                    {60 * 142, "Sauvegarde SQL nocturne", ExecutionStatus.SUCCESS, 176_800, 0, BACKUP_OUTPUT, ""},
                    {60 * 150, "Purge des journaux IIS", ExecutionStatus.SUCCESS, 5_120, 0, "287 fichiers supprimés (1 102,3 Mo libérés)", ""},
            };
            // Insertion de la plus ancienne à la plus récente pour garder des identifiants croissants.
            for (int index = runs.length - 1; index >= 0; index--) {
                Object[] run = runs[index];
                String name = (String) run[1];
                Script script = scripts.stream().filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
                ExecutionRecord record = new ExecutionRecord();
                record.setScriptId(script.getId());
                record.setScriptName(name);
                record.setLanguage(script.getLanguage());
                record.setStatus((ExecutionStatus) run[2]);
                record.setStartedAt(now.minus(Duration.ofMinutes((Integer) run[0])));
                record.setDurationMillis((Integer) run[3]);
                record.setExitCode((Integer) run[4]);
                record.setStdout((String) run[5]);
                record.setStderr((String) run[6]);
                record.setUserName(index % 4 == 3 ? "t.dubois" : "s.martin");
                historyRepository.save(record);
            }
        }

        private static final String HEALTH_OUTPUT = """
                Système      : Microsoft Windows 11 Professionnel 10.0.26100
                Mémoire libre: 9,4 Go
                Démarré le   : 24/10/2026 07:52:11
                Disque C:    : 182 Go libres sur 476 Go
                Disque D:    : 611 Go libres sur 931 Go""";

        private static final String PING_OUTPUT = """
                === Connectivité ===
                Envoi d'une requête 'Ping' 192.168.10.25 avec 32 octets de données :
                Réponse de 192.168.10.25 : octets=32 temps=1 ms TTL=128
                Réponse de 192.168.10.25 : octets=32 temps<1ms TTL=128
                Réponse de 192.168.10.25 : octets=32 temps=1 ms TTL=128
                Réponse de 192.168.10.25 : octets=32 temps<1ms TTL=128
                Paquets : envoyés = 4, reçus = 4, perdus = 0 (perte 0%)""";

        private static final String BACKUP_OUTPUT = "Base Compta sauvegardée\nBase RH sauvegardée\nBase Stock sauvegardée";

        private static Script script(String name, String category, ScriptLanguage language, String description,
                                     String tags, boolean favorite, String content) {
            Script script = new Script();
            script.setName(name);
            script.setCategory(category);
            script.setLanguage(language);
            script.setDescription(description);
            script.setTagsText(tags);
            script.setFavorite(favorite);
            script.setAuthor(favorite ? "s.martin" : "t.dubois");
            script.setContent(content.stripTrailing());
            return script;
        }

        private static void updateVariable(VariableService service, Map<String, VariableEntry> variables, String name, String value) {
            VariableEntry variable = variables.get(name);
            if (variable != null) {
                variable.setDefaultValue(value);
                service.save(variable);
            }
        }

        private static void addVariable(VariableService service, String name, String value, String description, boolean secret) {
            VariableEntry variable = new VariableEntry();
            variable.setName(name);
            variable.setDefaultValue(value);
            variable.setDescription(description);
            variable.setSecret(secret);
            service.save(variable);
        }
    }
}
