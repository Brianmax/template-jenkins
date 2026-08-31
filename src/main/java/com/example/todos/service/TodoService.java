package com.example.todos.service;

import com.example.todos.dto.TodoCreateRequest;
import com.example.todos.dto.TodoResponse;
import com.example.todos.dto.TodoUpdateRequest;
import com.example.todos.entity.Todo;
import com.example.todos.exception.ResourceNotFoundException;
import com.example.todos.repository.TodoRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TodoService {

    private static final Logger log = LoggerFactory.getLogger(TodoService.class);
    private static final String TODO_NOT_FOUND = "Todo not found with id: ";

    private final TodoRepository todoRepository;

    public TodoService(TodoRepository todoRepository) {
        this.todoRepository = todoRepository;
    }

    public List<TodoResponse> findAll() {
        log.info("operation=todo.listAll");
        return todoRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public TodoResponse findById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Todo id must not be null");
        }
        log.info("operation=todo.findById todoId={}", id);
        Todo todo = todoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(TODO_NOT_FOUND + id));
        return toResponse(todo);
    }

    @Transactional
    public TodoResponse create(TodoCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be null");
        }
        log.info("operation=todo.create title={}", request.title());
        Todo todo = new Todo();
        todo.setTitle(request.title());
        todo.setDescription(request.description());
        Todo saved = todoRepository.save(todo);
        log.info("operation=todo.created todoId={}", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public TodoResponse update(Long id, TodoUpdateRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("Todo id must not be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be null");
        }
        log.info("operation=todo.update todoId={}", id);

        Todo todo = todoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(TODO_NOT_FOUND + id));

        if (request.title() != null) {
            todo.setTitle(request.title());
        }
        if (request.description() != null) {
            todo.setDescription(request.description());
        }
        if (request.completed() != null) {
            todo.setCompleted(request.completed());
        }

        Todo updated = todoRepository.save(todo);
        log.info("operation=todo.updated todoId={}", updated.getId());
        return toResponse(updated);
    }

    @Transactional
    public void delete(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Todo id must not be null");
        }
        log.info("operation=todo.delete todoId={}", id);
        if (!todoRepository.existsById(id)) {
            throw new ResourceNotFoundException(TODO_NOT_FOUND + id);
        }
        todoRepository.deleteById(id);
        log.info("operation=todo.deleted todoId={}", id);
    }

    private TodoResponse toResponse(Todo todo) {
        return new TodoResponse(
                todo.getId(),
                todo.getTitle(),
                todo.getDescription(),
                todo.isCompleted(),
                todo.getCreatedAt(),
                todo.getUpdatedAt()
        );
    }
}
