package br.com.fiap.alertacom.security;

import br.com.fiap.alertacom.domain.repository.CidadaoRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Implementação do UserDetailsService do Spring Security para carregar os detalhes
 * do usuário a partir da tabela TB_CIDADAO usando o e-mail como username.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final CidadaoRepository cidadaoRepository;

    public UserDetailsServiceImpl(CidadaoRepository cidadaoRepository) {
        this.cidadaoRepository = cidadaoRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Nome de usuário (e-mail) não pode ser nulo ou vazio");
        }
        return cidadaoRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("Cidadão não encontrado com o e-mail: " + username));
    }
}
