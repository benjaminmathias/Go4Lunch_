package com.bmathias.go4lunch_.data.repositories;

import static com.bmathias.go4lunch_.utils.Constants.TAG;
import static com.bmathias.go4lunch_.utils.Constants.USERS;

import android.annotation.SuppressLint;
import android.location.Location;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bmathias.go4lunch_.BuildConfig;
import com.bmathias.go4lunch_.data.model.LikedRestaurant;
import com.bmathias.go4lunch_.data.model.RestaurantDetails;
import com.bmathias.go4lunch_.data.model.RestaurantItem;
import com.bmathias.go4lunch_.data.model.User;
import com.bmathias.go4lunch_.data.model.UserLocation;
import com.bmathias.go4lunch_.data.network.PlacesApiService;
import com.bmathias.go4lunch_.data.network.model.DataResult;
import com.bmathias.go4lunch_.data.network.model.places.RestaurantApi;
import com.bmathias.go4lunch_.data.network.model.places.RestaurantsApiResult;
import com.bmathias.go4lunch_.data.network.model.placesDetails.DetailsResultAPI;
import com.bmathias.go4lunch_.data.network.model.placesDetails.RestaurantDetailsAPI;
import com.bmathias.go4lunch_.utils.LocationService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;

public class RestaurantRepository {

    private final String photoBaseUrl;
    private final PlacesApiService placesAPIService;
    private final LocationService mLocationService;
    private final MySharedPrefs sharedPrefs;

    private Double latitude;
    private Double longitude;

    private static volatile RestaurantRepository instance;

    private final FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
    private final FirebaseFirestore rootRef = FirebaseFirestore.getInstance();
    private final CollectionReference usersRef = rootRef.collection(USERS);
    private final CollectionReference likedRestaurantsRef = rootRef.collection("likedRestaurants");

    private static final String type = "restaurant";

    private RestaurantRepository(LocationService locationService, PlacesApiService placesAPIService, String photoBaseUrl, MySharedPrefs sharedPrefs) {
        this.placesAPIService = placesAPIService;
        this.photoBaseUrl = photoBaseUrl;
        this.mLocationService = locationService;
        this.sharedPrefs = sharedPrefs;
    }

    public static RestaurantRepository getInstance(LocationService locationService, PlacesApiService placesAPIService, String photoBaseUrl, MySharedPrefs sharedPrefs) {
        RestaurantRepository result = instance;
        if (result != null) {
            return result;
        }
        synchronized (RestaurantRepository.class) {
            if (instance == null) {
                instance = new RestaurantRepository(locationService, placesAPIService, photoBaseUrl, sharedPrefs);
            }
            return instance;
        }
    }

    // RESTAURANT //
    private Observable<RestaurantsApiResult> getRestaurantsApiResultObservable(UserLocation userLocation) {
        latitude = userLocation.getLatitude();
        longitude = userLocation.getLongitude();

        String location = latitude + "," + longitude;

        String radius = sharedPrefs.getString("radius", "1000");

        Log.d("User Location is", location);
        Log.d("Radius is", "" + radius);
        return placesAPIService.getRestaurants(location, "distance", type, BuildConfig.MAPS_API_KEY);
    }

    // Retrieve Restaurant and convert to our own model
    @SuppressLint("CheckResult")
    public LiveData<DataResult<List<RestaurantItem>>> streamFetchRestaurants() {

        MutableLiveData<DataResult<List<RestaurantItem>>> _restaurants = new MutableLiveData<>();

        Observable<UserLocation> userLocationObservable = mLocationService.retrieveLocation();

        Log.d("User Location ", "Stream called");
        userLocationObservable.flatMap(userLocation ->
                        Observable.zip(getRestaurantsApiResultObservable(userLocation), getSelectedRestaurantIdsObservable(), getNumberOfFavoriteObservable(), (restaurantsApiResult, selectedRestaurantIds, numberOfFavorites)
                                ->
                                restaurantsConverter(restaurantsApiResult.getResults(), photoBaseUrl, selectedRestaurantIds, numberOfFavorites))
                )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(restaurantsItems -> {
                    DataResult<List<RestaurantItem>> dataResult = new DataResult<>(restaurantsItems);
                    _restaurants.postValue(dataResult);
                }, throwable -> {
                    DataResult<List<RestaurantItem>> dataResult = new DataResult<>(throwable);
                    Log.e("onError", "======================================================");
                    Log.e("onError", "onError : " + throwable.getMessage());
                    throwable.printStackTrace();
                    Log.e("onError", "======================================================");
                    _restaurants.postValue(dataResult);
                });

        return _restaurants;
    }

