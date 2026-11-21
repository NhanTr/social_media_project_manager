package com.nhantr.moderation_service.repository;

import com.nhantr.moderation_service.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
}
