package com.openclassrooms.tourguide;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

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


	@ParameterizedTest
	@ValueSource(ints = {100, 1000})
	public void highVolumeTrackLocation(int userNumber) {
	    GpsUtil gpsUtil = new GpsUtil();
	    RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
	    // Users should be incremented up to 100,000, and test finishes within 15
	    // minutes
	    InternalTestHelper.setInternalUserNumber(userNumber);
	    TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

	    List<User> allUsers = new ArrayList<>();
	    allUsers = tourGuideService.getAllUsers();

	    StopWatch stopWatch = new StopWatch();
	    stopWatch.start();

	    // Parallelize the tracking of user locations and reward calculation
	    allUsers.parallelStream().forEach(user -> {
	        tourGuideService.trackUserLocation(user);
	    });

	    stopWatch.stop();
	    tourGuideService.tracker.stopTracking();

	    System.out.println("highVolumeTrackLocation: Time Elapsed: " + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");
	    assertTrue(TimeUnit.MINUTES.toSeconds(15) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}



	@ParameterizedTest
	@ValueSource(ints = {100, 1000, 10000, 100000})
	public void highVolumeGetRewards(int userNumber) {
	    GpsUtil gpsUtil = new GpsUtil();
	    RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

	    InternalTestHelper.setInternalUserNumber(userNumber);
	    StopWatch stopWatch = new StopWatch();
	    stopWatch.start();
	    TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

	    Attraction attraction = gpsUtil.getAttractions().get(0);
	    List<User> allUsers = tourGuideService.getAllUsers();

	    allUsers.forEach(u -> u.addToVisitedLocations(new VisitedLocation(u.getUserId(), attraction, new Date())));

	    allUsers.parallelStream().forEach(u -> rewardsService.calculateRewards(u));

	    allUsers.forEach(user -> assertTrue(user.getUserRewards().size() > 0));

	    stopWatch.stop();
	    tourGuideService.tracker.stopTracking();

	    System.out.println("highVolumeGetRewards: Time Elapsed: " + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime())
	            + " seconds.");
	    assertTrue(TimeUnit.MINUTES.toSeconds(20) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}

}
