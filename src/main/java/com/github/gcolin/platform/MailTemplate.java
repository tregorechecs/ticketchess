package com.github.gcolin.platform;

import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import java.io.IOException;
import java.io.StringWriter;

public class MailTemplate {

    private final MustacheFactory factory;

    public MailTemplate() {
        factory = new RootRelativeMustacheFactory("templates");
    }

    public String render(String name, Object data) throws IOException {
        String templateName = name;
        if (!templateName.endsWith(".mustache")) {
            templateName = templateName + ".mustache";
        }
        if (!templateName.startsWith("tmpl/")) {
            templateName = "tmpl/" + templateName;
        }
        Mustache mustache = factory.compile(templateName);
        StringWriter output = new StringWriter();
        mustache.execute(output, data).flush();
        return output.toString();
    }
}
