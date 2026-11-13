package br.com.fiap.alertacom.domain;

import br.com.fiap.alertacom.domain.model.*;
import br.com.fiap.alertacom.domain.repository.CidadaoRepository;
import br.com.fiap.alertacom.domain.repository.OcorrenciaRepository;
import br.com.fiap.alertacom.domain.repository.ProtocoloDefesaCivilRepository;
import br.com.fiap.alertacom.dto.request.*;
import br.com.fiap.alertacom.dto.response.CidadaoResponse;
import br.com.fiap.alertacom.dto.response.OcorrenciaResponse;
import br.com.fiap.alertacom.dto.response.ProtocoloResponse;
import br.com.fiap.alertacom.event.AlertaCriticoEvent;
import br.com.fiap.alertacom.exception.DuplicateResourceException;
import br.com.fiap.alertacom.exception.GlobalExceptionHandler;
import br.com.fiap.alertacom.exception.RegraNegocioException;
import br.com.fiap.alertacom.exception.ResourceNotFoundException;
import br.com.fiap.alertacom.service.CidadaoService;
import br.com.fiap.alertacom.service.OcorrenciaService;
import br.com.fiap.alertacom.service.ProtocoloService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpTimeoutException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.ConnectException;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Adversarial challenger test suite for Milestone 2 (M2) — CitizenTech AlertaCom.
 * Empirically stress-tests:
 * 1. State machine transition rules & full 36-cell matrix (legal vs illegal).
 * 2. Terminal state immutability enforcement for RESOLVIDA and CANCELADA.
 * 3. Strict 1:1 Protocol-to-Occurrence constraint at service and database levels.
 * 4. Protocol reconclusion guard preventing double-resolution.
 * 5. Duplicate email constraint throwing DuplicateResourceException (HTTP 409).
 * 6. AMQP exception resilience (AmqpConnectException, AmqpTimeoutException, null broker).
 * 7. BCrypt password hashing security & lack of plaintext leakage.
 * 8. RFC 7807 ProblemDetail error contract verification.
 */
@SpringBootTest
@ActiveProfiles("test")
class AdversarialMilestone2BusinessRulesTest {

    @Autowired
    private CidadaoService cidadaoService;

    @Autowired
    private OcorrenciaService ocorrenciaService;

    @Autowired
    private ProtocoloService protocoloService;

    @Autowired
    private CidadaoRepository cidadaoRepository;

    @Autowired
    private OcorrenciaRepository ocorrenciaRepository;

    @Autowired
    private ProtocoloDefesaCivilRepository protocoloRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    private Cidadao cidadaoPadrao;

    @BeforeEach
    void setUp() {
        protocoloRepository.deleteAllInBatch();
        ocorrenciaRepository.deleteAllInBatch();
        cidadaoRepository.deleteAllInBatch();
        reset(rabbitTemplate);

        cidadaoPadrao = cidadaoRepository.save(new Cidadao(
                "Cidadão Challenger",
                "challenger.m2@fiap.com.br",
                "(11) 98765-4321",
                "Vila Prudente",
                passwordEncoder.encode("SenhaSegura#123"),
                "ROLE_CIDADAO"
        ));
    }

    private Ocorrencia criarOcorrenciaEmStatus(StatusOcorrencia status) {
        Ocorrencia oc = Ocorrencia.builder()
                .titulo("Incidente de Risco " + status.name())
                .descricao("Descrição técnica com mais de dez caracteres para validação")
                .categoria(CategoriaOcorrencia.ALAGAMENTO)
                .severidade(SeveridadeOcorrencia.MEDIA)
                .status(status)
                .endereco("Rua Teste, 100")
                .bairro("Vila Prudente")
                .cidadao(cidadaoPadrao)
                .dataCriacao(LocalDateTime.now())
                .build();
        return ocorrenciaRepository.save(oc);
    }

    // =========================================================================
    // 1. STATE MACHINE TRANSITIONS: FULL MATRIX STRESS TEST
    // =========================================================================

    @Nested
    @DisplayName("ADV-FSM: Máquina de Estados e Matriz de Transições")
    class StateMachineTransitions {

