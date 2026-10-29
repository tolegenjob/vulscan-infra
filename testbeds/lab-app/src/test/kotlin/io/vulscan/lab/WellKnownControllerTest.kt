package io.vulscan.lab

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

/** N-1: подтверждение владения target (Core `TargetVerificationService`) должно проходить против lab-app. */
@WebMvcTest(WellKnownController::class)
class WellKnownControllerTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @Test
    fun `reflects the token back as the exact file body`() {
        val result = mvc.perform(get("/.well-known/vulnscan-abc123XYZ.txt")).andReturn()
        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("abc123XYZ")
        assertThat(result.response.contentType).startsWith("text/plain")
    }
}
