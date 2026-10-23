package dev.startexport;

public final class StarTExporterConfig {

    public static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("startexport.enabled", "false"));
    public static final String OUTPUT = System.getProperty("startexport.output", "exported");
    public static final boolean QUIT = Boolean.parseBoolean(System.getProperty("startexport.quit", "true"));

}
