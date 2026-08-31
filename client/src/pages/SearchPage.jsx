// SearchPage.jsx
import React, { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api.js';
import StatusBadge from '../components/StatusBadge.jsx';

export default function SearchPage() {
  const [params] = useSearchParams();
  const query = params.get('q') || '';
  const [results, setResults] = useState(null);

  useEffect(() => { api.search(query).then(setResults); }, [query]);
  if (!results) return <div>Searching...</div>;

  const count = results.aircraft.length + results.discrepancies.length + results.modifications.length;
  return (
    <>
      <div className="page-heading"><div><h1>Search</h1><p>{count} results for “{query}”</p></div></div>
      {results.aircraft.length > 0 && <section className="panel"><h2>Aircraft</h2>{results.aircraft.map(a => <Link className="search-result" key={a.id} to={`/aircraft/${a.id}`}><strong>{a.tail_number}</strong><span>{a.model}</span><StatusBadge status={a.status} /></Link>)}</section>}
      {results.discrepancies.length > 0 && <section className="panel"><h2>Discrepancies</h2>{results.discrepancies.map(d => <Link className="search-result" key={d.id} to={`/aircraft/${d.aircraft_id}`}><strong>{d.tail_number} · {d.discrepancy_number}</strong><span>{d.description}</span><StatusBadge status={d.symbol} /></Link>)}</section>}
      {results.modifications.length > 0 && <section className="panel"><h2>Modifications</h2>{results.modifications.map(m => <Link className="search-result" key={m.id} to={`/aircraft/${m.aircraft_id}`}><strong>{m.tail_number} · {m.mod_number}</strong><span>{m.title}</span><StatusBadge status={m.status} /></Link>)}</section>}
      {count === 0 && <div className="empty-state">No matching maintenance records found.</div>}
    </>
  );
}
