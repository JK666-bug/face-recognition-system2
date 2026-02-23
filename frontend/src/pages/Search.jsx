import React, { useState } from 'react';
import { Upload, Search as SearchIcon, MapPin, Clock, ArrowRight, Loader2, Play, X } from 'lucide-react';
import './Search.css';

const Search = () => {
    const [selectedFile, setSelectedFile] = useState(null);
    const [previewUrl, setPreviewUrl] = useState(null);
    const [results, setResults] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [activeVideo, setActiveVideo] = useState(null); // URL of current video

    const handleFileChange = (e) => {
        const file = e.target.files[0];
        if (file) {
            setSelectedFile(file);
            setPreviewUrl(URL.createObjectURL(file));
            setResults([]);
            setError('');
        }
    };

    const handleSearch = async () => {
        if (!selectedFile) return;

        setLoading(true);
        setError('');

        const formData = new FormData();
        formData.append('file', selectedFile);

        try {
            const response = await fetch('http://localhost:8080/api/face/search', {
                method: 'POST',
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
                    <div className="results-header">
                        <h2>Trajectory Results</h2>
                        {results.length > 0 && <span className="match-count">{results.length} Matches Found</span>}
                    </div>

                    {results.length > 0 ? (
                        <div className="timeline">
                            {results.map((item, index) => (
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
                                            </div>
                                            {item.videoUrl && (
                                                <button
                                                    className="play-btn"
                                                    title="Play original video"
                                                    onClick={() => setActiveVideo(item.videoUrl)}
                                                >
                                                    <Play size={20} fill="currentColor" />
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
