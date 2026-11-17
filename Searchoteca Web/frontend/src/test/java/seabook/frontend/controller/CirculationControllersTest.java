package seabook.frontend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import seabook.frontend.dto.ModalReopen;
import seabook.frontend.dto.archive.CopyLookup;
import seabook.frontend.dto.catalog.BookDetail;
import seabook.frontend.dto.catalog.CopyAvailability;
import seabook.frontend.dto.catalog.PageResponse;
import seabook.frontend.dto.circulation.ExitTypes;
import seabook.frontend.dto.circulation.ReceiptSnapshot;
import seabook.frontend.dto.circulation.StudentLoanItem;
import seabook.frontend.dto.settings.SettingsView;
import seabook.frontend.dto.waitlist.WaitlistEntry;
import seabook.frontend.dto.waitlist.WaitlistSummary;
import seabook.frontend.service.BackendService;
import seabook.frontend.service.CatalogService;
import seabook.frontend.service.CirculationService;
import seabook.frontend.support.BackendErrors;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Consulta pública e fila, empréstimos/devoluções, saídas, vendas e "meus empréstimos". */
class CirculationControllersTest {

    private CirculationService circulation;
    private BackendService backend;
    private CatalogService catalog;
    private CopyCodes copyCodes;
    private RedirectAttributesModelMap flash;
    private ExtendedModelMap model;

    @BeforeEach
    void setUp() {
        circulation = mock(CirculationService.class);
        backend = mock(BackendService.class);
        catalog = mock(CatalogService.class);
        copyCodes = mock(CopyCodes.class);
        flash = new RedirectAttributesModelMap();
        model = new ExtendedModelMap();
    }

    private Object flash(String key) {
        return flash.getFlashAttributes().get(key);
    }

    private static CopyLookup copy(long id) {
        return new CopyLookup(id, (int) id, "C" + id, "111", "Livro", "Autor", "AVAILABLE", "D", "L", null);
    }

    private static ReceiptSnapshot receipt() {
        return new ReceiptSnapshot("CMP-1", "DONATION", LocalDateTime.now(), null, null, null, List.of(), null, null);
    }

    // ------------------------------------------------------------------ consulta e fila

    @Test
    @DisplayName("consulta: busca com o termo aparado e paginação mantendo o filtro; endereços antigos redirecionam")
    void search() {
        CatalogController c = new CatalogController(catalog, circulation);
        assertEquals("pages/consulta", c.search(" dom ", 0, model));
        assertEquals("dom", model.get("q"));
        assertEquals("/acervo/consulta?q=dom", model.get("paginaUrl"));
        c.search(null, 0, model);
        assertEquals("", model.get("q"));

        assertEquals("redirect:/acervo/consulta", c.oldSearch(" "));
        assertEquals("redirect:/acervo/consulta?q=dom", c.oldSearch("dom"));
        assertEquals("redirect:/acervo/consulta/978-85", c.oldDetail("978-85"));
    }

    private static BookDetail book(int available) {
        return new BookDetail("111", "Livro", "Autor", null, null, null,
                List.of(new CopyAvailability("D", "Dep", "L1", "Estante", available, 2)));
    }

    @Test
    @DisplayName("detalhe: quadro da fila só para aluno e só quando não há exemplar disponível")
    void detailWaitlist() {
        CatalogController c = new CatalogController(catalog, circulation);
        MockHttpSession session = new MockHttpSession();
        WaitlistSummary summary = new WaitlistSummary("111", 1, 3, 0, 2, false, null, null, null, true);
        when(circulation.waitlistSummary("111")).thenReturn(summary);

        when(catalog.detail("111")).thenReturn(book(1));
        session.setAttribute("role", "students");
        assertEquals("pages/consulta-detalhe", c.detail("111", null, false, session, model));
        assertNull(model.get("fila"));   // tem exemplar disponível

        when(catalog.detail("111")).thenReturn(book(0));
        assertEquals("pages/consulta-parcial", c.detail("111", "dom", true, session, model));
        assertSame(summary, model.get("fila"));

        session.setAttribute("role", "loan_op");
        c.detail("111", null, false, session, model);
        assertNull(model.get("fila"));   // operador não entra na fila
        c.detail("111", null, false, null, model);
        assertNull(model.get("fila"));

        when(catalog.detail("999")).thenReturn(null);
        c.detail("999", null, false, session, model);
        assertNull(model.get("fila"));
    }

