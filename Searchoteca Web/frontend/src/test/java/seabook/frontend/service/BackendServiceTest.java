package seabook.frontend.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;
import seabook.frontend.config.RestClientConfig;
import seabook.frontend.dto.archive.BatchMoveRequest;
import seabook.frontend.dto.archive.BatchMoveResult;
import seabook.frontend.dto.archive.CopyRequest;
import seabook.frontend.dto.archive.QuantityMoveRequest;
import seabook.frontend.dto.archive.QuantityMoveResult;
import seabook.frontend.dto.settings.SettingsUpdateRequest;
import seabook.frontend.dto.settings.SettingsView;
import seabook.frontend.dto.users.StudentRequest;
import seabook.frontend.model.Book;
import seabook.frontend.model.Department;
import seabook.frontend.model.Location;
import seabook.frontend.model.User;
import seabook.frontend.service.BackendService.AuditQuery;
import seabook.frontend.support.StubHttpServer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Todas as rotas do BackendService contra um "backend" local: método, URL, corpo e leitura da resposta. */
class BackendServiceTest {

    private static final String BOOK = "{\"isbn\":\"9788535914849\",\"title\":\"Dom Casmurro\",\"author\":\"Machado\",\"rel_year\":1899}";
    private static final String SETTINGS = "{\"salesEnabled\":false,\"loanPeriodDays\":21,\"maxOpenLoansPerStudent\":3,"
            + "\"blockOverdueLoans\":true,\"waitlistMaxSize\":3,\"inactivityDays\":90}";

    private StubHttpServer server;
    private BackendService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new StubHttpServer();
        service = new BackendService(new RestClientConfig().backendRestClient(server.url()));
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private static String page(String item, int page, int totalPages) {
        return "{\"content\":[" + item + "],\"page\":" + page + ",\"size\":20,\"totalElements\":1,\"totalPages\":" + totalPages + "}";
    }

    // ------------------------------------------------------------------ livros

    @Test
    @DisplayName("livros: lista paginada, busca, cadastro, edição, preço e exclusão")
    void books() {
        server.on("/api/livro", 200, page(BOOK, 0, 1))
                .on("/api/livro/9788535914849", 200, BOOK)
                .on("/api/livro/9788535914849/preco", 200, BOOK);
        assertEquals(1899, service.listBooksPage(-1).content().get(0).getRel_year());
        assertEquals("/api/livro?page=0&size=20", server.last().pathAndQuery());

        assertEquals("Dom Casmurro", service.getBook("9788535914849").getTitle());

        Book book = new Book();
        book.setIsbn("9788535914849");
        book.setTitle("Dom Casmurro");
        service.createBook(book);
        assertEquals("POST", server.last().method());
        assertTrue(server.last().body().contains("\"title\":\"Dom Casmurro\""));

        service.updateBook("9788535914849", book);
        assertEquals("PUT", server.last().method());

        service.updatePrice("9788535914849", new BigDecimal("29.90"));
        assertTrue(server.last().body().contains("29.9"));

        server.on("/api/livro/9788535914849", 204, "");
        service.deleteBook("9788535914849");
        assertEquals("DELETE", server.last().method());
    }

    @Test
    @DisplayName("consulta do ISBN: dados encontrados, erro com corpo, erro sem corpo e 401")
    void lookupIsbn() {
        server.on("/api/livro/consulta/9788535914849", 200, "{\"title\":\"Dom Casmurro\"}");
        ResponseEntity<Map<String, Object>> ok = service.lookupIsbn(" 9788535914849 ");
        assertEquals("Dom Casmurro", ok.getBody().get("title"));

        server.on("/api/livro/consulta/0000000000", 400, "{\"message\":\"ISBN inválido\"}");
        ResponseEntity<Map<String, Object>> bad = service.lookupIsbn("0000000000");
        assertEquals(400, bad.getStatusCode().value());
        assertEquals("ISBN inválido", bad.getBody().get("message"));

        server.on("/api/livro/consulta/111", 404, "");
        assertEquals(Map.of(), service.lookupIsbn("111").getBody());

        server.on("/api/livro/consulta/222", 200, "");
        assertEquals(Map.of(), service.lookupIsbn("222").getBody());

        server.on("/api/livro/consulta/333", 401, "{}");
        assertThrows(RestClientResponseException.class, () -> service.lookupIsbn("333"));
    }

