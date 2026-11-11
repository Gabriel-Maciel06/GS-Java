package br.com.fiap.alertacom.domain;

import br.com.fiap.alertacom.domain.model.*;
import br.com.fiap.alertacom.domain.repository.CidadaoRepository;
import br.com.fiap.alertacom.domain.repository.OcorrenciaRepository;
import br.com.fiap.alertacom.domain.repository.ProtocoloDefesaCivilRepository;
import org.hibernate.proxy.HibernateProxy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Adversarial challenger test suite for Milestone 1.
 * Tests:
 * 1. 3-level cascade deletion (Cidadao -> Ocorrencia -> ProtocoloDefesaCivil).
 * 2. orphanRemoval on 1:N (Cidadao.ocorrencias) and 1:1 (Ocorrencia.protocolo).
 * 3. Vlad Mihalcea equals/hashCode contract across entity lifecycle (transient, persisted, detached, proxy).
 */
@DataJpaTest
@ActiveProfiles("test")
class AdversarialMilestone1DomainTest {

    @Autowired
    private CidadaoRepository cidadaoRepository;

    @Autowired
    private OcorrenciaRepository ocorrenciaRepository;

    @Autowired
    private ProtocoloDefesaCivilRepository protocoloRepository;

    @Autowired
    private TestEntityManager entityManager;

    // =========================================================================
    // 1. STRESS TESTS: CASCADE DELETION (3 NÍVEIS)
    // =========================================================================

    @Test
    @DisplayName("ADV-CAS-1: Excluir Cidadao deve deletar em cascata Ocorrencia E ProtocoloDefesaCivil (3 níveis)")
    void testCascadeDeleteTresNiveis() {
        Cidadao cidadao = new Cidadao("Munícipe Cascata", "cascata@fiap.com.br", "11988887777", "Morumbi", "hash", "ROLE_CIDADAO");
        Ocorrencia oc = new Ocorrencia("Desmoronamento", "Talude cedeu", CategoriaOcorrencia.DESLIZAMENTO,
                SeveridadeOcorrencia.CRITICA, -23.59, -46.68, "Rua do Morro, 10", "Morumbi", cidadao);
        cidadao.addOcorrencia(oc);

        ProtocoloDefesaCivil protocolo = new ProtocoloDefesaCivil("DEF-2026-77701", "Equipe Tática 1",
                "Isolamento da encosta", LocalDateTime.now().plusHours(2), oc);
        oc.vincularProtocolo(protocolo);

        // Salva a árvore inteira a partir da raiz (Cidadao)
        Cidadao salvo = cidadaoRepository.saveAndFlush(cidadao);
        Long cidadaoId = salvo.getId();
        Long ocorrenciaId = salvo.getOcorrencias().get(0).getId();
        Long protocoloId = salvo.getOcorrencias().get(0).getProtocolo().getId();

        entityManager.clear();

        // Verifica existência prévia dos 3 registros
        assertThat(cidadaoRepository.findById(cidadaoId)).isPresent();
        assertThat(ocorrenciaRepository.findById(ocorrenciaId)).isPresent();
        assertThat(protocoloRepository.findById(protocoloId)).isPresent();

        // Executa exclusão da raiz (Cidadao)
        Cidadao paraExcluir = cidadaoRepository.findById(cidadaoId).orElseThrow();
        cidadaoRepository.delete(paraExcluir);
        entityManager.flush();
        entityManager.clear();

        // Valida que os 3 níveis foram completamente eliminados do banco
        assertThat(cidadaoRepository.findById(cidadaoId)).isEmpty();
        assertThat(ocorrenciaRepository.findById(ocorrenciaId)).isEmpty();
        assertThat(protocoloRepository.findById(protocoloId)).isEmpty();
    }

    // =========================================================================
    // 2. STRESS TESTS: orphanRemoval EM COLEÇÕES E RELACIONAMENTOS 1:1
    // =========================================================================