    @Test
    @DisplayName("detalhe: falha no quadro da fila não derruba a página, exceto sessão expirada")
    void detailWaitlistErrors() {
        CatalogController c = new CatalogController(catalog, circulation);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("role", "students");
        when(catalog.detail("111")).thenReturn(book(0));

        when(circulation.waitlistSummary("111")).thenThrow(BackendErrors.status(500));
        c.detail("111", null, false, session, model);
        assertNull(model.get("fila"));

        reset(circulation);
        when(circulation.waitlistSummary("111")).thenThrow(BackendErrors.offline());
        c.detail("111", null, false, session, model);
        assertNull(model.get("fila"));

        reset(circulation);
        when(circulation.waitlistSummary("111")).thenThrow(BackendErrors.status(401));
        assertThrows(RestClientResponseException.class, () -> c.detail("111", null, false, session, model));
    }

    @Test
    @DisplayName("entrar e sair da fila: avisos de sucesso e regras do backend (fila cheia, já está com o livro)")
    void joinAndLeave() {
        CatalogController c = new CatalogController(catalog, circulation);
        assertEquals("redirect:/acervo/consulta/111", c.join("111", flash));
        assertTrue(((String) flash("info")).startsWith("Você entrou na fila"));

        doThrow(BackendErrors.status(409, "A fila deste livro está cheia")).when(circulation).joinWaitlist("222");
        c.join("222", flash);
        assertEquals("A fila deste livro está cheia", flash("erro"));

        assertEquals("redirect:/meus-emprestimos", c.leave("111", null, flash));
        assertEquals("Você saiu da fila de espera.", flash("info"));
        assertEquals("redirect:/meus-emprestimos", c.leave("111", "/acervo/consulta/111?parcial=true", flash));
        assertEquals("redirect:/acervo/consulta/111", c.leave("111", "/acervo/consulta/111", flash));

        doThrow(BackendErrors.status(404, "Você não está na fila")).when(circulation).leaveWaitlist("333");
        c.leave("333", null, flash);
        assertEquals("Você não está na fila", flash("erro"));
    }

    // ------------------------------------------------------------------ empréstimos

    @Test
    @DisplayName("lista de empréstimos: filtro válido ou todos; regras da organização na tela")
    void loanList() {
        LoanController c = new LoanController(circulation, backend, copyCodes);
        when(backend.settingsOrDefaults()).thenReturn(new SettingsView(true, 21, 3, false, 3, 90));
        assertEquals("pages/emprestimos", c.list("OVERDUE", 0, model));
        verify(circulation).listLoans("OVERDUE", 0);
        assertEquals(21, model.get("prazoDias"));
        assertEquals(3, model.get("limiteLivros"));
        assertEquals(false, model.get("bloqueiaAtraso"));
        c.list("INVALIDO", 0, model);
        assertEquals("", model.get("status"));
    }

    @Test
    @DisplayName("novo empréstimo: RA aparado e os IDs dos exemplares lidos")
    void loanCreate() {
        LoanController c = new LoanController(circulation, backend, copyCodes);
        when(copyCodes.resolve("C1\nC2")).thenReturn(List.of(copy(1), copy(2)));
        assertEquals("redirect:/emprestimos", c.create(" RA1 ", "C1\nC2", flash));
        verify(circulation).createLoan(argThat(r -> r.ra().equals("RA1") && r.items().equals(List.of(1L, 2L))));
        assertEquals("Empréstimo registrado para o RA RA1 (2 livros).", flash("info"));

        when(copyCodes.resolve("C1")).thenReturn(List.of(copy(1)));
        c.create("RA1", "C1", flash);
        assertEquals("Empréstimo registrado para o RA RA1 (1 livro).", flash("info"));
    }

