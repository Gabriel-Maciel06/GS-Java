package br.com.fiap.alertacom.domain;

import br.com.fiap.alertacom.domain.model.*;
import br.com.fiap.alertacom.domain.repository.CidadaoRepository;
import br.com.fiap.alertacom.domain.repository.OcorrenciaRepository;
import br.com.fiap.alertacom.domain.repository.ProtocoloDefesaCivilRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Suíte de Testes Adversariais do Challenger M1.1 para o Milestone 1.
 * Executa estresse empírico sobre:
 * 1. Máquina de estados (StatusOcorrencia e Ocorrencia.transicionarStatus)
 * 2. Unicidade de chaves naturais (email e numero_protocolo)
 * 3. Cardinalidade estrita 1:1 (ocorrencia_id em TB_PROTOCOLO_DEFESA_CIVIL)
 * 4. Nulidade e limites de tamanho de colunas (estresse de borda)
 * 5. Robustez de enums e queries de repositório
 */
@DataJpaTest
@ActiveProfiles("test")
class AdversarialDomainChallengeTest {

    @Autowired
    private CidadaoRepository cidadaoRepository;

    @Autowired
    private OcorrenciaRepository ocorrenciaRepository;

    @Autowired
    private ProtocoloDefesaCivilRepository protocoloRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Cidadao cidadaoBase;
    private Ocorrencia ocorrenciaBase;

    @BeforeEach
    void setUp() {
        cidadaoBase = new Cidadao(
                "Cidadão Teste Base",
                "cidadao.base@alerta.com",
                "11988881234",
                "Mooca",
                "$2a$10$hashValidoParaTesteSeguro1234567890",
                "ROLE_CIDADAO"
        );
        entityManager.persistAndFlush(cidadaoBase);

        ocorrenciaBase = new Ocorrencia(
                "Ocorrência Base para Testes",
                "Descrição detalhada do incidente observado pelo munícipe no bairro Mooca",
                CategoriaOcorrencia.ALAGAMENTO,
                SeveridadeOcorrencia.ALTA,
                -23.5505,
                -46.6333,
                "Rua dos Trilhos, 100",
                "Mooca",
                cidadaoBase
        );
        entityManager.persistAndFlush(ocorrenciaBase);
    }

    // =========================================================================
    // 1. MÁQUINA DE ESTADOS - REGRAS E TRANSIÇÕES (STATUSOCORRENCIA)
    // =========================================================================

    @Nested
    @DisplayName("Desafio 1: Matriz Completa da Máquina de Estados (StatusOcorrencia)")
    class StateMachineChallenge {

        @Test
        @DisplayName("Deve validar a matriz completa de transições permitidas vs proibidas (36 combinações)")
        void testMatrizCompletaDeTransicoes() {
            // 12 transições legais são permitidas no modelo de domínio de defesa civil e zeladoria
            Set<String> transicoesValidas = Set.of(
                    "ABERTA->EM_ANALISE",
                    "ABERTA->ENCAMINHADA_DEFESA_CIVIL",
                    "ABERTA->CANCELADA",
                    "EM_ANALISE->ENCAMINHADA_DEFESA_CIVIL",
                    "EM_ANALISE->EM_ATENDIMENTO",
                    "EM_ANALISE->RESOLVIDA",
                    "EM_ANALISE->CANCELADA",
                    "ENCAMINHADA_DEFESA_CIVIL->EM_ATENDIMENTO",
                    "ENCAMINHADA_DEFESA_CIVIL->RESOLVIDA",
                    "ENCAMINHADA_DEFESA_CIVIL->CANCELADA",
                    "EM_ATENDIMENTO->RESOLVIDA",
                    "EM_ATENDIMENTO->CANCELADA"
            );

            for (StatusOcorrencia origem : StatusOcorrencia.values()) {
                for (StatusOcorrencia destino : StatusOcorrencia.values()) {
                    String transicao = origem.name() + "->" + destino.name();
                    boolean permitido = origem.canTransitionTo(destino);

                    if (transicoesValidas.contains(transicao)) {
                        assertTrue(permitido, "Transição deveria ser VÁLIDA: " + transicao);
                    } else {
                        assertFalse(permitido, "Transição deveria ser INVÁLIDA: " + transicao);
                    }
                }
            }
        }

