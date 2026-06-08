import { useState } from 'react';
import { checkStatus } from '../api/coinbase';

export default function CoinbaseConnect({ connected, onStatusChange }) {
  const [checking, setChecking] = useState(false);
  const [message, setMessage] = useState('');

  async function handleCheck() {
    setChecking(true);
    setMessage('');
    const s = await checkStatus();
    onStatusChange(!!s?.authenticated);
    setMessage(s?.authenticated
      ? 'Connected to Coinbase Advanced Trade API.'
      : s?.hasCredentials
        ? 'Credentials found but authentication failed. Check your API key/secret in .env.'
        : 'No credentials found. Add COINBASE_API_KEY_NAME and COINBASE_API_PRIVATE_KEY to your .env file.'
    );
    setChecking(false);
  }

  return (
    <div className="connect-box">
      <div className={`connect-status ${connected ? 'cs-live' : 'cs-off'}`}>
        <span className="conn-dot" />
        {connected ? 'Connected — live data & trading enabled' : 'Disconnected — using simulated data'}
      </div>
      <button className="btn btn-primary" onClick={handleCheck} disabled={checking}>
        {checking ? 'Checking…' : 'Check Connection'}
      </button>
      {message && <div className="connect-msg">{message}</div>}
      <div className="connect-help">
        <p>To connect:</p>
        <ol>
          <li>Go to <strong>Coinbase → Settings → API</strong></li>
          <li>Create an <strong>Advanced Trade</strong> API key</li>
          <li>Copy the key name and private key into your <code>.env</code> file</li>
          <li>Restart the server (<code>npm run server</code>)</li>
          <li>Click <strong>Check Connection</strong></li>
        </ol>
      </div>
    </div>
  );
}
