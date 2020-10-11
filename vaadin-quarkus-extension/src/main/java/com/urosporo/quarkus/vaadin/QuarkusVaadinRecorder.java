package com.urosporo.quarkus.vaadin;

import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class QuarkusVaadinRecorder {

    public void registerRoute(final BeanContainer container, final String routeAnnotatedClassName) {

        container.instance(QuarkusBuildContext.class).registerRoute(routeAnnotatedClassName);
    }
}
