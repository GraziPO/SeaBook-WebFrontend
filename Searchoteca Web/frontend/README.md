# Searchoteca — frontend (versão nova)

Versão reorganizada do frontend: **a casca da aplicação é fixa** (topo, menu lateral, fundo) e o
conteúdo das telas usa **componentes e modais**, em vez de uma página separada para cada formulário.
Todas as telas já estão migradas.

## Telas

| Menu | Tela | Rota | Quem vê |
|---|---|---|---|
| Acervo | Consulta (tela inicial, também sem login) | `/acervo/consulta`, detalhe `/acervo/consulta/{isbn}` | todos |
| Acervo | Livros (busca do ISBN na Open Library, preço) | `/acervo/livros` | root, operadores |
| Acervo | Departamentos | `/acervo/departamentos` | root, operadores |
| Acervo | Localizações | `/acervo/localizacoes` | root, operadores |
| Acervo | Exemplares (entrada, movimentação, reimpressão) e etiquetas | `/acervo/exemplares`, `/acervo/exemplares/etiquetas` | root, operadores |
| Empréstimos | Lista, novo empréstimo, devolução, cancelamento | `/emprestimos` | root, operador de empréstimo |
| Meus empréstimos | Com você, fila de espera, histórico | `/meus-emprestimos` | aluno |
| Vendas | Caixa com cupom térmico (só com o módulo ligado) | `/vendas` | root, operador de empréstimo |
| Saídas | Doação e baixa | `/saidas` | root, operador de empréstimo |
| Saídas | Comprovantes | `/saidas/comprovantes` | root, operador de empréstimo |
| Relatórios | Dashboard | `/dashboard` | root |
| Relatórios | Logs (auditoria + CSV) | `/relatorios/auditoria` | root |
| Configurações | Organização | `/configuracoes` | root |
| Configurações | Usuários (operadores, alunos, pedidos de conta) | `/usuarios`, `/usuarios/pedidos` | root, admin_users |
| Configurações | Alterar senha (modal) | — | todos logados |

Os endereços antigos (`/consulta`, `/acervo`, `/comprovantes`, `/saidas/nova`) redirecionam para os novos.
O login, o MFA, os termos e a recuperação de senha estão em `templates/auth/`.

## Estrutura

```
templates/
  layout/app.html        casca fixa: <head>, topo, menu, mensagens, modais globais, scripts
  layout/parts.html      topo e menu lateral
  components/
    modal.html           modal genérico (<dialog>) + confirmação global (substitui o confirm())
    ui.html              cabeçalho, lista vazia, paginação, erro no modal, interruptor, reabertura de modal
    forms.html           formulário de alterar senha (modal global)
    scan.html            lista de leitura (campo de uma linha + lista para conferir)
    labels.html          etiqueta do exemplar (vertical, adaptável) + escolha do tamanho
    receipt.html         comprovante (A4) e cupom de venda (impressora térmica 80 mm)
    book.html            detalhe do livro (disponibilidade, onde encontrar, fila de espera)
    markdown.html        Markdown dos termos (login)
  pages/                 uma tela = um arquivo (só o miolo + os modais dela)
  auth/                  login, MFA, termos, recuperação de senha
static/
  css/style.css          base visual
  css/app.css            casca fixa, modais, cartões, dashboard, impressão
  js/app.js              abre/preenche/fecha os modais (data-open-modal, data-f-*, data-fields, ...)
  js/scan.js             lista de leitura (consulta cada código em /exemplares/consulta)
  js/isbn.js             busca do ISBN no cadastro do livro
  js/labels.js           tamanho da etiqueta e código de barras (JsBarcode, em vendor/)
```

## Como uma tela usa a casca

```html
<html th:replace="~{layout/app :: page(title='Departamentos', active='acervo/departamentos',
                                       content=~{::content}, modals=~{::modals})}">
<section th:fragment="content"> ... </section>
<th:block th:fragment="modals"> ... </th:block>
```

## Como um botão abre um modal

```html
<button type="button" data-open-modal="modal-departamento"
        th:data-form-action="@{/acervo/departamentos/EXA/editar}"
        data-title="Editar departamento" data-submit-label="Salvar" data-readonly="departCode"
        th:data-f-depart-code="${d.departCode}" th:data-f-depart-name="${d.departName}">Editar</button>
```

`data-f-depart-code` preenche o campo `name="departCode"`. A lista completa de `data-*` está no topo do `js/app.js`.

**Erro do backend:** o controller põe a mensagem no flash `erroModal` e o que foi digitado num `ModalReopen`
(flash `reabrirModal`). O layout desenha um botão escondido (`components/ui :: reopen`) e o modal reabre sozinho,
preenchido, com o erro dentro dele. A lista de leitura também volta, porque ela envia os **códigos das etiquetas**;
o controller converte código em ID (`CopyCodes`) quando o backend pede ID.

## Rodar

Mesmo jeito da versão anterior (porta 8081, backend em `backend.base-url`). Só uma das duas versões roda por vez.
O backend precisa estar com: paginação, configurações (`/api/configuracoes`), ISBN (`/api/livro/consulta`)
e as rotas novas do pacote `backend-frontend-v2` (exemplar pelo código, lista de empréstimos com aluno,
itens do aluno, usuários por tipo, dashboard com data e hora).
