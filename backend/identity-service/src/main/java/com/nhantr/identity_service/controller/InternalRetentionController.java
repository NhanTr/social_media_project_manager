package com.nhantr.identity_service.controller;

import com.nhantr.identity_service.service.AccountRetentionWorker;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalRetentionController {
    private final AccountRetentionWorker worker;
    public InternalRetentionController(AccountRetentionWorker worker) { this.worker = worker; }
    @PostMapping("/internal/v1/retention/accounts/run")
    public Map<String, Integer> run(@RequestParam(defaultValue = "true") boolean dryRun) { return worker.run(dryRun); }
}
