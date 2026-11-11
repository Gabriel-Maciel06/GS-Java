package br.com.fiap.alertacom.service;

import br.com.fiap.alertacom.domain.model.Ocorrencia;
import br.com.fiap.alertacom.domain.model.ProtocoloDefesaCivil;
import br.com.fiap.alertacom.domain.repository.OcorrenciaRepository;
import br.com.fiap.alertacom.domain.repository.ProtocoloDefesaCivilRepository;
import br.com.fiap.alertacom.dto.request.ConcluirProtocoloRequest;
import br.com.fiap.alertacom.dto.request.ProtocoloRequest;
import br.com.fiap.alertacom.dto.response.ProtocoloResponse;
import br.com.fiap.alertacom.exception.RegraNegocioException;
import br.com.fiap.alertacom.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class ProtocoloService {

    private final ProtocoloDefesaCivilRepository protocoloRepository;
    private final OcorrenciaRepository ocorrenciaRepository;

    public ProtocoloService(
            ProtocoloDefesaCivilRepository protocoloRepository,
            OcorrenciaRepository ocorrenciaRepository) {
        this.protocoloRepository = protocoloRepository;
        this.ocorrenciaRepository = ocorrenciaRepository;
    }

    @Transactional
    public ProtocoloResponse emitir(ProtocoloRequest request) {
        Ocorrencia ocorrencia = ocorrenciaRepository.findById(request.ocorrenciaId())
            .orElseThrow(() -> new ResourceNotFoundException("Ocorrência não encontrada com o ID: " + request.ocorrenciaId()));

        if (protocoloRepository.existsByOcorrenciaId(request.ocorrenciaId())) {
            throw new RegraNegocioException("A ocorrência ID " + request.ocorrenciaId() + " já possui protocolo oficial da Defesa Civil emitido.");
        }

        String numeroFormatado = gerarNumeroProtocoloUnico();

        ProtocoloDefesaCivil protocolo = ProtocoloDefesaCivil.builder()
            .numeroProtocolo(numeroFormatado)
            .equipeResponsavel(request.equipeResponsavel())
            .parecerTecnico(request.parecerTecnico())
            .dataPrevisaoAtendimento(request.dataPrevisaoAtendimento())
            .ocorrencia(ocorrencia)
            .build();

        ocorrencia.vincularProtocolo(protocolo);
        ProtocoloDefesaCivil salvo = protocoloRepository.save(protocolo);

        return ProtocoloResponse.fromEntity(salvo);
    }

    public ProtocoloResponse buscarPorId(Long id) {
        return protocoloRepository.findByIdWithDetails(id)
            .map(ProtocoloResponse::fromEntity)
            .orElseThrow(() -> new ResourceNotFoundException("Protocolo não encontrado com o ID: " + id));
    }

    public ProtocoloResponse buscarPorNumero(String numeroProtocolo) {
        return protocoloRepository.findByNumeroProtocoloWithDetails(numeroProtocolo)
            .map(ProtocoloResponse::fromEntity)
            .orElseThrow(() -> new ResourceNotFoundException("Protocolo não encontrado com o número: " + numeroProtocolo));
    }

    public Page<ProtocoloResponse> listar(Pageable pageable) {
        return protocoloRepository.findAll(pageable).map(ProtocoloResponse::fromEntity);
    }

    @Transactional
    public ProtocoloResponse concluir(Long id, ConcluirProtocoloRequest request) {
        ProtocoloDefesaCivil protocolo = protocoloRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Protocolo não encontrado com o ID: " + id));

        if (protocolo.getDataConclusao() != null) {
            throw new RegraNegocioException("Protocolo ID " + id + " já foi concluído anteriormente em: " + protocolo.getDataConclusao());
        }

        protocolo.concluirAtendimento(request.parecerFinal());
        ProtocoloDefesaCivil salvo = protocoloRepository.save(protocolo);

        return ProtocoloResponse.fromEntity(salvo);
    }

    private synchronized String gerarNumeroProtocoloUnico() {
        int ano = LocalDateTime.now().getYear();
        long totalExistentes = protocoloRepository.count();
        long sequencial = totalExistentes + 1;
        String numero = String.format("DEF-%d-%05d", ano, sequencial);

        while (protocoloRepository.existsByNumeroProtocolo(numero)) {
            sequencial++;
            numero = String.format("DEF-%d-%05d", ano, sequencial);
        }
        return numero;
    }
}