    // ------------------------------------------------------------------ exemplares

    @Test
    @DisplayName("exemplares: cadastro, etiquetas, leitura do código e as duas movimentações")
    void copies() {
        String label = "{\"copyId\":1,\"code\":\"C1\",\"title\":\"Dom Casmurro\",\"copyIndex\":1}";
        server.on("/api/exemplares", 200, "[" + label + "]")
                .on("/api/exemplares/etiquetas", 200, "[" + label + "]")
                .on("/api/exemplares/codigo/C1", 200, "{\"copyId\":1,\"code\":\"C1\",\"status\":\"AVAILABLE\"}")
                .on("/api/exemplares/movimentacao", 200, "{\"localCode\":\"L2\",\"moved\":1,\"labels\":[" + label + "],\"alreadyInLocation\":[]}")
                .on("/api/exemplares/movimentacao/quantidade", 200, "{\"fromLocalCode\":\"L1\",\"toLocalCode\":\"L2\",\"moved\":2,"
                        + "\"books\":[{\"isbn\":\"111\",\"title\":\"Livro\",\"copyIndexes\":[4,3]}],\"labels\":[]}");

        assertEquals("C1", service.createCopies(new CopyRequest("111", "DEP", "L1", 1)).get(0).code());
        assertEquals(1, service.findLabels(List.of(1L, 2L)).size());
        assertEquals("/api/exemplares/etiquetas?labels=1&labels=2", server.last().pathAndQuery());
        assertEquals("AVAILABLE", service.lookupCopy(" C1 ").status());

        BatchMoveResult batch = service.moveCopies(new BatchMoveRequest("L2", List.of("C1"), 1));
        assertEquals(1, batch.moved());
        QuantityMoveResult qty = service.moveByQuantity(new QuantityMoveRequest("L1", "L2",
                List.of(new QuantityMoveRequest.Item("111", 2))));
        assertEquals(List.of(4, 3), qty.books().get(0).copyIndexes());
        assertTrue(server.last().body().contains("\"quantity\":2"));
    }

    // ------------------------------------------------------------------ departamentos e localizações

    @Test
    @DisplayName("departamentos: junta todas as páginas; CRUD completo")
    void departments() {
        server.on("/api/departamento", 200, page("{\"departCode\":\"DEP\",\"departName\":\"Biblioteca\"}", 0, 1))
                .on("/api/departamento/DEP", 200, "{\"departCode\":\"DEP\",\"departName\":\"Biblioteca\"}");
        List<Department> all = service.listDepart();
        assertEquals(1, all.size());
        assertEquals("/api/departamento?page=0&size=100", server.last().pathAndQuery());
        assertEquals("Biblioteca", service.getDepart("DEP").getDepartName());

        Department d = new Department();
        d.setDepartCode("DEP");
        server.on("/api/departamento", 200, "{\"departCode\":\"DEP\"}");
        assertEquals("DEP", service.createDepart(d).getDepartCode());
        assertEquals("POST", server.last().method());
        service.updateDepart("DEP", d);
        assertEquals("PUT", server.last().method());
        server.on("/api/departamento/DEP", 204, "");
        service.deleteDepart("DEP");
        assertEquals("DELETE", server.last().method());
    }

    @Test
    @DisplayName("lista que vem nula do backend não quebra a junção das páginas")
    void nullPages() {
        server.on("/api/localizacao", 200, "{\"page\":0,\"size\":100,\"totalElements\":0,\"totalPages\":0}");
        assertTrue(service.listLocal().isEmpty());
        server.on("/api/departamento", 200, "{\"page\":0,\"size\":100,\"totalElements\":0,\"totalPages\":0}");
        assertTrue(service.listDepart().isEmpty());
    }

