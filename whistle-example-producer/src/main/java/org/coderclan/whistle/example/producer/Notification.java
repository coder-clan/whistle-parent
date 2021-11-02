package org.coderclan.whistle.example.producer;

import org.coderclan.whistle.api.EventService;
import org.coderclan.whistle.example.producer.api.ExampleEventType;
import org.coderclan.whistle.example.producer.api.PredatorInformation;
import org.coderclan.whistle.example.producer.api.PreyInformation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.Random;

@Component
public class Notification {
    @Autowired
    private EventService eventService;


//    @PostConstruct
//    public void init() {
//        Notification n = this;
//        Thread t = new Thread(new Runnable() {
//
//            @Override
//            public void run() {
//                while (true) {
//                    n.sendNotification();
//                    try {
//                        Thread.sleep(10 * 1000);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//        });
//        t.setDaemon(true);
//        t.start();
//    }

    // @Scheduled(cron = "*/10 * * * * *")
    @Transactional(rollbackFor = Exception.class)
    public void sendNotification() {
        Random rand = new Random();
        if (rand.nextBoolean()) {
            sendPredatorInSight();
        } else {
            sendPreyCaught();
        }
    }

    private void sendPreyCaught() {
        Random random = new Random();

        PreyInformation content = new PreyInformation();
        content.setPreyType(random.nextBoolean() ? PreyInformation.PreyType.GOAT : PreyInformation.PreyType.RABBIT);
        content.setNumber(random.nextInt(3) + 1);

        this.eventService.publishEvent(ExampleEventType.PREY_CAUGHT, content);
    }

    private void sendPredatorInSight() {
        Random random = new Random();

        PredatorInformation content = new PredatorInformation();
        content.setPredatorType(random.nextBoolean() ? PredatorInformation.PredatorType.LION : PredatorInformation.PredatorType.WOLF);
        content.setNumber(random.nextInt(10) + 1);

        this.eventService.publishEvent(ExampleEventType.PREDATOR_IN_SIGHT, content);
    }
}
