# 🎬 Roteiro Oficial do Vídeo Pitch (5 Minutos) — AlertaCom CitizenTech

> **Global Solution 2026/2 — FIAP | Disciplina: Java Advanced**  
> **Projeto:** AlertaCom — Plataforma CitizenTech de Resiliência Urbana e Defesa Civil  
> **Aluno Responsável:** **Gabriel Maciel Alves de Oliveira — RM 562795 (Turma 2TDSR)**  
> **Duração Alvo:** **4 min 50 seg** (Teto máximo estrito da FIAP: **5 min 00 seg**)  
> **Link do Vídeo no YouTube:** `[INSERIR_LINK_YOUTUBE_NAO_LISTADO_OU_PUBLICO_AQUI]`

---

## ⚠️ Regras de Ouro da Banca FIAP para a Gravação

1. **Limite Estrito de 5 Minutos:** O vídeo **não pode ultrapassar 5:00** sob hipótese alguma. Este roteiro foi cronometrado para fechar em **4:50**, deixando 10 segundos de margem de segurança.
2. **Locução por Voz Obrigatória:** Toda a explicação deve ser narrada por voz clara e firme (não substitua a fala por legendas ou música de fundo).
3. **Demonstração Técnica Real em Tela:** Durante o Bloco 3 (1:40 – 3:40), mostre o sistema funcionando de verdade (Swagger UI, autenticação JWT, abertura de ocorrência crítica, disparo no RabbitMQ, protocolo da Defesa Civil e boletim gerado pela Spring AI).
4. **Visibilidade no YouTube:** Publique como **Não Listado** ou **Público** (nunca Privado, pois a banca não conseguirá abrir).

---

## ✅ Checklist Pré-Gravação (Preparação do Ambiente em 3 Minutos)

Antes de apertar `REC` no OBS Studio / QuickTime / Loom:

- [ ] **Resolução e Fonte:** Tela em `1920x1080` (Full HD) e zoom do navegador em **125%** para que o código e o JSON fiquem perfeitamente legíveis no vídeo.
- [ ] **Subir a Aplicação e Infraestrutura:**
  - Com Docker completo: `docker compose up -d --build`
  - Ou local rápido + RabbitMQ: `mvn spring-boot:run`
