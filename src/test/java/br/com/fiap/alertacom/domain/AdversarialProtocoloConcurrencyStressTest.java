package br.com.fiap.alertacom.domain;

import br.com.fiap.alertacom.domain.model.*;
import br.com.fiap.alertacom.domain.repository.CidadaoRepository;
import br.com.fiap.alertacom.domain.repository.OcorrenciaRepository;
import br.com.fiap.alertacom.domain.repository.ProtocoloDefesaCivilRepository;
import br.com.fiap.alertacom.dto.request.ConcluirProtocoloRequest;
import br.com.fiap.alertacom.dto.request.ProtocoloRequest;
import br.com.fiap.alertacom.dto.response.ProtocoloResponse;
import br.com.fiap.alertacom.exception.RegraNegocioException;
import br.com.fiap.alertacom.service.ProtocoloService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Suíte de Testes Adversariais e Estresse de Concorrência do Challenger 1 (Milestone M2).
 * Foco estrito:
 * 1. Concorrência e unicidade atômica da sequence SEQ_PROTOCOLO_NUMERO (livre de colisão).
 * 2. Emissão concorrente massiva via ProtocoloService.emitir() em múltiplas threads.
 * 3. Race condition de dupla emissão para a mesma ocorrência (1:1 constraint).
 * 4. Inviolabilidade absoluta dos status terminais (RESOLVIDA e CANCELADA) contra emissão de protocolo.
 * 5. Fallback gracioso com degradação controlada quando a sequence de banco falha.
 */
@SpringBootTest
@ActiveProfiles("test")
public class AdversarialProtocoloConcurrencyStressTest {

    @Autowired
    private ProtocoloService protocoloService;

    @Autowired
    private ProtocoloDefesaCivilRepository protocoloRepository;

    @Autowired
    private OcorrenciaRepository ocorrenciaRepository;

    @Autowired
    private CidadaoRepository cidadaoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    private Cidadao cidadaoPadrao;

    @BeforeEach
    void setUp() {
        // Garantir que a sequence exista no H2 para os testes de banco
        jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS SEQ_PROTOCOLO_NUMERO START WITH 1 INCREMENT BY 1 NOCACHE");

        protocoloRepository.deleteAllInBatch();
        ocorrenciaRepository.deleteAllInBatch();
        cidadaoRepository.deleteAllInBatch();

        cidadaoPadrao = cidadaoRepository.save(new Cidadao(
                "Munícipe Teste Estresse",
                "municipe.stress@alerta.com",
                "11977778888",
                "Perus",
                passwordEncoder.encode("SenhaForte#2026"),
                "ROLE_CIDADAO"
        ));
    }

    private Ocorrencia criarOcorrencia(StatusOcorrencia status, String titulo) {
        return ocorrenciaRepository.save(Ocorrencia.builder()
                .titulo(titulo)
                .descricao("Descrição técnica com mais de dez caracteres para estresse")
                .categoria(CategoriaOcorrencia.DESLIZAMENTO)
                .severidade(SeveridadeOcorrencia.CRITICA)
                .status(status)
                .endereco("Av. das Encostas, 1000")
                .bairro("Perus")
                .cidadao(cidadaoPadrao)
                .dataCriacao(LocalDateTime.now())
                .build());
    }

    // =========================================================================
    // 1. ESTRESSE DE CONCORRÊNCIA NA SEQUENCE NATIVA
    // =========================================================================

    @Nested
    @DisplayName("1. Concorrência e Unicidade na Sequence SEQ_PROTOCOLO_NUMERO")
    class SequenceConcurrencyStress {