    // RESTAURANT DETAILS //
    private Observable<DetailsResultAPI> getRestaurantsDetailsApiResultObservable(String placeId) {
        return placesAPIService.getRestaurantDetails(placeId, BuildConfig.MAPS_API_KEY);
    }

    // Retrieve RestaurantDetails and convert to our own model
    @SuppressLint("CheckResult")
    public LiveData<DataResult<RestaurantDetails>> streamFetchRestaurantDetails(String placeId) {

        MutableLiveData<DataResult<RestaurantDetails>> _restaurantDetails = new MutableLiveData<>();

        Observable.zip(getRestaurantsDetailsApiResultObservable(placeId), getCurrentUserFavoriteObservable(placeId), (detailsResultAPI, currentUserFavorite)
                        -> restaurantDetailsConverter(detailsResultAPI.getResult(), photoBaseUrl, currentUserFavorite)
                )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(detailsResult -> {
                    DataResult<RestaurantDetails> dataResult = new DataResult<>(detailsResult);
                    _restaurantDetails.postValue(dataResult);
                }, throwable -> {
                    DataResult<RestaurantDetails> dataResult = new DataResult<>(throwable);
                    _restaurantDetails.postValue(dataResult);
                });

        return _restaurantDetails;
    }

    // CONVERTERS
    // Convert the Restaurant model provided by Places API to our own model
    private static List<RestaurantItem> restaurantsConverter(List<RestaurantApi> restaurantApis, String photoBaseUrl, Set<String> selectedRestaurantIds, List<String> likedRestaurants) {
        List<RestaurantItem> restaurantItems = new ArrayList<>(restaurantApis.size());
        for (RestaurantApi restaurantAPI : restaurantApis) {
            RestaurantItem.Builder builder = new RestaurantItem.Builder()
                    .withName(restaurantAPI.getName())
                    .withAddress(restaurantAPI.getVicinity())
                    .withIsOpen(restaurantAPI.getOpeningHours())
                    .withPlaceId(restaurantAPI.getPlaceId());

            if (restaurantAPI.getGeometry() != null) {
                builder.withLongitude(restaurantAPI.getGeometry().getLocation().getLng());
                builder.withLatitude(restaurantAPI.getGeometry().getLocation().getLat());
                builder.withDistance(Math.round(getDistance(
                        restaurantAPI.getGeometry().getLocation().getLat(),
                        restaurantAPI.getGeometry().getLocation().getLng(),
                        instance.latitude,
                        instance.longitude))
                );
            }

            if (restaurantAPI.getPhotos() != null && !restaurantAPI.getPhotos().isEmpty()) {
                String photoReference = restaurantAPI.getPhotos().get(0).getPhotoReference();
                String photoUrl = photoBaseUrl + photoReference + "&key=" + BuildConfig.MAPS_API_KEY;
                builder.withPhoto(photoUrl);
            }

            builder.withIsSomeoneEating(selectedRestaurantIds.contains(restaurantAPI.getPlaceId()));

           // builder.withNumberOfFavorites(likedRestaurants.stream().filter(id -> id.equals(restaurantAPI.getPlaceId())).collect(Collectors.toList()));
            /*
            if (likedRestaurants != null) {
                Set<String> distinct = new HashSet<>(likedRestaurants);
                for (String s : distinct) {
                    builder.withNumberOfFavorites(Collections.frequency(likedRestaurants, restaurantAPI.getPlaceId()));
                }
                Log.d(TAG, "Number of likes for " + restaurantAPI.getName() + " = " + distinct.size());
            }*/

            if (likedRestaurants != null) {
                int numberOfFavorites = Collections.frequency(likedRestaurants, restaurantAPI.getPlaceId());
                builder.withNumberOfFavorites(numberOfFavorites);
                Log.d(TAG, "Number of likes for " + restaurantAPI.getName() + " = " + numberOfFavorites);
            }


            Log.d("RestaurantRepository", restaurantAPI.getName() + " isSomeoneEating = " + selectedRestaurantIds.contains(restaurantAPI.getPlaceId()));

            RestaurantItem restaurantItem = builder.build();
            restaurantItems.add(restaurantItem);
        }
        return restaurantItems;
    }

