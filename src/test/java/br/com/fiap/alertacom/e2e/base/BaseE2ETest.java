package br.com.fiap.alertacom.e2e.base;

import br.com.fiap.alertacom.domain.repository.CidadaoRepository;
import br.com.fiap.alertacom.domain.repository.OcorrenciaRepository;
import br.com.fiap.alertacom.domain.repository.ProtocoloDefesaCivilRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Classe base abstrata para todos os testes E2E do AlertaCom.
 * Executa com o perfil 'test', servidor Tomcat em porta aleatória e banco H2 em modo Oracle.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseE2ETest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired(required = false)
    protected CidadaoRepository cidadaoRepository;

    @Autowired(required = false)
    protected OcorrenciaRepository ocorrenciaRepository;

    @Autowired(required = false)
    protected ProtocoloDefesaCivilRepository protocoloDefesaCivilRepository;

    @Autowired(required = false)
    protected JwtTestHelper jwtHelper;

    @Autowired(required = false)
    protected SpringAiTestHarness aiHarness;

    /**
     * MockBean do RabbitTemplate para captura determinística de mensagens
     * publicadas na topologia AMQP sem necessitar de daemon externo do RabbitMQ.
     */
    @MockBean
    protected RabbitTemplate rabbitTemplate;

    protected RabbitMqTestHarness rabbitHarness;

    @BeforeEach
    public void setupBaseHarness() {
        if (this.restTemplate != null) {
            this.restTemplate.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
        }
        if (this.jwtHelper == null) {
            this.jwtHelper = new JwtTestHelper();
        }
        if (this.aiHarness == null) {
            this.aiHarness = new SpringAiTestHarness(objectMapper);
        }
        this.rabbitHarness = new RabbitMqTestHarness(rabbitTemplate, objectMapper);
        resetDatabaseState();
    }

    /**
     * Limpa o estado relacional entre testes respeitando a integridade referencial.
     */
    protected void resetDatabaseState() {
        if (protocoloDefesaCivilRepository != null) {
            try {
                protocoloDefesaCivilRepository.deleteAllInBatch();
            } catch (Exception ignored) {
            }
        }
        if (ocorrenciaRepository != null) {
            try {
                ocorrenciaRepository.deleteAllInBatch();
            } catch (Exception ignored) {
            }
        }
        if (cidadaoRepository != null) {
            try {
                cidadaoRepository.deleteAllInBatch();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Monta a URL completa para um endpoint relativo.
     */
    protected String url(String relativePath) {
        if (!relativePath.startsWith("/")) {
            relativePath = "/" + relativePath;
        }
        return "http://localhost:" + port + relativePath;
    }

    // =========================================================================
    // Métodos Utilitários de Requisição HTTP Opaque-Box
    // =========================================================================

    protected <T> ResponseEntity<T> get(String path, String jwtToken, Class<T> responseType) {
        HttpHeaders headers = jwtHelper.criarHeaders(jwtToken);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        return restTemplate.exchange(url(path), HttpMethod.GET, requestEntity, responseType);
    }

    protected <T> ResponseEntity<T> get(String path, String jwtToken, ParameterizedTypeReference<T> responseType) {
        HttpHeaders headers = jwtHelper.criarHeaders(jwtToken);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        return restTemplate.exchange(url(path), HttpMethod.GET, requestEntity, responseType);
    }

    protected <T, R> ResponseEntity<R> post(String path, T body, String jwtToken, Class<R> responseType) {
        HttpHeaders headers = jwtHelper.criarHeaders(jwtToken);
        HttpEntity<T> requestEntity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(url(path), HttpMethod.POST, requestEntity, responseType);
    }

    protected <T, R> ResponseEntity<R> postAnonimo(String path, T body, Class<R> responseType) {
        HttpHeaders headers = jwtHelper.criarHeadersAnonimo();
        HttpEntity<T> requestEntity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(url(path), HttpMethod.POST, requestEntity, responseType);
    }

    protected <T, R> ResponseEntity<R> put(String path, T body, String jwtToken, Class<R> responseType) {
        HttpHeaders headers = jwtHelper.criarHeaders(jwtToken);
        HttpEntity<T> requestEntity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(url(path), HttpMethod.PUT, requestEntity, responseType);
    }

    protected <T, R> ResponseEntity<R> patch(String path, T body, String jwtToken, Class<R> responseType) {
        HttpHeaders headers = jwtHelper.criarHeaders(jwtToken);
        HttpEntity<T> requestEntity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(url(path), HttpMethod.PATCH, requestEntity, responseType);
    }

    protected <R> ResponseEntity<R> delete(String path, String jwtToken, Class<R> responseType) {
        HttpHeaders headers = jwtHelper.criarHeaders(jwtToken);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        return restTemplate.exchange(url(path), HttpMethod.DELETE, requestEntity, responseType);
    }

    // Atalhos JsonNode Opaque-Box
    protected ResponseEntity<JsonNode> getJson(String path, String jwtToken) {
        return get(path, jwtToken, JsonNode.class);
    }

    protected ResponseEntity<JsonNode> postJson(String path, Object body, String jwtToken) {
        return post(path, body, jwtToken, JsonNode.class);
    }

    protected ResponseEntity<JsonNode> postAnonimoJson(String path, Object body) {
        return postAnonimo(path, body, JsonNode.class);
    }

    protected ResponseEntity<JsonNode> putJson(String path, Object body, String jwtToken) {
        return put(path, body, jwtToken, JsonNode.class);
    }

    protected ResponseEntity<JsonNode> patchJson(String path, Object body, String jwtToken) {
        return patch(path, body, jwtToken, JsonNode.class);
    }

    protected ResponseEntity<JsonNode> deleteJson(String path, String jwtToken) {
        return delete(path, jwtToken, JsonNode.class);
    }

    protected JsonNode toJsonNode(Object obj) {
        return objectMapper.valueToTree(obj);
    }
}
