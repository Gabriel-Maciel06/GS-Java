package br.com.fiap.alertacom.service;

import br.com.fiap.alertacom.domain.model.Cidadao;
import br.com.fiap.alertacom.domain.repository.CidadaoRepository;
import br.com.fiap.alertacom.dto.request.AtualizarCidadaoRequest;
import br.com.fiap.alertacom.dto.request.CidadaoRequest;
import br.com.fiap.alertacom.dto.response.CidadaoResponse;
import br.com.fiap.alertacom.exception.DuplicateResourceException;
import br.com.fiap.alertacom.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CidadaoService {

    private final CidadaoRepository cidadaoRepository;
    private final PasswordEncoder passwordEncoder;

    public CidadaoService(CidadaoRepository cidadaoRepository, PasswordEncoder passwordEncoder) {
        this.cidadaoRepository = cidadaoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public CidadaoResponse cadastrar(CidadaoRequest request) {
        if (cidadaoRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("E-mail já cadastrado na plataforma: " + request.email());
        }

        String senhaCriptografada = passwordEncoder.encode(request.senha());

        Cidadao cidadao = new Cidadao(
            request.nome(),
            request.email(),
            request.telefone(),
            request.bairro(),
            senhaCriptografada,
            request.role()
        );

        Cidadao salvo = cidadaoRepository.save(cidadao);
        return CidadaoResponse.fromEntity(salvo);
    }

    public Page<CidadaoResponse> listar(Pageable pageable) {
        return cidadaoRepository.findAll(pageable).map(CidadaoResponse::fromEntity);
    }

    public CidadaoResponse buscarPorId(Long id) {
        return cidadaoRepository.findById(id)
            .map(CidadaoResponse::fromEntity)
            .orElseThrow(() -> new ResourceNotFoundException("Cidadão não encontrado com o ID: " + id));
    }

    @Transactional
    public CidadaoResponse atualizar(Long id, AtualizarCidadaoRequest request) {
        Cidadao cidadao = cidadaoRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Cidadão não encontrado com o ID: " + id));

        cidadao.setNome(request.nome());
        if (request.telefone() != null) {
            cidadao.setTelefone(request.telefone());
        }
        cidadao.setBairro(request.bairro());

        Cidadao atualizado = cidadaoRepository.save(cidadao);
        return CidadaoResponse.fromEntity(atualizado);
    }

    @Transactional
    public void desativar(Long id) {
        Cidadao cidadao = cidadaoRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Cidadão não encontrado com o ID: " + id));
        cidadao.setAtivo(Boolean.FALSE);
        cidadaoRepository.save(cidadao);
    }

    public Long buscarIdPorEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return cidadaoRepository.findByEmail(email)
                .map(Cidadao::getId)
                .orElse(null);
    }
}
