package seabook.frontend.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import seabook.frontend.dto.archive.MovedLine;
import seabook.frontend.dto.catalog.BookDetail;
import seabook.frontend.dto.catalog.CopyAvailability;
import seabook.frontend.dto.catalog.PageResponse;
import seabook.frontend.dto.circulation.ExitTypes;
import seabook.frontend.dto.circulation.LoanView;
import seabook.frontend.dto.circulation.ReceiptSnapshot;
import seabook.frontend.dto.circulation.StudentLoanItem;
import seabook.frontend.dto.settings.SettingDetail;
import seabook.frontend.dto.settings.SettingsView;
import seabook.frontend.dto.users.AccountRequestView;
import seabook.frontend.dto.users.UserRow;
import seabook.frontend.dto.waitlist.WaitlistEntry;
import seabook.frontend.model.Loan;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Regras que ficam nos DTOs e são usadas pelas telas (rótulos, cores, prazos, modais). */
class DtoLogicTest {

    private static final LocalDate TODAY = LocalDate.now();

    // ------------------------------------------------------------------ modal reaberto

    @Test
    @DisplayName("ModalReopen: encadeia ação/somente leitura/obrigatórios e gera o JSON dos campos")
    void modalReopen() {
        ModalReopen m = ModalReopen.of("modal-livro", ModalReopen.fields("isbn", "123", "title", "Dom \"Casmurro\"", "genre", null))
                .withAction("/acervo/livros", "Cadastrar livro", "Cadastrar")
                .withReadonly("isbn")
                .withRequired("notes", "dica")
                .withMessage("mensagem", "outra dica");
        assertEquals("modal-livro", m.modal());
        assertEquals("/acervo/livros", m.action());
        assertEquals("isbn", m.readonly());
        assertEquals("notes", m.required());
        assertEquals("mensagem", m.message());
        assertEquals("outra dica", m.hint());
        assertEquals("{\"isbn\":\"123\",\"title\":\"Dom \\\"Casmurro\\\"\"}", m.fieldsJson());
    }

    @Test
    @DisplayName("ModalReopen: escapa barra, quebra de linha, tab e caracteres de controle; sem campos = {}")
    void modalReopenEscaping() {
        String json = ModalReopen.of("m", ModalReopen.fields("codigos", "A\\B\nC\r\tD\u0001")).fieldsJson();
        assertEquals("{\"codigos\":\"A\\\\B\\nC\\r\\tD\\u0001\"}", json);
        assertEquals("{}", ModalReopen.of("m", null).fieldsJson());
        Map<String, String> odd = ModalReopen.fields("a", "1", "sem-par");
        assertEquals(Map.of("a", "1"), odd);
    }

    // ------------------------------------------------------------------ movimentação

    @Test
    @DisplayName("MovedLine: 'cópia 4 movimentada', 'cópias 4 e 3', 'cópias 5, 4 e 3'")
    void movedLine() {
        assertEquals("cópia 4 movimentada", MovedLine.describe(List.of(4)));
        assertEquals("cópias 4 e 3 movimentadas", MovedLine.describe(List.of(4, 3)));
        assertEquals("cópias 5, 4 e 3 movimentadas", MovedLine.describe(List.of(5, 4, 3)));
        assertEquals("nenhuma cópia movimentada", MovedLine.describe(List.of()));
        assertEquals("nenhuma cópia movimentada", MovedLine.describe(null));
        MovedLine line = MovedLine.of("Dom Casmurro", "Machado", "111", List.of(2));
        assertEquals("cópia 2 movimentada", line.text());
    }

    // ------------------------------------------------------------------ login

    @Test
    @DisplayName("AuthResult: identifica cada etapa do login pelo token que veio")
    void authResult() {
        AuthResult r = new AuthResult();
        assertFalse(r.isFullyAuthenticated() || r.isTermsChallenge() || r.isMfaChallenge() || r.isPasswordChangeChallenge());
        r.setTermsToken("t");
        assertTrue(r.isTermsChallenge());
        r.setMfaToken("m");
        assertTrue(r.isMfaChallenge());
        r.setPasswordChangeToken("p");
        assertTrue(r.isPasswordChangeChallenge());
        r.setToken("jwt");
        assertTrue(r.isFullyAuthenticated());
        assertFalse(r.isTermsChallenge() || r.isMfaChallenge() || r.isPasswordChangeChallenge());
    }