        @Test
        @DisplayName("ADV-FSM-1: Deve testar exaustivamente a matriz completa 6x6 de transições")
        void testExhaustive36TransitionMatrix() {
            StatusOcorrencia[] todosStatus = StatusOcorrencia.values();

            for (StatusOcorrencia from : todosStatus) {
                for (StatusOcorrencia to : todosStatus) {
                    Ocorrencia oc = criarOcorrenciaEmStatus(from);
                    boolean isLegal = from.canTransitionTo(to);

                    AtualizarStatusRequest request = new AtualizarStatusRequest(to, "Tentativa de transição " + from + " -> " + to);

                    if (isLegal) {
                        OcorrenciaResponse resp = ocorrenciaService.transicionarStatus(oc.getId(), request);
                        assertThat(resp.status())
                                .as("Transição legal de %s para %s deve ser refletida na resposta", from, to)
                                .isEqualTo(to);

                        Ocorrencia noBanco = ocorrenciaRepository.findById(oc.getId()).orElseThrow();
                        assertThat(noBanco.getStatus())
                                .as("Transição legal de %s para %s deve ser persistida", from, to)
                                .isEqualTo(to);
                    } else {
                        assertThatThrownBy(() -> ocorrenciaService.transicionarStatus(oc.getId(), request))
                                .as("Transição ilegal de %s para %s DEVE lançar RegraNegocioException", from, to)
                                .isInstanceOf(RegraNegocioException.class)
                                .hasMessageContaining("Transição de status inválida");

                        Ocorrencia noBanco = ocorrenciaRepository.findById(oc.getId()).orElseThrow();
                        assertThat(noBanco.getStatus())
                                .as("Status original %s deve ser preservado após transição ilegal para %s", from, to)
                                .isEqualTo(from);
                    }
                }
            }
        }

        @Test
        @DisplayName("ADV-FSM-2: Transição com novo status nulo deve lançar RegraNegocioException")
        void testTransitionToNullThrowsRegraNegocioException() {
            Ocorrencia oc = criarOcorrenciaEmStatus(StatusOcorrencia.ABERTA);
            AtualizarStatusRequest request = new AtualizarStatusRequest(null, "Novo status nulo");

            assertThatThrownBy(() -> ocorrenciaService.transicionarStatus(oc.getId(), request))
                    .isInstanceOf(RegraNegocioException.class);
        }

        @Test
        @DisplayName("ADV-FSM-3: Cancelamento de ocorrência deve respeitar regras de transição para CANCELADA")
        void testCancelamentoRulesAcrossAllStatuses() {
            for (StatusOcorrencia status : StatusOcorrencia.values()) {
                Ocorrencia oc = criarOcorrenciaEmStatus(status);
                boolean podeCancelar = status.canTransitionTo(StatusOcorrencia.CANCELADA);

                if (podeCancelar) {
                    ocorrenciaService.cancelar(oc.getId(), "Cancelamento legítimo");
                    Ocorrencia atualizada = ocorrenciaRepository.findById(oc.getId()).orElseThrow();
                    assertThat(atualizada.getStatus()).isEqualTo(StatusOcorrencia.CANCELADA);
                } else {
                    assertThatThrownBy(() -> ocorrenciaService.cancelar(oc.getId(), "Cancelamento ilegal"))
                            .as("Cancelar ocorrência no status %s deve lançar RegraNegocioException", status)
                            .isInstanceOf(RegraNegocioException.class)
                            .hasMessageContaining("não pode ser cancelada");

                    Ocorrencia inalterada = ocorrenciaRepository.findById(oc.getId()).orElseThrow();
                    assertThat(inalterada.getStatus()).isEqualTo(status);
                }
            }
        }

