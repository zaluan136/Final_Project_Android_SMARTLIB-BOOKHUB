package com.example.smartlibandbookhub.data;

import android.content.Context;
import com.example.smartlibandbookhub.domain.Library;

public final class LibraryRepository implements LibraryDataSource {
  private final LibraryStore store;

  public LibraryRepository(Context context) {
    store = new LibraryStore(context.getApplicationContext());
  }

  public Library read(long today) throws Exception {
    return store.read(today);
  }

  public Library change(long today, LibraryStore.Change change) throws Exception {
    return store.change(today, change);
  }
}
