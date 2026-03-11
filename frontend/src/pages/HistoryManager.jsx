import React, { useState, useEffect, useMemo } from 'react';
import { Trash2, MapPin, Clock, Search, Filter, RefreshCcw, Loader2, AlertTriangle } from 'lucide-react';

const HistoryManager = () => {
    const [records, setRecords] = useState([]);
    const [loading, setLoading] = useState(false);
    const [filterLocation, setFilterLocation] = useState('all');
    const [searchTerm, setSearchTerm] = useState('');
    const [deletingId, setDeletingId] = useState(null);

    useEffect(() => {
        fetchRecords();
    }, []);

    const fetchRecords = async () => {
        setLoading(true);
        try {
            const response = await fetch('http://localhost:8080/api/management/captures');
            if (response.ok) {
                const data = await response.json();
                setRecords(data);
            }
        } catch (error) {
            console.error('Failed to fetch history:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleDelete = async (id) => {
        if (!window.confirm('Are you sure you want to permanently delete this capture record from both MinIO and Milvus?')) return;
        
        setDeletingId(id);
        try {
            const response = await fetch(`http://localhost:8080/api/management/captures/${id}`, {
                method: 'DELETE'
            });
            if (response.ok) {
                setRecords(records.filter(r => r.id !== id));
            }
        } catch (error) {
            alert('Deletion failed. Please check backend logs.');
        } finally {
            setDeletingId(null);
        }
    };

    // Derived data for filters
    const locations = useMemo(() => {
        const locs = ['all', ...new Set(records.map(r => r.location).filter(l => l && l !== 'Unknown'))];
        return locs;
    }, [records]);

    const filteredRecords = useMemo(() => {
        return records.filter(r => {
            const matchLoc = filterLocation === 'all' || r.location === filterLocation;
            const matchSearch = r.id.toLowerCase().includes(searchTerm.toLowerCase()) || 
                               (r.timestamp && r.timestamp.includes(searchTerm));
            return matchLoc && matchSearch;
        });
    }, [records, filterLocation, searchTerm]);

    return (
        <div style={{ padding: '30px' }}>
            <div style={{ marginBottom: '32px', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
                <div>
                    <h2 style={{ fontSize: '28px', fontWeight: '800', marginBottom: '8px' }}>Event Discovery & Management</h2>
                    <p style={{ color: '#64748b' }}>Search, filter and manage historical snapshots in vector and object storage.</p>
                </div>
                <button 
                    onClick={fetchRecords} 
                    style={{ 
                        display: 'flex', alignItems: 'center', gap: '8px', padding: '10px 20px',
                        background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.1)',
                        borderRadius: '12px', color: '#94a3b8', cursor: 'pointer'
                    }}
                >
                    <RefreshCcw size={18} className={loading ? 'spinner' : ''} />
                    Refresh Data
                </button>
            </div>

            {/* Filter Bar */}
            <div style={{ 
                display: 'flex', gap: '16px', marginBottom: '32px', 
                background: 'rgba(255,255,255,0.02)', padding: '20px', borderRadius: '18px',
                border: '1px solid rgba(255,255,255,0.05)'
            }}>
                <div style={{ flex: 1, position: 'relative' }}>
                    <Search size={18} style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', color: '#475569' }} />
                    <input 
                        type="text" 
                        placeholder="Search by ID or Timestamp..." 
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        style={{ 
                            width: '100%', padding: '12px 12px 12px 42px', background: 'rgba(255,255,255,0.03)',
                            border: '1px solid rgba(255,255,255,0.1)', borderRadius: '12px', color: 'white'
                        }}
                    />
                </div>
                <div style={{ width: '200px', position: 'relative' }}>
                    <Filter size={18} style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', color: '#475569' }} />
                    <select 
                        value={filterLocation}
                        onChange={(e) => setFilterLocation(e.target.value)}
                        style={{ 
                            width: '100%', padding: '12px 12px 12px 42px', background: 'rgba(255,255,255,0.03)',
                            border: '1px solid rgba(255,255,255,0.1)', borderRadius: '12px', color: 'white',
                            appearance: 'none'
                        }}
                    >
                        {locations.map(loc => (
                            <option key={loc} value={loc} style={{ background: '#0f172a' }}>
                                {loc === 'all' ? 'All Locations' : loc}
                            </option>
                        ))}
                    </select>
                </div>
            </div>

            {loading && records.length === 0 ? (
                <div style={{ padding: '100px', textAlign: 'center' }}>
                    <Loader2 size={48} className="spinner" color="#00d2ff" style={{ margin: '0 auto' }} />
                    <p style={{ marginTop: '20px', color: '#64748b' }}>Indexing cloud records...</p>
                </div>
            ) : (
                <>
                    <div style={{ 
                        display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', 
                        gap: '24px' 
                    }}>
                        {filteredRecords.map(record => (
                            <div key={record.id} style={{ 
                                background: 'rgba(255,255,255,0.03)', borderRadius: '20px', 
                                border: '1px solid rgba(255,255,255,0.08)', overflow: 'hidden',
                                transition: 'all 0.3s ease', position: 'relative'
                            }} className="history-card">
                                <img 
                                    src={`data:image/jpeg;base64,${record.imageBase64}`} 
                                    style={{ width: '100%', height: '200px', objectFit: 'cover' }}
                                    alt="Capture"
                                />
                                <div style={{ padding: '20px' }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#00d2ff', fontSize: '12px', fontWeight: 'bold', marginBottom: '8px' }}>
                                        <MapPin size={14} />
                                        {record.location}
                                    </div>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#94a3b8', fontSize: '12px', marginBottom: '16px' }}>
                                        <Clock size={14} />
                                        {record.timestamp}
                                    </div>
                                    <div style={{ fontSize: '10px', color: '#1e293b', background: '#e2e8f0', padding: '4px 8px', borderRadius: '6px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                        ID: {record.id}
                                    </div>
                                    
                                    <button 
                                        onClick={() => handleDelete(record.id)}
                                        disabled={deletingId === record.id}
                                        style={{ 
                                            marginTop: '20px', width: '100%', padding: '10px',
                                            background: 'rgba(244, 63, 94, 0.1)', border: '1px solid rgba(244, 63, 94, 0.2)',
                                            borderRadius: '10px', color: '#f43f5e', cursor: 'pointer',
                                            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px',
                                            transition: 'all 0.2s'
                                        }}
                                        className="delete-btn"
                                    >
                                        {deletingId === record.id ? <Loader2 size={16} className="spinner" /> : <Trash2 size={16} />}
                                        {deletingId === record.id ? 'Wiping...' : 'Drop Record'}
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>

                    {filteredRecords.length === 0 && (
                        <div style={{ padding: '100px', textAlign: 'center', background: 'rgba(255,255,255,0.01)', borderRadius: '24px' }}>
                            <AlertTriangle size={48} color="#475569" style={{ margin: '0 auto 20px' }} />
                            <h3 style={{ color: '#94a3b8' }}>No records found matching your filters</h3>
                        </div>
                    )}
                </>
            )}

            <style>{`
                .history-card:hover { transform: translateY(-5px); border-color: rgba(0, 210, 255, 0.3); box-shadow: 0 10px 30px rgba(0,0,0,0.3); }
                .delete-btn:hover { background: #f43f5e; color: white; }
                .spinner { animation: spin 1s linear infinite; }
                @keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
            `}</style>
        </div>
    );
};

export default HistoryManager;