    // Convert the RestaurantDetails model provided by Places Details API to our own model
    private static RestaurantDetails restaurantDetailsConverter(RestaurantDetailsAPI restaurantDetailsAPI, String photoBaseUrl, Boolean isCurrentUserFavorite) {
        RestaurantDetails.Builder builder = new RestaurantDetails.Builder()
                .withAddress(restaurantDetailsAPI.getFormattedAddress());

        if (restaurantDetailsAPI.getName() != null) {
            builder.withName(restaurantDetailsAPI.getName());
        }

        if (restaurantDetailsAPI.getPlaceId() != null) {
            builder.withPlaceId(restaurantDetailsAPI.getPlaceId());
        }
        if (restaurantDetailsAPI.getInternationalPhoneNumber() != null) {
            builder.withPhoneNumber(restaurantDetailsAPI.getFormattedPhoneNumber());
        }

        if (restaurantDetailsAPI.getWebsite() != null) {
            builder.withWebsite(restaurantDetailsAPI.getWebsite());
        }

        if (restaurantDetailsAPI.getPhotos() != null && !restaurantDetailsAPI.getPhotos().isEmpty()) {
            String photoReference = restaurantDetailsAPI.getPhotos().get(0).getPhotoReference();
            String photoUrl = photoBaseUrl + photoReference + "&key=" + BuildConfig.MAPS_API_KEY;
            builder.withPhotoUrl(photoUrl);
        }

        builder.withIsCurrentUserFavorite(isCurrentUserFavorite);

        return builder.build();
    }

    // Update user selected restaurant
    public void updateSelectedRestaurant(String placeId, String placeName) {

        if (firebaseAuth.getUid() != null) {
            DocumentReference userRef = rootRef.collection(USERS).document(firebaseAuth.getUid());
            userRef.update("selectedRestaurantId", placeId).addOnSuccessListener(aVoid -> Log.d(TAG, "selectedRestaurantId successfully updated!"))
                    .addOnFailureListener(e -> Log.w(TAG, "Error updating document", e));
            userRef.update("selectedRestaurantName", placeName).addOnSuccessListener(aVoid -> Log.d(TAG, "selectedRestaurantName successfully updated!"))
                    .addOnFailureListener(e -> Log.w(TAG, "Error updating document", e));
        }
    }

    // Delete user selected restaurant
    public void deleteSelectedRestaurant() {

        if (firebaseAuth.getUid() != null) {
            DocumentReference userRef = rootRef.collection(USERS).document(firebaseAuth.getUid());
            userRef.update("selectedRestaurantId", null).addOnSuccessListener(aVoid -> Log.d(TAG, "selectedRestaurantId successfully deleted!"))
                    .addOnFailureListener(e -> Log.w(TAG, "Error updating document", e));
            userRef.update("selectedRestaurantName", null).addOnSuccessListener(aVoid -> Log.d(TAG, "selectedRestaurantName successfully deleted!"))
                    .addOnFailureListener(e -> Log.w(TAG, "Error updating document", e));
        }
    }

