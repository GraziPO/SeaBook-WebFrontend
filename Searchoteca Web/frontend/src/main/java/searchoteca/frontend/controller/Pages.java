package searchoteca.frontend.controller;

import org.springframework.web.util.UriComponentsBuilder;

/**
 * Endereço base da paginação com os filtros da tela (?q=..., ?status=...),
 * para o "Próxima" não perder a busca. O template acrescenta o page.
 */
final class Pages {
    private Pages() {}

    static String url(String path, String... nameValue) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath(path);
        for (int i = 0; i + 1 < nameValue.length; i += 2) {
            String value = nameValue[i + 1];
            if (value != null && !value.isBlank()) b.queryParam(nameValue[i], value.trim());
        }
        return b.encode().build().toUriString();
    }
}
