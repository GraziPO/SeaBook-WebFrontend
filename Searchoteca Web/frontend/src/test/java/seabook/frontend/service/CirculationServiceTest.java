package seabook.frontend.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientResponseException;
import seabook.frontend.config.RestClientConfig;
import seabook.frontend.dto.circulation.ExitRequest;
import seabook.frontend.dto.circulation.LoanRequest;
import seabook.frontend.dto.circulation.LoanView;
import seabook.frontend.dto.circulation.ReceiptSnapshot;
import seabook.frontend.dto.catalog.PageResponse;
import seabook.frontend.support.StubHttpServer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Empréstimos, devoluções, fila de espera, saídas e comprovantes. */
class CirculationServiceTest {

    private StubHttpServer server;
    private CirculationService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new StubHttpServer();
        service = new CirculationService(new RestClientConfig().backendRestClient(server.url()));
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    @DisplayName("lista de empréstimos: com e sem filtro de situação")
    void listLoans() {
        server.on("/api/emprestimos", 200, "{\"content\":[{\"loanId\":7,\"studentName\":\"Maria\",\"ra\":\"RA1\","
                + "\"loanDate\":\"2026-11-01T10:00:00\",\"dueDate\":\"2026-11-16\",\"status\":\"OPEN\",\"overdue\":false,"
                + "\"items\":2,\"openItems\":2}],\"page\":0,\"size\":20,\"totalElements\":1,\"totalPages\":1}");
        PageResponse<LoanView> page = service.listLoans("OVERDUE", 2);
        assertEquals(LocalDate.of(2026, 11, 16), page.content().get(0).dueDate());
        assertEquals("/api/emprestimos?page=2&size=20&status=OVERDUE", server.last().pathAndQuery());
        service.listLoans(" ", -1);
        assertEquals("/api/emprestimos?page=0&size=20", server.last().pathAndQuery());
    }

    @Test
    @DisplayName("empréstimo, cancelamento e devolução pelo exemplar")
    void loanActions() {
        server.on("/api/emprestimos", 200, "{}")
                .on("/api/emprestimos/7/cancelar", 200, "{}")
                .on("/api/emprestimos/devolucao/3", 200, "{}");
        service.createLoan(new LoanRequest("RA1", List.of(1L, 2L)));
        assertTrue(server.last().body().contains("\"items\":[1,2]"));
        service.cancelLoan(7L);
        assertEquals("POST", server.last().method());
        service.returnCopy(3L);
        assertEquals("/api/emprestimos/devolucao/3", server.last().pathAndQuery());
    }

    @Test
    @DisplayName("limite de livros: o 409 do backend chega como exceção")
    void loanRefused() {
        server.on("/api/emprestimos", 409, "{\"message\":\"Limite de 5 livros atingido\"}");
        RestClientResponseException ex = assertThrows(RestClientResponseException.class,
                () -> service.createLoan(new LoanRequest("RA1", List.of(1L))));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("aluno: meus livros e minhas filas; entrar e sair da fila")
    void student() {
        server.on("/api/emprestimos/historico/itens", 200, "[{\"loanId\":1,\"code\":\"C1\",\"loanStatus\":\"OPEN\"}]")
                .on("/api/fila/minhas", 200, "[{\"waitlistId\":1,\"isbn\":\"111\",\"status\":\"WAITING\",\"position\":2}]")
                .on("/api/fila/111", 200, "{\"isbn\":\"111\",\"queueSize\":2,\"limit\":3,\"joined\":true,\"position\":2,\"canJoin\":false}");
        assertEquals("C1", service.myLoanItems().get(0).code());
        assertEquals(2, service.myWaitlist().get(0).position());
        assertTrue(service.waitlistSummary("111").joined());
        service.joinWaitlist("111");
        assertEquals("POST", server.last().method());
        service.leaveWaitlist("111");
        assertEquals("DELETE", server.last().method());
    }

    @Test
    @DisplayName("saída registrada devolve o comprovante; busca pelo código aparado")
    void exitsAndReceipts() {
        String receipt = "{\"code\":\"CMP-20261116-000001\",\"exitType\":\"SALE\",\"issuedAt\":\"2026-11-16T10:00:00\","
                + "\"operator\":{\"id\":1,\"username\":\"op\",\"name\":\"Operador\"},"
                + "\"items\":[{\"copyId\":1,\"copyCode\":\"C1\",\"title\":\"Livro\",\"unitPrice\":35.00}],\"total\":35.00}";
        server.on("/api/saidas", 200, receipt)
                .on("/api/saidas/api/comprovantes/CMP-20261116-000001", 200, receipt);
        ReceiptSnapshot r = service.registerExit(new ExitRequest("SALE", List.of(1L), null));
        assertEquals(new BigDecimal("35.00"), r.total());
        assertEquals("Venda", r.exitTypeLabel());
        assertEquals("op", r.operator().username());
        assertEquals("CMP-20261116-000001", service.findReceipt("  CMP-20261116-000001 ").code());
    }
}
