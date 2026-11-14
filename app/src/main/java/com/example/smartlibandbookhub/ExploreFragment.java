package com.example.smartlibandbookhub;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.smartlibandbookhub.domain.Library;
import java.util.List;

public final class ExploreFragment extends Fragment {
  private RecyclerView recycler;
  private View header;
  private LibraryListAdapter adapter;
  private Library displayedLibrary;
  private Library.Role displayedRole;
  private String displayedQuery;
  private String displayedCategory;
  private String displayedSort;
  private boolean displayedAvailable;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_explore, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    recycler = (RecyclerView) view;
    recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
    header = getLayoutInflater().inflate(R.layout.view_explore_header, recycler, false);
    displayedLibrary = null;
  }

  @Override
  public void onStart() {
    super.onStart();
    MainActivity activity = (MainActivity) requireActivity();
    LibraryViewModel viewModel = activity.libraryViewModel();
    viewModel.getState().removeObservers(getViewLifecycleOwner());
    viewModel.getState().observe(getViewLifecycleOwner(), state -> {
      if (state.library == null) return;
      boolean available = viewModel.getSaved("only_available", false);
      String sortValue = viewModel.getSaved("sort_by", LibraryViewModel.SortBy.DEFAULT.name());
      LibraryViewModel.SortBy sort;
      try {
        sort = LibraryViewModel.SortBy.valueOf(sortValue);
      } catch (IllegalArgumentException e) {
        sort = LibraryViewModel.SortBy.DEFAULT;
      }
      boolean newAdapter = displayedLibrary != state.library || displayedRole != state.role || adapter == null;
      if (!newAdapter && state.query.equals(displayedQuery)
          && state.category.equals(displayedCategory) && sortValue.equals(displayedSort)
          && available == displayedAvailable) return;
      displayedLibrary = state.library;
      displayedRole = state.role;
      displayedQuery = state.query;
      displayedCategory = state.category;
      displayedSort = sortValue;
      displayedAvailable = available;
      List<Library.Book> books = viewModel.getFilteredBooks(state.query, state.category, available, sort);
      TextView first = header.findViewById(R.id.explore_first_book);
      first.setText(books.isEmpty() ? "" : books.get(0).title);
      ((TextView) header.findViewById(R.id.explore_count)).setText(getString(
          state.query.isEmpty() ? R.string.catalog_count : R.string.search_result_count, books.size()));
      header.findViewById(R.id.explore_empty).setVisibility(books.isEmpty() ? View.VISIBLE : View.GONE);
      if (newAdapter) {
        BookDialogs dialogs = new BookDialogs(requireContext(), state.library, state.role,
            viewModel, activity::editBook, activity::confirm);
        adapter = new LibraryListAdapter(requireContext(), header, state.library,
            new LibraryListAdapter.Listener() {
              @Override public void onBookClick(Library.Book book) { dialogs.showDetails(book); }
              @Override public void bindLoan(View row, Library.Loan loan, boolean desk) {}
            });
        adapter.showBooks(books);
        android.os.Parcelable position = recycler.getLayoutManager().onSaveInstanceState();
        recycler.setAdapter(adapter);
        recycler.getLayoutManager().onRestoreInstanceState(position);
      } else {
        adapter.showBooks(books);
      }
    });
  }

  @Override
  public void onDestroyView() {
    recycler.setAdapter(null);
    recycler = null;
    header = null;
    adapter = null;
    displayedLibrary = null;
    super.onDestroyView();
  }
}
