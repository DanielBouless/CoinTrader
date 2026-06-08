const API_BASE = '/api';

export async function checkStatus() {
  try {
    const res = await fetch(`${API_BASE}/status`);
    if (!res.ok) return { hasCredentials: false, authenticated: false };
    return res.json();
  } catch {
    return { hasCredentials: false, authenticated: false };
  }
}

export async function fetchProducts() {
  const res = await fetch(`${API_BASE}/products`);
  if (!res.ok) throw new Error('Failed to fetch products');
  return res.json();
}

export async function fetchCandles(productId, days = 365) {
  const res = await fetch(`${API_BASE}/candles?product_id=${encodeURIComponent(productId)}&days=${days}`);
  if (!res.ok) throw new Error('Failed to fetch candles');
  const data = await res.json();
  return data.candles || [];
}

export async function fetchAccounts() {
  const res = await fetch(`${API_BASE}/accounts`);
  if (!res.ok) throw new Error('Failed to fetch accounts');
  return res.json();
}

export async function fetchOrders() {
  const res = await fetch(`${API_BASE}/orders`);
  if (!res.ok) throw new Error('Failed to fetch orders');
  return res.json();
}

export async function placeOrder({ product_id, side, base_size, quote_size }) {
  const res = await fetch(`${API_BASE}/orders`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ product_id, side, base_size, quote_size }),
  });
  return res.json();
}
