package org.coderclan.whistle.example.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "org.coderclan")
@EnableScheduling
public class ProducerApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext c = SpringApplication.run(ProducerApplication.class, args);
        Notification n = c.getBean(Notification.class);
        while (true) {
            n.sendNotification();
            try {
                Thread.sleep(10 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