    @Test
    @DisplayName("localizações: lista, estantes com busca, estoque e CRUD")
    void locations() {
        String loc = "{\"localCode\":\"L1\",\"localName\":\"Estante 1\",\"departCode\":\"DEP\"}";
        server.on("/api/localizacao", 200, page(loc, 0, 1))
                .on("/api/localizacao/L1", 200, loc)
                .on("/api/localizacao/estantes", 200, page("{\"localCode\":\"L1\",\"available\":3}", 0, 1))
                .on("/api/localizacao/L1/estoque", 200, "[{\"isbn\":\"111\",\"title\":\"Livro\",\"total\":3,\"available\":2}]");
        assertEquals("L1", service.listLocal().get(0).getLocalCode());
        assertEquals(3, service.locationShelves(" est ", 0).content().get(0).available());
        assertEquals("/api/localizacao/estantes?busca=est&page=0&size=20", server.last().pathAndQuery());
        service.locationShelves(null, 0);
        assertEquals("/api/localizacao/estantes?busca=&page=0&size=20", server.last().pathAndQuery());
        assertEquals(2, service.localBooksStock("L1").get(0).available());
        assertEquals("Estante 1", service.getLocal("L1").getLocalName());

        Location l = new Location();
        l.setLocalCode("L1");
        server.on("/api/localizacao", 200, loc);
        assertEquals("L1", service.createLocal(l).getLocalCode());
        service.updateLocal("L1", l);
        server.on("/api/localizacao/L1", 204, "");
        service.deleteLocal("L1");
        assertEquals("DELETE", server.last().method());
    }

    // ------------------------------------------------------------------ usuários e pedidos

    @Test
    @DisplayName("usuários: lista por aba, detalhe (roleCode -> role_code), cadastro, edição, ativar/desativar, aluno e senha")
    void users() {
        String user = "{\"userid\":2,\"username\":\"joao\",\"roleCode\":\"loan_op\",\"status\":true}";
        server.on("/api/usuarios", 200, page("{\"userId\":2,\"username\":\"joao\",\"status\":true}", 0, 1))
                .on("/api/usuarios/joao", 200, user)
                .on("/api/usuarios/joao/status/ativar", 200, "")
                .on("/api/usuarios/joao/status/desativar", 200, "")
                .on("/api/usuarios/aluno", 200, "")
                .on("/api/usuarios/me/senha", 200, "{}");
        assertTrue(service.listUsersPage("alunos", 0).content().get(0).active());
        assertEquals("/api/usuarios?tipo=alunos&page=0&size=20", server.last().pathAndQuery());

        User u = service.getUser("joao");
        assertEquals(2L, u.getId());
        assertEquals("loan_op", u.getRole_code());

        service.createUser(u);
        service.updateUser("joao", u);
        service.activateUser("joao");
        assertEquals("PATCH", server.last().method());
        service.deactivateUser("joao");
        assertTrue(server.last().pathAndQuery().endsWith("/desativar"));
        service.createStudent(new StudentRequest("maria", "Maria", "m@x.com", null, "RA1", null));
        assertTrue(server.last().body().contains("\"ra\":\"RA1\""));
        service.changeOwnPassword("atual", "NovaSenha1");
        assertTrue(server.last().body().contains("\"newPassword\":\"NovaSenha1\""));
    }

    @Test
    @DisplayName("pedidos de conta: listar (com/sem status), aprovar e recusar com observação aparada")
    void accountRequests() {
        server.on("/api/usuarios/pedidos", 200, "[{\"requestId\":1,\"type\":\"REACTIVATE\",\"status\":\"PENDING\"}]")
                .on("/api/usuarios/pedidos/1/aprovar", 200, "{}")
                .on("/api/usuarios/pedidos/1/recusar", 204, "");
        assertTrue(service.listAccountRequests("PENDING").get(0).pending());
        assertEquals("/api/usuarios/pedidos?status=PENDING", server.last().pathAndQuery());
        service.listAccountRequests(null);
        assertEquals("/api/usuarios/pedidos", server.last().pathAndQuery());

        service.approveAccountRequest(1L, "  ok  ");
        assertTrue(server.last().body().contains("\"note\":\"ok\""));
        service.rejectAccountRequest(1L, " ");
        assertFalse(server.last().body().contains("\"note\":\""));
    }

