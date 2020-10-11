package com.urosporo.quarkus.vaadin;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

import com.vaadin.flow.component.Component;

@ApplicationScoped
public class QuarkusBuildContext {

    private static final Logger LOGGER = Logger.getLogger(QuarkusBuildContext.class);

    private final List<String> routes = new ArrayList<>();

    public void registerRoute(final String routeAnnotatedBean) {

        addIfNotExist(routeAnnotatedBean, this.routes);
    }

    public List<Class<? extends Component>> getRoutes() {

        return this.routes.stream().map(this::findComponentClass).filter(Optional::isPresent).map(Optional::get).collect(toList());
    }

    private void addIfNotExist(final String annotatedBean, final List<String> annotatedBeans) {

        final boolean alreadyExist = annotatedBeans.contains(annotatedBean);
        if (!alreadyExist) {
            annotatedBeans.add(annotatedBean);
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<Class<? extends Component>> findComponentClass(final String componentClassName) {

        try {

            return of((Class<? extends Component>) Thread.currentThread().getContextClassLoader().loadClass(componentClassName));

        } catch (final ClassNotFoundException e) {

            LOGGER.warn("Couldn't found the Vaadin component-class of {}", componentClassName, e);

            return empty();
        }
    }
}
