package searchoteca.frontend.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reabre o modal de uma tela depois que o backend recusou o formulário (flash "reabrirModal").
 * O layout desenha um botão escondido com data-auto-open (components/ui :: reopen) e o js/app.js
 * preenche o formulário com o que a pessoa tinha digitado; a mensagem vai no flash "erroModal".
 *
 * @param modal       id do &lt;dialog&gt;
 * @param action      para onde o formulário envia (null mantém o do HTML)
 * @param title       título do modal (null mantém)
 * @param submitLabel texto do botão principal (null mantém)
 * @param readonly    campos só leitura, separados por vírgula (ex.: o código, na edição)
 * @param required    campos obrigatórios neste caso, separados por vírgula
 * @param hint        texto de ajuda do modal (null mantém)
 * @param fields      nome do campo -> valor
 */
public record ModalReopen(String modal, String action, String title, String submitLabel,
                          String readonly, String required, String hint, Map<String, String> fields) {

    public static ModalReopen of(String modal, Map<String, String> fields) {
        return new ModalReopen(modal, null, null, null, null, null, null, fields);
    }

    public ModalReopen withAction(String action, String title, String submitLabel) {
        return new ModalReopen(modal, action, title, submitLabel, readonly, required, hint, fields);
    }

    public ModalReopen withReadonly(String readonly) {
        return new ModalReopen(modal, action, title, submitLabel, readonly, required, hint, fields);
    }

    public ModalReopen withRequired(String required, String hint) {
        return new ModalReopen(modal, action, title, submitLabel, readonly, required, hint, fields);
    }

    /** Map que aceita null (o Map.of não aceita). */
    public static Map<String, String> fields(String... nameValue) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < nameValue.length; i += 2) {
            map.put(nameValue[i], nameValue[i + 1]);
        }
        return map;
    }

    /** {"isbn":"978...","title":"..."}: lido pelo data-fields do js/app.js. */
    public String fieldsJson() {
        StringBuilder sb = new StringBuilder("{");
        if (fields != null) {
            boolean first = true;
            for (Map.Entry<String, String> e : fields.entrySet()) {
                if (e.getValue() == null) continue;
                if (!first) sb.append(',');
                first = false;
                sb.append(quote(e.getKey())).append(':').append(quote(e.getValue()));
            }
        }
        return sb.append('}').toString();
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
