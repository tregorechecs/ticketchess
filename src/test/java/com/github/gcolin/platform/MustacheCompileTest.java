package com.github.gcolin.platform;

import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MustacheCompileTest {

    @Test
    void allTemplatesCompile() throws Exception {
        MustacheFactory factory = new RootRelativeMustacheFactory("templates");
        Path root = Path.of("src/main/resources/templates");
        Assertions.assertTrue(Files.isDirectory(root), "templates root missing: " + root.toAbsolutePath());

        int[] counts = {0, 0};
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.toString().endsWith(".mustache")).forEach(p -> {
                counts[0]++;
                String name = root.relativize(p).toString().replace('\\', '/');
                Mustache mustache = factory.compile(name);
                Assertions.assertNotNull(mustache, name);
                StringWriter out = new StringWriter();
                Map<String, Object> model = new HashMap<>();
                model.put("contextPath", "");
                model.put("backgroundUrl", "/assets/6.jpg");
                model.put(
                        "i18n",
                        (java.util.function.Function<String, String>) key -> key == null ? "" : key);
                try {
                    mustache.execute(out, model).flush();
                    counts[1]++;
                } catch (Exception e) {
                    // partials may need more model keys; compile success is enough
                }
            });
        }
        Assertions.assertTrue(counts[0] > 20, "expected many templates, found " + counts[0]);
    }

    @Test
    void noTodoIfMarkersRemain() throws Exception {
        Path root = Path.of("src/main/resources/templates");
        StringBuilder leftovers = new StringBuilder();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.toString().endsWith(".mustache")).forEach(p -> {
                try {
                    String text = Files.readString(p);
                    if (text.contains("TODO_IF_") || text.contains("<!-- unresolved endif")) {
                        leftovers
                                .append(root.relativize(p).toString().replace('\\', '/'))
                                .append('\n');
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        Assertions.assertEquals("", leftovers.toString(), "templates still contain TODO_IF_ markers");
    }
}
