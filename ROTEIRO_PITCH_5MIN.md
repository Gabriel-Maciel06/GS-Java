# 🎬 Roteiro Oficial do Vídeo Pitch (5 Minutos) — AlertaCom CitizenTech

> **Global Solution 2026/2 — FIAP | Disciplina: Java Advanced**  
> **Projeto:** AlertaCom — Plataforma CitizenTech de Resiliência Urbana e Defesa Civil  
> **Integrantes (Turma 2TDSR):**  
> - **Gabriel Maciel** — RM 562795  
> - **Thomas Fontes** — RM 562254  
> - **Vitória Rodrigues Martins** — RM 565160  
> - **Matheus Pereira Molina** — RM 563399  
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
- [ ] **Verificar Gestor Municipal Inicial:** O gestor municipal padrão (`gestor@alertacom.com.br` / `Gestor@2026!`) já foi provisionado automaticamente pelo `GestorSeedInitializer` no startup da aplicação, permitindo login direto sem etapas manuais preliminares.
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
| **Bloco 3** | `1:40 – 3:40` | 2 min 00 seg | **Demonstração Técnica Ao Vivo:** Swagger UI, Auth JWT + RBAC (Seed Gestor & Blindagem Privilege Escalation), Proteção contra IDOR na Ocorrência Crítica, RabbitMQ pós-commit, Protocolo com Sequence Atômica e Boletim Preventivo via Spring AI (Dual-Engine). |
| **Bloco 4** | `3:40 – 4:25` | 45 seg | **Modelo de Negócios B2G & Viabilidade:** SaaS para Prefeituras e Defesa Civil, redução de custos emergenciais e ROI social de 7 para 1. |
| **Bloco 5** | `4:25 – 5:00` | 35 seg | **Impacto ESG (ODS 11 e ODS 13) & Encerramento:** Cidades sustentáveis, ação climática e fechamento de alto impacto. |

---

## 🎙️ Roteiro Minuto a Minuto (Teleprompter + Direção de Tela)

---

### 🔴 BLOCO 1 (0:00 – 0:50) — Gancho e Contextualização do Problema Urbano/Climático

**[TELA — 0:00 a 0:20]**  
*Exibir a capa do projeto no `README.md` (ou slide inicial) destacando: **AlertaCom — Plataforma CitizenTech de Resiliência Urbana e Defesa Civil**, **Global Solution 2026/2 FIAP**, e os integrantes da **Turma 2TDSR**: **Gabriel Maciel (RM 562795)**, **Thomas Fontes (RM 562254)**, **Vitória Rodrigues Martins (RM 565160)** e **Matheus Pereira Molina (RM 563399)**.*

**[LOCUÇÃO — 0:00 a 0:20]**  
> "Olá, banca avaliadora da Global Solution FIAP! Nós somos a equipe do **AlertaCom**, da **turma 2TDSR**, composta por **Gabriel Maciel (RM 562795)**, **Thomas Fontes (RM 562254)**, **Vitória Rodrigues Martins (RM 565160)** e **Matheus Pereira Molina (RM 563399)**.  
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
> "Nossa arquitetura integra persistência relacional em **Oracle Database 23c** para três entidades de domínio fortemente relacionadas — `Cidadao`, `Ocorrencia` e `ProtocoloDefesaCivil` —, controle de acesso stateless com **Spring Security e JWT blindado contra escalada de privilégios e ataques de IDOR**, tratamento global de erros padronizado na **RFC 7807 (`ProblemDetail`)**, mensageria assíncrona em tempo real com **RabbitMQ** desacoplada via eventos pós-commit e inteligência artificial preventiva com **Spring AI** acoplada diretamente aos dados do banco."

---

### 🟢 BLOCO 3 (1:40 – 3:40) — Demonstração Técnica Ao Vivo (2 Minutos)

