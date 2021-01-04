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

package com.urosporo.quarkus.vaadin;

import static com.urosporo.quarkus.vaadin.DeploymentValidator.DeploymentProblem.ErrorCode.ABSENT_OWNER_OF_NON_ROUTE_COMPONENT;
import static com.urosporo.quarkus.vaadin.DeploymentValidator.DeploymentProblem.ErrorCode.NON_ROUTE_SCOPED_HAVE_OWNER;
import static com.urosporo.quarkus.vaadin.DeploymentValidator.DeploymentProblem.ErrorCode.NORMAL_SCOPED_COMPONENT;
import static com.urosporo.quarkus.vaadin.DeploymentValidator.DeploymentProblem.ErrorCode.OWNER_IS_NOT_ROUTE_COMPONENT;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;

import com.urosporo.quarkus.vaadin.DeploymentValidator.DeploymentProblem.ErrorCode;
import com.urosporo.quarkus.vaadin.cdi.annotation.NormalRouteScoped;
import com.urosporo.quarkus.vaadin.cdi.annotation.RouteScopeOwner;
import com.urosporo.quarkus.vaadin.cdi.annotation.RouteScoped;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.HasErrorParameter;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLayout;

@Dependent
class DeploymentValidator {

    static class BeanInfo {

        private final Bean<?> bean;
        private final Annotated annotated;

        BeanInfo(final Bean<?> bean, final Annotated annotated) {

            this.bean = bean;
            this.annotated = annotated;
        }

        Type getBaseType() {

            return this.annotated.getBaseType();
        }

        private boolean isNormalScoped(final BeanManager bm) {

            return bm.isNormalScope(this.bean.getScope());
        }

        private boolean isRouteScoped() {

            final Class<? extends Annotation> scope = this.bean.getScope();
            return scope.equals(RouteScoped.class) || scope.equals(NormalRouteScoped.class);
        }

        private boolean isComponent() {

            for (final Type type : this.bean.getTypes()) {
                if (type instanceof Class && Component.class.isAssignableFrom((Class) type)) {
                    return true;
                }
            }
            return false;
        }

        private Optional<RouteScopeOwner> getRouteScopeOwner() {

            for (final Annotation ann : this.bean.getQualifiers()) {
                if (ann instanceof RouteScopeOwner) {
                    return Optional.of((RouteScopeOwner) ann);
                }
            }
            return Optional.empty();
        }

    }

    /**
     * Represents a deployment problem to be passed to the container. Message and stacktrace will appear in server log. It is not thrown, or caught.
     */
    static class DeploymentProblem extends Throwable {

        enum ErrorCode {
            NORMAL_SCOPED_COMPONENT,
            NON_ROUTE_SCOPED_HAVE_OWNER,
            ABSENT_OWNER_OF_NON_ROUTE_COMPONENT,
            OWNER_IS_NOT_ROUTE_COMPONENT
        }

        private final Type baseType;
        private final ErrorCode errorCode;

        private DeploymentProblem(final String message, final Type baseType, final ErrorCode errorCode) {

            super(message);
            this.baseType = baseType;
            this.errorCode = errorCode;
        }

        /**
         * For testing purposes only.
         *
         * @return annotated base type of the invalid bean
         */
        Type getBaseType() {

            return this.baseType;
        }

        /**
         * For testing purposes only.
         *
         * @return errorCode of the invalid bean
         */
        ErrorCode getErrorCode() {

            return this.errorCode;
        }
    }

    private interface BeanValidator {

        boolean isInvalid(BeanInfo beanInfo);

        ErrorCode getErrorCode();

        String getErrorMessage(BeanInfo beanInfo);

    }

    private class NormalScopedComponentValidator implements BeanValidator {

        @Override
        public boolean isInvalid(final BeanInfo beanInfo) {

            return beanInfo.isComponent() && beanInfo.isNormalScoped(DeploymentValidator.this.beanManager);
        }

