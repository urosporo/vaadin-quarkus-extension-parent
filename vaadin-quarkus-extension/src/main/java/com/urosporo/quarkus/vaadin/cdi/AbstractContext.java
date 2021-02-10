package com.urosporo.quarkus.vaadin.cdi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.ContextNotActiveException;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;

import io.quarkus.arc.InjectableContext;

public abstract class AbstractContext implements InjectableContext {

    /**
     * An implementation has to return the underlying storage which contains the items held in the Context.
     *
     * @param contextual
     *            dummy 4 deploy (TODO fix it)
     * @param createIfNotExist
     *            whether a ContextualStorage shall get created if it doesn't yet exist.
     * @return the underlying storage
     */
    protected abstract ContextualStorage getContextualStorage(Contextual<?> contextual, boolean createIfNotExist);

    protected List<ContextualStorage> getActiveContextualStorages() {

        final List<ContextualStorage> result = new ArrayList<>();
        result.add(getContextualStorage(null, false));
        return result;
    }

    @Override
    public <T> T get(final Contextual<T> bean) {

        checkActive();

        final ContextualStorage storage = getContextualStorage(bean, false);
        if (storage == null) {
            return null;
        }

        final Map<Object, ContextualInstanceInfo<?>> contextMap = storage.getStorage();
        final ContextualInstanceInfo<?> contextualInstanceInfo = contextMap.get(storage.getBeanKey(bean));
        if (contextualInstanceInfo == null) {
            return null;
        }

        return (T) contextualInstanceInfo.getContextualInstance();
    }

    @Override
    public <T> T get(final Contextual<T> bean, final CreationalContext<T> creationalContext) {

        if (creationalContext == null) {
            return get(bean);
        }

        checkActive();

        final ContextualStorage storage = getContextualStorage(bean, true);

        final Map<Object, ContextualInstanceInfo<?>> contextMap = storage.getStorage();
        final ContextualInstanceInfo<?> contextualInstanceInfo = contextMap.get(storage.getBeanKey(bean));

        if (contextualInstanceInfo != null) {
            @SuppressWarnings("unchecked")
            final T instance = (T) contextualInstanceInfo.getContextualInstance();

            if (instance != null) {
                return instance;
            }
        }

        return storage.createContextualInstance(bean, creationalContext);
    }

    /**
     * Destroy the Contextual Instance of the given Bean.
     *
     * @param bean
     *            dictates which bean shall get cleaned up
     */
    @Override
    public void destroy(final Contextual bean) {

        final ContextualStorage storage = getContextualStorage(bean, false);
        if (storage == null) {
            return;
        }

        final ContextualInstanceInfo<?> contextualInstanceInfo = storage.getStorage().remove(storage.getBeanKey(bean));

        if (contextualInstanceInfo == null) {
            return;
        }

        destroyBean(bean, contextualInstanceInfo);
    }

    /**
     * destroys all the Contextual Instances in the Storage returned by {@link #getContextualStorage(Contextual, boolean)}.
     */
    public void destroyAllActive() {

        final List<ContextualStorage> storages = getActiveContextualStorages();
        if (storages == null) {
            return;
        }

        for (final ContextualStorage storage : storages) {
            if (storage != null) {
                destroyAllActive(storage);
            }
        }
    }

    /**
     * Destroys all the Contextual Instances in the specified ContextualStorage. This is a static method to allow various holder objects to cleanup
     * properly in &#064;PreDestroy.
     *
     * @param storage
     *            dummy 4 deploy (TODO fix it)
     * 
     * @return dummy 4 deploy (TODO fix it)
     */
    public static Map<Object, ContextualInstanceInfo<?>> destroyAllActive(final ContextualStorage storage) {

        // drop all entries in the storage before starting with destroying the original entries
        final Map<Object, ContextualInstanceInfo<?>> contextMap = new HashMap<>(storage.getStorage());
        storage.getStorage().clear();

        for (final Map.Entry<Object, ContextualInstanceInfo<?>> entry : contextMap.entrySet()) {
            final Contextual bean = storage.getBean(entry.getKey());

            final ContextualInstanceInfo<?> contextualInstanceInfo = entry.getValue();
            destroyBean(bean, contextualInstanceInfo);
        }
        return contextMap;
    }

    public static void destroyBean(final Contextual bean, final ContextualInstanceInfo<?> contextualInstanceInfo) {

        bean.destroy(contextualInstanceInfo.getContextualInstance(), contextualInstanceInfo.getCreationalContext());
    }

    /**
     * Make sure that the Context is really active.
     *
     * @throws ContextNotActiveException
     *             if there is no active Context for the current Thread.
     */
    protected void checkActive() {

        if (!isActive()) {
            throw new ContextNotActiveException(
                    "CDI context with scope annotation @" + getScope().getName() + " is not active with respect to the current thread");
        }
    }

}