        @Test
        @DisplayName("Deve retornar false para canTransitionTo quando novoStatus for null ou auto-transição")
        void testTransicoesNulasEAutoTransicoes() {
            for (StatusOcorrencia status : StatusOcorrencia.values()) {
                assertFalse(status.canTransitionTo(null), "Transição para null deve ser rejeitada para " + status);
                assertFalse(status.canTransitionTo(status), "Auto-transição para si mesmo deve ser rejeitada para " + status);
            }
        }

        @Test
        @DisplayName("Deve garantir que os estados terminais RESOLVIDA e CANCELADA sejam marcados como isTerminal() == true")
        void testEstadosTerminais() {
            assertTrue(StatusOcorrencia.RESOLVIDA.isTerminal(), "RESOLVIDA deve ser terminal");
            assertTrue(StatusOcorrencia.CANCELADA.isTerminal(), "CANCELADA deve ser terminal");

            assertFalse(StatusOcorrencia.ABERTA.isTerminal(), "ABERTA não é terminal");
            assertFalse(StatusOcorrencia.EM_ANALISE.isTerminal(), "EM_ANALISE não é terminal");
            assertFalse(StatusOcorrencia.ENCAMINHADA_DEFESA_CIVIL.isTerminal(), "ENCAMINHADA_DEFESA_CIVIL não é terminal");
            assertFalse(StatusOcorrencia.EM_ATENDIMENTO.isTerminal(), "EM_ATENDIMENTO não é terminal");
        }

        @ParameterizedTest
        @EnumSource(StatusOcorrencia.class)
        @DisplayName("Estado RESOLVIDA é terminal e deve rejeitar transição para QUALQUER outro status")
        void testResolvidaRejeitaTodasTransicoes(StatusOcorrencia destino) {
            Ocorrencia oc = new Ocorrencia();
            oc.setStatus(StatusOcorrencia.RESOLVIDA);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> oc.transicionarStatus(destino),
                    "RESOLVIDA não pode transicionar para " + destino);

            assertThat(ex.getMessage()).contains("Transição de status inválida");
        }

        @ParameterizedTest
        @EnumSource(StatusOcorrencia.class)
        @DisplayName("Estado CANCELADA é terminal e deve rejeitar transição para QUALQUER outro status")
        void testCanceladaRejeitaTodasTransicoes(StatusOcorrencia destino) {
            Ocorrencia oc = new Ocorrencia();
            oc.setStatus(StatusOcorrencia.CANCELADA);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> oc.transicionarStatus(destino),
                    "CANCELADA não pode transicionar para " + destino);

