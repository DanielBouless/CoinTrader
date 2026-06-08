function Stat({ label, value, highlight }) {
  return (
    <div className={`metric-card ${highlight ? `metric-${highlight}` : ''}`}>
      <div className="metric-label">{label}</div>
      <div className="metric-value">{value}</div>
    </div>
  );
}

export default function Metrics({ metrics }) {
  if (!metrics) return null;
  const { totalReturn, buyHoldReturn, finalEquity, initialCapital,
          totalTrades, winRate, avgWin, avgLoss,
          maxDrawdown, sharpeRatio, profitFactor } = metrics;

  const retHighlight = totalReturn > 0 ? 'green' : totalReturn < 0 ? 'red' : '';
  const vsHold = totalReturn - buyHoldReturn;

  return (
    <div className="metrics-grid">
      <Stat label="Total Return"      value={`${totalReturn > 0 ? '+' : ''}${totalReturn}%`}  highlight={retHighlight} />
      <Stat label="Buy & Hold"        value={`${buyHoldReturn > 0 ? '+' : ''}${buyHoldReturn}%`} />
      <Stat label="vs Buy & Hold"     value={`${vsHold > 0 ? '+' : ''}${vsHold.toFixed(2)}%`}   highlight={vsHold > 0 ? 'green' : 'red'} />
      <Stat label="Final Equity"      value={`$${finalEquity.toLocaleString()}`} />
      <Stat label="Total Trades"      value={totalTrades} />
      <Stat label="Win Rate"          value={`${winRate}%`} highlight={winRate >= 50 ? 'green' : 'red'} />
      <Stat label="Avg Win"           value={`$${avgWin.toFixed(2)}`}  highlight="green" />
      <Stat label="Avg Loss"          value={`$${avgLoss.toFixed(2)}`} highlight="red" />
      <Stat label="Max Drawdown"      value={`${maxDrawdown}%`}        highlight="red" />
      <Stat label="Sharpe (365d)"     value={sharpeRatio} highlight={sharpeRatio >= 1 ? 'green' : ''} />
      <Stat label="Profit Factor"     value={isFinite(profitFactor) ? profitFactor : '∞'} highlight={profitFactor >= 1.5 ? 'green' : ''} />
    </div>
  );
}
