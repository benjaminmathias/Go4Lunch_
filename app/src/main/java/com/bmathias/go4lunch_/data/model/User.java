package com.bmathias.go4lunch_.data.model;

import com.google.firebase.firestore.Exclude;

import java.io.Serializable;
import java.util.List;

public class User implements Serializable {

   public String uid;

   public String name;

   public String photoUrl;

   @SuppressWarnings("WeakerAccess")
   public String email;

   public String selectedRestaurant;

   public List<String> likedRestaurants;

   @Exclude
   public boolean isAuthenticated;

   @Exclude
   boolean isNew, isCreated;

   public User() {}

   public User(String uid, String name, String email) {
      this.uid = uid;
      this.name = name;
      this.email = email;
   }
}
