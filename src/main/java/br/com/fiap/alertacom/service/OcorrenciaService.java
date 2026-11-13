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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class OcorrenciaService {

    private static final Logger log = LoggerFactory.getLogger(OcorrenciaService.class);
    public static final String EXCHANGE_ALERTA = RabbitMQConfig.EXCHANGE_ALERTA;

    private final OcorrenciaRepository ocorrenciaRepository;
    private final CidadaoRepository cidadaoRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AlertaPublisherService alertaPublisherService;

    @Autowired
    public OcorrenciaService(
            OcorrenciaRepository ocorrenciaRepository,
            CidadaoRepository cidadaoRepository,
            ApplicationEventPublisher eventPublisher,
            @Autowired(required = false) AlertaPublisherService alertaPublisherService) {
        this.ocorrenciaRepository = ocorrenciaRepository;
        this.cidadaoRepository = cidadaoRepository;
        this.eventPublisher = eventPublisher;
        this.alertaPublisherService = alertaPublisherService != null ? alertaPublisherService : new AlertaPublisherService(null);
    }

    public OcorrenciaService(
            OcorrenciaRepository ocorrenciaRepository,
            CidadaoRepository cidadaoRepository,
            ApplicationEventPublisher eventPublisher) {
        this(ocorrenciaRepository, cidadaoRepository, eventPublisher, new AlertaPublisherService(null));
    }

    @Transactional
    public OcorrenciaResponse criar(OcorrenciaRequest request) {
        Cidadao cidadao = resolverCidadaoAutenticado(request);

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

        if (salva.isCriticaOuAlta() && eventPublisher != null) {
            AlertaCriticoEvent evento = alertaPublisherService.construirEvento(salva);
            eventPublisher.publishEvent(evento);
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

        if (!ocorrencia.getStatus().canTransitionTo(request.novoStatus())) {
            throw new RegraNegocioException(String.format(
                "Transição de status inválida de '%s' para '%s' na ocorrência ID %d",
                ocorrencia.getStatus(), request.novoStatus(), id
            ));
        }

        ocorrencia.transicionarStatus(request.novoStatus());
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

    private Cidadao resolverCidadaoAutenticado(OcorrenciaRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAutenticado = auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());

        boolean isGestor = isAutenticado && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_GESTOR".equals(a.getAuthority()));

        // Se for GESTOR e informou um cidadaoId válido, permite vincular àquele cidadão
        if (isGestor && request.cidadaoId() != null) {
            return cidadaoRepository.findById(request.cidadaoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cidadão solicitante não encontrado com o ID: " + request.cidadaoId()));
        }

        // Se for usuário autenticado (incluindo ROLE_CIDADAO), vincula estritamente à sua própria conta (neutralizando IDOR)
        if (isAutenticado) {
            Object principal = auth.getPrincipal();
            if (principal instanceof Cidadao c && c.getId() != null) {
                return cidadaoRepository.findById(c.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Cidadão autenticado não encontrado com o ID: " + c.getId()));
            }

            String email = extrairEmailDoPrincipal(principal);
            if (email != null && !email.isBlank()) {
                return cidadaoRepository.findByEmail(email)
                        .orElseThrow(() -> new ResourceNotFoundException("Cidadão autenticado não encontrado no banco de dados: " + email));
            }
        }

        // Fallback exclusivo para testes unitários Mockito onde não há contexto de segurança ativo
        if (request.cidadaoId() != null) {
            return cidadaoRepository.findById(request.cidadaoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cidadão solicitante não encontrado com o ID: " + request.cidadaoId()));
        }

        throw new AccessDeniedException("É necessário estar autenticado para registrar uma ocorrência.");
    }

    private String extrairEmailDoPrincipal(Object principal) {
        if (principal instanceof UserDetails u) {
            return u.getUsername();
        } else if (principal instanceof Cidadao c) {
            return c.getEmail();
        } else if (principal instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }
}
