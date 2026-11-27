package com.example.smartlibandbookhub;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.smartlibandbookhub.domain.Library;
import java.util.ArrayList;
import java.util.List;

public final class LoansFragment extends Fragment {
  private RecyclerView recycler;
  private View header;
  private Library displayedLibrary;
  private Library.Role displayedRole;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_loans, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    recycler = (RecyclerView) view;
    recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
    header = getLayoutInflater().inflate(R.layout.view_loans_header, recycler, false);
    header.findViewById(R.id.loans_explore).setOnClickListener(
        v -> ((MainActivity) requireActivity()).showExplore());
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
      if (displayedLibrary == state.library && displayedRole == state.role) return;
      displayedLibrary = state.library;
      displayedRole = state.role;
      List<Library.Loan> entries = new ArrayList<>();
      for (int i = state.library.loans.size() - 1; i >= 0; i--) {
        Library.Loan loan = state.library.loans.get(i);
        if (loan.readerId.equals(Library.READER_ID)) entries.add(loan);
      }
      int overdue = 0;
      long today = LibraryFormat.today();
      for (Library.Loan loan : entries) {
        if (loan.status == Library.Status.BORROWED && loan.dueDay < today) overdue++;
      }
      android.widget.TextView reminder = header.findViewById(R.id.loans_overdue);
      reminder.setVisibility(overdue > 0 ? View.VISIBLE : View.GONE);
      reminder.setText(getString(R.string.overdue_reminder, overdue));
      header.findViewById(R.id.loans_empty).setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
      LoanRowBinder binder = new LoanRowBinder(state.library, state.role, viewModel, activity::confirm);
      LibraryListAdapter adapter = new LibraryListAdapter(requireContext(), header, state.library,
          new LibraryListAdapter.Listener() {
            @Override public void onBookClick(Library.Book book) {}
            @Override public void bindLoan(View row, Library.Loan loan, boolean desk) {
              binder.bindLoan(row, loan, desk);
            }
          });
      adapter.showLoans(entries, false);
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
