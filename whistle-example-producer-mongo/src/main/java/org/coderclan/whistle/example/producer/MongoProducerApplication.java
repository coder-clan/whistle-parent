package org.coderclan.whistle.example.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MongoProducerApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext c = SpringApplication.run(MongoProducerApplication.class, args);

        Notification n = c.getBean(Notification.class);
        while (true) {
            try {
                n.sendNotification();
                Thread.sleep(10 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
