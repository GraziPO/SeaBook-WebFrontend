package seabook.frontend.controller;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import seabook.frontend.dto.archive.CopyLookup;
import seabook.frontend.service.BackendService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A lista de leitura (components/scan.html) envia os códigos das etiquetas, um por linha.
 * O backend recebe IDs nas saídas, nos empréstimos, nas devoluções e na reimpressão;
 * aqui cada código vira o exemplar correspondente (GET /api/exemplares/codigo/{codigo}).
 * Enviar o código (e não o ID) deixa o modal reabrir com a mesma lista depois de um erro.
 */
@Component
class CopyCodes {

    private final BackendService backendService;

    CopyCodes(BackendService backendService) {
        this.backendService = backendService;
    }

    /**
     * @throws IllegalArgumentException com a mensagem para o usuário (lista vazia ou código inexistente)
     * @throws org.springframework.web.client.RestClientException outras falhas do backend
     */
    List<CopyLookup> resolve(String text) {
        List<String> codes = FormParsing.codes(text);
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("Leia ao menos um exemplar.");
        }
        Map<Long, CopyLookup> copies = new LinkedHashMap<>();   // a mesma etiqueta lida duas vezes conta uma
        for (String code : codes) {
            try {
                CopyLookup copy = backendService.lookupCopy(code);
                if (copy != null) copies.putIfAbsent(copy.copyId(), copy);
            } catch (RestClientResponseException ex) {
                if (ex.getStatusCode().value() == 404) {
                    throw new IllegalArgumentException("Exemplar \"" + code + "\" não encontrado. Confira a etiqueta.");
                }
                throw ex;
            }
        }
        return new ArrayList<>(copies.values());
    }

    List<Long> ids(String text) {
        return resolve(text).stream().map(CopyLookup::copyId).toList();
    }
}
