package com.urosporo.quarkus.vaadin.cdi;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;

import io.quarkus.arc.Unremovable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.urosporo.quarkus.vaadin.QuarkusNpmTemplateParser;
import com.urosporo.quarkus.vaadin.cdi.annotation.VaadinServiceEnabled;
import com.urosporo.quarkus.vaadin.cdi.annotation.VaadinServiceScoped;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.polymertemplate.TemplateParser;
import com.vaadin.flow.di.DefaultInstantiator;
import com.vaadin.flow.di.Instantiator;
import com.vaadin.flow.i18n.I18NProvider;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServiceInitListener;

@VaadinServiceScoped
@VaadinServiceEnabled
@SuppressWarnings("serial")
@Unremovable
public class QuarkusInstantiator implements Instantiator {

    private static final String CANNOT_USE_CDI_BEANS_FOR_I18N = "Cannot use CDI beans for I18N, falling back to the default behavior.";
    private static final String FALLING_BACK_TO_DEFAULT_INSTANTIATION = "Falling back to default instantiation.";

    private final AtomicBoolean i18NLoggingEnabled = new AtomicBoolean(true);
    private DefaultInstantiator delegate;

    @Inject
    BeanManager beanManager;

    public Class<? extends VaadinService> getServiceClass() {

        return QuarkusVaadinServletService.class;
    }

    public BeanManager getBeanManager() {

        return this.beanManager;
    }

    @Override
    public boolean init(final VaadinService service) {

        this.delegate = new DefaultInstantiator(service);
        return this.delegate.init(service) && getServiceClass().isAssignableFrom(service.getClass());
    }

    @Override
    public Stream<VaadinServiceInitListener> getServiceInitListeners() {

        return Stream.concat(this.delegate.getServiceInitListeners(), Stream.of(getBeanManager()::fireEvent));
    }

    @Override
    public <T> T getOrCreate(final Class<T> type) {

        return new BeanLookup<>(getBeanManager(), type)
                .setUnsatisfiedHandler(() -> getLogger().debug("'{}' is not a CDI bean. " + FALLING_BACK_TO_DEFAULT_INSTANTIATION, type.getName()))
                .setAmbiguousHandler(e -> getLogger().debug("Multiple CDI beans found. " + FALLING_BACK_TO_DEFAULT_INSTANTIATION, e))
                .lookupOrElseGet(() -> {
                    final T instance = this.delegate.getOrCreate(type);
                    // BeanProvider.injectFields(instance); // TODO maybe it could be fixed after Quarkus-Arc ticket
                    // https://github.com/quarkusio/quarkus/issues/2378 is done
                    return instance;
                });
    }

    @Override
    public I18NProvider getI18NProvider() {

        final BeanLookup<I18NProvider> lookup = new BeanLookup<>(getBeanManager(), I18NProvider.class, BeanLookup.SERVICE);
        if (this.i18NLoggingEnabled.compareAndSet(true, false)) {
            lookup.setUnsatisfiedHandler(
                    () -> getLogger().info("Can't find any @VaadinServiceScoped bean implementing '{}'. " + CANNOT_USE_CDI_BEANS_FOR_I18N,
                            I18NProvider.class.getSimpleName()))
                    .setAmbiguousHandler(e -> getLogger().warn("Found more beans for I18N. " + CANNOT_USE_CDI_BEANS_FOR_I18N, e));
        } else {
            lookup.setAmbiguousHandler(e -> {
            });
        }
        return lookup.lookupOrElseGet(this.delegate::getI18NProvider);
    }

    @Override
    public <T extends Component> T createComponent(final Class<T> componentClass) {

        return this.delegate.createComponent(componentClass);
    }

    @Override
    public TemplateParser getTemplateParser() {

        return QuarkusNpmTemplateParser.getInstance();
    }

    private static Logger getLogger() {

        return LoggerFactory.getLogger(QuarkusInstantiator.class);
    }
}
