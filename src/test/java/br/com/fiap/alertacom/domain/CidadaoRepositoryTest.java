package br.com.fiap.alertacom.domain;

import br.com.fiap.alertacom.domain.model.Cidadao;
import br.com.fiap.alertacom.domain.repository.CidadaoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ActiveProfiles("test")
class CidadaoRepositoryTest {

    @Autowired
    private CidadaoRepository cidadaoRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Deve persistir cidadão com sucesso e gerar ID e carimbo de auditoria")
    void testSalvarCidadaoComSucesso() {
        Cidadao cidadao = new Cidadao(
                "Gabriel Maciel",
                "gabriel@fiap.com.br",
                "11999998888",
                "Morumbi",
                "$2a$10$hashBcryptSeguro",
                "ROLE_CIDADAO"
        );

        Cidadao salvo = cidadaoRepository.save(cidadao);
        entityManager.flush();

        assertThat(salvo.getId()).isNotNull();
        assertThat(salvo.getDataCadastro()).isNotNull();
        assertThat(salvo.getAtivo()).isTrue();
        assertThat(salvo.getRole()).isEqualTo("ROLE_CIDADAO");
    }

    @Test
    @DisplayName("Deve violar constraint de unicidade ao cadastrar e-mail duplicado (UK_CIDADAO_EMAIL)")
    void testViolacaoUnicidadeEmail() {
        Cidadao c1 = new Cidadao("Usuario Um", "duplicado@fiap.com.br", "11911111111", "Mooca", "hash1", "ROLE_CIDADAO");
        cidadaoRepository.saveAndFlush(c1);

        Cidadao c2 = new Cidadao("Usuario Dois", "duplicado@fiap.com.br", "11922222222", "Tatuapé", "hash2", "ROLE_CIDADAO");

        assertThrows(DataIntegrityViolationException.class, () -> cidadaoRepository.saveAndFlush(c2));
    }

    @Test
    @DisplayName("Deve localizar cidadão por e-mail com sucesso")
    void testBuscarPorEmail() {
        Cidadao c = new Cidadao("Carlos Silva", "carlos.silva@fiap.com.br", "11988887777", "Ipiranga", "hashSecret", "ROLE_CIDADAO");
        cidadaoRepository.save(c);
        entityManager.flush();
        entityManager.clear();

        Optional<Cidadao> resultado = cidadaoRepository.findByEmail("carlos.silva@fiap.com.br");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getNome()).isEqualTo("Carlos Silva");
        assertThat(resultado.get().getBairro()).isEqualTo("Ipiranga");
    }

    @Test
    @DisplayName("Deve retornar Optional vazio ao buscar por e-mail inexistente")
    void testBuscarPorEmailInexistente() {
        Optional<Cidadao> resultado = cidadaoRepository.findByEmail("inexistente@alerta.com");
        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("Deve verificar existência de e-mail com existsByEmail")
    void testExistsByEmail() {
        Cidadao c = new Cidadao("Ana Paula", "ana.paula@fiap.com.br", "11977776666", "Vila Mariana", "hashAna", "ROLE_GESTOR");
        cidadaoRepository.save(c);
        entityManager.flush();

        assertThat(cidadaoRepository.existsByEmail("ana.paula@fiap.com.br")).isTrue();
        assertThat(cidadaoRepository.existsByEmail("nao.existe@fiap.com.br")).isFalse();
    }

    @Test
    @DisplayName("Deve listar cidadãos filtrando por bairro com paginação")
    void testBuscarPorBairroPaginado() {
        cidadaoRepository.save(new Cidadao("Munícipe 1", "m1@fiap.com", "11911111111", "Perus", "h1", "ROLE_CIDADAO"));
        cidadaoRepository.save(new Cidadao("Munícipe 2", "m2@fiap.com", "11922222222", "Perus", "h2", "ROLE_CIDADAO"));
        cidadaoRepository.save(new Cidadao("Munícipe 3", "m3@fiap.com", "11933333333", "Mooca", "h3", "ROLE_CIDADAO"));
        entityManager.flush();
        entityManager.clear();

        Page<Cidadao> paginaPerus = cidadaoRepository.findByBairro("Perus", PageRequest.of(0, 10, Sort.by("nome").ascending()));

        assertThat(paginaPerus.getTotalElements()).isEqualTo(2);
        assertThat(paginaPerus.getContent()).extracting(Cidadao::getNome).containsExactly("Munícipe 1", "Munícipe 2");
    }

    @Test
    @DisplayName("Deve filtrar apenas cidadãos com status ativo = true")
    void testBuscarPorAtivoTrue() {
        Cidadao ativo = new Cidadao("Ativo", "ativo@fiap.com", "1191", "Centro", "h1", "ROLE_CIDADAO");
        Cidadao inativo = new Cidadao("Inativo", "inativo@fiap.com", "1192", "Centro", "h2", "ROLE_CIDADAO");
        inativo.setAtivo(false);

        cidadaoRepository.save(ativo);
        cidadaoRepository.save(inativo);
        entityManager.flush();
        entityManager.clear();

        Page<Cidadao> resultado = cidadaoRepository.findByAtivoTrue(PageRequest.of(0, 10));

        assertThat(resultado.getContent()).extracting(Cidadao::getEmail).contains("ativo@fiap.com");
        assertThat(resultado.getContent()).extracting(Cidadao::getEmail).doesNotContain("inativo@fiap.com");
    }
}