    // Add a favorite restaurant
    public void addFavoriteRestaurant(String placeId) {
        if (firebaseAuth.getUid() != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("restaurantId", placeId);
            data.put("userId", firebaseAuth.getUid());

            likedRestaurantsRef.add(data)
                    .addOnSuccessListener(documentReference -> Log.d(TAG, "DocumentSnapshot written with ID: " + documentReference.getId()))
                    .addOnFailureListener(e -> Log.w(TAG, "Error adding document", e));
        }
    }

    // Remove a specific favorite restaurant
    public void removeFavoriteRestaurant(String placeId) {
        if (firebaseAuth.getUid() != null) {

            Query query = likedRestaurantsRef.whereEqualTo("restaurantId", placeId)
                    .whereEqualTo("userId", firebaseAuth.getUid());

            query.get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    for (DocumentSnapshot document : task.getResult()) {
                        likedRestaurantsRef.document(document.getId()).delete();
                    }
                } else {
                    Log.d(TAG, "Error getting documents: ", task.getException());
                }
            });
        }
    }

    // Retrieve a set of Restaurant selected by users
    public Observable<Set<String>> getSelectedRestaurantIdsObservable() {
        BehaviorSubject<Set<String>> restaurantIdsObservable = BehaviorSubject.create();

        usersRef.whereNotEqualTo("selectedRestaurantId", null)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.d(TAG, "Listen failed.", error);
                        return;
                    }

                    List<User> users = Objects.requireNonNull(value).toObjects(User.class);

                    Log.d(TAG, "Example ID : " + users.get(0).getSelectedRestaurantId());
                    Set<String> restaurantIds = new HashSet<>();
                    for (User user : users) {
                        restaurantIds.add(user.getSelectedRestaurantId());
                    }

                    restaurantIdsObservable.onNext(restaurantIds);
                });

        return restaurantIdsObservable;
    }

    // Retrieve the amount of favorite
    public Observable<List<String>> getNumberOfFavoriteObservable() {
        BehaviorSubject<List<String>> restaurantLikesObservable = BehaviorSubject.create();

        likedRestaurantsRef.whereNotEqualTo("restaurantId", null)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.d(TAG, "Listen failed.", error);
                        return;
                    }

                    List<LikedRestaurant> likedRestaurants = Objects.requireNonNull(value).toObjects(LikedRestaurant.class);

                    Log.d(TAG, "Example ID : " + likedRestaurants.get(0).getRestaurantId());

                    List<String> restaurantLikes = new ArrayList<>();
                    for (LikedRestaurant likedRestaurant : likedRestaurants) {
                        restaurantLikes.add(likedRestaurant.getRestaurantId());
                    }

                    Log.d(TAG, "Number of likes : " + restaurantLikes.size());
                    Log.d(TAG, "Liked  new list ID : " + Arrays.toString(restaurantLikes.toArray()));


                    restaurantLikesObservable.onNext(restaurantLikes);
                });

        return restaurantLikesObservable;
    }

    // Retrieve if the current user has favorited a specific restaurant
    public Observable<Boolean> getCurrentUserFavoriteObservable(String placeId) {
        BehaviorSubject<Boolean> currentUserFavoriteObservable = BehaviorSubject.create();

        likedRestaurantsRef.whereEqualTo("restaurantId", placeId)
                .whereEqualTo("userId", firebaseAuth.getUid())
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.d(TAG, "Listen failed.", error);
                        return;
                    }

                    boolean currentUserFavorite = !Objects.requireNonNull(value).getDocuments().isEmpty();

                    currentUserFavoriteObservable.onNext(currentUserFavorite);
                });

        return currentUserFavoriteObservable;
    }

    // Compute distance between user's phone and restaurant location
    public static float getDistance(Double latA, Double lngA, Double latB, Double lngB) {
        Location locationA = new Location("point A");

        locationA.setLatitude(latA);
        locationA.setLongitude(lngA);

        Location locationB = new Location("point B");

        locationB.setLatitude(latB);
        locationB.setLongitude(lngB);

        Log.d("RestaurantRepository", "Distance = " + Math.round(locationA.distanceTo(locationB)));
        return Math.round(locationA.distanceTo(locationB));
    }
}
