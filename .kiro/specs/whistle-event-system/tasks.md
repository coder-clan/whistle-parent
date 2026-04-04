# Implementation Plan: Spring Boot 4 / Java 25 Compatibility

## Overview

Implement the 4 design areas from the "Addendum: Spring Boot 4 / Java 25 Compatibility Design" to make Whistle compatible with Spring Boot 4.x (Spring Framework 7), Jackson 3, and Java 25. This covers auto-configuration class path migration, Jackson 2/3 dual-support, javax/jakarta annotation handling, and Spring Cloud Stream 5.0 verification.

## Tasks

- [x] 1. Auto-Configuration Class Path Migration (Design 1: B1, B2, M2)
  - [x] 1.1 Update WhistleConfiguration @AutoConfigureAfter to use string-based name attribute
    - Replace `@AutoConfigureAfter({DataSourceAutoConfiguration.class, WhistleMongodbConfiguration.class})` with `@AutoConfigureAfter(name = {"org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration", "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration", "org.coderclan.whistle.WhistleMongodbConfiguration"})`
    - Remove the `import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration` import statement
    - _Requirements: 15.6, 19.1, 19.4_

  - [x] 1.2 Update WhistleMongodbConfiguration @AutoConfigureAfter to use string-based name attribute
    - Replace `@AutoConfigureAfter(MongoAutoConfiguration.class)` with `@AutoConfigureAfter(name = {"org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration", "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration"})`
    - Remove the `import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration` import statement
    - Keep `@ConditionalOnClass(MongoCustomConversions.class)` unchanged (stable across versions)
    - _Requirements: 15.6, 19.2, 19.4_

  - [x] 1.3 Write property test for version-adaptive auto-configuration class resolution
    - **Property 28: Version-adaptive auto-configuration class resolution**
    - Verify that `@AutoConfigureAfter` annotations on both WhistleConfiguration and WhistleMongodbConfiguration specify both old (Spring Boot 2.x/3.x) and new (Spring Boot 4.x) package paths using string-based `name` attributes
    - **Validates: Requirements 15.6, 19.1, 19.2, 19.4**

- [x] 2. Checkpoint - Verify auto-configuration migration compiles
  - Ensure all tests pass, ask the user if questions arise.

