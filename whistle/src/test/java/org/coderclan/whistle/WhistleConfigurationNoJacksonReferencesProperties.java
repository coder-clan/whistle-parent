package org.coderclan.whistle;

import net.jqwik.api.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * Property 31: WhistleConfiguration has no Jackson class references in method signatures
 *
 * Validates: Requirements 20.3
 *
 * Uses reflection to verify that no method in WhistleConfiguration has parameter types
 * or return types referencing com.fasterxml.jackson.* or tools.jackson.* packages.
 * This prevents NoClassDefFoundError during reflection-based bean introspection on
 * Spring Boot 4.x where Jackson 2 is not on the classpath.
 */
class WhistleConfigurationNoJacksonReferencesProperties {

    private static final List<String> JACKSON_PACKAGE_PREFIXES = Arrays.asList(
            "com.fasterxml.jackson.",
            "tools.jackson."
    );

    @Property
    @Tag("Feature: whistle-event-system, Property 31: WhistleConfiguration has no Jackson class references in method signatures")
    void noMethodParameterReferencesJacksonClasses(@ForAll("declaredMethods") Method method) {
        for (Class<?> paramType : method.getParameterTypes()) {
            String paramTypeName = paramType.getName();
            for (String prefix : JACKSON_PACKAGE_PREFIXES) {
                assert !paramTypeName.startsWith(prefix) :
                        "Method '" + method.getName() + "' has parameter type referencing Jackson: " +
                        paramTypeName + " (prefix: " + prefix + ")";
            }
        }
    }

    @Property
    @Tag("Feature: whistle-event-system, Property 31: WhistleConfiguration has no Jackson class references in method signatures")
    void noMethodReturnTypeReferencesJacksonClasses(@ForAll("declaredMethods") Method method) {
        String returnTypeName = method.getReturnType().getName();
        for (String prefix : JACKSON_PACKAGE_PREFIXES) {
            assert !returnTypeName.startsWith(prefix) :
                    "Method '" + method.getName() + "' has return type referencing Jackson: " +
                    returnTypeName + " (prefix: " + prefix + ")";
        }
    }

    @Provide
    Arbitrary<Method> declaredMethods() {
        Method[] methods = WhistleConfiguration.class.getDeclaredMethods();
        assert methods.length > 0 : "WhistleConfiguration must have at least one declared method";
        return Arbitraries.of(methods);
    }
}
