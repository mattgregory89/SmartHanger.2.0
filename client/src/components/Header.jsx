// Header.jsx
import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

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
        <Link className="brand" to="/">SmartHangar</Link>
        <span className="subtitle">Aircraft Maintenance Tracker</span>
      </div>
      <form className="search-form" onSubmit={submit}>
        <input value={query} onChange={e => setQuery(e.target.value)} placeholder="Search tail, discrepancy, Red X..." />
      </form>
      <div className="user-area">
        <div><strong>{user.full_name}</strong><small>{user.role}</small></div>
        <button className="secondary" onClick={onLogout}>Log out</button>
      </div>
    </header>
  );
}
