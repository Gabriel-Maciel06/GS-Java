package br.com.fiap.alertacom.e2e.tier2;

import br.com.fiap.alertacom.e2e.base.BaseE2ETest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tier 2: Cidadão Boundary & Validation E2E Tests (T2_CID_01 a T2_CID_06).
 */
public class CidadaoBoundaryE2ETest extends BaseE2ETest {

    @Test
    @DisplayName("T2_CID_01: Validação de Formato de E-mail Inválido")
    void testValidacaoEmailInvalido() {
        Map<String, Object> req = Map.of(
                "nome", "Usuario Email Invalido",
                "email", "email-sem-arroba-formato-errado",
                "telefone", "(11) 98765-4321",
                "bairro", "Centro",
                "senha", "Senha123@"
        );

        ResponseEntity<JsonNode> response = postAnonimoJson("/api/cidadaos", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = response.getBody();
        if (body != null && body.has("title")) {
            assertThat(body.get("title").asText()).isNotBlank();
        }
    }

    @Test
    @DisplayName("T2_CID_02: Validação de Telefone Fora do Padrão Brasileiro")
    void testValidacaoTelefoneInvalido() {
        Map<String, Object> req = Map.of(
                "nome", "Usuario Telefone Invalido",
                "email", "telefone.invalido@exemplo.com",
                "telefone", "123",
                "bairro", "Centro",
                "senha", "Senha123@"
        );

        ResponseEntity<JsonNode> response = postAnonimoJson("/api/cidadaos", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("T2_CID_03: Fronteira de Comprimento do Nome (2 caracteres vs 151 caracteres)")
    void testFronteiraComprimentoNome() {
        // Abaixo do mínimo (2 caracteres)
        ResponseEntity<JsonNode> respCurto = postAnonimoJson("/api/cidadaos", Map.of(
                "nome", "Zé",
                "email", "ze@exemplo.com",
                "telefone", "(11) 98765-4321",
                "bairro", "Centro",
                "senha", "Senha123@"
        ));
        assertThat(respCurto.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Acima do máximo (151 caracteres)
        String nomeLongo = "A".repeat(151);
        ResponseEntity<JsonNode> respLongo = postAnonimoJson("/api/cidadaos", Map.of(
                "nome", nomeLongo,
                "email", "longo@exemplo.com",
                "telefone", "(11) 98765-4321",
                "bairro", "Centro",
                "senha", "Senha123@"
        ));
        assertThat(respLongo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("T2_CID_04: Consulta de Cidadão por ID Inexistente")
    void testConsultaCidadaoIdInexistente() {
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor@alertacom.com", "Gestor", "Centro");

        ResponseEntity<JsonNode> response = getJson("/api/cidadaos/999999", tokenGestor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode body = response.getBody();
        if (body != null && body.has("status")) {
            assertThat(body.get("status").asInt()).isEqualTo(404);
        }
    }

    @Test
    @DisplayName("T2_CID_05: Paginação além do Limite de Dados (page=9999)")
    void testPaginacaoAlemLimiteDados() {
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor@alertacom.com", "Gestor", "Centro");

        ResponseEntity<JsonNode> response = getJson("/api/cidadaos?page=9999&size=10", tokenGestor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        JsonNode content = body.get("content");
        assertThat(content).isNotNull();
        assertThat(content.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("T2_CID_06: Paginação com Parâmetros Negativos ou Inválidos (page=-1 ou size=0)")
    void testPaginacaoParametrosInvalidos() {
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor@alertacom.com", "Gestor", "Centro");

        ResponseEntity<JsonNode> response = getJson("/api/cidadaos?page=-1&size=0", tokenGestor);

        assertThat(response.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
