package seabook.frontend.dto.circulation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tipos de saída do backend (enum ExitType): rótulos e o texto de cada cartão da tela de saídas. */
public final class ExitTypes {
    private ExitTypes() {}

    /**
     * Um cartão da tela de saídas.
     * @param notesRequired a observação é obrigatória (na baixa, o motivo)
     */
    public record ExitOption(String key, String label, String description, String hint, boolean notesRequired) {}

    /** Só as saídas definitivas: empréstimo tem tela própria. */
    public static final List<ExitOption> OPTIONS = List.of(
            new ExitOption("SALE", "Venda",
                    "O exemplar é vendido e sai do acervo. O valor vem do preço cadastrado no livro.",
                    "O preço de cada exemplar vem do cadastro do livro e não pode ser alterado aqui. "
                            + "Livro sem preço cadastrado não pode ser vendido.",
                    false),
            new ExitOption("DONATION", "Doação",
                    "O exemplar é doado a outra instituição ou pessoa e sai do acervo.",
                    "A doação não tem valor. Use as observações para registrar para quem foi doado.",
                    false),
            new ExitOption("WRITE_OFF", "Baixa",
                    "O exemplar sai do acervo por dano, extravio ou descarte.",
                    "Na baixa, a observação é obrigatória: informe o motivo (danificado, extraviado...).",
                    true));

    /** Mantido para os rótulos (comprovantes etc.). */
    public static final Map<String, String> DEFINITIVE = new LinkedHashMap<>();
    static {
        OPTIONS.forEach(o -> DEFINITIVE.put(o.key(), o.label()));
    }

    public static ExitOption option(String type) {
        return OPTIONS.stream().filter(o -> o.key().equals(type)).findFirst().orElse(OPTIONS.get(1));
    }

    public static String label(String type) {
        if ("LOAN".equals(type)) return "Empréstimo";
        return DEFINITIVE.getOrDefault(type, type);
    }
}
