package searchoteca.frontend.service;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import searchoteca.frontend.dto.catalog.PageResponse;
import searchoteca.frontend.dto.circulation.ExitRequest;
import searchoteca.frontend.dto.circulation.LoanRequest;
import searchoteca.frontend.dto.circulation.LoanView;
import searchoteca.frontend.dto.circulation.ReceiptSnapshot;
import searchoteca.frontend.dto.circulation.StudentLoanItem;
import searchoteca.frontend.dto.waitlist.WaitlistEntry;
import searchoteca.frontend.dto.waitlist.WaitlistSummary;

import java.util.List;

/** Empréstimos, devoluções, fila de espera, saídas definitivas e comprovantes. */
@Service
public class CirculationService {

    /**
     * No backend, a rota do comprovante está declarada dentro do ExitController,
     * que tem @RequestMapping("/api/saidas"). Por isso o caminho real hoje é
     * /api/saidas/api/comprovantes/{code}. Quando o backend for corrigido para
     * /api/comprovantes/{code}, basta trocar esta constante.
     */
    private static final String RECEIPT_PATH = "/api/saidas/api/comprovantes/{code}";

    private final RestClient restClient;

    public CirculationService(RestClient backendRestClient) {
        this.restClient = backendRestClient;
    }

    // ---------------- EMPRÉSTIMOS ----------------

    /** status: vazio (todos), OPEN (em dia), OVERDUE, CLOSED ou CANCELLED. */
    public PageResponse<LoanView> listLoans(String status, int page) {
        return restClient.get()
                .uri(uri -> {
                    uri.path("/api/emprestimos")
                            .queryParam("page", Math.max(page, 0))
                            .queryParam("size", BackendService.PAGE_SIZE);
                    if (status != null && !status.isBlank()) uri.queryParam("status", status);
                    return uri.build();
                })
                .retrieve()
                .body(new ParameterizedTypeReference<PageResponse<LoanView>>() {});
    }

    /** Livros de todos os empréstimos do aluno logado (loans:view_own), os 300 mais recentes. */
    public List<StudentLoanItem> myLoanItems() {
        return restClient.get()
                .uri("/api/emprestimos/historico/itens")
                .retrieve()
                .body(new ParameterizedTypeReference<List<StudentLoanItem>>() {});
    }

    public void createLoan(LoanRequest request) {
        restClient.post()
                .uri("/api/emprestimos")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public void cancelLoan(Long loanId) {
        restClient.post()
                .uri("/api/emprestimos/{id}/cancelar", loanId)
                .retrieve()
                .toBodilessEntity();
    }

    /** A devolução é pelo exemplar: o backend acha o empréstimo em aberto dele. */
    public void returnCopy(Long copyId) {
        restClient.post()
                .uri("/api/emprestimos/devolucao/{copyId}", copyId)
                .retrieve()
                .toBodilessEntity();
    }

    // ---------------- FILA DE ESPERA (aluno) ----------------

    public List<WaitlistEntry> myWaitlist() {
        return restClient.get()
                .uri("/api/fila/minhas")
                .retrieve()
                .body(new ParameterizedTypeReference<List<WaitlistEntry>>() {});
    }

    public WaitlistSummary waitlistSummary(String isbn) {
        return restClient.get()
                .uri("/api/fila/{isbn}", isbn)
                .retrieve()
                .body(WaitlistSummary.class);
    }

    public void joinWaitlist(String isbn) {
        restClient.post()
                .uri("/api/fila/{isbn}", isbn)
                .retrieve()
                .toBodilessEntity();
    }

    public void leaveWaitlist(String isbn) {
        restClient.delete()
                .uri("/api/fila/{isbn}", isbn)
                .retrieve()
                .toBodilessEntity();
    }

    // ---------------- SAÍDAS E COMPROVANTES ----------------

    public ReceiptSnapshot registerExit(ExitRequest request) {
        return restClient.post()
                .uri("/api/saidas")
                .body(request)
                .retrieve()
                .body(ReceiptSnapshot.class);
    }

    public ReceiptSnapshot findReceipt(String code) {
        return restClient.get()
                .uri(RECEIPT_PATH, code.trim())
                .retrieve()
                .body(ReceiptSnapshot.class);
    }
}
