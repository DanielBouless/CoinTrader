// Williams %R: range -100 to 0. Oversold near -100, overbought near 0.
export function williamsR(data, period = 14) {
  const out = [];
  for (let i = 0; i < data.length; i++) {
    if (i < period - 1) { out.push(null); continue; }
    let hh = -Infinity, ll = Infinity;
    for (let j = i - period + 1; j <= i; j++) {
      if (data[j].high  > hh) hh = data[j].high;
      if (data[j].low   < ll) ll = data[j].low;
    }
    const range = hh - ll;
    out.push(range === 0 ? -50 : +((hh - data[i].close) / range * -100).toFixed(2));
  }
  return out;
}

// DMI (Directional Movement Index) using Wilder smoothing.
export function dmi(data, period = 14) {
  const n = data.length;
  const plusDI  = new Array(n).fill(null);
  const minusDI = new Array(n).fill(null);
  const adx     = new Array(n).fill(null);
  if (n < period + 2) return { plusDI, minusDI, adx };

  const tr = [], pdm = [], mdm = [];
  for (let i = 1; i < n; i++) {
    tr.push(Math.max(
      data[i].high - data[i].low,
      Math.abs(data[i].high - data[i - 1].close),
      Math.abs(data[i].low  - data[i - 1].close)
    ));
    const up   = data[i].high - data[i - 1].high;
    const down = data[i - 1].low - data[i].low;
    pdm.push(up > down && up > 0 ? up : 0);
    mdm.push(down > up && down > 0 ? down : 0);
  }

  let atr = tr.slice(0, period).reduce((a, b) => a + b, 0);
  let sp  = pdm.slice(0, period).reduce((a, b) => a + b, 0);
  let sm  = mdm.slice(0, period).reduce((a, b) => a + b, 0);

  const di = (v, a) => a === 0 ? 0 : +(100 * v / a).toFixed(2);
  plusDI[period]  = di(sp, atr);
  minusDI[period] = di(sm, atr);

  const dxArr = [];
  const s0 = plusDI[period] + minusDI[period];
  dxArr.push(s0 === 0 ? 0 : Math.abs(plusDI[period] - minusDI[period]) / s0 * 100);

  for (let i = period; i < tr.length; i++) {
    atr = atr - atr / period + tr[i];
    sp  = sp  - sp  / period + pdm[i];
    sm  = sm  - sm  / period + mdm[i];
    const pdi = di(sp, atr);
    const mdi = di(sm, atr);
    plusDI[i + 1]  = pdi;
    minusDI[i + 1] = mdi;
    const s = pdi + mdi;
    dxArr.push(s === 0 ? 0 : Math.abs(pdi - mdi) / s * 100);
  }

  if (dxArr.length >= period) {
    let adxVal = dxArr.slice(0, period).reduce((a, b) => a + b, 0) / period;
    adx[2 * period - 1] = +adxVal.toFixed(2);
    for (let i = period; i < dxArr.length; i++) {
      adxVal = (adxVal * (period - 1) + dxArr[i]) / period;
      adx[period + i] = +adxVal.toFixed(2);
    }
  }

  return { plusDI, minusDI, adx };
}

export function sma(data, period) {
  const out = [];
  for (let i = 0; i < data.length; i++) {
    if (i < period - 1) { out.push(null); continue; }
    let sum = 0;
    for (let j = i - period + 1; j <= i; j++) sum += data[j].close;
    out.push(+(sum / period).toFixed(6));
  }
  return out;
}
