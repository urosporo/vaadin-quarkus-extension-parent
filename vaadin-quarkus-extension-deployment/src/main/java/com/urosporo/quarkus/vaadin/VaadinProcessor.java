package com.urosporo.quarkus.vaadin;

import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import com.urosporo.quarkus.vaadin.cdi.QuarkusVaadinServlet;
import com.urosporo.quarkus.vaadin.cdi.annotation.NormalRouteScoped;
import com.urosporo.quarkus.vaadin.cdi.annotation.NormalUIScoped;
import com.urosporo.quarkus.vaadin.cdi.annotation.RouteScoped;
import com.urosporo.quarkus.vaadin.cdi.annotation.UIScoped;
import com.urosporo.quarkus.vaadin.cdi.annotation.VaadinServiceScoped;
import com.urosporo.quarkus.vaadin.cdi.annotation.VaadinSessionScoped;
import com.urosporo.quarkus.vaadin.cdi.context.NormalRouteContextWrapper;
import com.urosporo.quarkus.vaadin.cdi.context.NormalUIContextWrapper;
import com.urosporo.quarkus.vaadin.cdi.context.RouteScopedContext;
import com.urosporo.quarkus.vaadin.cdi.context.UIScopedContext;
import com.urosporo.quarkus.vaadin.cdi.context.VaadinServiceScopedContext;
import com.urosporo.quarkus.vaadin.cdi.context.VaadinSessionScopedContext;
import com.vaadin.flow.router.Route;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanArchiveIndexBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.BeanDefiningAnnotationBuildItem;
import io.quarkus.arc.deployment.ContextRegistrationPhaseBuildItem;
import io.quarkus.arc.deployment.ContextRegistrationPhaseBuildItem.ContextConfiguratorBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.undertow.deployment.ServletBuildItem;

/**
 *
 * @author Urosporo
 * @since 1.0.0
 *
 */
public class VaadinProcessor {

    private static final Logger LOGGER = Logger.getLogger(VaadinProcessor.class);

    private static final DotName ROUTE_ANNOTATION = DotName.createSimple(Route.class.getName());

    @BuildStep
    public void build(final BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer,
            final BuildProducer<BeanDefiningAnnotationBuildItem> additionalBeanDefiningAnnotationRegistry,
            final BuildProducer<FeatureBuildItem> featureProducer) {

        LOGGER.info("Add Feature");

        featureProducer.produce(new FeatureBuildItem("vaadin-framework"));

        additionalBeanProducer.produce(AdditionalBeanBuildItem.unremovableOf(QuarkusVaadinServlet.class));

        additionalBeanDefiningAnnotationRegistry.produce(new BeanDefiningAnnotationBuildItem(DotName.createSimple("com.vaadin.flow.router.Route")));
    }

    @BuildStep
    public ContextConfiguratorBuildItem registerVaadinServiceScopedContext(final ContextRegistrationPhaseBuildItem phase) {

        LOGGER.info("Register VaadinServiceScopedContext");

        return new ContextConfiguratorBuildItem(
                phase.getContext().configure(VaadinServiceScoped.class).normal().contextClass(VaadinServiceScopedContext.class));
    }

    @BuildStep
    public ContextConfiguratorBuildItem registerVaadinSessionScopedContext(final ContextRegistrationPhaseBuildItem phase) {

        LOGGER.info("Register VaadinSessionScopedContext");

        return new ContextConfiguratorBuildItem(
                phase.getContext().configure(VaadinSessionScoped.class).normal().contextClass(VaadinSessionScopedContext.class));
    }

    @BuildStep
    public ContextConfiguratorBuildItem registerUIScopedContext(final ContextRegistrationPhaseBuildItem phase) {

        LOGGER.info("Register UIScopedContext");

        return new ContextConfiguratorBuildItem(phase.getContext().configure(UIScoped.class).contextClass(UIScopedContext.class));
    }

    @BuildStep
    public ContextConfiguratorBuildItem registerNormalUIScopedContext(final ContextRegistrationPhaseBuildItem phase) {

        LOGGER.info("Register NormalUIScopedContext");

        return new ContextConfiguratorBuildItem(
                phase.getContext().configure(NormalUIScoped.class).normal().contextClass(NormalUIContextWrapper.class));
    }

    @BuildStep
    public ContextConfiguratorBuildItem registerRouteScopedContext(final ContextRegistrationPhaseBuildItem phase) {

        LOGGER.info("Register RouteScopedContext");

        return new ContextConfiguratorBuildItem(phase.getContext().configure(RouteScoped.class).contextClass(RouteScopedContext.class));
    }

    @BuildStep
    public ContextConfiguratorBuildItem registerNormalRouteScopedContext(final ContextRegistrationPhaseBuildItem phase) {

        LOGGER.info("Register NormalRouteScopedContext");

        return new ContextConfiguratorBuildItem(
                phase.getContext().configure(NormalRouteScoped.class).normal().contextClass(NormalRouteContextWrapper.class));
    }

    @BuildStep
    void mapVaadinServletPaths(final BuildProducer<ServletBuildItem> servletProducer) {

        LOGGER.info("Add QuarkusVaadinServlet");

        servletProducer.produce(ServletBuildItem.builder(QuarkusVaadinServlet.class.getName(), QuarkusVaadinServlet.class.getName()).addMapping("/*")
                .setAsyncSupported(true).build());
    }

