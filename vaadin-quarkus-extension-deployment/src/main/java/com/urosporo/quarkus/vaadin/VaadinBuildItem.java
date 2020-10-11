package com.urosporo.quarkus.vaadin;

import io.quarkus.builder.item.MultiBuildItem;

public class VaadinBuildItem extends MultiBuildItem {

    private final Class<?> vaadinAnnotatedClass;

    public VaadinBuildItem(final Class<?> vaadinAnnotatedClass) {

        this.vaadinAnnotatedClass = vaadinAnnotatedClass;
    }

    public Class<?> getVaadinAnnotatedClass() {

        return this.vaadinAnnotatedClass;
    }
}
