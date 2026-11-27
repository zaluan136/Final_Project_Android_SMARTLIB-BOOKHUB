package com.example.smartlibandbookhub;

import static com.example.smartlibandbookhub.LibraryFormat.today;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.example.smartlibandbookhub.domain.Library;
import com.example.smartlibandbookhub.LibraryViewModel.SortBy;
import com.example.smartlibandbookhub.data.LibraryRepository;
import com.example.smartlibandbookhub.domain.Library.*;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MainActivity extends AppCompatActivity {

  private LibraryViewModel viewModel;
  private LibraryFormDialogs formDialogs;
  private Library library;
  private Role role = Role.READER;
  private MaterialButton roleButton;
  private NavigationBarView navigation;
  private View searchBarContainer;
  private EditText searchInput;
  private ImageView searchClear;
  private TextView topTitle;
  private View filterScroll;
  private ChipGroup filterChips;
  private final List<Chip> categoryChipViews = new java.util.ArrayList<>();
  private Chip sortChip;
  private Chip availChip;
  private int tab = R.id.nav_explore;
  private String query = "", category = "Tất cả";
  private SortBy sortBy = SortBy.DEFAULT;
  private boolean onlyAvailable = false;
  private boolean busy;
  private static final String[] ROLES = {"Độc giả", "Thủ thư", "Quản trị"};

  @Override
  protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    EdgeToEdge.enable(this);
    setContentView(R.layout.activity_main);
    viewModel = new ViewModelProvider(this,
        new LibraryViewModelFactory(this, getIntent().getExtras(), getApplication(),
            new LibraryRepository(getApplicationContext()))).get(LibraryViewModel.class);
    formDialogs = new LibraryFormDialogs(this, viewModel, saved);
    if (getSupportFragmentManager().findFragmentById(R.id.explore_container) == null) {
      getSupportFragmentManager().beginTransaction()
          .add(R.id.explore_container, new ExploreFragment()).commitNow();
    }
    if (getSupportFragmentManager().findFragmentById(R.id.profile_container) == null) {
      getSupportFragmentManager().beginTransaction()
          .add(R.id.profile_container, new ProfileFragment()).commitNow();
    }
    if (getSupportFragmentManager().findFragmentById(R.id.loans_container) == null) {
      getSupportFragmentManager().beginTransaction()
          .add(R.id.loans_container, new LoansFragment()).commitNow();
    }
    if (getSupportFragmentManager().findFragmentById(R.id.manage_container) == null) {
      getSupportFragmentManager().beginTransaction()
          .add(R.id.manage_container, new ManageFragment()).commitNow();
    }
    ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.main),
        (v, insets) -> {
          Insets b =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
          v.setPadding(b.left, b.top, b.right, b.bottom);
          return WindowInsetsCompat.CONSUMED;
        });
    role = Role.valueOf(viewModel.getSaved("role", Role.READER.name()));
    tab = viewModel.getSaved("tab", R.id.nav_explore);
    query = viewModel.getSaved("query", "");
    category = viewModel.getSaved("category", "Tất cả");
    try {
      sortBy = SortBy.valueOf(viewModel.getSaved("sort_by", SortBy.DEFAULT.name()));
    } catch (IllegalArgumentException e) {
      sortBy = SortBy.DEFAULT;
    }
    onlyAvailable = viewModel.getSaved("only_available", false);
    roleButton = findViewById(R.id.role_button);
    navigation = findViewById(R.id.navigation);
    searchBarContainer = findViewById(R.id.search_bar_container);
    searchInput = findViewById(R.id.search_input);
    searchClear = findViewById(R.id.search_clear);
    topTitle = findViewById(R.id.top_title);
    filterScroll = findViewById(R.id.filter_scroll);
    filterChips = findViewById(R.id.filter_chips);
    if (searchInput != null) {
      if (!query.isEmpty()) searchInput.setText(query);
      if (searchClear != null) searchClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
      searchInput.addTextChangedListener(
          new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            public void onTextChanged(CharSequence s, int start, int before, int count) {
              query = s.toString();
              viewModel.setQuery(query);
              if (searchClear != null) {
                searchClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
              }
            }

            public void afterTextChanged(Editable s) {}
          });
    }
    if (searchClear != null) {
      searchClear.setOnClickListener(v -> {
        if (searchInput != null) searchInput.setText("");
      });
    }
    roleButton.setText(ROLES[role.ordinal()]);
    roleButton.setOnClickListener(
        v ->
            new MaterialAlertDialogBuilder(this)
                .setTitle("Vai trò trong bản demo")
                .setSingleChoiceItems(
                    ROLES,
                    role.ordinal(),
                    (d, which) -> {
                      role = Role.values()[which];
                      viewModel.setRole(role);
                      roleButton.setText(ROLES[which]);
                      d.dismiss();
                      render();
                    })
                .setNegativeButton("Đóng", null)
                .show());
    navigation.setSelectedItemId(tab);
    navigation.setOnItemSelectedListener(
        item -> {
          if (busy) return false;
          tab = item.getItemId();
          viewModel.setTab(tab);
          render();
          return true;
        });
    viewModel.getState().observe(this, state -> {
      busy = state.loading;
      roleButton.setEnabled(!busy);
      findViewById(R.id.loading).setVisibility(busy ? View.VISIBLE : View.GONE);
      formDialogs.setBusy(busy);
      if (library != state.library) {
        library = state.library;
        render();
        formDialogs.restoreForm();
      }
    });
    viewModel.getResult().observe(this, result -> {
      if (result == null) return;
      if (result.error == null) {
        viewModel.consumeResult();
        if (result.form) formDialogs.dismiss();
        if (result.message != null) notice(result.message);
      } else if (!result.form && result.error instanceof IllegalArgumentException) {
        viewModel.consumeResult();
        notice(result.error.getMessage());
      } else {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        if (result.form) {
          builder.setTitle("Chưa thể lưu")
              .setMessage(result.error instanceof IllegalArgumentException
                  ? result.error.getMessage()
                  : "Không thể lưu dữ liệu. Kiểm tra dung lượng thiết bị và thử lại.")
              .setPositiveButton("Đã hiểu", (d, w) -> viewModel.consumeResult());
        } else {
          builder.setTitle("Không thể đọc hoặc lưu dữ liệu")
              .setMessage("Thao tác chưa được xác nhận. Dữ liệu hiện có được giữ lại; hãy kiểm tra dung lượng thiết bị rồi thử lại. Không xóa dữ liệu ứng dụng nếu cần khôi phục.")
              .setPositiveButton("Thử lại", (d, w) -> {
                viewModel.consumeResult();
                load();
              })
              .setNegativeButton("Đóng", (d, w) -> viewModel.consumeResult());
        }
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setOnCancelListener(d -> viewModel.consumeResult());
        dialog.show();
      }
    });
    if (viewModel.getState().getValue().library == null && !busy) load();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putString("role", role.name());
    out.putInt("tab", tab);
    out.putString("query", query);
    out.putString("category", category);
    out.putString("sort_by", sortBy.name());
    out.putBoolean("only_available", onlyAvailable);
    formDialogs.saveState(out);
  }

  private void load() {
    viewModel.load(today());
  }

  private void render() {
    if (library == null) return;
    if (searchBarContainer != null && topTitle != null) {
      if (tab == R.id.nav_explore) {
        searchBarContainer.setVisibility(View.VISIBLE);
        if (filterScroll != null) filterScroll.setVisibility(View.VISIBLE);
        topTitle.setVisibility(View.GONE);
      } else {
        searchBarContainer.setVisibility(View.GONE);
        if (filterScroll != null) filterScroll.setVisibility(View.GONE);
        topTitle.setVisibility(View.VISIBLE);
        if (tab == R.id.nav_loans) topTitle.setText(R.string.loans);
        else if (tab == R.id.nav_manage) topTitle.setText(R.string.manage);
        else topTitle.setText(R.string.profile);
      }
    }
    setupFilterChips();
    findViewById(R.id.explore_container).setVisibility(tab == R.id.nav_explore ? View.VISIBLE : View.GONE);
    findViewById(R.id.loans_container).setVisibility(tab == R.id.nav_loans ? View.VISIBLE : View.GONE);
    findViewById(R.id.manage_container).setVisibility(tab == R.id.nav_manage ? View.VISIBLE : View.GONE);
    findViewById(R.id.profile_container).setVisibility(tab == R.id.nav_profile ? View.VISIBLE : View.GONE);
  }

  private void setupFilterChips() {
    if (filterChips == null || library == null) return;
    filterChips.removeAllViews();
    categoryChipViews.clear();

    // 1. Sort Chip
    sortChip = new Chip(this, null, com.google.android.material.R.attr.chipStyle);
    sortChip.setChipIconResource(R.drawable.ic_sort);
    sortChip.setChipIconVisible(true);
    sortChip.setCheckable(false);
    sortChip.setEnsureMinTouchTargetSize(true);
    updateSortChipDisplay();
    sortChip.setOnClickListener(
        v -> {
          String[] labels = new String[SortBy.values().length];
          for (int i = 0; i < SortBy.values().length; i++) labels[i] = SortBy.values()[i].label;
          new MaterialAlertDialogBuilder(this)
              .setTitle("Sắp xếp danh sách")
              .setSingleChoiceItems(
                  labels,
                  sortBy.ordinal(),
                  (d, which) -> {
                    sortBy = SortBy.values()[which];
                    viewModel.setSortBy(sortBy);
                    updateSortChipDisplay();
                    d.dismiss();
                  })
              .setNegativeButton("Đóng", null)
              .show();
        });
    filterChips.addView(sortChip);

    // 2. Only Available Filter Chip
    availChip = new Chip(this, null, com.google.android.material.R.attr.chipStyle);
    availChip.setText(R.string.only_available);
    availChip.setCheckable(true);
    availChip.setChecked(onlyAvailable);
    availChip.setOnClickListener(
        v -> {
          onlyAvailable = availChip.isChecked();
          viewModel.setOnlyAvailable(onlyAvailable);
        });
    filterChips.addView(availChip);

    // 3. Category Filter Chips
    Set<String> categories = new LinkedHashSet<>();
    categories.add("Tất cả");
    for (Book b : library.books) if (!b.archived) categories.add(b.category);
    if (!categories.contains(category)) {
      category = "Tất cả";
      viewModel.setCategory(category);
    }
    for (String name : categories) {
      Chip c = new Chip(this, null, com.google.android.material.R.attr.chipStyle);
      c.setText(name);
      c.setCheckable(true);
      c.setEnsureMinTouchTargetSize(true);
      c.setChecked(name.equals(category));
      c.setOnClickListener(
          v -> {
            category = name;
            viewModel.setCategory(category);
            for (Chip other : categoryChipViews) {
              other.setChecked(other.getText().toString().equals(category));
            }
            });
      categoryChipViews.add(c);
      filterChips.addView(c);
    }
  }

  private void updateSortChipDisplay() {
    if (sortChip == null) return;
    sortChip.setText(sortBy == SortBy.DEFAULT ? getString(R.string.sort)
        : getString(R.string.sort_selected, sortBy.label));
  }

  void editBook(Book book) {
    if (!busy) formDialogs.editBook(book);
  }

  void addBook() {
    if (!busy) formDialogs.editBook(null);
  }

  void editRules() {
    formDialogs.editRules();
  }

  LibraryViewModel libraryViewModel() {
    return viewModel;
  }



  void showExplore() {
    if (!busy) navigation.setSelectedItemId(R.id.nav_explore);
  }

  void confirm(String message, Runnable action) {
    if (busy) return;
    new MaterialAlertDialogBuilder(this)
        .setTitle("Xác nhận thao tác")
        .setMessage(message)
        .setNegativeButton("Quay lại", null)
        .setPositiveButton("Xác nhận", (d, w) -> action.run())
        .show();
  }

  private void notice(String message) {
    Snackbar snackbar = Snackbar.make(findViewById(R.id.main), message, Snackbar.LENGTH_LONG);
    if (navigation instanceof com.google.android.material.bottomnavigation.BottomNavigationView) {
      snackbar.setAnchorView(navigation);
    }
    snackbar.show();
  }


}
