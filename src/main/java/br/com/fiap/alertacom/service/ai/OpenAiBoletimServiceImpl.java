package br.com.fiap.alertacom.service.ai;

import br.com.fiap.alertacom.config.SpringAiConfig;
import br.com.fiap.alertacom.domain.model.CategoriaOcorrencia;
import br.com.fiap.alertacom.domain.model.Ocorrencia;
import br.com.fiap.alertacom.domain.model.SeveridadeOcorrencia;
import br.com.fiap.alertacom.domain.model.StatusOcorrencia;
import br.com.fiap.alertacom.domain.repository.OcorrenciaRepository;
import br.com.fiap.alertacom.dto.response.BoletimPreventivoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementação Dual-Engine do serviço de Boletim Preventivo da Defesa Civil.
 * <p>
 * Opera em dois modos complementares de alta resiliência:
 * <ul>
 *   <li><b>Modo Primário (SPRING_AI_OPENAI):</b> Aciona o modelo LLM via {@link ChatModel} quando
 *       uma chave de produção válida está configurada e o serviço externo está acessível.</li>
 *   <li><b>Modo Contingência (FALLBACK_DETERMINISTICO):</b> Sintetiza automaticamente o boletim
 *       com base nas ocorrências ativas do banco de dados quando a chave é de desenvolvimento/teste
 *       ou em caso de timeout/falha na comunicação com a API da OpenAI, garantindo disponibilidade
 *       de 100% em situações de emergência climática.</li>
 * </ul>
 * </p>
 */
