package com.scriptpro.services;

import com.scriptpro.models.Script;
import com.scriptpro.models.ScriptLanguage;

import java.util.ArrayList;
import java.util.List;

public final class TemplateService {
    public List<Script> templates() {
        List<Script> templates = new ArrayList<>();
        templates.add(template(
                "Informations système",
                "Administration",
                ScriptLanguage.POWERSHELL,
                "Collecte les informations Windows essentielles.",
                "Get-ComputerInfo | Select-Object CsName, WindowsProductName, WindowsVersion, BiosSeralNumber\n" +
                        "Get-CimInstance Win32_Processor | Select-Object Name, NumberOfCores, NumberOfLogicalProcessors"));
        templates.add(template(
                "Services Windows",
                "Administration",
                ScriptLanguage.POWERSHELL,
                "Analyse l'état des services Windows.",
                "Get-Service | Sort-Object Status, DisplayName | Select-Object Status, Name, DisplayName"));
        templates.add(template(
                "Utilisateurs locaux",
                "Sécurité",
                ScriptLanguage.POWERSHELL,
                "Liste les comptes locaux disponibles.",
                "Get-LocalUser | Select-Object Name, Enabled, LastLogon"));
        templates.add(template(
                "Diagnostic IP",
                "Réseau",
                ScriptLanguage.CMD,
                "Affiche la configuration réseau et teste une cible.",
                "ipconfig /all\nping {{IP}}\ntracert {{IP}}"));
        templates.add(template(
                "Utilisation disque",
                "Maintenance",
                ScriptLanguage.BASH,
                "Contrôle les volumes et l'espace disponible.",
                "df -h\nlsblk"));
        templates.add(template(
                "Mémoire Linux",
                "Maintenance",
                ScriptLanguage.BASH,
                "Affiche la mémoire disponible.",
                "free -h\nvmstat 1 5"));
        templates.add(template(
                "Inventaire réseau Python",
                "Inventaire",
                ScriptLanguage.PYTHON,
                "Récupère le nom d'hôte et les adresses IP.",
                "import socket\n\nhostname = socket.gethostname()\nprint(hostname)\nfor address in socket.getaddrinfo(hostname, None):\n    print(address[4][0])"));
        return templates;
    }

    private Script template(String name, String category, ScriptLanguage language, String description, String content) {
        Script script = new Script();
        script.setName(name);
        script.setCategory(category);
        script.setLanguage(language);
        script.setDescription(description);
        script.setContent(content);
        script.setFavorite(false);
        return script;
    }
}