    @Test
    @DisplayName("empréstimo recusado (limite, atraso, código inválido): reabre o modal com o RA e a lista")
    void loanRefused() {
        LoanController c = new LoanController(circulation, backend, copyCodes);
        when(copyCodes.resolve(" ")).thenThrow(new IllegalArgumentException("Leia ao menos um exemplar."));
        c.create("RA1", " ", flash);
        assertEquals("Leia ao menos um exemplar.", flash("erroModal"));

        when(copyCodes.resolve("C1, C2")).thenReturn(List.of(copy(1), copy(2)));
        doThrow(BackendErrors.status(409, "O aluno atingiu o limite de 5 livros")).when(circulation).createLoan(any());
        c.create("RA1", "C1, C2", flash);
        assertEquals("O aluno atingiu o limite de 5 livros", flash("erroModal"));
        ModalReopen reopen = (ModalReopen) flash("reabrirModal");
        assertEquals("modal-emprestimo", reopen.modal());
        assertEquals("C1\nC2", reopen.fields().get("items"));
        assertEquals("RA1", reopen.fields().get("ra"));
    }

    @Test
    @DisplayName("devolução: todos devolvidos (singular/plural)")
    void giveBackOk() {
        LoanController c = new LoanController(circulation, backend, copyCodes);
        when(copyCodes.resolve("C1\nC2")).thenReturn(List.of(copy(1), copy(2)));
        c.giveBack("C1\nC2", flash);
        verify(circulation).returnCopy(1L);
        verify(circulation).returnCopy(2L);
        assertEquals("2 exemplares devolvidos.", flash("info"));
        when(copyCodes.resolve("C1")).thenReturn(List.of(copy(1)));
        c.giveBack("C1", flash);
        assertEquals("1 exemplar devolvido.", flash("info"));
    }

    @Test
    @DisplayName("devolução com erro no meio: informa quantos já foram e reabre só os que faltam")
    void giveBackPartial() {
        LoanController c = new LoanController(circulation, backend, copyCodes);
        when(copyCodes.resolve(anyString())).thenReturn(List.of(copy(1), copy(2), copy(3)));
        doThrow(BackendErrors.status(409, "Exemplar não está emprestado")).when(circulation).returnCopy(2L);
        c.giveBack("C1 C2 C3", flash);
        assertEquals("1 exemplar devolvido antes do erro. C2: Exemplar não está emprestado", flash("erroModal"));
        assertEquals("C2\nC3", ((ModalReopen) flash("reabrirModal")).fields().get("copyIds"));

        reset(circulation);
        doThrow(BackendErrors.status(409, "x")).when(circulation).returnCopy(1L);
        c.giveBack("C1 C2 C3", flash);
        assertEquals("x", flash("erroModal"));   // falhou no primeiro: sem o "antes do erro"
    }

    @Test
    @DisplayName("devolução: leitura inválida ou backend fora antes de devolver qualquer exemplar")
    void giveBackReadErrors() {
        LoanController c = new LoanController(circulation, backend, copyCodes);
        when(copyCodes.resolve("X")).thenThrow(new IllegalArgumentException("Exemplar \"X\" não encontrado."));
        c.giveBack("X", flash);
        assertEquals("Exemplar \"X\" não encontrado.", flash("erroModal"));
        when(copyCodes.resolve("Y")).thenThrow(BackendErrors.offline());
        c.giveBack("Y", flash);
        assertTrue(((String) flash("erroModal")).startsWith("Não foi possível conectar"));
        verifyNoInteractions(circulation);
    }

