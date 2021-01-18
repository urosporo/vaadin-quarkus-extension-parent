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

import javax.enterprise.context.spi.Contextual;
import javax.enterprise.inject.spi.BeanManager;

import com.urosporo.quarkus.vaadin.cdi.AbstractContext;
import com.urosporo.quarkus.vaadin.cdi.BeanProvider;
import com.urosporo.quarkus.vaadin.cdi.ContextualStorage;
import com.urosporo.quarkus.vaadin.cdi.annotation.UIScoped;
import com.urosporo.quarkus.vaadin.cdi.annotation.VaadinSessionScoped;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;

import io.quarkus.arc.Arc;
import io.quarkus.arc.Unremovable;

/**
 * UIScopedContext is the context for {@link UIScoped @UIScoped} beans.
 */
public class UIScopedContext extends AbstractContext {

    private ContextualStorageManager contextualStorageManager;

    @Override
    protected ContextualStorage getContextualStorage(final Contextual<?> contextual, final boolean createIfNotExist) {

        return this.contextualStorageManager.getContextualStorage(createIfNotExist);
    }

    private void init() {
        if (contextualStorageManager == null) {
            BeanManager beanManager = Arc.container().beanManager();
            this.contextualStorageManager = BeanProvider.getContextualReference(beanManager, ContextualStorageManager.class, false);
        }
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return UIScoped.class;
    }

    @Override
    public boolean isActive() {
        init();
        return VaadinSession.getCurrent() != null && UI.getCurrent() != null;
    }

    @VaadinSessionScoped
    @Unremovable
    public static class ContextualStorageManager extends AbstractContextualStorageManager<Integer> {

        public ContextualStorageManager() {

            // Session lock checked in VaadinSessionScopedContext while
            // getting the session attribute of this beans context.
            super(false);
        }

        public ContextualStorage getContextualStorage(final boolean createIfNotExist) {

            final Integer uiId = UI.getCurrent().getUIId();
            return super.getContextualStorage(uiId, createIfNotExist);
        }

        @Override
        protected ContextualStorage newContextualStorage(final Integer uiId) {

            UI.getCurrent().addDetachListener(this::destroy);
            return super.newContextualStorage(uiId);
        }

        private void destroy(final DetachEvent event) {

            final int uiId = event.getUI().getUIId();
            super.destroy(uiId);
        }
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
