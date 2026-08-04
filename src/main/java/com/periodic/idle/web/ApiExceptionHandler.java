package com.periodic.idle.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Обробка бізнес-помилок (недостатньо ресурсів, locked, max level тощо) для ВСІХ
 * `/api/**`-контролерів: повертає 400 з message, який UI показує гравцеві.
 * Винесено з {@code GameController} в окремий {@code @RestControllerAdvice}, щоб
 * розбиття цього контролера на кілька менших (по тіру/концерну) не дублювало
 * однакову обробку винятків у кожному з них.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleRuntime(RuntimeException ex) {
        return Map.of("error", ex.getMessage() == null ? "Помилка" : ex.getMessage());
    }
}
