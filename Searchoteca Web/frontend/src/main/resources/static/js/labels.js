/*
 * ETIQUETAS ADAPTÁVEIS (Code 128, biblioteca JsBarcode no próprio projeto: funciona sem internet).
 *
 * O modelo é sempre o mesmo, empilhado na vertical: título, "Exemplar N · DEPTO / LOCAL",
 * código de barras e o código em texto. O tamanho da etiqueta é escolhido na tela
 * ([data-label-size]) e tudo se ajusta a ele: letras, altura das barras e o @page da impressão.
 *
 * Nitidez na térmica: a barra mais fina ("módulo") sempre tem um número INTEIRO de pontos da
 * impressora (0,125 mm em 203 dpi; 0,0847 mm em 300 dpi). Usa o maior módulo que cabe na largura;
 * se só couber 1 ponto (barras finas demais para muitos leitores), avisa na tela.
 * A escolha fica guardada no navegador (só conveniência; o padrão é 50 × 30 mm, 203 dpi).
 */
(function () {
    'use strict';
    var roll = document.querySelector('.label-roll');
    if (!roll || !window.JsBarcode) return;

    var form = document.querySelector('[data-label-size]');
    var warn = document.querySelector('[data-label-warn]');
    var STORE = 'searchoteca.etiqueta';
    var PAD_MM = 2.5;          // margem interna (lados)

    function load() {
        try { return JSON.parse(localStorage.getItem(STORE)) || null; } catch (e) { return null; }
    }
    function save(cfg) {
        try { localStorage.setItem(STORE, JSON.stringify(cfg)); } catch (e) { /* sem armazenamento: tudo bem */ }
    }

    function current() {
        var preset = form.elements.namedItem('preset').value;
        var w, h;
        if (preset === 'custom') {
            w = parseFloat(form.elements.namedItem('width').value);
            h = parseFloat(form.elements.namedItem('height').value);
        } else {
            var parts = preset.split('x');
            w = parseFloat(parts[0]); h = parseFloat(parts[1]);
        }
        return {
            preset: preset,
            width: Math.min(Math.max(w || 50, 25), 120),
            height: Math.min(Math.max(h || 30, 15), 120),
            dpi: parseInt(form.elements.namedItem('dpi').value, 10) || 203
        };
    }

    function apply(cfg) {
        roll.style.setProperty('--label-w', cfg.width + 'mm');
        roll.style.setProperty('--label-h', cfg.height + 'mm');

        // o tamanho do papel na impressão acompanha a etiqueta
        var style = document.getElementById('label-page-size') || document.head.appendChild(
            Object.assign(document.createElement('style'), { id: 'label-page-size' }));
        style.textContent = '@page etiqueta { size: ' + cfg.width + 'mm ' + cfg.height + 'mm; margin: 0; }';

        var dot = 25.4 / cfg.dpi;
        var available = cfg.width - 2 * PAD_MM;
        var tooLong = [];

        roll.querySelectorAll('svg.barcode[data-code]').forEach(function (svg) {
            var code = svg.getAttribute('data-code');
            try {
                JsBarcode(svg, code, { format: 'CODE128', width: 1, height: 40, margin: 0, displayValue: false });
                var modules = parseFloat(svg.getAttribute('width'));
                var dots = Math.floor(available / (modules * dot));
                if (dots < 2) { dots = 1; tooLong.push(code); }   // 1 ponto: fino demais para muitos leitores
                svg.setAttribute('viewBox', '0 0 ' + modules + ' 40');
                svg.setAttribute('preserveAspectRatio', 'none');
                svg.removeAttribute('width');
                svg.removeAttribute('height');
                svg.style.width = (modules * dots * dot).toFixed(3) + 'mm';
            } catch (e) {
                svg.style.display = 'none';
            }
        });

        if (warn) {
            warn.hidden = tooLong.length === 0;
            warn.textContent = tooLong.length
                ? 'Etiqueta estreita para ' + tooLong.length + ' código(s): as barras ficam com 1 ponto de largura e alguns leitores podem não ler. Prefira uma etiqueta mais larga.'
                : '';
        }
        var custom = form.querySelector('[data-label-custom]');
        if (custom) custom.hidden = cfg.preset !== 'custom';
    }

    // restaura a última escolha deste navegador
    var saved = load();
    if (saved) {
        if (form.querySelector('option[value="' + saved.preset + '"]')) form.elements.namedItem('preset').value = saved.preset;
        form.elements.namedItem('width').value = saved.width;
        form.elements.namedItem('height').value = saved.height;
        form.elements.namedItem('dpi').value = String(saved.dpi);
    }

    form.addEventListener('input', function () {
        var cfg = current();
        save(cfg);
        apply(cfg);
    });
    apply(current());
})();
