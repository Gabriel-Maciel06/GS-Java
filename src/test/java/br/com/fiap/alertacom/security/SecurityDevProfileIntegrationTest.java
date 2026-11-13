package br.com.fiap.alertacom.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@DisplayName("Testes de Segurança específicos para o Perfil DEV (H2 Console e FrameOptions SAMEORIGIN)")
class SecurityDevProfileIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("DEV HEADERS: Em perfil dev, cabeçalho X-Frame-Options deve ser SAMEORIGIN")
    void deveConterHeaderFrameOptionsSameOriginNoPerfilDev() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Frame-Options")).isEqualTo("SAMEORIGIN");
    }

    @Test
    @DisplayName("DEV H2 CONSOLE: Em perfil dev, rota /h2-console/ é acessível publicamente (200 OK)")
    void devePermitirAcessoAoH2ConsoleNoPerfilDev() {
        ResponseEntity<String> response = restTemplate.getForEntity("/h2-console/", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Frame-Options")).isEqualTo("SAMEORIGIN");
    }
}
