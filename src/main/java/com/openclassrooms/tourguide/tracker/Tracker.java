package com.openclassrooms.tourguide.tracker;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;

public class Tracker extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(Tracker.class);
    private static final long trackingPollingInterval = TimeUnit.MINUTES.toSeconds(5);
    private final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final TourGuideService tourGuideService;
    private volatile boolean stop = false;

    public Tracker(TourGuideService tourGuideService) {
        this.tourGuideService = tourGuideService;
    }

    /**
     * Assures to shut down the Tracker thread
     */
    public void stopTracking() {
        stop = true;
        executorService.shutdown();
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted() && !stop) {
                List<User> users = tourGuideService.getAllUsers();
                logger.debug("Begin Tracker. Tracking " + users.size() + " users.");

                users.forEach(u -> executorService.submit(() -> tourGuideService.trackUserLocation(u)));

                logger.debug("Tracker sleeping");
                TimeUnit.SECONDS.sleep(trackingPollingInterval);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("Tracker interrupted");
        } finally {
            executorService.shutdown();
            logger.debug("Tracker stopped");
        }
    }
}