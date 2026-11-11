package br.com.fiap.alertacom.security;

import br.com.fiap.alertacom.domain.model.Cidadao;
import br.com.fiap.alertacom.domain.repository.CidadaoRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Testes de Integração RBAC e Filtro de Segurança Spring Security")
class SecurityRbacIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private CidadaoRepository cidadaoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${jwt.secret:${api.security.token.secret:AlertaComSuperChaveSecretaGlobalSolutionFIAP2026}}")
    private String secret;

    private String tokenCidadao;
    private String tokenGestor;
    private Long idCidadao;

    @BeforeEach
    void setUp() {
        Cidadao cidadao = Cidadao.builder()
                .nome("Munícipe Teste")
                .email("municipe.rbac@alertacom.com")
                .telefone("(11) 98888-1111")
                .bairro("Vila Prudente")
                .senha(passwordEncoder.encode("Senha123@"))
                .role("ROLE_CIDADAO")
                .ativo(true)
                .build();
        Cidadao salvoCidadao = cidadaoRepository.save(cidadao);
        idCidadao = salvoCidadao.getId();
        tokenCidadao = tokenService.gerarToken(salvoCidadao);

        Cidadao gestor = Cidadao.builder()
                .nome("Gestor Teste")
                .email("gestor.rbac@alertacom.com")
                .telefone("(11) 99999-2222")
                .bairro("Sé")
                .senha(passwordEncoder.encode("Senha123@"))
                .role("ROLE_GESTOR")
                .ativo(true)
                .build();
        Cidadao salvoGestor = cidadaoRepository.save(gestor);
        tokenGestor = tokenService.gerarToken(salvoGestor);
    }

    // =========================================================================
    // 1. Rotas Públicas (permitAll)
    // =========================================================================

    @Test
    @DisplayName("PÚBLICO: POST /api/auth/login acessível sem autenticação prévia")
    void devePermitirAcessoAnonimoAoLogin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest()) // Rejeitado por validação, NÃO por 401
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("PÚBLICO: POST /api/auth/register e /cadastro acessíveis anonimamente")
    void devePermitirAcessoAnonimoAoCadastro() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PÚBLICO: POST /api/cidadaos acessível anonimamente")
    void devePermitirAcessoAnonimoAoEndpointCidadaos() throws Exception {
        mockMvc.perform(post("/api/cidadaos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PÚBLICO: GET /api/protocolos/numero/{num} acessível anonimamente")
    void devePermitirAcessoAnonimoAoProtocoloPorNumero() throws Exception {
        mockMvc.perform(get("/api/protocolos/numero/DEF-2026-99999"))
                .andExpect(status().isNotFound()) // Não encontrado, NÃO 401
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    @DisplayName("PÚBLICO: GET /api/protocolos/consultar/{num} alias acessível anonimamente")
    void devePermitirAcessoAnonimoAoProtocoloAliasConsultar() throws Exception {
        mockMvc.perform(get("/api/protocolos/consultar/DEF-2026-99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    @DisplayName("PÚBLICO: GET /v3/api-docs acessível anonimamente")
    void devePermitirAcessoAnonimoOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 2. Rotas Protegidas - 401 Unauthorized
    // =========================================================================

    @Test
    @DisplayName("PROTEGIDO: Requisição sem token em rota protegida retorna 401 com ProblemDetail")
    void deveRetornar401QuandoRequisicaoSemToken() throws Exception {
        mockMvc.perform(get("/api/cidadaos"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.title", is("Não Autorizado")));
    }

    @Test
    @DisplayName("PROTEGIDO: Requisição com token expirado retorna 401 Unauthorized")
    void deveRetornar401ComTokenExpirado() throws Exception {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        String tokenExpirado = JWT.create()
                .withIssuer("alertacom-api")
                .withSubject("municipe.rbac@alertacom.com")
                .withExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .sign(algorithm);

        mockMvc.perform(get("/api/ocorrencias/minhas")
                        .header("Authorization", "Bearer " + tokenExpirado))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("PROTEGIDO: Requisição com assinatura adulterada retorna 401 Unauthorized")
    void deveRetornar401ComTokenAdulterado() throws Exception {
        String tokenAdulterado = tokenCidadao + "AssinaturaCorrompida";

        mockMvc.perform(get("/api/ocorrencias/minhas")
                        .header("Authorization", "Bearer " + tokenAdulterado))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    // =========================================================================
    // 3. Controle RBAC - 403 Forbidden (ROLE_CIDADAO em rotas de Gestor)
    // =========================================================================

    @Test
    @DisplayName("RBAC: ROLE_CIDADAO tentando listar todos os munícipes recebe 403 Forbidden")
    void deveRetornar403QuandoCidadaoTentaListarCidadaos() throws Exception {
        mockMvc.perform(get("/api/cidadaos")
                        .header("Authorization", "Bearer " + tokenCidadao))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.title", is("Acesso Proibido")));
    }

    @Test
    @DisplayName("RBAC: ROLE_CIDADAO tentando emitir protocolo oficial recebe 403 Forbidden")
    void deveRetornar403QuandoCidadaoTentaEmitirProtocolo() throws Exception {
        mockMvc.perform(post("/api/protocolos")
                        .header("Authorization", "Bearer " + tokenCidadao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("RBAC: ROLE_CIDADAO tentando excluir munícipe recebe 403 Forbidden")
    void deveRetornar403QuandoCidadaoTentaExcluirCidadao() throws Exception {
        mockMvc.perform(delete("/api/cidadaos/1")
                        .header("Authorization", "Bearer " + tokenCidadao))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("RBAC: ROLE_CIDADAO tentando alterar status de ocorrência recebe 403 Forbidden")
    void deveRetornar403QuandoCidadaoTentaAlterarStatus() throws Exception {
        mockMvc.perform(patch("/api/ocorrencias/1/status")
                        .header("Authorization", "Bearer " + tokenCidadao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    // =========================================================================
    // 4. Acesso Autorizado (200 / 201)
    // =========================================================================

    @Test
    @DisplayName("AUTORIZADO: ROLE_GESTOR acessa GET /api/cidadaos com sucesso (200 OK)")
    void devePermitirGestorListarCidadaos() throws Exception {
        mockMvc.perform(get("/api/cidadaos")
                        .header("Authorization", "Bearer " + tokenGestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("AUTORIZADO: ROLE_CIDADAO acessa GET /api/ocorrencias/minhas com sucesso (200 OK)")
    void devePermitirCidadaoListarMinhasOcorrencias() throws Exception {
        mockMvc.perform(get("/api/ocorrencias/minhas")
                        .header("Authorization", "Bearer " + tokenCidadao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("AUTORIZADO: ROLE_CIDADAO acessa perfil por ID (200 OK)")
    void devePermitirCidadaoAcessarPerfilPorId() throws Exception {
        mockMvc.perform(get("/api/cidadaos/" + idCidadao)
                        .header("Authorization", "Bearer " + tokenCidadao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(idCidadao.intValue())))
                .andExpect(jsonPath("$.email", is("municipe.rbac@alertacom.com")));
    }
}
