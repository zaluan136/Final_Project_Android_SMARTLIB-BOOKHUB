package com.example.smartlibandbookhub;

import android.app.Application;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AbstractSavedStateViewModelFactory;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.savedstate.SavedStateRegistryOwner;
import com.example.smartlibandbookhub.data.LibraryDataSource;

public final class LibraryViewModelFactory extends AbstractSavedStateViewModelFactory {
  private final Application application;
  private final LibraryDataSource repository;

  public LibraryViewModelFactory(SavedStateRegistryOwner owner, @Nullable Bundle arguments,
      Application application, LibraryDataSource repository) {
    super(owner, arguments);
    this.application = application;
    this.repository = repository;
  }

  @NonNull
  @Override
  protected <T extends ViewModel> T create(@NonNull String key, @NonNull Class<T> modelClass,
      @NonNull SavedStateHandle handle) {
    if (modelClass.isAssignableFrom(LibraryViewModel.class)) {
      return modelClass.cast(new LibraryViewModel(application, handle, repository));
    }
    throw new IllegalArgumentException("Unknown ViewModel: " + modelClass);
  }
}
