package seabook.frontend.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import seabook.frontend.config.BackendMessages;
import seabook.frontend.dto.dashboard.DashboardCharts;
import seabook.frontend.dto.dashboard.DashboardData;
import seabook.frontend.service.BackendService;
import seabook.frontend.service.BackendService.AuditQuery;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * RELATÓRIOS: Dashboard (dashboard:view, só root) e Logs, a trilha de auditoria (audit:view, só root).
 * Os gráficos do dashboard são SVG montados com as coordenadas calculadas no DashboardCharts.
 */
@Controller
public class ReportController {

    /** Opções do seletor de período, em dias. */
    private static final List<Integer> PERIODS = List.of(7, 30, 90, 365);

    /** Ações registradas pelo backend (enum AuditAction), para o filtro dos logs. */
    static final List<String> AUDIT_ACTIONS = List.of(
            "CREATE", "UPDATE", "DELETE", "ACCESS",
            "LOGIN_SUCCESS", "LOGIN_FAILURE", "ACCESS_DENIED",
            "MFA_CODE_SENT", "MFA_SUCCESS", "MFA_FAILURE", "MFA_LOCKED",
            "TERMS_ACCEPTED", "PASSWORD_CHANGED",
            "PASSWORD_RESET_REQUESTED", "PASSWORD_RESET_SUCCESS", "PASSWORD_RESET_FAILURE",
            "ACTIVATE_USER", "DEACTIVATE_USER", "ACTIVATE_REQUEST", "DEACTIVATE_REQUEST",
            "ACCOUNT_REQUEST_APPROVED", "ACCOUNT_REQUEST_REJECTED", "USER_ANONYMIZED",
            "WAITLIST_JOINED", "WAITLIST_LEFT", "WAITLIST_RESERVED", "WAITLIST_FULFILLED", "WAITLIST_EXPIRED",
            "SETTINGS_UPDATED", "AUDIT_EXPORTED");

    private final BackendService backendService;

    public ReportController(BackendService backendService) {
        this.backendService = backendService;
    }

    // ---------------- DASHBOARD ----------------

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "30") int dias, Model model) {
        int period = PERIODS.contains(dias) ? dias : 30;
        DashboardData data = backendService.dashboard(period);
        if (data == null) data = DashboardData.empty(period);
        model.addAttribute("d", data);
        model.addAttribute("k", data.kpis() != null ? data.kpis() : DashboardData.empty(period).kpis());
        model.addAttribute("seg", data.security() != null ? data.security() : DashboardData.empty(period).security());
        model.addAttribute("line", DashboardCharts.line(data.diary()));
        model.addAttribute("donut", DashboardCharts.donut(data.copiesByStatus()));
        model.addAttribute("periods", PERIODS);
        model.addAttribute("period", period);
        model.addAttribute("diasInatividade", backendService.settingsOrDefaults().inactivityDays());
        return "pages/dashboard";
    }

    // ---------------- LOGS (trilha de auditoria) ----------------

    @GetMapping("/relatorios/auditoria")
    public String logs(@RequestParam(required = false) String usuario,
                       @RequestParam(required = false) String acao,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                       @RequestParam(defaultValue = "100") int limite,
                       Model model) {
        AuditQuery q = new AuditQuery(usuario, acao, de, ate, clamp(limite));
        model.addAttribute("filtro", q);
        model.addAttribute("acoes", AUDIT_ACTIONS);
        model.addAttribute("limites", List.of(50, 100, 200, 500));
        model.addAttribute("exportUrl", Pages.url("/relatorios/auditoria/exportar",
                "usuario", usuario, "acao", acao,
                "de", de == null ? null : de.toString(), "ate", ate == null ? null : ate.toString(),
                "limite", "500"));
        try {
            model.addAttribute("logs", backendService.listAudit(q));
        } catch (RestClientException ex) {
            model.addAttribute("logs", List.of());
            model.addAttribute("erro", BackendMessages.forForm(ex, "Não foi possível carregar os logs."));
        }
        return "pages/logs";
    }

    /** Repassa o CSV do backend (até 500 registros, com os mesmos filtros). A exportação fica na auditoria. */
    @GetMapping("/relatorios/auditoria/exportar")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String usuario,
                                         @RequestParam(required = false) String acao,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                         @RequestParam(defaultValue = "500") int limite) {
        try {
            ResponseEntity<byte[]> csv = backendService.exportAudit(new AuditQuery(usuario, acao, de, ate, clamp(limite)));
            String disposition = csv.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            disposition != null ? disposition : "attachment; filename=\"auditoria.csv\"")
                    .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                    .body(csv.getBody());
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) throw ex;
            String message = BackendMessages.of(ex, "Não foi possível exportar os logs.");
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                    .body(message.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static int clamp(int limite) {
        return Math.min(Math.max(limite, 1), 500);
    }
}