#### Parte 3.1 (1:40 – 2:05): Swagger UI, Autenticação JWT & Controle RBAC
**[TELA — 1:40 a 2:05]**  
1. *Alternar para a **Aba 2 (Swagger UI: `http://localhost:8080/swagger-ui.html`)**.*
2. *Destacar que o sistema adota **segurança estrita contra escalada de privilégios**: o cadastro público (`POST /api/cidadaos`) força rigorosamente `ROLE_CIDADAO`, impedindo que invasores injetem privilégios administrativos.*
3. *Demonstrar o login do Gestor Municipal pré-provisionado pelo `GestorSeedInitializer` executando `POST /api/auth/login` com `"email": "gestor@alertacom.com.br"` e `"senha": "Gestor@2026!"`.*
4. *Copiar o `token` Bearer JWT retornado no HTTP 200, clicar no botão verde **`Authorize`** no topo do Swagger e colar o token.*

**[LOCUÇÃO — 1:40 a 2:05]**  
> "Vamos ver o AlertaCom funcionando ao vivo. No **Swagger UI**, documentado com SpringDoc OpenAPI 2.6, todos os contratos REST são protegidos por **Spring Security e JWT HMAC-SHA256** com perfis `ROLE_CIDADAO` e `ROLE_GESTOR`.  
> Para garantir conformidade com padrões de segurança corporativa, o sistema possui **blindagem ativa contra escalada de privilégios**: o cadastro de munícipes em `/api/cidadaos` força estritamente o papel de cidadão, enquanto o acesso administrativo é garantido por um gestor municipal inicial pré-provisionado de forma segura via seed idempotente.  
> Realizo o login direto com o gestor em `/api/auth/login`, recebo o token JWT assinado contendo as claims de identificação, papel e bairro, e autentico minha sessão administrativa no botão *Authorize*."

---

#### Parte 3.2 (2:05 – 2:40): Abertura de Ocorrência Crítica, Bean Validation/RFC 7807 e Disparo Assíncrono RabbitMQ
**[TELA — 2:05 a 2:40]**  
1. *No Swagger UI, abrir `POST /api/ocorrencias` e enviar o payload com `"severidade": "CRITICA"`, `"categoria": "ALAGAMENTO"` no bairro `"Vila Prudente"`.*
2. *Ressaltar na tela o campo `"cidadaoId"`: destacar que a API possui **proteção contra IDOR (Insecure Direct Object Reference)**. Mesmo que um usuário mal-intencionado manipule o `cidadaoId` no JSON tentando associar a ocorrência a outra pessoa, o backend ignora o valor e vincula o registro estritamente ao munícipe autenticado pelo token JWT.*
3. *Mostrar a resposta **HTTP 201 Created** com status inicial `ABERTA`.*
4. *Alternar imediatamente para o **Terminal de Logs (Aba 4)** e para o **RabbitMQ Management (Aba 3: `http://localhost:15672`)** mostrando o evento publicado na exchange `alerta.exchange` pós-commit relacional, roteado para a fila `alertas.criticos.queue` e consumido pelo `@RabbitListener` com o broadcast de emergência da Defesa Civil.*

**[LOCUÇÃO — 2:05 a 2:40]**  
> "Agora, simulando um morador da **Vila Prudente** durante uma tempestade severa, enviamos um `POST /api/ocorrencias` reportando um alagamento de severidade **CRÍTICA** na Avenida Anhaia Mello.  
> Aqui temos outro diferencial de segurança: nossa API possui **proteção ativa contra IDOR**. O serviço vincula a ocorrência diretamente à identidade autenticada no token JWT, ignorando qualquer tentativa de adulteração de `cidadaoId` no payload por munícipes comuns.  
> Todos os campos passam pelo **Jakarta Bean Validation** com tratamento RFC 7807 (`ProblemDetail`). E graças ao desacoplamento transacional via eventos pós-commit, assim que a ocorrência é gravada no banco, a exchange `alerta.exchange` do **RabbitMQ** recebe o evento e nosso consumidor `@RabbitListener` dispara o broadcast imediato para o centro de operações da Defesa Civil!"

---

