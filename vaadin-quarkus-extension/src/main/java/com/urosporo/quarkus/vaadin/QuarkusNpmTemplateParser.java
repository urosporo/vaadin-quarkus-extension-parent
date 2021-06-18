package com.urosporo.quarkus.vaadin;

import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.polymertemplate.NpmTemplateParser;
import com.vaadin.flow.component.polymertemplate.TemplateParser;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.frontend.FrontendUtils;

public class QuarkusNpmTemplateParser extends NpmTemplateParser {

    private static final TemplateParser INSTANCE = new QuarkusNpmTemplateParser();

    public static TemplateParser getInstance() {

        return INSTANCE;
    }

    @Override
    protected String getSourcesFromTemplate(final VaadinService service, final String tag, final String url) {

        final InputStream content = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("/META-INF/resources/frontend/" + url.substring(2));
        if (content != null) {
            getLogger().debug("Found sources from the tag '{}' in the template '{}'", tag, url);
            return FrontendUtils.streamToString(content);
        }

        return super.getSourcesFromTemplate(service, tag, url);
    }

    private Logger getLogger() {

        return LoggerFactory.getLogger(QuarkusNpmTemplateParser.class.getName());
    }
}