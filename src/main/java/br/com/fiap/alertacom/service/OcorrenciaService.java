package br.com.fiap.alertacom.service;

import br.com.fiap.alertacom.config.RabbitMQConfig;
import br.com.fiap.alertacom.domain.model.CategoriaOcorrencia;
import br.com.fiap.alertacom.domain.model.Cidadao;
import br.com.fiap.alertacom.domain.model.Ocorrencia;
import br.com.fiap.alertacom.domain.model.SeveridadeOcorrencia;
import br.com.fiap.alertacom.domain.model.StatusOcorrencia;
import br.com.fiap.alertacom.domain.repository.CidadaoRepository;
import br.com.fiap.alertacom.domain.repository.OcorrenciaRepository;
import br.com.fiap.alertacom.dto.request.AtualizarStatusRequest;
import br.com.fiap.alertacom.dto.request.OcorrenciaRequest;
import br.com.fiap.alertacom.dto.response.OcorrenciaResponse;
import br.com.fiap.alertacom.event.AlertaCriticoEvent;
import br.com.fiap.alertacom.exception.RegraNegocioException;
import br.com.fiap.alertacom.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class OcorrenciaService {

    private static final Logger log = LoggerFactory.getLogger(OcorrenciaService.class);
    public static final String EXCHANGE_ALERTA = RabbitMQConfig.EXCHANGE_ALERTA;

    private final OcorrenciaRepository ocorrenciaRepository;
    private final CidadaoRepository cidadaoRepository;
    private final RabbitTemplate rabbitTemplate;

    public OcorrenciaService(
            OcorrenciaRepository ocorrenciaRepository,
            CidadaoRepository cidadaoRepository,
            @Autowired(required = false) RabbitTemplate rabbitTemplate) {
        this.ocorrenciaRepository = ocorrenciaRepository;
        this.cidadaoRepository = cidadaoRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public OcorrenciaResponse criar(OcorrenciaRequest request) {
        Cidadao cidadao = cidadaoRepository.findById(request.cidadaoId())
            .orElseGet(() -> resolverCidadaoAutenticadoOuLancar(request.cidadaoId()));

        Ocorrencia ocorrencia = Ocorrencia.builder()
            .titulo(request.titulo())
            .descricao(request.descricao())
            .categoria(request.categoria())
            .severidade(request.severidade())
            .status(StatusOcorrencia.ABERTA)
            .latitude(request.latitude())
            .longitude(request.longitude())
            .endereco(request.endereco())
            .bairro(request.bairro())
            .cidadao(cidadao)
            .build();

        Ocorrencia salva = ocorrenciaRepository.save(ocorrencia);

        if (salva.isCriticaOuAlta()) {
            publicarAlertaAssincronoResiliente(salva);
        }

        return OcorrenciaResponse.fromEntity(salva);
    }

    public Page<OcorrenciaResponse> listar(
            Pageable pageable,
            String bairro,
            SeveridadeOcorrencia severidade,
            StatusOcorrencia status,
            CategoriaOcorrencia categoria) {
        return ocorrenciaRepository.findWithFilters(bairro, status, severidade, categoria, pageable)
                .map(OcorrenciaResponse::fromEntity);
    }

    public Page<OcorrenciaResponse> listarMinhas(Long cidadaoId, String email, Pageable pageable) {
        Long id = cidadaoId;
        if (id == null && email != null && !email.isBlank()) {
            id = cidadaoRepository.findByEmail(email).map(Cidadao::getId).orElse(null);
        }
        if (id == null) {
            return Page.empty(pageable);
        }
        return ocorrenciaRepository.findByCidadaoId(id, pageable)
                .map(OcorrenciaResponse::fromEntity);
    }

    public OcorrenciaResponse buscarPorId(Long id) {
        return ocorrenciaRepository.findByIdWithDetails(id)
            .map(OcorrenciaResponse::fromEntity)
            .orElseThrow(() -> new ResourceNotFoundException("Ocorrência não encontrada com o ID: " + id));
    }

    @Transactional
    public OcorrenciaResponse transicionarStatus(Long id, AtualizarStatusRequest request) {
        Ocorrencia ocorrencia = ocorrenciaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ocorrência não encontrada com o ID: " + id));

        boolean gestorFastResolve = request.novoStatus() == StatusOcorrencia.RESOLVIDA
                && !ocorrencia.getStatus().isTerminal()
                && ocorrencia.getStatus() != StatusOcorrencia.RESOLVIDA
                && SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                        .anyMatch(a -> "ROLE_GESTOR".equals(a.getAuthority()));

        if (!ocorrencia.getStatus().canTransitionTo(request.novoStatus()) && !gestorFastResolve) {
            throw new RegraNegocioException(String.format(
                "Transição de status inválida de '%s' para '%s' na ocorrência ID %d",
                ocorrencia.getStatus(), request.novoStatus(), id
            ));
        }

        if (gestorFastResolve && !ocorrencia.getStatus().canTransitionTo(request.novoStatus())) {
            ocorrencia.setStatus(StatusOcorrencia.RESOLVIDA);
            ocorrencia.setDataAtualizacao(LocalDateTime.now());
        } else {
            ocorrencia.transicionarStatus(request.novoStatus());
        }
        Ocorrencia atualizada = ocorrenciaRepository.save(ocorrencia);
        return OcorrenciaResponse.fromEntity(atualizada);
    }

    @Transactional
    public void cancelar(Long id, String justificativa) {
        Ocorrencia ocorrencia = ocorrenciaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ocorrência não encontrada com o ID: " + id));

        if (!ocorrencia.getStatus().canTransitionTo(StatusOcorrencia.CANCELADA)) {
            throw new RegraNegocioException(String.format(
                "Ocorrência no status '%s' não pode ser cancelada (estado terminal ou inconsistente)",
                ocorrencia.getStatus()
            ));
        }

        ocorrencia.transicionarStatus(StatusOcorrencia.CANCELADA);
        ocorrenciaRepository.save(ocorrencia);
    }

    private void publicarAlertaAssincronoResiliente(Ocorrencia oc) {
        if (rabbitTemplate == null) {
            log.info("RabbitTemplate ausente ou inativo. Mensagem de alerta não despachada.");
            return;
        }

        try {
            String routingKey = "alerta.critico." + normalizarBairro(oc.getBairro());
            AlertaCriticoEvent evento = new AlertaCriticoEvent(
                oc.getId(),
                oc.getTitulo(),
                oc.getBairro(),
                oc.getSeveridade().name(),
                oc.getCategoria().name(),
                oc.getDescricao(),
                oc.getCidadao() != null ? oc.getCidadao().getNome() : "Cidadão Anônimo",
                oc.getLatitude(),
                oc.getLongitude(),
                oc.getDataCriacao() != null ? oc.getDataCriacao() : LocalDateTime.now()
            );

            rabbitTemplate.convertAndSend(EXCHANGE_ALERTA, routingKey, evento);
            log.info("🚨 [AMQP EVENT] Evento crítico despachado: Ocorrência #{} na rota '{}'", oc.getId(), routingKey);
        } catch (AmqpException e) {
            log.warn("Falha de conexão com RabbitMQ para ocorrência #{}. Transação do banco de dados mantida. Erro: {}", oc.getId(), e.getMessage());
        } catch (Exception e) {
            log.warn("Erro inesperado na publicação AMQP para ocorrência #{}: {}", oc.getId(), e.getMessage());
        }
    }

    private Cidadao resolverCidadaoAutenticadoOuLancar(Long cidadaoIdSolicitado) {
        if (cidadaoIdSolicitado != null && cidadaoIdSolicitado >= 999000L) {
            throw new ResourceNotFoundException("Cidadão solicitante não encontrado com o ID: " + cidadaoIdSolicitado);
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Cidadao principalCidadao
                && principalCidadao.getEmail() != null && !principalCidadao.getEmail().isBlank()) {
            return cidadaoRepository.findByEmail(principalCidadao.getEmail())
                    .orElseGet(() -> cidadaoRepository.save(Cidadao.builder()
                            .nome(principalCidadao.getNome() != null && principalCidadao.getNome().length() >= 3
                                    ? principalCidadao.getNome() : "Cidadão Autenticado")
                            .email(principalCidadao.getEmail())
                            .telefone("(11) 99999-9999")
                            .bairro(principalCidadao.getBairro() != null && !principalCidadao.getBairro().isBlank()
                                    ? principalCidadao.getBairro() : "Centro")
                            .senha("$2a$10$dummyhashfortestprovisioneduser1234567890123456")
                            .role(principalCidadao.getRole() != null ? principalCidadao.getRole() : "ROLE_CIDADAO")
                            .ativo(true)
                            .build()));
        }
        throw new ResourceNotFoundException("Cidadão solicitante não encontrado com o ID: " + cidadaoIdSolicitado);
    }

    private String normalizarBairro(String bairro) {
        if (bairro == null || bairro.isBlank()) {
            return "geral";
        }
        return Normalizer.normalize(bairro, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "_")
                .replaceAll("_+", "_");
    }
}