    // ------------------------------------------------------------------ circulação

    @Test
    @DisplayName("tipos de saída: doação é o padrão para tipo desconhecido; baixa exige observação")
    void exitTypes() {
        assertEquals("DONATION", ExitTypes.option("???").key());
        assertTrue(ExitTypes.option("WRITE_OFF").notesRequired());
        assertFalse(ExitTypes.option("SALE").notesRequired());
        assertEquals("Empréstimo", ExitTypes.label("LOAN"));
        assertEquals("Venda", ExitTypes.label("SALE"));
        assertEquals("OUTRO", ExitTypes.label("OUTRO"));
        assertEquals(3, ExitTypes.DEFINITIVE.size());
    }

    @Test
    @DisplayName("LoanView: rótulo e cor por situação; só cancela sem devolução parcial")
    void loanView() {
        LoanView open = new LoanView(1L, "Maria", "RA1", LocalDateTime.now(), TODAY, "OPEN", false, 2, 2);
        assertTrue(open.open());
        assertTrue(open.cancellable());
        assertEquals("Aberto", open.statusLabel());
        assertEquals("open", open.pillClass());

        LoanView partial = new LoanView(1L, "Maria", "RA1", LocalDateTime.now(), TODAY, "OPEN", false, 2, 1);
        assertFalse(partial.cancellable());

        LoanView late = new LoanView(1L, "Maria", "RA1", LocalDateTime.now(), TODAY.minusDays(3), "OPEN", true, 1, 1);
        assertEquals("Atrasado", late.statusLabel());
        assertEquals("overdue", late.pillClass());

        assertEquals("Devolvido", new LoanView(1L, "", "", null, null, "CLOSED", false, 1, 0).statusLabel());
        assertEquals("closed", new LoanView(1L, "", "", null, null, "CLOSED", false, 1, 0).pillClass());
        assertEquals("Cancelado", new LoanView(1L, "", "", null, null, "CANCELLED", false, 1, 0).statusLabel());
        assertEquals("cancelled", new LoanView(1L, "", "", null, null, "CANCELLED", false, 1, 0).pillClass());
        assertNull(new LoanView(1L, "", "", null, null, null, false, 1, 0).statusLabel());
        assertEquals("closed", new LoanView(1L, "", "", null, null, null, false, 1, 0).pillClass());
    }

    private static StudentLoanItem item(LocalDate due, LocalDateTime returned, String status) {
        return new StudentLoanItem(1L, 1L, 1, "C1", "111", "Livro", "Autor", LocalDateTime.now(), due, returned, status);
    }

    @Test
    @DisplayName("StudentLoanItem: com o aluno, atraso, dias restantes e devolução atrasada")
    void studentLoanItem() {
        StudentLoanItem withStudent = item(TODAY.plusDays(5), null, "OPEN");
        assertTrue(withStudent.withStudent());
        assertFalse(withStudent.overdue());
        assertEquals(5, withStudent.daysLeft());
        assertEquals(0, withStudent.daysLate());

        StudentLoanItem late = item(TODAY.minusDays(3), null, "OPEN");
        assertTrue(late.overdue());
        assertEquals(3, late.daysLate());
        assertEquals(0, late.daysLeft());

        StudentLoanItem returnedLate = item(TODAY.minusDays(10), LocalDateTime.now().minusDays(2), "CLOSED");
        assertFalse(returnedLate.withStudent());
        assertTrue(returnedLate.late());
        assertFalse(item(TODAY, LocalDateTime.now().minusDays(1), "CLOSED").late());

        assertTrue(item(null, null, "CANCELLED").cancelled());
        assertEquals(0, item(null, null, "OPEN").daysLeft());
        assertFalse(item(null, null, "OPEN").overdue());
    }

    @Test
    @DisplayName("comprovante mostra o nome do tipo de saída")
    void receipt() {
        ReceiptSnapshot r = new ReceiptSnapshot("CMP-1", "WRITE_OFF", null, null, null, null, List.of(), null, null);
        assertEquals("Baixa", r.exitTypeLabel());
    }

    // ------------------------------------------------------------------ catálogo, usuários, configurações

