package com.openclassrooms.tourguide.tracker;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;

public class Tracker extends Thread {
	private Logger logger = LoggerFactory.getLogger(Tracker.class);
	private static final long trackingPollingInterval = TimeUnit.MINUTES.toSeconds(5);
	private final ExecutorService executorService = Executors.newCachedThreadPool(); 
	private final TourGuideService tourGuideService;
	private boolean stop = false;
    private static final int BATCH_SIZE = 700; 


	public Tracker(TourGuideService tourGuideService) {
		this.tourGuideService = tourGuideService;

		executorService.submit(this);
	}

	/**
	 * Assures to shut down the Tracker thread
	 */
	public void stopTracking() {
		stop = true;
		executorService.shutdownNow();
	}

	 @Override
	    public void run() {
	        StopWatch stopWatch = new StopWatch();
	        while (true) {
	            if (Thread.currentThread().isInterrupted() || stop) {
	                logger.debug("Tracker stopping");
	                break;
	            }

	            List<User> users = tourGuideService.getAllUsers();
	            logger.debug("Begin Tracker. Tracking " + users.size() + " users.");
	            stopWatch.start();

	            for (int i = 0; i < users.size(); i += BATCH_SIZE) {
	                List<User> batchUsers = users.subList(i, Math.min(i + BATCH_SIZE, users.size()));
	                List<CompletableFuture<Void>> futures = batchUsers.stream()
	                        .map(u -> CompletableFuture.runAsync(() -> tourGuideService.trackUserLocation(u), executorService))
	                        .collect(Collectors.toList());

	                CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	                allOf.join();
	            }

	            stopWatch.stop();
	            logger.debug("Tracker Time Elapsed: " + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");
	            stopWatch.reset();
	            try {
	                logger.debug("Tracker sleeping");
	                TimeUnit.SECONDS.sleep(trackingPollingInterval);
	            } catch (InterruptedException e) {
	                break;
	            }
	        }
	    }
}