    @Test
    @DisplayName("ADV-ORP-1: Remover Ocorrencia da lista de Cidadao deve acionar orphanRemoval e cascata para Protocolo")
    void testOrphanRemovalRemoverItemColecao() {
        Cidadao cidadao = new Cidadao("Orfao Test", "orfao@fiap.com.br", "11999991111", "Mooca", "pwd", "ROLE_CIDADAO");

        Ocorrencia oc1 = new Ocorrencia("Oc 1", "Desc 1", CategoriaOcorrencia.ALAGAMENTO, SeveridadeOcorrencia.ALTA, null, null, "Rua A", "Mooca", cidadao);
        Ocorrencia oc2 = new Ocorrencia("Oc 2", "Desc 2", CategoriaOcorrencia.BUEIRO_OBSTRUIDO, SeveridadeOcorrencia.MEDIA, null, null, "Rua B", "Mooca", cidadao);
        cidadao.addOcorrencia(oc1);
        cidadao.addOcorrencia(oc2);

        ProtocoloDefesaCivil prot1 = new ProtocoloDefesaCivil("DEF-2026-88801", "Equipe Alfa", "Parecer", null, oc1);
        oc1.vincularProtocolo(prot1);

        Cidadao salvo = cidadaoRepository.saveAndFlush(cidadao);
        Long oc1Id = oc1.getId();
        Long oc2Id = oc2.getId();
        Long prot1Id = prot1.getId();

        entityManager.clear();

        // Recarrega Cidadao e remove apenas oc1
        Cidadao recarregado = cidadaoRepository.findById(salvo.getId()).orElseThrow();
        Ocorrencia oc1ParaRemover = recarregado.getOcorrencias().stream()
                .filter(o -> o.getId().equals(oc1Id))
                .findFirst().orElseThrow();

        recarregado.removeOcorrencia(oc1ParaRemover);
        cidadaoRepository.saveAndFlush(recarregado);
        entityManager.clear();

        // oc1 e seu protocolo devem ter sido removidos por orphanRemoval
        assertThat(ocorrenciaRepository.findById(oc1Id)).isEmpty();
        assertThat(protocoloRepository.findById(prot1Id)).isEmpty();

        // oc2 e cidadao devem permanecer intactos
        assertThat(ocorrenciaRepository.findById(oc2Id)).isPresent();
        assertThat(cidadaoRepository.findById(salvo.getId())).isPresent();
    }

    @Test
    @DisplayName("ADV-ORP-2: Limpar ocorrencias (clear) de Cidadao deve deletar todas ocorrencias e protocolos órfãos")
    void testOrphanRemovalLimparColecao() {
        Cidadao cidadao = new Cidadao("Clear Test", "clear@fiap.com.br", "11999992222", "Ipiranga", "pwd", "ROLE_CIDADAO");

        Ocorrencia oc1 = new Ocorrencia("Oc A", "Desc A", CategoriaOcorrencia.ENXURRADA, SeveridadeOcorrencia.ALTA, null, null, "Rua A", "Ipiranga", cidadao);
        Ocorrencia oc2 = new Ocorrencia("Oc B", "Desc B", CategoriaOcorrencia.QUEDA_ARVORE, SeveridadeOcorrencia.MEDIA, null, null, "Rua B", "Ipiranga", cidadao);
        cidadao.addOcorrencia(oc1);
        cidadao.addOcorrencia(oc2);

        ProtocoloDefesaCivil prot1 = new ProtocoloDefesaCivil("DEF-2026-88802", "Eq 1", "P1", null, oc1);
        ProtocoloDefesaCivil prot2 = new ProtocoloDefesaCivil("DEF-2026-88803", "Eq 2", "P2", null, oc2);
        oc1.vincularProtocolo(prot1);
        oc2.vincularProtocolo(prot2);

        cidadaoRepository.saveAndFlush(cidadao);
        Long oc1Id = oc1.getId();
        Long oc2Id = oc2.getId();
        Long prot1Id = prot1.getId();
        Long prot2Id = prot2.getId();

        entityManager.clear();

        // Limpa a coleção inteira
        Cidadao recarregado = cidadaoRepository.findById(cidadao.getId()).orElseThrow();
        recarregado.getOcorrencias().clear();
        cidadaoRepository.saveAndFlush(recarregado);
        entityManager.clear();

        // Ambas ocorrencias e ambos protocolos devem estar excluídos
        assertThat(ocorrenciaRepository.findById(oc1Id)).isEmpty();
        assertThat(ocorrenciaRepository.findById(oc2Id)).isEmpty();
        assertThat(protocoloRepository.findById(prot1Id)).isEmpty();
        assertThat(protocoloRepository.findById(prot2Id)).isEmpty();

        // Cidadão deve continuar existindo com lista de ocorrências vazia
        Cidadao cidadaoFinal = cidadaoRepository.findById(cidadao.getId()).orElseThrow();
        assertThat(cidadaoFinal.getOcorrencias()).isEmpty();
    }