#### Parte 3.3 (2:40 – 3:10): Paginação Nativa, Despacho e Conclusão de Protocolo da Defesa Civil (`ROLE_GESTOR`)
**[TELA — 2:40 a 3:10]**  
1. *No Swagger UI, executar `GET /api/ocorrencias?bairro=Vila Prudente&page=0&size=10` mostrando o retorno paginado (`Page<OcorrenciaResponse>`).*
2. *Executar `POST /api/protocolos` vinculando a ocorrência recém-criada (`ocorrenciaId: 1`) a uma equipe tática da Defesa Civil. Mostrar o número oficial gerado (`DEF-2026-00001`) e o status da ocorrência mudando automaticamente para `ENCAMINHADA_DEFESA_CIVIL`.*
3. *(Opcional/Rápido) Mostrar `PATCH /api/protocolos/{id}/concluir` promovendo a ocorrência para `RESOLVIDA`.*

**[LOCUÇÃO — 2:40 a 3:10]**  
> "Na visão do gestor público autenticado com `ROLE_GESTOR`, consultamos as ocorrências com **paginação nativa `Pageable`** e filtros dinâmicos por bairro, severidade e status.  
> Para agir sobre o incidente, o gestor emite um protocolo oficial em `POST /api/protocolos`. A geração do número oficial utiliza sequence atômica no banco (`SEQ_PROTOCOLO_NUMERO`), eliminando qualquer risco de colisão sob acessos concorrentes, e promove a máquina de estados da ocorrência para `ENCAMINHADA_DEFESA_CIVIL`, permitindo acompanhar todo o atendimento até a conclusão técnica em `RESOLVIDA`."

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
*Exibir a tabela de alinhamento com os **ODS 11 e ODS 13 da ONU** e a seção final do `README.md` com os resultados dos testes automatizados (`mvn test -Pe2e` 100% verde) e a identificação completa da equipe (**Turma 2TDSR**: **Gabriel Maciel — RM 562795**, **Thomas Fontes — RM 562254**, **Vitória Rodrigues Martins — RM 565160** e **Matheus Pereira Molina — RM 563399**).*

**[LOCUÇÃO — 4:25 a 5:00]**  
> "Com isso, o AlertaCom entrega impacto direto nos Objetivos de Desenvolvimento Sustentável da ONU: atendemos ao **ODS 11 (Cidades e Comunidades Sustentáveis)** ao dar voz imediata às comunidades periféricas e reduzir drasticamente o tempo de socorro urbano; e cumprimos o **ODS 13 (Ação Contra a Mudança Global do Clima)** ao usar inteligência artificial e arquitetura orientada a eventos para adaptar nossas cidades às emergências climáticas.  
> Todo o projeto está conteinerizado em Docker, protegido contra vulnerabilidades como IDOR e Privilege Escalation, e homologado com **100% de sucesso por uma suíte de 446 testes automatizados** — sendo 366 testes de unidade, domínio e segurança, e 80 testes E2E em 4 tiers — integrados a uma esteira de CI contínua no **GitHub Actions**.  
> **AlertaCom**: quando o cidadão fala, a cidade escuta e a Defesa Civil salva vidas. Muito obrigado!"

---

## 📋 Cheat Sheet de Payloads para Copiar e Colar Durante a Gravação

Deixe estes blocos prontos para colar no Swagger UI durante o **Bloco 3**:

### 1. (Opcional) Cadastro de Munícipe — Demonstração Anti-Privilege Escalation (`POST /api/cidadaos`)
*Dica de Demonstração:* Mostre à banca que mesmo enviando `"role": "ROLE_GESTOR"`, o backend força `"role": "ROLE_CIDADAO"`.
```json
{
  "nome": "Morador Vila Prudente",
  "email": "morador.prudente@email.com",
  "telefone": "(11) 98888-1234",
  "bairro": "Vila Prudente",
  "senha": "SenhaSegura@2026",
  "role": "ROLE_GESTOR"
}
```

### 2. Login Direto do Gestor Municipal (`POST /api/auth/login`)
Utilize a credencial do gestor municipal inicial pré-provisionado pelo `GestorSeedInitializer` no startup da aplicação:
```json
{
  "email": "gestor@alertacom.com.br",
  "senha": "Gestor@2026!"
}
```
*(Nota: Se customizado via variável de ambiente `ALERTACOM_GESTOR_DEFAULT_SENHA=admin123`, utilize `admin123`).*

