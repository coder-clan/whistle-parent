package org.coderclan.whistle;

import net.jqwik.api.*;
import org.coderclan.whistle.api.AbstractEventContent;
import org.coderclan.whistle.api.EventContent;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Property 30: Jackson 3 serialization round trip preserves EventContent
 *
 * Validates: Requirements 20.4
 *
 * Verifies that for any valid EventContent object, serializing to JSON then
 * deserializing back using Jackson 3 produces an object equal to the original.
 */
class Jackson3SerializationRoundTripProperties {

    /**
     * Concrete test subclass of AbstractEventContent with an additional data field.
     * Requires a no-arg constructor for Jackson deserialization.
     */
    static class TestEventContent extends AbstractEventContent {
        private String data;

        /** No-arg constructor required for Jackson deserialization. */
        public TestEventContent() {
        }

        public TestEventContent(String idempotentId, Instant time, String data) {
            try {
                java.lang.reflect.Field idField = AbstractEventContent.class.getDeclaredField("idempotentId");
                idField.setAccessible(true);
                idField.set(this, idempotentId);

                java.lang.reflect.Field timeField = AbstractEventContent.class.getDeclaredField("time");
                timeField.setAccessible(true);
                timeField.set(this, time);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set AbstractEventContent fields", e);
            }
            this.data = data;
        }

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }
    }

    private final Jackson3EventContentSerializer serializer;

    Jackson3SerializationRoundTripProperties() {
        // In Jackson 3, JavaTimeModule is built into jackson-databind; no manual registration needed.
        tools.jackson.databind.ObjectMapper objectMapper = JsonMapper.builder().build();
        this.serializer = new Jackson3EventContentSerializer(objectMapper);
    }

    @Property
    @Tag("Feature: whistle-event-system, Property 30: Jackson 3 serialization round trip preserves EventContent")
    void serializationRoundTripPreservesEventContent(@ForAll("testEventContents") TestEventContent original) {
        String json = serializer.toJson(original);
        EventContent deserialized = serializer.toEventContent(json, TestEventContent.class);

        // equals/hashCode is based on idempotentId
        assert original.equals(deserialized) :
                "Round-trip serialization must preserve equality. Original idempotentId: " +
                original.getIdempotentId() + ", deserialized idempotentId: " +
                ((TestEventContent) deserialized).getIdempotentId();

        // Also verify the data field is preserved
        TestEventContent deserializedContent = (TestEventContent) deserialized;
        assert Objects.equals(original.getData(), deserializedContent.getData()) :
                "Round-trip serialization must preserve data field. Original: " +
                original.getData() + ", deserialized: " + deserializedContent.getData();

        // Verify time is preserved
        assert Objects.equals(original.getTime(), deserializedContent.getTime()) :
                "Round-trip serialization must preserve time field. Original: " +
                original.getTime() + ", deserialized: " + deserializedContent.getTime();
    }

    @Provide
    Arbitrary<TestEventContent> testEventContents() {
        Arbitrary<String> ids = Arbitraries.create(() -> UUID.randomUUID().toString());
        Arbitrary<Instant> times = Arbitraries.longs()
                .between(0L, 4102444800L) // epoch seconds from 0 to ~2100
                .map(Instant::ofEpochSecond);
        Arbitrary<String> data = Arbitraries.strings()
                .alpha()
                .ofMinLength(0)
                .ofMaxLength(100)
                .injectNull(0.1);

        return Combinators.combine(ids, times, data)
                .as(TestEventContent::new);
    }
}