    @Test
    @DisplayName("ADV-ORP-3: Desvincular Protocolo em Ocorrencia (desvincularProtocolo) deve acionar orphanRemoval no 1:1")
    void testOrphanRemovalOneToOneDesvincular() {
        Cidadao cidadao = new Cidadao("OneToOne Test", "1to1@fiap.com.br", "11999993333", "Santana", "pwd", "ROLE_CIDADAO");
        Ocorrencia oc = new Ocorrencia("Buraco Crítico", "Desc", CategoriaOcorrencia.EROSAO_SOLO, SeveridadeOcorrencia.CRITICA, null, null, "Rua C", "Santana", cidadao);
        cidadao.addOcorrencia(oc);
        ProtocoloDefesaCivil prot = new ProtocoloDefesaCivil("DEF-2026-88804", "Eq Tática", "Parecer", null, oc);
        oc.vincularProtocolo(prot);

        cidadaoRepository.saveAndFlush(cidadao);
        Long ocId = oc.getId();
        Long protId = prot.getId();

        entityManager.clear();

        // Desvincula o protocolo na ocorrência
        Ocorrencia ocCarregada = ocorrenciaRepository.findById(ocId).orElseThrow();
        assertThat(ocCarregada.getProtocolo()).isNotNull();

        ocCarregada.desvincularProtocolo();
        ocorrenciaRepository.saveAndFlush(ocCarregada);
        entityManager.clear();

        // Protocolo deve ter sido removido do banco
        assertThat(protocoloRepository.findById(protId)).isEmpty();

        // Ocorrência permanece viva sem protocolo
        Ocorrencia ocSemProtocolo = ocorrenciaRepository.findById(ocId).orElseThrow();
        assertThat(ocSemProtocolo.getProtocolo()).isNull();
    }

    // =========================================================================
    // 3. STRESS TESTS: VLAD MIHALCEA EQUALS & HASHCODE CONTRACT
    // =========================================================================

    @Test
    @DisplayName("ADV-VM-1: Entidade transiente adicionada a HashSet deve ser encontrada após persist (ID gerado)")
    void testVladMihalceaHashSetAposPersist() {
        Cidadao transiente = new Cidadao("Vlad Test", "vlad.set@fiap.com.br", "11999994444", "Perus", "pwd", "ROLE_CIDADAO");

        Set<Cidadao> conjunto = new HashSet<>();
        conjunto.add(transiente);

        // No estado transiente (id == null), deve estar no conjunto
        assertThat(conjunto.contains(transiente)).isTrue();
        int hashInicial = transiente.hashCode();

        // Persiste a entidade no banco de dados, atribuindo ID gerado (ex: 1L)
        entityManager.persistAndFlush(transiente);
        assertThat(transiente.getId()).isNotNull();

        // O hashCode NÃO pode mudar após a persistência (regra de ouro Vlad Mihalcea)
        assertThat(transiente.hashCode()).isEqualTo(hashInicial);

        // O conjunto DEVE continuar encontrando a entidade mesmo com o ID agora preenchido
        assertThat(conjunto.contains(transiente))
                .withFailMessage("Violação Vlad Mihalcea: Entidade foi perdida no HashSet após transição transiente -> persistida!")
                .isTrue();

        // Adicionar a mesma entidade persistida novamente não deve duplicar no Set
        conjunto.add(transiente);
        assertThat(conjunto).hasSize(1);
    }

    @Test
    @DisplayName("ADV-VM-2: Entidade transiente usada como chave em HashMap deve ser recuperada após persist")
    void testVladMihalceaHashMapAposPersist() {
        Cidadao cidadao = new Cidadao("Munícipe Map", "map.municipe@fiap.com.br", "11999995555", "Tatuapé", "pwd", "ROLE_CIDADAO");
        entityManager.persistAndFlush(cidadao);

        Ocorrencia transiente = new Ocorrencia("Alagamento Map", "Desc", CategoriaOcorrencia.ALAGAMENTO,
                SeveridadeOcorrencia.ALTA, null, null, "Rua Map", "Tatuapé", cidadao);

        Map<Ocorrencia, String> mapa = new HashMap<>();
        mapa.put(transiente, "VALOR_MONITORAMENTO");

        // Transiente: deve ser recuperável
        assertThat(mapa.get(transiente)).isEqualTo("VALOR_MONITORAMENTO");
        int hashAntes = transiente.hashCode();

        // Persiste
        entityManager.persistAndFlush(transiente);
        assertThat(transiente.getId()).isNotNull();

        // HashCode imutável
        assertThat(transiente.hashCode()).isEqualTo(hashAntes);

        // Recuperável no mapa
        assertThat(mapa.get(transiente))
                .withFailMessage("Violação Vlad Mihalcea: Chave transiente não foi localizada no HashMap após persistência!")
                .isEqualTo("VALOR_MONITORAMENTO");
    }

