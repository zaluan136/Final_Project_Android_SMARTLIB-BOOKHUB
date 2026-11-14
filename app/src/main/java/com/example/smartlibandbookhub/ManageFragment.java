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
import java.util.ArrayList;
import java.util.List;

public final class ManageFragment extends Fragment {
  private RecyclerView recycler;
  private View header;
  private Library displayedLibrary;
  private Library.Role displayedRole;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_manage, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    recycler = (RecyclerView) view;
    recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
    header = getLayoutInflater().inflate(R.layout.view_manage_header, recycler, false);
    header.findViewById(R.id.manage_add_book).setOnClickListener(
        v -> ((MainActivity) requireActivity()).addBook());
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
      header.findViewById(R.id.manage_add_book).setEnabled(!state.loading);
      if (displayedLibrary == state.library && displayedRole == state.role) return;
      displayedLibrary = state.library;
      displayedRole = state.role;
      Library library = state.library;
      boolean staff = state.role != Library.Role.READER;
      header.findViewById(R.id.manage_reader_guard).setVisibility(staff ? View.GONE : View.VISIBLE);
      header.findViewById(R.id.manage_staff_content).setVisibility(staff ? View.VISIBLE : View.GONE);
      List<Library.Loan> entries = new ArrayList<>();
      if (staff) {
        int total = 0, available = 0, borrowed = 0, waiting = 0;
        long fees = 0;
        for (Library.Book book : library.books) {
          if (!book.archived) {
            total += book.copies;
            available += library.available(book.id);
          }
        }
        for (Library.Loan loan : library.loans) {
          if (loan.status == Library.Status.BORROWED) borrowed++;
          if (loan.status == Library.Status.WAITING) waiting++;
          if (loan.status == Library.Status.RETURNED) fees += library.fine(loan, LibraryFormat.today());
        }
        ((TextView) header.findViewById(R.id.manage_summary)).setText(
            getString(R.string.library_overview_details, total, available, borrowed, waiting));
        ((TextView) header.findViewById(R.id.manage_fees)).setText(
            getString(R.string.recorded_fees, LibraryFormat.money(fees)));
        for (int i = library.loans.size() - 1; i >= 0; i--) entries.add(library.loans.get(i));
        header.findViewById(R.id.manage_empty).setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
      }
      LoanRowBinder binder = new LoanRowBinder(library, state.role, viewModel, activity::confirm);
      LibraryListAdapter adapter = new LibraryListAdapter(requireContext(), header, library,
          new LibraryListAdapter.Listener() {
            @Override public void onBookClick(Library.Book book) {}
            @Override public void bindLoan(View row, Library.Loan loan, boolean desk) {
              binder.bindLoan(row, loan, desk);
            }
          });
      adapter.showLoans(entries, true);
      android.os.Parcelable position = recycler.getLayoutManager().onSaveInstanceState();
      recycler.setAdapter(adapter);
      recycler.getLayoutManager().onRestoreInstanceState(position);
    });
  }

  @Override
  public void onDestroyView() {
    recycler.setAdapter(null);
    recycler = null;
    header = null;
    displayedLibrary = null;
    super.onDestroyView();
  }
}
