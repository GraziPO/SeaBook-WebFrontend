# SeaBook — frontend web

Interface web do SeaBook (Thymeleaf, renderizada no servidor). Não acessa o banco: todas as telas
conversam com a API do backend (repositório `PFC`), enviando o token JWT do usuário logado.

- Java 21 · Spring Boot 4.1 · Thymeleaf
- Porta padrão **8081** (o backend fica na 8080)

---

## 1. Pré-requisitos

- JDK 21 (`java -version`)
- **Backend rodando** (veja o README do backend). Sem ele, o login e as telas mostram
  "Não foi possível conectar ao servidor".
- Maven não precisa ser instalado: use o `mvnw` / `mvnw.cmd` desta pasta.

## 2. Rodar

Dentro de `Searchoteca Web/frontend`:

```bash
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

Acesse **http://localhost:8081**. A tela inicial é a consulta pública ao acervo (`/acervo/consulta`), que
funciona sem login. O login fica em **http://localhost:8081/login**.

Na máquina, nenhuma configuração é necessária: o frontend procura o backend em `http://localhost:8080`.
Para apontar para outro endereço:

```bash
BACKEND_BASE_URL=http://outro-endereco:8080 ./mvnw spring-boot:run
# Windows (PowerShell): $env:BACKEND_BASE_URL="http://outro-endereco:8080"; .\mvnw.cmd spring-boot:run
```

Gerar o `.jar`:

```bash
./mvnw -DskipTests package
java -jar target/*.jar
```

### Variáveis de ambiente

| Variável | Padrão | Para que serve |
|---|---|---|
| `BACKEND_BASE_URL` | `http://localhost:8080` | endereço da API |
| `PORT` | `8081` | porta do frontend (o Railway define sozinho) |
| `SESSION_COOKIE_SECURE` | `false` | `true` em produção (https): o cookie da sessão só trafega por https |

A sessão dura 60 minutos, a mesma validade do token do backend.

### Primeiro acesso

Entre com o usuário **root** criado pelo backend (senha inicial `root123`). O sistema pede o aceite dos termos,
o código enviado por e-mail e a troca obrigatória da senha. Em desenvolvimento, se o backend estiver com
`MFA_MOCK_CODE`, digite esse código no MFA.

## 3. Testes

```bash
./mvnw verify
```

- Testes unitários com JUnit 5, Mockito e os mocks do Spring Test. O backend não precisa estar no ar: os
  testes dos services usam um servidor HTTP local que faz o papel da API.
- Cobertura com JaCoCo: relatório em `target/site/jacoco/index.html`; o `verify` falha abaixo de 60% de linhas.
- Detalhes do que é testado: `LEIA-ME-TESTES.md`.

## 4. Integração contínua e deploy

- **GitHub Actions** (`.github/workflows/ci-frontend.yml`, na raiz do repositório): compila e roda os testes a
  cada push.
- **Railway:** o *Root Directory* do serviço é `Searchoteca Web/frontend`, e a imagem vem do `Dockerfile` desta pasta.
  Variáveis no painel: `BACKEND_BASE_URL` (rede interna, ex. `http://backend.railway.internal:8080`) e
  `SESSION_COOKIE_SECURE=true`. O health check é o `/actuator/health`.

## 5. Telas

| Menu | Tela | Rota | Quem vê |
|---|---|---|---|
| Acervo | Consulta (tela inicial, também sem login) | `/acervo/consulta`, detalhe `/acervo/consulta/{isbn}` | todos |
| Acervo | Livros (busca do ISBN, preço, exemplares) | `/acervo/livros` | root, operadores |
| Acervo | Departamentos e localizações | `/acervo/departamentos` | root, operadores |
| Acervo | Movimentações (avulsa e em lote) | `/acervo/movimentacoes` | root, operador de acervo |
| Acervo | Etiquetas (entrada, movimentação, reimpressão) | `/acervo/exemplares/etiquetas` | root, operadores |
| Empréstimos | Lista, novo empréstimo, devolução, cancelamento | `/emprestimos` | root, operador de empréstimo |
| Meus empréstimos | Com você, fila de espera, histórico | `/meus-emprestimos` | aluno |
| Vendas | Caixa com cupom (só com o módulo ligado) | `/vendas` | root, operador de empréstimo |
| Saídas | Doação, baixa e comprovantes | `/saidas`, `/saidas/comprovantes` | root, operador de empréstimo |
| Relatórios | Dashboard e logs de auditoria (CSV) | `/dashboard`, `/relatorios/auditoria` | root |
| Configurações | Organização | `/configuracoes` | root |
| Configurações | Usuários e pedidos de conta | `/usuarios`, `/usuarios/pedidos` | root, admin_users |
| — | Alterar senha (modal) | — | todos logados |

O menu mostra só o que o perfil pode usar; quem decide de verdade é o backend, que responde 403 a uma rota
digitada sem permissão. Os endereços antigos (`/consulta`, `/acervo`, `/comprovantes`, `/saidas/nova`,
`/acervo/localizacoes`) redirecionam para os novos.

## 6. Estrutura

```
src/main/java/seabook/frontend/
  config/       cliente HTTP com o token, login obrigatório, erros do backend, menu por perfil
  controller/   uma classe por área (login, acervo, movimentações, empréstimos, saídas, usuários...)
  service/      chamadas à API (BackendService, AuthService, CatalogService, CirculationService)
  dto/ · model/ dados recebidos da API
src/main/resources/
  templates/layout/      casca fixa (topo, menu, mensagens, modais globais)
  templates/components/  modal, lista de leitura, etiqueta, comprovante, detalhe do livro
  templates/pages/       uma tela por arquivo
  templates/auth/        login, MFA, termos, recuperação de senha
  static/css · static/js · static/vendor/jsbarcode (código de barras, sem depender de internet)
```

### Como uma tela usa a casca

```html
<html th:replace="~{layout/app :: page(title='Departamentos', active='acervo/departamentos',
                                       content=~{::content}, modals=~{::modals})}">
<section th:fragment="content"> ... </section>
<th:block th:fragment="modals"> ... </th:block>
```

### Como um botão abre um modal

```html
<button type="button" data-open-modal="modal-departamento"
        th:data-form-action="@{/acervo/departamentos/EXA/editar}"
        data-title="Editar departamento" data-submit-label="Salvar" data-readonly="departCode"
        th:data-f-depart-code="${d.departCode}" th:data-f-depart-name="${d.departName}">Editar</button>
```

`data-f-depart-code` preenche o campo `name="departCode"`. A lista completa de `data-*` está no topo do `js/app.js`.

**Erro do backend:** o controller põe a mensagem no flash `erroModal` e o que foi digitado num `ModalReopen`
(flash `reabrirModal`); o modal reabre sozinho, preenchido, com o erro dentro dele. A lista de leitura envia os
**códigos das etiquetas**, e o controller converte código em ID (`CopyCodes`) quando o backend pede ID.

## 7. Problemas comuns

| Sintoma | Causa provável |
|---|---|
| "Não foi possível conectar ao servidor" | backend parado ou `BACKEND_BASE_URL` errado |
| Volta para o login com "Sua sessão expirou" | o token de 1 hora venceu: entre de novo |
| Tela sem estilo depois de atualizar | o `application.yaml` precisa do bloco `spring.web.resources.chain` (cache dos CSS/JS) |
| Etiqueta sem código de barras | o arquivo `static/vendor/jsbarcode/JsBarcode.all.min.js` não foi copiado |
