/*
 * Copyright 2000-2018 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.urosporo.quarkus.vaadin.cdi.context;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.function.Supplier;

import javax.enterprise.context.spi.Contextual;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.PassivationCapable;

import com.urosporo.quarkus.vaadin.cdi.AbstractContext;
import com.urosporo.quarkus.vaadin.cdi.BeanProvider;
import com.urosporo.quarkus.vaadin.cdi.ContextualStorage;
import com.urosporo.quarkus.vaadin.cdi.annotation.NormalUIScoped;
import com.urosporo.quarkus.vaadin.cdi.annotation.RouteScopeOwner;
import com.urosporo.quarkus.vaadin.cdi.annotation.RouteScoped;
import com.urosporo.quarkus.vaadin.cdi.annotation.UIScoped;

import io.quarkus.arc.Arc;
import io.quarkus.arc.Unremovable;

/**
 * Context for {@link RouteScoped @RouteScoped} beans.
 */
public class RouteScopedContext extends AbstractContext {

    @NormalUIScoped
    @Unremovable
    public static class ContextualStorageManager extends AbstractContextualStorageManager<Class> {

        public ContextualStorageManager() {

            // Session lock checked in VaadinSessionScopedContext while
            // getting the session attribute.
            super(false);
        }
    }

    private ContextualStorageManager contextManager;
    private Supplier<Boolean> isUIContextActive;
    private BeanManager beanManager;

    private void init() {
        if (this.beanManager == null) {
            this.beanManager = Arc.container().beanManager();
            this.contextManager = BeanProvider.getContextualReference(this.beanManager, ContextualStorageManager.class, false);
            this.isUIContextActive = () -> Arc.container().getActiveContext(UIScoped.class).isActive();
        }
    }

    @Override
    public Class<? extends Annotation> getScope() {

        return RouteScoped.class;
    }

    @Override
    public boolean isActive() {
        init();
        return this.isUIContextActive.get();
    }

    @Override
    protected ContextualStorage getContextualStorage(final Contextual<?> contextual, final boolean createIfNotExist) {
        init();
        final Class key = convertToKey(contextual);
        return this.contextManager.getContextualStorage(key, createIfNotExist);
    }

    private Class convertToKey(Contextual<?> contextual) {

        if (!(contextual instanceof Bean)) {
            if (contextual instanceof PassivationCapable) {
                final String id = ((PassivationCapable) contextual).getId();
                contextual = this.beanManager.getPassivationCapableBean(id);
            } else {
                throw new IllegalArgumentException(contextual.getClass().getName() + " is not of type " + Bean.class.getName());
            }
        }
        final Bean<?> bean = (Bean<?>) contextual;
        return bean.getQualifiers().stream().filter(annotation -> annotation instanceof RouteScopeOwner)
                .map(annotation -> (Class) (((RouteScopeOwner) annotation).value())).findFirst().orElse(bean.getBeanClass());
    }

    @Override
    public void destroy() {
        init();
        destroyAllActive();
    }

    @Override
    public ContextState getState() {
        init();
        return Collections::emptyMap; // FIXME
    }
}