- [x] 3. Jackson 2/3 Dual-Support (Design 2: B3, B4)
  - [x] 3.1 Create Jackson3EventContentSerializer class
    - Create `whistle/src/main/java/org/coderclan/whistle/Jackson3EventContentSerializer.java`
    - Implement `EventContentSerializer` interface using `tools.jackson.databind.ObjectMapper`
    - Use `tools.jackson.core.JacksonException` for exception handling
    - Mirror the structure of existing `JacksonEventContentSerializer` but with Jackson 3 APIs
    - _Requirements: 20.1, 20.4_

  - [x] 3.2 Create WhistleJacksonConfiguration with dual Jackson inner classes
    - Create `whistle/src/main/java/org/coderclan/whistle/WhistleJacksonConfiguration.java`
    - Outer class: `@Configuration` with NO Jackson imports, annotated with `@AutoConfigureBefore(name = "org.coderclan.whistle.WhistleConfiguration")` to ensure the serializer bean is available before persistenter beans that depend on it
    - Static inner class `Jackson2Configuration`: `@Configuration` + `@ConditionalOnClass(name = "com.fasterxml.jackson.databind.ObjectMapper")` + `@Bean @ConditionalOnMissingBean(EventContentSerializer.class)` creating `JacksonEventContentSerializer`
    - Static inner class `Jackson3Configuration`: `@Configuration` + `@ConditionalOnClass(name = "tools.jackson.databind.ObjectMapper")` + `@Bean @ConditionalOnMissingBean(EventContentSerializer.class)` creating `Jackson3EventContentSerializer`
    - _Requirements: 20.1, 20.2, 20.5_

  - [x] 3.3 Add Jackson 2 and Jackson 3 as optional Maven dependencies
    - Add `com.fasterxml.jackson.core:jackson-databind` as `<optional>true</optional>` dependency in `whistle/pom.xml` (currently transitive via Spring Boot; making it explicit and optional ensures `JacksonEventContentSerializer` compiles)
    - Add `tools.jackson.databind:jackson-databind` as `<optional>true</optional>` dependency in `whistle/pom.xml` (required for `Jackson3EventContentSerializer` to compile; optional so it does not propagate to consumers)
    - _Requirements: 20.1, 20.2_

  - [x] 3.4 Remove eventContentSerializer() bean method from WhistleConfiguration
    - Remove the `eventContentSerializer(@Autowired ObjectMapper objectMapper)` method from `WhistleConfiguration`
    - Remove the `import com.fasterxml.jackson.databind.ObjectMapper` import statement
    - This eliminates Jackson 2 class references from WhistleConfiguration method signatures, preventing `NoClassDefFoundError` during reflection on Spring Boot 4
    - _Requirements: 20.3_

  - [x] 3.5 Register WhistleJacksonConfiguration in AutoConfiguration.imports
    - Add `org.coderclan.whistle.WhistleJacksonConfiguration` to `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
    - Add `org.coderclan.whistle.WhistleJacksonConfiguration` to `META-INF/spring.factories` for Spring Boot 2.x compatibility
    - _Requirements: 18.3, 20.5_

  - [x] 3.6 Write property test for Jackson version-adaptive serializer selection
    - **Property 29: Jackson version-adaptive serializer selection**
    - Verify that exactly one `EventContentSerializer` bean is activated based on classpath: Jackson 2 on Spring Boot 2.x/3.x, Jackson 3 on Spring Boot 4.x, and neither when a custom serializer is provided
    - **Validates: Requirements 20.1, 20.2, 20.5**

  - [x] 3.7 Write property test for Jackson 3 serialization round trip
    - **Property 30: Jackson 3 serialization round trip preserves EventContent**
    - Verify that for any valid EventContent object, serializing to JSON then deserializing back using Jackson 3 produces an object equal to the original
    - **Validates: Requirements 20.4**

  - [x] 3.8 Write property test for no Jackson class references in WhistleConfiguration
    - **Property 31: WhistleConfiguration has no Jackson class references in method signatures**
    - Use reflection to verify that no method in `WhistleConfiguration` has parameter types or return types referencing `com.fasterxml.jackson.*` or `tools.jackson.*`
    - **Validates: Requirements 20.3**

- [x] 4. Checkpoint - Verify Jackson dual-support compiles
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. javax/jakarta Annotation Handling (Design 3: M1)
  - [x] 5.1 Replace @PostConstruct with InitializingBean in WhistleConfiguration
    - Make `WhistleConfiguration` implement `InitializingBean` (in addition to existing `ApplicationContextAware`)
    - Rename `init()` method to `afterPropertiesSet()` (or have `afterPropertiesSet()` call the existing init logic)
    - Remove both `@PostConstruct` and `@jakarta.annotation.PostConstruct` annotations
    - Remove `import javax.annotation.PostConstruct` import statement
    - This ensures cross-version compatibility with Spring Boot 2.x, 3.x, and 4.x without any javax/jakarta annotation dependency
    - _Requirements: 16.1, 16.2, 16.6_

  - [x] 5.2 Write property test for functional programming model verification
    - **Property 32: Functional programming model for Spring Cloud Stream**
    - Verify that Whistle source code does not reference `@EnableBinding`, `@StreamListener`, or `RabbitAutoConfiguration` classes
    - **Validates: Requirements 21.1, 21.2, 21.4**

- [x] 6. Final checkpoint - Ensure all changes compile and tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 7. Maven Profile-Based Multi-Version Verification
  - [ ] 7.1 Add Spring Boot 2/3/4 Maven profiles to root pom.xml
    - Add three profiles: `spring-boot-2` (default), `spring-boot-3`, `spring-boot-4`
    - Each profile sets `spring-boot.version`, `spring-cloud.version`, `maven.compiler.source`, `maven.compiler.target`, and `spring-boot-maven-plugin.version`
    - Add `spring-boot-dependencies` BOM import in dependencyManagement to override parent-managed versions
  - [ ] 7.2 Update example project POMs to use property-based spring-boot-maven-plugin version
    - Replace hardcoded `<version>2.6.4</version>` in spring-boot-maven-plugin with `${spring-boot-maven-plugin.version}`
    - Applies to: whistle-example-producer, whistle-example-consumer
  - [ ] 7.3 Verify compilation under each profile
    - Run `mvn compile -Pspring-boot-2`, `mvn compile -Pspring-boot-3`, `mvn compile -Pspring-boot-4`

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Design 4 (Spring Cloud Stream 5.0 Compatibility) requires no code changes; verification is covered by Property 32 in task 5.2
- Each task references specific requirements for traceability
- Property tests validate the 5 new correctness properties (28-32) from the design addendum
- The implementation language is Java, matching the existing codebase
