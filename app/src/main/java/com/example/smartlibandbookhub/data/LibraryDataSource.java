package com.example.smartlibandbookhub.data;

import com.example.smartlibandbookhub.domain.Library;

public interface LibraryDataSource {
  Library read(long today) throws Exception;
  Library change(long today, LibraryStore.Change change) throws Exception;
}
