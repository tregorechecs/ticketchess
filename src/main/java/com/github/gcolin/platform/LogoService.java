package com.github.gcolin.platform;

import java.nio.file.Path;

public class LogoService extends AbstractOrgImageFileService {

    public LogoService() {
        super("logo", "png", 2 * 1024 * 1024, "Logo");
    }

    public Path getLogoFile() {
        return getFile();
    }
}
