package br.com.fiap.alertacom.domain.repository;

import br.com.fiap.alertacom.domain.model.Cidadao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositório Spring Data JPA para a entidade Cidadao.
 * Gerencia operações de persistência, consultas paginadas e autenticação.
 */
@Repository
public interface CidadaoRepository extends JpaRepository<Cidadao, Long> {

    /**
     * Localiza um cidadão pelo e-mail cadastrado (chave natural única).
     * Utilizado primordialmente pelo serviço de autenticação JWT (UserDetailsService).
     *
     * @param email E-mail institucional ou pessoal do cidadão.
     * @return Optional contendo o cidadão caso encontrado.
     */
    Optional<Cidadao> findByEmail(String email);

    /**
     * Verifica a existência prévia de um e-mail no banco de dados.
     * Utilizado na validação prévia de cadastro para evitar conflitos (HTTP 409).
     *
     * @param email E-mail a ser verificado.
     * @return true se o e-mail já estiver em uso; false caso contrário.
     */
    boolean existsByEmail(String email);

    /**
     * Lista cidadãos cadastrados filtrados por bairro com paginação nativa.
     *
     * @param bairro Nome do bairro pesquisado.
     * @param pageable Configurações de paginação e ordenação (page, size, sort).
     * @return Página de cidadãos do bairro informado.
     */
    Page<Cidadao> findByBairro(String bairro, Pageable pageable);

    /**
     * Lista apenas cidadãos ativos na plataforma com suporte a paginação.
     *
     * @param pageable Configurações de paginação e ordenação.
     * @return Página de cidadãos ativos.
     */
    Page<Cidadao> findByAtivoTrue(Pageable pageable);

    /**
     * Lista cidadãos ativos de um determinado bairro com paginação.
     *
     * @param bairro Nome do bairro.
     * @param pageable Configurações de paginação e ordenação.
     * @return Página de cidadãos ativos do bairro.
     */
    Page<Cidadao> findByBairroAndAtivoTrue(String bairro, Pageable pageable);

    /**
     * Lista cidadãos pelo papel de autorização (ROLE_CIDADAO ou ROLE_GESTOR).
     *
     * @param role Papel de segurança a ser filtrado.
     * @param pageable Configurações de paginação.
     * @return Página de cidadãos com o papel informado.
     */
    Page<Cidadao> findByRole(String role, Pageable pageable);

    /**
     * Contabiliza o número total de cidadãos cadastrados em um determinado bairro.
     *
     * @param bairro Nome do bairro.
     * @return Quantidade total de munícipes cadastrados.
     */
    long countByBairro(String bairro);

    /**
     * Contabiliza o total de cidadãos com conta ativa.
     *
     * @return Quantidade total de usuários ativos.
     */
    long countByAtivoTrue();

    /**
     * Busca um cidadão carregando ansiosamente (JOIN FETCH) sua lista de ocorrências.
     * Previne a ocorrência de LazyInitializationException fora do escopo transacional.
     *
     * @param id Identificador único do cidadão.
     * @return Optional com o cidadão e suas ocorrências carregadas.
     */
    @Query("SELECT c FROM Cidadao c LEFT JOIN FETCH c.ocorrencias WHERE c.id = :id")
    Optional<Cidadao> findByIdWithOcorrencias(@Param("id") Long id);
}