    @Test
    @DisplayName("ADV-VM-3: Duas instâncias transientes distintas (id == null) não devem ser consideradas iguais")
    void testVladMihalceaInstanciasTransientesDistintas() {
        Cidadao c1 = new Cidadao("C1", "c1@fiap.com.br", "1191", "Centro", "pwd", "ROLE_CIDADAO");
        Cidadao c2 = new Cidadao("C2", "c2@fiap.com.br", "1192", "Centro", "pwd", "ROLE_CIDADAO");

        // Ambas têm id == null
        assertThat(c1.getId()).isNull();
        assertThat(c2.getId()).isNull();

        // Duas instâncias transientes distintas na memória não são iguais
        assertThat(c1.equals(c2)).isFalse();
        assertThat(c2.equals(c1)).isFalse();

        // Adicionadas ao HashSet, devem gerar 2 elementos distintos
        Set<Cidadao> set = new HashSet<>();
        set.add(c1);
        set.add(c2);
        assertThat(set).hasSize(2);
    }

    @Test
    @DisplayName("ADV-VM-4: Duas instâncias separadas com mesmo ID não-nulo devem ser iguais e colidir no Set")
    void testVladMihalceaInstanciasPersistidasMesmoId() {
        Cidadao c1 = new Cidadao(100L, "Instancia 1", "email1@fiap.com.br", "1191", "Sé", "pwd", "ROLE_CIDADAO", true);
        Cidadao c2 = new Cidadao(100L, "Instancia 2", "email2@fiap.com.br", "1192", "Sé", "pwd", "ROLE_CIDADAO", true);

        // Devem ser consideradas iguais pois possuem o mesmo ID persistido
        assertThat(c1.equals(c2)).isTrue();
        assertThat(c2.equals(c1)).isTrue();
        assertThat(c1.hashCode()).isEqualTo(c2.hashCode());

        Set<Cidadao> set = new HashSet<>();
        set.add(c1);
        set.add(c2);
        assertThat(set).hasSize(1);
    }

    @Test
    @DisplayName("ADV-VM-5: Comparação de entidade persistida com Hibernate Proxy (Lazy loading)")
    void testVladMihalceaEqualsComHibernateProxy() {
        Cidadao c = new Cidadao("Proxy Test", "proxy@fiap.com.br", "11999996666", "Mooca", "pwd", "ROLE_CIDADAO");
        entityManager.persistAndFlush(c);
        Long id = c.getId();

        entityManager.clear();

        // Obtém um proxy não inicializado via getReference
        Cidadao proxy = entityManager.getEntityManager().getReference(Cidadao.class, id);

        // Verifica que é de fato um proxy Hibernate
        assertThat(proxy).isInstanceOf(HibernateProxy.class);

        // Instância real desacoplada com mesmo ID
        Cidadao realComMesmoId = new Cidadao(id, "Proxy Test", "proxy@fiap.com.br", "11999996666", "Mooca", "pwd", "ROLE_CIDADAO", true);

        // Avaliação empírica da compatibilidade proxy vs entidade real:
        boolean proxyEqualsReal = proxy.equals(realComMesmoId);
        boolean realEqualsProxy = realComMesmoId.equals(proxy);

        System.out.println("Empirical Check - proxy.equals(real): " + proxyEqualsReal);
        System.out.println("Empirical Check - real.equals(proxy): " + realEqualsProxy);
        System.out.println("Proxy class: " + proxy.getClass().getName());
        System.out.println("Real class: " + realComMesmoId.getClass().getName());
        System.out.println("Proxy hashCode: " + proxy.hashCode());
        System.out.println("Real hashCode: " + realComMesmoId.hashCode());

        // Teste de simetria do equals (Object.equals contract)
        // Se proxy.equals(real) != real.equals(proxy), a simetria está quebrada!
        Set<Cidadao> setComProxy = new HashSet<>();
        setComProxy.add(proxy);
        boolean setContainsReal = setComProxy.contains(realComMesmoId);
        System.out.println("Set com proxy contém real: " + setContainsReal);

        Set<Cidadao> setComReal = new HashSet<>();
        setComReal.add(realComMesmoId);
        boolean setContainsProxy = setComReal.contains(proxy);
        System.out.println("Set com real contém proxy: " + setContainsProxy);

        // Asserção do contrato de simetria de java.lang.Object.equals:
        // Se a implementação usa getClass() != o.getClass(), real.equals(proxy) falha com false!
        assertTrue(proxyEqualsReal, "proxy.equals(real) deve ser true");
        assertTrue(realEqualsProxy, 
                "VIOLAÇÃO DO CONTRATO VLAD MIHALCEA / EQUALS SYMMETRY: real.equals(proxy) retornou false! " +
                "Necessário substituir 'getClass() != o.getClass()' por '!(o instanceof Cidadao)' e usar 'getId()'.");

        // Prova empírica: HashMap deve recuperar elemento quando a chave inserida é o proxy e a busca usa a entidade real
        Map<Cidadao, String> mapaComProxy = new HashMap<>();
        mapaComProxy.put(proxy, "PAYLOAD");
        String valorRecuperadoComReal = mapaComProxy.get(realComMesmoId);
        assertEquals("PAYLOAD", valorRecuperadoComReal,
                "VIOLAÇÃO DE HASHMAP: Retornou null na busca por entidade real porque real.equals(proxy) é false!");
    }

