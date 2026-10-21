package com.nhantr.identity_service.controller;

import com.nhantr.identity_service.dto.response.UserResponse;
import com.nhantr.identity_service.enums.UserStatus;
import com.nhantr.identity_service.repository.UserRepository;
import com.nhantr.identity_service.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/users")
public class InternalUserController {
    private final UserRepository repository;
    private final UserService service;

    public InternalUserController(UserRepository repository, UserService service) {
        this.repository = repository;
        this.service = service;
    }

    @GetMapping("/search")
    public List<UserResponse> search(@RequestParam @Size(min = 1, max = 100) String keyword) {
        return service.search(keyword);
    }

    @PostMapping("/batch")
    public List<UserResponse> batch(@Valid @RequestBody BatchUsersRequest request) {
        return repository.findAllByIdIn(request.ids()).stream().map(UserResponse::from).toList();
    }

    @PostMapping("/{id}/status")
    public UserResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return service.changeStatus(id, request.status());
    }

    public record BatchUsersRequest(@NotEmpty @Size(max = 100) List<@NotNull Long> ids) {}
    public record StatusRequest(@NotNull UserStatus status) {}
}
