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
 *   data-fields='{"a":"1"}'    preenche vários campos de uma vez (reabertura depois de um erro)
 *   data-readonly="a,b"         campos só leitura (ex.: o código, na edição)
 *   data-required="a,b"         campos obrigatórios neste caso (ex.: observação, na baixa)
 *   data-modal-src="/url"       carrega o corpo do modal do servidor ([data-modal-content])
 *
 * data-auto-open: o botão (escondido) é "clicado" ao carregar a página. É assim que um modal
 * reabre depois de um erro, já com o que a pessoa tinha digitado e a mensagem do backend.
 * data-close-modal fecha o modal; data-print imprime só o modal aberto.
 * data-print="thermal" imprime no formato da impressora térmica (rolo de 80 mm: comprovante de venda).
 * data-print="labels" fecha o modal e imprime só as etiquetas (lista de exemplares).
 * data-copy="texto" copia o texto (o botão mostra "Copiado" por um instante).
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

    function setField(form, name, value) {
        var el = form.elements.namedItem(name);
        if (!el) return;
        if (el.type === 'checkbox') { el.checked = value === 'true'; return; }
        el.value = value;
        // lista de leitura (scan.js): recarrega as linhas com os códigos enviados
        var scan = el.hasAttribute && el.hasAttribute('data-scan-value') && el.closest('[data-scan]');
        if (scan && scan.scanLoad) setTimeout(function () { scan.scanLoad(value); }, 0);
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
                if (name) setField(form, name, trigger.dataset[key]);
            });
            // data-fields='{"isbn":"...","title":"..."}': usado ao reabrir depois de um erro do backend
            if (trigger.dataset.fields) {
                var values = {};
                try { values = JSON.parse(trigger.dataset.fields); } catch (e) { values = {}; }
                Object.keys(values).forEach(function (name) {
                    if (values[name] !== null && values[name] !== undefined) setField(form, name, String(values[name]));
                });
            }
        }

        setText(dialog, '[data-modal-title]', trigger.dataset.title);
        setText(dialog, '[data-modal-submit]', trigger.dataset.submitLabel);
        setText(dialog, '[data-modal-message]', trigger.dataset.message);
        setText(dialog, '[data-modal-hint]', trigger.dataset.hint);

        // a mensagem de erro só aparece quando o modal reabre por causa do erro
        dialog.querySelectorAll('[data-modal-error]').forEach(function (el) { el.hidden = !auto; });

        // conteúdo carregado do servidor (ex.: detalhe do livro): mostra "Carregando…" e troca quando chegar
        var target = dialog.querySelector('[data-modal-content]');
        if (target && trigger.dataset.modalSrc) {
            target.innerHTML = '<p class="muted">Carregando…</p>';
            fetch(trigger.dataset.modalSrc, { headers: { 'X-Requested-With': 'fetch' } })
                .then(function (r) { if (!r.ok) throw new Error(r.status); return r.text(); })
                .then(function (html) { target.innerHTML = html; })
                .catch(function () {
                    target.innerHTML = '<div class="alert alert-error">Não foi possível carregar agora. Tente de novo.</div>';
                });
        }

        dialog.showModal();

        // põe o foco no primeiro campo editável
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
        // grupo do menu que só abre/fecha (sem trocar de tela)
        var toggle = event.target.closest('[data-sidebar-toggle]');
        if (toggle) {
            var sub = document.getElementById(toggle.getAttribute('aria-controls'));
            var open = toggle.getAttribute('aria-expanded') !== 'true';
            toggle.setAttribute('aria-expanded', String(open));
            toggle.classList.toggle('active', open);
            if (sub) sub.hidden = !open;
            return;
        }
        var closer = event.target.closest('[data-close-modal]');
        if (closer) {
            var dialog = closer.closest('dialog');
            if (dialog) dialog.close();
            return;
        }
        // copiar um texto (ex.: o código do exemplar na lista depois do cadastro)
        var copier = event.target.closest('[data-copy]');
        if (copier) {
            var text = copier.dataset.copy;
            var done = function () {
                var old = copier.textContent;
                copier.textContent = 'Copiado';
                setTimeout(function () { copier.textContent = old; }, 1500);
            };
            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(text).then(done, function () {});
            }
            return;
        }
        var printer = event.target.closest('[data-print]');
        // etiquetas: fecha o modal de tamanho antes, para sair só a fila de etiquetas
        if (printer && printer.dataset.print === 'labels') {
            var labelDialog = printer.closest('dialog');
            if (labelDialog) labelDialog.close();
            setTimeout(function () { window.print(); }, 50);
            return;
        }
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
