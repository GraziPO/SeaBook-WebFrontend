package seabook.frontend.service;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;
import seabook.frontend.dto.AuditLog;
import seabook.frontend.dto.archive.BatchMoveRequest;
import seabook.frontend.dto.archive.BatchMoveResult;
import seabook.frontend.dto.archive.CopyLabel;
import seabook.frontend.dto.archive.CopyLookup;
import seabook.frontend.dto.archive.CopyRequest;
import seabook.frontend.dto.archive.LocationBookStock;
import seabook.frontend.dto.archive.LocationShelf;
import seabook.frontend.dto.archive.QuantityMoveRequest;
import seabook.frontend.dto.archive.QuantityMoveResult;
import seabook.frontend.dto.archive.PriceRequest;
import seabook.frontend.dto.auth.ChangePasswordRequest;
import seabook.frontend.dto.catalog.PageResponse;
import seabook.frontend.dto.dashboard.DashboardData;
import seabook.frontend.dto.settings.SettingDetail;
import seabook.frontend.dto.settings.SettingsUpdateRequest;
import seabook.frontend.dto.settings.SettingsView;
import seabook.frontend.dto.users.AccountRequestView;
import seabook.frontend.dto.users.DeactivationRequest;
import seabook.frontend.dto.users.DecisionRequest;
import seabook.frontend.dto.users.MyAccount;
import seabook.frontend.dto.users.StudentRequest;
import seabook.frontend.dto.users.UserRow;
import seabook.frontend.model.Book;
import seabook.frontend.model.Department;
import seabook.frontend.model.Location;
import seabook.frontend.model.User;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Chamadas ao backend para acervo, usuários, configurações, auditoria e dashboard.
 * (Empréstimos, saídas e fila ficam no CirculationService; a consulta pública, no CatalogService.)
 * As listas são paginadas no backend: ?page=0&size=20, no máximo 100 por página.
 */
@Service
public class BackendService {

    public static final int PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final RestClient restClient;

    public BackendService(RestClient backendRestClient) {
        this.restClient = backendRestClient;
    }

    // ---------------- LIVROS ----------------

    public PageResponse<Book> listBooksPage(int page) {
        return restClient.get()
                .uri("/api/livro?page={page}&size={size}", Math.max(page, 0), PAGE_SIZE)
                .retrieve()
                .body(new ParameterizedTypeReference<PageResponse<Book>>() {});
    }

    public Book getBook(String isbn) {
        return restClient.get()
                .uri("/api/livro/{isbn}", isbn)
                .retrieve()
                .body(Book.class);
    }

    public Book createBook(Book book) {
        return restClient.post()
                .uri("/api/livro")
                .body(book)
                .retrieve()
                .body(Book.class);
    }

    public Book updateBook(String isbn, Book book) {
        return restClient.put()
                .uri("/api/livro/{isbn}", isbn)
                .body(book)
                .retrieve()
                .body(Book.class);
    }

    public void deleteBook(String isbn) {
        restClient.delete()
                .uri("/api/livro/{isbn}", isbn)
                .retrieve()
                .toBodilessEntity();
    }

    /** null tira o livro da venda. */
    public Book updatePrice(String isbn, BigDecimal price) {
        return restClient.post()
                .uri("/api/livro/{isbn}/preco", isbn)
                .body(new PriceRequest(price))
                .retrieve()
                .body(Book.class);
    }

