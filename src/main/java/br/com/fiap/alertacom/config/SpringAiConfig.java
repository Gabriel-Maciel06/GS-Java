package br.com.fiap.alertacom.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração da arquitetura Dual-Engine para geração de Boletins Preventivos da Defesa Civil.
 * <p>
 * Combina a integração com o Spring AI (OpenAI GPT-4o-mini) quando uma chave válida de produção
 * está disponível e o Motor de Fallback Determinístico Inteligente para operação offline,
 * ambientes de homologação/testes ou contingência em caso de indisponibilidade da API externa.
 * </p>
 */
@Configuration
public class SpringAiConfig {

    @Value("${spring.ai.openai.api-key:mock-key}")
    private String openAiApiKey;

    @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}")
    private String openAiModel;

    public String getOpenAiApiKey() {
        return openAiApiKey;
    }

    public String getOpenAiModel() {
        return openAiModel;
    }

    /**
     * Verifica se a chave da OpenAI configurada no ambiente é uma credencial real de produção
     * e não um marcador de desenvolvimento/teste.
     *
     * @param apiKey Chave configurada em {@code spring.ai.openai.api-key}.
     * @return {@code true} se for uma chave elegível para chamada real ao provedor LLM.
     */
    public static boolean isChaveOpenAiValida(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        String normalized = apiKey.trim().toLowerCase();
        return !normalized.startsWith("mock")
                && !normalized.startsWith("dummy")
                && !normalized.contains("sua-chave")
                && !normalized.contains("placeholder")
                && !normalized.equals("test")
                && !normalized.equals("none")
                && !normalized.equals("disabled");
    }
}
