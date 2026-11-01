package com.HemashreeVI.seatlock;

import com.HemashreeVI.seatlock.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeatlockApplicationTests extends IntegrationTest {

    @Test
    void healthIsUp() {
        assertThat(get("/actuator/health", null).getBody().get("status").asString()).isEqualTo("UP");
    }
}
