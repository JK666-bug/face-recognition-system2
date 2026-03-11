import React, { useState, useEffect } from 'react';
import { Save, RefreshCcw, ShieldCheck, Target, AlertCircle, CheckCircle2 } from 'lucide-react';

const Settings = () => {
    const [settings, setSettings] = useState({
        captureConfidence: 0.65,
        searchThreshold: 0.70
    });
    const [loading, setLoading] = useState(false);
    const [status, setStatus] = useState({ type: '', message: '' });

    useEffect(() => {
        fetchSettings();
    }, []);

    const fetchSettings = async () => {
        setLoading(true);
        try {
            const response = await fetch('http://localhost:8080/api/settings');
            const data = await response.json();
            setSettings(data);
        } catch (error) {
            console.error('Failed to fetch settings:', error);
            showStatus('error', 'Failed to load settings from server.');
        } finally {
            setLoading(false);
        }
    };

    const handleSave = async () => {
        setLoading(true);
        try {
            const response = await fetch('http://localhost:8080/api/settings', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(settings)
            });
            if (response.ok) {
                showStatus('success', 'System configurations updated successfully!');
            } else {
                showStatus('error', 'Server rejected settings update.');
            }
        } catch (error) {
            showStatus('error', 'Connection failed while saving settings.');
        } finally {
            setLoading(false);
        }
    };

    const showStatus = (type, message) => {
        setStatus({ type, message });
        setTimeout(() => setStatus({ type: '', message: '' }), 4000);
    };

    return (
        <div style={{ maxWidth: '800px', margin: '0 auto', padding: '40px 20px' }}>
            <div style={{ marginBottom: '40px' }}>
                <h2 style={{ fontSize: '28px', fontWeight: '700', marginBottom: '12px' }}>System Configuration</h2>
                <p style={{ color: '#64748b' }}>Precision control for face detection and vector similarity indexing.</p>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '32px' }}>
                {/* Capture Card */}
                <div style={{
                    background: 'rgba(255, 255, 255, 0.03)',
                    borderRadius: '24px',
                    padding: '32px',
                    border: '1px solid rgba(255, 255, 255, 0.08)',
                    backdropFilter: 'blur(10px)'
                }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '24px' }}>
                        <div style={{ width: '48px', height: '48px', background: 'rgba(0, 210, 255, 0.1)', borderRadius: '14px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                            <Target size={24} color="#00d2ff" />
                        </div>
                        <div>
                            <h3 style={{ fontSize: '18px', fontWeight: '600' }}>Capture Confidence</h3>
                            <p style={{ fontSize: '14px', color: '#64748b' }}>Minimum score for the real-time algorithm to save a face.</p>
                        </div>
                    </div>
                    
                    <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
                        <input 
                            type="range" 
                            min="0.1" 
                            max="0.95" 
                            step="0.01" 
                            value={settings.captureConfidence}
                            onChange={(e) => setSettings({...settings, captureConfidence: parseFloat(e.target.value)})}
                            style={{ flex: 1, accentColor: '#00d2ff' }}
                        />
                        <div style={{ 
                            padding: '8px 16px', 
                            background: 'rgba(0, 210, 255, 0.1)', 
                            borderRadius: '10px',
                            color: '#00d2ff',
                            fontSize: '18px',
                            fontWeight: '700',
                            minWidth: '60px',
                            textAlign: 'center'
                        }}>
                            {settings.captureConfidence.toFixed(2)}
                        </div>
                    </div>
                </div>

                {/* Similarity Card */}
                <div style={{
                    background: 'rgba(255, 255, 255, 0.03)',
                    borderRadius: '24px',
                    padding: '32px',
                    border: '1px solid rgba(255, 255, 255, 0.08)',
                    backdropFilter: 'blur(10px)'
                }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '24px' }}>
                        <div style={{ width: '48px', height: '48px', background: 'rgba(16, 185, 129, 0.1)', borderRadius: '14px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                            <ShieldCheck size={24} color="#10b981" />
                        </div>
                        <div>
                            <h3 style={{ fontSize: '18px', fontWeight: '600' }}>Search Threshold</h3>
                            <p style={{ fontSize: '14px', color: '#64748b' }}>Vector similarity floor for historical trajectory matching.</p>
                        </div>
                    </div>
                    
                    <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
                        <input 
                            type="range" 
                            min="0.1" 
                            max="0.95" 
                            step="0.01" 
                            value={settings.searchThreshold}
                            onChange={(e) => setSettings({...settings, searchThreshold: parseFloat(e.target.value)})}
                            style={{ flex: 1, accentColor: '#10b981' }}
                        />
                        <div style={{ 
                            padding: '8px 16px', 
                            background: 'rgba(16, 185, 129, 0.1)', 
                            borderRadius: '10px',
                            color: '#10b981',
                            fontSize: '18px',
                            fontWeight: '700',
                            minWidth: '60px',
                            textAlign: 'center'
                        }}>
                            {settings.searchThreshold.toFixed(2)}
                        </div>
                    </div>
                </div>

                {/* Status Message */}
                {status.message && (
                    <div style={{
                        padding: '16px 24px',
                        borderRadius: '14px',
                        background: status.type === 'success' ? 'rgba(16, 185, 129, 0.1)' : 'rgba(244, 63, 94, 0.1)',
                        color: status.type === 'success' ? '#10b981' : '#f43f5e',
                        border: `1px solid ${status.type === 'success' ? 'rgba(16, 185, 129, 0.2)' : 'rgba(244, 63, 94, 0.2)'}`,
                        display: 'flex',
                        alignItems: 'center',
                        gap: '12px',
                        animation: 'fadeIn 0.3s ease-out'
                    }}>
                        {status.type === 'success' ? <CheckCircle2 size={18} /> : <AlertCircle size={18} />}
                        {status.message}
                    </div>
                )}

                {/* Actions */}
                <div style={{ display: 'flex', gap: '16px', marginTop: '10px' }}>
                    <button
                        onClick={handleSave}
                        disabled={loading}
                        style={{
                            flex: 2,
                            padding: '16px',
                            background: 'linear-gradient(135deg, #00d2ff 0%, #3a7bd5 100%)',
                            border: 'none',
                            borderRadius: '16px',
                            color: 'white',
                            fontSize: '16px',
                            fontWeight: '600',
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            gap: '10px',
                            boxShadow: '0 8px 20px rgba(0, 210, 255, 0.2)'
                        }}
                    >
                        <Save size={20} />
                        {loading ? 'Committing Changes...' : 'Save Configurations'}
                    </button>

                    <button
                        onClick={fetchSettings}
                        disabled={loading}
                        style={{
                            flex: 1,
                            padding: '16px',
                            background: 'rgba(255, 255, 255, 0.05)',
                            border: '1px solid rgba(255, 255, 255, 0.1)',
                            borderRadius: '16px',
                            color: '#94a3b8',
                            fontSize: '16px',
                            fontWeight: '600',
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            gap: '10px'
                        }}
                    >
                        <RefreshCcw size={20} className={loading ? 'spinner' : ''} />
                        Reset
                    </button>
                </div>
            </div>

            <style>{`
                @keyframes fadeIn { from { opacity: 0; transform: translateY(-10px); } to { opacity: 1; transform: translateY(0); } }
                .spinner { animation: spin 1s linear infinite; }
                @keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
            `}</style>
        </div>
    );
};

export default Settings;
