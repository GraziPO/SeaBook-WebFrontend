/*
 * ETIQUETAS (Code 128, biblioteca JsBarcode no próprio projeto: funciona sem internet).
 *
 * Tamanho FIXO: 50 × 30 mm, impressora de 203 dpi. Para mudar, troque LABEL_W_MM aqui e os
 * pontos marcados com "TAMANHO DA ETIQUETA" no css/app.css.
 *
 * Servem para os exemplares (tela de etiquetas) e para as localizações (Acervo > Departamentos).
 * As etiquetas ficam escondidas na página (.label-roll.print-only). Na hora de imprimir, as que vão
 * sair são copiadas para #label-print-area, filha direta do <body>, e só ela vai para o papel.
 *
 * Nitidez na térmica: a barra mais fina ("módulo") sempre tem um número INTEIRO de pontos da
 * impressora (0,125 mm em 203 dpi). Usa o maior módulo que cabe na largura. 1 ponto (0,125 mm)
 * é lido pelos leitores comuns; se nem 1 ponto couber (código longo demais para 50 mm), as barras
 * ficariam espremidas e o aviso [data-label-warn] aparece na tela.
 *
 * Quem usa (ver js/app.js e templates/components/labels.html):
 *   data-print="labels"                       imprime todas as etiquetas da página
 *   data-print="labels" data-label-code="X"   imprime só a etiqueta X
 *   data-label-preview="X"                    (no botão que abre o modal) mostra a etiqueta X em
 *                                             [data-label-preview-slot] e aponta o botão de imprimir
 *                                             do modal ([data-label-print]) para ela
 */
(function () {
    'use strict';

    var LABEL_W_MM = 50;       // TAMANHO DA ETIQUETA (largura)
    var PAD_MM = 2.5;          // margem interna dos lados (igual ao padding do .copy-label)
    var DPI = 203;
    var DOT_MM = 25.4 / DPI;
    var BAR_H = 40;            // altura de desenho; a altura real vem do CSS
    var CODE_PT = 6.5;         // letra do código em texto (igual ao .copy-label-code do CSS)
    var MONO_EM = 0.62;        // largura de um caractere da fonte monoespaçada, em "em"
    var PT_MM = 25.4 / 72;

    /** O código em texto embaixo das barras: diminui a letra até caber inteiro na largura. */
    function fitCodeText(el) {
        var len = (el.textContent || '').trim().length;
        if (!len) return;
        var maxPt = (LABEL_W_MM - 2 * PAD_MM) / (len * MONO_EM * PT_MM);
        if (maxPt < CODE_PT) {
            el.style.fontSize = Math.max(maxPt, 4).toFixed(2) + 'pt';
            el.style.letterSpacing = '0';
        }
    }

    function render(svg) {
        var code = svg.getAttribute('data-code');
        try {
            JsBarcode(svg, code, { format: 'CODE128', width: 1, height: BAR_H, margin: 0, displayValue: false });
            var modules = parseFloat(svg.getAttribute('width'));
            var dots = Math.floor((LABEL_W_MM - 2 * PAD_MM) / (modules * DOT_MM));
            var tooLong = dots < 1;
            if (dots < 1) dots = 1;
            svg.setAttribute('viewBox', '0 0 ' + modules + ' ' + BAR_H);
            svg.setAttribute('preserveAspectRatio', 'none');
            svg.removeAttribute('width');
            svg.removeAttribute('height');
            svg.style.width = (modules * dots * DOT_MM).toFixed(3) + 'mm';
            svg.style.maxWidth = '100%';
            return tooLong ? code : null;
        } catch (e) {
            svg.style.display = 'none';
            return null;
        }
    }

    function renderAll() {
        var tooLong = [];
        document.querySelectorAll('.label-roll svg.barcode[data-code]').forEach(function (svg) {
            var code = render(svg);
            if (code) tooLong.push(code);
        });
        document.querySelectorAll('.label-roll .copy-label-code').forEach(fitCodeText);
        document.querySelectorAll('[data-label-warn]').forEach(function (warn) {
            warn.hidden = tooLong.length === 0;
            warn.textContent = tooLong.length
                ? 'Código longo demais para a etiqueta de 50 mm em ' + tooLong.length + ' caso(s): as barras ficam espremidas e o leitor pode não ler. Use um código de localização mais curto.'
                : '';
        });
    }

    function labelsFor(code) {
        var all = Array.prototype.slice.call(document.querySelectorAll('.label-roll .copy-label'));
        if (!code) return all;
        return all.filter(function (el) { return el.getAttribute('data-label-code') === code; });
    }

    /** Imprime todas as etiquetas da página, ou só a do código informado. */
    function print(code) {
        var labels = labelsFor(code);
        if (labels.length === 0) return;

        var old = document.getElementById('label-print-area');
        if (old) old.remove();
        var area = document.createElement('div');
        area.id = 'label-print-area';
        labels.forEach(function (el) { area.appendChild(el.cloneNode(true)); });
        document.body.appendChild(area);
        document.body.classList.add('printing-labels');

        var cleaned = false;
        function cleanup() {
            if (cleaned) return;
            cleaned = true;
            document.body.classList.remove('printing-labels');
            area.remove();
            window.removeEventListener('afterprint', cleanup);
        }
        window.addEventListener('afterprint', cleanup);
        window.print();
        if (!('onafterprint' in window)) setTimeout(cleanup, 1000);   // navegador sem afterprint
    }

    /** Modal da etiqueta da localização: copia a etiqueta para a prévia e aponta o botão de imprimir. */
    document.addEventListener('click', function (event) {
        var trigger = event.target.closest('[data-label-preview]');
        if (!trigger) return;
        var code = trigger.getAttribute('data-label-preview');
        var dialog = document.getElementById(trigger.getAttribute('data-open-modal'));
        if (!dialog) return;
        var slot = dialog.querySelector('[data-label-preview-slot]');
        var label = labelsFor(code)[0];
        if (slot) {
            slot.innerHTML = '';
            if (label) slot.appendChild(label.cloneNode(true));
        }
        dialog.querySelectorAll('[data-label-print]').forEach(function (button) {
            button.setAttribute('data-label-code', code);
        });
    });

    window.SeaBookLabels = { print: print };

    if (window.JsBarcode) renderAll();
})();
