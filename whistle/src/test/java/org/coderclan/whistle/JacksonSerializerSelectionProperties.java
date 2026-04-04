package org.coderclan.whistle;

import net.jqwik.api.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Property 29: Jackson version-adaptive serializer selection
 *
 * Validates: Requirements 20.1, 20.2, 20.5
 *
 * Verifies that WhistleJacksonConfiguration contains exactly two static inner
 * @Configuration classes — one for Jackson 2 and one for Jackson 3 — each guarded
 * by @ConditionalOnClass for the appropriate ObjectMapper and providing a bean
 * method guarded by @ConditionalOnMissingBean(EventContentSerializer.class).
 */
class JacksonSerializerSelectionProperties {

    @Property
    @Tag("Feature: whistle-event-system, Property 29: Jackson version-adaptive serializer selection")
    void whistleJacksonConfigurationHasExactlyTwoInnerConfigurationClasses() {
        Class<?>[] declaredClasses = WhistleJacksonConfiguration.class.getDeclaredClasses();

        List<Class<?>> configClasses = Arrays.stream(declaredClasses)
                .filter(c -> c.isAnnotationPresent(Configuration.class))
                .collect(Collectors.toList());

        assert configClasses.size() == 2 :
                "WhistleJacksonConfiguration must have exactly 2 static inner @Configuration classes, but found " +
                configClasses.size() + ": " + configClasses;
    }

    @Property
    @Tag("Feature: whistle-event-system, Property 29: Jackson version-adaptive serializer selection")
    void jackson2ConfigurationHasConditionalOnClassForJackson2ObjectMapper() {
        Class<?> jackson2Config = findInnerClass("Jackson2Configuration");

        ConditionalOnClass annotation = jackson2Config.getAnnotation(ConditionalOnClass.class);
        assert annotation != null :
                "Jackson2Configuration must have @ConditionalOnClass annotation";

        List<String> names = Arrays.asList(annotation.name());
        assert names.contains("com.fasterxml.jackson.databind.ObjectMapper") :
                "Jackson2Configuration @ConditionalOnClass must specify 'com.fasterxml.jackson.databind.ObjectMapper', " +
                "but found: " + names;
    }

    @Property
    @Tag("Feature: whistle-event-system, Property 29: Jackson version-adaptive serializer selection")
    void jackson3ConfigurationHasConditionalOnClassForJackson3ObjectMapper() {
        Class<?> jackson3Config = findInnerClass("Jackson3Configuration");

        ConditionalOnClass annotation = jackson3Config.getAnnotation(ConditionalOnClass.class);
        assert annotation != null :
                "Jackson3Configuration must have @ConditionalOnClass annotation";

        List<String> names = Arrays.asList(annotation.name());
        assert names.contains("tools.jackson.databind.ObjectMapper") :
                "Jackson3Configuration @ConditionalOnClass must specify 'tools.jackson.databind.ObjectMapper', " +
                "but found: " + names;
    }

    @Property
    @Tag("Feature: whistle-event-system, Property 29: Jackson version-adaptive serializer selection")
    void bothInnerClassesHaveBeanMethodWithConditionalOnMissingBean(@ForAll("innerConfigClasses") Class<?> innerClass) {
        // Find a @Bean method
        Method beanMethod = Arrays.stream(innerClass.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Bean.class))
                .findFirst()
                .orElse(null);

        assert beanMethod != null :
                innerClass.getSimpleName() + " must have a @Bean method";

        ConditionalOnMissingBean conditionalAnnotation = beanMethod.getAnnotation(ConditionalOnMissingBean.class);
        assert conditionalAnnotation != null :
                innerClass.getSimpleName() + " @Bean method must have @ConditionalOnMissingBean annotation";

        List<Class<?>> missingBeanTypes = Arrays.asList(conditionalAnnotation.value());
        assert missingBeanTypes.contains(EventContentSerializer.class) :
                innerClass.getSimpleName() + " @Bean method @ConditionalOnMissingBean must specify EventContentSerializer.class, " +
                "but found: " + missingBeanTypes;
    }

    @Provide
    Arbitrary<Class<?>> innerConfigClasses() {
        Class<?> jackson2Config = findInnerClass("Jackson2Configuration");
        Class<?> jackson3Config = findInnerClass("Jackson3Configuration");
        return Arbitraries.of(jackson2Config, jackson3Config);
    }

    private static Class<?> findInnerClass(String simpleName) {
        return Arrays.stream(WhistleJacksonConfiguration.class.getDeclaredClasses())
                .filter(c -> c.getSimpleName().equals(simpleName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "WhistleJacksonConfiguration must contain inner class " + simpleName));
    }
}
