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
 * Teste de integração de banco validando a execução do script DDL canônico (schema.sql)
 * em conformidade estrita com a sintaxe dual Oracle 23c / H2 (MODE=Oracle).
 */
@SpringBootTest
@ActiveProfiles("test")
class SchemaSqlCompatibilityTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Deve carregar e executar o schema.sql canônico sem erros de sintaxe no H2 modo Oracle")
    void testExecucaoSchemaSqlSemErros() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("schema.sql"));

        assertDoesNotThrow(() -> populator.execute(dataSource),
                "O arquivo schema.sql deve ser 100% compatível com a sintaxe Oracle 23c e H2 MODE=Oracle");

        // Verifica existência das tabelas principais e sequence de protocolo
        Integer totalCidadaos = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TB_CIDADAO", Integer.class);
        Integer totalOcorrencias = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TB_OCORRENCIA", Integer.class);
        Integer totalProtocolos = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TB_PROTOCOLO_DEFESA_CIVIL", Integer.class);
        Long proximoSeq = jdbcTemplate.queryForObject("SELECT SEQ_PROTOCOLO_NUMERO.NEXTVAL FROM DUAL", Long.class);

        assertThat(totalCidadaos).isNotNull().isZero();
        assertThat(totalOcorrencias).isNotNull().isZero();
        assertThat(totalProtocolos).isNotNull().isZero();
        assertThat(proximoSeq).isNotNull().isPositive();
    }

    @Test
    @DisplayName("Deve validar a idempotência do script schema.sql ao executá-lo duas vezes consecutivas")
    void testIdempotenciaSchemaSql() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("schema.sql"));

        // Primeira execução
        assertDoesNotThrow(() -> populator.execute(dataSource));

        // Segunda execução consecutiva (testa funcionamento do DROP TABLE IF EXISTS ... CASCADE CONSTRAINTS)
        assertDoesNotThrow(() -> populator.execute(dataSource),
                "O script schema.sql deve ser totalmente idempotente em sucessivas execuções");
    }
}
