package com.urosporo.quarkus.vaadin.cdi;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;

import io.quarkus.arc.impl.BeanManagerProvider;

public final class BeanProvider {

    private BeanProvider() {

        // this is a utility class which doesn't get instantiated.
    }

    /**
     * Get a Contextual Reference by its type and qualifiers. You can use this method to get contextual references of a given type. A "Contextual
     * Reference" is a proxy which will automatically resolve the correct contextual instance when you access any method.
     *
     * <p>
     * <b>Attention:</b> You shall not use this method to manually resolve a &#064;Dependent bean! The reason is that contextual instances usually
     * live in the well-defined lifecycle of their injection point (the bean they got injected into). But if we manually resolve a &#064;Dependent
     * bean, then it does <b>not</b> belong to such well defined lifecycle (because &#064;Dependent is not &#064;NormalScoped) and thus will not be
     * automatically destroyed at the end of the lifecycle. You need to manually destroy this contextual instance via
     * {@link javax.enterprise.context.spi.Contextual#destroy(Object, javax.enterprise.context.spi.CreationalContext)}. Thus you also need to manually
     * store the CreationalContext and the Bean you used to create the contextual instance.
     * </p>
     *
     * @param type
     *            the type of the bean in question
     * @param qualifiers
     *            additional qualifiers which further distinct the resolved bean
     * @param <T>
     *            target type
     *
     * @return the resolved Contextual Reference
     *
     * @throws IllegalStateException
     *             if the bean could not be found.
     * @see #getContextualReference(Class, boolean, Annotation...)
     */
    public static <T> T getContextualReference(final Class<T> type, final Annotation... qualifiers) {

        return getContextualReference(type, false, qualifiers);
    }

    /**
     * {@link #getContextualReference(Class, Annotation...)} which returns <code>null</code> if the 'optional' parameter is set to <code>true</code>.
     *
     * @param type
     *            the type of the bean in question
     * @param optional
     *            if <code>true</code> it will return <code>null</code> if no bean could be found or created. Otherwise it will throw an
     *            {@code IllegalStateException}
     * @param qualifiers
     *            additional qualifiers which distinguish the resolved bean
     * @param <T>
     *            target type
     *
     * @return the resolved Contextual Reference
     *
     * @see #getContextualReference(Class, Annotation...)
     */
    public static <T> T getContextualReference(final Class<T> type, final boolean optional, final Annotation... qualifiers) {

        final BeanManager beanManager = getBeanManager();

        return getContextualReference(beanManager, type, optional, qualifiers);
    }

    /**
     * {@link #getContextualReference(Class, Annotation...)} which returns <code>null</code> if the 'optional' parameter is set to <code>true</code>.
     * This method is intended for usage where the BeanManger is known, e.g. in Extensions.
     *
     * @param beanManager
     *            the BeanManager to use
     * @param type
     *            the type of the bean in question
     * @param optional
     *            if <code>true</code> it will return <code>null</code> if no bean could be found or created. Otherwise it will throw an
     *            {@code IllegalStateException}
     * @param qualifiers
     *            additional qualifiers which further distinct the resolved bean
     * @param <T>
     *            target type
     *
     * @return the resolved Contextual Reference
     *
     * @see #getContextualReference(Class, Annotation...)
     */
    public static <T> T getContextualReference(final BeanManager beanManager, final Class<T> type, final boolean optional,
            final Annotation... qualifiers) {

        final Set<Bean<?>> beans = beanManager.getBeans(type, qualifiers);

        if (beans == null || beans.isEmpty()) {
            if (optional) {
                return null;
            }

            throw new IllegalStateException("Could not find beans for Type=" + type + " and qualifiers:" + Arrays.toString(qualifiers));
        }

        return getContextualReference(type, beanManager, beans);
    }

    /**
     * Get a Contextual Reference by its EL Name. This only works for beans with the &#064;Named annotation.
     *
     * <p>
     * <b>Attention:</b> please see the notes on manually resolving &#064;Dependent beans in
     * {@link #getContextualReference(Class, java.lang.annotation.Annotation...)}!
     * </p>
     *
     * @param name
     *            the EL name of the bean
     *
     * @return the resolved Contextual Reference
     *
     * @throws IllegalStateException
     *             if the bean could not be found.
     * @see #getContextualReference(String, boolean)
     */
    public static Object getContextualReference(final String name) {

        return getContextualReference(name, false);
    }

