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
 * Tier 2: Auth & RBAC Boundary & Security E2E Tests (T2_AUTH_01 a T2_AUTH_06).
 */
public class AuthBoundaryE2ETest extends BaseE2ETest {

    @Test
    @DisplayName("T2_AUTH_01: Login com Senha Incorreta")
    void testLoginSenhaIncorreta() {
        // Cadastra cidadão
        postAnonimoJson("/api/auth/cadastro", Map.of(
                "nome", "Usuario Teste Senha",
                "email", "senha.errada@exemplo.com",
                "telefone", "(11) 98888-7777",
                "bairro", "Centro",
                "senha", "SenhaCorreta@2026",
                "role", "ROLE_CIDADAO"
        ));

        ResponseEntity<JsonNode> response = postAnonimoJson("/api/auth/login", Map.of(
                "email", "senha.errada@exemplo.com",
                "senha", "SenhaTotalmenteErrada"
        ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        JsonNode body = response.getBody();
        if (body != null && body.has("status")) {
            assertThat(body.get("status").asInt()).isEqualTo(401);
            assertThat(body.has("title")).isTrue();
        }
    }

    @Test
    @DisplayName("T2_AUTH_02: Login com E-mail Inexistente na Base de Dados")
    void testLoginEmailInexistente() {
        ResponseEntity<JsonNode> response = postAnonimoJson("/api/auth/login", Map.of(
                "email", "fantasma.naoexiste@alertacom.com",
                "senha", "QualquerCoisa123"
        ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        JsonNode body = response.getBody();
        if (body != null && body.has("status")) {
            assertThat(body.get("status").asInt()).isEqualTo(401);
        }
    }

    @Test
    @DisplayName("T2_AUTH_03: Acesso a Rota Protegida sem Header Authorization")
    void testAcessoRotaProtegidaSemToken() {
        ResponseEntity<JsonNode> response = getJson("/api/cidadaos", null);

        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("T2_AUTH_04: Token JWT Adulterado / Assinatura Corrompida (Tampered Token)")
    void testTokenJwtAdulterado() {
        String tokenValido = jwtHelper.gerarTokenCidadao(1L, "cidadao@exemplo.com", "Cidadao", "Centro");
        String tokenAdulterado = jwtHelper.gerarTokenAdulterado(tokenValido);

        ResponseEntity<JsonNode> response = getJson("/api/ocorrencias/minhas", tokenAdulterado);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("T2_AUTH_05: Rejeição RBAC: Cidadão Tentando Acessar Rota Exclusiva de Gestor")
    void testRejeicaoRbacCidadaoEmRotaGestor() {
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao.invasor@exemplo.com", "Invasor", "Centro");

        Map<String, Object> req = Map.of(
                "ocorrenciaId", 1L,
                "equipeResponsavel", "Equipe Invasora",
                "parecerTecnico", "Tentativa não autorizada",
                "dataPrevisaoAtendimento", "2026-11-15T10:00:00"
        );

        ResponseEntity<JsonNode> response = postJson("/api/protocolos", req, tokenCidadao);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode body = response.getBody();
        if (body != null && body.has("status")) {
            assertThat(body.get("status").asInt()).isEqualTo(403);
            assertThat(body.has("title")).isTrue();
        }
    }

    @Test
    @DisplayName("T2_AUTH_06: Auto-Registro com E-mail Duplicado")
    void testAutoRegistroEmailDuplicado() {
        Map<String, Object> cadastroReq = Map.of(
                "nome", "Primeiro Cadastro",
                "email", "duplicado@exemplo.com",
                "telefone", "(11) 91111-2222",
                "bairro", "Vila Prudente",
                "senha", "Senha123@",
                "role", "ROLE_CIDADAO"
        );
        postAnonimoJson("/api/auth/cadastro", cadastroReq);

        // Segunda tentativa com mesmo e-mail
        ResponseEntity<JsonNode> response = postAnonimoJson("/api/auth/cadastro", cadastroReq);

        assertThat(response.getStatusCode()).isIn(HttpStatus.CONFLICT, HttpStatus.BAD_REQUEST);
        JsonNode body = response.getBody();
        if (body != null && body.has("title")) {
            assertThat(body.get("title").asText()).isNotBlank();
        }
    }
}
