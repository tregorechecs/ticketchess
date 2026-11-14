package com.github.gcolin.platform.plugin;

/** Collects DDL statements contributed by plugins at startup. */
public interface SchemaBag {

    void add(String sql);
}
