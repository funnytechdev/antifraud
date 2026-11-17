package com.antifraud.service.decision;

import com.antifraud.contracts.Verdict;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Read-only access to decisions: why a particular operation was allowed, reviewed or blocked. */
@RestController
@RequestMapping("/api/v1/decisions")
public class DecisionController {

    private static final int MAX_LIMIT = 500;

    private final DecisionRepository decisions;

    public DecisionController(DecisionRepository decisions) {
        this.decisions = decisions;
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<StoredDecision> byTransaction(@PathVariable UUID transactionId) {
        return ResponseEntity.of(decisions.findById(transactionId));
    }

    @GetMapping
    public List<StoredDecision> latest(@RequestParam(required = false) String cardId,
                                       @RequestParam(required = false) Verdict verdict,
                                       @RequestParam(defaultValue = "50") int limit) {
        return decisions.findLatest(cardId, verdict, Math.clamp(limit, 1, MAX_LIMIT));
    }
}
