function syncColors(pickerId, inputId) {
    debugger;
    let colorMap = document.getElementById(inputId);
    let picker = document.getElementById(pickerId);
    let hueStr = '0', satStr = '0', valStr = '0';
    let colorStr = colorMap.value;

    if (colorStr) {
        try {
            colorStr = colorStr.replace(/(\w+):/g, '"$1":');
            colorStr = colorStr.slice(1).slice(0, -1);
            const parsedColor = JSON.parse(`{${colorStr}}`);
            hueStr = `${parsedColor.hue}`;
            satStr = `${parsedColor.saturation}`;
            levStr = `${parsedColor.level}`;

            let hue = parseFloat(hueStr) / 100;
            let sat = parseFloat(satStr) / 100;
            let level = parseFloat(levStr) / 100;

            let RGB = HSVtoRGB(hue, sat, level);

            picker.value = "#" + ((1 << 24) + (RGB.r << 16) + (RGB.g << 8) + RGB.b).toString(16).slice(1);
            return;
        } catch (e) {
            // ignore
        }
    }

    // Otherwise, read the picker and populate the Map input
    let hexString = picker.value;

    let rgb = {
        r: parseInt(hexString.substring(1, 3), 16),
        g: parseInt(hexString.substring(3, 5), 16),
        b: parseInt(hexString.substring(5, 7), 16)
    };
    let hsv = RGBtoHSV(rgb);
    colorMap.value = `[hue:${hsv.h * 100}, saturation:${hsv.s * 100}, level:${hsv.v * 100}]`;
}

function HSVtoRGB(h, s, v) {
    var r, g, b, i, f, p, q, t;
    if (arguments.length === 1) {
        s = h.s, v = h.v, h = h.h;
    }
    i = Math.floor(h * 6);
    f = h * 6 - i;
    p = v * (1 - s);
    q = v * (1 - f * s);
    t = v * (1 - (1 - f) * s);
    switch (i % 6) {
        case 0: r = v, g = t, b = p; break;
        case 1: r = q, g = v, b = p; break;
        case 2: r = p, g = v, b = t; break;
        case 3: r = p, g = q, b = v; break;
        case 4: r = t, g = p, b = v; break;
        case 5: r = v, g = p, b = q; break;
    }
    return {
        r: Math.round(r * 255),
        g: Math.round(g * 255),
        b: Math.round(b * 255)
    };
}

function RGBtoHSV(r, g, b) {
    if (arguments.length === 1) {
        g = r.g, b = r.b, r = r.r;
    }
    var max = Math.max(r, g, b), min = Math.min(r, g, b),
        d = max - min,
        h,
        s = (max === 0 ? 0 : d / max),
        v = max / 255;

    switch (max) {
        case min: h = 0; break;
        case r: h = (g - b) + d * (g < b ? 6 : 0); h /= 6 * d; break;
        case g: h = (b - r) + d * 2; h /= 6 * d; break;
        case b: h = (r - g) + d * 4; h /= 6 * d; break;
    }

    return {
        h: h,
        s: s,
        v: v
    };
}
