package com.nhantr.identity_service.controller;

import com.nhantr.common.security.CurrentUser;
import com.nhantr.identity_service.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {
    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping("/delete-request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AccountService.DeletionResponse requestDeletion(@AuthenticationPrincipal CurrentUser user) {
        return service.requestDeletion(user.id());
    }
}