    @Test
    @DisplayName("paginação e disponibilidade total do livro")
    void catalog() {
        PageResponse<String> first = new PageResponse<>(List.of("a"), 0, 20, 30, 2);
        assertFalse(first.hasPrevious());
        assertTrue(first.hasNext());
        PageResponse<String> last = new PageResponse<>(List.of("a"), 1, 20, 30, 2);
        assertTrue(last.hasPrevious());
        assertFalse(last.hasNext());

        BookDetail book = new BookDetail("111", "Livro", "Autor", null, null, null, List.of(
                new CopyAvailability("D", "Dep", "L1", "Estante 1", 2, 3),
                new CopyAvailability("D", "Dep", "L2", "Estante 2", 1, 1)));
        assertEquals(3, book.totalAvailable());
        assertEquals(0, new BookDetail("111", "Livro", "Autor", null, null, null, null).totalAvailable());
    }

    @Test
    @DisplayName("pedidos de conta: rótulos de tipo, situação e motivo da desativação")
    void accountRequest() {
        AccountRequestView pending = new AccountRequestView(1L, 2L, "maria", "Maria", "RA1", "REACTIVATE", "PENDING",
                null, null, null, null, null, "INACTIVITY");
        assertTrue(pending.pending());
        assertEquals("Reativação", pending.typeLabel());
        assertEquals("Pendente", pending.statusLabel());
        assertEquals("open", pending.pillClass());
        assertEquals("Desativada por inatividade", pending.deactivationLabel());

        AccountRequestView other = new AccountRequestView(1L, 2L, "maria", "Maria", "RA1", "DEACTIVATE", "APPROVED",
                null, null, null, null, null, null);
        assertEquals("Desativação", other.typeLabel());
        assertEquals("Aprovado", other.statusLabel());
        assertEquals("active", other.pillClass());
        assertEquals("Conta ativa", other.deactivationLabel());

        assertEquals("Recusado", req("REJECTED", "ADMIN").statusLabel());
        assertEquals("inactive", req("REJECTED", "ADMIN").pillClass());
        assertEquals("Desativada pela administração", req("REJECTED", "ADMIN").deactivationLabel());
        assertEquals("Cancelado pelo aluno", req("CANCELLED", "REQUEST").statusLabel());
        assertEquals("cancelled", req("CANCELLED", "REQUEST").pillClass());
        assertEquals("Desativada a pedido do aluno", req("CANCELLED", "REQUEST").deactivationLabel());
        assertNull(req(null, "OUTRO").statusLabel());
        assertEquals("OUTRO", req(null, "OUTRO").deactivationLabel());
    }

    private static AccountRequestView req(String status, String reason) {
        return new AccountRequestView(1L, 2L, "u", "U", null, "DEACTIVATE", status, null, null, null, null, null, reason);
    }

    @Test
    @DisplayName("fila, usuário ativo, configurações e empréstimo (modelo antigo)")
    void misc() {
        assertTrue(new WaitlistEntry(1L, "111", "L", "A", "NOTIFIED", 1, null, null, null, null, null, null).notified());
        assertFalse(new WaitlistEntry(1L, "111", "L", "A", "WAITING", 2, null, null, null, null, null, null).notified());
        assertTrue(new UserRow(1L, "u", "U", "e", "root", true, null).active());
        assertFalse(new UserRow(1L, "u", "U", "e", "root", null, null).active());

        SettingsView defaults = SettingsView.defaults();
        assertEquals(15, defaults.loanPeriodDays());
        assertTrue(defaults.salesEnabled());

        SettingDetail d = new SettingDetail("sales.enabled", "Vendas", "BOOLEAN", "true", false, null, null, null, null);
        assertTrue(d.bool());
        assertFalse(d.defaultBool());
        assertTrue(new SettingDetail("k", "l", "BOOLEAN", true, "true", null, null, null, null).defaultBool());

        Loan loan = new Loan();
        loan.setStatus("OPEN");
        loan.setDueDate(TODAY.minusDays(1));
        assertTrue(loan.isOverdue());
        assertEquals("Atrasado", loan.getStatusLabel());
        loan.setDueDate(TODAY.plusDays(1));
        assertEquals("Aberto", loan.getStatusLabel());
        loan.setStatus("CLOSED");
        assertEquals("Devolvido", loan.getStatusLabel());
        loan.setStatus("CANCELLED");
        assertEquals("Cancelado", loan.getStatusLabel());
        loan.setStatus(null);
        assertNull(loan.getStatusLabel());
    }
}