    /**
     * Busca os dados do ISBN na Open Library (pelo backend). Devolve o status e o corpo como vieram,
     * para o js/isbn.js decidir: 200 preenche, 404 não achou, 409 inválido/duplicado, 503 fora do ar.
     */
    public ResponseEntity<Map<String, Object>> lookupIsbn(String isbn) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri("/api/livro/consulta/{isbn}", isbn.trim())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            return ResponseEntity.ok(body == null ? Map.of() : body);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) throw ex;
            Map<String, Object> body;
            try {
                body = ex.getResponseBodyAs(new ParameterizedTypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {
                body = null;
            }
            return ResponseEntity.status(ex.getStatusCode()).body(body == null ? Map.of() : body);
        }
    }

    // ---------------- EXEMPLARES ----------------

    /** Cria os exemplares e devolve as etiquetas já com o código gerado pelo banco. */
    public List<CopyLabel> createCopies(CopyRequest request) {
        return restClient.post()
                .uri("/api/exemplares")
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<List<CopyLabel>>() {});
    }

    /** Reimpressão: o parâmetro do backend se chama "labels". */
    public List<CopyLabel> findLabels(List<Long> copyIds) {
        return restClient.get()
                .uri(uri -> uri.path("/api/exemplares/etiquetas")
                        .queryParam("labels", copyIds.toArray())
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<CopyLabel>>() {});
    }

    public BatchMoveResult moveCopies(BatchMoveRequest request) {
        return restClient.post()
                .uri("/api/exemplares/movimentacao")
                .body(request)
                .retrieve()
                .body(BatchMoveResult.class);
    }

    /** Movimentação em lote por quantidade: o backend escolhe quais exemplares vão e devolve os números. */
    public QuantityMoveResult moveByQuantity(QuantityMoveRequest request) {
        return restClient.post()
                .uri("/api/exemplares/movimentacao/quantidade")
                .body(request)
                .retrieve()
                .body(QuantityMoveResult.class);
    }

    /** Exemplar pelo código da etiqueta. O backend responde 404 se não existir. */
    public CopyLookup lookupCopy(String code) {
        return restClient.get()
                .uri("/api/exemplares/codigo/{codigo}", code.trim())
                .retrieve()
                .body(CopyLookup.class);
    }

    // ---------------- LOCALIZAÇÃO (consulta pública, só leitura) ----------------
    // Rotas /api/public/localizacao: funcionam com ou sem login. A tela Localização usa estas para
    // qualquer perfil; cadastrar, editar e excluir continuam nas rotas protegidas abaixo.

    public List<Department> publicDepartments() {
        return restClient.get()
                .uri("/api/public/localizacao/departamentos")
                .retrieve()
                .body(new ParameterizedTypeReference<List<Department>>() {});
    }

    public Department publicDepart(String departCode) {
        return restClient.get()
                .uri("/api/public/localizacao/departamentos/{departCode}", departCode)
                .retrieve()
                .body(Department.class);
    }

    public List<Location> publicLocations() {
        return restClient.get()
                .uri("/api/public/localizacao")
                .retrieve()
                .body(new ParameterizedTypeReference<List<Location>>() {});
    }

    public Location publicLocal(String localCode) {
        return restClient.get()
                .uri("/api/public/localizacao/{localCode}", localCode)
                .retrieve()
                .body(Location.class);
    }

    /** Livros da localização com total, disponíveis, emprestados e reservados (só exemplares do acervo). */
    public List<LocationBookStock> publicLocalBooks(String localCode) {
        return restClient.get()
                .uri("/api/public/localizacao/{localCode}/livros", localCode)
                .retrieve()
                .body(new ParameterizedTypeReference<List<LocationBookStock>>() {});
    }

    // ---------------- DEPARTAMENTOS ----------------

    /** Uma página da listagem (o backend pagina desde o RNF06: ?page=0&size=20). */
    public PageResponse<Department> listDepartPage(int page, int size) {
        return restClient.get()
                .uri("/api/departamento?page={page}&size={size}", Math.max(page, 0), size)
                .retrieve()
                .body(new ParameterizedTypeReference<PageResponse<Department>>() {});
    }

    /** Para listas de seleção (ex.: departamento da localização): todas as páginas. */
    public List<Department> listDepart() {
        List<Department> all = new ArrayList<>();
        int page = 0;
        PageResponse<Department> current;
        do {
            current = listDepartPage(page++, MAX_PAGE_SIZE);
            if (current != null && current.content() != null) all.addAll(current.content());
        } while (current != null && current.hasNext());
        return all;
    }

    public Department getDepart(String departCode) {
        return restClient.get()
                .uri("/api/departamento/{departCode}", departCode)
                .retrieve()
                .body(Department.class);
    }

    public Department createDepart(Department depart) {
        return restClient.post()
                .uri("/api/departamento")
                .body(depart)
                .retrieve()
                .body(Department.class);
    }

    public Department updateDepart(String departCode, Department depart) {
        return restClient.put()
                .uri("/api/departamento/{departCode}", departCode)
                .body(depart)
                .retrieve()
                .body(Department.class);
    }

    public void deleteDepart(String departCode) {
        restClient.delete()
                .uri("/api/departamento/{departCode}", departCode)
                .retrieve()
                .toBodilessEntity();
    }

    // ---------------- LOCALIZAÇÕES ----------------

    public PageResponse<Location> listLocalPage(int page, int size) {
        return restClient.get()
                .uri("/api/localizacao?page={page}&size={size}", Math.max(page, 0), size)
                .retrieve()
                .body(new ParameterizedTypeReference<PageResponse<Location>>() {});
    }

    /** Para listas de seleção (entrada e movimentação de exemplares): todas as páginas. */
    public List<Location> listLocal() {
        List<Location> all = new ArrayList<>();
        int page = 0;
        PageResponse<Location> current;
        do {
            current = listLocalPage(page++, MAX_PAGE_SIZE);
            if (current != null && current.content() != null) all.addAll(current.content());
        } while (current != null && current.hasNext());
        return all;
    }

    /** Tela Movimentações: localizações (busca por código, nome ou departamento) com os exemplares na estante. */
    public PageResponse<LocationShelf> locationShelves(String busca, int page) {
        return restClient.get()
                .uri(uri -> uri.path("/api/localizacao/estantes")
                        .queryParam("busca", busca == null ? "" : busca.trim())
                        .queryParam("page", Math.max(page, 0))
                        .queryParam("size", PAGE_SIZE)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<PageResponse<LocationShelf>>() {});
    }

    /** Livros da localização com total, disponíveis, emprestados e reservados. */
    public List<LocationBookStock> localBooksStock(String localCode) {
        return restClient.get()
                .uri("/api/localizacao/{localCode}/estoque", localCode)
                .retrieve()
                .body(new ParameterizedTypeReference<List<LocationBookStock>>() {});
    }

    public Location getLocal(String localCode) {
        return restClient.get()
                .uri("/api/localizacao/{localCode}", localCode)
                .retrieve()
                .body(Location.class);
    }

    public Location createLocal(Location local) {
        return restClient.post()
                .uri("/api/localizacao")
                .body(local)
                .retrieve()
                .body(Location.class);
    }

    public Location updateLocal(String localCode, Location local) {
        return restClient.put()
                .uri("/api/localizacao/{localCode}", localCode)
                .body(local)
                .retrieve()
                .body(Location.class);
    }

    public void deleteLocal(String localCode) {
        restClient.delete()
                .uri("/api/localizacao/{localCode}", localCode)
                .retrieve()
                .toBodilessEntity();
    }

    // ---------------- USUÁRIOS ----------------
    // Obs: o backend não expõe DELETE para usuários, só ativar/desativar.

    /** tipo: "operadores" ou "alunos". */
    public PageResponse<UserRow> listUsersPage(String tipo, int page) {
        return restClient.get()
                .uri("/api/usuarios?tipo={tipo}&page={page}&size={size}", tipo, Math.max(page, 0), PAGE_SIZE)
                .retrieve()
                .body(new ParameterizedTypeReference<PageResponse<UserRow>>() {});
    }

    public User getUser(String username) {
        return restClient.get()
                .uri("/api/usuarios/{username}", username)
                .retrieve()
                .body(User.class);
    }

    /** Sem senha: o backend usa o próprio usuário como senha inicial e exige a troca no 1º acesso. */
    public User createUser(User user) {
        return restClient.post()
                .uri("/api/usuarios")
                .body(user)
                .retrieve()
                .body(User.class);
    }

    public User updateUser(String username, User user) {
        return restClient.put()
                .uri("/api/usuarios/{username}", username)
                .body(user)
                .retrieve()
                .body(User.class);
    }

    public void activateUser(String username) {
        restClient.patch()
                .uri("/api/usuarios/{username}/status/ativar", username)
                .retrieve()
                .toBodilessEntity();
    }

    public void deactivateUser(String username) {
        restClient.patch()
                .uri("/api/usuarios/{username}/status/desativar", username)
                .retrieve()
                .toBodilessEntity();
    }

    /** O backend fixa o perfil "students" e usa o RA como senha inicial. */
    public void createStudent(StudentRequest request) {
        restClient.post()
                .uri("/api/usuarios/aluno")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    /** Troca voluntária: o id do usuário vem do token, não da URL. */
    public void changeOwnPassword(String currentPassword, String newPassword) {
        restClient.post()
                .uri("/api/usuarios/me/senha")
                .body(new ChangePasswordRequest(currentPassword, newPassword))
                .retrieve()
                .toBodilessEntity();
    }

    // ---------------- PEDIDOS DE CONTA (desativação / reativação) ----------------

    /** status: PENDING para a fila de análise; null traz os 200 mais recentes. */
    public List<AccountRequestView> listAccountRequests(String status) {
        return restClient.get()
                .uri(uri -> {
                    uri.path("/api/usuarios/pedidos");
                    if (status != null && !status.isBlank()) uri.queryParam("status", status);
                    return uri.build();
                })
                .retrieve()
                .body(new ParameterizedTypeReference<List<AccountRequestView>>() {});
    }

    public void approveAccountRequest(Long id, String note) {
        restClient.post()
                .uri("/api/usuarios/pedidos/{id}/aprovar", id)
                .body(new DecisionRequest(blankToNull(note)))
                .retrieve()
                .toBodilessEntity();
    }

    public void rejectAccountRequest(Long id, String note) {
        restClient.post()
                .uri("/api/usuarios/pedidos/{id}/recusar", id)
                .body(new DecisionRequest(blankToNull(note)))
                .retrieve()
                .toBodilessEntity();
    }

    // ---------------- MINHA CONTA (qualquer usuário logado) ----------------

    /** Os dados que o sistema guarda sobre quem está logado. */
    public MyAccount myAccount() {
        return restClient.get()
                .uri("/api/usuarios/me")
                .retrieve()
                .body(MyAccount.class);
    }

    /** Pedidos de desativação/reativação do próprio aluno (só o perfil aluno tem account:request). */
    public List<AccountRequestView> myAccountRequests() {
        List<AccountRequestView> list = restClient.get()
                .uri("/api/usuarios/me/pedidos")
                .retrieve()
                .body(new ParameterizedTypeReference<List<AccountRequestView>>() {});
        return list == null ? List.of() : list;
    }

    public void requestDeactivation(String reason) {
        restClient.post()
                .uri("/api/usuarios/me/desativacao")
                .body(new DeactivationRequest(blankToNull(reason)))
                .retrieve()
                .toBodilessEntity();
    }

    public void cancelDeactivation() {
        restClient.delete()
                .uri("/api/usuarios/me/desativacao")
                .retrieve()
                .toBodilessEntity();
    }

    // ---------------- CONFIGURAÇÕES DA ORGANIZAÇÃO ----------------

    /** Valores em vigor (qualquer usuário logado). */
    public SettingsView settings() {
        return restClient.get()
                .uri("/api/configuracoes")
                .retrieve()
                .body(SettingsView.class);
    }

    /** Como settings(), mas sem derrubar a tela se a leitura falhar: usa os padrões do backend. */
    public SettingsView settingsOrDefaults() {
        try {
            SettingsView view = settings();
            return view == null ? SettingsView.defaults() : view;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) throw ex;
            return SettingsView.defaults();
        } catch (RestClientException ex) {
            return SettingsView.defaults();
        }
    }

    /** Tela do root: valor, padrão, limites e quem alterou por último (settings:manage). */
    public List<SettingDetail> settingDetails() {
        return restClient.get()
                .uri("/api/configuracoes/detalhes")
                .retrieve()
                .body(new ParameterizedTypeReference<List<SettingDetail>>() {});
    }

    public SettingsView updateSettings(SettingsUpdateRequest request) {
        return restClient.put()
                .uri("/api/configuracoes")
                .body(request)
                .retrieve()
                .body(SettingsView.class);
    }

    /**
     * Módulo de vendas ligado? Se a consulta falhar, mostra a opção:
     * o backend recusa a venda de qualquer forma quando está desligado.
     */
    public boolean salesEnabled() {
        try {
            SettingsView view = settings();
            return view == null || view.salesEnabled();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) throw ex;   // sessão expirada: vai para o login
            return true;
        } catch (RestClientException ex) {
            return true;
        }
    }

    // ---------------- AUDITORIA ----------------

    /** Filtros da trilha de auditoria. Todos opcionais; limite entre 1 e 500 (o backend corta). */
    public record AuditQuery(String usuario, String acao, LocalDate de, LocalDate ate, int limite) {}

    public List<AuditLog> listAudit(AuditQuery q) {
        return restClient.get()
                .uri(uri -> auditUri(uri, "/api/auditTrail", q))
                .retrieve()
                .body(new ParameterizedTypeReference<List<AuditLog>>() {});
    }

    /** CSV pronto do backend (RF21), com o nome do arquivo no Content-Disposition. */
    public ResponseEntity<byte[]> exportAudit(AuditQuery q) {
        return restClient.get()
                .uri(uri -> auditUri(uri, "/api/auditTrail/exportar", q))
                .retrieve()
                .toEntity(byte[].class);
    }

    private static URI auditUri(UriBuilder uri, String path, AuditQuery q) {
        uri.path(path).queryParam("limite", q.limite());
        if (q.usuario() != null && !q.usuario().isBlank()) uri.queryParam("usuario", q.usuario().trim());
        if (q.acao() != null && !q.acao().isBlank()) uri.queryParam("acao", q.acao().trim());
        if (q.de() != null) uri.queryParam("de", q.de());
        if (q.ate() != null) uri.queryParam("ate", q.ate());
        return uri.build();
    }

    // ---------------- DASHBOARD ----------------

    /** GET /api/dashboard?dias=N — exige dashboard:view (403 para quem não tem). */
    public DashboardData dashboard(int days) {
        return restClient.get()
                .uri(uri -> uri.path("/api/dashboard").queryParam("dias", days).build())
                .retrieve()
                .body(DashboardData.class);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
