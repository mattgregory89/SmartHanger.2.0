// Header.jsx
import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import OfflineStatus from './OfflineStatus.jsx';
import SmartHangarLogo from './SmartHangarLogo.jsx';

export default function Header({ user, onLogout }) {
  const [query, setQuery] = useState('');
  const navigate = useNavigate();

  function submit(e) {
    e.preventDefault();
    if (query.trim()) navigate(`/search?q=${encodeURIComponent(query.trim())}`);
  }

  return (
    <header className="topbar">
      <div className="brand-area">
        <div className="brand-row">
          <Link to="/">
            <SmartHangarLogo size={48} />
          </Link>

          <div>
            <Link className="brand" to="/">SmartHangar</Link>
            <span className="subtitle">Aircraft Maintenance Tracker</span>
          </div>
        </div>

        <div className="header-links">
          <Link className="demo-link" to="/offline-demo">Offline Demo</Link>
          <Link className="demo-link" to="/analytics">Analytics</Link>
        </div>
      </div>
      <form className="search-form" onSubmit={submit}>
        <input value={query} onChange={e => setQuery(e.target.value)} placeholder="Search tail, discrepancy, Red X..." />
      </form>
      <OfflineStatus />
      <div className="user-area">
        <div><strong>{user.full_name}</strong><small>{user.role}</small></div>
        <button className="secondary" onClick={onLogout}>Log out</button>
      </div>
    </header>
  );
}