    @Test
    @DisplayName("ADV-VM-6: Quebra empírica de simetria do equals em Ocorrencia com Hibernate Proxy")
    void testVladMihalceaEqualsOcorrenciaComProxy() {
        Cidadao c = new Cidadao("Mun", "mun.proxy@fiap.com.br", "11999997777", "Sé", "pwd", "ROLE_CIDADAO");
        Ocorrencia oc = new Ocorrencia("Alagamento Px", "Desc", CategoriaOcorrencia.ALAGAMENTO, SeveridadeOcorrencia.ALTA, null, null, "Rua Px", "Sé", c);
        c.addOcorrencia(oc);
        cidadaoRepository.saveAndFlush(c);
        Long ocId = oc.getId();

        entityManager.clear();

        Ocorrencia proxy = entityManager.getEntityManager().getReference(Ocorrencia.class, ocId);
        assertThat(proxy).isInstanceOf(HibernateProxy.class);

        Ocorrencia realComMesmoId = new Ocorrencia();
        realComMesmoId.setId(ocId);

        boolean proxyEqualsReal = proxy.equals(realComMesmoId);
        boolean realEqualsProxy = realComMesmoId.equals(proxy);

        System.out.println("Ocorrencia proxy.equals(real): " + proxyEqualsReal);
        System.out.println("Ocorrencia real.equals(proxy): " + realEqualsProxy);

        assertTrue(proxyEqualsReal, "Ocorrencia proxy.equals(real) deve ser true");
        assertTrue(realEqualsProxy,
                "VIOLAÇÃO DE SIMETRIA EM OCORRENCIA: real.equals(proxy) retornou false devido a getClass() != o.getClass()!");
    }

    @Test
    @DisplayName("ADV-VM-7: Quebra empírica de simetria do equals em ProtocoloDefesaCivil com Hibernate Proxy")
    void testVladMihalceaEqualsProtocoloComProxy() {
        Cidadao c = new Cidadao("Mun Prot", "mun.prot@fiap.com.br", "11999998888", "Sé", "pwd", "ROLE_CIDADAO");
        Ocorrencia oc = new Ocorrencia("Deslizamento Px", "Desc", CategoriaOcorrencia.DESLIZAMENTO, SeveridadeOcorrencia.CRITICA, null, null, "Rua Px", "Sé", c);
        c.addOcorrencia(oc);
        ProtocoloDefesaCivil prot = new ProtocoloDefesaCivil("DEF-2026-66601", "Eq Px", "Parecer Px", null, oc);
        oc.vincularProtocolo(prot);
        cidadaoRepository.saveAndFlush(c);
        Long protId = prot.getId();

        entityManager.clear();

        ProtocoloDefesaCivil proxy = entityManager.getEntityManager().getReference(ProtocoloDefesaCivil.class, protId);
        assertThat(proxy).isInstanceOf(HibernateProxy.class);

        ProtocoloDefesaCivil realComMesmoId = new ProtocoloDefesaCivil();
        realComMesmoId.setId(protId);

        boolean proxyEqualsReal = proxy.equals(realComMesmoId);
        boolean realEqualsProxy = realComMesmoId.equals(proxy);

        System.out.println("Protocolo proxy.equals(real): " + proxyEqualsReal);
        System.out.println("Protocolo real.equals(proxy): " + realEqualsProxy);

        assertTrue(proxyEqualsReal, "Protocolo proxy.equals(real) deve ser true");
        assertTrue(realEqualsProxy,
                "VIOLAÇÃO DE SIMETRIA EM PROTOCOLO: real.equals(proxy) retornou false devido a getClass() != o.getClass()!");
    }
}
