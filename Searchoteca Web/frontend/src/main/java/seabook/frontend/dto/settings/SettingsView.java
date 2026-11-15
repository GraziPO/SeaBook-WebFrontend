package seabook.frontend.dto.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Valores em vigor (GET /api/configuracoes): qualquer usuário logado pode ler. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SettingsView(boolean salesEnabled, int loanPeriodDays, int maxOpenLoansPerStudent,
                           boolean blockOverdueLoans, int waitlistMaxSize, int inactivityDays) {

    /** Valores padrão do backend, usados se a leitura falhar. */
    public static SettingsView defaults() {
        return new SettingsView(true, 15, 5, true, 5, 180);
    }
}
