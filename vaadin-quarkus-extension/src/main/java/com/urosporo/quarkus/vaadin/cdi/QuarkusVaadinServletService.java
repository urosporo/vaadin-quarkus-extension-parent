package com.urosporo.quarkus.vaadin.cdi;

import static com.urosporo.quarkus.vaadin.cdi.BeanLookup.SERVICE;

import java.io.Serializable;
import java.util.Optional;
import java.util.Set;

import javax.enterprise.context.spi.Context;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.AmbiguousResolutionException;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.urosporo.quarkus.vaadin.cdi.annotation.VaadinServiceScoped;
import com.urosporo.quarkus.vaadin.cdi.context.VaadinSessionScopedContext;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.PollEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.di.Instantiator;
import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationListener;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterListener;
import com.vaadin.flow.router.BeforeLeaveEvent;
import com.vaadin.flow.router.BeforeLeaveListener;
import com.vaadin.flow.router.ListenerPriority;
import com.vaadin.flow.server.ErrorHandler;
import com.vaadin.flow.server.ServiceDestroyEvent;
import com.vaadin.flow.server.ServiceException;
import com.vaadin.flow.server.SessionDestroyEvent;
import com.vaadin.flow.server.SessionInitEvent;
import com.vaadin.flow.server.SystemMessagesProvider;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServletService;
import com.vaadin.flow.server.VaadinSession;

import io.quarkus.arc.Arc;

public class QuarkusVaadinServletService extends VaadinServletService {

    private final QuarkusVaadinServiceDelegate delegate;

    public QuarkusVaadinServletService(final QuarkusVaadinServlet servlet, final DeploymentConfiguration configuration,
            final BeanManager beanManager) {

        super(servlet, configuration);
        this.delegate = new QuarkusVaadinServiceDelegate(this, beanManager);
    }

    @Override
    public void init() throws ServiceException {

        this.delegate.init();
        super.init();
    }

    @Override
    public void fireUIInitListeners(final UI ui) {

        this.delegate.addUIListeners(ui);
        super.fireUIInitListeners(ui);
    }

    @Override
    public Optional<Instantiator> loadInstantiators() throws ServiceException {

        final BeanManager beanManager = this.delegate.getBeanManager();

        final Set<Bean<?>> beans = beanManager.getBeans(Instantiator.class, SERVICE);
        if (beans == null || beans.isEmpty()) {
            throw new ServiceException("Cannot init VaadinService " + "because no CDI instantiator bean found.");
        }
        final Bean<Instantiator> bean;
        try {
            // noinspection unchecked
            bean = (Bean<Instantiator>) beanManager.resolve(beans);
        } catch (final AmbiguousResolutionException e) {
            throw new ServiceException("There are multiple eligible CDI " + Instantiator.class.getSimpleName() + " beans.", e);
        }

        // Return the contextual instance (rather than CDI proxy) as it will be
        // stored inside VaadinService. Not relying on the proxy allows
        // accessing VaadinService::getInstantiator even when
        // VaadinServiceScopedContext is not active
        final CreationalContext<Instantiator> creationalContext = beanManager.createCreationalContext(bean);
        final Context context = beanManager.getContext(VaadinServiceScoped.class);
        final Instantiator instantiator = context.get(bean, creationalContext);

        if (!instantiator.init(this)) {
            final Class<?> unproxiedClass = ProxyUtils.getUnproxiedClass(instantiator.getClass());
            throw new ServiceException("Cannot init VaadinService because " + unproxiedClass.getName() + " CDI bean init()" + " returned false.");
        }
        return Optional.of(instantiator);
    }

    @Override
    public QuarkusVaadinServlet getServlet() {

        return (QuarkusVaadinServlet) super.getServlet();
    }

    /**
     * This class implements the actual instantiation and event brokering functionality of {@link QuarkusVaadinServletService}.
     */
    public static class QuarkusVaadinServiceDelegate implements Serializable {

        private final VaadinService vaadinService;

        private transient BeanManager beanManager;

        private final UIEventListener uiEventListener;