    @Test
    @DisplayName("cancelar empréstimo: sucesso e recusa (já tem devolução)")
    void cancel() {
        LoanController c = new LoanController(circulation, backend, copyCodes);
        assertEquals("redirect:/emprestimos?status=OPEN", c.cancel(7L, "/emprestimos?status=OPEN", flash));
        assertTrue(((String) flash("info")).startsWith("Empréstimo 7 cancelado."));
        doThrow(BackendErrors.status(409, "Empréstimo com devolução não pode ser cancelado")).when(circulation).cancelLoan(8L);
        c.cancel(8L, null, flash);
        assertEquals("Empréstimo com devolução não pode ser cancelado", flash("erro"));
    }

    // ------------------------------------------------------------------ saídas e vendas

    @Test
    @DisplayName("saídas: tela sem a venda; venda vai para a tela própria; doação registrada com comprovante")
    void exits() {
        ExitController c = new ExitController(circulation, copyCodes);
        assertEquals("pages/saidas", c.page(model));
        assertFalse(((List<?>) model.get("tipos")).stream().anyMatch(o -> ((ExitTypes.ExitOption) o).key().equals("SALE")));
        assertEquals("redirect:/vendas", c.register("SALE", "C1", null, flash));

        when(copyCodes.ids("C1")).thenReturn(List.of(1L));
        when(circulation.registerExit(any())).thenReturn(receipt());
        assertEquals("redirect:/saidas", c.register("DONATION", "C1", "  Escola X ", flash));
        verify(circulation).registerExit(argThat(r -> r.type().equals("DONATION") && r.notes().equals("Escola X")));
        assertEquals("Doação registrada. Comprovante CMP-1.", flash("info"));
        assertNotNull(flash("comprovante"));

        c.register("DONATION", "C1", " ", flash);
        verify(circulation).registerExit(argThat(r -> r.notes() == null));
    }

    @Test
    @DisplayName("baixa sem motivo (regra do backend) reabre o modal com o motivo obrigatório")
    void writeOffRefused() {
        ExitController c = new ExitController(circulation, copyCodes);
        when(copyCodes.ids(anyString())).thenReturn(List.of(1L));
        when(circulation.registerExit(any())).thenThrow(BackendErrors.status(409, "informe o motivo da baixa"));
        c.register("WRITE_OFF", "C1\nC2", null, flash);
        assertEquals("informe o motivo da baixa", flash("erroModal"));
        ModalReopen reopen = (ModalReopen) flash("reabrirModal");
        assertEquals("notes", reopen.required());
        assertEquals("Registrar baixa", reopen.title());

        when(copyCodes.ids("")).thenThrow(new IllegalArgumentException("Leia ao menos um exemplar."));
        c.register("DONATION", "", null, flash);
        assertEquals("Leia ao menos um exemplar.", flash("erroModal"));
        assertEquals("", ((ModalReopen) flash("reabrirModal")).required());
    }

    @Test
    @DisplayName("comprovantes: busca pelo código, código inexistente e endereços antigos")
    void receipts() {
        ExitController c = new ExitController(circulation, copyCodes);
        assertEquals("pages/comprovantes", c.receipts(null, model));
        assertNull(model.get("comprovante"));
        when(circulation.findReceipt("CMP-1")).thenReturn(receipt());
        c.receipts("CMP-1", model);
        assertNotNull(model.get("comprovante"));
        when(circulation.findReceipt("CMP-X")).thenThrow(BackendErrors.status(404));
        c.receipts("CMP-X", model);
        assertEquals("Comprovante CMP-X não encontrado.", model.get("erro"));

        assertEquals("redirect:/saidas", c.oldAddresses(null));
        assertEquals("redirect:/saidas/comprovantes?codigo=CMP-1", c.oldAddresses("CMP-1"));
    }

