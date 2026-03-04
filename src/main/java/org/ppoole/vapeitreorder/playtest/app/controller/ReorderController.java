package org.ppoole.vapeitreorder.playtest.app.controller;

import org.ppoole.vapeitreorder.playtest.app.domain.ProductoPrioridades;
import org.ppoole.vapeitreorder.playtest.app.service.PlaytestSessionException;
import org.ppoole.vapeitreorder.playtest.app.service.ReorderExecutionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reorder")
public class ReorderController {

    private final ReorderExecutionService reorderExecutionService;

    public ReorderController(ReorderExecutionService reorderExecutionService) {
        this.reorderExecutionService = reorderExecutionService;
    }

    @PostMapping("/session/vaperalia")
    public Map<String, String> loadVaperaliaSession() {
        reorderExecutionService.saveVaperaliaSession();
        return Map.of("message", "Vaperalia session saved.");
    }

    @PostMapping("/session/eciglogistica")
    public Map<String, String> loadEciglogisticaSession() {
        reorderExecutionService.saveEciglogisticaSession();
        return Map.of("message", "Eciglogistica session saved.");
    }

    @PostMapping("/run")
    public List<ProductoPrioridades> runReorder() {
        return reorderExecutionService.generatePrioridades();
    }

    @ExceptionHandler(PlaytestSessionException.class)
    public ResponseEntity<Map<String, String>> handleSessionException(PlaytestSessionException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", exception.getMessage()));
    }
}
