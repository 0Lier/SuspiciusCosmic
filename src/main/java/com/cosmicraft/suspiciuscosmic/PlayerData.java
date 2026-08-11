package com.cosmicraft.suspiciuscosmic;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PlayerData {
    private Location referenceLocation;
    private long referenceStartTime;
    private final List<TimelineEvent> timeline = new ArrayList<>();
    private final List<Long> alertedMinutes = new ArrayList<>();

    public PlayerData(Location referenceLocation, long referenceStartTime) {
        this.referenceLocation = referenceLocation;
        this.referenceStartTime = referenceStartTime;
    }

    public Location getReferenceLocation() {
        return referenceLocation;
    }

    public void setReferenceLocation(Location referenceLocation) {
        this.referenceLocation = referenceLocation;
    }

    public long getReferenceStartTime() {
        return referenceStartTime;
    }

    public void setReferenceStartTime(long referenceStartTime) {
        this.referenceStartTime = referenceStartTime;
    }

    public void addTimelineEvent(String description) {
        timeline.add(new TimelineEvent(description, System.currentTimeMillis()));
    }

    public List<TimelineEvent> getEventsSince(long timeMillis) {
        List<TimelineEvent> recent = new ArrayList<>();
        long threshold = System.currentTimeMillis() - timeMillis;
        for (TimelineEvent event : timeline) {
            if (event.timestamp >= threshold) {
                recent.add(event);
            }
        }
        return recent;
    }

    public void cleanOldEvents(long maxAgeMillis) {
        long threshold = System.currentTimeMillis() - maxAgeMillis;
        Iterator<TimelineEvent> it = timeline.iterator();
        while (it.hasNext()) {
            if (it.next().timestamp < threshold) {
                it.remove();
            }
        }
    }

    public List<Long> getAlertedMinutes() {
        return alertedMinutes;
    }

    public void clearAlertedMinutes() {
        alertedMinutes.clear();
    }

    public static class TimelineEvent {
        public String description;
        public long timestamp;

        public TimelineEvent(String description, long timestamp) {
            this.description = description;
            this.timestamp = timestamp;
        }
    }
}
