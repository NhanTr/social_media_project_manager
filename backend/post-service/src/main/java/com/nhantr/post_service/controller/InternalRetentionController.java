package com.nhantr.post_service.controller;

import com.nhantr.post_service.storage.AccountMediaRetentionService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalRetentionController {
    private final AccountMediaRetentionService retention;
    public InternalRetentionController(AccountMediaRetentionService retention) { this.retention = retention; }
    @PostMapping("/internal/v1/retention/accounts/{id}")
    public AccountMediaRetentionService.Result cleanup(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean dryRun) {
        return retention.cleanup(id, dryRun);
    }
}
