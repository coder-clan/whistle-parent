package org.coderclan.whistle;

public class Constants {
    public static final String CONTENT_JAVA_TYPE_HEADER = "org-coderclan-whistle-java-type";
    public static final String RABBITMQ_HEADER_ORIGINAL_EXCHANGE = "x-original-exchange";
    public static final String RABBITMQ_HEADER_RECEIVED_EXCHANGE = "amqp_receivedExchange";

    public static final String EVENT_PERSISTENT_ID_HEADER = "org-coderclan-whistle-persistent-id";
    public static final int MAX_QUEUE_COUNT = 32;

    private Constants() {
    }
}
