package dev.startexport;

import dev.startexport.exporters.ExportEMI;

public final class StarTExporterConfig {

    public static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("startexport.enabled", "false"));
    public static final String OUTPUT = System.getProperty("startexport.output", "exported");
    public static final boolean QUIT = Boolean.parseBoolean(System.getProperty("startexport.quit", "true"));
    public static final long RENDER_BATCH_NANO = Long.parseLong(System.getProperty("startexport.renderbatchdurationms", "50")) * 1_000_000L;
    public static final boolean DISABLE_IMAGE_RENDERING = Boolean.parseBoolean(System.getProperty("startexport.disableimagerendering", "false"));

    public static long getAllowedRenderTimeNano() {
        int activeRenderers = 0;
        if (!ExportEMI.isItemExportingDone()) {
            activeRenderers++;
        }
        if (!ExportEMI.isFluidExportingDone()) {
            activeRenderers++;
        }
        if (!ExportEMI.isRecipeExportingDone()) {
            activeRenderers++;
        }
        return activeRenderers == 0 ? 0 : RENDER_BATCH_NANO / activeRenderers;
    }

}
