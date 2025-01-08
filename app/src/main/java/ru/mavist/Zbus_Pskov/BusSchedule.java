package ru.mavist.Zbus_Pskov;

public class BusSchedule {
    private String time;
    private String comment;

    public BusSchedule(String time, String comment) {
        this.time = time;
        this.comment = comment;
    }

    public String getTime() {
        return time;
    }

    public String getComment() {
        return comment;
    }
}
