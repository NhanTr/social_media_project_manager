package com.nhantr.identity_service.service;

import com.nhantr.identity_service.entity.User;
import com.nhantr.identity_service.enums.AuthProvider;
import com.nhantr.identity_service.enums.UserStatus;
import com.nhantr.identity_service.repository.UserRepository;
import com.nhantr.identity_service.security.PhoneLookupHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserSearchTests {
    private UserRepository repository;
    private PhoneLookupHasher hasher;
    private UserService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
        hasher = mock(PhoneLookupHasher.class);
        service = new UserService(repository, hasher);
    }

    @Test
    void textualInputUsesUsernameAndDisplayNamePartialSearch() {
        when(repository.searchByUsernameOrDisplayName("Alice", UserStatus.ACTIVE))
                .thenReturn(List.of(user(1L, "alice")));

        var results = service.search(" Alice ");

        assertThat(results).extracting("username").containsExactly("alice");
        verify(repository).searchByUsernameOrDisplayName("Alice", UserStatus.ACTIVE);
        verify(repository, never()).findByPhoneLookupHashAndStatus("alice", UserStatus.ACTIVE);
    }

    @Test
    void exactPhoneInputUsesHashEqualityLookup() {
        when(hasher.hash("0901234567")).thenReturn("phone-hash");
        when(repository.findByPhoneLookupHashAndStatus("phone-hash", UserStatus.ACTIVE))
                .thenReturn(Optional.of(user(2L, "phone_user")));

        var results = service.search("0901234567");

        assertThat(results).extracting("username").containsExactly("phone_user");
        verify(repository).findByPhoneLookupHashAndStatus("phone-hash", UserStatus.ACTIVE);
        verify(repository, never()).searchByUsernameOrDisplayName("0901234567", UserStatus.ACTIVE);
    }

    @Test
    void partialPhoneInputDoesNotFallBackToRawLikeSearch() {
        service = new UserService(repository, new PhoneLookupHasher());

        assertThat(service.search("090123")).isEmpty();

        verify(repository, never()).searchByUsernameOrDisplayName("090123", UserStatus.ACTIVE);
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(username);
        user.setStatus(UserStatus.ACTIVE);
        user.setAuthProvider(AuthProvider.PHONE);
        return user;
    }
}
