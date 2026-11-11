package br.com.fiap.alertacom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AlertaComApplicationTests {

    @Test
    @DisplayName("Deve carregar o contexto da aplicação com perfil de teste")
    void contextLoads() {
    }
}
