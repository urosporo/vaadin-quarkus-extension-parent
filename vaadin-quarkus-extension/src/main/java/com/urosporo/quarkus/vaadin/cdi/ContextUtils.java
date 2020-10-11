package com.urosporo.quarkus.vaadin.cdi;

import java.lang.annotation.Annotation;

import javax.enterprise.inject.spi.BeanManager;

import io.quarkus.arc.Arc;

public class ContextUtils {

    private ContextUtils() {

        // prevent instantiation
    }

    /**
     * Checks if the context for the given scope annotation is active.
     *
     * @param scopeAnnotationClass
     *            The scope annotation (e.g. @RequestScoped.class)
     * @return If the context is active.
     */
    public static boolean isContextActive(final Class<? extends Annotation> scopeAnnotationClass) {

        return isContextActive(scopeAnnotationClass, Arc.container().beanManager());
    }

    /**
     * Checks if the context for the given scope annotation is active.
     *
     * @param scopeAnnotationClass
     *            The scope annotation (e.g. @RequestScoped.class)
     * @param beanManager
     *            The {@link BeanManager}
     * @return If the context is active.
     */
    public static boolean isContextActive(final Class<? extends Annotation> scopeAnnotationClass, final BeanManager beanManager) {

        try {
            if (beanManager.getContext(scopeAnnotationClass) == null || !beanManager.getContext(scopeAnnotationClass).isActive()) {
                return false;
            }
        }
        // catch (ContextNotActiveException e) // FIXME
        catch (final RuntimeException e) {
            return false;
        }

        return true;
    }

}