        @Test
        @DisplayName("ADV-FSM-4: Usuário com ROLE_GESTOR NÃO pode burlar a máquina de estados (ex: ABERTA -> RESOLVIDA)")
        void testGestorCannotBypassStateMachineToResolvida() {
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            "gestor@alertacom.com.br",
                            "senha",
                            java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_GESTOR"))
                    )
            );
            try {
                Ocorrencia oc = criarOcorrenciaEmStatus(StatusOcorrencia.ABERTA);
                AtualizarStatusRequest request = new AtualizarStatusRequest(
                        StatusOcorrencia.RESOLVIDA,
                        "Tentativa de bypass pelo Gestor"
                );

                assertThatThrownBy(() -> ocorrenciaService.transicionarStatus(oc.getId(), request))
                        .as("Gestor não pode pular direto de ABERTA para RESOLVIDA sem passar pelas etapas regulamentares")
                        .isInstanceOf(RegraNegocioException.class)
                        .hasMessageContaining("Transição de status inválida");

                Ocorrencia noBanco = ocorrenciaRepository.findById(oc.getId()).orElseThrow();
                assertThat(noBanco.getStatus()).isEqualTo(StatusOcorrencia.ABERTA);
            } finally {
                org.springframework.security.core.context.SecurityContextHolder.clearContext();
            }
        }
    }

    // =========================================================================
    // 2. TERMINAL STATE ENFORCEMENT: RESOLVIDA & CANCELADA
    // =========================================================================

    @Nested
    @DisplayName("ADV-TRM: Imutabilidade Estrita dos Estados Terminais")
    class TerminalStateEnforcement {

        @ParameterizedTest
        @EnumSource(StatusOcorrencia.class)
        @DisplayName("ADV-TRM-1: Ocorrência RESOLVIDA não pode transicionar para NENHUM status")
        void testResolvidaIsTerminalAndImmutable(StatusOcorrencia destino) {
            Ocorrencia oc = criarOcorrenciaEmStatus(StatusOcorrencia.RESOLVIDA);
            AtualizarStatusRequest request = new AtualizarStatusRequest(destino, "Tentativa de alterar ocorrência resolvida");

            assertThatThrownBy(() -> ocorrenciaService.transicionarStatus(oc.getId(), request))
                    .isInstanceOf(RegraNegocioException.class);

            Ocorrencia noBanco = ocorrenciaRepository.findById(oc.getId()).orElseThrow();
            assertThat(noBanco.getStatus()).isEqualTo(StatusOcorrencia.RESOLVIDA);
        }

        @Test
        @DisplayName("ADV-TRM-2: Ocorrência RESOLVIDA não pode ser cancelada")
        void testResolvidaCannotBeCancelled() {
            Ocorrencia oc = criarOcorrenciaEmStatus(StatusOcorrencia.RESOLVIDA);

            assertThatThrownBy(() -> ocorrenciaService.cancelar(oc.getId(), "Tentar cancelar resolvida"))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("não pode ser cancelada");

            Ocorrencia noBanco = ocorrenciaRepository.findById(oc.getId()).orElseThrow();
            assertThat(noBanco.getStatus()).isEqualTo(StatusOcorrencia.RESOLVIDA);
        }

        @ParameterizedTest
        @EnumSource(StatusOcorrencia.class)
        @DisplayName("ADV-TRM-3: Ocorrência CANCELADA não pode transicionar para NENHUM status")
        void testCanceladaIsTerminalAndImmutable(StatusOcorrencia destino) {
            Ocorrencia oc = criarOcorrenciaEmStatus(StatusOcorrencia.CANCELADA);
            AtualizarStatusRequest request = new AtualizarStatusRequest(destino, "Tentativa de alterar ocorrência cancelada");

            assertThatThrownBy(() -> ocorrenciaService.transicionarStatus(oc.getId(), request))
                    .isInstanceOf(RegraNegocioException.class);

            Ocorrencia noBanco = ocorrenciaRepository.findById(oc.getId()).orElseThrow();
            assertThat(noBanco.getStatus()).isEqualTo(StatusOcorrencia.CANCELADA);
        }

        @Test
        @DisplayName("ADV-TRM-4: Ocorrência CANCELADA não pode ser cancelada novamente")
        void testCanceladaCannotBeCancelledAgain() {
            Ocorrencia oc = criarOcorrenciaEmStatus(StatusOcorrencia.CANCELADA);

            assertThatThrownBy(() -> ocorrenciaService.cancelar(oc.getId(), "Tentar re-cancelar"))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("não pode ser cancelada");
        }

        @Test
        @DisplayName("ADV-TRM-5: Predicado isTerminal() deve ser verdadeiro APENAS para RESOLVIDA e CANCELADA")
        void testIsTerminalPredicateExclusivity() {
            assertTrue(StatusOcorrencia.RESOLVIDA.isTerminal());
            assertTrue(StatusOcorrencia.CANCELADA.isTerminal());

            assertFalse(StatusOcorrencia.ABERTA.isTerminal());
            assertFalse(StatusOcorrencia.EM_ANALISE.isTerminal());
            assertFalse(StatusOcorrencia.ENCAMINHADA_DEFESA_CIVIL.isTerminal());
            assertFalse(StatusOcorrencia.EM_ATENDIMENTO.isTerminal());
        }
    }

    // =========================================================================
    // 3. STRICT 1:1 PROTOCOL CONSTRAINT
    // =========================================================================

    @Nested
    @DisplayName("ADV-PRT: Restrição Estrita de Cardinalidade 1:1 no Protocolo")
    class ProtocolConstraints {

        @Test
        @DisplayName("ADV-PRT-1: Emitir protocolo com sucesso transiciona ocorrência para ENCAMINHADA_DEFESA_CIVIL")
        void testEmitirProtocoloPrimeiraVez() {
            Ocorrencia oc = criarOcorrenciaEmStatus(StatusOcorrencia.EM_ANALISE);

            ProtocoloRequest request = new ProtocoloRequest(
                    "Equipe Tática Delta",
                    "Vistoria de campo e contenção preliminar",
                    LocalDateTime.now().plusHours(12),
                    oc.getId()
            );

            ProtocoloResponse response = protocoloService.emitir(request);

            assertThat(response).isNotNull();
            assertThat(response.id()).isNotNull();
            assertThat(response.numeroProtocolo()).matches("^DEF-\\d{4}-\\d{5}$");
            assertThat(response.ocorrenciaId()).isEqualTo(oc.getId());
            assertThat(response.ocorrenciaStatus()).isEqualTo(StatusOcorrencia.ENCAMINHADA_DEFESA_CIVIL.name());

            Ocorrencia noBanco = ocorrenciaRepository.findById(oc.getId()).orElseThrow();
            assertThat(noBanco.getStatus()).isEqualTo(StatusOcorrencia.ENCAMINHADA_DEFESA_CIVIL);
            assertThat(protocoloRepository.count()).isEqualTo(1L);
        }

        @Test
        @DisplayName("ADV-PRT-2: Tentativa de emitir segundo protocolo para a mesma ocorrência DEVE lançar RegraNegocioException")
        void testEmitirProtocoloDuplicadoLancaRegraNegocioException() {
            Ocorrencia oc = criarOcorrenciaEmStatus(StatusOcorrencia.EM_ANALISE);

            ProtocoloRequest request1 = new ProtocoloRequest(
                    "Equipe Alpha",
                    "Primeiro despacho oficial",
                    LocalDateTime.now().plusHours(6),
                    oc.getId()
            );
            protocoloService.emitir(request1);

            ProtocoloRequest request2 = new ProtocoloRequest(
                    "Equipe Beta",
                    "Segundo despacho concorrente indevido",
                    LocalDateTime.now().plusHours(12),
                    oc.getId()
            );

            assertThatThrownBy(() -> protocoloService.emitir(request2))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("já possui protocolo oficial da Defesa Civil");

            assertThat(protocoloRepository.count()).isEqualTo(1L);
        }

        @Test
        @DisplayName("ADV-PRT-3: Violação de unicidade 1:1 no nível de banco de dados deve lançar DataIntegrityViolationException")
        void testViolacaoUnicidadeOcorrenciaIdNoBanco() {
            Ocorrencia oc = criarOcorrenciaEmStatus(StatusOcorrencia.EM_ANALISE);

            ProtocoloDefesaCivil p1 = ProtocoloDefesaCivil.builder()
                    .numeroProtocolo("DEF-2026-90001")
                    .equipeResponsavel("Equipe 1")
                    .ocorrencia(oc)
                    .dataEmissao(LocalDateTime.now())
                    .build();
            protocoloRepository.saveAndFlush(p1);

            ProtocoloDefesaCivil p2 = ProtocoloDefesaCivil.builder()
                    .numeroProtocolo("DEF-2026-90002")
                    .equipeResponsavel("Equipe 2")
                    .ocorrencia(oc)
                    .dataEmissao(LocalDateTime.now())
                    .build();

            assertThatThrownBy(() -> protocoloRepository.saveAndFlush(p2))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("ADV-PRT-4: Emitir protocolo para ocorrência inexistente deve lançar ResourceNotFoundException")
        void testEmitirProtocoloOcorrenciaInexistente() {
            ProtocoloRequest request = new ProtocoloRequest(
                    "Equipe Inexistente",
                    "Parecer",
                    LocalDateTime.now().plusHours(10),
                    999999L
            );

            assertThatThrownBy(() -> protocoloService.emitir(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Ocorrência não encontrada");
        }
    }

    // =========================================================================
    // 4. PROTOCOL RECONCLUSION GUARD (DOUBLE-RESOLUTION DEFENSE)
    // =========================================================================

    @Nested
    @DisplayName("ADV-REC: Salvaguarda contra Re-conclusão de Protocolo")
    class ProtocolReconclusionGuard {

        @Test
        @DisplayName("ADV-REC-1: Concluir protocolo pela primeira vez atualiza status para RESOLVIDA com timestamp")
        void testConcluirProtocoloComSucesso() {
            Ocorrencia oc = criarOcorrenciaEmStatus(StatusOcorrencia.EM_ANALISE);
            ProtocoloResponse emitido = protocoloService.emitir(new ProtocoloRequest(
                    "Equipe Resolução",
                    "Vistoria",
                    LocalDateTime.now().plusHours(2),
                    oc.getId()
            ));

            ConcluirProtocoloRequest conclusao = new ConcluirProtocoloRequest("Obras de contenção e drenagem concluídas com sucesso.");
            ProtocoloResponse concluido = protocoloService.concluir(emitido.id(), conclusao);

            assertThat(concluido.dataConclusao()).isNotNull();
            assertThat(concluido.ocorrenciaStatus()).isEqualTo(StatusOcorrencia.RESOLVIDA.name());

            Ocorrencia ocAtualizada = ocorrenciaRepository.findById(oc.getId()).orElseThrow();
            assertThat(ocAtualizada.getStatus()).isEqualTo(StatusOcorrencia.RESOLVIDA);
        }

        @Test
        @DisplayName("ADV-REC-2: Tentativa de concluir protocolo JÁ concluído DEVE lançar RegraNegocioException")
        void testTentativaReconclusaoLancaRegraNegocioException() {
            Ocorrencia oc = criarOcorrenciaEmStatus(StatusOcorrencia.EM_ANALISE);
            ProtocoloResponse emitido = protocoloService.emitir(new ProtocoloRequest(
                    "Equipe Resolução",
                    "Vistoria",
                    LocalDateTime.now().plusHours(2),
                    oc.getId()
            ));

            ConcluirProtocoloRequest conclusao1 = new ConcluirProtocoloRequest("Primeira conclusão legítima");
            ProtocoloResponse concluido1 = protocoloService.concluir(emitido.id(), conclusao1);
            LocalDateTime dataPrimeiraConclusao = concluido1.dataConclusao();

            ConcluirProtocoloRequest conclusao2 = new ConcluirProtocoloRequest("Segunda conclusão indevida / fraudulenta");

            assertThatThrownBy(() -> protocoloService.concluir(emitido.id(), conclusao2))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("já foi concluído anteriormente");

            // Terceira tentativa também deve ser rechaçada
            assertThatThrownBy(() -> protocoloService.concluir(emitido.id(), conclusao2))
                    .isInstanceOf(RegraNegocioException.class);

            ProtocoloDefesaCivil protocoloNoBanco = protocoloRepository.findById(emitido.id()).orElseThrow();
            assertThat(protocoloNoBanco.getDataConclusao()).isEqualTo(dataPrimeiraConclusao);
            assertThat(protocoloNoBanco.getParecerTecnico()).isEqualTo("Primeira conclusão legítima");
        }

        @Test
        @DisplayName("ADV-REC-3: Concluir protocolo inexistente deve lançar ResourceNotFoundException")
        void testConcluirProtocoloInexistente() {
            ConcluirProtocoloRequest req = new ConcluirProtocoloRequest("Parecer final");

            assertThatThrownBy(() -> protocoloService.concluir(888888L, req))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Protocolo não encontrado");
        }
    }

    // =========================================================================
    // 5. DUPLICATE EMAIL CONSTRAINT (HTTP 409)
    // =========================================================================

    @Nested
    @DisplayName("ADV-EML: Restrição de Unicidade de E-mail (DuplicateResourceException)")
    class DuplicateEmailConstraint {

        @Test
        @DisplayName("ADV-EML-1: Cadastrar cidadão com e-mail já existente DEVE lançar DuplicateResourceException")
        void testDuplicateEmailThrowsDuplicateResourceException() {
            CidadaoRequest request1 = new CidadaoRequest(
                    "Primeiro Munícipe",
                    "duplicado.teste@fiap.com.br",
                    "(11) 91234-5678",
                    "Mooca",
                    "senhaForte#123",
                    "ROLE_CIDADAO"
            );
            CidadaoResponse resp1 = cidadaoService.cadastrar(request1);
            assertThat(resp1.id()).isNotNull();

            CidadaoRequest requestDuplicado = new CidadaoRequest(
                    "Segundo Munícipe Tentando Mesmo Email",
                    "duplicado.teste@fiap.com.br",
                    "(11) 98765-4321",
                    "Ipiranga",
                    "outraSenha#456",
                    "ROLE_CIDADAO"
            );

            assertThatThrownBy(() -> cidadaoService.cadastrar(requestDuplicado))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("E-mail já cadastrado");

            // Verifica que apenas o primeiro munícipe existe no banco
            assertThat(cidadaoRepository.findAll())
                    .filteredOn(c -> c.getEmail().equals("duplicado.teste@fiap.com.br"))
                    .hasSize(1);
        }

        @Test
        @DisplayName("ADV-EML-2: Inserção direta de e-mail duplicado via repositório dispara DataIntegrityViolationException")
        void testDuplicateEmailDatabaseConstraintViolation() {
            Cidadao c1 = new Cidadao("Nome 1", "unique.db@fiap.com.br", "(11) 91111-1111", "Centro", "hash", "ROLE_CIDADAO");
            cidadaoRepository.saveAndFlush(c1);

            Cidadao c2 = new Cidadao("Nome 2", "unique.db@fiap.com.br", "(11) 92222-2222", "Centro", "hash", "ROLE_CIDADAO");

            assertThatThrownBy(() -> cidadaoRepository.saveAndFlush(c2))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // =========================================================================
    // 6. AMQP EXCEPTION RESILIENCE (BROKER DOWN / TIMEOUT / NULL)
    // =========================================================================

    @Nested
    @DisplayName("ADV-MQ: Resiliência de Mensageria e Tolerância a Falhas AMQP")
    class AmqpResilience {

        @Test
        @DisplayName("ADV-MQ-1: Broker offline com AmqpConnectException não deve abortar transação para severidade CRITICA")
        void testAmqpConnectExceptionDoesNotAbortTransaction() {
            doThrow(new AmqpConnectException(new ConnectException("Connection refused to rabbitmq:5672")))
                    .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

            OcorrenciaRequest request = new OcorrenciaRequest(
                    "Rompimento de Talude Crítico",
                    "Deslizamento em massa atingindo moradias na encosta",
                    CategoriaOcorrencia.DESLIZAMENTO,
                    SeveridadeOcorrencia.CRITICA,
                    -23.55052,
                    -46.63330,
                    "Rua Alta da Colina, 900",
                    "Vila Prudente",
                    cidadaoPadrao.getId()
            );

            OcorrenciaResponse response = ocorrenciaService.criar(request);

            assertThat(response).isNotNull();
            assertThat(response.id()).isNotNull();
            assertThat(response.severidade()).isEqualTo(SeveridadeOcorrencia.CRITICA);
            assertThat(response.status()).isEqualTo(StatusOcorrencia.ABERTA);

            // Verifica que a ocorrência foi devidamente persistida e commitada no banco
            Ocorrencia noBanco = ocorrenciaRepository.findById(response.id()).orElseThrow();
            assertThat(noBanco.getTitulo()).isEqualTo(request.titulo());
            assertThat(noBanco.getSeveridade()).isEqualTo(SeveridadeOcorrencia.CRITICA);

            verify(rabbitTemplate, times(1)).convertAndSend(
                    eq("alerta.exchange"),
                    eq("alerta.critico.vila_prudente"),
                    any(AlertaCriticoEvent.class)
            );
        }

        @Test
        @DisplayName("ADV-MQ-2: Timeout no broker com AmqpTimeoutException não deve abortar transação para severidade ALTA")
        void testAmqpTimeoutExceptionDoesNotAbortTransaction() {
            doThrow(new AmqpTimeoutException("Timeout esperando ACK do cluster RabbitMQ"))
                    .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

            OcorrenciaRequest request = new OcorrenciaRequest(
                    "Alagamento de Pista Expressa",
                    "Água subindo rapidamente bloqueando a marginal",
                    CategoriaOcorrencia.ALAGAMENTO,
                    SeveridadeOcorrencia.ALTA,
                    -23.55052,
                    -46.63330,
                    "Marginal Tietê, km 15",
                    "Tatuapé",
                    cidadaoPadrao.getId()
            );

            OcorrenciaResponse response = ocorrenciaService.criar(request);

            assertThat(response).isNotNull();
            assertThat(response.id()).isNotNull();
            assertThat(response.severidade()).isEqualTo(SeveridadeOcorrencia.ALTA);

            Ocorrencia noBanco = ocorrenciaRepository.findById(response.id()).orElseThrow();
            assertThat(noBanco.getId()).isEqualTo(response.id());
        }

        @Test
        @DisplayName("ADV-MQ-3: Exceção genérica AmqpException não aborta transação")
        void testGenericAmqpExceptionDoesNotAbortTransaction() {
            doThrow(new AmqpException("Falha genérica no canal AMQP") {})
                    .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

            OcorrenciaRequest request = new OcorrenciaRequest(
                    "Erosão de Cabeceira de Ponte",
                    "Risco iminente de colapso estrutural",
                    CategoriaOcorrencia.EROSAO_SOLO,
                    SeveridadeOcorrencia.CRITICA,
                    -23.55052,
                    -46.63330,
                    "Ponte do Limão",
                    "Limão",
                    cidadaoPadrao.getId()
            );

            OcorrenciaResponse response = ocorrenciaService.criar(request);
            assertThat(response.id()).isNotNull();
            assertThat(ocorrenciaRepository.existsById(response.id())).isTrue();
        }

        @Test
        @DisplayName("ADV-MQ-4: Serviço com RabbitTemplate nulo opera perfeitamente sem NullPointerException")
        void testNullRabbitTemplateGracefulDegradation() {
            OcorrenciaService serviceSemBroker = new OcorrenciaService(ocorrenciaRepository, cidadaoRepository, null);

            OcorrenciaRequest request = new OcorrenciaRequest(
                    "Queda de Árvore sobre Linha Férrea",
                    "Galho bloqueando trilhos com faíscas elétricas",
                    CategoriaOcorrencia.QUEDA_ARVORE,
                    SeveridadeOcorrencia.CRITICA,
                    -23.55052,
                    -46.63330,
                    "Av dos Estados, 100",
                    "Ipiranga",
                    cidadaoPadrao.getId()
            );

            OcorrenciaResponse response = serviceSemBroker.criar(request);
            assertThat(response).isNotNull();
            assertThat(response.id()).isNotNull();
            assertThat(ocorrenciaRepository.existsById(response.id())).isTrue();
        }

        @Test
        @DisplayName("ADV-MQ-5: Severidades BAIXA e MEDIA NUNCA devem acionar publicação RabbitMQ")
        void testNonCriticalSeveritiesNeverInvokeRabbitTemplate() {
            OcorrenciaRequest reqBaixa = new OcorrenciaRequest(
                    "Tampa de bueiro desnivelada",
                    "Pequeno desnível no asfalto ao redor do bueiro",
                    CategoriaOcorrencia.BUEIRO_OBSTRUIDO,
                    SeveridadeOcorrencia.BAIXA,
                    -23.55052,
                    -46.63330,
                    "Rua das Flores, 50",
                    "Jardins",
                    cidadaoPadrao.getId()
            );

            OcorrenciaRequest reqMedia = new OcorrenciaRequest(
                    "Lixo obstruindo boca de lobo",
                    "Folhas e sacos plásticos acumulados na sarjeta",
                    CategoriaOcorrencia.BUEIRO_OBSTRUIDO,
                    SeveridadeOcorrencia.MEDIA,
                    -23.55052,
                    -46.63330,
                    "Rua Augusta, 200",
                    "Consolação",
                    cidadaoPadrao.getId()
            );

            ocorrenciaService.criar(reqBaixa);
            ocorrenciaService.criar(reqMedia);

            verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
        }

        @Test
        @DisplayName("ADV-MQ-6: Routing key normaliza caracteres acentuados e espaços")
        void testRoutingKeyNormalization() {
            OcorrenciaRequest req = new OcorrenciaRequest(
                    "Alagamento Crítico",
                    "Risco iminente com chuva torrencial no bairro",
                    CategoriaOcorrencia.ALAGAMENTO,
                    SeveridadeOcorrencia.CRITICA,
                    -23.55052,
                    -46.63330,
                    "Praça Central, 1",
                    "Vila São José e Pompéia",
                    cidadaoPadrao.getId()
            );

            ocorrenciaService.criar(req);

            verify(rabbitTemplate, times(1)).convertAndSend(
                    eq("alerta.exchange"),
                    eq("alerta.critico.vila_sao_jose_e_pompeia"),
                    any(AlertaCriticoEvent.class)
            );
        }

        @Test
        @DisplayName("ADV-MQ-7: Rollback no banco de dados NUNCA deve publicar evento no RabbitMQ (@TransactionalEventListener AFTER_COMMIT)")
        void testDatabaseRollbackSuppressesAmqpPublication() {
            org.springframework.transaction.support.TransactionTemplate txTemplate =
                    new org.springframework.transaction.support.TransactionTemplate(transactionManager);

            OcorrenciaRequest request = new OcorrenciaRequest(
                    "Deslizamento Crítico que Sofrerá Rollback",
                    "Descrição detalhada para teste de rollback transacional",
                    CategoriaOcorrencia.DESLIZAMENTO,
                    SeveridadeOcorrencia.CRITICA,
                    -23.55052,
                    -46.63330,
                    "Rua do Rollback, 123",
                    "Vila Prudente",
                    cidadaoPadrao.getId()
            );

            assertThatThrownBy(() -> {
                txTemplate.execute(status -> {
                    ocorrenciaService.criar(request);
                    status.setRollbackOnly();
                    throw new RuntimeException("Simulando falha transacional após criar ocorrência crítica");
                });
            }).isInstanceOf(RuntimeException.class)
              .hasMessageContaining("Simulando falha transacional");

            // Verifica que a ocorrência NÃO foi gravada no banco
            assertThat(ocorrenciaRepository.findAll())
                    .noneMatch(oc -> oc.getTitulo().equals("Deslizamento Crítico que Sofrerá Rollback"));

            // E comprovação empírica máxima: RabbitTemplate NUNCA foi chamado
            verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
        }
    }

    // =========================================================================
    // 7. PASSWORD SECURITY & BCRYPT ENCRYPTION
    // =========================================================================

    @Nested
    @DisplayName("ADV-SEC: Segurança de Senha e Criptografia BCrypt")
    class PasswordSecurity {

        private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

        @Test
        @DisplayName("ADV-SEC-1: Senha NUNCA pode ser armazenada em texto plano no banco de dados")
        void testPasswordNeverStoredPlaintext() {
            String senhaPlana = "MinhaSenhaSuperSecreta@2026";

            CidadaoRequest request = new CidadaoRequest(
                    "Munícipe Seguro",
                    "seguro@fiap.com.br",
                    "(11) 98765-1122",
                    "Mooca",
                    senhaPlana,
                    "ROLE_CIDADAO"
            );

            CidadaoResponse response = cidadaoService.cadastrar(request);

            Cidadao persistido = cidadaoRepository.findById(response.id()).orElseThrow();

            assertThat(persistido.getSenha())
                    .as("Senha armazenada não pode ser igual à senha plana")
                    .isNotEqualTo(senhaPlana);

            assertThat(persistido.getSenha())
                    .as("Senha deve seguir rigorosamente o padrão hash BCrypt ($2a$, $2b$ ou $2y$)")
                    .matches(BCRYPT_PATTERN);

            assertThat(passwordEncoder.matches(senhaPlana, persistido.getSenha()))
                    .as("PasswordEncoder deve validar com sucesso a senha plana contra o hash persistido")
                    .isTrue();

            assertThat(passwordEncoder.matches("SenhaTotalmenteErrada", persistido.getSenha()))
                    .as("PasswordEncoder deve rejeitar senhas incorretas")
                    .isFalse();
        }

        @Test
        @DisplayName("ADV-SEC-2: CidadaoResponse e DTOs não devem expor senha sob nenhuma hipótese")
        void testCidadaoResponseDoesNotLeakPassword() {
            CidadaoRequest request = new CidadaoRequest(
                    "Munícipe Sem Vazamento",
                    "sem.vazamento@fiap.com.br",
                    "(11) 98765-3344",
                    "Tatuapé",
                    "SenhaForte#2026",
                    "ROLE_CIDADAO"
            );

            CidadaoResponse response = cidadaoService.cadastrar(request);

            // Verifica que o record CidadaoResponse não possui campos ou métodos de senha
            assertThat(CidadaoResponse.class.getDeclaredFields())
                    .extracting("name")
                    .doesNotContain("senha", "password", "hash");

            assertThat(response.email()).isEqualTo("sem.vazamento@fiap.com.br");
        }

        @Test
        @DisplayName("ADV-SEC-3: Atualização de cadastro não deve apagar nem corromper o hash da senha")
        void testUpdateProfilePreservesPasswordHash() {
            CidadaoRequest cadastroReq = new CidadaoRequest(
                    "Nome Antigo",
                    "preserva.hash@fiap.com.br",
                    "(11) 98888-0000",
                    "Bairro Antigo",
                    "SenhaOriginal#999",
                    "ROLE_CIDADAO"
            );
            CidadaoResponse cadastrado = cidadaoService.cadastrar(cadastroReq);

            Cidadao antes = cidadaoRepository.findById(cadastrado.id()).orElseThrow();
            String hashOriginal = antes.getSenha();

            AtualizarCidadaoRequest updateReq = new AtualizarCidadaoRequest(
                    "Nome Novo Atualizado",
                    "(11) 99999-1111",
                    "Bairro Novo Atualizado"
            );
            CidadaoResponse atualizado = cidadaoService.atualizar(cadastrado.id(), updateReq);

            assertThat(atualizado.nome()).isEqualTo("Nome Novo Atualizado");

            Cidadao depois = cidadaoRepository.findById(cadastrado.id()).orElseThrow();
            assertThat(depois.getSenha())
                    .as("O hash de senha BCrypt deve ser mantido intacto após atualização de dados cadastrais")
                    .isEqualTo(hashOriginal);

            assertThat(passwordEncoder.matches("SenhaOriginal#999", depois.getSenha())).isTrue();
        }
    }

    // =========================================================================
    // 8. RFC 7807 PROBLEMDETAIL SPECIFICATION COMPLIANCE
    // =========================================================================

    @Nested
    @DisplayName("ADV-RFC: Conformidade com ProblemDetail RFC 7807")
    class ProblemDetailCompliance {

        @Test
        @DisplayName("ADV-RFC-1: RegraNegocioException deve mapear para HTTP 422 Unprocessable Entity com título e tipo padronizados")
        void testRegraNegocioProducesHttp422() {
            RegraNegocioException ex = new RegraNegocioException("Transição inválida de status ou protocolo duplicado");
            ProblemDetail problem = globalExceptionHandler.handleRegraNegocio(ex);

            assertThat(problem).isNotNull();
            assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
            assertThat(problem.getTitle()).isEqualTo("Violação de Regra de Negócio");
            assertThat(problem.getDetail()).isEqualTo(ex.getMessage());
            assertThat(problem.getType().toString()).contains("unprocessable-entity");
            assertThat(problem.getProperties()).containsKey("timestamp");
            assertThat(problem.getProperties().get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("ADV-RFC-2: DuplicateResourceException deve mapear para HTTP 409 Conflict com título e tipo padronizados")
        void testDuplicateResourceProducesHttp409() {
            DuplicateResourceException ex = new DuplicateResourceException("E-mail já cadastrado na plataforma: duplicado@fiap.com.br");
            ProblemDetail problem = globalExceptionHandler.handleDuplicateResource(ex);

            assertThat(problem).isNotNull();
            assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
            assertThat(problem.getTitle()).isEqualTo("Conflito de Recurso");
            assertThat(problem.getDetail()).isEqualTo(ex.getMessage());
            assertThat(problem.getType().toString()).contains("conflict");
            assertThat(problem.getProperties()).containsKey("timestamp");
            assertThat(problem.getProperties().get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("ADV-RFC-3: ResourceNotFoundException deve mapear para HTTP 404 Not Found")
        void testResourceNotFoundProducesHttp404() {
            ResourceNotFoundException ex = new ResourceNotFoundException("Ocorrência não encontrada com o ID: 12345");
            ProblemDetail problem = globalExceptionHandler.handleResourceNotFound(ex);

            assertThat(problem).isNotNull();
            assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(problem.getTitle()).isEqualTo("Recurso Não Encontrado");
            assertThat(problem.getDetail()).isEqualTo(ex.getMessage());
            assertThat(problem.getType().toString()).contains("not-found");
            assertThat(problem.getProperties()).containsKey("timestamp");
        }
    }
}
