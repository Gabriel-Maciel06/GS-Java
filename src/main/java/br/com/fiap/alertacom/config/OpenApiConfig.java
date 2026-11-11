package br.com.fiap.alertacom.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuração institucional do SpringDoc OpenAPI 2.6.0 (Swagger UI) para o AlertaCom.
 * Define metadados corporativos da Global Solution 2026/2 FIAP, servidores de execução
 * e esquema global de autenticação Stateless Bearer JWT (HMAC-SHA256).
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AlertaCom API — Plataforma CitizenTech de Resiliência Urbana e Defesa Civil")
                        .version("1.0.0")
                        .description("""
                                Backend corporativo desenvolvido para a **Global Solution 2026/2 FIAP (Java Advanced)** sob o conceito **CitizenTech**, alinhado aos **ODS 11** (Cidades e Comunidades Sustentáveis) e **ODS 13** (Ação Contra a Mudança Global do Clima) da ONU.
                                
                                ### Pilares Arquiteturais
                                - **Domínio Relacional & Persistência Dual:** Gestão integrada de `Cidadao`, `Ocorrencia` e `ProtocoloDefesaCivil` sobre **Oracle Database 23c** (Produção/Docker) e **H2 Database em modo Oracle** (Desenvolvimento/Testes), com paginação nativa (`Pageable`) e máquina de estados transacional.
                                - **Validação & Padronização RFC 7807:** Validação declarativa via Jakarta Bean Validation e tratamento global de exceções retornando `ProblemDetail` padronizado.
                                - **Segurança Stateless JWT & RBAC:** Autenticação via tokens JWT assinados com HMAC-SHA256 e controle de acesso baseado em papéis (`ROLE_CIDADAO` e `ROLE_GESTOR`).
                                - **Arquitetura Orientada a Eventos (RabbitMQ):** Publicação assíncrona resiliente na exchange `alerta.exchange` (`routingKey: alerta.critico.#` -> fila `alertas.criticos.queue`) para ocorrências de severidade `ALTA` ou `CRITICA`, com consumo em tempo real via `@RabbitListener`.
                                - **Inteligência Artificial Preventiva (Spring AI Dual-Engine):** Geração de Boletins Preventivos de Defesa Civil contextualizados com dados reais do bairro via **OpenAI ChatClient** e motor de contingência determinístico (**Fallback Offline**).
                                """)
                        .contact(new Contact()
                                .name("Equipe AlertaCom (2TDSR): Gabriel Maciel (RM562795), Thomas Fontes (RM562254), Vitória Martins (RM565160), Matheus Molina (RM563399)")
                                .email("rm562795@fiap.com.br")
                                .url("https://github.com/Gabriel-Maciel06/GS-Java"))
                        .license(new License()
                                .name("MIT License — Global Solution FIAP 2026/2")
                                .url("https://opensource.org/licenses/MIT")))
                .externalDocs(new ExternalDocumentation()
                        .description("Documentação Corporativa AlertaCom & Roteiro do Pitch (ODS 11 & ODS 13)")
                        .url("https://github.com/Gabriel-Maciel06/GS-Java"))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Ambiente Local (Dev H2 / Docker Oracle 23c)")
                ))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Informe o token JWT obtido em `POST /api/auth/login` (sem o prefixo 'Bearer ').")));
    }
}
