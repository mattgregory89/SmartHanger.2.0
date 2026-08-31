import React, { useState, useEffect } from "react";
import { Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import LoginPage from './pages/LoginPage.jsx';
import DashboardPage from './pages/DashboardPage.jsx';
import AircraftPage from './pages/AircraftPage.jsx';
import SearchPage from './pages/SearchPage.jsx';
import Header from './components/Header.jsx';

export default function App() {
  const [user, setUser] = useState(() => {
    const saved = localStorage.getItem('smarthangarUser');
    return saved ? JSON.parse(saved) : null;
  });
  const navigate = useNavigate();

  function handleLogin(nextUser) {
    localStorage.setItem('smarthangarUser', JSON.stringify(nextUser));
    setUser(nextUser);
  }

  function logout() {
    localStorage.removeItem('smarthangarUser');
    setUser(null);
    navigate('/login');
  }

  if (!user) {
    return <Routes><Route path="*" element={<LoginPage onLogin={handleLogin} />} /></Routes>;
  }

  return (
    <>
      <Header user={user} onLogout={logout} />
      <main className="page-shell">
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/aircraft/:id" element={<AircraftPage user={user} />} />
          <Route path="/search" element={<SearchPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </>
  );
}
