package com.antifraud.service.blacklist;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

/** Management of blacklists; {@code type} is {@code cards} or {@code merchants}. */
@RestController
@RequestMapping("/api/v1/blacklist/{type}")
public class BlacklistController {

    private final BlacklistService blacklist;

    public BlacklistController(BlacklistService blacklist) {
        this.blacklist = blacklist;
    }

    @GetMapping
    public Set<String> list(@PathVariable String type) {
        return blacklist.list(resolve(type));
    }

    @PostMapping
    public AddResponse add(@PathVariable String type, @RequestBody AddRequest request) {
        if (request.values() == null || request.values().stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'values' must be a list of non-blank strings");
        }
        return new AddResponse(blacklist.add(resolve(type), request.values()));
    }

    @DeleteMapping("/{value}")
    public ResponseEntity<Void> remove(@PathVariable String type, @PathVariable String value) {
        return blacklist.remove(resolve(type), value)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    private static BlacklistType resolve(String type) {
        return BlacklistType.fromPath(type)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown blacklist: " + type));
    }

    public record AddRequest(List<String> values) {
    }

    /**
     * @param added number of values that were not on the list before
     */
    public record AddResponse(long added) {
    }
}
