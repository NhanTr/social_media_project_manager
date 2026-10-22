package com.nhantr.identity_service.entity;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import static org.assertj.core.api.Assertions.assertThat;

class UserMappingTests {

    @Test
    void phoneLookupHashUsesTheFixedWidthTypeCreatedByFlyway() throws Exception {
        var field = User.class.getDeclaredField("phoneLookupHash");
        Column mapping = field.getAnnotation(Column.class);
        JdbcTypeCode jdbcType = field.getAnnotation(JdbcTypeCode.class);

        assertThat(mapping.columnDefinition()).isEqualTo("CHAR(64)");
        assertThat(jdbcType).isNotNull();
        assertThat(jdbcType.value()).isEqualTo(SqlTypes.CHAR);
    }
}
