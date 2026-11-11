package br.com.fiap.alertacom.domain.repository;

import br.com.fiap.alertacom.domain.model.CategoriaOcorrencia;
import br.com.fiap.alertacom.domain.model.Ocorrencia;
import br.com.fiap.alertacom.domain.model.SeveridadeOcorrencia;
import br.com.fiap.alertacom.domain.model.StatusOcorrencia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositório Spring Data JPA para a entidade Ocorrencia.
 * Provê consultas paginadas por bairro, severidade, categoria, status,
 * busca dinâmica com múltiplos filtros e integração com o motor de IA.
 */
@Repository
public interface OcorrenciaRepository extends JpaRepository<Ocorrencia, Long> {

    /**
     * Consulta paginada de ocorrências registradas em um bairro específico.
     *
     * @param bairro Nome do bairro.
     * @param pageable Metadados de paginação e ordenação.
     * @return Página de ocorrências no bairro.
     */
    Page<Ocorrencia> findByBairro(String bairro, Pageable pageable);

    /**
     * Consulta paginada de ocorrências filtradas por grau de severidade.
     *
     * @param severidade Grau de gravidade (BAIXA, MEDIA, ALTA, CRITICA).
     * @param pageable Metadados de paginação.
     * @return Página de ocorrências com a severidade indicada.
     */
    Page<Ocorrencia> findBySeveridade(SeveridadeOcorrencia severidade, Pageable pageable);

    /**
     * Consulta paginada de ocorrências filtradas por status atual do ciclo de vida.
     *
     * @param status Status da ocorrência (ABERTA, EM_ANALISE, ENCAMINHADA_DEFESA_CIVIL, etc.).
     * @param pageable Metadados de paginação.
     * @return Página de ocorrências com o status indicado.
     */
    Page<Ocorrencia> findByStatus(StatusOcorrencia status, Pageable pageable);

    /**
     * Consulta paginada de ocorrências filtradas por categoria do incidente climático.
     *
     * @param categoria Categoria da ocorrência (ALAGAMENTO, DESLIZAMENTO, etc.).
     * @param pageable Metadados de paginação.
     * @return Página de ocorrências na categoria informada.
     */
    Page<Ocorrencia> findByCategoria(CategoriaOcorrencia categoria, Pageable pageable);

    /**
     * Consulta paginada de ocorrências abertas por um determinado cidadão.
     *
     * @param cidadaoId ID do cidadão solicitante.
     * @param pageable Metadados de paginação.
     * @return Página de ocorrências pertencentes ao cidadão.
     */
    Page<Ocorrencia> findByCidadaoId(Long cidadaoId, Pageable pageable);

    /**
     * Consulta paginada por bairro e status simultaneamente.
     *
     * @param bairro Nome do bairro.
     * @param status Status da ocorrência.
     * @param pageable Metadados de paginação.
     * @return Página de ocorrências atendendo a ambos os critérios.
     */
    Page<Ocorrencia> findByBairroAndStatus(String bairro, StatusOcorrencia status, Pageable pageable);

    /**
     * Consulta paginada por bairro e severidade simultaneamente.
     *
     * @param bairro Nome do bairro.
     * @param severidade Severidade da ocorrência.
     * @param pageable Metadados de paginação.
     * @return Página de ocorrências atendendo a ambos os critérios.
     */
    Page<Ocorrencia> findByBairroAndSeveridade(String bairro, SeveridadeOcorrencia severidade, Pageable pageable);

    /**
     * Lista todas as ocorrências de um bairro cujo status NÃO seja o informado (ex: excluir CANCELADA).
     *
     * @param bairro Nome do bairro.
     * @param status Status a ser excluído da listagem.
     * @return Lista de ocorrências ativas no bairro.
     */
    List<Ocorrencia> findByBairroAndStatusNot(String bairro, StatusOcorrencia status);

    /**
     * Lista ocorrências registradas em um bairro a partir de uma data limite.
     * MÉTODO FUNDAMENTAL PARA A INTEGRAÇÃO COM SPRING AI (Milestone 5):
     * Alimenta o prompt do Boletim Preventivo da Defesa Civil com o histórico recente de relatos.
     *
     * @param bairro Nome do bairro alvo.
     * @param dataLimite Carimbo de data/hora a partir do qual as ocorrências serão agregadas.
     * @return Lista de ocorrências recentes no bairro para sumarização via LLM.
     */
    List<Ocorrencia> findByBairroAndDataCriacaoAfter(String bairro, LocalDateTime dataLimite);

    /**
     * Contabiliza a quantidade de ocorrências em um bairro com determinado status.
     *
     * @param bairro Nome do bairro.
     * @param status Status pesquisado.
     * @return Quantidade total de ocorrências.
     */
    long countByBairroAndStatus(String bairro, StatusOcorrencia status);

    /**
     * Contabiliza a quantidade de ocorrências por grau de severidade.
     *
     * @param severidade Nível de severidade.
     * @return Total de ocorrências no nível informado.
     */
    long countBySeveridade(SeveridadeOcorrencia severidade);

    /**
     * Contabiliza a quantidade de ocorrências por status.
     *
     * @param status Status da ocorrência.
     * @return Total de ocorrências no status informado.
     */
    long countByStatus(StatusOcorrencia status);

    /**
     * Retorna as 5 ocorrências mais recentes cadastradas na plataforma.
     * Utilizado em painéis de monitoramento em tempo real (feed de atividades).
     *
     * @return Lista com até 5 ocorrências ordenadas por data de criação decrescente.
     */
    List<Ocorrencia> findTop5ByOrderByDataCriacaoDesc();

    /**
     * Busca uma ocorrência carregando de forma ansiosa (JOIN FETCH) tanto o cidadão vinculador
     * quanto o protocolo oficial da Defesa Civil (caso emitido).
     * Elimina completamente o problema N+1 ao detalhar chamados na API REST.
     *
     * @param id Identificador da ocorrência.
     * @return Optional com a ocorrência e seus relacionamentos carregados.
     */
    @Query("SELECT o FROM Ocorrencia o " +
           "LEFT JOIN FETCH o.cidadao " +
           "LEFT JOIN FETCH o.protocolo " +
           "WHERE o.id = :id")
    Optional<Ocorrencia> findByIdWithDetails(@Param("id") Long id);

    /**
     * Consulta dinâmica multi-critério com paginação nativa.
     * Permite ao gestor municipal combinar livremente filtros de bairro, status, severidade e categoria.
     * Caso qualquer parâmetro seja nulo, a cláusula correspondente é ignorada.
     *
     * @param bairro Filtro opcional por bairro (insensível a maiúsculas/minúsculas).
     * @param status Filtro opcional por status.
     * @param severidade Filtro opcional por severidade.
     * @param categoria Filtro opcional por categoria.
     * @param pageable Configurações de paginação e ordenação.
     * @return Página de ocorrências que atendem aos filtros fornecidos.
     */
    @Query("SELECT o FROM Ocorrencia o WHERE " +
           "(:bairro IS NULL OR LOWER(o.bairro) = LOWER(:bairro)) AND " +
           "(:status IS NULL OR o.status = :status) AND " +
           "(:severidade IS NULL OR o.severidade = :severidade) AND " +
           "(:categoria IS NULL OR o.categoria = :categoria)")
    Page<Ocorrencia> findWithFilters(
            @Param("bairro") String bairro,
            @Param("status") StatusOcorrencia status,
            @Param("severidade") SeveridadeOcorrencia severidade,
            @Param("categoria") CategoriaOcorrencia categoria,
            Pageable pageable);
}