    // ------------------------------------------------------------------ configurações

    @Test
    @DisplayName("configurações: leitura, detalhes, alteração e módulo de vendas")
    void settings() {
        server.on("/api/configuracoes", 200, SETTINGS)
                .on("/api/configuracoes/detalhes", 200, "[{\"key\":\"loan.period-days\",\"value\":21,\"min\":1,\"max\":90}]");
        assertEquals(21, service.settings().loanPeriodDays());
        assertEquals(21, service.settingsOrDefaults().loanPeriodDays());
        assertFalse(service.salesEnabled());
        assertEquals(90, service.settingDetails().get(0).max());
        SettingsView saved = service.updateSettings(new SettingsUpdateRequest(null, 21, null, null, null, null));
        assertEquals("PUT", server.last().method());
        assertEquals(3, saved.maxOpenLoansPerStudent());
    }

    @Test
    @DisplayName("configurações indisponíveis: usa os padrões; vendas consideradas ligadas; 401 sobe")
    void settingsFallbacks() {
        server.on("/api/configuracoes", 500, "{}");
        assertEquals(SettingsView.defaults(), service.settingsOrDefaults());
        assertTrue(service.salesEnabled());

        server.on("/api/configuracoes", 200, "");
        assertEquals(SettingsView.defaults(), service.settingsOrDefaults());
        assertTrue(service.salesEnabled());

        server.on("/api/configuracoes", 401, "{}");
        assertThrows(RestClientResponseException.class, service::settingsOrDefaults);
        assertThrows(RestClientResponseException.class, service::salesEnabled);

        BackendService offline = new BackendService(new RestClientConfig().backendRestClient("http://127.0.0.1:1"));
        assertEquals(SettingsView.defaults(), offline.settingsOrDefaults());
        assertTrue(offline.salesEnabled());
    }

    // ------------------------------------------------------------------ relatórios

    @Test
    @DisplayName("auditoria: só os filtros preenchidos vão na URL; exportação devolve o CSV com o nome do arquivo")
    void audit() {
        server.on("/api/auditTrail", 200, "[{\"id\":1,\"time_stamp\":\"2026-11-16T10:00:00\",\"username\":\"root\",\"action\":\"LOGIN_SUCCESS\"}]")
                .on("/api/auditTrail/exportar", 200, "id;acao", "Content-Disposition", "attachment; filename=\"auditoria.csv\"");
        var logs = service.listAudit(new AuditQuery(" root ", " LOGIN_SUCCESS ", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 16), 100));
        assertEquals(2026, logs.get(0).timeStamp().getYear());
        assertEquals("/api/auditTrail?limite=100&usuario=root&acao=LOGIN_SUCCESS&de=2026-11-01&ate=2026-11-16",
                server.last().pathAndQuery());
        service.listAudit(new AuditQuery(null, " ", null, null, 50));
        assertEquals("/api/auditTrail?limite=50", server.last().pathAndQuery());

        ResponseEntity<byte[]> csv = service.exportAudit(new AuditQuery(null, null, null, null, 500));
        assertEquals("id;acao", new String(csv.getBody()));
        assertTrue(csv.getHeaders().getFirst("Content-Disposition").contains("auditoria.csv"));
    }

    @Test
    @DisplayName("dashboard: pede o período e lê os números")
    void dashboard() {
        server.on("/api/dashboard", 200, "{\"periodDays\":7,\"kpis\":{\"openLoans\":3},\"diary\":[{\"day\":\"2026-11-10\",\"loans\":2,\"returns\":1}]}");
        var data = service.dashboard(7);
        assertEquals(3, data.kpis().openLoans());
        assertEquals(2, data.diary().get(0).loans());
        assertEquals("/api/dashboard?dias=7", server.last().pathAndQuery());
    }
}
