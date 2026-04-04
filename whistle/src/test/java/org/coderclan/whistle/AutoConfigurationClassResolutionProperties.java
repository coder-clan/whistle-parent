package org.coderclan.whistle;

import net.jqwik.api.*;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;

import java.util.Arrays;
import java.util.List;

/**
 * Property 28: Version-adaptive auto-configuration class resolution
 *
 * Validates: Requirements 15.6, 19.1, 19.2, 19.4
 *
 * Verifies that @AutoConfigureAfter annotations on both WhistleConfiguration and
 * WhistleMongodbConfiguration specify both old (Spring Boot 2.x/3.x) and new
 * (Spring Boot 4.x) package paths using string-based name attributes.
 */
class AutoConfigurationClassResolutionProperties {

    @Property
    @Tag("Feature: whistle-event-system, Property 28: Version-adaptive auto-configuration class resolution")
    void whistleConfigurationAutoConfigureAfterContainsBothDataSourcePaths(@ForAll("configClasses") Class<?> configClass) {
        // Only test WhistleConfiguration for DataSource paths
        Assume.that(configClass == WhistleConfiguration.class);

        AutoConfigureAfter annotation = configClass.getAnnotation(AutoConfigureAfter.class);

        // Annotation must be present
        assert annotation != null :
                "WhistleConfiguration must have @AutoConfigureAfter annotation";

        List<String> names = Arrays.asList(annotation.name());

        // Must contain the old Spring Boot 2.x/3.x DataSourceAutoConfiguration path
        assert names.contains("org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration") :
                "WhistleConfiguration @AutoConfigureAfter must include old DataSourceAutoConfiguration path " +
                "(org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration), but found: " + names;

        // Must contain the new Spring Boot 4.x DataSourceAutoConfiguration path
        assert names.contains("org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration") :
                "WhistleConfiguration @AutoConfigureAfter must include new DataSourceAutoConfiguration path " +
                "(org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration), but found: " + names;

        // Must use string-based name attribute (no class references) — verified by the fact
        // that annotation.value() should NOT contain DataSourceAutoConfiguration.class
        Class<?>[] classRefs = annotation.value();
        for (Class<?> ref : classRefs) {
            assert !ref.getName().contains("DataSourceAutoConfiguration") :
                    "WhistleConfiguration should use string-based name attribute for DataSourceAutoConfiguration, " +
                    "not class reference: " + ref.getName();
        }
    }

    @Property
    @Tag("Feature: whistle-event-system, Property 28: Version-adaptive auto-configuration class resolution")
    void whistleMongodbConfigurationAutoConfigureAfterContainsBothMongoPaths(@ForAll("configClasses") Class<?> configClass) {
        // Only test WhistleMongodbConfiguration for Mongo paths
        Assume.that(configClass == WhistleMongodbConfiguration.class);

        AutoConfigureAfter annotation = configClass.getAnnotation(AutoConfigureAfter.class);

        // Annotation must be present
        assert annotation != null :
                "WhistleMongodbConfiguration must have @AutoConfigureAfter annotation";

        List<String> names = Arrays.asList(annotation.name());

        // Must contain the old Spring Boot 2.x/3.x MongoAutoConfiguration path
        assert names.contains("org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration") :
                "WhistleMongodbConfiguration @AutoConfigureAfter must include old MongoAutoConfiguration path " +
                "(org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration), but found: " + names;

        // Must contain the new Spring Boot 4.x MongoAutoConfiguration path
        assert names.contains("org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration") :
                "WhistleMongodbConfiguration @AutoConfigureAfter must include new MongoAutoConfiguration path " +
                "(org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration), but found: " + names;

        // Must use string-based name attribute (no class references) — verified by the fact
        // that annotation.value() should NOT contain MongoAutoConfiguration.class
        Class<?>[] classRefs = annotation.value();
        for (Class<?> ref : classRefs) {
            assert !ref.getName().contains("MongoAutoConfiguration") :
                    "WhistleMongodbConfiguration should use string-based name attribute for MongoAutoConfiguration, " +
                    "not class reference: " + ref.getName();
        }
    }

    @Provide
    Arbitrary<Class<?>> configClasses() {
        return Arbitraries.of(WhistleConfiguration.class, WhistleMongodbConfiguration.class);
    }
}
