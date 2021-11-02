package org.coderclan.whistle.example.producer.api;

import org.coderclan.whistle.api.EventContent;

import java.io.Serializable;

public class PredatorInformation extends EventContent implements Serializable {
    private PredatorType predatorType;
    private Integer number;
    private String location;

    public PredatorType getPredatorType() {
        return predatorType;
    }

    public void setPredatorType(PredatorType predatorType) {
        this.predatorType = predatorType;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @Override
    public String toString() {
        return "PredatorInformation{" +
                "predatorType=" + predatorType +
                ", number=" + number +
                ", location='" + location + '\'' +
                '}';
    }

    public static enum PredatorType {
        LION, TIGER, WOLF
    }
}
