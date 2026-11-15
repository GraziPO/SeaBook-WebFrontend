package searchoteca.frontend.dto.settings;

import java.util.List;

/** Grupo de configurações na tela (Vendas, Empréstimos, Fila de espera, Alunos). */
public record SettingsSection(String title, List<SettingDetail> items) {}
