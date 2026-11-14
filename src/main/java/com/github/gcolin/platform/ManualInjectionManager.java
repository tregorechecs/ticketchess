package com.github.gcolin.platform;

import jakarta.ws.rs.RuntimeType;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.glassfish.jersey.client.innate.inject.NonInjectionManager;
import org.glassfish.jersey.internal.inject.Binder;
import org.glassfish.jersey.internal.inject.Binding;
import org.glassfish.jersey.internal.inject.ForeignDescriptor;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.internal.inject.ServiceHolder;

/**
 * {@link InjectionManager} without HK2: wraps Jersey's {@link NonInjectionManager} (which is final) and
 * additionally no-arg constructs provider/resource classes that were never bound, instead of failing with
 * "No binding found". No field injection is performed; request state comes from {@link RequestContext}.
 */
public final class ManualInjectionManager implements InjectionManager {

    private final NonInjectionManager delegate;
    private final Set<Binding> registered = Collections.newSetFromMap(new IdentityHashMap<>());

    public ManualInjectionManager() {
        this(new NonInjectionManager());
    }

    ManualInjectionManager(NonInjectionManager delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T> T create(Class<T> createMe) {
        try {
            return delegate.create(createMe);
        } catch (IllegalStateException e) {
            // Unregistered class (e.g. a @Provider registered by class): plain no-arg construction.
            return createUnbound(createMe, e);
        }
    }

    private <T> T createUnbound(Class<T> type, IllegalStateException noBinding) {
        try {
            return delegate.createAndInitialize(type);
        } catch (RuntimeException e) {
            e.addSuppressed(noBinding);
            throw e;
        }
    }

    @Override
    public <T> T createAndInitialize(Class<T> createMe) {
        return delegate.createAndInitialize(createMe);
    }

    @Override
    public void completeRegistration() {
        delegate.completeRegistration();
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public void register(Binding binding) {
        // Jersey's servlet container hands the same binder to both ResourceConfig and ApplicationHandler, so
        // its bindings arrive twice. HK2 tolerates that; NonInjectionManager fails with "Ambiguous providing services".
        if (registered.add(binding)) {
            delegate.register(binding);
        }
    }

    @Override
    public void register(Iterable<Binding> descriptors) {
        for (Binding binding : descriptors) {
            register(binding);
        }
    }

    @Override
    public void register(Binder binder) {
        for (Binding binding : binder.getBindings()) {
            register(binding);
        }
    }

    @Override
    public void register(Object provider) throws IllegalArgumentException {
        delegate.register(provider);
    }

    @Override
    public boolean isRegistrable(Class<?> clazz) {
        return delegate.isRegistrable(clazz);
    }

    @Override
    public <T> List<ServiceHolder<T>> getAllServiceHolders(Class<T> contract, Annotation... qualifiers) {
        return delegate.getAllServiceHolders(contract, qualifiers);
    }

    @Override
    public <T> T getInstance(Class<T> contractOrImpl, Annotation... qualifiers) {
        return delegate.getInstance(contractOrImpl, qualifiers);
    }

    @Override
    public <T> T getInstance(Class<T> contractOrImpl, String classAnalyzer) {
        return delegate.getInstance(contractOrImpl, classAnalyzer);
    }

    @Override
    public <T> T getInstance(Class<T> contractOrImpl) {
        try {
            return delegate.getInstance(contractOrImpl);
        } catch (IllegalStateException e) {
            if (isInstantiable(contractOrImpl)) {
                return createUnbound(contractOrImpl, e);
            }
            // Unbound contract (e.g. optional bean validation): like HK2, report "absent".
            return null;
        }
    }

    private static boolean isInstantiable(Class<?> type) {
        return !type.isInterface() && !Modifier.isAbstract(type.getModifiers());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getInstance(Type contractOrImpl) {
        if (contractOrImpl instanceof Class<?> clazz) {
            return (T) getInstance(clazz);
        }
        return delegate.getInstance(contractOrImpl);
    }

    @Override
    public Object getInstance(ForeignDescriptor foreignDescriptor) {
        return delegate.getInstance(foreignDescriptor);
    }

    @Override
    public ForeignDescriptor createForeignDescriptor(Binding binding) {
        return delegate.createForeignDescriptor(binding);
    }

    @Override
    public <T> List<T> getAllInstances(Type contractOrImpl) {
        return delegate.getAllInstances(contractOrImpl);
    }

    @Override
    public void inject(Object injectMe) {
        delegate.inject(injectMe);
    }

    @Override
    public void inject(Object injectMe, String classAnalyzer) {
        delegate.inject(injectMe, classAnalyzer);
    }

    @Override
    public void preDestroy(Object preDestroyMe) {
        delegate.preDestroy(preDestroyMe);
    }

    @Override
    public RuntimeType getRuntimeType() {
        return delegate.getRuntimeType();
    }
}
