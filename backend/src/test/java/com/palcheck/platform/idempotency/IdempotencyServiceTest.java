package com.palcheck.platform.idempotency;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.simple.JdbcClient;

class IdempotencyServiceTest {
    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 86399})
    void configurationCannotUndercutThePublishedGuarantee(long seconds) {
        assertThatIllegalArgumentException().isThrownBy(() -> new IdempotencyService(
                mock(JdbcClient.class), Clock.systemUTC(), Duration.ofSeconds(seconds)))
                .withMessageContaining("at least 24 hours");
    }

    @Test
    void theWindowCanBeLengthenedWithoutChangingTheStore() {
        assertThatCode(() -> new IdempotencyService(
                mock(JdbcClient.class), Clock.systemUTC(), Duration.ofHours(48)))
                .doesNotThrowAnyException();
    }
}
