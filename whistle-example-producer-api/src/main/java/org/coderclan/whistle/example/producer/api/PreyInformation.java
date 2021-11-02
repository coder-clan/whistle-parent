package org.coderclan.whistle.example.producer.api;

import org.coderclan.whistle.api.EventContent;

import java.io.Serializable;

public class PreyInformation extends EventContent implements Serializable {
    private PreyType preyType;
    private Integer number;

    public PreyType getPreyType() {
        return preyType;
    }

    public void setPreyType(PreyType preyType) {
        this.preyType = preyType;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    @Override
    public String toString() {
        return "PreyInformation{" +
                "preyType=" + preyType +
                ", number=" + number +
                '}';
    }

    public static enum PreyType {
        GOAT, RABBIT
    }
}