        @Test
        @DisplayName("ADV-SEQ-1: 50 threads concorrentes consumindo SEQ_PROTOCOLO_NUMERO.NEXTVAL devem gerar 50 números únicos sem colisões")
        void testSequenceConcorrenteSemColisao() throws InterruptedException, ExecutionException {
            int threadCount = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            List<Future<Long>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await(); // Disparo sincronizado
                    return protocoloRepository.obterProximoNumeroSequencial();
                }));
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown(); // Dispara todas as 50 threads no mesmo nanossegundo

            Set<Long> numerosGerados = ConcurrentHashMap.newKeySet();
            for (Future<Long> future : futures) {
                Long val = future.get();
                assertThat(val).isNotNull().isPositive();
                numerosGerados.add(val);
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            // Avaliação de Unicidade Absoluta: 50 requisições concorrentes -> exatamente 50 números distintos
            assertEquals(threadCount, numerosGerados.size(),
                    "Deve haver exatamente 50 números únicos gerados pela sequence, sem duplicatas");
        }
    }

    // =========================================================================
    // 2. EMISSÃO MASSIVA DE PROTOCOLOS CONCORRENTES VIA PROTOCOLOSERVICE
    // =========================================================================

    @Nested
    @DisplayName("2. Emissão Massiva Concorrente via ProtocoloService.emitir()")
    class ProtocoloServiceMassiveEmissionStress {

        @Test
        @DisplayName("ADV-EMIT-1: 30 ocorrências distintas recebendo protocolo simultaneamente por 30 threads devem gerar números únicos e transicionar status")
        void testEmissaoSimultaneaMultiplasOcorrencias() throws InterruptedException, ExecutionException {
            int threadCount = 30;
            List<Ocorrencia> ocorrencias = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                ocorrencias.add(criarOcorrencia(StatusOcorrencia.ABERTA, "Ocorrência Concorrente #" + i));
            }

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            List<Future<ProtocoloResponse>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                final Long ocId = ocorrencias.get(i).getId();
                final int index = i;
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    ProtocoloRequest request = new ProtocoloRequest(
                            "Equipe Tática #" + index,
                            "Despacho rápido em teste de estresse",
                            LocalDateTime.now().plusHours(4),
                            ocId
                    );
                    return protocoloService.emitir(request);
                }));
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            Set<String> numerosProtocolo = ConcurrentHashMap.newKeySet();
            int anoAtual = LocalDateTime.now().getYear();

            for (Future<ProtocoloResponse> future : futures) {
                ProtocoloResponse resp = future.get();
                assertNotNull(resp);
                assertNotNull(resp.numeroProtocolo());
                assertTrue(resp.numeroProtocolo().startsWith("DEF-" + anoAtual + "-"),
                        "Protocolo deve seguir o padrão DEF-" + anoAtual + "-XXXXX");
                numerosProtocolo.add(resp.numeroProtocolo());
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            // Todas as 30 emissões devem possuir números distintos
            assertEquals(threadCount, numerosProtocolo.size(),
                    "Todos os 30 protocolos emitidos concorrentemente devem possuir identificadores únicos");

            // Todas as 30 ocorrências devem ter sido transicionadas para ENCAMINHADA_DEFESA_CIVIL
            for (Ocorrencia oc : ocorrencias) {
                Ocorrencia atualizada = ocorrenciaRepository.findById(oc.getId()).orElseThrow();
                assertEquals(StatusOcorrencia.ENCAMINHADA_DEFESA_CIVIL, atualizada.getStatus(),
                        "Ocorrência vinculada deve transicionar para ENCAMINHADA_DEFESA_CIVIL");
            }

            assertEquals(threadCount, protocoloRepository.count(),
                    "Devem existir exatamente 30 protocolos salvos no repositório");
        }
    }

    // =========================================================================
    // 3. RACE CONDITION DE DUPLA EMISSÃO PARA A MESMA OCORRÊNCIA
    // =========================================================================

    @Nested
    @DisplayName("3. Race Condition: Emissões Concorrentes para a MESMA Ocorrência (Garantia 1:1)")
    class DoubleIssuanceRaceConditionStress {

        @Test
        @DisplayName("ADV-RACE-1: 15 threads tentando emitir protocolo simultaneamente para a MESMA ocorrência -> Exatamente 1 deve vencer")
        void testConcorrenciaMesmaOcorrenciaApenasUmVence() throws InterruptedException {
            Ocorrencia oc = criarOcorrencia(StatusOcorrencia.ABERTA, "Ocorrência Alvo de Corrida");
            Long ocorrenciaId = oc.getId();

            int threadCount = 15;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            AtomicInteger sucessos = new AtomicInteger(0);
            AtomicInteger rejeicoes = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();
                        ProtocoloRequest request = new ProtocoloRequest(
                                "Equipe Concorrente " + idx,
                                "Disputa de protocolo",
                                LocalDateTime.now().plusHours(2),
                                ocorrenciaId
                        );
                        protocoloService.emitir(request);
                        sucessos.incrementAndGet();
                    } catch (RegraNegocioException | DataIntegrityViolationException ex) {
                        rejeicoes.incrementAndGet();
                    } catch (Exception ex) {
                        // Qualquer outra exceção inesperada
                        rejeicoes.incrementAndGet();
                    }
                });
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            assertEquals(1, sucessos.get(), "Exatamente 1 emissão deve ser bem-sucedida");
            assertEquals(threadCount - 1, rejeicoes.get(), "As outras 14 threads devem ser rejeitadas");

            assertEquals(1L, protocoloRepository.count(), "Apenas 1 protocolo deve existir no banco de dados");
            Ocorrencia ocFinal = ocorrenciaRepository.findById(ocorrenciaId).orElseThrow();
            assertEquals(StatusOcorrencia.ENCAMINHADA_DEFESA_CIVIL, ocFinal.getStatus());
        }
    }

    // =========================================================================
    // 4. INVIOLABILIDADE DE STATUS TERMINAIS (RESOLVIDA E CANCELADA)
    // =========================================================================

    @Nested
    @DisplayName("4. Inviolabilidade de Status Terminais na Emissão de Protocolo")
    class TerminalStatusInviolabilityStress {

        @Test
        @DisplayName("ADV-TRM-EMIT-1: Tentativa de emitir protocolo para ocorrência RESOLVIDA DEVE lançar RegraNegocioException")
        void testEmitirParaOcorrenciaResolvidaRejeitado() {
            Ocorrencia oc = criarOcorrencia(StatusOcorrencia.RESOLVIDA, "Incidente Anteriormente Resolvido");

            ProtocoloRequest request = new ProtocoloRequest(
                    "Equipe Tática Tardia",
                    "Tentativa espúria de emissão",
                    LocalDateTime.now().plusHours(1),
                    oc.getId()
            );

            assertThatThrownBy(() -> protocoloService.emitir(request))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("status terminal 'RESOLVIDA'");

            assertThat(protocoloRepository.count()).isZero();
        }

        @Test
        @DisplayName("ADV-TRM-EMIT-2: Tentativa de emitir protocolo para ocorrência CANCELADA DEVE lançar RegraNegocioException")
        void testEmitirParaOcorrenciaCanceladaRejeitado() {
            Ocorrencia oc = criarOcorrencia(StatusOcorrencia.CANCELADA, "Incidente Trote Cancelado");

            ProtocoloRequest request = new ProtocoloRequest(
                    "Equipe Tática Desavisada",
                    "Tentativa indevida de emissão para cancelada",
                    LocalDateTime.now().plusHours(1),
                    oc.getId()
            );

            assertThatThrownBy(() -> protocoloService.emitir(request))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("status terminal 'CANCELADA'");

            assertThat(protocoloRepository.count()).isZero();
        }

        @Test
        @DisplayName("ADV-TRM-EMIT-3: 10 threads concorrentes tentando emitir protocolo para ocorrência CANCELADA devem ser 100% rejeitadas")
        void testConcorrenciaEmissaoParaStatusTerminalRejeicaoTotal() throws InterruptedException {
            Ocorrencia oc = criarOcorrencia(StatusOcorrencia.CANCELADA, "Ocorrência Falsa Cancelada");
            Long ocorrenciaId = oc.getId();

            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            AtomicInteger sucessos = new AtomicInteger(0);
            AtomicInteger rejeicoesEsperadas = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();
                        protocoloService.emitir(new ProtocoloRequest(
                                "Equipe Tática",
                                "Parecer",
                                LocalDateTime.now().plusHours(1),
                                ocorrenciaId
                        ));
                        sucessos.incrementAndGet();
                    } catch (RegraNegocioException ex) {
                        if (ex.getMessage().contains("status terminal 'CANCELADA'")) {
                            rejeicoesEsperadas.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            assertEquals(0, sucessos.get(), "Nenhuma thread pode emitir protocolo em status terminal");
            assertEquals(threadCount, rejeicoesEsperadas.get(), "Todas as 10 threads devem receber RegraNegocioException");
            assertThat(protocoloRepository.count()).isZero();
        }
    }

    // =========================================================================
    // 5. RESILIÊNCIA E COMPORTAMENTO DA SEQUENCE E FALLBACK
    // =========================================================================

    @Nested
    @DisplayName("5. Resiliência e Comportamento da Sequence e Fallback")
    class SequenceFallbackGracefulStress {

        @Test
        @DisplayName("ADV-FLB-1: Avaliação de fallback quando a sequence não existe no banco de dados (Hibernate transaction poisoning)")
        void testComportamentoQuandoSequenceInexistenteNoBanco() {
            // Remove temporariamente a sequence para simular indisponibilidade no banco
            jdbcTemplate.execute("DROP SEQUENCE IF EXISTS SEQ_PROTOCOLO_NUMERO");

            Ocorrencia oc1 = criarOcorrencia(StatusOcorrencia.ABERTA, "Ocorrência Fallback");

            ProtocoloRequest request = new ProtocoloRequest(
                    "Equipe Fallback", "Parecer de teste", LocalDateTime.now().plusHours(1), oc1.getId()
            );

            // Observação empírica: Dentro de um método @Transactional com JPA, quando uma native query falha
            // no banco (SQL error), o Hibernate marca a Session como rollback-only. Mesmo com o try-catch em
            // gerarNumeroProtocoloUnico, o Spring TransactionManager lança UnexpectedRollbackException ao tentar commit.
            // Isso demonstra que o fallback gracioso funciona para mocks unitários e retorno nulo, mas falha no banco real.
            assertThatThrownBy(() -> protocoloService.emitir(request))
                    .isInstanceOf(org.springframework.transaction.UnexpectedRollbackException.class)
                    .hasMessageContaining("rollback-only");

            // Restaura sequence para manter o ambiente íntegro
            jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS SEQ_PROTOCOLO_NUMERO START WITH 1 INCREMENT BY 1 NOCACHE");
        }

        @Test
        @DisplayName("ADV-FLB-2: Com a sequence restaurada, emissão volta a operar com 100% de sucesso")
        void testRecuperacaoAposRestauracaoDaSequence() {
            jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS SEQ_PROTOCOLO_NUMERO START WITH 1 INCREMENT BY 1 NOCACHE");

            Ocorrencia oc = criarOcorrencia(StatusOcorrencia.ABERTA, "Ocorrência Pós-Restauração");

            ProtocoloResponse resp = protocoloService.emitir(new ProtocoloRequest(
                    "Equipe Recuperada", "Parecer normal", LocalDateTime.now().plusHours(2), oc.getId()
            ));

            assertNotNull(resp);
            int ano = LocalDateTime.now().getYear();
            assertThat(resp.numeroProtocolo()).matches("^DEF-" + ano + "-\\d{5}$");
            assertEquals(StatusOcorrencia.ENCAMINHADA_DEFESA_CIVIL.name(), resp.ocorrenciaStatus());
        }
    }

    // =========================================================================
    // 6. CICLO DE VIDA COMPLETO: EMISSÃO -> CONCLUIR -> BLOQUEIO
    // =========================================================================

    @Nested
    @DisplayName("6. Ciclo de Vida: Emissão -> Conclusão -> Bloqueio de Re-emissão")
    class FullLifecycleLockStress {

        @Test
        @DisplayName("ADV-LIFE-1: Concluir protocolo avança ocorrência para RESOLVIDA e bloqueia novas emissões")
        void testCicloCompletoEmissaoConclusaoBloqueio() {
            Ocorrencia oc = criarOcorrencia(StatusOcorrencia.ABERTA, "Desabamento Parcial de Muro");

            // 1. Emitir
            ProtocoloResponse emitido = protocoloService.emitir(new ProtocoloRequest(
                    "Equipe Emergencial",
                    "Isolamento da calçada",
                    LocalDateTime.now().plusHours(3),
                    oc.getId()
            ));
            assertEquals(StatusOcorrencia.ENCAMINHADA_DEFESA_CIVIL.name(), emitido.ocorrenciaStatus());

            // 2. Concluir
            ProtocoloResponse concluido = protocoloService.concluir(emitido.id(), new ConcluirProtocoloRequest(
                    "Muro escorado e trânsito liberado"
            ));
            assertEquals(StatusOcorrencia.RESOLVIDA.name(), concluido.ocorrenciaStatus());
            assertNotNull(concluido.dataConclusao());

            // 3. Tentar emitir novo protocolo para a mesma ocorrência (agora RESOLVIDA)
            // DEVE lançar RegraNegocioException tanto pela condição terminal quanto pela condição 1:1
            assertThatThrownBy(() -> protocoloService.emitir(new ProtocoloRequest(
                    "Outra Equipe",
                    "Tentativa inválida após conclusão",
                    LocalDateTime.now().plusHours(5),
                    oc.getId()
            )))
            .isInstanceOf(RegraNegocioException.class);
        }
    }
}
