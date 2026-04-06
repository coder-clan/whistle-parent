package org.coderclan.whistle;

import net.jqwik.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Property 32: Functional programming model for Spring Cloud Stream
 *
 * Validates: Requirements 21.1, 21.2, 21.4
 *
 * Verifies that Whistle source code does not reference {@code @EnableBinding},
 * {@code @StreamListener}, or {@code RabbitAutoConfiguration} classes, ensuring
 * compatibility with Spring Cloud Stream 4.x and 5.0.
 */
class FunctionalProgrammingModelProperties {

    private static final List<String> FORBIDDEN_REFERENCES = Arrays.asList(
            "EnableBinding",
            "StreamListener",
            "RabbitAutoConfiguration"
    );

    private static final Path SOURCE_ROOT = Paths.get("src/main/java");

    @Property
    @Tag("Feature: whistle-event-system, Property 32: Functional programming model for Spring Cloud Stream")
    void sourceFileDoesNotContainForbiddenReferences(@ForAll("javaSourceFiles") Path sourceFile) throws IOException {
        String content = new String(Files.readAllBytes(sourceFile));

        for (String forbidden : FORBIDDEN_REFERENCES) {
            assert !content.contains(forbidden) :
                    "Source file '" + sourceFile + "' contains forbidden reference '" + forbidden +
                    "'. Whistle must use the functional programming model and not reference " +
                    "@EnableBinding, @StreamListener, or RabbitAutoConfiguration.";
        }
    }

    @Provide
    Arbitrary<Path> javaSourceFiles() {
        List<Path> sourceFiles = collectJavaSourceFiles();
        assert !sourceFiles.isEmpty() :
                "No Java source files found under " + SOURCE_ROOT.toAbsolutePath();
        return Arbitraries.of(sourceFiles);
    }

    private static List<Path> collectJavaSourceFiles() {
        try (Stream<Path> walk = Files.walk(SOURCE_ROOT)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source directory: " + SOURCE_ROOT.toAbsolutePath(), e);
        }
    }
}
