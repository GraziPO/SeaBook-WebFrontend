package seabook.frontend.dto.archive;

import java.util.List;

/**
 * Uma linha do resumo depois da movimentação: "Dom Casmurro — cópias 4 e 3 movimentadas".
 * text: a parte depois do título ("cópias 4 e 3 movimentadas").
 */
public record MovedLine(String title, String author, String isbn, List<Integer> copyIndexes, String text) {

    public static MovedLine of(String title, String author, String isbn, List<Integer> indexes) {
        return new MovedLine(title, author, isbn, indexes, describe(indexes));
    }

    /** [4] -> "cópia 4 movimentada"; [4, 3] -> "cópias 4 e 3 movimentadas"; [5, 4, 3] -> "cópias 5, 4 e 3 movimentadas". */
    public static String describe(List<Integer> indexes) {
        if (indexes == null || indexes.isEmpty()) return "nenhuma cópia movimentada";
        List<String> n = indexes.stream().map(String::valueOf).toList();
        if (n.size() == 1) return "cópia " + n.get(0) + " movimentada";
        String list = String.join(", ", n.subList(0, n.size() - 1)) + " e " + n.get(n.size() - 1);
        return "cópias " + list + " movimentadas";
    }
}
