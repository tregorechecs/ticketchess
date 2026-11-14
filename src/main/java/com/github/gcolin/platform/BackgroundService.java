package com.github.gcolin.platform;

import java.nio.file.Path;

public class BackgroundService extends AbstractOrgImageFileService {

    public BackgroundService() {
        super("background", "jpg", 5 * 1024 * 1024, "Background");
    }

    public Path getBackgroundFile() {
        return getFile();
    }
}
