package com.example.analyzer.web;

public class CommandResult {
    private boolean ok;
    private String output;
    private String error;
    private String pumlPath;
    private String svgPath;
    private String svgContent;

    public static CommandResult ok(String output) {
        CommandResult r = new CommandResult();
        r.ok = true;
        r.output = output;
        return r;
    }

    public static CommandResult error(String error) {
        CommandResult r = new CommandResult();
        r.ok = false;
        r.error = error;
        return r;
    }

    public boolean isOk() {
        return ok;
    }

    public String getOutput() {
        return output;
    }

    public String getError() {
        return error;
    }

    public String getPumlPath() {
        return pumlPath;
    }

    public void setPumlPath(String pumlPath) {
        this.pumlPath = pumlPath;
    }

    public String getSvgPath() {
        return svgPath;
    }

    public void setSvgPath(String svgPath) {
        this.svgPath = svgPath;
    }

    public String getSvgContent() {
        return svgContent;
    }

    public void setSvgContent(String svgContent) {
        this.svgContent = svgContent;
    }
}
