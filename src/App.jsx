import { useState, useCallback, useEffect, useRef } from 'react';
import { checkStatus, fetchCandles, fetchAccounts, placeOrder } from './api/coinbase';
import { buildChartData, runBacktest } from './engine/backtester';
import { autoOptimize } from './engine/optimizer';
import Chart from './components/Chart';
import Metrics from './components/Metrics';
import TradeLog from './components/TradeLog';
import CoinbaseConnect from './components/CoinbaseConnect';
import './styles/app.css';

const DEFAULT_PARAMS  = { dmiPeriod: 14, wrPeriod: 14, lowerThreshold: -80, upperThreshold: -10 };
const POPULAR_PAIRS   = ['BTC-USD', 'ETH-USD', 'SOL-USD', 'XRP-USD', 'DOGE-USD', 'ADA-USD', 'AVAX-USD', 'LINK-USD'];
const TIMEFRAMES      = [
  { label: '3 months', days: 90  },
  { label: '6 months', days: 180 },
  { label: '1 year',   days: 365 },
  { label: '2 years',  days: 730 },
];

function Slider({ label, id, value, min, max, step = 1, onChange }) {
  return (
    <div className="slider-row">
      <div className="slider-header">
        <label htmlFor={id}>{label}</label>
        <span className="slider-val">{value}</span>
      </div>
      <input id={id} type="range" min={min} max={max} step={step} value={value}
        onChange={e => onChange(step < 1 ? +e.target.value : parseInt(e.target.value))} />
      <div className="slider-bounds"><span>{min}</span><span>{max}</span></div>
    </div>
  );
}

