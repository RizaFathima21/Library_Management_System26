package com.example.library.controller;

import com.example.library.dto.BookRequest;
import com.example.library.exception.BookNotFoundException;
import com.example.library.model.Book;
import com.example.library.repository.BookRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/books")
@CrossOrigin(origins = "*")
public class BookController {

    private final BookRepository repository;

    public BookController(BookRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<List<Book>> getAllBooks(@RequestParam(required = false) String search) {
        if (search != null && !search.trim().isEmpty()) {
            return ResponseEntity.ok(
                repository.findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCaseOrIsbnContainingIgnoreCase(search, search, search)
            );
        }
        return ResponseEntity.ok(repository.findAll());
    }

    @PostMapping
    public ResponseEntity<?> addBook(@Valid @RequestBody BookRequest request) {
        if (repository.findByIsbn(request.getIsbn()).isPresent()) {
            Map<String, String> error = new HashMap<>();
            error.put("isbn", "A book with this ISBN already exists.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }

        // Fixed: request.getIsbn() instead of request.getISBN()
        Book book = new Book(request.getTitle(), request.getAuthor(), request.getIsbn(), request.getCategory());
        Book savedBook = repository.save(book);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedBook);
    }

    @PutMapping("/{id}/borrow")
    public ResponseEntity<?> borrowBook(@PathVariable Long id, @RequestParam String memberName) {
        Book book = repository.findById(id)
                .orElseThrow(() -> new BookNotFoundException("Book not found with ID: " + id));

        if (book.isBorrowed()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Book is already issued to " + book.getBorrowedBy());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }

        book.setBorrowed(true);
        book.setBorrowedBy(memberName);
        book.setIssueDate(LocalDate.now());

        return ResponseEntity.ok(repository.save(book));
    }

    @PutMapping("/{id}/return")
    public ResponseEntity<Book> returnBook(@PathVariable Long id) {
        Book book = repository.findById(id)
                .orElseThrow(() -> new BookNotFoundException("Book not found with ID: " + id));

        book.setBorrowed(false);
        book.setBorrowedBy(null);
        book.setIssueDate(null);

        return ResponseEntity.ok(repository.save(book));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new BookNotFoundException("Book not found with ID: " + id);
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Long>> getSummary() {
        long total = repository.count();
        long borrowed = repository.countByBorrowed(true);
        long available = total - borrowed;

        Map<String, Long> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("available", available);
        stats.put("borrowed", borrowed);

        return ResponseEntity.ok(stats);
    }
}