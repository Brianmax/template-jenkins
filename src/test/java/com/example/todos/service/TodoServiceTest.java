package com.example.todos.service;

import com.example.todos.dto.TodoCreateRequest;
import com.example.todos.dto.TodoResponse;
import com.example.todos.dto.TodoUpdateRequest;
import com.example.todos.entity.Todo;
import com.example.todos.exception.ResourceNotFoundException;
import com.example.todos.repository.TodoRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock
    private TodoRepository todoRepository;

    @InjectMocks
    private TodoService todoService;

    // ── findAll ───────────────────────────────────────────────────────────────

    @Test
    void findAll_returnsMappedResponses() {
        when(todoRepository.findAll()).thenReturn(List.of(todo(1L, "Buy milk", null, false)));

        List<TodoResponse> result = todoService.findAll();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).title()).isEqualTo("Buy milk");
        assertThat(result.get(0).completed()).isFalse();
    }

    @Test
    void findAll_emptyRepository_returnsEmptyList() {
        when(todoRepository.findAll()).thenReturn(List.of());

        assertThat(todoService.findAll()).isEmpty();
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_existingId_returnsResponse() {
        when(todoRepository.findById(1L)).thenReturn(Optional.of(todo(1L, "Read book", "Chapter 3", false)));

        TodoResponse result = todoService.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.title()).isEqualTo("Read book");
        assertThat(result.description()).isEqualTo("Chapter 3");
    }

    @Test
    void findById_unknownId_throwsResourceNotFoundException() {
        when(todoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> todoService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void findById_nullId_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> todoService.findById(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_validRequest_savesAndReturnsResponse() {
        Todo saved = todo(1L, "Exercise", "30 min jog", false);
        when(todoRepository.save(any(Todo.class))).thenReturn(saved);

        TodoResponse result = todoService.create(new TodoCreateRequest("Exercise", "30 min jog"));

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.title()).isEqualTo("Exercise");
        verify(todoRepository).save(any(Todo.class));
    }

    @Test
    void create_nullRequest_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> todoService.create(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_titleAndCompleted_updatesCorrectly() {
        Todo existing = todo(1L, "Old title", null, false);
        when(todoRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(todoRepository.save(any(Todo.class))).thenReturn(existing);

        TodoResponse result = todoService.update(1L, new TodoUpdateRequest("New title", null, true));

        assertThat(result.title()).isEqualTo("New title");
        assertThat(result.completed()).isTrue();
    }

    @Test
    void update_unknownId_throwsResourceNotFoundException() {
        when(todoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> todoService.update(99L, new TodoUpdateRequest("x", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_nullId_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> todoService.update(null, new TodoUpdateRequest("x", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_nullRequest_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> todoService.update(1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_existingId_deletesSuccessfully() {
        when(todoRepository.existsById(1L)).thenReturn(true);

        todoService.delete(1L);

        verify(todoRepository).deleteById(1L);
    }

    @Test
    void delete_unknownId_throwsResourceNotFoundException() {
        when(todoRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> todoService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(todoRepository, never()).deleteById(any());
    }

    @Test
    void delete_nullId_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> todoService.delete(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Todo todo(Long id, String title, String description, boolean completed) {
        Todo t = new Todo();
        t.setId(id);
        t.setTitle(title);
        t.setDescription(description);
        t.setCompleted(completed);
        return t;
    }
}
