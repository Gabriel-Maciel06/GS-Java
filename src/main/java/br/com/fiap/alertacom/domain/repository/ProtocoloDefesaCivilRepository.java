package br.com.fiap.alertacom.domain.repository;

import br.com.fiap.alertacom.domain.model.ProtocoloDefesaCivil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositório Spring Data JPA para a entidade ProtocoloDefesaCivil.
 * Gerencia despachos oficiais, controle de unicidade por ocorrência e acompanhamento de equipes táticas.
 */
@Repository
public interface ProtocoloDefesaCivilRepository extends JpaRepository<ProtocoloDefesaCivil, Long> {

    /**
     * Localiza um protocolo oficial pelo número identificador (ex: DEF-2026-00912).
     *
     * @param numeroProtocolo Código único formatado do protocolo.
     * @return Optional contendo o protocolo caso localizado.
     */
    Optional<ProtocoloDefesaCivil> findByNumeroProtocolo(String numeroProtocolo);

    /**
     * Verifica se determinado número de protocolo já foi utilizado.
     *
     * @param numeroProtocolo Número do protocolo a validar.
     * @return true se o número já existe; false caso contrário.
     */
    boolean existsByNumeroProtocolo(String numeroProtocolo);

    /**
     * Localiza o protocolo vinculado a uma determinada ocorrência.
     *
     * @param ocorrenciaId Identificador da ocorrência.
     * @return Optional contendo o protocolo vinculado.
     */
    Optional<ProtocoloDefesaCivil> findByOcorrenciaId(Long ocorrenciaId);

    /**
     * Verifica se uma determinada ocorrência já possui protocolo da Defesa Civil emitido.
     * Assegura a cardinalidade estrita 1:1 e evita emissões duplicadas de despacho (HTTP 422).
     *
     * @param ocorrenciaId Identificador da ocorrência.
     * @return true se a ocorrência já possui protocolo vinculado; false caso contrário.
     */
    boolean existsByOcorrenciaId(Long ocorrenciaId);

    /**
     * Consulta paginada de protocolos alocados para uma equipe técnica responsável.
     *
     * @param equipeResponsavel Nome ou código da equipe da Defesa Civil.
     * @param pageable Metadados de paginação.
     * @return Página de protocolos atribuídos à equipe.
     */
    Page<ProtocoloDefesaCivil> findByEquipeResponsavel(String equipeResponsavel, Pageable pageable);

    /**
     * Consulta paginada de protocolos pendentes (com data de conclusão nula).
     * Representa a fila de trabalho ativa e despachos operacionais em andamento.
     *
     * @param pageable Metadados de paginação.
     * @return Página de protocolos ainda não concluídos.
     */
    Page<ProtocoloDefesaCivil> findByDataConclusaoIsNull(Pageable pageable);

    /**
     * Consulta paginada de protocolos já concluídos e arquivados pela Defesa Civil.
     *
     * @param pageable Metadados de paginação.
     * @return Página de protocolos finalizados.
     */
    Page<ProtocoloDefesaCivil> findByDataConclusaoIsNotNull(Pageable pageable);

    /**
     * Busca um protocolo carregando ansiosamente (JOIN FETCH) a ocorrência vinculada
     * e o cidadão solicitante original em uma única consulta SQL.
     *
     * @param id Identificador único do protocolo.
     * @return Optional contendo o protocolo com detalhes completos.
     */
    @Query("SELECT p FROM ProtocoloDefesaCivil p " +
           "JOIN FETCH p.ocorrencia o " +
           "JOIN FETCH o.cidadao " +
           "WHERE p.id = :id")
    Optional<ProtocoloDefesaCivil> findByIdWithDetails(@Param("id") Long id);

    /**
     * Busca um protocolo pelo número oficial carregando ansiosamente todos os relacionamentos.
     *
     * @param numeroProtocolo Número do protocolo (ex: DEF-2026-00001).
     * @return Optional contendo o protocolo completo.
     */
    @Query("SELECT p FROM ProtocoloDefesaCivil p " +
           "JOIN FETCH p.ocorrencia o " +
           "JOIN FETCH o.cidadao " +
           "WHERE p.numeroProtocolo = :numeroProtocolo")
    Optional<ProtocoloDefesaCivil> findByNumeroProtocoloWithDetails(@Param("numeroProtocolo") String numeroProtocolo);
}
