package com.openclassrooms.tourguide.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;

@Service
public class RewardsService {
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;
	private Logger logger = LoggerFactory.getLogger(RewardsService.class);

	// proximity in miles
    private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;

	
	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}
	
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}
	
	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}
	
	/**
	 * Asynchronously calculates rewards for a user based on their visited locations and attractions.
	 *
	 * @param user The user for whom to calculate rewards.
	 */
	public void calculateRewards(User user) {
	    List<VisitedLocation> userLocations = user.getVisitedLocations();
	    
	    List<Attraction> attractions = gpsUtil.getAttractions();

	    ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

	    try {
	        // Create a list of CompletableFuture to represent asynchronous reward calculation tasks
	        List<CompletableFuture<Void>> futures = userLocations.parallelStream()
	                .flatMap(visitedLocation ->
	                        attractions.stream()
	                                .filter(attraction -> user.getUserRewards().stream()
	                                        .noneMatch(r -> r.attraction.attractionName.equals(attraction.attractionName)))
	                                .filter(attraction -> nearAttraction(visitedLocation, attraction))
	                                .map(attraction -> CompletableFuture.runAsync(() -> {
	                                    // Asynchronously calculate and add the reward for the user
	                                    user.addUserReward(new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)));
	                                    // Log the reward calculation for the user and attraction
	                                    logger.debug("Calculated reward for user {} at attraction {}: {} points",
	                                            user.getUserName(), attraction.attractionName, getRewardPoints(attraction, user));
	                                }, executorService)))
	                .collect(Collectors.toList());

	        // Wait for all CompletableFuture tasks to complete
	        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	        allOf.join();

	    } finally {
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
	}

	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) > attractionProximityRange ? false : true;
	}
	
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
	}
	
	protected int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}
	
	public double getDistance(Location loc1, Location loc2) {
        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);

        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                               + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

        double nauticalMiles = 60 * Math.toDegrees(angle);
        double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
        return statuteMiles;
	}

	
	
}
