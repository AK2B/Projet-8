package com.openclassrooms.tourguide.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import gpsUtil.GpsUtil;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;
		
		Locale.setDefault(Locale.US);

		if (testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		tracker = new Tracker(this);
		addShutDownHook();
	}

	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	public VisitedLocation getUserLocation(User user) {
		VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation()
				: trackUserLocation(user);
		return visitedLocation;
	}

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return internalUserMap.values().stream().collect(Collectors.toList());
	}

	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}

	public VisitedLocation trackUserLocation(User user) {
	    ExecutorService executorService = Executors.newFixedThreadPool(100);

	    CompletableFuture<VisitedLocation> future = CompletableFuture.supplyAsync(() -> {
	        VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
	        user.addToVisitedLocations(visitedLocation);
	        if (visitedLocation != null) {
	            rewardsService.calculateRewards(user);
	        }
	        return visitedLocation;
	    }, executorService);

	    try {
	        // Attendez la fin de la tâche asynchrone
	        VisitedLocation visitedLocation = future.get();
	        return visitedLocation;
	    } catch (InterruptedException | ExecutionException e) {
	        throw new RuntimeException(e);
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
	
	/**
     * Gets the closest five tourist attractions to the user - no matter how far away they are.
     *
     * @param user The user for whom to find nearby attractions.
     * @return A list of maps representing nearby attractions with information such as name,
     *         latitude, longitude, distance from the user, and reward points.
     */
    public List<Map<String, Object>> getNearByAttractions(User user) {
        logger.debug("Getting nearby attractions for user: {}", user.getUserName());

        // Get the current location of the user
        VisitedLocation visitedLocation = getUserLocation(user);

        // Use parallelStream to parallelize attraction distance calculations
        List<Map<String, Object>> closestAttractions = gpsUtil.getAttractions().parallelStream()
                .sorted(Comparator.comparingDouble(attraction -> rewardsService.getDistance(attraction, visitedLocation.location)))
                .limit(5)
                .map(attraction -> {
                    // Create a map to represent an attraction with specific information
                    Map<String, Object> attractionMap = new ConcurrentHashMap<>();
                    attractionMap.put("name", attraction.attractionName);
                    attractionMap.put("attractionLatitude", attraction.latitude);
                    attractionMap.put("attractionLongitude", attraction.longitude);
                    attractionMap.put("userLatitude", visitedLocation.location.latitude);
                    attractionMap.put("userLongitude", visitedLocation.location.longitude);
                    attractionMap.put("distance", rewardsService.getDistance(attraction, visitedLocation.location));
                    attractionMap.put("rewardPoints", rewardsService.getRewardPoints(attraction, user));
                    return attractionMap;
                })
                .collect(Collectors.toList());

        logger.debug("Found {} nearby attractions for user: {}", closestAttractions.size(), user.getUserName());

        return closestAttractions;
    }

	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				tracker.stopTracking();
			}
		});
	}

	/**********************************************************************************
	 * 
	 * Methods Below: For Internal Testing
	 * 
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes
	// internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();

	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);

			internalUserMap.put(userName, user);
		});
		logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}

	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i -> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
					new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

}
