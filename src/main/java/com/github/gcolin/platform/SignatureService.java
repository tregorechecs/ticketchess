package com.github.gcolin.platform;

import java.nio.file.Path;

public class SignatureService extends AbstractOrgImageFileService {

    public SignatureService() {
        super("signature", "png", 2 * 1024 * 1024, "Signature");
    }

    public Path getSignatureFile() {
        return getFile();
    }
}
