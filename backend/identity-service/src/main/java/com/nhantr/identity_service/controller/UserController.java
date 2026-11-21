package com.nhantr.identity_service.controller;

import com.nhantr.identity_service.dto.response.UserResponse;
import com.nhantr.common.security.CurrentUser;
import com.nhantr.identity_service.dto.request.UpdateUserRequest;
import com.nhantr.identity_service.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse getMe(@AuthenticationPrincipal CurrentUser currentUser) {
        return userService.getMe(currentUser.id());
    }

    @PutMapping("/me")
    public UserResponse updateMe(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return userService.updateMe(currentUser.id(), request);
    }
}
