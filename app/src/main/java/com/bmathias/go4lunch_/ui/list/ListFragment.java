package com.bmathias.go4lunch_.ui.list;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bmathias.go4lunch_.data.network.model.places.RestaurantApi;
import com.bmathias.go4lunch_.injection.Injection;
import com.bmathias.go4lunch_.injection.ViewModelFactory;
import com.bmathias.go4lunch_.viewmodel.ListViewModel;
import com.bmathias.go4lunch_.databinding.FragmentListBinding;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.reactivex.Observer;


public class ListFragment extends Fragment implements ListAdapter.OnRestaurantListener {

    private ListViewModel listViewModel;
    private ListAdapter adapter;
    private FragmentListBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentListBinding.inflate(inflater, container, false);

        this.setupViewModel();
        this.setupRecyclerView();
        return binding.getRoot();
    }

    private void setupViewModel(){
        ViewModelFactory viewModelFactory = Injection.provideViewModelFactory();
        this.listViewModel = new ViewModelProvider(this, viewModelFactory).get(ListViewModel.class);
        this.listViewModel.observeRestaurants();
    }

    private void setupRecyclerView(){
        binding.fragmentListRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ListAdapter(Collections.emptyList(), this);
        binding.fragmentListRecyclerView.setAdapter(adapter);

        listViewModel.getRestaurants().observe(getViewLifecycleOwner(), restaurantItems -> {
            adapter.setRestaurantItems(restaurantItems);
            adapter.notifyDataSetChanged();
//            binding.fragmentListRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
//            adapter = new ListAdapter(restaurantItems, this);
//            binding.fragmentListRecyclerView.setAdapter(adapter);
        });
    }

    @Override
    public void onRestaurantClick(String placeId) {
        Toast.makeText(this.getActivity(), "Click on " + placeId, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(getActivity(), DetailsActivity.class);
        intent.putExtra("placeId", placeId);
        startActivity(intent);
    }
}
