package com.scriptpro.models;

import java.time.Instant;

public class ExecutionRecord {
    private Long id;
    private Long scriptId;
    private String scriptName;
    private ScriptLanguage language;
    private ExecutionStatus status;
    private Instant startedAt;
    private long durationMillis;
    private int exitCode;
    private String stdout;
    private String stderr;
    private String userName;

    public ExecutionRecord() {
    }

    public ExecutionRecord(Long id, Long scriptId, String scriptName, ScriptLanguage language,
                           ExecutionStatus status, Instant startedAt, long durationMillis,
                           int exitCode, String stdout, String stderr, String userName) {
        this.id = id;
        this.scriptId = scriptId;
        this.scriptName = scriptName;
        this.language = language;
        this.status = status;
        this.startedAt = startedAt;
        this.durationMillis = durationMillis;
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
        this.userName = userName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getScriptId() {
        return scriptId;
    }

    public void setScriptId(Long scriptId) {
        this.scriptId = scriptId;
    }

    public String getScriptName() {
        return scriptName;
    }

    public void setScriptName(String scriptName) {
        this.scriptName = scriptName;
    }

    public ScriptLanguage getLanguage() {
        return language;
    }

    public void setLanguage(ScriptLanguage language) {
        this.language = language;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public String getStdout() {
        return stdout;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public void setStderr(String stderr) {
        this.stderr = stderr;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}
