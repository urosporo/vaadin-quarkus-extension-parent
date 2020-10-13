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

import javax.enterprise.context.spi.AlterableContext;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;

import com.urosporo.quarkus.vaadin.cdi.annotation.NormalRouteScoped;
import com.urosporo.quarkus.vaadin.cdi.annotation.RouteScoped;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableContext;

/**
 * Used to bind multiple scope annotations to a single context. Will delegate all context-related operations to it's underlying instance, apart from
 * getting the scope of the context.
 *
 */
public class NormalRouteContextWrapper implements AlterableContext, InjectableContext {

    private InjectableContext context;

    @Override
    public Class<? extends Annotation> getScope() {

        return NormalRouteScoped.class;
    }

    @Override
    public <T> T get(final Contextual<T> component, final CreationalContext<T> creationalContext) {

        return this.context.get(component, creationalContext);
    }

    @Override
    public <T> T get(final Contextual<T> component) {

        return this.context.get(component);
    }

    @Override
    public boolean isActive() {

        if (this.context == null) {
            this.context = Arc.container().getActiveContext(RouteScoped.class);
        }

        return this.context.isActive();
    }

    @Override
    public void destroy(final Contextual<?> contextual) {

        this.context.destroy(contextual);
    }

    @Override
    public void destroy() {

        this.context.destroy();
    }

    @Override
    public ContextState getState() {

        return this.context.getState();
    }
}
