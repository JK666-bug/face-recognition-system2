import React, { useState, useEffect, useMemo } from 'react';
import { Trash2, MapPin, Clock, Search, Filter, RefreshCcw, Loader2, AlertTriangle, Video, Play, X, Download } from 'lucide-react';

const HistoryManager = () => {
    const [records, setRecords] = useState([]);
    const [loading, setLoading] = useState(false);
    const [filterLocation, setFilterLocation] = useState('all');
    const [searchTerm, setSearchTerm] = useState('');
    const [deletingId, setDeletingId] = useState(null);
    const [downloadingId, setDownloadingId] = useState(null);
    const [activeVideo, setActiveVideo] = useState(null);

    useEffect(() => {
        fetchRecords();
    }, []);

    const handleDelete = async (id) => {
        if (!window.confirm('Are you sure you want to permanently remove this capture record from storage and vector database?')) {
            return;
        }

        setDeletingId(id);
        const token = localStorage.getItem('token');
        try {
            const response = await fetch(`http://localhost:8080/api/management/captures/${id}`, {
                method: 'DELETE',
                headers: { 'Authorization': `Bearer ${token}` }
            });

            if (response.ok) {
                setRecords(records.filter(r => r.id !== id));
            } else {
                alert('Deletion failed. You might not have permission.');
            }
        } catch (error) {
            console.error('Delete error:', error);
            alert('Server connection error.');
        } finally {
            setDeletingId(null);
        }
    };

    const handleClearAll = async () => {
        if (!window.confirm('CRITICAL WARNING: This will permanently delete ALL capture records, images, and vectors. This action cannot be undone. Proceed?')) {
            return;
        }

        setLoading(true);
        const token = localStorage.getItem('token');
        try {
            const response = await fetch('http://localhost:8080/api/management/captures/all', {
                method: 'DELETE',
                headers: { 'Authorization': `Bearer ${token}` }
            });

            if (response.ok) {
                setRecords([]);
                alert('All data has been cleared from cloud storage and vector database.');
            } else {
                alert('Bulk deletion failed.');
            }
        } catch (error) {
            console.error('Clear all error:', error);
            alert('Server connection error.');
        } finally {
            setLoading(false);
        }
    };

    const fetchRecords = async () => {
        setLoading(true);
        const token = localStorage.getItem('token');
        try {
            const response = await fetch('http://localhost:8080/api/management/captures', {
                headers: { 'Authorization': `Bearer ${token}` }
            });
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

    const handleDownloadVideo = async (record) => {
        if (!record.videoUrl) {
            alert("Video link not available yet.");
            return;
        }
        
        setDownloadingId(record.videoId);
        try {
            // 使用 fetch 获取视频二进制流，确保不受浏览器拦截或签名丢失影响
            const response = await fetch(record.videoUrl);
            if (!response.ok) throw new Error('Download failed');
            
            const blob = await response.blob();
            // 验证是否真的是 XML (MinIO 报错通常返回 XML)
            if (blob.type === 'application/xml' || blob.type === 'text/xml') {
                throw new Error('Server returned XML instead of video. The link might be expired.');
            }

            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            // 强制命名为 .mp4
            a.download = `${record.videoId || 'evidence'}.mp4`;
            document.body.appendChild(a);
            a.click();
            
            // 释放内存
            window.URL.revokeObjectURL(url);
            a.remove();
        } catch (error) {
            console.error('Download error:', error);
            alert('Download failed. Server error or file missing.');
        } finally {
            setDownloadingId(null);
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
            const searchLower = searchTerm.toLowerCase();
            const matchSearch = r.id.toLowerCase().includes(searchLower) || 
                               (r.timestamp && r.timestamp.toLowerCase().includes(searchLower)) ||
                               (r.location && r.location.toLowerCase().includes(searchLower));
            return matchLoc && matchSearch;
        });
    }, [records, filterLocation, searchTerm]);

    return (
        <div style={{ padding: '30px' }}>
            <div style={{ marginBottom: '32px', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
                <div>
                    <h2 style={{ fontSize: '28px', fontWeight: '800', marginBottom: '8px' }}>Dataset Explorer</h2>
                    <p style={{ color: '#94a3b8' }}>Search, filter and manage historical snapshots in vector and object storage.</p>
                </div>
                <div style={{ display: 'flex', gap: '12px' }}>
                    <button 
                        onClick={handleClearAll} 
                        style={{ 
                            display: 'flex', alignItems: 'center', gap: '8px', padding: '10px 20px',
                            background: 'rgba(244, 63, 94, 0.1)', border: '1px solid rgba(244, 63, 94, 0.2)',
                            borderRadius: '12px', color: '#f43f5e', cursor: 'pointer', fontWeight: 'bold'
                        }}
                    >
                        <Trash2 size={18} />
                        Clear All History
                    </button>
                    <button 
                        onClick={fetchRecords} 
                        style={{ 
                            display: 'flex', alignItems: 'center', gap: '8px', padding: '10px 20px',
                            background: 'rgba(255, 255, 255, 0.05)', border: '1px solid rgba(255, 255, 255, 0.1)',
                            borderRadius: '12px', color: '#94a3b8', cursor: 'pointer'
                        }}
                    >
                        <RefreshCcw size={18} className={loading ? 'spinner' : ''} />
                        Refresh Data
                    </button>
                </div>
            </div>

            {/* Filter Bar */}
            <div style={{ 
                display: 'flex', gap: '16px', marginBottom: '32px', 
                background: 'rgba(255,255,255,0.02)', padding: '20px', borderRadius: '18px',
                border: '1px solid rgba(255, 255, 255, 0.05)'
            }}>
                <div style={{ flex: 1, position: 'relative' }}>
                    <Search size={18} style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', color: '#475569' }} />
                    <input 
                        type="text" 
                        placeholder="Search by ID, Time or Location..." 
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        style={{ 
                            width: '100%', padding: '12px 12px 12px 42px', background: 'rgba(255,255,255,0.03)',
                            border: '1px solid rgba(255, 255, 255, 0.1)', borderRadius: '12px', color: 'white'
                        }}
                    />
                </div>
                <div style={{ width: '220px', position: 'relative' }}>
                    <Filter size={18} style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', color: '#475569' }} />
                    <select 
                        value={filterLocation}
                        onChange={(e) => setFilterLocation(e.target.value)}
                        style={{ 
                            width: '100%', padding: '12px 12px 12px 42px', background: 'rgba(255,255,255,0.03)',
                            border: '1px solid rgba(255, 255, 255, 0.1)', borderRadius: '12px', color: 'white',
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
                    <p style={{ marginTop: '20px', color: '#94a3b8' }}>Indexing cloud records...</p>
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
                                <button 
                                    onClick={() => handleDelete(record.id)}
                                    disabled={deletingId === record.id}
                                    style={{ 
                                        position: 'absolute', top: '10px', right: '10px',
                                        width: '32px', height: '32px', borderRadius: '8px',
                                        background: 'rgba(244, 63, 94, 0.1)', border: '1px solid rgba(244, 63, 94, 0.2)',
                                        color: '#f43f5e', cursor: 'pointer', display: 'flex',
                                        alignItems: 'center', justifyContent: 'center',
                                        transition: 'all 0.2s', zIndex: 10
                                    }}
                                    className="delete-btn"
                                    title="Drop Record"
                                >
                                    {deletingId === record.id ? <Loader2 size={16} className="spinner" /> : <Trash2 size={16} />}
                                </button>
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
                                    <div style={{ fontSize: '10px', color: '#1e293b', background: '#334155', padding: '4px 8px', borderRadius: '6px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                        ID: {record.id}
                                    </div>
                                    
                                    {record.videoId && (
                                        <button 
                                            onClick={() => handleDownloadVideo(record)}
                                            disabled={downloadingId === record.videoId}
                                            style={{ 
                                                marginTop: '12px', width: '100%', padding: '12px',
                                                background: 'rgba(16, 185, 129, 0.1)', border: '1px solid rgba(16, 185, 129, 0.2)',
                                                borderRadius: '12px', color: '#10b981', cursor: 'pointer',
                                                display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px',
                                                transition: 'all 0.2s', fontWeight: 'bold'
                                            }}
                                        >
                                            {downloadingId === record.videoId ? <Loader2 size={18} className="spinner" /> : <Download size={18} />}
                                            {downloadingId === record.videoId ? 'Downloading...' : 'Download Video'}
                                        </button>
                                    )}

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

            {activeVideo && (
                <div style={{
                    position: 'fixed', top: 0, left: 0, width: '100%', height: '100%',
                    background: 'rgba(0,0,0,0.85)', backdropFilter: 'blur(10px)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000,
                    padding: '40px'
                }} onClick={() => setActiveVideo(null)}>
                    <div style={{
                        width: '100%', maxWidth: '900px', background: '#0f172a',
                        borderRadius: '24px', border: '1px solid rgba(255, 255, 255, 0.1)',
                        overflow: 'hidden', position: 'relative'
                    }} onClick={e => e.stopPropagation()}>
                        <div style={{ padding: '20px', borderBottom: '1px solid rgba(255, 255, 255, 0.05)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <h3 style={{ fontSize: '18px', fontWeight: 'bold' }}>Source Video Feedback</h3>
                            <button onClick={() => setActiveVideo(null)} style={{ background: 'none', border: 'none', color: '#94a3b8', cursor: 'pointer' }}>
                                <X size={24} />
                            </button>
                        </div>
                        <video 
                            src={activeVideo} 
                            controls 
                            autoPlay 
                            style={{ width: '100%', display: 'block', maxHeight: '70vh' }}
                        />
                        <div style={{ padding: '15px', textAlign: 'center', color: '#475569', fontSize: '12px' }}>
                            Streaming encrypted content from MinIO Cloud Storage
                        </div>
                    </div>
                </div>
            )}

            <style>{`
                .history-card:hover { transform: translateY(-5px); border-color: rgba(0, 210, 255, 0.3); box-shadow: 0 10px 30px rgba(0,0,0,0.3); }
                .delete-btn:hover { background: #f43f5e; color: white; }
                .play-btn:hover { background: rgba(16, 185, 129, 0.2); border-color: #10b981; }
                .spinner { animation: spin 1s linear infinite; }
                @keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
            `}</style>
        </div>
    );
};

export default HistoryManager;
