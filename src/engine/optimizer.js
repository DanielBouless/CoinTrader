import { williamsR, dmi } from './indicators';

const DMI_PERIODS  = [5, 7, 10, 14, 20, 25, 30];
const WR_PERIODS   = [5, 7, 10, 14, 20, 25, 30];
const LOWER_THRESH = [-95, -90, -85, -80, -75, -70];
const UPPER_THRESH = [-30, -25, -20, -15, -10, -5];
const CHUNK_SIZE   = 60;

function scoreMetrics(metrics, mode) {
  if (!metrics || metrics.totalTrades === 0) return -Infinity;
  if (mode === 'winrate') return metrics.winRate    ?? -Infinity;
  if (mode === 'avgwin')  return metrics.avgWin     ?? -Infinity;
  return metrics.totalReturn ?? -Infinity;
}

function backtestCached(data, plusDI, minusDI, wr, lowerThreshold, upperThreshold, initialCapital) {
  const n = data.length;

  let armed = false;
  const buyZone = new Uint8Array(n);
  for (let i = 1; i < n; i++) {
    if (wr[i] === null || plusDI[i] === null || minusDI[i] === null) continue;
    if (wr[i] <= lowerThreshold) armed = true;
    const dmiCross = plusDI[i] > minusDI[i] &&
                     plusDI[i - 1] !== null && plusDI[i - 1] <= minusDI[i - 1];
    if (armed && dmiCross) { buyZone[i] = 1; armed = false; }
  }

  const signals = new Int8Array(n);
  for (let i = 1; i < n; i++) {
    if (buyZone[i]) signals[i] = 1;
    if (plusDI[i] !== null && minusDI[i] !== null &&
        minusDI[i] > plusDI[i] && minusDI[i - 1] !== null && minusDI[i - 1] <= plusDI[i - 1])
      signals[i] = -1;
  }

  let capital = initialCapital;
  let position = null;
  let totalTrades = 0, wins = 0, winPnl = 0, lossPnl = 0;
  let peak = initialCapital, maxDrawdown = 0;
  const equityVals = [];

  for (let i = 0; i < n; i++) {
    if (signals[i] === 1 && !position) {
      const units = capital / data[i].close;
      if (units > 0) {
        capital -= units * data[i].close;
        position = { entryPrice: data[i].close, units };
      }
    } else if (signals[i] === -1 && position) {
      capital += position.units * data[i].close;
      const pnl = (data[i].close - position.entryPrice) * position.units;
      totalTrades++;
      if (pnl > 0) { wins++; winPnl += pnl; } else { lossPnl += pnl; }
      position = null;
    }
    const equity = capital + (position ? position.units * data[i].close : 0);
    equityVals.push(equity);
    if (equity > peak) peak = equity;
    const dd = (peak - equity) / peak * 100;
    if (dd > maxDrawdown) maxDrawdown = dd;
  }

  if (totalTrades === 0) return null;

  const finalEquity  = equityVals[equityVals.length - 1];
  const totalReturn  = (finalEquity - initialCapital) / initialCapital * 100;
  const winRate      = (wins / totalTrades) * 100;

  let sumR = 0, sumR2 = 0;
  for (let i = 1; i < equityVals.length; i++) {
    const r = (equityVals[i] - equityVals[i - 1]) / equityVals[i - 1];
    sumR += r; sumR2 += r * r;
  }
  const m    = equityVals.length - 1;
  const avgR = sumR / m;
  const stdR = Math.sqrt(Math.max(0, sumR2 / m - avgR * avgR));
  // 365 days for crypto
  const sharpe       = stdR > 0 ? (avgR / stdR) * Math.sqrt(365) : 0;
  const profitFactor = lossPnl < 0 ? winPnl / Math.abs(lossPnl) : winPnl > 0 ? Infinity : 0;

  return {
    totalReturn:   +totalReturn.toFixed(2),
    finalEquity:   +finalEquity.toFixed(2),
    initialCapital,
    totalTrades,
    winRate:       +winRate.toFixed(1),
    avgWin:        +(wins > 0 ? winPnl / wins : 0).toFixed(2),
    avgLoss:       +(totalTrades - wins > 0 ? lossPnl / (totalTrades - wins) : 0).toFixed(2),
    maxDrawdown:   +maxDrawdown.toFixed(2),
    sharpeRatio:   +sharpe.toFixed(2),
    profitFactor:  +profitFactor.toFixed(2),
    buyHoldReturn: +((data[n - 1].close - data[0].close) / data[0].close * 100).toFixed(2),
  };
}

export async function autoOptimize(data, initialCapital = 10000, mode = 'return', onProgress) {
  if (data.length < 60) return null;

  const dmiCache = {};
  for (const p of DMI_PERIODS) dmiCache[p] = dmi(data, p);
  const wrCache = {};
  for (const p of WR_PERIODS) wrCache[p] = williamsR(data, p);

  const combos = [];
  for (const dmiPeriod of DMI_PERIODS)
    for (const wrPeriod of WR_PERIODS)
      for (const lowerThreshold of LOWER_THRESH)
        for (const upperThreshold of UPPER_THRESH)
          combos.push({ dmiPeriod, wrPeriod, lowerThreshold, upperThreshold });

  let best = null;
  let bestScore = -Infinity;

  for (let i = 0; i < combos.length; i += CHUNK_SIZE) {
    const chunk = combos.slice(i, i + CHUNK_SIZE);
    for (const { dmiPeriod, wrPeriod, lowerThreshold, upperThreshold } of chunk) {
      const { plusDI, minusDI } = dmiCache[dmiPeriod];
      const wr = wrCache[wrPeriod];
      const metrics = backtestCached(data, plusDI, minusDI, wr, lowerThreshold, upperThreshold, initialCapital);
      const s = scoreMetrics(metrics, mode);
      if (s > bestScore) { bestScore = s; best = { dmiPeriod, wrPeriod, lowerThreshold, upperThreshold, metrics }; }
    }
    onProgress?.(Math.min(i + CHUNK_SIZE, combos.length), combos.length);
    await new Promise(r => setTimeout(r, 0));
  }

  if (!best) return null;
  return {
    dmiPeriod:      best.dmiPeriod,
    wrPeriod:       best.wrPeriod,
    lowerThreshold: best.lowerThreshold,
    upperThreshold: best.upperThreshold,
    metrics:        best.metrics,
    score:          +bestScore.toFixed(4),
    totalCombos:    combos.length,
  };
}
