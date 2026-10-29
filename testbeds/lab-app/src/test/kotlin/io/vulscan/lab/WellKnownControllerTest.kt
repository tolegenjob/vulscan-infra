package io.vulscan.lab

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

/**
 * N-1: подтверждение владения target (Core `TargetVerificationService`) должно проходить против
 * lab-app. Контроллер не имеет зависимостей — поднимать Spring-контекст/MockMvc не нужно.
 */
class WellKnownControllerTest {
    private val controller = WellKnownController()

    @Test
    fun `reflects the token back as the exact file body`() {
        val response = controller.verify("abc123XYZ")
        assertThat(response.statusCode.value()).isEqualTo(200)
        assertThat(response.body).isEqualTo("abc123XYZ")
        assertThat(response.headers.contentType).isEqualTo(MediaType.TEXT_PLAIN)
    }
}