### 3. Abertura de Ocorrência Crítica (`POST /api/ocorrencias` — Demonstração Anti-IDOR & AMQP)
*Dica de Demonstração:* Mostre que o campo `cidadaoId` pode ser omitido ou conter qualquer valor arbitrário (`999999`); a API ignora o valor e vincula o registro diretamente ao munícipe autenticado pelo token JWT, mitigando vulnerabilidades IDOR.
```json
{
  "titulo": "Transbordamento crítico do Córrego da Mooca na Av. Anhaia Mello",
  "descricao": "Água subiu mais de 1,5 metro em 15 minutos, bloqueando a via sob o monotrilho e ilhando motoristas.",
  "categoria": "ALAGAMENTO",
  "severidade": "CRITICA",
  "latitude": -23.584100,
  "longitude": -46.587200,
  "endereco": "Av. Professor Luiz Ignácio Anhaia Mello, 1200",
  "bairro": "Vila Prudente"
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

---

## 💻 Cheat Sheet Alternativo de Comandos cURL (Terminal / Script demo.sh)

Caso prefira demonstrar a execução via terminal Linux/macOS ou script shell automatizado:

```bash
# 1. Login com o Gestor Municipal (Seed Seguro)
TOKEN_GESTOR=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "gestor@alertacom.com.br",
    "senha": "Gestor@2026!"
  }' | jq -r '.token')

echo "Token Gestor: $TOKEN_GESTOR"

# 2. Cadastro de Cidadão (Demonstração Anti-Privilege Escalation)
curl -i -X POST http://localhost:8080/api/cidadaos \
  -H "Content-Type: application/json" \
  -d '{
    "nome": "Morador Vila Prudente",
    "email": "morador.prudente@email.com",
    "telefone": "(11) 98888-1234",
    "bairro": "Vila Prudente",
    "senha": "SenhaSegura@2026",
    "role": "ROLE_GESTOR"
  }'
# Retorno esperado: HTTP 201 com role forçada para "ROLE_CIDADAO"

# 3. Login do Cidadão
TOKEN_CIDADAO=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "morador.prudente@email.com",
    "senha": "SenhaSegura@2026"
  }' | jq -r '.token')

# 4. Abertura de Ocorrência Crítica (Demonstração Anti-IDOR & AMQP Pós-Commit)
curl -i -X POST http://localhost:8080/api/ocorrencias \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_CIDADAO" \
  -d '{
    "titulo": "Transbordamento crítico do Córrego da Mooca na Av. Anhaia Mello",
    "descricao": "Água subiu mais de 1,5 metro em 15 minutos, bloqueando a via sob o monotrilho e ilhando motoristas.",
    "categoria": "ALAGAMENTO",
    "severidade": "CRITICA",
    "latitude": -23.584100,
    "longitude": -46.587200,
    "endereco": "Av. Professor Luiz Ignácio Anhaia Mello, 1200",
    "bairro": "Vila Prudente"
  }'

# 5. Emissão de Protocolo Oficial da Defesa Civil (ROLE_GESTOR)
curl -i -X POST http://localhost:8080/api/protocolos \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_GESTOR" \
  -d '{
    "ocorrenciaId": 1,
    "equipeResponsavel": "Batalhão de Salvamento Tático Leste - Viatura B-12",
    "parecerTecnico": "Despacho prioritário de botes de resgate, interdição preventiva com a CET e acionamento de bombas de sucção.",
    "dataPrevisaoAtendimento": "2027-12-31T18:00:00"
  }'

# 6. Conclusão Técnica do Protocolo (ROLE_GESTOR)
curl -i -X PATCH http://localhost:8080/api/protocolos/1/concluir \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_GESTOR" \
  -d '{
    "parecerFinal": "Operação concluída sem vítimas. Vias drenadas, galerias desobstruídas e tráfego liberado com segurança."
  }'

# 7. Geração do Boletim Preventivo com Spring AI (ROLE_GESTOR)
curl -i -X POST "http://localhost:8080/api/relatorios/boletim-bairro?bairro=Vila%20Prudente" \
  -H "Authorization: Bearer $TOKEN_GESTOR"
```
