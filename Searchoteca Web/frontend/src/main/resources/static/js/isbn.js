/*
 * Busca por ISBN no cadastro do livro (RF05). Botão [data-isbn-lookup] dentro do formulário:
 * consulta GET /acervo/livros/isbn?isbn=... (o frontend repassa para o backend, que pergunta à Open Library)
 * e preenche título, autor, ano, editora e gênero. O operador confere e salva. Nada é gravado na busca.
 *   200 preenche | 404 não encontrado | 409 ISBN inválido ou já cadastrado | 503 Open Library fora do ar
 * A consulta pode ser trocada (window.IsbnLookup) — a prévia usa dados de exemplo.
 */
(function () {
    'use strict';

    function defaultLookup(url, isbn) {
        return fetch(url + '?isbn=' + encodeURIComponent(isbn), { headers: { Accept: 'application/json' } })
            .then(function (r) {
                return r.json().catch(function () { return {}; }).then(function (body) {
                    return { status: r.status, body: body };
                });
            });
    }

    document.addEventListener('click', function (event) {
        var button = event.target.closest('[data-isbn-lookup]');
        if (!button) return;
        var form = button.closest('form');
        var isbn = form.elements.namedItem('isbn');
        var status = form.querySelector('[data-isbn-status]');
        var value = (isbn.value || '').trim();

        function show(text, kind) {
            status.textContent = text;
            status.className = 'isbn-status ' + (kind || '');
            status.hidden = !text;
        }

        if (!value) { show('Digite ou leia o ISBN primeiro.', 'warn'); isbn.focus(); return; }

        button.disabled = true;
        show('Consultando a Open Library…');
        (window.IsbnLookup || defaultLookup)(button.dataset.isbnLookup || '/acervo/livros/isbn', value)
            .then(function (res) {
                if (res.status === 200) {
                    ['isbn', 'title', 'author', 'rel_year', 'publisher', 'genre'].forEach(function (name) {
                        var el = form.elements.namedItem(name);
                        if (el && res.body[name] != null) el.value = res.body[name];
                    });
                    show('Dados preenchidos pela Open Library. Confira antes de salvar.', 'ok');
                    var title = form.elements.namedItem('title');
                    if (title) title.focus();
                } else {
                    show((res.body && res.body.message) || 'Não foi possível consultar. Preencha manualmente.', 'warn');
                }
            })
            .catch(function () { show('Não foi possível consultar agora. Preencha manualmente.', 'warn'); })
            .then(function () { button.disabled = false; });
    });

    // Enter no campo do ISBN (o leitor manda Enter) busca, em vez de enviar o formulário
    document.addEventListener('keydown', function (event) {
        if (event.key !== 'Enter' || !event.target.matches('input[name=isbn]')) return;
        var button = event.target.form && event.target.form.querySelector('[data-isbn-lookup]');
        if (button && !event.target.readOnly) { event.preventDefault(); button.click(); }
    });
})();
