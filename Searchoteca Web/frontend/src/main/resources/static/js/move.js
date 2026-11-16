/*
 * MOVIMENTAÇÕES (Acervo > Movimentações).
 *
 * Avulsa  <form data-single-move data-lookup="/exemplares/consulta">
 *   O leitor "digita" o código e tecla Enter: em vez de enviar o formulário, consulta o exemplar
 *   e mostra o livro, o número, onde está e a situação. Só exemplar disponível pode ser movido.
 *
 * Em lote <form data-batch-move>
 *   Cada linha tem a quantidade (0 até o que está na estante), o atalho "todos" e o aviso
 *   quando passa do limite. O rodapé soma o total e o botão fica desligado enquanto houver erro
 *   ou nada escolhido. O backend confere tudo de novo.
 */
(function () {
    'use strict';

    var STATUS = {
        AVAILABLE: 'Disponível', LOANED: 'Emprestado', RESERVED: 'Reservado',
        SOLD: 'Vendido', DONATED: 'Doado', WRITTEN_OFF: 'Baixado'
    };

    // ---------------- avulsa ----------------

    function setupSingle(form) {
        var input = form.querySelector('[data-single-code]');
        var findBtn = form.querySelector('[data-single-find]');
        var feedback = form.querySelector('[data-single-feedback]');
        var card = form.querySelector('[data-single-card]');
        var dest = form.querySelector('[data-single-dest]');
        var submit = form.querySelector('[data-single-submit]');
        var url = form.dataset.lookup || '/exemplares/consulta';
        var current = null;     // exemplar consultado
        var checkedCode = '';   // código que gerou o "current"

        function say(text, kind) {
            feedback.textContent = text || '';
            feedback.className = 'scan-feedback' + (kind ? ' ' + kind : '');
        }

        function fill(name, value) {
            var el = card.querySelector('[data-c="' + name + '"]');
            if (el) el.textContent = value == null ? '—' : value;
        }

        function refresh() {
            var ok = current && current.status === 'AVAILABLE'
                && (!dest.value || dest.value !== current.localCode);
            if (current && dest.value && dest.value === current.localCode) {
                say('Esse exemplar já está em ' + current.localCode + '. Escolha outra localização.', 'error');
            } else if (current && current.status === 'AVAILABLE') {
                say('Exemplar encontrado. Escolha a nova localização.', 'ok');
            }
            submit.disabled = !ok;
        }

        function show(copy) {
            current = copy;
            fill('title', copy.title);
            fill('author', copy.author);
            fill('index', copy.copyIndex != null ? 'Exemplar ' + copy.copyIndex : null);
            fill('code', copy.code);
            fill('local', copy.localCode);
            fill('depart', copy.departCode);
            var pill = card.querySelector('[data-c="status"]');
            pill.textContent = STATUS[copy.status] || copy.status;
            pill.className = 'status-pill ' + (copy.status === 'AVAILABLE' ? 'active' : 'open');
            card.hidden = false;
            if (copy.status !== 'AVAILABLE') {
                say((STATUS[copy.status] || copy.status) + ': só exemplar disponível na estante pode ser movimentado.', 'error');
            }
            refresh();
            if (copy.status === 'AVAILABLE') dest.focus();
        }

        function find() {
            var code = (input.value || '').trim().toUpperCase();
            input.value = code;
            if (!code) { say('Leia a etiqueta do exemplar.', 'error'); return; }
            if (code === checkedCode && current) { refresh(); return; }
            current = null;
            checkedCode = code;
            card.hidden = true;
            submit.disabled = true;
            say('Buscando…');
            fetch(url + '?codigo=' + encodeURIComponent(code), { headers: { Accept: 'application/json' } })
                .then(function (r) {
                    if (r.status === 404) return null;
                    if (!r.ok) throw new Error('HTTP ' + r.status);
                    return r.json();
                })
                .then(function (copy) {
                    if (code !== checkedCode) return;   // outra leitura chegou antes
                    if (!copy) { say('Exemplar "' + code + '" não encontrado. Confira a etiqueta.', 'error'); return; }
                    show(copy);
                })
                .catch(function () {
                    say('Não foi possível consultar o exemplar agora. Tente de novo.', 'error');
                });
        }

        input.addEventListener('keydown', function (e) {
            if (e.key === 'Enter') { e.preventDefault(); find(); }
        });
        input.addEventListener('input', function () {
            if ((input.value || '').trim().toUpperCase() !== checkedCode) {
                current = null;
                card.hidden = true;
                submit.disabled = true;
                say('');
            }
        });
        findBtn.addEventListener('click', find);
        dest.addEventListener('change', refresh);
        form.addEventListener('submit', function (e) {
            if (!current || current.status !== 'AVAILABLE') { e.preventDefault(); find(); }
        });

        submit.disabled = true;
        if (input.value.trim()) find();     // voltou de um erro com o código preenchido
    }

    // ---------------- em lote ----------------

    function setupBatch(form) {
        var rows = Array.prototype.slice.call(form.querySelectorAll('[data-qty-row]'));
        var total = form.querySelector('[data-qty-total]');
        var submit = form.querySelector('[data-qty-submit]');
        if (!rows.length || !total || !submit) return;

        function check(row) {
            var input = row.querySelector('[data-qty]');
            var err = row.querySelector('[data-qty-err]');
            var max = parseInt(input.getAttribute('max'), 10) || 0;
            var raw = (input.value || '').trim();
            var value = raw === '' ? 0 : Number(raw);
            var message = '';
            if (!Number.isInteger(value) || value < 0) {
                message = 'Use um número inteiro de 0 a ' + max + '.';
            } else if (value > max) {
                message = 'Máximo ' + max + ': é o que está na estante.';
            }
            input.classList.toggle('over', !!message);
            err.textContent = message;
            err.hidden = !message;
            return { value: message ? 0 : value, bad: !!message };
        }

        function update() {
            var sum = 0, bad = 0;
            rows.forEach(function (row) {
                var r = check(row);
                sum += r.value;
                if (r.bad) bad++;
            });
            var text = '<strong>' + sum + '</strong> ' + (sum === 1 ? 'exemplar selecionado' : 'exemplares selecionados');
            if (bad) text += ' · ' + bad + (bad === 1 ? ' linha acima do limite' : ' linhas acima do limite');
            total.innerHTML = text;
            submit.disabled = bad > 0 || sum === 0;
        }

        rows.forEach(function (row) {
            var input = row.querySelector('[data-qty]');
            input.addEventListener('input', update);
            var all = row.querySelector('[data-qty-all]');
            all.addEventListener('click', function () {
                input.value = input.getAttribute('max');
                update();
            });
        });
        form.addEventListener('submit', function (e) {
            update();
            if (submit.disabled) e.preventDefault();
        });
        update();
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('[data-single-move]').forEach(setupSingle);
        document.querySelectorAll('[data-batch-move]').forEach(setupBatch);
    });
})();
