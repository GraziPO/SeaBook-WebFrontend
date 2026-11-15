package searchoteca.frontend.controller;

import java.util.Arrays;
import java.util.List;

/**
 * Lê as listas enviadas pela lista de leitura (um código por linha).
 * O leitor USB "digita" o código e aperta Enter, então cada leitura vira uma linha;
 * também aceitamos vírgula, ponto e vírgula e espaço como separadores.
 */
final class FormParsing {
    private FormParsing() {}

    static List<String> codes(String text) {
        if (text == null) return List.of();
        return Arrays.stream(text.split("[\\s,;]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** Volta a lista para o formato do campo (um por linha), para reabrir o modal. */
    static String join(List<String> codes) {
        return String.join("\n", codes);
    }

    static Integer intOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
