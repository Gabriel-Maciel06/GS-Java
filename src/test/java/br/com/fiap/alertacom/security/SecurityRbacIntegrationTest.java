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

import static org.assertj.core.api.Assertions.assertThat;
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

    // =========================================================================
    // 5. Blindagem contra Vulnerabilidades M1 (Escalada, IDOR e Usuário Inexistente / Inativo)
    // =========================================================================

    @Test
    @DisplayName("F1.1: Cadastro público em POST /api/cidadaos deve forçar ROLE_CIDADAO mesmo se payload enviar ROLE_GESTOR")
    void deveForcarRoleCidadaoQuandoCadastroPublicoEnviarRoleGestor() throws Exception {
        String jsonEscalada = """
            {
                "nome": "Atacante Escalada",
                "email": "atacante.escalada@alertacom.com",
                "telefone": "(11) 98888-0000",
                "bairro": "Centro",
                "senha": "SenhaForte@123",
                "role": "ROLE_GESTOR"
            }
            """;

        mockMvc.perform(post("/api/cidadaos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEscalada))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is("ROLE_CIDADAO")));

        Cidadao salvo = cidadaoRepository.findByEmail("atacante.escalada@alertacom.com").orElseThrow();
        assertThat(salvo.getRole()).isEqualTo("ROLE_CIDADAO");
    }

    @Test
    @DisplayName("F1.2: Requisição com token de usuário inexistente no banco deve retornar 401 Unauthorized")
    void deveRetornar401QuandoTokenPossuirUsuarioInexistenteNoBanco() throws Exception {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        String tokenFantasma = JWT.create()
                .withIssuer("alertacom-api")
                .withSubject("fantasma.nao.existe@alertacom.com")
                .withClaim("role", "ROLE_GESTOR")
                .withExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .sign(algorithm);

        mockMvc.perform(get("/api/ocorrencias/minhas")
                        .header("Authorization", "Bearer " + tokenFantasma))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.title", is("Não Autorizado")));
    }

    @Test
    @DisplayName("F1.2: Requisição com token de usuário inativo (ativo = false) deve retornar 401 Unauthorized")
    void deveRetornar401QuandoTokenPossuirUsuarioInativo() throws Exception {
        Cidadao inativo = cidadaoRepository.save(Cidadao.builder()
                .nome("Munícipe Desativado")
                .email("desativado.rbac@alertacom.com")
                .telefone("(11) 96666-5555")
                .bairro("Brás")
                .senha(passwordEncoder.encode("Senha123@"))
                .role("ROLE_CIDADAO")
                .ativo(false)
                .build());

        String tokenInativo = tokenService.gerarToken(inativo);

        mockMvc.perform(get("/api/ocorrencias/minhas")
                        .header("Authorization", "Bearer " + tokenInativo))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.title", is("Não Autorizado")));
    }

    @Test
    @DisplayName("F1.4: IDOR Cidadão - Munícipe tentando consultar perfil de outro cidadão recebe 403 Forbidden")
    void deveRetornar403QuandoCidadaoTentaConsultarOutroCidadao() throws Exception {
        Cidadao vitima = cidadaoRepository.save(Cidadao.builder()
                .nome("Munícipe Vítima")
                .email("vitima.idor@alertacom.com")
                .telefone("(11) 97777-3333")
                .bairro("Mooca")
                .senha(passwordEncoder.encode("Senha123@"))
                .role("ROLE_CIDADAO")
                .ativo(true)
                .build());

        mockMvc.perform(get("/api/cidadaos/" + vitima.getId())
                        .header("Authorization", "Bearer " + tokenCidadao))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.title", is("Acesso Proibido")));
    }

    @Test
    @DisplayName("F1.4: IDOR Cidadão - Munícipe tentando atualizar dados de outro cidadão recebe 403 Forbidden")
    void deveRetornar403QuandoCidadaoTentaAtualizarOutroCidadao() throws Exception {
        Cidadao vitima = cidadaoRepository.save(Cidadao.builder()
                .nome("Munícipe Vítima 2")
                .email("vitima2.idor@alertacom.com")
                .telefone("(11) 97777-4444")
                .bairro("Mooca")
                .senha(passwordEncoder.encode("Senha123@"))
                .role("ROLE_CIDADAO")
                .ativo(true)
                .build());

        String payload = """
            {
                "nome": "Nome Hackeado",
                "telefone": "(11) 90000-0000",
                "bairro": "Sé"
            }
            """;

        mockMvc.perform(put("/api/cidadaos/" + vitima.getId())
                        .header("Authorization", "Bearer " + tokenCidadao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("F1.4: Gestor pode consultar qualquer cidadão (200 OK)")
    void devePermitirGestorConsultarQualquerCidadao() throws Exception {
        mockMvc.perform(get("/api/cidadaos/" + idCidadao)
                        .header("Authorization", "Bearer " + tokenGestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(idCidadao.intValue())));
    }

    @Test
    @DisplayName("F1.5: IDOR Ocorrência - POST /api/ocorrencias vincula ao cidadão autenticado e ignora cidadaoId do payload")
    void deveVincularOcorrenciaAoCidadaoAutenticadoIgnorandoPayload() throws Exception {
        Long idFicticio = 99999L;
        String payload = String.format("""
            {
                "titulo": "Alagamento na Rua dos Trilhos",
                "descricao": "Água da chuva invadindo comércios e garagens",
                "categoria": "ALAGAMENTO",
                "severidade": "MEDIA",
                "latitude": -23.55052,
                "longitude": -46.63330,
                "endereco": "Rua dos Trilhos, 1200",
                "bairro": "Vila Prudente",
                "cidadaoId": %d
            }
            """, idFicticio);

        mockMvc.perform(post("/api/ocorrencias")
                        .header("Authorization", "Bearer " + tokenCidadao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cidadao.id", is(idCidadao.intValue())))
                .andExpect(jsonPath("$.cidadao.email", is("municipe.rbac@alertacom.com")));
    }

    // =========================================================================
    // 6. Matriz de Segurança do Spring Boot Actuator
    // =========================================================================

    @Test
    @DisplayName("ACTUATOR: GET /actuator/health é público (200 OK) e não expõe detalhes para anônimos")
    void devePermitirAcessoAnonimoAoActuatorHealthSemDetalhes() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    @DisplayName("ACTUATOR: GET /actuator/info é público (200 OK)")
    void devePermitirAcessoAnonimoAoActuatorInfo() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ACTUATOR: GET /actuator/metrics sem token retorna 401 Unauthorized com RFC 7807")
    void deveRetornar401QuandoAnonimoTentaAcessarMetricas() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.title", is("Não Autorizado")));
    }

    @Test
    @DisplayName("ACTUATOR: GET /actuator/metrics com ROLE_CIDADAO retorna 403 Forbidden")
    void deveRetornar403QuandoCidadaoTentaAcessarMetricas() throws Exception {
        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", "Bearer " + tokenCidadao))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.title", is("Acesso Proibido")));
    }

    @Test
    @DisplayName("ACTUATOR: GET /actuator/metrics com ROLE_GESTOR é autorizado (200 OK)")
    void devePermitirGestorAcessarMetricas() throws Exception {
        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", "Bearer " + tokenGestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
    }

    @Test
    @DisplayName("ACTUATOR: GET /actuator (raiz) sem token retorna 401 Unauthorized")
    void deveRetornar401QuandoAnonimoTentaAcessarRaizActuator() throws Exception {
        mockMvc.perform(get("/actuator"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("ACTUATOR: GET /actuator com ROLE_CIDADAO retorna 403 Forbidden")
    void deveRetornar403QuandoCidadaoTentaAcessarRaizActuator() throws Exception {
        mockMvc.perform(get("/actuator")
                        .header("Authorization", "Bearer " + tokenCidadao))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("ACTUATOR: GET /actuator com ROLE_GESTOR é autorizado (200 OK)")
    void devePermitirGestorAcessarRaizActuator() throws Exception {
        mockMvc.perform(get("/actuator")
                        .header("Authorization", "Bearer " + tokenGestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links").exists());
    }

    @Test
    @DisplayName("ACTUATOR: GET /actuator/health com ROLE_GESTOR expõe detalhes dos componentes")
    void deveExibirDetalhesHealthQuandoGestorAutenticado() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("Authorization", "Bearer " + tokenGestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.components").exists())
                .andExpect(jsonPath("$.components.db.status", is("UP")));
    }

    // =========================================================================
    // 7. H2 Console e Cabeçalhos de Segurança fora do perfil Dev (Perfil Test)
    // =========================================================================

    @Test
    @DisplayName("H2 CONSOLE: Fora do perfil dev, acesso ao H2 Console é bloqueado (401)")
    void deveBloquearH2ConsoleForaDoPerfilDevParaAnonimo() throws Exception {
        mockMvc.perform(get("/h2-console"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("HEADERS: Fora do perfil dev (ex: test/prod), cabeçalho X-Frame-Options deve ser DENY")
    void deveConterHeaderFrameOptionsDenyForaDoPerfilDev() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", is("DENY")));
    }
}
