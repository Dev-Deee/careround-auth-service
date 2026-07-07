package com.dayo.careround.authservice.config;

import com.dayo.careround.authservice.data.models.AuthUser;
import com.dayo.careround.authservice.data.models.enums.AdminPermission;
import com.dayo.careround.authservice.data.models.enums.UserRole;
import com.dayo.careround.authservice.data.models.enums.UserStatus;
import com.dayo.careround.authservice.data.repositories.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Set;

@Configuration
@RequiredArgsConstructor
public class SeedConfig {

    @Bean
    CommandLineRunner seedSuperAdmin(AuthUserRepository repo, BCryptPasswordEncoder encoder) {
        return args -> {
            if (repo.existsByRole(UserRole.ADMIN)) return;

            AuthUser superAdmin = new AuthUser();
            superAdmin.setEmail(System.getProperty("SEED_ADMIN_EMAIL"));
            superAdmin.setPhoneNumber(System.getProperty("SEED_ADMIN_PHONE"));
            superAdmin.setPasswordHash(encoder.encode(System.getProperty("SEED_ADMIN_PASSWORD")));
            superAdmin.setRole(UserRole.ADMIN);
            superAdmin.setStatus(UserStatus.ACTIVE);
            superAdmin.setPermissions(Set.of(AdminPermission.values()));
            repo.save(superAdmin);
        };
    }
}