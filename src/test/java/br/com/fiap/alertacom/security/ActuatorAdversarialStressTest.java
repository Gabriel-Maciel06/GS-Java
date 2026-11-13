package br.com.fiap.alertacom.security;

import br.com.fiap.alertacom.domain.model.Cidadao;
import br.com.fiap.alertacom.domain.repository.CidadaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suíte de Testes Adversários de Estresse para o Milestone M3 (Empirical Challenger).
 * Valida a matriz completa de segurança do Actuator, proteção do H2 Console
 * e headers anti-clickjacking fora do ambiente de desenvolvimento.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Auditoria Adversária M3: Matriz Actuator, H2 Console e Headers")
class ActuatorAdversarialStressTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private CidadaoRepository cidadaoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String tokenCidadao;
    private String tokenGestor;

    @BeforeEach
    void setUp() {
        Cidadao cidadao = Cidadao.builder()
                .nome("Cidadão Auditor")
                .email("auditor.cidadao@alertacom.com")
                .telefone("(11) 91111-2222")
                .bairro("Mooca")
                .senha(passwordEncoder.encode("Senha123@"))
                .role("ROLE_CIDADAO")
                .ativo(true)
                .build();
        Cidadao salvoCidadao = cidadaoRepository.save(cidadao);
        tokenCidadao = tokenService.gerarToken(salvoCidadao);

        Cidadao gestor = Cidadao.builder()
                .nome("Gestor Auditor")
                .email("auditor.gestor@alertacom.com")
                .telefone("(11) 92222-3333")
                .bairro("Consolação")
                .senha(passwordEncoder.encode("Senha123@"))
                .role("ROLE_GESTOR")
                .ativo(true)
                .build();
        Cidadao salvoGestor = cidadaoRepository.save(gestor);
        tokenGestor = tokenService.gerarToken(salvoGestor);
    }

    // =========================================================================
    // 1. Stress de /actuator/health: Provas de Vazamento de Infraestrutura
    // =========================================================================

    @Test
    @DisplayName("STRESS-HEALTH-01: Anônimo em /actuator/health recebe 200 OK sem detalhes de db, diskSpace ou components")
    void stressHealthAnonimoSemVazamentoDeComponentes() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(jsonPath("$.db").doesNotExist())
                .andExpect(jsonPath("$.diskSpace").doesNotExist())
                .andExpect(header().string("X-Frame-Options", is("DENY")));
    }

    @Test
    @DisplayName("STRESS-HEALTH-02: Cidadão em /actuator/health recebe 200 OK sem detalhes internos de infraestrutura")
    void stressHealthCidadaoSemVazamentoDeComponentes() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("Authorization", "Bearer " + tokenCidadao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @DisplayName("STRESS-HEALTH-03: Gestor em /actuator/health recebe 200 OK COM detalhes de componentes (db, diskSpace)")
    void stressHealthGestorComDetalhesCompletos() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("Authorization", "Bearer " + tokenGestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.components").exists())
                .andExpect(jsonPath("$.components.db").exists())
                .andExpect(jsonPath("$.components.db.status", is("UP")));
    }

    @Test
    @DisplayName("STRESS-HEALTH-04: Sub-caminhos de health probe (/actuator/health/liveness e readiness) acessíveis anonimamente")
    void stressHealthProbesAnonimos() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }

    // =========================================================================
    // 2. /actuator/info: Validação Institucional
    // =========================================================================

    @Test
    @DisplayName("STRESS-INFO-01: Anônimo em /actuator/info recebe 200 OK (comporta endpoint ativo)")
    void stressInfoAnonimoSucesso() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", is("DENY")));
    }

    // =========================================================================
    // 3. Matriz de Autorização: /actuator/metrics, /actuator/env e /actuator/beans
    // =========================================================================

    @Test
    @DisplayName("STRESS-METRICS-01: /actuator/metrics - Anônimo 401, Cidadão 403, Gestor 200")
    void stressMetricsMatrizPermissoes() throws Exception {
        // Anônimo -> 401
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.status", is(401)));

        // Cidadão -> 403
        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", "Bearer " + tokenCidadao))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.status", is(403)));

        // Gestor -> 200
        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", "Bearer " + tokenGestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
    }

    @Test
    @DisplayName("STRESS-ENV-01: /actuator/env - Anônimo 401, Cidadão 403, Gestor 200")
    void stressEnvMatrizPermissoes() throws Exception {
        // Anônimo -> 401
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.status", is(401)));

        // Cidadão -> 403
        mockMvc.perform(get("/actuator/env")
                        .header("Authorization", "Bearer " + tokenCidadao))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.status", is(403)));

        // Gestor -> 200
        mockMvc.perform(get("/actuator/env")
                        .header("Authorization", "Bearer " + tokenGestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propertySources").isArray());
    }

    @Test
    @DisplayName("STRESS-BEANS-01: /actuator/beans - Anônimo 401, Cidadão 403, Gestor 200")
    void stressBeansMatrizPermissoes() throws Exception {
        // Anônimo -> 401
        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.status", is(401)));

        // Cidadão -> 403
        mockMvc.perform(get("/actuator/beans")
                        .header("Authorization", "Bearer " + tokenCidadao))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.status", is(403)));

        // Gestor -> 200
        mockMvc.perform(get("/actuator/beans")
                        .header("Authorization", "Bearer " + tokenGestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contexts").exists());
    }

    @Test
    @DisplayName("STRESS-ROOT-01: /actuator raiz - Anônimo 401, Cidadão 403, Gestor 200")
    void stressActuatorRootMatrizPermissoes() throws Exception {
        // Anônimo -> 401
        mockMvc.perform(get("/actuator"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));

        // Cidadão -> 403
        mockMvc.perform(get("/actuator")
                        .header("Authorization", "Bearer " + tokenCidadao))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));

        // Gestor -> 200
        mockMvc.perform(get("/actuator")
                        .header("Authorization", "Bearer " + tokenGestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links").exists());
    }

    // =========================================================================
    // 4. Bloqueio Adversário do H2 Console fora do perfil Dev
    // =========================================================================

    @Test
    @DisplayName("STRESS-H2-01: Fora de dev, acesso a /h2-console e subcaminhos é categoricamente rejeitado para todos os atores")
    void stressH2ConsoleBloqueioGeralForaDeDev() throws Exception {
        // Anônimo -> 401
        mockMvc.perform(get("/h2-console"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/h2-console/"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/h2-console/login.do"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/h2-console/test"))
                .andExpect(status().isUnauthorized());

        // Cidadão -> 403 (denyAll)
        mockMvc.perform(get("/h2-console")
                        .header("Authorization", "Bearer " + tokenCidadao))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/h2-console/login.do")
                        .header("Authorization", "Bearer " + tokenCidadao))
                .andExpect(status().isForbidden());

        // Gestor -> 403 (denyAll - nem gestor pode abrir H2 console em prod/test)
        mockMvc.perform(get("/h2-console")
                        .header("Authorization", "Bearer " + tokenGestor))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/h2-console/test")
                        .header("Authorization", "Bearer " + tokenGestor))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 5. Anti-Clickjacking: X-Frame-Options DENY em múltiplos endpoints
    // =========================================================================

    @Test
    @DisplayName("STRESS-HEADER-01: X-Frame-Options deve ser estritamente DENY em requisições de diferentes rotas fora de dev")
    void stressHeaderFrameOptionsDenyEmDiversasRotas() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().string("X-Frame-Options", is("DENY")));

        mockMvc.perform(get("/actuator/info"))
                .andExpect(header().string("X-Frame-Options", is("DENY")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(header().string("X-Frame-Options", is("DENY")));
    }
}
