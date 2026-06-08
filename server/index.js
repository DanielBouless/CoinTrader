import express from 'express';
import cors from 'cors';
import fetch from 'node-fetch';
import crypto from 'crypto';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import dotenv from 'dotenv';

const __dirname = dirname(fileURLToPath(import.meta.url));
dotenv.config({ path: join(__dirname, '..', '.env') });

const PORT     = process.env.PORT || 3001;
const API_BASE = 'https://api.coinbase.com';
const KEY_NAME = process.env.COINBASE_API_KEY_NAME  || '';
const PRIV_KEY = (process.env.COINBASE_API_PRIVATE_KEY || '').replace(/\\n/g, '\n');

const app = express();
app.use(cors());
app.use(express.json());

// ── JWT auth (Coinbase Advanced Trade uses ES256 JWTs) ─────────────────────

function buildJwt(method, path) {
  if (!KEY_NAME || !PRIV_KEY) return null;
  const now = Math.floor(Date.now() / 1000);
  const header  = { alg: 'ES256', kid: KEY_NAME };
  const payload = {
    sub: KEY_NAME,
    iss: 'cdp',
    nbf: now,
    exp: now + 120,
    uri: `${method} api.coinbase.com${path}`,
  };
  const encode  = obj => Buffer.from(JSON.stringify(obj)).toString('base64url');
  const signing = `${encode(header)}.${encode(payload)}`;
  const sig     = crypto.createSign('SHA256').update(signing).sign(PRIV_KEY, 'base64url');
  return `${signing}.${sig}`;
}

function authHeaders(method, path) {
  const jwt = buildJwt(method, path);
  if (!jwt) return {};
  return { Authorization: `Bearer ${jwt}`, 'Content-Type': 'application/json' };
}

async function cbFetch(method, path, body = null) {
  const opts = {
    method,
    headers: { ...authHeaders(method, path), Accept: 'application/json' },
  };
  if (body) opts.body = JSON.stringify(body);
  const res = await fetch(`${API_BASE}${path}`, opts);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Coinbase ${res.status}: ${text}`);
  }
  return res.json();
}

// ── Status ─────────────────────────────────────────────────────────────────

app.get('/api/status', async (req, res) => {
  const hasCredentials = !!(KEY_NAME && PRIV_KEY);
  if (!hasCredentials) return res.json({ hasCredentials: false, authenticated: false });
  try {
    await cbFetch('GET', '/api/v3/brokerage/accounts?limit=1');
    res.json({ hasCredentials: true, authenticated: true });
  } catch {
    res.json({ hasCredentials: true, authenticated: false });
  }
});

// ── Products (crypto pairs) ────────────────────────────────────────────────

app.get('/api/products', async (req, res) => {
  try {
    const data = await cbFetch('GET', '/api/v3/brokerage/products?product_type=SPOT&limit=250');
    const products = (data.products || [])
      .filter(p => p.quote_currency_id === 'USD' && !p.is_disabled)
      .map(p => ({
        id:           p.product_id,
        baseCurrency: p.base_currency_id,
        price:        p.price,
        volume24h:    p.volume_24h,
      }))
      .sort((a, b) => a.id.localeCompare(b.id));
    res.json(products);
  } catch (err) { res.status(500).json({ error: err.message }); }
});

// ── Candles (OHLCV price history) ─────────────────────────────────────────

app.get('/api/candles', async (req, res) => {
  const { product_id, days = '365' } = req.query;
  if (!product_id) return res.status(400).json({ error: 'product_id required' });

  const numDays = parseInt(days, 10);
  // Coinbase candle granularity options (seconds)
  // ONE_DAY = 86400
  const granularity = 'ONE_DAY';
  const end   = Math.floor(Date.now() / 1000);
  const start = end - numDays * 86400;

  try {
    // Coinbase limits to 300 candles per request; paginate if needed
    const allCandles = [];
    let chunkEnd = end;

    while (chunkEnd > start) {
      const chunkStart = Math.max(start, chunkEnd - 299 * 86400);
      const path = `/api/v3/brokerage/products/${encodeURIComponent(product_id)}/candles` +
        `?start=${chunkStart}&end=${chunkEnd}&granularity=${granularity}`;

      let data;
      try {
        data = await cbFetch('GET', path);
      } catch {
        // If authenticated fetch fails, try public endpoint
        const pubRes = await fetch(`${API_BASE}/api/v3/brokerage/market/products/${encodeURIComponent(product_id)}/candles` +
          `?start=${chunkStart}&end=${chunkEnd}&granularity=${granularity}`);
        if (!pubRes.ok) throw new Error(`Candles fetch failed: ${pubRes.status}`);
        data = await pubRes.json();
      }

      const candles = (data.candles || []).map(c => ({
        date:   new Date(parseInt(c.start, 10) * 1000).toISOString().split('T')[0],
        open:   +parseFloat(c.open).toFixed(6),
        high:   +parseFloat(c.high).toFixed(6),
        low:    +parseFloat(c.low).toFixed(6),
        close:  +parseFloat(c.close).toFixed(6),
        volume: +parseFloat(c.volume).toFixed(4),
      }));
      allCandles.push(...candles);

      if (chunkStart <= start) break;
      chunkEnd = chunkStart - 1;
    }

    // Deduplicate and sort ascending
    const seen = new Set();
    const unique = allCandles
      .filter(c => { if (seen.has(c.date)) return false; seen.add(c.date); return true; })
      .sort((a, b) => a.date.localeCompare(b.date));

    res.json({ product_id, candles: unique });
  } catch (err) { res.status(500).json({ error: err.message }); }
});

// ── Accounts / Portfolio ───────────────────────────────────────────────────

app.get('/api/accounts', async (req, res) => {
  try {
    const data = await cbFetch('GET', '/api/v3/brokerage/accounts?limit=50');
    res.json(data.accounts || []);
  } catch (err) { res.status(500).json({ error: err.message }); }
});

// ── Orders ─────────────────────────────────────────────────────────────────

app.get('/api/orders', async (req, res) => {
  try {
    const data = await cbFetch('GET', '/api/v3/brokerage/orders/historical/batch?limit=50&order_status=FILLED');
    res.json(data.orders || []);
  } catch (err) { res.status(500).json({ error: err.message }); }
});

// Place a market order
app.post('/api/orders', async (req, res) => {
  const { product_id, side, base_size, quote_size } = req.body;
  if (!product_id || !side) return res.status(400).json({ error: 'product_id and side required' });

  const order_configuration = base_size
    ? { market_market_ioc: { base_size: String(base_size) } }
    : { market_market_ioc: { quote_size: String(quote_size || 100) } };

  try {
    const data = await cbFetch('POST', '/api/v3/brokerage/orders', {
      client_order_id: `ct_${Date.now()}`,
      product_id,
      side: side.toUpperCase(),
      order_configuration,
    });
    res.json({ success: true, order: data });
  } catch (err) { res.status(500).json({ error: err.message }); }
});

// ── Start ──────────────────────────────────────────────────────────────────

app.listen(PORT, () => {
  console.log(`\n  CoinTrader server running on http://localhost:${PORT}`);
  console.log(`  Coinbase credentials: ${!!(KEY_NAME && PRIV_KEY) ? 'configured' : 'missing — copy .env.example to .env'}\n`);
});