        public QuarkusVaadinServiceDelegate(final VaadinService vaadinService, final BeanManager beanManager) {

            this.beanManager = beanManager;
            this.vaadinService = vaadinService;

            this.uiEventListener = new UIEventListener(this);
        }

        public void init() throws ServiceException {

            lookup(SystemMessagesProvider.class).ifPresent(this.vaadinService::setSystemMessagesProvider);
            this.vaadinService.addUIInitListener(e -> getBeanManager().fireEvent(e));
            this.vaadinService.addSessionInitListener(this::sessionInit);
            this.vaadinService.addSessionDestroyListener(this::sessionDestroy);
            this.vaadinService.addServiceDestroyListener(this::fireCdiDestroyEvent);
        }

        public void addUIListeners(final UI ui) {

            ui.addAfterNavigationListener(this.uiEventListener);
            ui.addBeforeLeaveListener(this.uiEventListener);
            ui.addBeforeEnterListener(this.uiEventListener);
            ui.addPollListener(this.uiEventListener);
        }

        public <T> Optional<T> lookup(final Class<T> type) throws ServiceException {

            try {
                final T instance = new BeanLookup<>(getBeanManager(), type, SERVICE).lookup();
                return Optional.ofNullable(instance);
            } catch (final AmbiguousResolutionException e) {
                throw new ServiceException("There are multiple eligible CDI " + type.getSimpleName() + " beans.", e);
            }
        }

        public BeanManager getBeanManager() {

            if (this.beanManager == null) {
                this.beanManager = Arc.container().beanManager();
            }
            return this.beanManager;
        }

        private void sessionInit(final SessionInitEvent sessionInitEvent) throws ServiceException {

            final VaadinSession session = sessionInitEvent.getSession();
            lookup(ErrorHandler.class).ifPresent(session::setErrorHandler);
            getBeanManager().fireEvent(sessionInitEvent);
        }

        private void sessionDestroy(final SessionDestroyEvent sessionDestroyEvent) {

            getBeanManager().fireEvent(sessionDestroyEvent);
            if (VaadinSessionScopedContext.guessContextIsUndeployed()) {
                // Happens on tomcat when it expires sessions upon undeploy.
                // beanManager.getPassivationCapableBean returns null for
                // passivation id,
                // so we would get an NPE from AbstractContext.destroyAllActive
                getLogger().warn("VaadinSessionScoped context does not exist. "
                        + "Maybe application is undeployed."
                        + " Can't destroy VaadinSessionScopedContext.");
                return;
            }
            getLogger().debug("VaadinSessionScopedContext destroy");
            VaadinSessionScopedContext.destroy(sessionDestroyEvent.getSession());
        }

        private void fireCdiDestroyEvent(final ServiceDestroyEvent event) {

            try {
                getBeanManager().fireEvent(event);
            } catch (final Exception e) {
                // During application shutdown on TomEE 7,
                // beans are lost at this point.
                // Does not throw an exception, but catch anything just to be sure.
                getLogger().warn("Error at destroy event distribution with CDI.", e);
            }
        }

        private static Logger getLogger() {

            return LoggerFactory.getLogger(QuarkusVaadinServiceDelegate.class);
        }
    }

    /**
     * Static listener class, to avoid registering the whole service instance.
     */
    @ListenerPriority(-100) // navigation event listeners are last by default
    private static class UIEventListener
            implements AfterNavigationListener, BeforeEnterListener, BeforeLeaveListener, ComponentEventListener<PollEvent> {

        private final QuarkusVaadinServiceDelegate delegate;

        private UIEventListener(final QuarkusVaadinServiceDelegate delegate) {

            this.delegate = delegate;
        }

        @Override
        public void afterNavigation(final AfterNavigationEvent event) {

            this.delegate.getBeanManager().fireEvent(event);
        }

        @Override
        public void beforeEnter(final BeforeEnterEvent event) {

            this.delegate.getBeanManager().fireEvent(event);
        }

        @Override
        public void beforeLeave(final BeforeLeaveEvent event) {

            this.delegate.getBeanManager().fireEvent(event);
        }

        @Override
        public void onComponentEvent(final PollEvent event) {

            this.delegate.getBeanManager().fireEvent(event);
        }
    }
}