package com.urosporo.quarkus.vaadin.cdi;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.BeanManager;

public class ContextualStorage implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<Object, ContextualInstanceInfo<?>> contextualInstances;

    private final boolean concurrent;

    /**
     * @param beanManager
     *            is needed for serialisation
     * @param concurrent
     *            whether the ContextualStorage might get accessed concurrently by different threads
     * @param passivationCapable
     *            whether the storage is for passivation capable Scopes
     */
    public ContextualStorage(final BeanManager beanManager, final boolean concurrent) {

        this.concurrent = concurrent;
        if (concurrent) {
            this.contextualInstances = new ConcurrentHashMap<>();
        } else {
            this.contextualInstances = new HashMap<>();
        }
    }

    /**
     * @return the underlying storage map.
     */
    public Map<Object, ContextualInstanceInfo<?>> getStorage() {

        return this.contextualInstances;
    }

    /**
     * @return whether the ContextualStorage might get accessed concurrently by different threads.
     */
    public boolean isConcurrent() {

        return this.concurrent;
    }

    /**
     *
     * @param bean
     * @param creationalContext
     * @param <T>
     * @return
     */
    public <T> T createContextualInstance(final Contextual<T> bean, final CreationalContext<T> creationalContext) {

        final Object beanKey = getBeanKey(bean);
        if (isConcurrent()) {
            // locked approach
            ContextualInstanceInfo<T> instanceInfo = new ContextualInstanceInfo<>();

            final ConcurrentMap<Object, ContextualInstanceInfo<?>> concurrentMap = (ConcurrentHashMap<Object, ContextualInstanceInfo<?>>) this.contextualInstances;

            final ContextualInstanceInfo<T> oldInstanceInfo = (ContextualInstanceInfo<T>) concurrentMap.putIfAbsent(beanKey, instanceInfo);

            if (oldInstanceInfo != null) {
                instanceInfo = oldInstanceInfo;
            }
            synchronized (instanceInfo) {
                T instance = instanceInfo.getContextualInstance();
                if (instance == null) {
                    instance = bean.create(creationalContext);
                    instanceInfo.setContextualInstance(instance);
                    instanceInfo.setCreationalContext(creationalContext);
                }

                return instance;
            }

        } else {
            // simply create the contextual instance
            final ContextualInstanceInfo<T> instanceInfo = new ContextualInstanceInfo<>();
            instanceInfo.setCreationalContext(creationalContext);
            instanceInfo.setContextualInstance(bean.create(creationalContext));

            this.contextualInstances.put(beanKey, instanceInfo);

            return instanceInfo.getContextualInstance();
        }
    }

    /**
     * If the context is a passivating scope then we return the passivationId of the Bean. Otherwise we use the Bean directly.
     *
     * @return the key to use in the context map
     */
    public <T> Object getBeanKey(final Contextual<T> bean) {

        return bean;
    }

    /**
     * Restores the Bean from its beanKey.
     *
     * @see #getBeanKey(javax.enterprise.context.spi.Contextual)
     */
    public Contextual<?> getBean(final Object beanKey) {

        return (Contextual<?>) beanKey;
    }
}
