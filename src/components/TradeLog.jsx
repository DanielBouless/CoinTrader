export default function TradeLog({ trades }) {
  if (!trades?.length) return null;
  const sells = trades.filter(t => t.type === 'SELL').reverse();
  if (!sells.length) return <div className="trade-log-empty">No completed trades yet.</div>;

  return (
    <div className="trade-log">
      <div className="trade-log-title">Trade Log ({sells.length} trades)</div>
      <div className="trade-table-wrap">
        <table className="trade-table">
          <thead>
            <tr>
              <th>Date</th>
              <th>Units</th>
              <th>Price</th>
              <th>P&amp;L</th>
              <th>%</th>
              <th>Days</th>
            </tr>
          </thead>
          <tbody>
            {sells.map((t, i) => (
              <tr key={i} className={t.pnl >= 0 ? 'tr-win' : 'tr-loss'}>
                <td>{t.date}</td>
                <td>{typeof t.units === 'number' ? t.units.toFixed(6) : t.units}</td>
                <td>${typeof t.price === 'number' && t.price >= 1
                  ? t.price.toLocaleString(undefined, { maximumFractionDigits: 2 })
                  : t.price?.toFixed(6)}</td>
                <td className={t.pnl >= 0 ? 'pnl-pos' : 'pnl-neg'}>
                  {t.pnl >= 0 ? '+' : ''}${t.pnl?.toFixed(2)}
                </td>
                <td className={t.pnl >= 0 ? 'pnl-pos' : 'pnl-neg'}>
                  {t.pnlPct >= 0 ? '+' : ''}{t.pnlPct?.toFixed(2)}%
                </td>
                <td>{t.holdingDays ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
