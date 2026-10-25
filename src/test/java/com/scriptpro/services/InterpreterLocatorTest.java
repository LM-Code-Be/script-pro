package com.scriptpro.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de la recherche d'interpréteurs avec un PATH simulé.
 *
 * @author LM-Code
 */
class InterpreterLocatorTest {
    @TempDir
    Path directory;

    @Test
    void findsCommandUsingPathExtOnWindows() throws Exception {
        Path tool = Files.createFile(directory.resolve("outil.exe"));
        InterpreterLocator locator = new InterpreterLocator(
                Map.of("Path", directory.toString(), "PATHEXT", ".COM;.EXE"), true);
        assertEquals(tool.toAbsolutePath(), locator.onPath("outil").orElseThrow());
        assertTrue(locator.onPath("absent").isEmpty());
    }

    @Test
    void convertsWindowsPathForWsl() {
        assertEquals("/mnt/c/Users/test/script.sh",
                ScriptRunner.ScriptFiles.toWslPath(Path.of("C:\\Users\\test\\script.sh")));
    }
}
