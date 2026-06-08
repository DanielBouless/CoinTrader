import {
  ComposedChart, Line, Area, Scatter,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend,
  ReferenceLine, ReferenceArea,
  ResponsiveContainer,
} from 'recharts';

const SYNC = 'ctdmi';

function zoneRanges(data, key) {
  const ranges = [];
  let start = null;
  for (let i = 0; i < data.length; i++) {
    const active = data[i][key] !== null && data[i][key] !== undefined;
    if (active && start === null) start = data[i].date;
    if (!active && start !== null) {
      ranges.push({ x1: start, x2: data[i - 1].date });
      start = null;
    }
  }
  if (start !== null) ranges.push({ x1: start, x2: data[data.length - 1].date });
  return ranges;
}

function fmt(v) {
  if (v == null) return '';
  if (v >= 1000) return `$${v.toLocaleString(undefined, { maximumFractionDigits: 2 })}`;
  if (v >= 1)    return `$${v.toFixed(4)}`;
  return `$${v.toFixed(6)}`;
}

function PriceTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  const d = payload[0]?.payload;
  return (
    <div className="tt">
      <div className="tt-date">{label}</div>
      {d?.close != null && <div>O: {fmt(d.open)} H: {fmt(d.high)} L: {fmt(d.low)} C: {fmt(d.close)}</div>}
      {d?.buySignal  != null && <div className="tt-buy">● Buy signal</div>}
      {d?.sellSignal != null && <div className="tt-sell">● Sell signal</div>}
    </div>
  );
}

function DmiTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="tt">
      <div className="tt-date">{label}</div>
      {payload.filter(p => p.value != null && ['plusDI', 'minusDI'].includes(p.dataKey)).map(p => (
        <div key={p.dataKey} style={{ color: p.color }}>{p.name}: {(+p.value).toFixed(2)}</div>
      ))}
    </div>
  );
}

function WrTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  const wr = payload.find(p => p.dataKey === 'williamsR');
  if (!wr) return null;
  return (
    <div className="tt">
      <div className="tt-date">{label}</div>
      <div style={{ color: '#ffa726' }}>W%R: {(+wr.value).toFixed(2)}</div>
    </div>
  );
}

export default function Chart({ data, lowerThreshold = -80, upperThreshold = -20 }) {
  if (!data?.length) return null;

  const interval = Math.max(1, Math.floor(data.length / 10));
  const xProps = {
    dataKey: 'date',
    tick: { fontSize: 11, fill: '#6e7681' },
    interval,
    tickFormatter: v => v?.slice(5) ?? v,
  };

  const buyRanges  = zoneRanges(data, 'buyZoneActive');
  const sellRanges = zoneRanges(data, 'sellSignal');

  return (
    <div className="chart-stack">
      {/* Price */}
      <div className="chart-panel">
        <div className="panel-label">Price</div>
        <ResponsiveContainer width="100%" height={270}>
          <ComposedChart data={data} margin={{ top: 4, right: 16, bottom: 0, left: 8 }} syncId={SYNC}>
            <CartesianGrid strokeDasharray="3 3" stroke="#21262d" />
            <XAxis {...xProps} />
            <YAxis domain={['auto', 'auto']} tick={{ fontSize: 11, fill: '#6e7681' }}
              tickFormatter={v => fmt(v)} width={80} />
            <Tooltip content={<PriceTooltip />} />
            <Legend iconSize={10} wrapperStyle={{ fontSize: 11 }} />
            {buyRanges.map((r, i)  => <ReferenceArea key={`pb${i}`}  x1={r.x1} x2={r.x2} fill="#3fb95018" ifOverflow="visible" />)}
            {sellRanges.map((r, i) => <ReferenceArea key={`ps${i}`}  x1={r.x1} x2={r.x2} fill="#f8514918" ifOverflow="visible" />)}
            <Line type="monotone" dataKey="close" stroke="#c9d1d9" dot={false} strokeWidth={1.5} name="Price" connectNulls />
            <Scatter dataKey="buySignal"  fill="#3fb950" name="Buy"  shape="circle" legendType="circle" />
            <Scatter dataKey="sellSignal" fill="#f85149" name="Sell" shape="circle" legendType="circle" />
          </ComposedChart>
        </ResponsiveContainer>
      </div>

      {/* DMI */}
      <div className="chart-panel">
        <div className="panel-label">DMI (+DI / −DI)</div>
        <ResponsiveContainer width="100%" height={175}>
          <ComposedChart data={data} margin={{ top: 4, right: 16, bottom: 0, left: 8 }} syncId={SYNC}>
            <CartesianGrid strokeDasharray="3 3" stroke="#21262d" />
            <XAxis {...xProps} />
            <YAxis domain={[0, 'auto']} tick={{ fontSize: 11, fill: '#6e7681' }} />
            <Tooltip content={<DmiTooltip />} />
            <Legend iconSize={10} wrapperStyle={{ fontSize: 11 }} />
            {buyRanges.map((r, i)  => <ReferenceArea key={`db${i}`}  x1={r.x1} x2={r.x2} fill="#3fb95018" ifOverflow="visible" />)}
            {sellRanges.map((r, i) => <ReferenceArea key={`ds${i}`}  x1={r.x1} x2={r.x2} fill="#f8514918" ifOverflow="visible" />)}
            <Line type="monotone" dataKey="plusDI"  stroke="#3fb950" dot={false} strokeWidth={2} name="+DI" connectNulls />
            <Line type="monotone" dataKey="minusDI" stroke="#f85149" dot={false} strokeWidth={2} name="−DI" connectNulls />
          </ComposedChart>
        </ResponsiveContainer>
      </div>

      {/* Williams %R */}
      <div className="chart-panel">
        <div className="panel-label">Williams %R</div>
        <ResponsiveContainer width="100%" height={155}>
          <ComposedChart data={data} margin={{ top: 4, right: 16, bottom: 4, left: 8 }} syncId={SYNC}>
            <CartesianGrid strokeDasharray="3 3" stroke="#21262d" />
            <XAxis {...xProps} />
            <YAxis domain={[-100, 0]} ticks={[-100, -80, -60, -40, -20, 0]}
              tick={{ fontSize: 11, fill: '#6e7681' }} />
            <Tooltip content={<WrTooltip />} />
            {buyRanges.map((r, i)  => <ReferenceArea key={`wb${i}`}  x1={r.x1} x2={r.x2} fill="#3fb95018" ifOverflow="visible" />)}
            {sellRanges.map((r, i) => <ReferenceArea key={`ws${i}`}  x1={r.x1} x2={r.x2} fill="#f8514918" ifOverflow="visible" />)}
            <ReferenceLine y={lowerThreshold} stroke="#3fb950" strokeDasharray="5 3"
              label={{ value: `${lowerThreshold}`, fill: '#3fb950', fontSize: 10, position: 'right' }} />
            <ReferenceLine y={upperThreshold} stroke="#f85149" strokeDasharray="5 3"
              label={{ value: `${upperThreshold}`, fill: '#f85149', fontSize: 10, position: 'right' }} />
            <Area type="monotone" dataKey="williamsR" stroke="#ffa726" fill="#ffa72618"
              dot={false} strokeWidth={1.5} name="W%R" connectNulls />
            <Scatter dataKey="wrZoneBuy"  fill="#3fb950" name="" shape="circle" legendType="none" />
            <Scatter dataKey="wrZoneSell" fill="#f85149" name="" shape="circle" legendType="none" />
          </ComposedChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
