import React, { useEffect, useState } from 'react';
import { Loader2, Upload, AlertTriangle, Trash2, RefreshCw, Search, X, GitMerge } from 'lucide-react';

const PeopleLibrary = () => {
    const [people, setPeople] = useState([]);
    const [loading, setLoading] = useState(false);
    const [uploading, setUploading] = useState(false);
    const [peopleIdQuery, setPeopleIdQuery] = useState('');

    const fetchPeople = async (query = peopleIdQuery) => {
        setLoading(true);
        const token = localStorage.getItem('token');
        try {
            const trimmedQuery = query.trim();
            const url = trimmedQuery
                ? `http://localhost:8080/api/management/people?peopleId=${encodeURIComponent(trimmedQuery)}`
                : 'http://localhost:8080/api/management/people';
            const response = await fetch(url, {
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
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const handleSearch = async () => {
        await fetchPeople(peopleIdQuery);
    };

    const handleClearSearch = async () => {
        setPeopleIdQuery('');
        await fetchPeople('');
    };

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

    const handleMergePerson = async (item) => {
        const targetPeopleId = window.prompt(`Merge ${item.peopleId} into which People ID?`, '');
        if (!targetPeopleId) return;
        const trimmedTarget = targetPeopleId.trim();
        if (!trimmedTarget) return;
        if (trimmedTarget === item.peopleId) {
            alert('Source and target People ID cannot be the same.');
            return;
        }
        if (!window.confirm(`Merge ${item.peopleId} into ${trimmedTarget}?\n\nThis will move related face records to the target People ID and delete the source person.`)) {
            return;
        }

        const token = localStorage.getItem('token');
        try {
            const response = await fetch(
                `http://localhost:8080/api/management/people/${item.peopleId}/merge?targetPeopleId=${encodeURIComponent(trimmedTarget)}`,
                {
                    method: 'POST',
                    headers: { Authorization: `Bearer ${token}` }
                }
            );
            if (!response.ok) {
                const message = await response.text();
                throw new Error(message || 'Merge failed');
            }
            await fetchPeople();
        } catch (error) {
            console.error(error);
            alert(`Failed to merge person. ${error.message || ''}`);
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

            <div style={{ display: 'flex', gap: '12px', marginBottom: '20px', alignItems: 'center' }}>
                <div style={{ flex: 1, position: 'relative' }}>
                    <Search size={16} style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', color: '#94a3b8' }} />
                    <input
                        type="text"
                        value={peopleIdQuery}
                        onChange={(e) => setPeopleIdQuery(e.target.value)}
                        onKeyDown={(e) => {
                            if (e.key === 'Enter') {
                                handleSearch();
                            }
                        }}
                        placeholder="Search by People ID"
                        style={{
                            width: '100%',
                            padding: '10px 12px 10px 38px',
                            background: 'rgba(255,255,255,0.03)',
                            border: '1px solid rgba(255,255,255,0.08)',
                            borderRadius: '10px',
                            color: 'white',
                            outline: 'none'
                        }}
                    />
                </div>
                <button
                    onClick={handleSearch}
                    style={{
                        padding: '10px 14px',
                        borderRadius: '10px',
                        border: '1px solid rgba(14,165,233,0.35)',
                        background: 'rgba(14,165,233,0.15)',
                        color: '#7dd3fc',
                        cursor: 'pointer',
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '8px'
                    }}
                >
                    <Search size={16} />
                    Search
                </button>
                <button
                    onClick={handleClearSearch}
                    style={{
                        padding: '10px 14px',
                        borderRadius: '10px',
                        border: '1px solid rgba(255,255,255,0.12)',
                        background: 'rgba(255,255,255,0.04)',
                        color: '#cbd5e1',
                        cursor: 'pointer',
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '8px'
                    }}
                >
                    <X size={16} />
                    Clear
                </button>
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
                                        onClick={() => handleMergePerson(item)}
                                        style={{
                                            flex: 1,
                                            padding: '8px',
                                            borderRadius: '8px',
                                            border: '1px solid rgba(250,204,21,0.4)',
                                            color: '#fde68a',
                                            background: 'transparent',
                                            cursor: 'pointer',
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'center',
                                            gap: '6px'
                                        }}
                                    >
                                        <GitMerge size={14} />
                                        Merge
                                    </button>
                                </div>
                                <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
                                    <button
                                        onClick={() => handleDeletePerson(item)}
                                        style={{
                                            width: '100%',
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

            {!loading && people.length === 0 && (
                <div style={{ opacity: 0.8, marginTop: '12px' }}>
                    No people found{peopleIdQuery.trim() ? ` for "${peopleIdQuery.trim()}"` : ''}.
                </div>
            )}
        </div>
    );
};

export default PeopleLibrary;
