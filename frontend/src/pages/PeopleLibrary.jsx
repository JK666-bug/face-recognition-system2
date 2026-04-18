import React, { useEffect, useState } from 'react';
import { Loader2, Upload, AlertTriangle, Trash2, RefreshCw } from 'lucide-react';

const PeopleLibrary = () => {
    const [people, setPeople] = useState([]);
    const [loading, setLoading] = useState(false);
    const [uploading, setUploading] = useState(false);

    const fetchPeople = async () => {
        setLoading(true);
        const token = localStorage.getItem('token');
        try {
            const response = await fetch('http://localhost:8080/api/management/people', {
                headers: { Authorization: `Bearer ${token}` }
            });
            const data = await response.json();
            setPeople(Array.isArray(data) ? data : []);
        } catch (error) {
            console.error('Failed to fetch people library:', error);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchPeople();
    }, []);

    const handleUpload = async (event) => {
        const file = event.target.files?.[0];
        if (!file) return;
        setUploading(true);
        const token = localStorage.getItem('token');
        try {
            const formData = new FormData();
            formData.append('file', file);
            formData.append('suspect', 'false');
            const response = await fetch('http://localhost:8080/api/management/people/upload', {
                method: 'POST',
                headers: { Authorization: `Bearer ${token}` },
                body: formData
            });
            if (!response.ok) {
                throw new Error('Upload failed');
            }
            await fetchPeople();
        } catch (error) {
            console.error(error);
            alert('Upload failed. Please check backend/algorithm services.');
        } finally {
            setUploading(false);
            event.target.value = '';
        }
    };

    const toggleSuspect = async (item) => {
        const token = localStorage.getItem('token');
        try {
            const response = await fetch(
                `http://localhost:8080/api/management/people/${item.peopleId}/suspect?value=${!item.suspect}`,
                {
                    method: 'POST',
                    headers: { Authorization: `Bearer ${token}` }
                }
            );
            if (!response.ok) throw new Error('Toggle suspect failed');
            await fetchPeople();
        } catch (error) {
            console.error(error);
            alert('Failed to update suspect flag.');
        }
    };

    const handleDeletePerson = async (item) => {
        if (!window.confirm(`Delete person ${item.peopleId}? This will remove related captures and vectors.`)) {
            return;
        }
        const token = localStorage.getItem('token');
        try {
            const response = await fetch(`http://localhost:8080/api/management/people/${item.peopleId}`, {
                method: 'DELETE',
                headers: { Authorization: `Bearer ${token}` }
            });
            if (!response.ok) throw new Error('Delete failed');
            await fetchPeople();
        } catch (error) {
            console.error(error);
            alert('Failed to delete person.');
        }
    };

    const handleReplacePhoto = async (item, file) => {
        if (!file) return;
        const token = localStorage.getItem('token');
        try {
            const formData = new FormData();
            formData.append('file', file);
            const response = await fetch(`http://localhost:8080/api/management/people/${item.peopleId}/replace-photo`, {
                method: 'POST',
                headers: { Authorization: `Bearer ${token}` },
                body: formData
            });
            if (!response.ok) throw new Error('Replace failed');
            await fetchPeople();
        } catch (error) {
            console.error(error);
            alert('Failed to replace photo.');
        }
    };

    return (
        <div style={{ padding: '24px', color: 'white' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
                <h2 style={{ fontSize: '22px' }}>People Library</h2>
                <label style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', padding: '10px 14px', borderRadius: '10px', background: '#0ea5e9', cursor: 'pointer' }}>
                    {uploading ? <Loader2 size={16} className="spinner" /> : <Upload size={16} />}
                    Upload Face
                    <input type="file" hidden accept="image/*" onChange={handleUpload} />
                </label>
            </div>

            {loading ? (
                <div style={{ opacity: 0.8 }}>Loading people library...</div>
            ) : (
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill,minmax(250px,1fr))', gap: '16px' }}>
                    {people.map((item) => (
                        <div key={item.peopleId} style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: '14px', overflow: 'hidden' }}>
                            <img
                                src={`data:image/jpeg;base64,${item.imageBase64}`}
                                alt={item.peopleId}
                                style={{ width: '100%', height: '220px', objectFit: 'cover' }}
                            />
                            <div style={{ padding: '12px' }}>
                                <div style={{ fontSize: '12px', color: '#94a3b8' }}>People ID</div>
                                <div style={{ fontSize: '12px', wordBreak: 'break-all' }}>{item.peopleId}</div>
                                <div style={{ fontSize: '12px', marginTop: '8px', color: '#94a3b8' }}>Last Active</div>
                                <div style={{ fontSize: '12px' }}>{item.lastTime}</div>
                                <button
                                    onClick={() => toggleSuspect(item)}
                                    style={{
                                        marginTop: '12px',
                                        width: '100%',
                                        padding: '8px',
                                        borderRadius: '8px',
                                        border: '1px solid rgba(244,63,94,0.4)',
                                        background: item.suspect ? 'rgba(244,63,94,0.2)' : 'transparent',
                                        color: item.suspect ? '#fda4af' : '#f43f5e',
                                        cursor: 'pointer',
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        gap: '6px'
                                    }}
                                >
                                    <AlertTriangle size={14} />
                                    {item.suspect ? 'Marked Suspect' : 'Mark As Suspect'}
                                </button>
                                <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
                                    <label
                                        style={{
                                            flex: 1,
                                            padding: '8px',
                                            borderRadius: '8px',
                                            border: '1px solid rgba(59,130,246,0.4)',
                                            color: '#93c5fd',
                                            cursor: 'pointer',
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'center',
                                            gap: '6px'
                                        }}
                                    >
                                        <RefreshCw size={14} />
                                        Replace
                                        <input
                                            type="file"
                                            hidden
                                            accept="image/*"
                                            onChange={(e) => {
                                                handleReplacePhoto(item, e.target.files?.[0]);
                                                e.target.value = '';
                                            }}
                                        />
                                    </label>
                                    <button
                                        onClick={() => handleDeletePerson(item)}
                                        style={{
                                            flex: 1,
                                            padding: '8px',
                                            borderRadius: '8px',
                                            border: '1px solid rgba(244,63,94,0.4)',
                                            color: '#fda4af',
                                            background: 'transparent',
                                            cursor: 'pointer',
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'center',
                                            gap: '6px'
                                        }}
                                    >
                                        <Trash2 size={14} />
                                        Delete
                                    </button>
                                </div>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};

export default PeopleLibrary;
