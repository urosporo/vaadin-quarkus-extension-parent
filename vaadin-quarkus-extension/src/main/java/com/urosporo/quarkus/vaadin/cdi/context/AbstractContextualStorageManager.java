package com.urosporo.quarkus.vaadin.cdi.context;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PreDestroy;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;

import com.urosporo.quarkus.vaadin.cdi.AbstractContext;
import com.urosporo.quarkus.vaadin.cdi.ContextualStorage;

public abstract class AbstractContextualStorageManager<K> implements Serializable {

    @Inject
    BeanManager beanManager;
    private final boolean concurrent;
    private final Map<K, ContextualStorage> storageMap;

    protected AbstractContextualStorageManager(final boolean concurrent) {

        if (concurrent) {
            this.storageMap = new ConcurrentHashMap<>();
        } else {
            this.storageMap = new HashMap<>();
        }
        this.concurrent = concurrent;
    }

    protected ContextualStorage getContextualStorage(final K key, final boolean createIfNotExist) {

        if (createIfNotExist) {
            return this.storageMap.computeIfAbsent(key, this::newContextualStorage);
        } else {
            return this.storageMap.get(key);
        }
    }

    protected ContextualStorage newContextualStorage(final K key) {

        // Not required by the spec, but in reality beans are PassivationCapable.
        // Even for non serializable bean classes.
        // CDI implementations use PassivationCapable beans,
        // because injecting non serializable proxies might block serialization of
        // bean instances in a passivation capable context.
        return new ContextualStorage(this.beanManager, this.concurrent);
    }

    @PreDestroy
    protected void destroyAll() {

        final Collection<ContextualStorage> storages = this.storageMap.values();
        for (final ContextualStorage storage : storages) {
            AbstractContext.destroyAllActive(storage);
        }
        this.storageMap.clear();
    }

    protected void destroy(final K key) {

        final ContextualStorage storage = this.storageMap.remove(key);
        if (storage != null) {
            AbstractContext.destroyAllActive(storage);
        }
    }

    protected Set<K> getKeySet() {

        return Collections.unmodifiableSet(this.storageMap.keySet());
    }

}
