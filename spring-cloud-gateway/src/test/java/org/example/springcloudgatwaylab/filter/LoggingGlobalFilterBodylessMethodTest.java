package org.example.springcloudgatwaylab.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LoggingGlobalFilter - Body-less 메서드 판별")
class LoggingGlobalFilterBodylessMethodTest {

    @Test
    @DisplayName("GET, HEAD, DELETE, OPTIONS, TRACE는 body가 없는 메서드이다")
    void bodylessMethods_shouldNotHaveBody() {
        assertThat(LoggingGlobalFilter.hasBody(HttpMethod.GET)).isFalse();
        assertThat(LoggingGlobalFilter.hasBody(HttpMethod.HEAD)).isFalse();
        assertThat(LoggingGlobalFilter.hasBody(HttpMethod.DELETE)).isFalse();
        assertThat(LoggingGlobalFilter.hasBody(HttpMethod.OPTIONS)).isFalse();
        assertThat(LoggingGlobalFilter.hasBody(HttpMethod.TRACE)).isFalse();
    }

    @Test
    @DisplayName("POST, PUT, PATCH는 body가 있는 메서드이다")
    void bodyMethods_shouldHaveBody() {
        assertThat(LoggingGlobalFilter.hasBody(HttpMethod.POST)).isTrue();
        assertThat(LoggingGlobalFilter.hasBody(HttpMethod.PUT)).isTrue();
        assertThat(LoggingGlobalFilter.hasBody(HttpMethod.PATCH)).isTrue();
    }
}
