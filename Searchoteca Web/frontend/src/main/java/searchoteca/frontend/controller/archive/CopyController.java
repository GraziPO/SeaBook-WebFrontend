package searchoteca.controller.archive;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import searchoteca.DTO.archive.BatchMoveRequest;
import searchoteca.DTO.archive.CopyLabel;
import searchoteca.DTO.archive.CopyLookup;
import searchoteca.DTO.archive.CopyRequest;
import searchoteca.auditTrail.AuditAction;
import searchoteca.auditTrail.AuditLogService;
import searchoteca.service.catalog.CopyService;

import java.util.List;

@RestController
@RequestMapping("api/exemplares")
public class CopyController {
    private final CopyService copyService;
    private final AuditLogService auditLogService;

    public CopyController(CopyService copyService, AuditLogService auditLogService) {
        this.copyService = copyService;
        this.auditLogService = auditLogService;
    }

    @PreAuthorize("hasAuthority('copies:create')")
    @PostMapping()
    public ResponseEntity<?> createCopy(@RequestBody CopyRequest request){
        List<CopyLabel> labels = copyService.create(request);
        auditLogService.record(AuditAction.CREATE,
                request.quantity() + " exemplar(es) do ISBN " + request.isbn() + " em " + request.localCode());
        return ResponseEntity.ok(labels);
    }

    /**
     * Exemplar pelo código da etiqueta, com o livro e a situação. Usado pela lista de leitura
     * do frontend (baixa, venda, empréstimo, devolução, movimentação, reimpressão).
     */
    @PreAuthorize("hasAnyAuthority('copies:view', 'loans:create', 'loans:return', 'exits:create')")
    @GetMapping("/codigo/{codigo}")
    public ResponseEntity<CopyLookup> lookup(@PathVariable String codigo) {
        return ResponseEntity.ok(copyService.lookup(codigo));
    }

    //rotas específicas pra impressão de etiquetas
    @PreAuthorize("hasAuthority('copies:view')")
    @GetMapping("/etiquetas")
    public ResponseEntity<?> printLabels(@RequestParam List<Long> labels){
        List<CopyLabel> label = copyService.findLabels(labels);
        auditLogService.record(AuditAction.ACCESS,"reipressão de etiquetas: " + labels);
        return ResponseEntity.ok(label);
    }

    @PreAuthorize("hasAuthority('copies:edit')")
    @PostMapping("/movimentacao")
    public ResponseEntity<?> moveBatch(@RequestBody BatchMoveRequest request) {
        return ResponseEntity.ok(copyService.moveBatch(request));
    }
}
