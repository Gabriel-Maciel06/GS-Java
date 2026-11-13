package br.com.fiap.alertacom.domain;

import br.com.fiap.alertacom.domain.model.*;
import br.com.fiap.alertacom.domain.repository.CidadaoRepository;
import br.com.fiap.alertacom.domain.repository.OcorrenciaRepository;
import br.com.fiap.alertacom.domain.repository.ProtocoloDefesaCivilRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ProtocoloDefesaCivilRepositoryTest {

    @Autowired
    private ProtocoloDefesaCivilRepository protocoloRepository;

    @Autowired
    private OcorrenciaRepository ocorrenciaRepository;

    @Autowired
    private CidadaoRepository cidadaoRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Ocorrencia ocorrenciaPadrao;

    @BeforeEach
    void setUp() {
        entityManager.getEntityManager()
                .createNativeQuery("CREATE SEQUENCE IF NOT EXISTS SEQ_PROTOCOLO_NUMERO START WITH 1 INCREMENT BY 1 NOCACHE")
                .executeUpdate();

        Cidadao cidadao = new Cidadao("Gestor Defesa", "gestor.defesa@prefeitura.sp.gov.br", "11999990000", "Sé", "hashGestor", "ROLE_GESTOR");
        entityManager.persistAndFlush(cidadao);

        ocorrenciaPadrao = new Ocorrencia(
                "Deslizamento de terra em talude",
                "Trinca visível no asfalto com deslizamento parcial de terra",
                CategoriaOcorrencia.DESLIZAMENTO,
                SeveridadeOcorrencia.CRITICA,
                -23.5505,
                -46.6333,
                "Rua da Encosta, 500",
                "Perus",
                cidadao
        );
        entityManager.persistAndFlush(ocorrenciaPadrao);
    }

    @Test
    @DisplayName("Deve emitir e salvar protocolo com número formatado e auditoria")
    void testSalvarProtocoloComSucesso() {
        ProtocoloDefesaCivil protocolo = new ProtocoloDefesaCivil(
                "DEF-2026-00001",
                "Equipe Tática Bravo 3",
                "Vistoria de contenção e isolamento da área",
                LocalDateTime.now().plusHours(4),
                ocorrenciaPadrao
        );

        ProtocoloDefesaCivil salvo = protocoloRepository.save(protocolo);
        entityManager.flush();
        entityManager.clear();

        ProtocoloDefesaCivil buscado = protocoloRepository.findById(salvo.getId()).orElseThrow();
        assertThat(buscado.getId()).isNotNull();
        assertThat(buscado.getNumeroProtocolo()).isEqualTo("DEF-2026-00001");
        assertThat(buscado.getDataEmissao()).isNotNull();
        assertThat(buscado.getEquipeResponsavel()).isEqualTo("Equipe Tática Bravo 3");
        assertThat(buscado.getOcorrencia().getId()).isEqualTo(ocorrenciaPadrao.getId());
    }

    @Test
    @DisplayName("Deve lançar DataIntegrityViolationException ao duplicar número de protocolo (UK_PROTOCOLO_NUMERO)")
    void testViolacaoUnicidadeNumeroProtocolo() {
        ProtocoloDefesaCivil p1 = new ProtocoloDefesaCivil("DEF-2026-00099", "Equipe Alfa", "Parecer 1", null, ocorrenciaPadrao);
        protocoloRepository.saveAndFlush(p1);

        // Criar segunda ocorrência para tentar reutilizar o mesmo número de protocolo
        Ocorrencia oc2 = new Ocorrencia("Outra", "Desc", CategoriaOcorrencia.ALAGAMENTO, SeveridadeOcorrencia.ALTA, null, null, "Rua B", "Mooca", ocorrenciaPadrao.getCidadao());
        entityManager.persistAndFlush(oc2);

        ProtocoloDefesaCivil p2 = new ProtocoloDefesaCivil("DEF-2026-00099", "Equipe Beta", "Parecer 2", null, oc2);

        assertThrows(DataIntegrityViolationException.class, () -> protocoloRepository.saveAndFlush(p2));
    }

    @Test
    @DisplayName("Deve impedir vinculação de mais de um protocolo à mesma ocorrência (UK_PROTOCOLO_OCORRENCIA)")
    void testViolacaoUnicidadeOcorrenciaProtocolo() {
        ProtocoloDefesaCivil p1 = new ProtocoloDefesaCivil("DEF-2026-00101", "Equipe 1", "Parecer 1", null, ocorrenciaPadrao);
        protocoloRepository.saveAndFlush(p1);

        ProtocoloDefesaCivil p2 = new ProtocoloDefesaCivil("DEF-2026-00102", "Equipe 2", "Parecer 2", null, ocorrenciaPadrao);

        assertThrows(DataIntegrityViolationException.class, () -> protocoloRepository.saveAndFlush(p2));
    }

    @Test
    @DisplayName("Deve consultar protocolos pendentes com findByDataConclusaoIsNull")
    void testBuscarProtocolosPendentes() {
        ProtocoloDefesaCivil pendente = new ProtocoloDefesaCivil("DEF-2026-00201", "Equipe 1", "Em andamento", null, ocorrenciaPadrao);
        protocoloRepository.save(pendente);

        Ocorrencia ocConcluida = new Ocorrencia("Concluida", "Desc", CategoriaOcorrencia.QUEDA_ARVORE, SeveridadeOcorrencia.BAIXA, null, null, "Rua C", "Centro", ocorrenciaPadrao.getCidadao());
        entityManager.persistAndFlush(ocConcluida);

        ProtocoloDefesaCivil concluido = new ProtocoloDefesaCivil("DEF-2026-00202", "Equipe 2", "Atendido", null, ocConcluida);
        concluido.concluirAtendimento("Árvore podada e via liberada");
        protocoloRepository.save(concluido);

        entityManager.flush();
        entityManager.clear();

        Page<ProtocoloDefesaCivil> pendentes = protocoloRepository.findByDataConclusaoIsNull(PageRequest.of(0, 10));

        assertThat(pendentes.getTotalElements()).isEqualTo(1);
        assertThat(pendentes.getContent().get(0).getNumeroProtocolo()).isEqualTo("DEF-2026-00201");
    }

    @Test
    @DisplayName("Deve concluir atendimento atualizando status da ocorrência vinculada para RESOLVIDA")
    void testConcluirAtendimentoAtualizaOcorrencia() {
        ProtocoloDefesaCivil protocolo = new ProtocoloDefesaCivil("DEF-2026-00301", "Equipe Resgate", "Em análise", null, ocorrenciaPadrao);
        ocorrenciaPadrao.vincularProtocolo(protocolo);
        protocoloRepository.save(protocolo);
        entityManager.flush();

        assertThat(ocorrenciaPadrao.getStatus()).isEqualTo(StatusOcorrencia.ENCAMINHADA_DEFESA_CIVIL);

        protocolo.concluirAtendimento("Risco eliminado através de escoramento de emergência");
        entityManager.flush();
        entityManager.clear();

        Ocorrencia ocorrenciaAtualizada = ocorrenciaRepository.findById(ocorrenciaPadrao.getId()).orElseThrow();
        assertThat(ocorrenciaAtualizada.getStatus()).isEqualTo(StatusOcorrencia.RESOLVIDA);
    }

    @Test
    @DisplayName("Deve aplicar exclusão em cascata: excluir Ocorrencia exclui o Protocolo vinculado")
    void testCascadeDeleteOcorrenciaRemoveProtocolo() {
        ProtocoloDefesaCivil protocolo = new ProtocoloDefesaCivil("DEF-2026-00401", "Equipe Z", "Parecer", null, ocorrenciaPadrao);
        ocorrenciaPadrao.vincularProtocolo(protocolo);
        ProtocoloDefesaCivil salvo = protocoloRepository.save(protocolo);
        entityManager.flush();
        Long protocoloId = salvo.getId();

        ocorrenciaRepository.delete(ocorrenciaPadrao);
        entityManager.flush();
        entityManager.clear();

        assertThat(protocoloRepository.findById(protocoloId)).isEmpty();
    }

    @Test
    @DisplayName("Deve obter próximo número sequencial da sequence de protocolo")
    void testObterProximoNumeroSequencial() {
        Long seq1 = protocoloRepository.obterProximoNumeroSequencial();
        Long seq2 = protocoloRepository.obterProximoNumeroSequencial();

        assertThat(seq1).isNotNull().isPositive();
        assertThat(seq2).isNotNull().isGreaterThan(seq1);
    }
}
