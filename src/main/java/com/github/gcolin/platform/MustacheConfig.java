package com.github.gcolin.platform;

import com.github.mustachejava.MustacheFactory;

public class MustacheConfig {

    private MustacheFactory factory;

    public void init() {
        factory = new RootRelativeMustacheFactory("templates");
    }

    public MustacheFactory getFactory() {
        return factory;
    }
}
