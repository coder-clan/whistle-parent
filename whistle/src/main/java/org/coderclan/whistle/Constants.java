package org.coderclan.whistle;

import java.util.concurrent.LinkedBlockingQueue;

public class Constants {
    public static final String CONTENT_JAVA_TYPE_HEADER = "org-coderclan-whistle-java-type";
    public static final String EVENT_PERSISTENT_ID_HEADER = "org-coderclan-whistle-persistent-id";
    public static final LinkedBlockingQueue<Event> queue = new LinkedBlockingQueue<>(100);
    public static final int MAX_QUEUE_COUNT = 32;

    private Constants() {
    }
}
