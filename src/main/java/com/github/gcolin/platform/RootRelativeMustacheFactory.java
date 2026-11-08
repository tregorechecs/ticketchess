package com.github.gcolin.platform;

import com.github.mustachejava.DefaultMustacheFactory;

/**
 * Resolves {@code {{> path/name}}} partials from the templates root instead of
 * relative to the including template's directory.
 */
public class RootRelativeMustacheFactory extends DefaultMustacheFactory {

    public RootRelativeMustacheFactory(String resourceRoot) {
        super(resourceRoot);
    }

    @Override
    public String resolvePartialPath(String dir, String name, String extension) {
        if (name.startsWith("./") || name.startsWith("../")) {
            return super.resolvePartialPath(dir, name, extension);
        }
        String path = name.startsWith("/") ? name.substring(1) : name;
        if (extension != null && !path.endsWith(extension)) {
            path = path + extension;
        }
        return path;
    }
}
