package org.ppoole.vapeitreorder.playtest.app.controller;

import org.ppoole.vapeitreorder.playtest.app.domain.ProductoPrioridades;
import org.ppoole.vapeitreorder.playtest.app.service.PlaytestSessionException;
import org.ppoole.vapeitreorder.playtest.app.service.ReorderExecutionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/vaperalia/add-to-carrito")
    public Map<String, Object> addVaperaliaToCarrito(@RequestBody List<ReorderExecutionService.AddToCarritoItemRequest> items) {
        ReorderExecutionService.AddToCarritoBatchResult result = reorderExecutionService.addVaperaliaUrlsToCarrito(items);
        int addedCount = result.addedUrls().size();
        int failedCount = result.failedUrls().size();

        return Map.of(
                "message", "Vaperalia carrito: añadidos=" + addedCount + ", fallidos=" + failedCount,
                "addedUrls", result.addedUrls(),
                "failedUrls", result.failedUrls()
        );
    }

    @PostMapping("/eciglogistica/add-to-carrito")
    public Map<String, Object> addEciglogisticaToCarrito(@RequestBody List<ReorderExecutionService.AddToCarritoItemRequest> items) {
        ReorderExecutionService.AddToCarritoBatchResult result = reorderExecutionService.addEciglogisticaUrlsToCarrito(items);
        int addedCount = result.addedUrls().size();
        int failedCount = result.failedUrls().size();

        return Map.of(
                "message", "Eciglogistica carrito: añadidos=" + addedCount + ", fallidos=" + failedCount,
                "addedUrls", result.addedUrls(),
                "failedUrls", result.failedUrls()
        );
    }

    @ExceptionHandler(PlaytestSessionException.class)
    public ResponseEntity<Map<String, String>> handleSessionException(PlaytestSessionException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", exception.getMessage()));
    }
}
