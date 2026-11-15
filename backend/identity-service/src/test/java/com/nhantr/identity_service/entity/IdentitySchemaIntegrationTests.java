package com.nhantr.identity_service.entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
@SpringBootTest(properties={"spring.jpa.hibernate.ddl-auto=validate","spring.flyway.enabled=false"})
@Testcontainers(disabledWithoutDocker=true)
class IdentitySchemaIntegrationTests {
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("social_media_ai_db").withUsername("postgres").withPassword("test-only-password");
    @DynamicPropertySource static void configure(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",DB::getJdbcUrl);r.add("spring.datasource.username",DB::getUsername);r.add("spring.datasource.password",DB::getPassword);
    }
    @BeforeAll static void migrate() throws Exception {
        Path dir=Path.of("../database/migration");if(!dir.toFile().exists())dir=Path.of("database/migration");
        try(var connection=DriverManager.getConnection(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());var files=Files.list(dir)) {
            for(Path file:files.filter(p -> p.getFileName().toString().matches("V(1|2|3|4|12)__.*\\.sql")).sorted(java.util.Comparator.comparingInt(p -> Integer.parseInt(p.getFileName().toString().split("__")[0].substring(1)))).toList())
                ScriptUtils.executeSqlScript(connection,new FileSystemResource(file));
        }
    }
    @Autowired JdbcTemplate jdbc;
    @Test void tombstoneMayRemoveProviderIdentifiersOnlyWhenInactiveAndDeleted() {
        jdbc.update("INSERT INTO users(id,username,phone_number,auth_provider) VALUES(9001,'retention-schema-test','+84901119999','PHONE')");
        jdbc.update("UPDATE users SET status='INACTIVE',deleted_at=now(),phone_number=NULL,google_id=NULL WHERE id=9001");
        assertThat(jdbc.queryForObject("SELECT phone_number FROM users WHERE id=9001",String.class)).isNull();
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET status='ACTIVE' WHERE id=9001"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void allIdentityEntitiesValidateAgainstFlywaySchema() { /* Context creation validates actual JDBC mappings. */ }
}
