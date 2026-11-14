package com.example.smartlibandbookhub;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import com.example.smartlibandbookhub.domain.Library;
import com.example.smartlibandbookhub.domain.Library.Book;
import com.example.smartlibandbookhub.domain.Library.Loan;
import java.util.List;
import java.util.Locale;

final class LibraryListAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<LibraryListAdapter.Holder> {
    interface Listener {
      void onBookClick(Book book);
      void bindLoan(View row, Loan loan, boolean desk);
    }

    private final Context context;
    private final View content;
    private final Library library;
    private final Listener listener;

    LibraryListAdapter(Context context, View content, Library library, Listener listener) {
      this.context = context;
      this.content = content;
      this.library = library;
      this.listener = listener;
    }

    private List<Book> books = java.util.Collections.emptyList();
    private List<Loan> loans = java.util.Collections.emptyList();
    private boolean showBooks;
    private boolean desk;

    void showBooks(List<Book> values) {
      int previous = getItemCount() - 1;
      books = new java.util.ArrayList<>(values);
      loans = java.util.Collections.emptyList();
      showBooks = true;
      if (previous > 0) notifyItemRangeRemoved(1, previous);
      if (!books.isEmpty()) notifyItemRangeInserted(1, books.size());
    }

    void showLoans(List<Loan> values, boolean deskMode) {
      books = java.util.Collections.emptyList();
      loans = new java.util.ArrayList<>(values);
      showBooks = false;
      desk = deskMode;
    }

    @Override public int getItemCount() {
      return 1 + (showBooks ? books.size() : loans.size());
    }

    @Override public int getItemViewType(int position) {
      return position == 0 ? 0 : (showBooks ? 1 : 2);
    }

    @Override public Holder onCreateViewHolder(android.view.ViewGroup parent, int type) {
      if (type == 1) return new Holder(LayoutInflater.from(context).inflate(R.layout.item_book, parent, false));
      if (type == 2) return new Holder(LayoutInflater.from(context).inflate(R.layout.item_loan, parent, false));
      android.widget.FrameLayout frame = new android.widget.FrameLayout(parent.getContext());
      frame.setLayoutParams(new androidx.recyclerview.widget.RecyclerView.LayoutParams(-1, -2));
      return new Holder(frame);
    }

    @Override public void onBindViewHolder(Holder holder, int position) {
      if (position == 0) {
        android.widget.FrameLayout frame = (android.widget.FrameLayout) holder.itemView;
        if (content.getParent() instanceof android.view.ViewGroup) {
          ((android.view.ViewGroup) content.getParent()).removeView(content);
        }
        frame.removeAllViews();
        frame.addView(content);
      } else if (showBooks) {
        int index = position - 1;
        Book b = books.get(index);
        View row = holder.itemView;
        if (row instanceof com.google.android.material.listitem.ListItemLayout) {
          ((com.google.android.material.listitem.ListItemLayout) row).updateAppearance(index, books.size());
        }
        TextView coverView = row.findViewById(R.id.book_cover);
        coverView.setText(b.title);
        GradientDrawable cover = new GradientDrawable();
        cover.setColor(b.color);
        cover.setCornerRadius(8 * context.getResources().getDisplayMetrics().density);
        coverView.setBackground(cover);
        ((TextView) row.findViewById(R.id.book_title)).setText(b.title);
        ((TextView) row.findViewById(R.id.book_category))
            .setText(b.category.toUpperCase(Locale.ROOT) + "  ·  KỆ " + b.shelf.toUpperCase(Locale.ROOT));
        ((TextView) row.findViewById(R.id.book_author)).setText(b.author);
        int available = library.available(b.id);
        TextView stockView = row.findViewById(R.id.book_stock);
        if (available > 0) {
          stockView.setText("● Còn " + available + " / " + b.copies + " bản");
          stockView.setTextColor(context.getColor(R.color.forest));
        } else {
          stockView.setText("○ Hết bản  ·  Có thể đăng ký chờ");
          stockView.setTextColor(context.getColor(R.color.muted));
        }
        row.setOnClickListener(v -> listener.onBookClick(b));
      } else {
        int index = position - 1;
        if (holder.itemView instanceof com.google.android.material.listitem.ListItemLayout) {
          ((com.google.android.material.listitem.ListItemLayout) holder.itemView).updateAppearance(index, loans.size());
        }
        listener.bindLoan(holder.itemView, loans.get(index), desk);
      }
    }

    final class Holder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
      Holder(View view) { super(view); }
    }
  }

