package com.nhantr.identity_service.controller;
import com.nhantr.identity_service.entity.User;
import com.nhantr.identity_service.enums.UserStatus;
import com.nhantr.identity_service.repository.UserRepository;
import com.nhantr.identity_service.service.UserService;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ActiveUserTests {
    @Test void onlyExistingActiveUndeletedUserPassesGate() {
        var users = mock(UserRepository.class);
        var user = new User();
        var controller = new InternalUserController(users, mock(UserService.class));
        when(users.findById(1L)).thenReturn(Optional.of(user));
        user.setStatus(UserStatus.ACTIVE);
        assertThat(controller.active(1L).active()).isTrue();
        user.setStatus(UserStatus.BANNED);
        assertThat(controller.active(1L).active()).isFalse();
        user.setStatus(UserStatus.INACTIVE);
        assertThat(controller.active(1L).active()).isFalse();
        user.setStatus(UserStatus.ACTIVE); user.setDeletedAt(Instant.now());
        assertThat(controller.active(1L).active()).isFalse();
        assertThat(controller.active(99L).active()).isFalse();
    }
}
