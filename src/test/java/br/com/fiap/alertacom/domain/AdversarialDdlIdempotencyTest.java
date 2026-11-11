package br.com.fiap.alertacom.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Adversarial stress testing for DDL schema idempotency and repeatability.
 * Validates that schema.sql can execute multiple times consecutively in H2 (Oracle mode)
 * both with and without pre-existing relational data.
 */
@SpringBootTest
@ActiveProfiles("test")
class AdversarialDdlIdempotencyTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("ADV-DDL-1: Deve executar schema.sql 5 vezes consecutivas em banco limpo sem falhas")
    void testExecucaoMultiplaConsecutivaSchemaSql() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("schema.sql"));

        for (int i = 1; i <= 5; i++) {
            final int iteracao = i;
            assertDoesNotThrow(() -> populator.execute(dataSource),
                    "Falha na iteração " + iteracao + " de execução consecutiva do schema.sql");
        }

        // Valida que as tabelas existem e estão vazias
        Integer countCidadao = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TB_CIDADAO", Integer.class);
        Integer countOcorrencia = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TB_OCORRENCIA", Integer.class);
        Integer countProtocolo = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TB_PROTOCOLO_DEFESA_CIVIL", Integer.class);

        assertThat(countCidadao).isZero();
        assertThat(countOcorrencia).isZero();
        assertThat(countProtocolo).isZero();
    }

    @Test
    @DisplayName("ADV-DDL-2: Deve executar schema.sql consecutivamente mesmo com dados e FKs pré-existentes")
    void testExecucaoComDadosPreExistentesEFKs() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("schema.sql"));

        // Garante schema inicial
        populator.execute(dataSource);

        // Insere dados relacionais via SQL nativo
        jdbcTemplate.update("INSERT INTO TB_CIDADAO (nome, email, telefone, bairro, senha, role) " +
                "VALUES ('Usuario Stress', 'stress@fiap.com.br', '11999999999', 'Bela Vista', 'hash', 'ROLE_CIDADAO')");
        Long cidadaoId = jdbcTemplate.queryForObject("SELECT id_cidadao FROM TB_CIDADAO WHERE email = 'stress@fiap.com.br'", Long.class);

        jdbcTemplate.update("INSERT INTO TB_OCORRENCIA (cidadao_id, titulo, descricao, categoria, severidade, status, endereco, bairro) " +
                "VALUES (?, 'Alagamento Stress', 'Descricao', 'ALAGAMENTO', 'CRITICA', 'ABERTA', 'Rua X', 'Bela Vista')", cidadaoId);
        Long ocorrenciaId = jdbcTemplate.queryForObject("SELECT id_ocorrencia FROM TB_OCORRENCIA WHERE cidadao_id = ?", Long.class, cidadaoId);

        jdbcTemplate.update("INSERT INTO TB_PROTOCOLO_DEFESA_CIVIL (ocorrencia_id, numero_protocolo, equipe_responsavel) " +
                "VALUES (?, 'DEF-2026-99999', 'Equipe Alfa')", ocorrenciaId);

        // Confirma que os dados foram persistidos
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TB_PROTOCOLO_DEFESA_CIVIL", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TB_OCORRENCIA", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TB_CIDADAO", Integer.class)).isEqualTo(1);

        // Reexecuta o script 3 vezes seguidas sobre o banco com dados e FKs ativas
        for (int i = 1; i <= 3; i++) {
            final int iteracao = i;
            assertDoesNotThrow(() -> populator.execute(dataSource),
                    "Falha ao executar schema.sql com dados existentes na iteração " + iteracao);
        }

        // Verifica que o schema foi limpo e recriado com sucesso
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TB_CIDADAO", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TB_OCORRENCIA", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TB_PROTOCOLO_DEFESA_CIVIL", Integer.class)).isZero();

        // Verifica que novas inserções funcionam normalmente após recriação
        jdbcTemplate.update("INSERT INTO TB_CIDADAO (nome, email, telefone, bairro, senha, role) " +
                "VALUES ('Novo Usuario', 'novo@fiap.com.br', '11888888888', 'Centro', 'hash', 'ROLE_CIDADAO')");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TB_CIDADAO", Integer.class)).isEqualTo(1);
    }
}
