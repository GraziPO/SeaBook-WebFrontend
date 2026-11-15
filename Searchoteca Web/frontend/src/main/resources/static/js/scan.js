/*
 * LISTA DE LEITURA (baixa, venda, empréstimo, movimentação, reimpressão de etiquetas).
 *
 * Um campo de uma linha: o leitor de código de barras "digita" o código e aperta Enter.
 * Cada leitura vai para a lista abaixo, onde a pessoa confere o livro, corrige o código ou remove
 * a linha antes de confirmar. Só a lista é enviada, num campo escondido.
 *
 *   <div data-scan
 *        data-scan-submit="id"                 envia o ID (id) ou o código da etiqueta (code)
 *        data-scan-expect="AVAILABLE,RESERVED" situações aceitas; as outras ficam marcadas como erro
 *        data-scan-price                       mostra o preço de venda e o total
 *        data-scan-lookup="/exemplares/consulta">  onde consultar o exemplar (devolve JSON)
 *
 * A consulta pode ser trocada (window.ScanLookup) — a prévia usa dados de exemplo.
 */
(function () {
    'use strict';

    var STATUS = {
        AVAILABLE: 'Disponível', LOANED: 'Emprestado', RESERVED: 'Reservado',
        SOLD: 'Vendido', DONATED: 'Doado', WRITTEN_OFF: 'Baixado'
    };

    function money(v) {
        return v == null ? '—' : 'R$ ' + Number(v).toFixed(2).replace('.', ',');
    }

    /** Consulta padrão: GET <lookup>?codigo=X -> {copyId, copyIndex, code, title, author, status, salePrice} ou 404. */
    function defaultLookup(url, code) {
        return fetch(url + '?codigo=' + encodeURIComponent(code), { headers: { Accept: 'application/json' } })
            .then(function (r) {
                if (r.status === 404) return null;
                if (!r.ok) throw new Error('HTTP ' + r.status);
                return r.json();
            });
    }

    function lookup(url, code) {
        return (window.ScanLookup || defaultLookup)(url, code);
    }

    function setup(box) {
        var input = box.querySelector('[data-scan-input]');
        var addBtn = box.querySelector('[data-scan-add]');
        var tbody = box.querySelector('[data-scan-rows]');
        var empty = box.querySelector('[data-scan-empty]');
        var summary = box.querySelector('[data-scan-summary]');
        var feedback = box.querySelector('[data-scan-feedback]');
        var hidden = box.querySelector('[data-scan-value]');
        var submitById = (box.dataset.scanSubmit || 'id') === 'id';
        var expect = (box.dataset.scanExpect || '').split(',').filter(Boolean);
        var withPrice = box.hasAttribute('data-scan-price');
        var url = box.dataset.scanLookup || '/exemplares/consulta';
        var rows = [];   // {code, copy, state: 'loading'|'ok'|'warn'|'error', message}

        function say(text, kind) {
            feedback.textContent = text || '';
            feedback.className = 'scan-feedback' + (kind ? ' ' + kind : '');
        }

        function normalize(code) {
            return (code || '').trim().toUpperCase();
        }

        function find(code) {
            return rows.findIndex(function (r) { return r.code === code; });
        }

        function evaluate(row) {
            if (!row.copy) {
                row.state = 'error';
                row.message = 'Exemplar não encontrado';
            } else if (expect.length && expect.indexOf(row.copy.status) < 0) {
                row.state = 'error';
                row.message = (STATUS[row.copy.status] || row.copy.status) + ': não pode ser usado aqui';
            } else if (withPrice && row.copy.salePrice == null) {
                row.state = 'error';
                row.message = 'Livro sem preço de venda cadastrado';
            } else {
                row.state = 'ok';
                row.message = '';
            }
        }

        function resolve(row) {
            row.state = 'loading';
            render();
            lookup(url, row.code).then(function (copy) {
                row.copy = copy;
                evaluate(row);
                render();
            }).catch(function () {
                row.copy = null;
                row.state = 'error';
                row.message = 'Não foi possível consultar agora';
                render();
            });
        }

        function add(raw) {
            var code = normalize(raw);
            if (!code) return;
            var existing = find(code);
            if (existing >= 0) {
                say('O código ' + code + ' já está na lista.', 'warn');
                var tr = tbody.children[existing];
                if (tr) { tr.classList.remove('flash'); void tr.offsetWidth; tr.classList.add('flash'); }
                return;
            }
            var row = { code: code, copy: null, state: 'loading', message: '' };
            rows.push(row);
            say('Lido: ' + code, 'ok');
            resolve(row);
        }

        function render() {
            tbody.innerHTML = '';
            rows.forEach(function (row, i) {
                var tr = document.createElement('tr');
                tr.className = 'scan-row ' + row.state;

                var tdCode = document.createElement('td');
                var codeInput = document.createElement('input');
                codeInput.type = 'text';
                codeInput.value = row.code;
                codeInput.className = 'scan-code';
                codeInput.setAttribute('aria-label', 'Código da linha ' + (i + 1));
                codeInput.addEventListener('change', function () {
                    var code = normalize(codeInput.value);
                    if (!code) { rows.splice(i, 1); render(); return; }
                    if (code !== row.code && find(code) >= 0) {
                        say('O código ' + code + ' já está na lista.', 'warn');
                        codeInput.value = row.code;
                        return;
                    }
                    row.code = code;
                    resolve(row);
                });
                codeInput.addEventListener('keydown', function (e) {
                    if (e.key === 'Enter') { e.preventDefault(); codeInput.blur(); }
                });
                tdCode.appendChild(codeInput);

                var tdBook = document.createElement('td');
                if (row.state === 'loading') {
                    tdBook.innerHTML = '<span class="muted">Consultando…</span>';
                } else if (row.copy) {
                    tdBook.innerHTML = '<strong></strong><div class="muted"></div>';
                    tdBook.querySelector('strong').textContent = row.copy.title;
                    tdBook.querySelector('.muted').textContent =
                        (row.copy.author || '') + ' · Exemplar ' + row.copy.copyIndex;
                }
                if (row.message) {
                    var msg = document.createElement('div');
                    msg.className = 'scan-row-msg';
                    msg.textContent = row.message;
                    tdBook.appendChild(msg);
                }

                var tdStatus = document.createElement('td');
                if (row.copy) {
                    var pill = document.createElement('span');
                    // a cor fala da situação do exemplar (aceita aqui ou não), não de outros problemas da linha
                    var accepted = !expect.length || expect.indexOf(row.copy.status) >= 0;
                    pill.className = 'status-pill ' + (accepted ? 'active' : 'inactive');
                    pill.textContent = STATUS[row.copy.status] || row.copy.status;
                    tdStatus.appendChild(pill);
                }

                tr.appendChild(tdCode);
                tr.appendChild(tdBook);
                tr.appendChild(tdStatus);

                if (withPrice) {
                    var tdPrice = document.createElement('td');
                    tdPrice.className = 'num';
                    tdPrice.textContent = row.copy ? money(row.copy.salePrice) : '—';
                    tr.appendChild(tdPrice);
                }

                var tdDel = document.createElement('td');
                var del = document.createElement('button');
                del.type = 'button';
                del.className = 'scan-remove';
                del.setAttribute('aria-label', 'Remover ' + row.code);
                del.textContent = '×';
                del.addEventListener('click', function () {
                    rows.splice(i, 1);
                    say('Removido: ' + row.code);
                    render();
                    input.focus();
                });
                tdDel.appendChild(del);
                tr.appendChild(tdDel);

                tbody.appendChild(tr);
            });

            empty.hidden = rows.length > 0;
            box.querySelector('.scan-table').hidden = rows.length === 0;

            var ok = rows.filter(function (r) { return r.state === 'ok'; });
            var problems = rows.length - ok.length;
            var text = rows.length + (rows.length === 1 ? ' exemplar' : ' exemplares');
            if (problems) text += ' · ' + problems + ' com problema';
            if (withPrice) {
                var total = ok.reduce(function (s, r) { return s + Number(r.copy.salePrice || 0); }, 0);
                text += ' · Total ' + money(total);
            }
            summary.textContent = rows.length ? text : '';
            summary.classList.toggle('warn', problems > 0);

            hidden.value = ok.map(function (r) {
                return submitById ? r.copy.copyId : ((r.copy && r.copy.code) || r.code);
            }).join('\n');
            box.dataset.scanReady = rows.length > 0 && problems === 0 ? 'true' : 'false';
        }

        input.addEventListener('keydown', function (e) {
            if (e.key === 'Enter') {      // o leitor termina cada código com Enter
                e.preventDefault();
                add(input.value);
                input.value = '';
            }
        });
        addBtn.addEventListener('click', function () {
            add(input.value);
            input.value = '';
            input.focus();
        });

        // não deixa enviar com a lista vazia, consultando ou com problema
        var form = box.closest('form');
        if (form) {
            form.addEventListener('submit', function (e) {
                if (box.dataset.scanReady !== 'true') {
                    e.preventDefault();
                    e.stopImmediatePropagation();
                    say(rows.length === 0
                        ? 'Leia ao menos um exemplar.'
                        : 'Corrija ou remova as linhas marcadas antes de confirmar.', 'warn');
                    input.focus();
                }
            }, true);
            form.addEventListener('reset', function () {
                rows = [];
                say('');
                setTimeout(render, 0);
            });
        }

        /** Para reabrir depois de um erro do backend: preenche a lista com os códigos enviados. */
        box.scanLoad = function (text) {
            rows = [];
            (text || '').split(/[\s,;]+/).forEach(add);
            render();
        };

        render();

        // tela sem modal (ex.: Vendas) que voltou com erro: recarrega a lista enviada
        var restore = box.closest('[data-scan-restore]');
        if (restore && restore.dataset.scanRestore) box.scanLoad(restore.dataset.scanRestore);
    }

    document.querySelectorAll('[data-scan]').forEach(setup);
})();
