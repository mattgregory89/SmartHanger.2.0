// LoginPage.jsx
import React, { useState } from 'react';
import { api } from '../api.js';

export default function LoginPage({ onLogin }) {
  const [username, setUsername] = useState('maintainer');
  const [password, setPassword] = useState('demo123');
  const [error, setError] = useState('');

  async function submit(e) {
    e.preventDefault();
    setError('');
    try {
      onLogin(await api.login(username, password));
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <div className="login-screen">
      <form className="login-card" onSubmit={submit}>
        <div className="logo-mark">SH</div>
        <h1>SmartHangar</h1>
        <p>Aircraft Maintenance Tracker</p>
        <label>Username<input value={username} onChange={e => setUsername(e.target.value)} /></label>
        <label>Password<input type="password" value={password} onChange={e => setPassword(e.target.value)} /></label>
        {error && <div className="error">{error}</div>}
        <button type="submit">Sign in</button>
        <small>Demo: maintainer / demo123</small>
      </form>
    </div>
  );
}