    @BuildStep
    @Record(STATIC_INIT)
    void scanForRoutes(final BeanArchiveIndexBuildItem beanArchiveIndex, final BeanContainerBuildItem beanContainer,
            final BuildProducer<ReflectiveClassBuildItem> routeAnnotatedClassesProducer, final QuarkusVaadinRecorder recorder) {

        LOGGER.info("Scan for @Routes");

        scanForReflectiveBeans(beanArchiveIndex, routeAnnotatedClassesProducer, ROUTE_ANNOTATION, this::buildReflectiveClassBuildItem,
                target -> recorder.registerRoute(beanContainer.getValue(), target.toString()));
    }

    @BuildStep
    void registerForVaadinFlowReflection(final BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {

        LOGGER.info("Register Vaadin Flow 4 reflection");

        final ReflectiveClassBuildItem vaadinClassBuildItem = ReflectiveClassBuildItem
                .builder("com.vaadin.flow.component.UI", "com.vaadin.flow.component.PollEvent", "com.vaadin.flow.component.ClickEvent",
                        "com.vaadin.flow.component.CompositionEndEvent", "com.vaadin.flow.component.CompositionStartEvent",
                        "com.vaadin.flow.component.CompositionUpdateEvent", "com.vaadin.flow.component.KeyDownEvent",
                        "com.vaadin.flow.component.KeyPressEvent", "com.vaadin.flow.component.KeyUpEvent",
                        "com.vaadin.flow.component.splitlayout.GeneratedVaadinSplitLayout$SplitterDragendEvent",
                        "com.vaadin.flow.component.details.Details$OpenedChangeEvent", "com.vaadin.flow.component.details.Details",
                        "com.vaadin.flow.router.InternalServerError", "com.vaadin.flow.router.RouteNotFoundError", "com.vaadin.flow.theme.lumo.Lumo")
                .constructors(true).methods(true).build();

        reflectiveClass.produce(vaadinClassBuildItem);
    }

    @BuildStep
    void registerForAtmosphereReflection(final BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {

        LOGGER.info("Register Atmosphere 4 reflection");

        final ReflectiveClassBuildItem athmosClassBuildItem = ReflectiveClassBuildItem
                .builder("org.atmosphere.cpr.DefaultBroadcaster", "org.atmosphere.cpr.DefaultAtmosphereResourceFactory",
                        "org.atmosphere.cpr.DefaultBroadcasterFactory", "org.atmosphere.cpr.DefaultMetaBroadcaster",
                        "org.atmosphere.cpr.DefaultAtmosphereResourceSessionFactory", "org.atmosphere.util.VoidAnnotationProcessor",
                        "org.atmosphere.cache.UUIDBroadcasterCache", "org.atmosphere.websocket.protocol.SimpleHttpProtocol",
                        "org.atmosphere.interceptor.IdleResourceInterceptor", "org.atmosphere.interceptor.OnDisconnectInterceptor",
                        "org.atmosphere.interceptor.WebSocketMessageSuspendInterceptor", "org.atmosphere.interceptor.JavaScriptProtocol",
                        "org.atmosphere.interceptor.JSONPAtmosphereInterceptor", "org.atmosphere.interceptor.SSEAtmosphereInterceptor",
                        "org.atmosphere.interceptor.AndroidAtmosphereInterceptor", "org.atmosphere.interceptor.PaddingAtmosphereInterceptor",
                        "org.atmosphere.interceptor.CacheHeadersInterceptor", "org.atmosphere.interceptor.CorsInterceptor")
                .constructors(true).methods(true).build();

        reflectiveClass.produce(athmosClassBuildItem);
    }

    private void scanForReflectiveBeans(final BeanArchiveIndexBuildItem beanArchiveIndex,
            final BuildProducer<ReflectiveClassBuildItem> vaadinReflectiveClassProducer, final DotName annotationType,
            final Function<AnnotationTarget, ReflectiveClassBuildItem> buildItem, final Consumer<AnnotationTarget> recorder) {

        LOGGER.info("Scan for " + annotationType.local() + " annotated beans.");

        final IndexView indexView = beanArchiveIndex.getIndex();

        final Collection<AnnotationInstance> annotationInstances = indexView.getAnnotations(annotationType);

        LOGGER.info("Found " + annotationInstances.size() + " of " + annotationType.local() + " annotated beans.");

        for (final AnnotationInstance annotationInstance : annotationInstances) {

            recorder.accept(annotationInstance.target());
            vaadinReflectiveClassProducer.produce(buildItem.apply(annotationInstance.target()));

            LOGGER.info("Found "
                    + annotationType.toString()
                    + " annotation on class "
                    + annotationInstance.target().toString()
                    + ". Item will be registered to Vaadin.");

        }
    }

    private ReflectiveClassBuildItem buildReflectiveClassBuildItem(final AnnotationTarget target) {

        LOGGER.debug("Register reflective-item generated for " + target.toString());

        return new ReflectiveClassBuildItem(false, false, target.toString());
    }
}
