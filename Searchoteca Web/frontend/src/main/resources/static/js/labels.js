/*
 * Desenha o código de barras de cada <svg class="barcode" data-code="..."> (Code 128).
 * A biblioteca fica no próprio projeto (/vendor/jsbarcode): funciona sem internet.
 * O texto do código fica fora do SVG (div .label-code), uma vez só, como no modelo da etiqueta.
 *
 * Qualidade na impressora térmica: cada barra fina ("módulo") tem largura FIXA em milímetros,
 * múltipla do ponto da impressora. Em 203 dpi o ponto mede 0,125 mm, então 0,25 mm = 2 pontos
 * exatos: as barras saem nítidas, sem a borra de uma imagem esticada. Para impressora de 300 dpi,
 * use data-module-mm="0.254" (3 pontos) na folha de etiquetas.
 */
(function () {
    if (!window.JsBarcode) return;
    var sheet = document.querySelector('[data-module-mm]');
    var moduleMm = sheet ? parseFloat(sheet.getAttribute('data-module-mm')) : 0.25;

    document.querySelectorAll('svg.barcode[data-code]').forEach(function (svg) {
        try {
            // width 1 = 1 unidade por módulo: a largura do SVG passa a ser o número de módulos
            JsBarcode(svg, svg.getAttribute('data-code'), {
                format: 'CODE128', width: 1, height: 40, margin: 0, displayValue: false
            });
            var modules = parseFloat(svg.getAttribute('width'));
            svg.setAttribute('viewBox', '0 0 ' + modules + ' 40');
            svg.setAttribute('preserveAspectRatio', 'none');
            svg.removeAttribute('width');
            svg.removeAttribute('height');
            svg.style.width = (modules * moduleMm).toFixed(3) + 'mm';
        } catch (e) {
            svg.style.display = 'none';
        }
    });
})();
