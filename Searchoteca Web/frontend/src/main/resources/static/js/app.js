/*
 * Modais da aplicação (usa o <dialog> nativo: Esc fecha e o foco fica preso dentro dele).
 *
 * Qualquer elemento com data-open-modal="id-do-dialog" abre o modal. Ao abrir, o formulário
 * é limpo e ajustado com os data-* do botão que foi clicado:
 *
 *   data-title="..."            título do modal            ([data-modal-title])
 *   data-submit-label="..."     texto do botão principal   ([data-modal-submit])
 *   data-message="..."          texto da confirmação       ([data-modal-message])
 *   data-hint="..."             texto de ajuda             ([data-modal-hint])
 *   data-form-action="/url"     para onde o formulário envia
 *   data-f-<campo>="valor"      preenche o campo; data-f-depart-code -> name="departCode"
 *   data-readonly="a,b"         campos só leitura (ex.: o código, na edição)
 *   data-required="a,b"         campos obrigatórios neste caso (ex.: observação, na baixa)
 *
 * data-auto-open: o botão (escondido) é "clicado" ao carregar a página. É assim que um modal
 * reabre depois de um erro, já com o que a pessoa tinha digitado e a mensagem do backend.
 * data-close-modal fecha o modal; data-print imprime só o modal aberto.
 * data-print="thermal" imprime no formato da impressora térmica (rolo de 80 mm: comprovante de venda).
 */
(function () {
    'use strict';

    function setText(dialog, selector, value) {
        if (value === undefined) return;
        dialog.querySelectorAll(selector).forEach(function (el) {
            el.textContent = value;
            el.hidden = value === '';
        });
    }

    function names(list) {
        return (list || '').split(',').map(function (s) { return s.trim(); }).filter(Boolean);
    }

    /** data-f-depart-code chega no dataset como "fDepartCode": vira o nome do campo "departCode". */
    function fieldName(datasetKey) {
        if (datasetKey.length < 2 || datasetKey.charAt(0) !== 'f') return null;
        var second = datasetKey.charAt(1);
        if (second !== second.toUpperCase() || second === second.toLowerCase()) return null;
        return second.toLowerCase() + datasetKey.slice(2);
    }

    function openModal(trigger) {
        var dialog = document.getElementById(trigger.dataset.openModal);
        if (!dialog) return;
        var auto = trigger.hasAttribute('data-auto-open');
        var form = dialog.querySelector('form');

        if (form) {
            form.reset();
            if (trigger.dataset.formAction) form.action = trigger.dataset.formAction;

            form.querySelectorAll('[readonly]').forEach(function (el) { el.readOnly = false; });
            names(trigger.dataset.readonly).forEach(function (n) {
                var el = form.elements.namedItem(n);
                if (el) el.readOnly = true;
            });

            form.querySelectorAll('[data-required-toggle]').forEach(function (el) { el.required = false; });
            names(trigger.dataset.required).forEach(function (n) {
                var el = form.elements.namedItem(n);
                if (el) el.required = true;
            });

            Object.keys(trigger.dataset).forEach(function (key) {
                var name = fieldName(key);
                var el = name && form.elements.namedItem(name);
                if (!el) return;
                el.value = trigger.dataset[key];
                // lista de leitura (scan.js): recarrega as linhas com os códigos enviados
                var scan = el.hasAttribute('data-scan-value') && el.closest('[data-scan]');
                if (scan && scan.scanLoad) setTimeout(function () { scan.scanLoad(trigger.dataset[key]); }, 0);
            });
        }

        setText(dialog, '[data-modal-title]', trigger.dataset.title);
        setText(dialog, '[data-modal-submit]', trigger.dataset.submitLabel);
        setText(dialog, '[data-modal-message]', trigger.dataset.message);
        setText(dialog, '[data-modal-hint]', trigger.dataset.hint);

        // a mensagem de erro só aparece quando o modal reabre por causa do erro
        dialog.querySelectorAll('[data-modal-error]').forEach(function (el) { el.hidden = !auto; });

        dialog.showModal();

        // atualiza contadores (leitor de código de barras) e põe o foco no primeiro campo editável
        dialog.querySelectorAll('textarea[data-scan-count]').forEach(function (el) {
            el.dispatchEvent(new Event('input'));
        });
        var first = dialog.querySelector('input:not([type=hidden]):not([readonly]), textarea:not([readonly]), select');
        if (first) first.focus();
    }

    document.addEventListener('click', function (event) {
        var opener = event.target.closest('[data-open-modal]');
        if (opener) {
            event.preventDefault();
            openModal(opener);
            return;
        }
        var closer = event.target.closest('[data-close-modal]');
        if (closer) {
            var dialog = closer.closest('dialog');
            if (dialog) dialog.close();
            return;
        }
        var printer = event.target.closest('[data-print]');
        if (printer) {
            var thermal = printer.dataset.print === 'thermal';
            document.body.classList.toggle('print-thermal', thermal);
            window.print();
            setTimeout(function () { document.body.classList.remove('print-thermal'); }, 500);
        }
    });

    // evita envio duplo (clique duplo em "Registrar saída", por exemplo)
    document.addEventListener('submit', function (event) {
        if (event.defaultPrevented) return;
        var button = event.target.querySelector('[type=submit]');
        if (button) {
            setTimeout(function () { button.disabled = true; }, 0);
        }
    });

    var auto = document.querySelector('[data-auto-open]');
    if (auto) openModal(auto);
})();