- [ ] **Deixar 4 Abas Abertas no Navegador (na ordem do roteiro):**
  1. **Aba 1:** Apresentação / Diagrama de Arquitetura no `README.md` do GitHub.
  2. **Aba 2:** **Swagger UI** aberto em [`http://localhost:8080/swagger-ui.html`](http://localhost:8080/swagger-ui.html).
  3. **Aba 3:** **RabbitMQ Management UI** aberto em [`http://localhost:15672/#/queues`](http://localhost:15672/#/queues) (login `guest` / `guest`).
  4. **Aba 4:** Terminal com os logs da aplicação visíveis (`docker compose logs -f alertacom-api` ou console da IDE) para mostrar o log do `@RabbitListener` em tempo real.
- [ ] **Payloads de Demonstração Copiados:** Deixe o bloco de notas com os JSONs da Seção "Cheat Sheet de Payloads" (no final deste documento) aberto no monitor secundário para copiar e colar sem perder tempo digitando.

---

## ⏱️ Estrutura Cronometrada dos 5 Blocos

| Bloco | Minutagem | Duração | Foco Principal |
| :---: | :---: | :---: | :--- |
| **Bloco 1** | `0:00 – 0:50` | 50 seg | **Gancho & Problema:** Enchentes urbanas, bueiros obstruídos e o apagão de comunicação entre o cidadão periférico e a Defesa Civil. |
| **Bloco 2** | `0:50 – 1:40` | 50 seg | **Solução CitizenTech & Arquitetura:** O cidadão como sensor ativo da cidade e a arquitetura corporativa em Java 21, Spring Boot 3, Oracle, RabbitMQ e Spring AI. |
| **Bloco 3** | `1:40 – 3:40` | 2 min 00 seg | **Demonstração Técnica Ao Vivo:** Swagger UI, Auth JWT + RBAC, Bean Validation/RFC 7807, Ocorrência Crítica acionando RabbitMQ, Protocolo Defesa Civil e Boletim Preventivo via Spring AI. |
| **Bloco 4** | `3:40 – 4:25` | 45 seg | **Modelo de Negócios B2G & Viabilidade:** SaaS para Prefeituras e Defesa Civil, redução de custos emergenciais e ROI social de 7 para 1. |
| **Bloco 5** | `4:25 – 5:00` | 35 seg | **Impacto ESG (ODS 11 e ODS 13) & Encerramento:** Cidades sustentáveis, ação climática e fechamento de alto impacto. |

---

## 🎙️ Roteiro Minuto a Minuto (Teleprompter + Direção de Tela)

---

### 🔴 BLOCO 1 (0:00 – 0:50) — Gancho e Contextualização do Problema Urbano/Climático

**[TELA — 0:00 a 0:20]**  
*Exibir a capa do projeto no `README.md` (ou slide inicial) destacando: **AlertaCom — Plataforma CitizenTech de Resiliência Urbana e Defesa Civil**, **Global Solution 2026/2 FIAP**, **Gabriel Maciel Alves de Oliveira — RM 562795 (Turma 2TDSR)**.*

**[LOCUÇÃO — 0:00 a 0:20]**  
> "Olá, banca avaliadora da Global Solution FIAP! Eu sou o **Gabriel Maciel Alves de Oliveira, RM 562795, da turma 2TDSR**.  
> Todos os anos, quando o período de chuvas intensas atinge São Paulo e as grandes metrópoles brasileiras, assistimos exatamente à mesma tragédia: vias inteiras submersas em minutos, deslizamentos de encostas, quedas de árvores e famílias perdendo tudo."

**[TELA — 0:20 a 0:50]**  
*Destacar os dados do problema: mais de 8 milhões de brasileiros vivem em áreas de risco mapeadas e os prejuízos anuais com enchentes ultrapassam R$ 5 bilhões no Brasil. Mostrar o gargalo de comunicação entre o cidadão na rua e o poder público.*

**[LOCUÇÃO — 0:20 a 0:50]**  
> "Mas o maior agravante dessa crise não é apenas o volume de chuva: é o **apagão de comunicação em tempo real**. Hoje, um bueiro entupido na periferia ou uma trinca aparecendo em um muro de arrimo são vistos pelos moradores dias ou horas antes do desastre, mas o poder público só fica sabendo quando a enchente ou o deslizamento já aconteceu. O cidadão está na ponta, quer alertar, mas não possui um canal inteligente, auditável e integrado à Defesa Civil. É para resolver essa dor real que criamos o **AlertaCom**."

---

### 🟠 BLOCO 2 (0:50 – 1:40) — A Solução CitizenTech AlertaCom e Arquitetura Corporativa

**[TELA — 0:50 a 1:15]**  
*Rolar o `README.md` até a seção **Visão Geral CitizenTech** e posicionar no **Diagrama de Arquitetura Mermaid**, passando o cursor pelos blocos: Cidadão/Gestor $\rightarrow$ Spring Security JWT $\rightarrow$ REST Controllers $\rightarrow$ Oracle 23c $\rightarrow$ RabbitMQ (`alerta.exchange` $\rightarrow$ `alertas.criticos.queue`) $\rightarrow$ Spring AI Dual-Engine.*

**[LOCUÇÃO — 0:50 a 1:15]**  
> "O **AlertaCom** nasce sob o conceito de **CitizenTech**: nós transformamos cada cidadão em um **sensor ativo da cidade inteligente**. Pela nossa plataforma, qualquer munícipe reporta riscos urbanos e climáticos com geolocalização exata, categoria de risco e nível de severidade.  
> Para sustentar essa operação em escala municipal, desenvolvemos um backend corporativo em **Java 21 LTS com Spring Boot 3.3.5**, seguindo rigorosamente os seis pilares da engenharia corporativa moderna."

**[TELA — 1:15 a 1:40]**  
*Mostrar brevemente na IDE ou no diagrama os pacotes do código (`domain`, `security`, `controller`, `service`, `event`, `exception`) e o `docker-compose.yml` orquestrando Oracle 23c Free, RabbitMQ 3.13 e a API.*

**[LOCUÇÃO — 1:15 a 1:40]**  
> "Nossa arquitetura integra persistência relacional em **Oracle Database 23c** para três entidades de domínio fortemente relacionadas — `Cidadao`, `Ocorrencia` e `ProtocoloDefesaCivil` —, controle de acesso stateless com **Spring Security e JWT**, tratamento global de erros padronizado na **RFC 7807 (`ProblemDetail`)**, mensageria assíncrona em tempo real com **RabbitMQ** e inteligência artificial preventiva com **Spring AI** acoplada diretamente aos dados do banco."

---

### 🟢 BLOCO 3 (1:40 – 3:40) — Demonstração Técnica Ao Vivo (2 Minutos)

#### Parte 3.1 (1:40 – 2:05): Swagger UI, Autenticação JWT & Controle RBAC
**[TELA — 1:40 a 2:05]**  
1. *Alternar para a **Aba 2 (Swagger UI: `http://localhost:8080/swagger-ui.html`)**.*
2. *Mostrar o título oficial da API e os grupos de endpoints.*
3. *Abrir `POST /api/auth/login`, autenticar com o usuário Gestor (ou Cidadão), copiar o `token` JWT retornado no HTTP 200 e clicar no botão verde **`Authorize`** no topo do Swagger, colando o token.*

**[LOCUÇÃO — 1:40 a 2:05]**  
> "Vamos ver o AlertaCom funcionando ao vivo. Aqui no **Swagger UI**, documentado com SpringDoc OpenAPI 2.6, temos todos os nossos contratos REST protegidos por **Spring Security e JWT HMAC-SHA256** com dois papéis: `ROLE_CIDADAO` e `ROLE_GESTOR`.  
> Realizo o login em `/api/auth/login`, recebo o token JWT assinado contendo as claims de identificação, papel e bairro, e autentico minha sessão no botão *Authorize*."

---

#### Parte 3.2 (2:05 – 2:40): Abertura de Ocorrência Crítica, Bean Validation/RFC 7807 e Disparo Assíncrono RabbitMQ
**[TELA — 2:05 a 2:40]**  
1. *No Swagger UI, abrir `POST /api/ocorrencias` e enviar o payload de uma ocorrência com `"severidade": "CRITICA"` e `"tipoRisco": "ALAGAMENTO"` no bairro `"Vila Prudente"`.*
2. *Mostrar a resposta **HTTP 201 Created** com status inicial `ABERTA`.*
3. *Alternar imediatamente para o **Terminal de Logs (Aba 4)** e para o **RabbitMQ Management (Aba 3: `http://localhost:15672`)** mostrando o evento publicado na exchange `alerta.exchange`, roteado para a fila `alertas.criticos.queue` e consumido pelo `@RabbitListener` com o broadcast de emergência da Defesa Civil.*

**[LOCUÇÃO — 2:05 a 2:40]**  
> "Agora, simulando um morador da **Vila Prudente** durante uma tempestade severa, envio um `POST /api/ocorrencias` reportando um alagamento de severidade **CRÍTICA** na Avenida Anhaia Mello. Todos os campos passam pelo **Jakarta Bean Validation** — se um dado for inválido, nosso `@RestControllerAdvice` responde imediatamente no padrão **RFC 7807 `ProblemDetail`**.  
> Como a severidade é **CRÍTICA**, vejam o que acontece nos bastidores: o serviço persiste a ocorrência no banco e publica instantaneamente um evento JSON na exchange `alerta.exchange` do **RabbitMQ**. Sem travar a resposta HTTP do cidadão, a fila `alertas.criticos.queue` recebe a mensagem e o nosso consumidor `@RabbitListener` dispara o broadcast imediato para o centro de operações da Defesa Civil!"

---

#### Parte 3.3 (2:40 – 3:10): Paginação Nativa, Despacho e Conclusão de Protocolo da Defesa Civil (`ROLE_GESTOR`)
**[TELA — 2:40 a 3:10]**  
1. *No Swagger UI, executar `GET /api/ocorrencias?bairro=Vila Prudente&page=0&size=10` mostrando o retorno paginado (`Page<OcorrenciaResponse>`).*
2. *Executar `POST /api/protocolos` vinculando a ocorrência recém-criada (`ocorrenciaId: 1`) a uma equipe tática da Defesa Civil. Mostrar o número oficial gerado (`DEF-2026-00001`) e o status da ocorrência mudando automaticamente para `ENCAMINHADA_DEFESA_CIVIL`.*
3. *(Opcional/Rápido) Mostrar `PATCH /api/protocolos/{id}/concluir` promovendo a ocorrência para `RESOLVIDA`.*

**[LOCUÇÃO — 2:40 a 3:10]**  
> "Na visão do gestor público autenticado com `ROLE_GESTOR`, consultamos as ocorrências com **paginação nativa `Pageable`** e filtros dinâmicos por bairro, severidade e status.  
> Para agir sobre o incidente, o gestor emite um protocolo oficial em `POST /api/protocolos`. O sistema gera automaticamente o código único auditável `DEF-2026` e promove a máquina de estados da ocorrência para `ENCAMINHADA_DEFESA_CIVIL`, permitindo acompanhar todo o atendimento até a conclusão técnica em `RESOLVIDA`."

---

#### Parte 3.4 (3:10 – 3:40): Boletim Preventivo Municipal com Spring AI (Integrado ao Banco de Dados)
**[TELA — 3:10 a 3:40]**  
1. *No Swagger UI, abrir o endpoint `POST /api/relatorios/boletim-bairro`, informar `bairro = Vila Prudente` e clicar em **Execute**.*
2. *Destacar o JSON estruturado retornado (`HTTP 200 OK`): `bairro`, `totalOcorrenciasAnalisadas`, `nivelRiscoGeral: "CRÍTICO"`, `resumoSituacaoAtual`, `recomendacoesPopulacao`, `pontosCriticosEvitar` (mostrando o endereço real da Av. Anhaia Mello extraído do banco!) e `planoAcaoDefesaCivil`.*

**[LOCUÇÃO — 3:10 a 3:40]**  
> "E aqui está o grande diferencial de inteligência da plataforma: o nosso módulo **Spring AI**. Cumprindo rigorosamente a diretriz da arquitetura corporativa, **não é um chat solto**. Quando o gestor aciona `POST /api/relatorios/boletim-bairro` para a Vila Prudente, o serviço busca no banco de dados todas as ocorrências ativas daquele bairro, injeta os logradouros e severidades reais no contexto e sintetiza este **Boletim Preventivo Oficial da Defesa Civil**.  
> Observe que o JSON já traz a classificação de risco, os pontos exatos a evitar — incluindo a Avenida Anhaia Mello que acabamos de cadastrar —, orientações práticas para os moradores e o plano tático da Defesa Civil, contando ainda com arquitetura *Dual-Engine* que garante funcionamento contínuo mesmo em contingência offline."

---

### 🟣 BLOCO 4 (3:40 – 4:25) — Modelo de Negócios B2G e Viabilidade Financeira

**[TELA — 3:40 a 4:25]**  
*Voltar para a aba do `README.md` na seção **Modelo de Negócios B2G (Business-to-Government)**, destacando os planos de assinatura SaaS para Prefeituras e a relação de economia 7:1 em prevenção de desastres.*

**[LOCUÇÃO — 3:40 a 4:25]**  
> "E como o AlertaCom se sustenta financeiramente? Nosso modelo de negócios é **B2G (*Business-to-Government*)**, comercializado como uma assinatura **SaaS para Prefeituras Municipais, Subprefeituras e Coordenadorias de Defesa Civil**, escalonada pelo porte populacional do município.  
> Para o gestor público, a viabilidade econômica é imediata: instalar sensores IoT físicos em cada esquina custa dezenas de milhões de reais em hardware e manutenção; o AlertaCom transforma a própria população em uma rede viva de monitoramento com custo marginal próximo de zero. Além disso, cada **1 real investido em limpeza preventiva de bueiros e alerta precoce economiza até 7 reais** aos cofres municipais em reconstrução asfáltica, perdas humanas e indenizações judiciais por enchentes."

---

### 🔵 BLOCO 5 (4:25 – 5:00) — Impacto Social e Ambiental (ODS 11 e ODS 13) e Encerramento

**[TELA — 4:25 a 5:00]**  
*Exibir a tabela de alinhamento com os **ODS 11 e ODS 13 da ONU** e a seção final do `README.md` com os resultados dos testes automatizados (`mvn test -Pe2e` 100% verde) e a identificação completa do aluno: **Gabriel Maciel Alves de Oliveira — RM 562795 — Turma 2TDSR**.*

**[LOCUÇÃO — 4:25 a 5:00]**  
> "Com isso, o AlertaCom entrega impacto direto nos Objetivos de Desenvolvimento Sustentável da ONU: atendemos ao **ODS 11 (Cidades e Comunidades Sustentáveis)** ao dar voz imediata às comunidades periféricas e reduzir drasticamente o tempo de socorro urbano; e cumprimos o **ODS 13 (Ação Contra a Mudança Global do Clima)** ao usar inteligência artificial e arquitetura orientada a eventos para adaptar nossas cidades às emergências climáticas.  
> Todo o projeto está conteinerizado em Docker, documentado e validado por uma suíte automatizada de ponta a ponta.  
> **AlertaCom**: quando o cidadão fala, a cidade escuta e a Defesa Civil salva vidas. Muito obrigado!"

---

## 📋 Cheat Sheet de Payloads para Copiar e Colar Durante a Gravação

Deixe estes blocos prontos para colar no Swagger UI durante o **Bloco 3**:

### 1. Cadastro do Gestor (`POST /api/cidadaos`)
```json
{
  "nome": "Comandante Roberto Souza",
  "email": "comandante.souza@defesacivil.sp.gov.br",
  "telefone": "(11) 99999-1990",
  "bairro": "Vila Prudente",
  "senha": "GestorDefesa@2026",
  "role": "ROLE_GESTOR"
}
```

### 2. Login para Gerar o Token JWT (`POST /api/auth/login`)
```json
{
  "email": "comandante.souza@defesacivil.sp.gov.br",
  "senha": "GestorDefesa@2026"
}
```

### 3. Abertura de Ocorrência Crítica (`POST /api/ocorrencias` — Dispara RabbitMQ)
```json
{
  "titulo": "Transbordamento crítico do Córrego da Mooca na Av. Anhaia Mello",
  "descricao": "Água subiu mais de 1,5 metro em 15 minutos, bloqueando a via sob o monotrilho e ilhando motoristas.",
  "tipoRisco": "ALAGAMENTO",
  "severidade": "CRITICA",
  "latitude": -23.584100,
  "longitude": -46.587200,
  "endereco": "Av. Professor Luiz Ignácio Anhaia Mello, 1200",
  "bairro": "Vila Prudente",
  "cidadaoId": 1
}
```

### 4. Emissão de Protocolo Oficial da Defesa Civil (`POST /api/protocolos`)
```json
{
  "ocorrenciaId": 1,
  "equipeResponsavel": "Batalhão de Salvamento Tático Leste - Viatura B-12",
  "parecerTecnico": "Despacho prioritário de botes de resgate, interdição preventiva com a CET e acionamento de bombas de sucção.",
  "dataPrevisaoAtendimento": "2027-12-31T18:00:00"
}
```

### 5. Conclusão Técnica do Protocolo (`PATCH /api/protocolos/1/concluir`)
```json
{
  "parecerFinal": "Operação concluída sem vítimas. Vias drenadas, galerias desobstruídas e tráfego liberado com segurança."
}
```

### 6. Geração do Boletim Preventivo com Spring AI (`POST /api/relatorios/boletim-bairro`)
- **Parâmetro `bairro` (Query):** `Vila Prudente`