@Service
public class OpenAiBoletimServiceImpl implements BoletimIaService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiBoletimServiceImpl.class);

    public static final String MODO_OPENAI = "SPRING_AI_OPENAI";
    public static final String MODO_FALLBACK = "FALLBACK_DETERMINISTICO";
    private static final String MODELO_FALLBACK = "fallback-deterministico-defesa-civil-v1";

    private final OcorrenciaRepository ocorrenciaRepository;
    private final ChatModel chatModel;
    private final String openAiApiKey;
    private final String openAiModel;

    @Autowired
    public OpenAiBoletimServiceImpl(
            OcorrenciaRepository ocorrenciaRepository,
            @Autowired(required = false) ChatModel chatModel,
            @Value("${spring.ai.openai.api-key:mock-key}") String openAiApiKey,
            @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String openAiModel) {
        this.ocorrenciaRepository = ocorrenciaRepository;
        this.chatModel = chatModel;
        this.openAiApiKey = openAiApiKey;
        this.openAiModel = openAiModel;
    }

    @Override
    @Transactional(readOnly = true)
    public BoletimPreventivoResponse gerarBoletimPreventivo(String bairro) {
        return gerarBoletimPreventivo(bairro, 24);
    }

    @Override
    @Transactional(readOnly = true)
    public BoletimPreventivoResponse gerarBoletimPreventivo(String bairro, Integer horasHistorico) {
        validarBairro(bairro);
        String bairroLimpo = bairro.trim();

        List<Ocorrencia> ocorrenciasAtivas = buscarOcorrenciasAtivasDoBairro(bairroLimpo);
        String nivelRiscoGeral = calcularNivelRiscoGeral(ocorrenciasAtivas);
        List<String> pontosCriticos = extrairPontosCriticosEvitar(bairroLimpo, ocorrenciasAtivas);
        List<String> recomendacoes = gerarRecomendacoesPopulacao(nivelRiscoGeral, ocorrenciasAtivas);
        String planoAcao = gerarPlanoAcaoDefesaCivil(bairroLimpo, nivelRiscoGeral, ocorrenciasAtivas);

        if (chatModel != null && SpringAiConfig.isChaveOpenAiValida(openAiApiKey)) {
            try {
                String prompt = construirPromptDefesaCivil(bairroLimpo, nivelRiscoGeral, ocorrenciasAtivas);
                String sinteseIa = chatModel.call(prompt);
                if (sinteseIa != null && !sinteseIa.isBlank()) {
                    log.info("Boletim preventivo gerado via Spring AI OpenAI para o bairro '{}' ({} ocorrências ativas)",
                            bairroLimpo, ocorrenciasAtivas.size());
                    return new BoletimPreventivoResponse(
                            bairroLimpo,
                            LocalDateTime.now(),
                            ocorrenciasAtivas.size(),
                            nivelRiscoGeral,
                            sinteseIa.trim(),
                            recomendacoes,
                            pontosCriticos,
                            planoAcao,
                            MODO_OPENAI,
                            openAiModel != null && !openAiModel.isBlank() ? openAiModel : "gpt-4o-mini"
                    );
                }
            } catch (Exception ex) {
                log.warn("Falha ao consultar API externa da OpenAI para o bairro '{}'. Acionando Motor de Fallback Determinístico: {}",
                        bairroLimpo, ex.getMessage());
            }
        }

        return gerarBoletimDeterministico(
                bairroLimpo,
                ocorrenciasAtivas,
                nivelRiscoGeral,
                recomendacoes,
                pontosCriticos,
                planoAcao
        );
    }

    private void validarBairro(String bairro) {
        if (bairro == null || bairro.trim().isEmpty()) {
            throw new IllegalArgumentException("O parâmetro 'bairro' é obrigatório e não pode estar vazio ou em branco.");
        }
    }

    /**
     * Recupera as ocorrências do bairro e descarta obrigatoriamente ocorrências já encerradas
     * ({@link StatusOcorrencia#RESOLVIDA} ou {@link StatusOcorrencia#CANCELADA}).
     */
    private List<Ocorrencia> buscarOcorrenciasAtivasDoBairro(String bairroLimpo) {
        List<Ocorrencia> candidatas = null;

        Page<Ocorrencia> pagina = ocorrenciaRepository.findWithFilters(
                bairroLimpo, null, null, null, Pageable.unpaged()
        );
        if (pagina != null && pagina.getContent() != null) {
            candidatas = pagina.getContent();
        }

        if (candidatas == null) {
            candidatas = ocorrenciaRepository.findByBairroAndStatusNot(bairroLimpo, StatusOcorrencia.CANCELADA);
        }

        if (candidatas == null || candidatas.isEmpty()) {
            return List.of();
        }

        return candidatas.stream()
                .filter(Objects::nonNull)
                .filter(oc -> oc.getBairro() == null || oc.getBairro().trim().equalsIgnoreCase(bairroLimpo))
                .filter(oc -> oc.getStatus() != StatusOcorrencia.RESOLVIDA
                        && oc.getStatus() != StatusOcorrencia.CANCELADA)
                .toList();
    }

    /**
     * Determina a classificação de risco geral do bairro a partir da maior severidade ativa.
     */
    private String calcularNivelRiscoGeral(List<Ocorrencia> ocorrenciasAtivas) {
        if (ocorrenciasAtivas == null || ocorrenciasAtivas.isEmpty()) {
            return "BAIXO";
        }

        boolean temCritica = ocorrenciasAtivas.stream()
                .anyMatch(oc -> oc.getSeveridade() == SeveridadeOcorrencia.CRITICA);
        if (temCritica) {
            return "CRITICO";
        }

        boolean temAlta = ocorrenciasAtivas.stream()
                .anyMatch(oc -> oc.getSeveridade() == SeveridadeOcorrencia.ALTA);
        if (temAlta) {
            return "ALTO";
        }

        boolean temMedia = ocorrenciasAtivas.stream()
                .anyMatch(oc -> oc.getSeveridade() == SeveridadeOcorrencia.MEDIA);
        if (temMedia) {
            return "MODERADO";
        }

        return "BAIXO";
    }

    /**
     * Extrai a lista de endereços e pontos críticos ativos que a população deve evitar.
     * Caso não existam ocorrências ativas, retorna uma nota informativa garantindo lista não vazia.
     */
    private List<String> extrairPontosCriticosEvitar(String bairroLimpo, List<Ocorrencia> ocorrenciasAtivas) {
        if (ocorrenciasAtivas == null || ocorrenciasAtivas.isEmpty()) {
            return List.of("Nenhum ponto de bloqueio ou interdição ativo registrado no bairro " + bairroLimpo + " no momento.");
        }

        Set<String> pontos = new LinkedHashSet<>();
        for (Ocorrencia oc : ocorrenciasAtivas) {
            String endereco = oc.getEndereco() != null && !oc.getEndereco().isBlank()
                    ? oc.getEndereco().trim()
                    : bairroLimpo;
            String titulo = oc.getTitulo() != null && !oc.getTitulo().isBlank()
                    ? oc.getTitulo().trim()
                    : "Ocorrência em monitoramento";
            String severidade = oc.getSeveridade() != null ? oc.getSeveridade().name() : "ATIVA";
            pontos.add(String.format("%s - %s (%s)", endereco, titulo, severidade));
        }

        if (pontos.isEmpty()) {
            return List.of("Monitoramento preventivo nas vias principais do bairro " + bairroLimpo + ".");
        }
        return new ArrayList<>(pontos);
    }

    /**
     * Gera recomendações práticas de autoproteção à população de acordo com as categorias de risco ativas.
     */
    private List<String> gerarRecomendacoesPopulacao(String nivelRiscoGeral, List<Ocorrencia> ocorrenciasAtivas) {
        if (ocorrenciasAtivas == null || ocorrenciasAtivas.isEmpty()) {
            return List.of(
                    "Situação de normalidade: mantenha o acompanhamento dos alertas oficiais da Defesa Civil (SMS 40199).",
                    "Descarte corretamente resíduos domésticos e não obstrua bueiros ou bocas de lobo.",
                    "Em caso de mudança brusca nas condições climáticas, acione imediatamente o canal 199."
            );
        }

        Set<String> recomendacoes = new LinkedHashSet<>();
        Set<CategoriaOcorrencia> categorias = EnumSet.noneOf(CategoriaOcorrencia.class);
        for (Ocorrencia oc : ocorrenciasAtivas) {
            if (oc.getCategoria() != null) {
                categorias.add(oc.getCategoria());
            }
        }

        if (categorias.contains(CategoriaOcorrencia.ALAGAMENTO) || categorias.contains(CategoriaOcorrencia.ENXURRADA)) {
            recomendacoes.add("Evite trafegar por vias alagadas ou margens de córregos; 30 cm de água em movimento podem arrastar veículos.");
            recomendacoes.add("Não entre em contato com águas de enchente e desligue a chave geral de energia caso a água atinja o imóvel.");
        }
        if (categorias.contains(CategoriaOcorrencia.DESLIZAMENTO) || categorias.contains(CategoriaOcorrencia.EROSAO_SOLO)) {
            recomendacoes.add("Observe sinais de movimentação de solo como rachaduras em paredes, inclinação de postes ou árvores e água barrenta escorrendo.");
            recomendacoes.add("Abandone imediatamente imóveis em áreas de encosta sob risco crítico e procure abrigo seguro.");
        }
        if (categorias.contains(CategoriaOcorrencia.QUEDA_ARVORE)) {
            recomendacoes.add("Mantenha distância de árvores inclinadas e jamais toque em cabos elétricos rompidos na via pública.");
        }
        if (categorias.contains(CategoriaOcorrencia.BUEIRO_OBSTRUIDO)) {
            recomendacoes.add("Redobre a atenção ao caminhar próximo a guias e sarjetas com drenagem obstruída ou tampas deslocadas.");
        }

        recomendacoes.add("Evite deslocamentos pelos pontos críticos listados neste boletim e siga as orientações das equipes em campo.");
        if ("CRITICO".equals(nivelRiscoGeral) || "ALTO".equals(nivelRiscoGeral)) {
            recomendacoes.add("Em situação de emergência ou risco iminente à vida, acione a Defesa Civil (199) ou o Corpo de Bombeiros (193).");
        }

        return new ArrayList<>(recomendacoes);
    }

    /**
     * Elabora o plano tático da Defesa Civil proporcional ao nível de risco identificado no bairro.
     */
    private String gerarPlanoAcaoDefesaCivil(String bairroLimpo, String nivelRiscoGeral, List<Ocorrencia> ocorrenciasAtivas) {
        if (ocorrenciasAtivas == null || ocorrenciasAtivas.isEmpty()) {
            return String.format(
                    "Manter monitoramento meteorológico de rotina e rondas preventivas de zeladoria no bairro %s, sem necessidade de mobilização tática emergencial.",
                    bairroLimpo
            );
        }

        return switch (nivelRiscoGeral) {
            case "CRITICO" -> String.format(
                    "ACIONAMENTO DE CONTINGÊNCIA MÁXIMA em %s: Despacho imediato de equipes de resgate, vistoria geotécnica de encostas, isolamento de perímetros críticos e preparação de abrigos municipais para atendimento às %d ocorrências ativas.",
                    bairroLimpo, ocorrenciasAtivas.size()
            );
            case "ALTO" -> String.format(
                    "MOBILIZAÇÃO TÁTICA PRIORITÁRIA em %s: Envio de viaturas da Defesa Civil, agentes de trânsito para bloqueio preventivo de vias afetadas e equipes operacionais da subprefeitura para mitigação das %d ocorrências ativas.",
                    bairroLimpo, ocorrenciasAtivas.size()
            );
            case "MODERADO" -> String.format(
                    "OPERAÇÃO PREVENTIVA PROGRAMADA em %s: Despacho de equipes de zeladoria e drenagem urbana para vistoria técnica e resolução das %d ocorrências em aberto antes de novos eventos chuvosos.",
                    bairroLimpo, ocorrenciasAtivas.size()
            );
            default -> String.format(
                    "ACOMPANHAMENTO OPERACIONAL em %s: Monitoramento contínuo pela central da Defesa Civil e encaminhamento das %d demandas de baixa criticidade para manutenção municipal.",
                    bairroLimpo, ocorrenciasAtivas.size()
            );
        };
    }

    private BoletimPreventivoResponse gerarBoletimDeterministico(
            String bairroLimpo,
            List<Ocorrencia> ocorrenciasAtivas,
            String nivelRiscoGeral,
            List<String> recomendacoes,
            List<String> pontosCriticos,
            String planoAcao) {

        String resumoSituacao;
        if (ocorrenciasAtivas.isEmpty()) {
            resumoSituacao = String.format(
                    "Boletim Preventivo da Defesa Civil para o bairro %s: Situação de normalidade operacional. Nenhuma ocorrência ativa de risco climático ou zeladoria urbana registrada no momento.",
                    bairroLimpo
            );
        } else {
            Map<SeveridadeOcorrencia, Long> porSeveridade = ocorrenciasAtivas.stream()
                    .filter(oc -> oc.getSeveridade() != null)
                    .collect(Collectors.groupingBy(Ocorrencia::getSeveridade, Collectors.counting()));

            String distribuicao = porSeveridade.entrySet().stream()
                    .map(e -> e.getKey().name() + ": " + e.getValue())
                    .collect(Collectors.joining(", "));

            resumoSituacao = String.format(
                    "Boletim Preventivo da Defesa Civil para o bairro %s: Foram analisadas %d ocorrência(s) ativa(s) classificadas com nível de risco geral %s (distribuição por severidade: %s). Recomenda-se atenção redobrada aos pontos críticos listados.",
                    bairroLimpo,
                    ocorrenciasAtivas.size(),
                    nivelRiscoGeral,
                    distribuicao
            );
        }

        return new BoletimPreventivoResponse(
                bairroLimpo,
                LocalDateTime.now(),
                ocorrenciasAtivas.size(),
                nivelRiscoGeral,
                resumoSituacao,
                recomendacoes,
                pontosCriticos,
                planoAcao,
                MODO_FALLBACK,
                MODELO_FALLBACK
        );
    }

    private String construirPromptDefesaCivil(String bairro, String nivelRisco, List<Ocorrencia> ocorrencias) {
        StringBuilder sb = new StringBuilder();
        sb.append("Você é um especialista da Defesa Civil Municipal atuando no sistema AlertaCom (ODS 11 e ODS 13).\n");
        sb.append("Elabore um resumo executivo técnico, claro e objetivo (em português, máximo de 4 frases) ");
        sb.append("sobre a situação atual do bairro '").append(bairro).append("', ");
        sb.append("cujo nível de risco geral foi classificado como ").append(nivelRisco).append(".\n");
        sb.append("Total de ocorrências ativas no momento: ").append(ocorrencias.size()).append(".\n");

        if (!ocorrencias.isEmpty()) {
            sb.append("Relatos ativos:\n");
            for (Ocorrencia oc : ocorrencias) {
                sb.append("- [").append(oc.getSeveridade()).append(" / ").append(oc.getCategoria()).append("] ")
                  .append(oc.getTitulo()).append(" em ").append(oc.getEndereco())
                  .append(": ").append(oc.getDescricao()).append("\n");
            }
        } else {
            sb.append("Não há ocorrências ativas registradas no momento. Informe situação de normalidade sem inventar incidentes.\n");
        }
        return sb.toString();
    }
}