        @Override
        public ErrorCode getErrorCode() {

            return NORMAL_SCOPED_COMPONENT;
        }

        @Override
        public String getErrorMessage(final BeanInfo beanInfo) {

            return String.format("Normal scoped Vaadin components are not supported. " + "'%s' should not belong to a normal scope.",
                    beanInfo.getBaseType().getTypeName());
        }

    }

    private class OwnerIsNotRouteComponentValidator implements BeanValidator {

        @Override
        public boolean isInvalid(final BeanInfo beanInfo) {

            return beanInfo.isRouteScoped()
                    && beanInfo.getRouteScopeOwner().map(RouteScopeOwner::value).filter(DeploymentValidator::isNonRouteComponent).isPresent();
        }

        @Override
        public ErrorCode getErrorCode() {

            return OWNER_IS_NOT_ROUTE_COMPONENT;
        }

        @Override
        public String getErrorMessage(final BeanInfo beanInfo) {

            return String.format("'@%s' should define a route component on '%s'.", RouteScopeOwner.class.getSimpleName(),
                    beanInfo.getBaseType().getTypeName());
        }

    }

    private class AbsentOwnerOfNonRouteComponentValidator implements BeanValidator {

        @Override
        public boolean isInvalid(final BeanInfo beanInfo) {

            return beanInfo.isRouteScoped() && isNonRouteComponent(beanInfo.getBaseType()) && !beanInfo.getRouteScopeOwner().isPresent();
        }

        @Override
        public ErrorCode getErrorCode() {

            return ABSENT_OWNER_OF_NON_ROUTE_COMPONENT;
        }

        @Override
        public String getErrorMessage(final BeanInfo beanInfo) {

            return String.format("'%s' is not a route component, need a '@%s'.", beanInfo.getBaseType().getTypeName(),
                    RouteScopeOwner.class.getSimpleName());
        }

    }

    private class NonRouteScopedHaveOwnerValidator implements BeanValidator {

        @Override
        public boolean isInvalid(final BeanInfo beanInfo) {

            return !beanInfo.isRouteScoped() && beanInfo.getRouteScopeOwner().isPresent();
        }

        @Override
        public ErrorCode getErrorCode() {

            return NON_ROUTE_SCOPED_HAVE_OWNER;
        }

        @Override
        public String getErrorMessage(final BeanInfo beanInfo) {

            return String.format("'%s' should be '@%s' or '@%s' to have a '@%s'.", beanInfo.getBaseType().getTypeName(),
                    RouteScoped.class.getSimpleName(), NormalRouteScoped.class.getSimpleName(), RouteScopeOwner.class.getSimpleName());
        }

    }

    @Inject
    private BeanManager beanManager;

    private final List<BeanValidator> validators = Arrays.asList(new NormalScopedComponentValidator(), new AbsentOwnerOfNonRouteComponentValidator(),
            new OwnerIsNotRouteComponentValidator(), new NonRouteScopedHaveOwnerValidator());

    void validate(final Set<BeanInfo> infoSet, final Consumer<Throwable> problemConsumer) {

        infoSet.forEach(info -> validateBean(info, problemConsumer));
    }

    private void validateBean(final BeanInfo beanInfo, final Consumer<Throwable> problemConsumer) {

        this.validators.stream().filter(validator -> validator.isInvalid(beanInfo))
                .map(validator -> new DeploymentProblem(validator.getErrorMessage(beanInfo), beanInfo.getBaseType(), validator.getErrorCode()))
                .forEach(problemConsumer);
    }

    private static boolean isNonRouteComponent(final Type type) {

        if (!(type instanceof Class)) {
            return true;
        }
        final Class clazz = (Class) type;
        // RouteRegistry contains filtered route targets,
        // but we want to ignore filters here.
        return !clazz.isAnnotationPresent(Route.class)
                && !HasErrorParameter.class.isAssignableFrom(clazz)
                && !RouterLayout.class.isAssignableFrom(clazz);
    }

}
