package com.openclassrooms.tourguide;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;

public class TestPerformance {

	/*
	 * A note on performance improvements:
	 * 
	 * The number of users generated for the high volume tests can be easily
	 * adjusted via this method:
	 * 
	 * InternalTestHelper.setInternalUserNumber(100000);
	 * 
	 * 
	 * These tests can be modified to suit new solutions, just as long as the
	 * performance metrics at the end of the tests remains consistent.
	 * 
	 * These are performance metrics that we are trying to hit:
	 * 
	 * highVolumeTrackLocation: 100,000 users within 15 minutes:
	 * assertTrue(TimeUnit.MINUTES.toSeconds(15) >=
	 * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 *
	 * highVolumeGetRewards: 100,000 users within 20 minutes:
	 * assertTrue(TimeUnit.MINUTES.toSeconds(20) >=
	 * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 */

	@ParameterizedTest
	@ValueSource(ints = {100, 1000, 5000, 10000, 50000, 100000})
	public void highVolumeTrackLocation(int userNumber) {
	    GpsUtil gpsUtil = new GpsUtil();
	    RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

	    InternalTestHelper.setInternalUserNumber(userNumber);

	    // Create TourGuideService
	    TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

	    List<User> allUsers = tourGuideService.getAllUsers();

	    // Create an ExecutorService with a larger thread pool
	    int poolSize = Runtime.getRuntime().availableProcessors() * 4; // Twice the number of available processors
	    ExecutorService executorService = Executors.newFixedThreadPool(poolSize);

	    StopWatch stopWatch = new StopWatch();
	    stopWatch.start();

	    List<CompletableFuture<Void>> futures = allUsers.parallelStream()
	            .map(user -> CompletableFuture.runAsync(() -> tourGuideService.trackUserLocation(user), executorService)
	                    .thenAccept(result -> System.out.println("Processed location for user " + user.getUserId())))
	            .collect(Collectors.toList());

	    CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

	    try {
	        allOf.join(); // Use join instead of get to handle interruptions
	    } catch (CompletionException e) {
	        e.printStackTrace();
	    } finally {
	        // Graceful shutdown
	        executorService.shutdown();
	        try {
	            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
	                executorService.shutdownNow();
	            }
	        } catch (InterruptedException e) {
	            executorService.shutdownNow();
	            Thread.currentThread().interrupt();
	        }
	    }

	    stopWatch.stop();
	    tourGuideService.tracker.stopTracking();

	    System.out.println("highVolumeTrackLocation with " + userNumber + " users: Time Elapsed: "
	            + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");
	    assertTrue(TimeUnit.MINUTES.toSeconds(15) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}


	@ParameterizedTest
	@ValueSource(ints = {100, 1000, 10000, 100000})
	public void highVolumeGetRewards(int userNumber) {
	    GpsUtil gpsUtil = new GpsUtil();
	    RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

	    InternalTestHelper.setInternalUserNumber(userNumber);

	    // Create TourGuideService
	    TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

	    Attraction attraction = gpsUtil.getAttractions().get(0);

	    // Add visited locations for all users
	    List<User> allUsers = new ArrayList<>(tourGuideService.getAllUsers());
	    allUsers.forEach(u -> u.addToVisitedLocations(new VisitedLocation(u.getUserId(), attraction, new Date())));

	    List<CompletableFuture<Void>> futures = new ArrayList<>();

	    // Calculate rewards asynchronously
	    for (User user : allUsers) {
	        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> rewardsService.calculateRewards(user))
	                .thenAccept(ignoredResult -> {
	                    // Additional logic if needed
	                });
	        futures.add(future);
	    }

	    // Wait for all futures to complete
	    CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

	    try {
	        allOf.get(); // Block until all futures are completed
	    } catch (InterruptedException | ExecutionException e) {
	        e.printStackTrace();
	    }

	    // Validate results
	    allUsers.forEach(user -> assertTrue(user.getUserRewards().size() > 0));

	    tourGuideService.tracker.stopTracking();
	}

}
