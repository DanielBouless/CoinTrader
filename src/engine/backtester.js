import { williamsR, dmi } from './indicators';

// Two-stage Williams %R + DMI strategy:
//   Stage 1 — arm:   W%R touches lowerThreshold (oversold)
//   Stage 2 — entry: +DI crosses above -DI while armed
// Exit: -DI crosses above +DI
export function generateSignals(data, params) {
  const { plusDI, minusDI } = dmi(data, params.dmiPeriod);
  const wr = williamsR(data, params.wrPeriod);
  const n = data.length;

  let armed = false;
  const armedZone  = new Array(n).fill(false);
  const buyTrigger = new Array(n).fill(false);

  for (let i = 1; i < n; i++) {
    if (wr[i] === null || plusDI[i] === null || minusDI[i] === null) continue;
    if (wr[i] <= params.lowerThreshold) armed = true;
    armedZone[i] = armed;

    const dmiCross = plusDI[i] > minusDI[i] &&
                     plusDI[i - 1] !== null && plusDI[i - 1] <= minusDI[i - 1];
    if (armed && dmiCross) {
      buyTrigger[i] = true;
      armed = false;
    }
  }

  const sellCross = new Array(n).fill(false);
  for (let i = 1; i < n; i++) {
    if (plusDI[i] === null || minusDI[i] === null) continue;
    if (minusDI[i] > plusDI[i] && minusDI[i - 1] !== null && minusDI[i - 1] <= plusDI[i - 1])
      sellCross[i] = true;
  }

  const signals = new Array(n).fill(0);
  for (let i = 0; i < n; i++) {
    if (buyTrigger[i]) signals[i] =  1;
    if (sellCross[i])  signals[i] = -1;
  }

  return { signals, armedZone, buyZone: buyTrigger, sellZone: sellCross, plusDI, minusDI, wr };
}

export function buildChartData(data, params) {
  const { armedZone, buyZone, sellZone, plusDI, minusDI, wr } = generateSignals(data, params);
  return data.map((bar, i) => ({
    date:           bar.date,
    open:           bar.open,
    high:           bar.high,
    low:            bar.low,
    close:          bar.close,
    plusDI:         plusDI[i],
    minusDI:        minusDI[i],
    williamsR:      wr[i],
    buyZoneActive:  armedZone[i] && !buyZone[i] ? bar.close : null,
    buySignal:      buyZone[i]  ? bar.close : null,
    sellZoneActive: sellZone[i] ? bar.close : null,
    sellSignal:     sellZone[i] ? bar.close : null,
    wrZoneBuy:      buyZone[i]  ? wr[i] : null,
    wrZoneSell:     sellZone[i] ? wr[i] : null,
  }));
}

export function runBacktest(data, params, initialCapital = 10000) {
  const { signals } = generateSignals(data, params);
  const trades = [];
  let position  = null;
  let capital   = initialCapital;
  const equityCurve = [];

  for (let i = 0; i < data.length; i++) {
    if (signals[i] === 1 && !position) {
      // Crypto supports fractional units
      const units = capital / data[i].close;
      if (units > 0) {
        capital -= units * data[i].close;
        position = { entryPrice: data[i].close, entryDate: data[i].date, entryIdx: i, units };
        trades.push({ type: 'BUY', date: data[i].date, price: data[i].close, units });
      }
    } else if (signals[i] === -1 && position) {
      capital += position.units * data[i].close;
      const pnl    = (data[i].close - position.entryPrice) * position.units;
      const pnlPct = ((data[i].close - position.entryPrice) / position.entryPrice) * 100;
      trades.push({
        type: 'SELL',
        date: data[i].date,
        price: data[i].close,
        units: position.units,
        pnl:    +pnl.toFixed(2),
        pnlPct: +pnlPct.toFixed(2),
        holdingDays: i - position.entryIdx,
      });
      position = null;
    }
    const portfolioValue = capital + (position ? position.units * data[i].close : 0);
    equityCurve.push({ date: data[i].date, value: portfolioValue });
  }

  return { trades, equityCurve, metrics: computeMetrics(data, trades, equityCurve, initialCapital) };
}

function computeMetrics(data, trades, equityCurve, initialCapital) {
  const completedTrades = trades.filter(t => t.type === 'SELL');
  const wins   = completedTrades.filter(t => t.pnl > 0);
  const losses = completedTrades.filter(t => t.pnl <= 0);

  const finalEquity  = equityCurve.at(-1)?.value ?? initialCapital;
  const totalReturn  = ((finalEquity - initialCapital) / initialCapital) * 100;
  const bhReturn     = data.length > 1 ? ((data.at(-1).close - data[0].close) / data[0].close) * 100 : 0;
  const winRate      = completedTrades.length ? (wins.length / completedTrades.length) * 100 : 0;
  const avgWin       = wins.length   ? wins.reduce((s, t) => s + t.pnl, 0) / wins.length : 0;
  const avgLoss      = losses.length ? losses.reduce((s, t) => s + t.pnl, 0) / losses.length : 0;

  let peak = -Infinity, maxDrawdown = 0;
  for (const { value } of equityCurve) {
    if (value > peak) peak = value;
    const dd = (peak - value) / peak * 100;
    if (dd > maxDrawdown) maxDrawdown = dd;
  }

  const dailyReturns = equityCurve.slice(1).map((e, i) =>
    (e.value - equityCurve[i].value) / equityCurve[i].value);
  const avgR = dailyReturns.reduce((a, b) => a + b, 0) / (dailyReturns.length || 1);
  const stdR = Math.sqrt(dailyReturns.reduce((s, r) => s + (r - avgR) ** 2, 0) / (dailyReturns.length || 1));
  // Crypto trades 24/7 — annualise with 365 calendar days
  const sharpe = stdR !== 0 ? (avgR / stdR) * Math.sqrt(365) : 0;

  const grossProfit  = wins.reduce((s, t) => s + t.pnl, 0);
  const grossLoss    = Math.abs(losses.reduce((s, t) => s + t.pnl, 0));
  const profitFactor = grossLoss > 0 ? grossProfit / grossLoss : grossProfit > 0 ? Infinity : 0;

  return {
    totalReturn:   +totalReturn.toFixed(2),
    buyHoldReturn: +bhReturn.toFixed(2),
    finalEquity:   +finalEquity.toFixed(2),
    initialCapital,
    totalTrades:   completedTrades.length,
    winRate:       +winRate.toFixed(1),
    avgWin:        +avgWin.toFixed(2),
    avgLoss:       +avgLoss.toFixed(2),
    maxDrawdown:   +maxDrawdown.toFixed(2),
    sharpeRatio:   +sharpe.toFixed(2),
    profitFactor:  +profitFactor.toFixed(2),
  };
}
