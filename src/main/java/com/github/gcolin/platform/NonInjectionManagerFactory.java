package com.github.gcolin.platform;

import jakarta.annotation.Priority;
import jakarta.ws.rs.core.Configuration;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.internal.inject.InjectionManagerFactory;

/**
 * Jersey SPI: bootstrap without HK2. Wraps Jersey's NonInjectionManager (see {@link
 * ManualInjectionManager}); application deps resolve via {@link AppContext} / {@link RequestContext}.
 */
@Priority(1)
public class NonInjectionManagerFactory implements InjectionManagerFactory {

    @Override
    public InjectionManager create(Object parent) {
        return new ManualInjectionManager();
    }

    @Override
    public InjectionManager create(Object parent, Configuration configuration) {
        return new ManualInjectionManager();
    }
}
