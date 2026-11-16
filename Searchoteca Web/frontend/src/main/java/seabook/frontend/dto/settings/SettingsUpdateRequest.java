package seabook.frontend.dto.settings;

/** Corpo de PUT /api/configuracoes. O que vier null fica como está. */
public record SettingsUpdateRequest(Boolean salesEnabled, Integer loanPeriodDays, Integer maxOpenLoansPerStudent,
                                    Boolean blockOverdueLoans, Integer waitlistMaxSize, Integer inactivityDays) {}
