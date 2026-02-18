package org.ppoole.vapeitreorder.controller;

import org.ppoole.vapeitreorder.service.ReorderScheduler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TriggerController {

    private final ReorderScheduler reorderScheduler;

    public TriggerController(ReorderScheduler reorderScheduler) {
        this.reorderScheduler = reorderScheduler;
    }

    @PostMapping("/trigger")
    public ResponseEntity<String> trigger() {
        reorderScheduler.checkAndReorder();
        return ResponseEntity.ok("Triggered");
    }
}
