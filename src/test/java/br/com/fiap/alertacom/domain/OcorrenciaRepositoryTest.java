package br.com.fiap.alertacom.domain;

import br.com.fiap.alertacom.domain.model.*;
import br.com.fiap.alertacom.domain.repository.CidadaoRepository;
import br.com.fiap.alertacom.domain.repository.OcorrenciaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class OcorrenciaRepositoryTest {

    @Autowired
    private OcorrenciaRepository ocorrenciaRepository;

    @Autowired
    private CidadaoRepository cidadaoRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Cidadao cidadaoPadrao;

    @BeforeEach
    void setUp() {
        cidadaoPadrao = new Cidadao(
                "Maria Souza",
                "maria.souza@fiap.com.br",
                "11988889999",
                "Vila Prudente",
                "hashSenha",
                "ROLE_CIDADAO"
        );
        entityManager.persistAndFlush(cidadaoPadrao);
    }

    @Test
    @DisplayName("Deve persistir ocorrência vinculada ao cidadão com status ABERTA por padrão")
    void testSalvarOcorrenciaComSucesso() {
        Ocorrencia ocorrencia = new Ocorrencia(
                "Bueiro entupido com risco de transbordamento",
                "Bueiro completamente bloqueado por lixo e galhos na esquina com a Av. Principal",
                CategoriaOcorrencia.BUEIRO_OBSTRUIDO,
                SeveridadeOcorrencia.ALTA,
                -23.5855,
                -46.5655,
                "Rua das Flores, 120",
                "Vila Prudente",
                cidadaoPadrao
        );

        Ocorrencia salva = ocorrenciaRepository.save(ocorrencia);
        entityManager.flush();
        entityManager.clear();

        Ocorrencia buscada = ocorrenciaRepository.findById(salva.getId()).orElseThrow();
        assertThat(buscada.getId()).isNotNull();
        assertThat(buscada.getStatus()).isEqualTo(StatusOcorrencia.ABERTA);
        assertThat(buscada.getDataCriacao()).isNotNull();
        assertThat(buscada.getCategoria()).isEqualTo(CategoriaOcorrencia.BUEIRO_OBSTRUIDO);
        assertThat(buscada.getSeveridade()).isEqualTo(SeveridadeOcorrencia.ALTA);
        assertThat(buscada.getCidadao().getId()).isEqualTo(cidadaoPadrao.getId());
    }

    @Test
    @DisplayName("Deve filtrar ocorrências paginadas por bairro")
    void testBuscarPorBairroPaginado() {
        ocorrenciaRepository.save(new Ocorrencia("Alagamento 1", "Desc 1", CategoriaOcorrencia.ALAGAMENTO, SeveridadeOcorrencia.ALTA, null, null, "Rua A", "Ipiranga", cidadaoPadrao));
        ocorrenciaRepository.save(new Ocorrencia("Alagamento 2", "Desc 2", CategoriaOcorrencia.ALAGAMENTO, SeveridadeOcorrencia.MEDIA, null, null, "Rua B", "Ipiranga", cidadaoPadrao));
        ocorrenciaRepository.save(new Ocorrencia("Queda Árvore", "Desc 3", CategoriaOcorrencia.QUEDA_ARVORE, SeveridadeOcorrencia.BAIXA, null, null, "Rua C", "Morumbi", cidadaoPadrao));
        entityManager.flush();
        entityManager.clear();

        Page<Ocorrencia> paginaIpiranga = ocorrenciaRepository.findByBairro("Ipiranga", PageRequest.of(0, 10));

        assertThat(paginaIpiranga.getTotalElements()).isEqualTo(2);
        assertThat(paginaIpiranga.getContent()).allMatch(o -> o.getBairro().equals("Ipiranga"));
    }

    @Test
    @DisplayName("Deve filtrar ocorrências por severidade (ex: CRITICA)")
    void testBuscarPorSeveridade() {
        ocorrenciaRepository.save(new Ocorrencia("Deslizamento encosta", "Desc", CategoriaOcorrencia.DESLIZAMENTO, SeveridadeOcorrencia.CRITICA, null, null, "Morro X", "Perus", cidadaoPadrao));
        ocorrenciaRepository.save(new Ocorrencia("Bueiro leve", "Desc", CategoriaOcorrencia.BUEIRO_OBSTRUIDO, SeveridadeOcorrencia.BAIXA, null, null, "Rua Y", "Perus", cidadaoPadrao));
        entityManager.flush();
        entityManager.clear();

        Page<Ocorrencia> criticas = ocorrenciaRepository.findBySeveridade(SeveridadeOcorrencia.CRITICA, PageRequest.of(0, 10));

        assertThat(criticas.getTotalElements()).isEqualTo(1);
        assertThat(criticas.getContent().get(0).getSeveridade()).isEqualTo(SeveridadeOcorrencia.CRITICA);
    }

    @Test
    @DisplayName("Deve buscar ocorrências recentes no bairro para o gerador de boletim Spring AI")
    void testBuscarPorBairroEDataCriacaoApos() {
        LocalDateTime agora = LocalDateTime.now();
        Ocorrencia recente = new Ocorrencia("Alagamento Via", "Desc", CategoriaOcorrencia.ALAGAMENTO, SeveridadeOcorrencia.ALTA, null, null, "Av X", "Mooca", cidadaoPadrao);
        ocorrenciaRepository.save(recente);
        entityManager.flush();
        entityManager.clear();

        List<Ocorrencia> ocorrenciasParaIa = ocorrenciaRepository.findByBairroAndDataCriacaoAfter("Mooca", agora.minusHours(1));

        assertThat(ocorrenciasParaIa).isNotEmpty();
        assertThat(ocorrenciasParaIa.get(0).getBairro()).isEqualTo("Mooca");
    }

    @Test
    @DisplayName("Deve consultar ocorrências com múltiplos filtros dinâmicos via findWithFilters")
    void testFindWithFilters() {
        ocorrenciaRepository.save(new Ocorrencia("Alagamento Centro", "Desc", CategoriaOcorrencia.ALAGAMENTO, SeveridadeOcorrencia.CRITICA, null, null, "Rua Direita", "Centro", cidadaoPadrao));
        ocorrenciaRepository.save(new Ocorrencia("Bueiro Centro", "Desc", CategoriaOcorrencia.BUEIRO_OBSTRUIDO, SeveridadeOcorrencia.MEDIA, null, null, "Rua XV", "Centro", cidadaoPadrao));
        entityManager.flush();
        entityManager.clear();

        Page<Ocorrencia> resultado = ocorrenciaRepository.findWithFilters(
                "Centro",
                StatusOcorrencia.ABERTA,
                SeveridadeOcorrencia.CRITICA,
                CategoriaOcorrencia.ALAGAMENTO,
                PageRequest.of(0, 10)
        );

        assertThat(resultado.getTotalElements()).isEqualTo(1);
        assertThat(resultado.getContent().get(0).getTitulo()).isEqualTo("Alagamento Centro");
    }

    @Test
    @DisplayName("Deve carregar ocorrência com JOIN FETCH de cidadão e protocolo via findByIdWithDetails")
    void testFindByIdWithDetails() {
        Ocorrencia ocorrencia = new Ocorrencia("Queda de Fiação", "Desc", CategoriaOcorrencia.OUTROS, SeveridadeOcorrencia.ALTA, null, null, "Rua Z", "Mooca", cidadaoPadrao);
        Ocorrencia salva = ocorrenciaRepository.save(ocorrencia);
        entityManager.flush();
        entityManager.clear();

        Optional<Ocorrencia> detalhada = ocorrenciaRepository.findByIdWithDetails(salva.getId());

        assertThat(detalhada).isPresent();
        assertThat(detalhada.get().getCidadao().getNome()).isEqualTo("Maria Souza");
    }

    @Test
    @DisplayName("Deve aplicar exclusão em cascata: excluir Cidadao remove automaticamente suas ocorrências")
    void testCascadeDeleteCidadao() {
        Cidadao novoCidadao = new Cidadao("Temp", "temp@fiap.com", "119", "Bairro", "pwd", "ROLE_CIDADAO");
        Ocorrencia oc = new Ocorrencia("Oc Temp", "Desc", CategoriaOcorrencia.ENXURRADA, SeveridadeOcorrencia.ALTA, null, null, "Rua", "Bairro", novoCidadao);
        novoCidadao.addOcorrencia(oc);

        Cidadao salvo = cidadaoRepository.save(novoCidadao);
        entityManager.flush();
        Long ocorrenciaId = salvo.getOcorrencias().get(0).getId();

        cidadaoRepository.delete(salvo);
        entityManager.flush();
        entityManager.clear();

        assertThat(ocorrenciaRepository.findById(ocorrenciaId)).isEmpty();
    }
}
