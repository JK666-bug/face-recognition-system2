import React, { useState, useMemo } from 'react';
import { Upload, Search as SearchIcon, MapPin, Clock, ArrowRight, Loader2, Play, X, BarChart3, MoreHorizontal, Download } from 'lucide-react';
import { 
    BarChart, Bar, XAxis, YAxis, Tooltip as RechartsTooltip, ResponsiveContainer, 
    AreaChart, Area, Cell
} from 'recharts';
import './Search.css';

const Search = () => {
    const [selectedFile, setSelectedFile] = useState(null);
    const [previewUrl, setPreviewUrl] = useState(null);
    const [results, setResults] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [activeVideo, setActiveVideo] = useState(null); // URL of current video
    const [downloadingId, setDownloadingId] = useState(null);
    const [topK, setTopK] = useState(50);
    
    // Time filter states
    const [timeRange, setTimeRange] = useState('all'); // all, today, week, month, custom
    const [startTime, setStartTime] = useState('');
    const [endTime, setEndTime] = useState('');

    const handleFileChange = (e) => {
        const file = e.target.files[0];
        if (file) {
            setSelectedFile(file);
            setPreviewUrl(URL.createObjectURL(file));
            setResults([]);
            setError('');
        }
    };

    const handleTimeRangeChange = (range) => {
        setTimeRange(range);
        if (range === 'all') {
            setStartTime('');
            setEndTime('');
        } else if (range === 'today') {
            const today = new Date();
            const dateStr = formatDate(today);
            setStartTime(dateStr);
            setEndTime('');
        } else if (range === 'week') {
            const weekAgo = new Date();
            weekAgo.setDate(weekAgo.getDate() - 7);
            setStartTime(formatDate(weekAgo));
            setEndTime('');
        } else if (range === 'month') {
            const monthAgo = new Date();
            monthAgo.setMonth(monthAgo.getMonth() - 1);
            setStartTime(formatDate(monthAgo));
            setEndTime('');
        }
    };

    const formatDate = (date) => {
        const y = date.getFullYear();
        const m = String(date.getMonth() + 1).padStart(2, '0');
        const d = String(date.getDate()).padStart(2, '0');
        return `${y}-${m}-${d}`;
    };

    const handleSearch = async () => {
        if (!selectedFile) return;

        setLoading(true);
        setError('');

        const formData = new FormData();
        formData.append('file', selectedFile);
        formData.append('topK', topK);
        if (startTime) formData.append('startTime', startTime);
        if (endTime) formData.append('endTime', endTime);

        const token = localStorage.getItem('token');
        try {
            const response = await fetch('http://localhost:8080/api/face/search', {
                method: 'POST',
                headers: { 'Authorization': `Bearer ${token}` },
                body: formData,
            });

            if (!response.ok) {
                throw new Error('Search failed on server');
            }

            const data = await response.json();
            setResults(data);
        } catch (err) {
            setError('Search failed. Please ensure the backend is running and Milvus is initialized.');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const closeVideo = () => setActiveVideo(null);

    const handleDownloadVideo = async (item) => {
        if (!item.videoUrl) return;
        
        setDownloadingId(item.id);
        try {
            const response = await fetch(item.videoUrl);
            if (!response.ok) throw new Error('Download failed');
            
            const blob = await response.blob();
            if (blob.type === 'application/xml' || blob.type === 'text/xml') {
                throw new Error('Server returned XML. Link might be expired.');
            }

            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `trajectory_${item.id}.mp4`;
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            a.remove();
        } catch (error) {
            console.error('Download error:', error);
            alert('Download failed. File missing or server error.');
        } finally {
            setDownloadingId(null);
        }
    };

    // Derived filtered results
    const filteredResults = results.filter(item => {
        if ((!startTime || startTime === '') && (!endTime || endTime === '')) return true;
        if (!item.timestamp || item.timestamp === 'Unknown') return false;

        const ts = item.timestamp;
        let startOk = true;
        if (startTime) {
            const fullStart = startTime.length === 10 ? `${startTime} 00:00:00` : startTime;
            startOk = ts >= fullStart;
        }

        let endOk = true;
        if (endTime) {
            const fullEnd = endTime.length === 10 ? `${endTime} 23:59:59` : endTime;
            endOk = ts <= fullEnd;
        }

        return startOk && endOk;
    });

    // 1. Location frequency data
    const locationData = useMemo(() => {
        const counts = {};
        filteredResults.forEach(r => {
            const loc = r.location || 'Unknown';
            counts[loc] = (counts[loc] || 0) + 1;
        });
        return Object.keys(counts).map(key => ({
            name: key,
            count: counts[key]
        })).sort((a, b) => b.count - a.count);
    }, [filteredResults]);

    // 2. Time distribution data (Hourly)
    const hourlyData = useMemo(() => {
        const hours = Array.from({ length: 24 }, (_, i) => ({
            hour: `${i}:00`,
            fullHour: i,
            captures: 0
        }));
        
        filteredResults.forEach(r => {
            if (r.timestamp && typeof r.timestamp === 'string' && r.timestamp.includes(' ')) {
                const timePart = r.timestamp.split(' ')[1];
                if (timePart) {
                    const hour = parseInt(timePart.split(':')[0]);
                    if (!isNaN(hour) && hour >= 0 && hour < 24) {
                        hours[hour].captures++;
                    }
                }
            }
        });
        return hours;
    }, [filteredResults]);

    return (
        <div className="search-page">
            <header className="page-header">
                <h1>Face Trajectory Search</h1>
                <p>Upload a photo to find matching profiles and track their history.</p>
            </header>

            <div className="search-content">
                <div className="upload-section">
                    <div
                        className={`drop-zone ${previewUrl ? 'has-preview' : ''}`}
                        onClick={() => document.getElementById('fileInput').click()}
                    >
                        {previewUrl ? (
                            <img src={previewUrl} alt="Preview" className="preview-img" />
                        ) : (
                            <div className="drop-zone-prompt">
                                <Upload size={48} className="upload-icon" />
                                <span>Click or Drag to Upload Face Photo</span>
                            </div>
                        )}
                        <input
                            type="file"
                            id="fileInput"
                            hidden
                            accept="image/*"
                            onChange={handleFileChange}
                        />
                    </div>

                    <button
                        className="search-btn"
                        disabled={!selectedFile || loading}
                        onClick={handleSearch}
                    >
                        {loading ? <Loader2 className="spinner" /> : <SearchIcon size={20} />}
                        <span>{loading ? 'Searching...' : 'Start Search'}</span>
                    </button>

                    {error && <p className="error-text">{error}</p>}
                </div>

                <div className="results-section">
                    <div className="results-header-container">
                        <div className="results-header">
                            <div>
                                <h2>Trajectory Results</h2>
                                {results.length > 0 && (
                                    <span className="match-count">
                                        {filteredResults.length} {filteredResults.length !== results.length ? `(Filtered from ${results.length})` : 'Matches Found'}
                                    </span>
                                )}
                            </div>
                        </div>

                        {results.length > 0 && (
                            <div className="results-filter-bar">
                                <div className="time-chips">
                                    {[
                                        { id: 'all', label: 'All Time' },
                                        { id: 'today', label: 'Today' },
                                        { id: 'week', label: 'Past Week' },
                                        { id: 'month', label: 'Past Month' },
                                        { id: 'custom', label: 'Custom Range' }
                                    ].map(range => (
                                        <button
                                            key={range.id}
                                            className={`chip ${timeRange === range.id ? 'active' : ''}`}
                                            onClick={() => handleTimeRangeChange(range.id)}
                                        >
                                            {range.label}
                                        </button>
                                    ))}
                                </div>

                                <div className="limit-selector" style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: '8px' }}>
                                    <span style={{ fontSize: '11px', color: '#94a3b8' }}>Limit:</span>
                                    <select 
                                        value={topK} 
                                        onChange={(e) => setTopK(parseInt(e.target.value))}
                                        style={{ 
                                            background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.1)',
                                            borderRadius: '8px', color: '#00d2ff', fontSize: '11px', padding: '4px 8px',
                                            cursor: 'pointer', outline: 'none'
                                        }}
                                    >
                                        <option value={50} style={{ background: '#0f172a' }}>Top 50</option>
                                        <option value={100} style={{ background: '#0f172a' }}>Top 100</option>
                                        <option value={200} style={{ background: '#0f172a' }}>Top 200 (All)</option>
                                    </select>
                                </div>

                                {timeRange === 'custom' && (
                                    <div className="custom-date-range">
                                        <input
                                            type="date"
                                            value={startTime}
                                            onChange={(e) => setStartTime(e.target.value)}
                                            className="date-input"
                                        />
                                        <span className="date-separator">to</span>
                                        <input
                                            type="date"
                                            value={endTime}
                                            onChange={(e) => setEndTime(e.target.value)}
                                            className="date-input"
                                        />
                                    </div>
                                )}
                            </div>
                        )}
                    </div>

                    {filteredResults.length > 0 && (
                        <div className="analytics-dashboard">
                            <div className="analysis-grid">
                                {/* Location Chart Card */}
                                <div className="analysis-card location-chart">
                                    <div className="card-header">
                                        <MapPin size={18} />
                                        <span>Location Frequency</span>
                                    </div>
                                    <div className="chart-wrapper">
                                        <ResponsiveContainer width="100%" height="100%">
                                            <BarChart data={locationData} layout="vertical" margin={{ left: 10, right: 30, top: 10, bottom: 10 }}>
                                                <XAxis type="number" hide />
                                                <YAxis 
                                                    dataKey="name" 
                                                    type="category" 
                                                    width={140} 
                                                    interval={0}
                                                    style={{ fontSize: '11px', fill: '#94a3b8' }} 
                                                    tick={{ fill: '#94a3b8' }}
                                                />
                                                <RechartsTooltip 
                                                    cursor={{ fill: 'rgba(255, 255, 255, 0.05)' }}
                                                    contentStyle={{ background: '#0f172a', border: '1px solid #334155', borderRadius: '8px' }}
                                                />
                                                <Bar dataKey="count" fill="url(#barGradient)" radius={[0, 4, 4, 0]}>
                                                    {locationData.map((entry, index) => (
                                                        <Cell key={`cell-${index}`} fill={index === 0 ? '#00d2ff' : '#3a7bd5'} />
                                                    ))}
                                                </Bar>
                                                <defs>
                                                    <linearGradient id="barGradient" x1="0" y1="0" x2="1" y2="0">
                                                        <stop offset="0%" stopColor="#00d2ff" />
                                                        <stop offset="100%" stopColor="#3a7bd5" />
                                                    </linearGradient>
                                                </defs>
                                            </BarChart>
                                        </ResponsiveContainer>
                                    </div>
                                </div>

                                {/* Time Distribution Chart */}
                                <div className="analysis-card time-chart">
                                    <div className="card-header">
                                        <Clock size={18} />
                                        <span>Active Intensity (24h)</span>
                                    </div>
                                    <div className="chart-wrapper">
                                        <ResponsiveContainer width="100%" height="100%">
                                            <AreaChart data={hourlyData} margin={{ left: 0, right: 10, top: 10, bottom: 0 }}>
                                                <XAxis dataKey="hour" hide />
                                                <YAxis hide />
                                                <Area 
                                                    type="monotone" 
                                                    dataKey="captures" 
                                                    stroke="#10b981" 
                                                    fill="rgba(16, 185, 129, 0.1)" 
                                                    strokeWidth={2}
                                                />
                                                <RechartsTooltip 
                                                    contentStyle={{ background: '#0f172a', border: '1px solid #334155', borderRadius: '8px' }}
                                                />
                                            </AreaChart>
                                        </ResponsiveContainer>
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}

                    {filteredResults.length > 0 ? (
                        <div className="timeline">
                            <h3 className="timeline-title">History Flow</h3>
                            {filteredResults.map((item, index) => (
                                <div className="timeline-item" key={item.id}>
                                    <div className="timeline-marker"></div>
                                    <div className="timeline-content">
                                        <div className="match-card">
                                            <img src={`data:image/jpeg;base64,${item.imageBase64}`} alt="Match" className="match-thumb" />
                                            <div className="match-info">
                                                <div className="info-row">
                                                    <MapPin size={16} className="info-icon" />
                                                    <span>{item.location}</span>
                                                </div>
                                                <div className="info-row">
                                                    <Clock size={16} className="info-icon" />
                                                    <span>{item.timestamp}</span>
                                                </div>
                                                <div className="score-badge">
                                                    Similarity: {(item.score * 100).toFixed(2)}%
                                                </div>
                                                <div className="info-row">
                                                    <span style={{ color: '#94a3b8' }}>People ID:</span>
                                                    <span>{item.peopleId || 'Unknown'}</span>
                                                </div>
                                                {item.alertHit && (
                                                    <div className="score-badge" style={{ background: 'rgba(244, 63, 94, 0.15)', color: '#f43f5e' }}>
                                                        ALERT: suspect matched {item.alertPeopleId || item.peopleId}
                                                    </div>
                                                )}
                                            </div>
                                            {item.videoUrl && (
                                                <button
                                                    className="download-btn-small"
                                                    title="Download original video"
                                                    onClick={() => handleDownloadVideo(item)}
                                                    disabled={downloadingId === item.id}
                                                    style={{ marginLeft: 'auto' }}
                                                >
                                                    {downloadingId === item.id ? <Loader2 size={18} className="spinner" /> : <Download size={18} />}
                                                </button>
                                            )}
                                        </div>
                                        {index < results.length - 1 && <ArrowRight className="direction-arrow" size={24} />}
                                    </div>
                                </div>
                            ))}
                        </div>
                    ) : (
                        <div className="empty-results">
                            {!loading && <p>No results yet. Upload a photo to begin searching.</p>}
                            {loading && <p>Processing image and searching vector database...</p>}
                        </div>
                    )}
                </div>
            </div>

            {activeVideo && (
                <div className="video-modal-overlay" onClick={closeVideo}>
                    <div className="video-modal" onClick={e => e.stopPropagation()}>
                        <div className="modal-header">
                            <h3>Video Source Playback</h3>
                            <button className="close-btn" onClick={closeVideo}>
                                <X size={24} />
                            </button>
                        </div>
                        <div className="video-container">
                            <video
                                src={activeVideo}
                                controls
                                autoPlay
                                className="main-video-player"
                            >
                                Your browser does not support the video tag.
                            </video>
                        </div>
                        <div className="modal-footer">
                            <p>Streamed directly from MinIO Storage</p>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default Search;