    @Test
    @DisplayName("vendas: módulo desligado volta para saídas; venda registrada; livro sem preço mantém a lista")
    void sales() {
        SalesController c = new SalesController(circulation, backend, copyCodes);
        when(backend.salesEnabled()).thenReturn(false);
        assertEquals("redirect:/saidas", c.page(model, flash));
        assertTrue(((String) flash("erro")).startsWith("O módulo de vendas está desligado"));
        when(backend.salesEnabled()).thenReturn(true);
        assertEquals("pages/vendas", c.page(model, flash));

        when(copyCodes.ids("C1")).thenReturn(List.of(1L));
        when(circulation.registerExit(any())).thenReturn(receipt());
        c.sell("C1", " ", flash);
        verify(circulation).registerExit(argThat(r -> r.type().equals("SALE") && r.notes() == null));
        assertEquals("Venda registrada. Cupom CMP-1.", flash("info"));

        reset(circulation);
        when(circulation.registerExit(any())).thenThrow(BackendErrors.status(409, "O livro Dom Casmurro não tem preço de venda"));
        c.sell("C1", "obs", flash);
        assertEquals("O livro Dom Casmurro não tem preço de venda", flash("erroVenda"));
        assertEquals("C1", flash("vendaCodigos"));
        assertEquals("obs", flash("vendaObs"));

        when(copyCodes.ids("")).thenThrow(new IllegalArgumentException("Leia ao menos um exemplar."));
        c.sell("", null, flash);
        assertEquals("Leia ao menos um exemplar.", flash("erroVenda"));
    }

    // ------------------------------------------------------------------ meus empréstimos

    private static StudentLoanItem item(String code, LocalDate due, LocalDateTime returned, String status) {
        return new StudentLoanItem(1L, 1L, 1, code, "111", "Livro", "Autor", LocalDateTime.now(), due, returned, status);
    }

    @Test
    @DisplayName("meus empréstimos: abertos por prazo, atrasados, próxima devolução, reservas e histórico paginado")
    void studentLoans() {
        StudentLoansController c = new StudentLoansController(circulation);
        LocalDate today = LocalDate.now();
        List<StudentLoanItem> items = new ArrayList<>(List.of(
                item("A", today.plusDays(5), null, "OPEN"),
                item("B", today.minusDays(2), null, "OPEN"),
                item("C", null, null, "OPEN")));
        for (int i = 0; i < 25; i++) {
            items.add(item("H" + i, today.minusDays(30), LocalDateTime.now().minusDays(i), "CLOSED"));
        }
        when(circulation.myLoanItems()).thenReturn(items);
        when(circulation.myWaitlist()).thenReturn(List.of(
                new WaitlistEntry(1L, "1", "T", "A", "WAITING", 2, null, null, null, null, null, null),
                new WaitlistEntry(2L, "2", "T", "A", "NOTIFIED", 1, null, null, "C9", "L1", null, null)));

        assertEquals("pages/meus-emprestimos", c.page(1, model));
        List<?> abertos = (List<?>) model.get("abertos");
        assertEquals("B", ((StudentLoanItem) abertos.get(0)).code());
        assertEquals("C", ((StudentLoanItem) abertos.get(2)).code());   // sem prazo vai para o fim
        assertEquals(3, model.get("comVoce"));
        assertEquals(1L, model.get("atrasados"));
        assertEquals(today.minusDays(2), model.get("proximaDevolucao"));
        assertEquals(2L, ((WaitlistEntry) ((List<?>) model.get("fila")).get(0)).waitlistId());   // reserva primeiro
        assertEquals(1, ((List<?>) model.get("reservas")).size());

        PageResponse<?> historico = (PageResponse<?>) model.get("historico");
        assertEquals(1, historico.page());
        assertEquals(2, historico.totalPages());
        assertEquals(5, historico.content().size());
    }

    @Test
    @DisplayName("meus empréstimos sem nada (respostas nulas) e página fora do intervalo")
    void studentLoansEmpty() {
        StudentLoansController c = new StudentLoansController(circulation);
        c.page(99, model);
        assertEquals(0, model.get("comVoce"));
        assertNull(model.get("proximaDevolucao"));
        PageResponse<?> historico = (PageResponse<?>) model.get("historico");
        assertEquals(0, historico.page());
        assertEquals(1, historico.totalPages());
    }
}
