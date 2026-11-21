package com.nhantr.post_service.controller;

import com.nhantr.post_service.entity.Post;
import com.nhantr.post_service.service.PostService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/posts")
public class InternalPostController {
    private final PostService service;

    public InternalPostController(PostService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public PostService.PostResponse get(@PathVariable Long id) {
        return service.internalGet(id);
    }

    @PostMapping("/{id}/status")
    public PostService.PostResponse status(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return service.changeStatus(id, request.status());
    }

    public record StatusRequest(@NotNull Post.Status status) {}
}
