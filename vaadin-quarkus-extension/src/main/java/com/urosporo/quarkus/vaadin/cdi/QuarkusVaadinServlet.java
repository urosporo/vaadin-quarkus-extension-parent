package com.urosporo.quarkus.vaadin.cdi;

import static java.util.Optional.empty;
import static java.util.Optional.of;

import java.io.IOException;
import java.util.Optional;

import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jboss.logging.Logger;

import com.urosporo.quarkus.vaadin.QuarkusBuildContext;
import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.router.RouteConfiguration;
import com.vaadin.flow.server.ServiceException;
import com.vaadin.flow.server.VaadinContext;
import com.vaadin.flow.server.VaadinServlet;
import com.vaadin.flow.server.VaadinServletConfig;
import com.vaadin.flow.server.VaadinServletService;
import com.vaadin.flow.server.startup.ApplicationRouteRegistry;

@SuppressWarnings("serial")
public class QuarkusVaadinServlet extends VaadinServlet {

    private static final Logger LOGGER = Logger.getLogger(QuarkusVaadinServlet.class);

    private static final ThreadLocal<Optional<String>> SERVLET_NAME = new ThreadLocal<>();

    @Inject
    BeanManager beanManager;

    @Inject
    QuarkusBuildContext vaadinQuarkusContext;

    /**
     * Name of the Vaadin servlet for the current thread.
     * <p>
     * Until VaadinService appears in CurrentInstance, it have to be used to get the servlet name.
     * <p>
     * This method is meant for internal use only.
     *
     * @see VaadinServlet#getCurrent()
     * @return currently processing vaadin servlet name
     */
    public static Optional<String> getCurrentServletName() {

        return SERVLET_NAME.get();
    }

    @Override
    public void init(final ServletConfig servletConfig) throws ServletException {

        try {

            final VaadinContext vaadinContext = new VaadinServletConfig(servletConfig).getVaadinContext();

            initializeRoutes(vaadinContext);

            SERVLET_NAME.set(of(servletConfig.getServletName()));

            super.init(servletConfig);

        } finally {
            SERVLET_NAME.set(empty());
        }
    }

    @Override
    protected void service(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {

        try {

            SERVLET_NAME.set(of(getServletName()));

            super.service(request, response);

        } finally {
            SERVLET_NAME.set(empty());
        }
    }

    @Override
    public void destroy() {

        SERVLET_NAME.remove();

        super.destroy();
    }

    @Override
    protected VaadinServletService createServletService(final DeploymentConfiguration configuration) throws ServiceException {

        final QuarkusVaadinServletService service = new QuarkusVaadinServletService(this, configuration, this.beanManager);
        service.init();
        return service;
    }

    private void initializeRoutes(final VaadinContext vaadinContext) {

        final ApplicationRouteRegistry routeRegistry = ApplicationRouteRegistry.getInstance(vaadinContext);

        final RouteConfiguration routeConfiguration = RouteConfiguration.forRegistry(routeRegistry);

        LOGGER.info(this.vaadinQuarkusContext.getRoutes().size() + " routes are there to register.");

        this.vaadinQuarkusContext.getRoutes().forEach(route -> {

            if (!routeConfiguration.isRouteRegistered(route)) {
                LOGGER.info("Register route for " + route.getClass());
                routeConfiguration.setAnnotatedRoute(route);
            }
        });
    }

}