            assertThat(ex.getMessage()).contains("Transição de status inválida");
        }

        @Test
        @DisplayName("Deve rejeitar pulo de etapas inválidas (ex: ABERTA diretamente para RESOLVIDA ou EM_ATENDIMENTO)")
        void testRejeitarSaltoDeEtapas() {
            Ocorrencia oc = new Ocorrencia();
            oc.setStatus(StatusOcorrencia.ABERTA);

            assertThrows(IllegalStateException.class, () -> oc.transicionarStatus(StatusOcorrencia.RESOLVIDA));
            assertThrows(IllegalStateException.class, () -> oc.transicionarStatus(StatusOcorrencia.EM_ATENDIMENTO));
        }

        @Test
        @DisplayName("Deve executar ciclo de vida linear completo com sucesso e atualizar timestamp")
        void testCicloDeVidaLinearCompleto() {
            Ocorrencia oc = new Ocorrencia();
            oc.setStatus(StatusOcorrencia.ABERTA);

            oc.transicionarStatus(StatusOcorrencia.EM_ANALISE);
            assertThat(oc.getStatus()).isEqualTo(StatusOcorrencia.EM_ANALISE);
            assertThat(oc.getDataAtualizacao()).isNotNull();

            oc.transicionarStatus(StatusOcorrencia.ENCAMINHADA_DEFESA_CIVIL);
            assertThat(oc.getStatus()).isEqualTo(StatusOcorrencia.ENCAMINHADA_DEFESA_CIVIL);

            oc.transicionarStatus(StatusOcorrencia.EM_ATENDIMENTO);
            assertThat(oc.getStatus()).isEqualTo(StatusOcorrencia.EM_ATENDIMENTO);

            oc.transicionarStatus(StatusOcorrencia.RESOLVIDA);
            assertThat(oc.getStatus()).isEqualTo(StatusOcorrencia.RESOLVIDA);
            assertTrue(oc.getStatus().isTerminal());
        }

        @Test
        @DisplayName("Deve permitir cancelamento a partir de ABERTA e a partir de EM_ANALISE")
        void testCancelamentosValidos() {
            Ocorrencia oc1 = new Ocorrencia();
            oc1.setStatus(StatusOcorrencia.ABERTA);
            oc1.transicionarStatus(StatusOcorrencia.CANCELADA);
            assertThat(oc1.getStatus()).isEqualTo(StatusOcorrencia.CANCELADA);

            Ocorrencia oc2 = new Ocorrencia();
            oc2.setStatus(StatusOcorrencia.EM_ANALISE);
            oc2.transicionarStatus(StatusOcorrencia.CANCELADA);
            assertThat(oc2.getStatus()).isEqualTo(StatusOcorrencia.CANCELADA);
        }

        @Test
        @DisplayName("Deve permitir cancelamento a partir de ENCAMINHADA_DEFESA_CIVIL e EM_ATENDIMENTO")
        void testPermitirCancelamentoAposDespacho() {
            Ocorrencia oc1 = new Ocorrencia();
            oc1.setStatus(StatusOcorrencia.ENCAMINHADA_DEFESA_CIVIL);
            oc1.transicionarStatus(StatusOcorrencia.CANCELADA);
            assertThat(oc1.getStatus()).isEqualTo(StatusOcorrencia.CANCELADA);

            Ocorrencia oc2 = new Ocorrencia();
            oc2.setStatus(StatusOcorrencia.EM_ATENDIMENTO);
            oc2.transicionarStatus(StatusOcorrencia.CANCELADA);
            assertThat(oc2.getStatus()).isEqualTo(StatusOcorrencia.CANCELADA);
        }

        @Test
        @DisplayName("Deve validar parser StatusOcorrencia.fromString com maiúsculas, minúsculas e espaços")
        void testFromStringRobustez() {
            assertThat(StatusOcorrencia.fromString("  aberta  ")).isEqualTo(StatusOcorrencia.ABERTA);
            assertThat(StatusOcorrencia.fromString("RESOLVIDA")).isEqualTo(StatusOcorrencia.RESOLVIDA);
            assertThat(StatusOcorrencia.fromString("em_analise")).isEqualTo(StatusOcorrencia.EM_ANALISE);
            assertThat(StatusOcorrencia.fromString(null)).isNull();
            assertThat(StatusOcorrencia.fromString("   ")).isNull();

            assertThrows(IllegalArgumentException.class, () -> StatusOcorrencia.fromString("STATUS_INEXISTENTE"));
        }
    }

    // =========================================================================
    // 2. UNICIDADE DE CHAVES E CONSTRAINTS DE BANCO (DATAINTEGRITYVIOLATIONEXCEPTION)
    // =========================================================================

    @Nested
    @DisplayName("Desafio 2: Unicidade de Chaves Naturais e Integridade")
    class DatabaseIntegrityChallenge {

        @Test
        @DisplayName("Deve lançar DataIntegrityViolationException ao salvar Cidadao com e-mail duplicado")
        void testDuplicateEmailThrowsDataIntegrityViolationException() {
            Cidadao duplicado = new Cidadao(
                    "Outro Cidadão",
                    "cidadao.base@alerta.com", // Mesmo email do setup
                    "11977770000",
                    "Tatuapé",
                    "hashSenha",
                    "ROLE_CIDADAO"
            );

            assertThrows(DataIntegrityViolationException.class, () -> cidadaoRepository.saveAndFlush(duplicado));
        }

        @Test
        @DisplayName("Deve lançar DataIntegrityViolationException ao duplicar numeroProtocolo em ProtocoloDefesaCivil")
        void testDuplicateNumeroProtocoloThrowsDataIntegrityViolationException() {
            ProtocoloDefesaCivil p1 = new ProtocoloDefesaCivil(
                    "DEF-2026-CHALLENGE",
                    "Equipe Tática Alfa",
                    "Vistoria 1",
                    null,
                    ocorrenciaBase
            );
            protocoloRepository.saveAndFlush(p1);

            // Criar segunda ocorrência para associar o protocolo com número duplicado
            Ocorrencia oc2 = new Ocorrencia(
                    "Segunda Ocorrência",
                    "Descrição do segundo evento",
                    CategoriaOcorrencia.DESLIZAMENTO,
                    SeveridadeOcorrencia.CRITICA,
                    null, null, "Rua B", "Perus", cidadaoBase
            );
            entityManager.persistAndFlush(oc2);

            ProtocoloDefesaCivil p2Duplicado = new ProtocoloDefesaCivil(
                    "DEF-2026-CHALLENGE", // Mesmo protocolo
                    "Equipe Tática Beta",
                    "Vistoria 2",
                    null,
                    oc2
            );

            assertThrows(DataIntegrityViolationException.class, () -> protocoloRepository.saveAndFlush(p2Duplicado));
        }

        @Test
        @DisplayName("Deve lançar DataIntegrityViolationException ao vincular dois protocolos à mesma ocorrência (UK_PROTOCOLO_OCORRENCIA)")
        void testDuplicateOcorrenciaProtocoloThrowsDataIntegrityViolationException() {
            ProtocoloDefesaCivil p1 = new ProtocoloDefesaCivil(
                    "DEF-2026-00010",
                    "Equipe 1",
                    "Parecer 1",
                    null,
                    ocorrenciaBase
            );
            protocoloRepository.saveAndFlush(p1);

            ProtocoloDefesaCivil p2 = new ProtocoloDefesaCivil(
                    "DEF-2026-00020",
                    "Equipe 2",
                    "Parecer 2",
                    null,
                    ocorrenciaBase // Mesma ocorrência!
            );

            assertThrows(DataIntegrityViolationException.class, () -> protocoloRepository.saveAndFlush(p2));
        }
    }

    // =========================================================================
    // 3. NULIDADE E VALIDAÇÕES DE COLUNA OBRIGATÓRIA
    // =========================================================================

    @Nested
    @DisplayName("Desafio 3: Nulidade de Campos Obrigatórios (NOT NULL)")
    class NullHandlingChallenge {

        @Test
        @DisplayName("Deve rejeitar Cidadao sem nome (nullable = false)")
        void testCidadaoNomeNullThrowsException() {
            Cidadao c = new Cidadao(null, "sem.nome@alerta.com", "119", "Bairro", "hash", "ROLE_CIDADAO");
            assertThrows(DataIntegrityViolationException.class, () -> cidadaoRepository.saveAndFlush(c));
        }

        @Test
        @DisplayName("Deve rejeitar Cidadao sem email (nullable = false)")
        void testCidadaoEmailNullThrowsException() {
            Cidadao c = new Cidadao("Nome", null, "119", "Bairro", "hash", "ROLE_CIDADAO");
            assertThrows(DataIntegrityViolationException.class, () -> cidadaoRepository.saveAndFlush(c));
        }

        @Test
        @DisplayName("Deve rejeitar Cidadao sem senha (nullable = false)")
        void testCidadaoSenhaNullThrowsException() {
            Cidadao c = new Cidadao("Nome", "sem.senha@alerta.com", "119", "Bairro", null, "ROLE_CIDADAO");
            assertThrows(DataIntegrityViolationException.class, () -> cidadaoRepository.saveAndFlush(c));
        }

        @Test
        @DisplayName("Deve rejeitar Ocorrencia sem titulo (nullable = false)")
        void testOcorrenciaTituloNullThrowsException() {
            Ocorrencia oc = new Ocorrencia(null, "Descricao", CategoriaOcorrencia.ALAGAMENTO,
                    SeveridadeOcorrencia.ALTA, null, null, "Endereco", "Bairro", cidadaoBase);
            assertThrows(DataIntegrityViolationException.class, () -> ocorrenciaRepository.saveAndFlush(oc));
        }

        @Test
        @DisplayName("Deve rejeitar Ocorrencia sem endereco (nullable = false)")
        void testOcorrenciaEnderecoNullThrowsException() {
            Ocorrencia oc = new Ocorrencia("Titulo", "Descricao", CategoriaOcorrencia.ALAGAMENTO,
                    SeveridadeOcorrencia.ALTA, null, null, null, "Bairro", cidadaoBase);
            assertThrows(DataIntegrityViolationException.class, () -> ocorrenciaRepository.saveAndFlush(oc));
        }

        @Test
        @DisplayName("Deve rejeitar Ocorrencia sem cidadão associado (nullable = false)")
        void testOcorrenciaCidadaoNullThrowsException() {
            Ocorrencia oc = new Ocorrencia("Titulo", "Descricao", CategoriaOcorrencia.ALAGAMENTO,
                    SeveridadeOcorrencia.ALTA, null, null, "Endereco", "Bairro", null);
            assertThrows(DataIntegrityViolationException.class, () -> ocorrenciaRepository.saveAndFlush(oc));
        }

        @Test
        @DisplayName("Deve rejeitar Protocolo sem equipeResponsavel (nullable = false)")
        void testProtocoloEquipeNullThrowsException() {
            ProtocoloDefesaCivil p = new ProtocoloDefesaCivil("DEF-2026-99999", null, "Parecer", null, ocorrenciaBase);
            assertThrows(DataIntegrityViolationException.class, () -> protocoloRepository.saveAndFlush(p));
        }

        @Test
        @DisplayName("Deve rejeitar Protocolo sem ocorrencia vinculada (nullable = false)")
        void testProtocoloOcorrenciaNullThrowsException() {
            ProtocoloDefesaCivil p = new ProtocoloDefesaCivil("DEF-2026-99998", "Equipe Alfa", "Parecer", null, null);
            assertThrows(DataIntegrityViolationException.class, () -> protocoloRepository.saveAndFlush(p));
        }
    }

    // =========================================================================
    // 4. LIMITES DE TAMANHO DE COLUNA (BOUNDARY CONDITIONS)
    // =========================================================================

    @Nested
    @DisplayName("Desafio 4: Limites de Tamanho de Colunas (Boundary Conditions)")
    class ColumnBoundaryChallenge {

        @Test
        @DisplayName("Cidadao.nome: 150 caracteres deve persistir com sucesso, 151 deve lançar DataIntegrityViolationException")
        void testCidadaoNomeBoundary() {
            String nome150 = "A".repeat(150);
            Cidadao c150 = new Cidadao(nome150, "nome150@alerta.com", "119", "Bairro", "hash", "ROLE_CIDADAO");
            Cidadao salvo = cidadaoRepository.saveAndFlush(c150);
            assertThat(salvo.getId()).isNotNull();

            String nome151 = "A".repeat(151);
            Cidadao c151 = new Cidadao(nome151, "nome151@alerta.com", "119", "Bairro", "hash", "ROLE_CIDADAO");
            assertThrows(DataIntegrityViolationException.class, () -> cidadaoRepository.saveAndFlush(c151));
        }

        @Test
        @DisplayName("Ocorrencia.titulo: 120 caracteres deve persistir, 121 deve lançar DataIntegrityViolationException")
        void testOcorrenciaTituloBoundary() {
            String titulo120 = "T".repeat(120);
            Ocorrencia oc120 = new Ocorrencia(titulo120, "Desc", CategoriaOcorrencia.ALAGAMENTO,
                    SeveridadeOcorrencia.ALTA, null, null, "Endereco", "Bairro", cidadaoBase);
            Ocorrencia salva = ocorrenciaRepository.saveAndFlush(oc120);
            assertThat(salva.getId()).isNotNull();

            String titulo121 = "T".repeat(121);
            Ocorrencia oc121 = new Ocorrencia(titulo121, "Desc", CategoriaOcorrencia.ALAGAMENTO,
                    SeveridadeOcorrencia.ALTA, null, null, "Endereco", "Bairro", cidadaoBase);
            assertThrows(DataIntegrityViolationException.class, () -> ocorrenciaRepository.saveAndFlush(oc121));
        }

        @Test
        @DisplayName("Protocolo.numeroProtocolo: 30 caracteres deve persistir, 31 deve lançar DataIntegrityViolationException")
        void testProtocoloNumeroBoundary() {
            // Criar ocorrência 1
            Ocorrencia oc1 = new Ocorrencia("Oc 1", "Desc", CategoriaOcorrencia.OUTROS, SeveridadeOcorrencia.BAIXA, null, null, "End", "Bairro", cidadaoBase);
            entityManager.persistAndFlush(oc1);

            String num30 = "P".repeat(30);
            ProtocoloDefesaCivil p30 = new ProtocoloDefesaCivil(num30, "Equipe", "Parecer", null, oc1);
            ProtocoloDefesaCivil salvo = protocoloRepository.saveAndFlush(p30);
            assertThat(salvo.getId()).isNotNull();

            // Criar ocorrência 2
            Ocorrencia oc2 = new Ocorrencia("Oc 2", "Desc", CategoriaOcorrencia.OUTROS, SeveridadeOcorrencia.BAIXA, null, null, "End", "Bairro", cidadaoBase);
            entityManager.persistAndFlush(oc2);

            String num31 = "P".repeat(31);
            ProtocoloDefesaCivil p31 = new ProtocoloDefesaCivil(num31, "Equipe", "Parecer", null, oc2);
            assertThrows(DataIntegrityViolationException.class, () -> protocoloRepository.saveAndFlush(p31));
        }
    }

    // =========================================================================
    // 5. ENUMS, REGRAS DE SEVERIDADE E MENSAGERIA
    // =========================================================================

    @Nested
    @DisplayName("Desafio 5: Regras de Severidade e Publicação RabbitMQ")
    class SeveridadeChallenge {

        @Test
        @DisplayName("Severidade ALTA e CRITICA devem acionar publicação RabbitMQ (isCriticaOuAlta == true)")
        void testSeveridadeCriticaOuAlta() {
            assertTrue(SeveridadeOcorrencia.ALTA.isCriticaOuAlta());
            assertTrue(SeveridadeOcorrencia.CRITICA.isCriticaOuAlta());

            assertFalse(SeveridadeOcorrencia.BAIXA.isCriticaOuAlta());
            assertFalse(SeveridadeOcorrencia.MEDIA.isCriticaOuAlta());
        }

        @Test
        @DisplayName("Ocorrencia.isCriticaOuAlta deve ser null-safe caso severidade seja nula")
        void testOcorrenciaIsCriticaOuAltaNullSafe() {
            Ocorrencia oc = new Ocorrencia();
            oc.setSeveridade(null);
            assertFalse(oc.isCriticaOuAlta(), "Deve retornar false sem lançar NullPointerException");

            oc.setSeveridade(SeveridadeOcorrencia.CRITICA);
            assertTrue(oc.isCriticaOuAlta());
        }

        @Test
        @DisplayName("SeveridadeOcorrencia.fromString deve tratar variações de caixa e rejeitar inválidos")
        void testSeveridadeFromString() {
            assertThat(SeveridadeOcorrencia.fromString("  critica  ")).isEqualTo(SeveridadeOcorrencia.CRITICA);
            assertThat(SeveridadeOcorrencia.fromString("ALTA")).isEqualTo(SeveridadeOcorrencia.ALTA);
            assertThat(SeveridadeOcorrencia.fromString("media")).isEqualTo(SeveridadeOcorrencia.MEDIA);
            assertThat(SeveridadeOcorrencia.fromString("baixa")).isEqualTo(SeveridadeOcorrencia.BAIXA);
            assertThat(SeveridadeOcorrencia.fromString(null)).isNull();
            assertThat(SeveridadeOcorrencia.fromString("")).isNull();

            assertThrows(IllegalArgumentException.class, () -> SeveridadeOcorrencia.fromString("URGENTISSIMA"));
        }

        @Test
        @DisplayName("CategoriaOcorrencia.fromString deve tratar todas as 7 categorias e rejeitar inválidas")
        void testCategoriaFromString() {
            assertThat(CategoriaOcorrencia.fromString("alagamento")).isEqualTo(CategoriaOcorrencia.ALAGAMENTO);
            assertThat(CategoriaOcorrencia.fromString("bueiro_obstruido")).isEqualTo(CategoriaOcorrencia.BUEIRO_OBSTRUIDO);
            assertThat(CategoriaOcorrencia.fromString("deslizamento")).isEqualTo(CategoriaOcorrencia.DESLIZAMENTO);
            assertThat(CategoriaOcorrencia.fromString(null)).isNull();

            assertThrows(IllegalArgumentException.class, () -> CategoriaOcorrencia.fromString("TERREMOTO"));
        }
    }

    // =========================================================================
    // 6. QUERIES DE REPOSITÓRIO E FILTROS DINÂMICOS
    // =========================================================================

    @Nested
    @DisplayName("Desafio 6: Filtros Avançados e Consultas no Repositório")
    class RepositoryQueriesChallenge {

        @Test
        @DisplayName("Deve filtrar ocorrências de um bairro excluindo determinado status com findByBairroAndStatusNot")
        void testFindByBairroAndStatusNot() {
            Ocorrencia ocAtiva = new Ocorrencia("Ativa", "Desc", CategoriaOcorrencia.ALAGAMENTO,
                    SeveridadeOcorrencia.MEDIA, null, null, "Rua A", "Vila Prudente", cidadaoBase);
            ocAtiva.setStatus(StatusOcorrencia.ABERTA);

            Ocorrencia ocCancelada = new Ocorrencia("Cancelada", "Desc", CategoriaOcorrencia.ALAGAMENTO,
                    SeveridadeOcorrencia.BAIXA, null, null, "Rua B", "Vila Prudente", cidadaoBase);
            ocCancelada.setStatus(StatusOcorrencia.CANCELADA);

            ocorrenciaRepository.saveAndFlush(ocAtiva);
            ocorrenciaRepository.saveAndFlush(ocCancelada);
            entityManager.clear();

            List<Ocorrencia> ativasNoBairro = ocorrenciaRepository.findByBairroAndStatusNot("Vila Prudente", StatusOcorrencia.CANCELADA);

            assertThat(ativasNoBairro).hasSize(1);
            assertThat(ativasNoBairro.get(0).getTitulo()).isEqualTo("Ativa");
        }

        @Test
        @DisplayName("existsByOcorrenciaId deve retornar true apenas se o protocolo foi persistido")
        void testExistsByOcorrenciaId() {
            assertFalse(protocoloRepository.existsByOcorrenciaId(ocorrenciaBase.getId()));

            ProtocoloDefesaCivil p = new ProtocoloDefesaCivil("DEF-2026-EXISTS", "Equipe Alfa", "Parecer", null, ocorrenciaBase);
            protocoloRepository.saveAndFlush(p);

            assertTrue(protocoloRepository.existsByOcorrenciaId(ocorrenciaBase.getId()));
        }
    }
}
