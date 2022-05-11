package com.bmathias.go4lunch_.data.repositories;

import static com.bmathias.go4lunch_.utils.Constants.TAG;
import static com.bmathias.go4lunch_.utils.Constants.USERS;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bmathias.go4lunch_.data.model.RestaurantItem;
import com.bmathias.go4lunch_.data.model.User;
import com.bmathias.go4lunch_.data.network.model.DataResult;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public final class UsersRepository {

    private static volatile UsersRepository instance;

    private FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
    private final FirebaseFirestore rootRef = FirebaseFirestore.getInstance();
    private final CollectionReference usersRef = rootRef.collection(USERS);

    private UsersRepository() {
    }

    public static UsersRepository getInstance() {
        UsersRepository result = instance;
        if (result != null) {
            return result;
        }
        synchronized (UsersRepository.class) {
            if (instance == null) {
                instance = new UsersRepository();
            }
            return instance;
        }
    }

    // Retrieve all workmates besides current logged user
    public LiveData<List<User>> retrieveFirestoreUsers() {

        MutableLiveData<List<User>> _users = new MutableLiveData<>();

        usersRef.whereNotEqualTo("userId", firebaseAuth.getUid())
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            List<User> users = task.getResult().toObjects(User.class);
                            _users.postValue(users);
                            Log.d(TAG, document.getId() + " => " + document.getData());
                        }
                    } else {
                        Log.d(TAG, "Error getting documents: ", task.getException());
                    }
                });

        return _users;
    }

    // Retrieve only users eating at said restaurant in realtime
    public LiveData<List<User>> retrieveSpecificEatingUsers(String placeId) {

        MutableLiveData<List<User>> _specificUsers = new MutableLiveData<>();

        usersRef.whereEqualTo("selectedRestaurantId", placeId)
                .addSnapshotListener((value, error) -> {

                    if (error != null) {
                        Log.d(TAG, "Listen failed.", error);
                        return;
                    }

                    List<User> users = Objects.requireNonNull(value).toObjects(User.class);
                    for (QueryDocumentSnapshot document : value) {
                        _specificUsers.postValue(users);
                        Log.d(TAG, document.getId() + " => " + document.getData());
                    }

                });

        return _specificUsers;
    }
}

