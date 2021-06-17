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

import static javax.enterprise.event.Reception.IF_EXISTS;

import java.lang.annotation.Annotation;
import java.util.Collections;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.event.Observes;

import com.urosporo.quarkus.vaadin.cdi.AbstractContext;
import com.urosporo.quarkus.vaadin.cdi.BeanProvider;
import com.urosporo.quarkus.vaadin.cdi.ContextualStorage;
import com.urosporo.quarkus.vaadin.cdi.QuarkusVaadinServlet;
import com.urosporo.quarkus.vaadin.cdi.annotation.VaadinServiceScoped;
import com.vaadin.flow.server.ServiceDestroyEvent;
import com.vaadin.flow.server.VaadinServlet;
import com.vaadin.flow.server.VaadinServletService;

import io.quarkus.arc.Arc;
import io.quarkus.arc.Unremovable;

/**
 * Context for {@link VaadinServiceScoped @VaadinServiceScoped} beans.
 */
public class VaadinServiceScopedContext extends AbstractContext {

    private ContextualStorageManager contextManager;

    @Override
    protected ContextualStorage getContextualStorage(final Contextual<?> contextual, final boolean createIfNotExist) {

        if (this.contextManager == null) {
            this.contextManager = BeanProvider.getContextualReference(Arc.container().beanManager(), ContextualStorageManager.class, false);
        }

        final QuarkusVaadinServlet servlet = (QuarkusVaadinServlet) VaadinServlet.getCurrent();
        String servletName;
        if (servlet != null) {
            servletName = servlet.getServletName();
        } else {
            servletName = QuarkusVaadinServlet.getCurrentServletName().get();
        }
        return this.contextManager.getContextualStorage(servletName, createIfNotExist);
    }

    @Override
    public Class<? extends Annotation> getScope() {

        return VaadinServiceScoped.class;
    }

    @Override
    public boolean isActive() {

        final VaadinServlet servlet = VaadinServlet.getCurrent();
        return servlet instanceof QuarkusVaadinServlet || (servlet == null && QuarkusVaadinServlet.getCurrentServletName() != null);
    }

    @ApplicationScoped
    @Unremovable
    public static class ContextualStorageManager extends AbstractContextualStorageManager<String> {

        public ContextualStorageManager() {

            super(true);
        }

        /**
         * Service destroy event observer.
         *
         * During application shutdown it is container specific whether this observer being called, or not. Application context destroy may happen
         * earlier, and cleanup done by {@link #destroyAll()}.
         *
         * @param event
         *            service destroy event
         */
        private void onServiceDestroy(@Observes(notifyObserver = IF_EXISTS) final ServiceDestroyEvent event) {

            if (!(event.getSource() instanceof VaadinServletService)) {
                return;
            }
            final VaadinServletService service = (VaadinServletService) event.getSource();
            final String servletName = service.getServlet().getServletName();
            destroy(servletName);
        }
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
