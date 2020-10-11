package com.urosporo.quarkus.vaadin.cdi.context;

import java.lang.annotation.Annotation;
import java.util.Collections;

import javax.enterprise.context.spi.Contextual;
import javax.enterprise.inject.spi.BeanManager;

import com.urosporo.quarkus.vaadin.cdi.AbstractContext;
import com.urosporo.quarkus.vaadin.cdi.ContextUtils;
import com.urosporo.quarkus.vaadin.cdi.ContextualStorage;
import com.urosporo.quarkus.vaadin.cdi.annotation.VaadinSessionScoped;
import com.vaadin.flow.server.VaadinSession;

import io.quarkus.arc.Arc;

public class VaadinSessionScopedContext extends AbstractContext {

    private static final String ATTRIBUTE_NAME = VaadinSessionScopedContext.class.getName();

    @Override
    protected ContextualStorage getContextualStorage(final Contextual<?> contextual, final boolean createIfNotExist) {

        final VaadinSession session = VaadinSession.getCurrent();
        ContextualStorage storage = findContextualStorage(session);
        if (storage == null && createIfNotExist) {
            storage = new ContextualStorage(getBeanManager(), false);
            session.setAttribute(ATTRIBUTE_NAME, storage);
        }
        return storage;
    }

    private static ContextualStorage findContextualStorage(final VaadinSession session) {

        // session lock is checked inside
        return (ContextualStorage) session.getAttribute(ATTRIBUTE_NAME);
    }

    private BeanManager getBeanManager() {

        return Arc.container().beanManager();
    }

    @Override
    public Class<? extends Annotation> getScope() {

        return VaadinSessionScoped.class;
    }

    @Override
    public boolean isActive() {

        return VaadinSession.getCurrent() != null;
    }

    public static void destroy(final VaadinSession session) {

        final ContextualStorage storage = findContextualStorage(session);
        if (storage != null) {
            AbstractContext.destroyAllActive(storage);
        }
    }

    /**
     * Guess whether this context is undeployed.
     *
     * Tomcat expires sessions after contexts are undeployed. Need this guess to prevent exceptions when try to properly destroy contexts on session
     * expiration.
     *
     * @return true when context is not active, but sure it should
     */
    public static boolean guessContextIsUndeployed() {

        // Given there is a current VaadinSession, we should have an active context,
        // except we get here after the application is undeployed.
        return (VaadinSession.getCurrent() != null && !ContextUtils.isContextActive(VaadinSessionScoped.class));
    }

    @Override
    public void destroy() {

        destroyAllActive();
    }

    @Override
    public ContextState getState() {

        return Collections::emptyMap; // FIXME
    }
}
