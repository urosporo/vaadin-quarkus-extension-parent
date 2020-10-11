package com.urosporo.quarkus.vaadin.cdi;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class ProxyUtils {

    private ProxyUtils() {

        // prevent instantiation
    }

    /**
     * @param currentClass
     *            current class
     * @return class of the real implementation
     */
    public static Class getUnproxiedClass(final Class currentClass) {

        Class unproxiedClass = currentClass;

        while (isProxiedClass(unproxiedClass)) {
            unproxiedClass = unproxiedClass.getSuperclass();
        }

        return unproxiedClass;
    }

    /**
     * Analyses if the given class is a generated proxy class
     *
     * @param currentClass
     *            current class
     * @return true if the given class is a known proxy class, false otherwise
     */
    public static boolean isProxiedClass(final Class currentClass) {

        if (currentClass == null || currentClass.getSuperclass() == null) {
            return false;
        }

        return currentClass.getName().startsWith(currentClass.getSuperclass().getName()) && currentClass.getName().contains("$$");
    }

    public static List<Class<?>> getProxyAndBaseTypes(final Class<?> proxyClass) {

        final List<Class<?>> result = new ArrayList<>();
        result.add(proxyClass);

        if (isInterfaceProxy(proxyClass)) {
            for (final Class<?> currentInterface : proxyClass.getInterfaces()) {
                if (proxyClass.getName().startsWith(currentInterface.getName())) {
                    result.add(currentInterface);
                }
            }
        } else {
            Class unproxiedClass = proxyClass.getSuperclass();
            result.add(unproxiedClass);

            while (isProxiedClass(unproxiedClass)) {
                unproxiedClass = unproxiedClass.getSuperclass();
                result.add(unproxiedClass);
            }
        }
        return result;
    }

    public static boolean isInterfaceProxy(final Class<?> proxyClass) {

        final Class<?>[] interfaces = proxyClass.getInterfaces();
        if (Proxy.class.equals(proxyClass.getSuperclass()) && interfaces != null && interfaces.length > 0) {
            return true;
        }

        if (proxyClass.getSuperclass() != null && !proxyClass.getSuperclass().equals(Object.class)) {
            return false;
        }

        if (proxyClass.getName().contains("$$")) {
            for (final Class<?> currentInterface : interfaces) {
                if (proxyClass.getName().startsWith(currentInterface.getName())) {
                    return true;
                }
            }
        }

        return false;
    }
}
