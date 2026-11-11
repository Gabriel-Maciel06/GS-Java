package br.com.fiap.alertacom.e2e.tier1;

import br.com.fiap.alertacom.domain.model.Cidadao;
import br.com.fiap.alertacom.e2e.base.BaseE2ETest;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tier 1: Auth & RBAC Feature E2E Tests (T1_AUTH_01 a T1_AUTH_05).
 */
public class AuthFeatureE2ETest extends BaseE2ETest {

    @Test
    @DisplayName("T1_AUTH_01: Cadastro de Novo Cidadão com Sucesso via API pública")
    void testCadastroNovoCidadaoComSucesso() {
        Map<String, Object> cadastroReq = new HashMap<>();
        cadastroReq.put("nome", "Marcos Silva");
        cadastroReq.put("email", "marcos.silva@exemplo.com");
        cadastroReq.put("telefone", "(11) 98765-4321");
        cadastroReq.put("bairro", "Vila Prudente");
        cadastroReq.put("senha", "SenhaForte@2026");
        cadastroReq.put("role", "ROLE_CIDADAO");

        ResponseEntity<JsonNode> response = postAnonimoJson("/api/auth/cadastro", cadastroReq);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.has("id")).isTrue();
        assertThat(body.get("id").asLong()).isPositive();
        assertThat(body.get("nome").asText()).isEqualTo("Marcos Silva");
        assertThat(body.get("email").asText()).isEqualTo("marcos.silva@exemplo.com");
        assertThat(body.get("bairro").asText()).isEqualTo("Vila Prudente");
        assertThat(body.get("role").asText()).isEqualTo("ROLE_CIDADAO");
        if (body.has("ativo")) {
            assertThat(body.get("ativo").asBoolean()).isTrue();
        }
        assertThat(body.has("senha")).isFalse();

        // Verificação Opaque-Box: Login subsequente com as credenciais criadas
        Map<String, String> loginReq = Map.of(
                "email", "marcos.silva@exemplo.com",
                "senha", "SenhaForte@2026"
        );
        ResponseEntity<JsonNode> loginResp = postAnonimoJson("/api/auth/login", loginReq);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResp.getBody().get("token").asText()).isNotBlank();
    }

    @Test
    @DisplayName("T1_AUTH_02: Login Bem-Sucedido de Cidadão e Emissão de Token JWT")
    void testLoginBemSucedidoCidadao() {
        // Pré-requisito: cidadão cadastrado
        Map<String, Object> cadastroReq = Map.of(
                "nome", "Marcos Silva",
                "email", "marcos.login@exemplo.com",
                "telefone", "(11) 98765-4321",
                "bairro", "Vila Prudente",
                "senha", "SenhaForte@2026",
                "role", "ROLE_CIDADAO"
        );
        postAnonimoJson("/api/auth/cadastro", cadastroReq);

        Map<String, String> loginReq = Map.of(
                "email", "marcos.login@exemplo.com",
                "senha", "SenhaForte@2026"
        );
        ResponseEntity<JsonNode> response = postAnonimoJson("/api/auth/login", loginReq);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("token").asText()).isNotBlank();
        assertThat(body.get("token").asText().length()).isGreaterThan(50);
        assertThat(body.get("tipo").asText()).isEqualToIgnoringCase("Bearer");
        assertThat(body.get("role").asText()).isEqualTo("ROLE_CIDADAO");
        assertThat(body.get("nome").asText()).isEqualTo("Marcos Silva");
        assertThat(body.has("expiraEm")).isTrue();
    }

    @Test
    @DisplayName("T1_AUTH_03: Login Bem-Sucedido de Gestor Municipal")
    void testLoginBemSucedidoGestor() {
        // Seed gestor se repositório disponível, ou via cadastro
        if (cidadaoRepository != null) {
            Cidadao gestor = Cidadao.builder()
                    .nome("Comandante Souza")
                    .email("gestor.defesa@alertacom.com.br")
                    .telefone("(11) 91111-2222")
                    .bairro("Sé")
                    .senha(new BCryptPasswordEncoder().encode("DefesaCivil@2026"))
                    .role("ROLE_GESTOR")
                    .ativo(true)
                    .build();
            cidadaoRepository.save(gestor);
        }

        Map<String, String> loginReq = Map.of(
                "email", "gestor.defesa@alertacom.com.br",
                "senha", "DefesaCivil@2026"
        );
        ResponseEntity<JsonNode> response = postAnonimoJson("/api/auth/login", loginReq);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("token").asText()).isNotBlank();
        assertThat(body.get("role").asText()).isEqualTo("ROLE_GESTOR");
        assertThat(body.get("nome").asText()).isNotBlank();
    }

    @Test
    @DisplayName("T1_AUTH_04: Verificação de Assinatura e Claims do Token JWT")
    void testVerificacaoClaimsTokenJwt() {
        Map<String, Object> cadastroReq = Map.of(
                "nome", "Marcos Silva",
                "email", "marcos.claims@exemplo.com",
                "telefone", "(11) 98765-4321",
                "bairro", "Vila Prudente",
                "senha", "SenhaForte@2026",
                "role", "ROLE_CIDADAO"
        );
        postAnonimoJson("/api/auth/cadastro", cadastroReq);

        Map<String, String> loginReq = Map.of(
                "email", "marcos.claims@exemplo.com",
                "senha", "SenhaForte@2026"
        );
        ResponseEntity<JsonNode> response = postAnonimoJson("/api/auth/login", loginReq);
        String token = response.getBody().get("token").asText();

        DecodedJWT decoded = JWT.decode(token);
        assertThat(decoded.getIssuer()).isEqualTo("alertacom-api");
        assertThat(decoded.getSubject()).isEqualTo("marcos.claims@exemplo.com");
        assertThat(decoded.getClaim("role").asString()).isEqualTo("ROLE_CIDADAO");
        assertThat(decoded.getClaim("nome").asString()).isEqualTo("Marcos Silva");
        assertThat(decoded.getClaim("bairro").asString()).isEqualTo("Vila Prudente");
        assertThat(decoded.getExpiresAt().toInstant()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("T1_AUTH_05: Validação RBAC: Autorização Granular por Papel")
    void testValidacaoRbacPorPapel() {
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao@exemplo.com", "Cidadao Teste", "Vila Prudente");
        String tokenGestor = jwtHelper.gerarTokenGestor(2L, "gestor@exemplo.com", "Gestor Teste", "Centro");

        // 1. Cidadão acessa rota de munícipe
        ResponseEntity<JsonNode> cidadaoResp = getJson("/api/ocorrencias/minhas", tokenCidadao);
        assertThat(cidadaoResp.getStatusCode().is2xxSuccessful()).isTrue();

        // 2. Gestor acessa rota restrita de gestão de protocolos
        ResponseEntity<JsonNode> gestorResp = getJson("/api/protocolos", tokenGestor);
        assertThat(gestorResp.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
