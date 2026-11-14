package com.example.smartlibandbookhub;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.smartlibandbookhub.domain.Library;

public final class ProfileFragment extends Fragment {
  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_profile, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    View editRules = view.findViewById(R.id.profile_edit_rules);
    view.findViewById(R.id.profile_firebase).setOnClickListener(v -> {
      if (!((MainActivity) requireActivity()).libraryViewModel().getState().getValue().loading) {
        startActivity(new Intent(requireContext(), FirebaseActivity.class));
      }
    });
    editRules.setOnClickListener(v -> {
      if (!((MainActivity) requireActivity()).libraryViewModel().getState().getValue().loading) {
        ((MainActivity) requireActivity()).editRules();
      }
    });
  }

  @Override
  public void onStart() {
    super.onStart();
    View view = requireView();
    View editRules = view.findViewById(R.id.profile_edit_rules);
    LibraryViewModel viewModel = ((MainActivity) requireActivity()).libraryViewModel();
    viewModel.getState().removeObservers(getViewLifecycleOwner());
    viewModel.getState().observe(getViewLifecycleOwner(), state -> {
      Library library = state.library;
      if (library == null) return;
      ((TextView) view.findViewById(R.id.profile_rules)).setText(getString(
          R.string.borrowing_rules_details, library.maxLoans, library.loanDays,
          library.maxRenewals, library.renewalDays, LibraryFormat.money(library.dailyFine)));
      editRules.setVisibility(state.role == Library.Role.ADMIN ? View.VISIBLE : View.GONE);
      editRules.setEnabled(!state.loading);
      view.findViewById(R.id.profile_firebase).setEnabled(!state.loading);
    });
  }
}