export default function App() {
  const [coinbaseConnected, setCoinbaseConnected] = useState(false);

  const [pairInput, setPairInput]   = useState('BTC-USD');
  const [pair, setPair]             = useState('');
  const [days, setDays]             = useState(365);
  const [params, setParams]         = useState({ ...DEFAULT_PARAMS });

  const [rawData, setRawData]       = useState(null);
  const rawDataRef                  = useRef(null);
  const [chartData, setChartData]   = useState(null);
  const [dataSource, setDataSource] = useState('');
  const [isLoading, setIsLoading]   = useState(false);

  const [isOptimizing, setIsOptimizing]       = useState(false);
  const [optimizeProgress, setOptimizeProgress] = useState(null);
  const [optimizeInfo, setOptimizeInfo]       = useState(null);
  const [showOptMenu, setShowOptMenu]         = useState(false);

  const [initialCapital, setInitialCapital] = useState(10000);
  const [capitalInput, setCapitalInput]     = useState('10000');
  const [backtestResult, setBacktestResult] = useState(null);

  const [liveMode, setLiveMode]           = useState(false);
  const [accounts, setAccounts]           = useState(null);
  const [selectedAccount, setSelectedAccount] = useState('');
  const [quoteAmount, setQuoteAmount]     = useState(100);
  const [tradeStatus, setTradeStatus]     = useState('');
  const [liveSignal, setLiveSignal]       = useState(null);

  const [tab, setTab] = useState('chart');

  useEffect(() => {
    checkStatus().then(s => setCoinbaseConnected(!!s?.authenticated)).catch(() => {});
  }, []);

  async function loadData(p, numDays) {
    if (coinbaseConnected) {
      try {
        const data = await fetchCandles(p, numDays);
        if (data?.length > 0) { setDataSource('live'); return data; }
      } catch {}
    }
    setDataSource('simulated');
    return generateFallbackData(p, numDays);
  }

  const handleLoad = useCallback(async () => {
    const p = pairInput.trim().toUpperCase();
    if (!p) return;
    setIsLoading(true);
    setBacktestResult(null);
    setOptimizeInfo(null);
    setLiveSignal(null);
    const data = await loadData(p, days);
    rawDataRef.current = data;
    setRawData(data);
    setPair(p);
    setChartData(buildChartData(data, params));
    setIsLoading(false);
  }, [pairInput, days, params, coinbaseConnected]);

  function applyParams(newParams) {
    setParams(newParams);
    const data = rawDataRef.current;
    if (data) setChartData(buildChartData(data, newParams));
    setBacktestResult(null);
  }

  function handleParamChange(key, val) {
    applyParams({ ...params, [key]: val });
  }

  function handleRunBacktest() {
    const data = rawDataRef.current;
    if (!data) return;
    const result = runBacktest(data, params, initialCapital);
    setBacktestResult(result);

    const cd = buildChartData(data, params);
    const last = cd[cd.length - 1];
    setLiveSignal(last?.buySignal ? 'BUY' : last?.sellSignal ? 'SELL' : null);
    setTab('backtest');
  }

  async function handleAutoOptimize(mode) {
    const data = rawDataRef.current;
    if (!data) return;
    setShowOptMenu(false);
    setIsOptimizing(true);
    setOptimizeProgress({ done: 0, total: 1 });
    const result = await autoOptimize(data, initialCapital, mode, (done, total) => {
      setOptimizeProgress({ done, total });
    });
    if (result) {
      const newParams = {
        ...params,
        dmiPeriod:      result.dmiPeriod,
        wrPeriod:       result.wrPeriod,
        lowerThreshold: result.lowerThreshold,
        upperThreshold: result.upperThreshold,
      };
      applyParams(newParams);
      setOptimizeInfo(result);
    }
    setIsOptimizing(false);
    setOptimizeProgress(null);
  }

  async function handleLiveToggle() {
    if (!liveMode && coinbaseConnected && !accounts) {
      try {
        const accts = await fetchAccounts();
        setAccounts(Array.isArray(accts) ? accts : []);
      } catch { setAccounts([]); }
    }
    setLiveMode(v => !v);
    setTradeStatus('');
  }

  async function handlePlaceOrder(side) {
    setTradeStatus('Placing order…');
    try {
      const result = await placeOrder({ product_id: pair, side, quote_size: quoteAmount });
      if (result?.error) throw new Error(result.error);
      setTradeStatus(`${side} order placed — $${quoteAmount}`);
    } catch (err) { setTradeStatus(`Error: ${err.message}`); }
  }

  const sourceLabel = {
    live:      '● Live (Coinbase)',
    simulated: '● Simulated',
  };

  return (
    <div className="app">
      <header className="header">
        <div className="header-brand">
          <span className="brand-mark">₿</span>
          <span className="brand-name">CoinTrader</span>
        </div>
        <nav className="header-tabs">
          {['chart', 'backtest', 'settings'].map(t => (
            <button key={t} className={`htab ${tab === t ? 'htab-active' : ''}`}
              onClick={() => setTab(t)}>
              {t === 'chart' ? 'Chart' : t === 'backtest' ? 'Backtest' : 'Settings'}
            </button>
          ))}
        </nav>
        <div className="header-right">
          <span className={`conn-dot ${coinbaseConnected ? 'conn-live' : 'conn-off'}`} />
          <span className="conn-label">{coinbaseConnected ? 'Live' : 'Simulated'}</span>
        </div>
      </header>

      <div className="body">
        <aside className="sidebar">
          {/* Pair + Timeframe */}
          <section className="sb-section">
            <div className="sb-title">Crypto Pair</div>
            <div className="sb-row">
              <input className="sb-input" value={pairInput}
                onChange={e => setPairInput(e.target.value.toUpperCase())}
                onKeyDown={e => e.key === 'Enter' && handleLoad()}
                placeholder="BTC-USD" maxLength={12} />
              <select className="sb-select" value={days} onChange={e => setDays(+e.target.value)}>
                {TIMEFRAMES.map(tf => <option key={tf.days} value={tf.days}>{tf.label}</option>)}
              </select>
            </div>
            <div className="pair-shortcuts">
              {POPULAR_PAIRS.map(p => (
                <button key={p} className={`pair-chip ${pairInput === p ? 'pair-chip-active' : ''}`}
                  onClick={() => { setPairInput(p); }}>
                  {p.replace('-USD', '')}
                </button>
              ))}
            </div>
            <button className="btn btn-primary" onClick={handleLoad} disabled={isLoading}>
              {isLoading ? 'Loading…' : 'Load Chart'}
            </button>
            {dataSource && (
              <div className={`src-badge src-${dataSource}`}>{sourceLabel[dataSource]}</div>
            )}
          </section>

          {/* Parameters */}
          <section className="sb-section">
            <div className="sb-section-hdr">
              <div className="sb-title">Parameters</div>
              {isOptimizing
                ? <span className="btn-xs btn-auto" style={{ cursor: 'default' }}>
                    {optimizeProgress
                      ? `${Math.round(optimizeProgress.done / optimizeProgress.total * 100)}%`
                      : '…'}
                  </span>
                : <button className="btn-xs btn-auto" disabled={!rawData}
                    onClick={() => setShowOptMenu(v => !v)}>
                    ⚡ Auto
                  </button>
              }
            </div>
            {showOptMenu && !isOptimizing && (
              <div className="opt-menu">
                <button className="opt-menu-btn" onClick={() => handleAutoOptimize('return')}>↑ Total Return</button>
                <button className="opt-menu-btn" onClick={() => handleAutoOptimize('winrate')}>↑ Win Rate</button>
                <button className="opt-menu-btn" onClick={() => handleAutoOptimize('avgwin')}>↑ Avg Win</button>
              </div>
            )}
            {isOptimizing && optimizeProgress && (
              <div className="opt-progress-bar">
                <div className="opt-progress-fill"
                  style={{ width: `${Math.round(optimizeProgress.done / optimizeProgress.total * 100)}%` }} />
              </div>
            )}
            {optimizeInfo && !isOptimizing && (
              <div className="opt-info">
                Best of {optimizeInfo.totalCombos} combos · Score {optimizeInfo.score} ·
                Return {optimizeInfo.metrics.totalReturn > 0 ? '+' : ''}{optimizeInfo.metrics.totalReturn}% ·
                Sharpe {optimizeInfo.metrics.sharpeRatio}
              </div>
            )}
            <Slider label="DMI Period"             id="dmi"   value={params.dmiPeriod}       min={5}   max={50}  onChange={v => handleParamChange('dmiPeriod', v)} />
            <Slider label="Williams %R Length"     id="wr"    value={params.wrPeriod}        min={5}   max={50}  onChange={v => handleParamChange('wrPeriod', v)} />
            <Slider label="Lower Threshold (arm)"  id="lower" value={params.lowerThreshold}  min={-95} max={-50} onChange={v => handleParamChange('lowerThreshold', v)} />
            <Slider label="Upper Threshold (exit)" id="upper" value={params.upperThreshold}  min={-50} max={-5}  onChange={v => handleParamChange('upperThreshold', v)} />
          </section>

          {/* Legend */}
          <section className="sb-section legend">
            <div className="leg-row">
              <span className="leg-dot green-dot" />
              <span><b>Buy:</b> W%R ≤ {params.lowerThreshold} arms signal → +DI crosses above −DI</span>
            </div>
            <div className="leg-row">
              <span className="leg-dot red-dot" />
              <span><b>Sell:</b> −DI crosses above +DI</span>
            </div>
          </section>

          {/* Backtest */}
          <section className="sb-section">
            <div className="sb-title">Starting Capital</div>
            <div className="sb-row">
              <span className="sb-label" style={{ alignSelf: 'center' }}>$</span>
              <input className="sb-input" type="number" min={100} step={1000}
                value={capitalInput}
                onChange={e => setCapitalInput(e.target.value)}
                onBlur={() => {
                  const val = Math.max(100, parseInt(capitalInput) || 10000);
                  setInitialCapital(val);
                  setCapitalInput(String(val));
                }} />
            </div>
            <button className="btn btn-primary" onClick={handleRunBacktest} disabled={!rawData}>
              Run Backtest
            </button>
          </section>

          {/* Live trading */}
          <section className="sb-section">
            <div className="sb-section-hdr">
              <div className="sb-title">Live Trading</div>
              <label className="toggle" title={!coinbaseConnected ? 'Connect Coinbase in Settings' : ''}>
                <input type="checkbox" checked={liveMode} onChange={handleLiveToggle} disabled={!coinbaseConnected} />
                <span className="toggle-track" />
              </label>
            </div>
            {!coinbaseConnected && <p className="hint">Connect Coinbase in Settings to enable.</p>}
            {liveMode && coinbaseConnected && (
              <div className="live-controls">
                {liveSignal && (
                  <div className={`live-signal live-signal-${liveSignal.toLowerCase()}`}>
                    Latest signal: <b>{liveSignal}</b>
                  </div>
                )}
                <label className="sb-label">Order Size (USD)</label>
                <input type="number" min={1} className="sb-input"
                  value={quoteAmount} onChange={e => setQuoteAmount(Math.max(1, +e.target.value || 100))} />
                <div className="order-btns">
                  <button className="btn btn-buy"  onClick={() => handlePlaceOrder('BUY')}>▲ Buy ${quoteAmount}</button>
                  <button className="btn btn-sell" onClick={() => handlePlaceOrder('SELL')}>▼ Sell ${quoteAmount}</button>
                </div>
                {tradeStatus && (
                  <div className={`trade-status ${tradeStatus.startsWith('Error') ? 'ts-err' : 'ts-ok'}`}>
                    {tradeStatus}
                  </div>
                )}
              </div>
            )}
          </section>
        </aside>

        <main className="main">
          {!chartData ? (
            <div className="placeholder">
              <div className="ph-icon">₿</div>
              <h2>CoinTrader — Williams %R + DMI</h2>
              <p>Select a crypto pair and click <b>Load Chart</b> to get started.</p>
              <div className="ph-desc">
                <div><b>Buy zone:</b> W%R becomes oversold (arms signal) → +DI crosses above −DI</div>
                <div><b>Sell zone:</b> −DI crosses above +DI</div>
                <div style={{ marginTop: 8, color: '#6e7681', fontSize: 13 }}>
                  Strategy optimised for crypto's 24/7 market using 365-day Sharpe ratio
                </div>
              </div>
            </div>
          ) : (
            <>
              {tab === 'chart' && (
                <>
                  <div className="chart-header">
                    <span className="chart-sym">{pair}</span>
                    <span className="chart-strat">Williams %R ({params.wrPeriod}) + DMI ({params.dmiPeriod})</span>
                  </div>
                  <Chart data={chartData} lowerThreshold={params.lowerThreshold} upperThreshold={params.upperThreshold} />
                  {!backtestResult && (
                    <div className="run-hint">Click <b>Run Backtest</b> to evaluate this strategy on the loaded data.</div>
                  )}
                </>
              )}

              {tab === 'backtest' && (
                <>
                  <div className="chart-header">
                    <span className="chart-sym">{pair}</span>
                    <span className="chart-strat">Backtest Results</span>
                  </div>
                  {!backtestResult ? (
                    <div className="run-hint">Click <b>Run Backtest</b> in the sidebar to see results.</div>
                  ) : (
                    <>
                      <Chart data={chartData} lowerThreshold={params.lowerThreshold} upperThreshold={params.upperThreshold} />
                      <Metrics metrics={backtestResult.metrics} />
                      <TradeLog trades={backtestResult.trades} />
                    </>
                  )}
                </>
              )}

              {tab === 'settings' && (
                <div className="settings-page">
                  <h2>Coinbase Connection</h2>
                  <CoinbaseConnect connected={coinbaseConnected} onStatusChange={setCoinbaseConnected} />
                </div>
              )}
            </>
          )}

          {tab === 'settings' && !chartData && (
            <div className="settings-page">
              <h2>Coinbase Connection</h2>
              <CoinbaseConnect connected={coinbaseConnected} onStatusChange={setCoinbaseConnected} />
            </div>
          )}
        </main>
      </div>
    </div>
  );
}

function generateFallbackData(pair, days) {
  const seeds = { 'BTC-USD': 60000, 'ETH-USD': 3000, 'SOL-USD': 150, 'XRP-USD': 0.55 };
  let price = seeds[pair] ?? 100 + (pair.charCodeAt(0) % 500);
  const data = [];
  const start = new Date();
  start.setDate(start.getDate() - days);
  // Crypto trades 24/7 — include every calendar day
  for (let i = 0; i < days; i++) {
    const d = new Date(start);
    d.setDate(d.getDate() + i);
    const vol = price * 0.03;
    const change = (Math.random() - 0.48) * vol;
    price = Math.max(price * 0.01, price + change);
    const high  = +(price + Math.random() * vol * 0.5).toFixed(6);
    const low   = +(price - Math.random() * vol * 0.5).toFixed(6);
    data.push({
      date:   d.toISOString().split('T')[0],
      open:   +(price - (Math.random() - 0.5) * vol * 0.2).toFixed(6),
      high:   Math.max(high, price),
      low:    Math.min(low,  price),
      close:  +price.toFixed(6),
      volume: +(Math.random() * 5000 + 500).toFixed(2),
    });
  }
  return data;
}