    /**
     * Get a Contextual Reference by its EL Name. This only works for beans with the &#064;Named annotation.
     *
     * <p>
     * <b>Attention:</b> please see the notes on manually resolving &#064;Dependent beans in
     * {@link #getContextualReference(Class, java.lang.annotation.Annotation...)}!
     * </p>
     *
     * @param name
     *            the EL name of the bean
     * @param optional
     *            if <code>true</code> it will return <code>null</code> if no bean could be found or created. Otherwise it will throw an
     *            {@code IllegalStateException}
     *
     * @return the resolved Contextual Reference
     */
    public static Object getContextualReference(final String name, final boolean optional) {

        return getContextualReference(name, optional, Object.class);
    }

    /**
     * Get a Contextual Reference by its EL Name. This only works for beans with the &#064;Named annotation.
     *
     * <p>
     * <b>Attention:</b> please see the notes on manually resolving &#064;Dependent beans in
     * {@link #getContextualReference(Class, java.lang.annotation.Annotation...)}!
     * </p>
     *
     * @param name
     *            the EL name of the bean
     * @param optional
     *            if <code>true</code> it will return <code>null</code> if no bean could be found or created. Otherwise it will throw an
     *            {@code IllegalStateException}
     * @param type
     *            the type of the bean in question - use {@link #getContextualReference(String, boolean)} if the type is unknown e.g. in dyn.
     *            use-cases
     * @param <T>
     *            target type
     *
     * @return the resolved Contextual Reference
     */
    public static <T> T getContextualReference(final String name, final boolean optional, final Class<T> type) {

        return getContextualReference(getBeanManager(), name, optional, type);
    }

    /**
     * <p>
     * Get a Contextual Reference by its EL Name. This only works for beans with the &#064;Named annotation.
     * </p>
     *
     * <p>
     * <b>Attention:</b> please see the notes on manually resolving &#064;Dependent bean in
     * {@link #getContextualReference(Class, boolean, java.lang.annotation.Annotation...)}!
     * </p>
     *
     *
     * @param beanManager
     *            the BeanManager to use
     * @param name
     *            the EL name of the bean
     * @param optional
     *            if <code>true</code> it will return <code>null</code> if no bean could be found or created. Otherwise it will throw an
     *            {@code IllegalStateException}
     * @param type
     *            the type of the bean in question - use {@link #getContextualReference(String, boolean)} if the type is unknown e.g. in dyn.
     *            use-cases
     * @param <T>
     *            target type
     * @return the resolved Contextual Reference
     */
    public static <T> T getContextualReference(final BeanManager beanManager, final String name, final boolean optional, final Class<T> type) {

        final Set<Bean<?>> beans = beanManager.getBeans(name);

        if (beans == null || beans.isEmpty()) {
            if (optional) {
                return null;
            }

            throw new IllegalStateException("Could not find beans for Type=" + type + " and name:" + name);
        }

        return getContextualReference(type, beanManager, beans);
    }

    /**
     * Get the Contextual Reference for the given bean.
     *
     * <p>
     * <b>Attention:</b> please see the notes on manually resolving &#064;Dependent beans in
     * {@link #getContextualReference(Class, java.lang.annotation.Annotation...)}!
     * </p>
     *
     * @param type
     *            the type of the bean in question
     * @param bean
     *            bean definition for the contextual reference
     * @param <T>
     *            target type
     *
     * @return the resolved Contextual Reference
     */
    public static <T> T getContextualReference(final Class<T> type, final Bean<T> bean) {

        return getContextualReference(type, getBeanManager(), bean);
    }

    private static <T> T getContextualReference(final Class<T> type, final BeanManager beanManager, final Bean<?> bean) {

        // noinspection unchecked
        return getContextualReference(type, beanManager, Collections.<Bean<?>> singleton(bean));
    }

    /**
     * Internal helper method to resolve the right bean and resolve the contextual reference.
     *
     * @param type
     *            the type of the bean in question
     * @param beanManager
     *            current bean-manager
     * @param beans
     *            beans in question
     * @param <T>
     *            target type
     * @return the contextual reference
     */
    private static <T> T getContextualReference(final Class<T> type, final BeanManager beanManager, final Set<Bean<?>> beans) {

        final Bean<?> bean = beanManager.resolve(beans);

        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);

        @SuppressWarnings({ "unchecked" })
        final T result = (T) beanManager.getReference(bean, type, creationalContext);
        return result;
    }

    /**
     * Internal method to resolve the BeanManager via the {@link BeanManagerProvider}.
     *
     * @return current BeanManager
     */
    private static BeanManager getBeanManager() {

        return CDI.current().getBeanManager();
    }
}
