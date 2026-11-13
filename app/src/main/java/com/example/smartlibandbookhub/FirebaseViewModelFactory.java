package com.example.smartlibandbookhub;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.smartlibandbookhub.data.FirebaseDataSource;

public final class FirebaseViewModelFactory implements ViewModelProvider.Factory {
  private final FirebaseDataSource repository;

  public FirebaseViewModelFactory(FirebaseDataSource repository) {
    this.repository = repository;
  }

  @NonNull
  @Override
  public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
    if (modelClass.isAssignableFrom(FirebaseViewModel.class)) {
      return modelClass.cast(new FirebaseViewModel(repository));
    }
    throw new IllegalArgumentException("Unknown ViewModel: " + modelClass);
  }
}
