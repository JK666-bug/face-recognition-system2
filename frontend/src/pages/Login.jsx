import React, { useState } from 'react';
import { Shield, Lock, User, Loader2, AlertCircle } from 'lucide-react';

const Login = ({ onLogin }) => {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const handleSubmit = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError('');

        try {
            const response = await fetch('http://localhost:8080/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });

            if (response.ok) {
                const data = await response.json();
                localStorage.setItem('token', data.token);
                localStorage.setItem('user', JSON.stringify({
                    id: data.id,
                    username: data.username,
                    role: data.role
                }));
                onLogin(data);
            } else {
                setError('Invalid credentials. Access denied.');
            }
        } catch (err) {
            setError('System offline. Please check connection.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={{ 
            height: '100vh', width: '100vw', background: '#0f172a', 
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontFamily: 'Inter, sans-serif'
        }}>
            <div style={{ 
                width: '400px', background: 'rgba(255,255,255,0.03)', 
                backdropFilter: 'blur(20px)', border: '1px solid rgba(255,255,255,0.08)',
                borderRadius: '32px', padding: '48px', boxShadow: '0 24px 64px rgba(0,0,0,0.4)'
            }}>
                <div style={{ textAlign: 'center', marginBottom: '40px' }}>
                    <div style={{ 
                        width: '64px', height: '64px', background: 'linear-gradient(135deg, #00d2ff 0%, #3a7bd5 100%)',
                        borderRadius: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center',
                        margin: '0 auto 20px', boxShadow: '0 8px 16px rgba(0, 210, 255, 0.3)'
                    }}>
                        <Shield size={32} color="white" />
                    </div>
                    <h1 style={{ color: 'white', fontSize: '24px', fontWeight: '800', marginBottom: '8px' }}>FaceGuard AI</h1>
                    <p style={{ color: '#64748b', fontSize: '14px' }}>Enterprise Surveillance & Analysis</p>
                </div>

                <form onSubmit={handleSubmit}>
                    <div style={{ marginBottom: '24px' }}>
                        <label style={{ color: '#94a3b8', fontSize: '12px', fontWeight: 'bold', textTransform: 'uppercase', marginBottom: '8px', display: 'block' }}>Identity</label>
                        <div style={{ position: 'relative' }}>
                            <User size={18} style={{ position: 'absolute', left: '16px', top: '50%', transform: 'translateY(-50%)', color: '#475569' }} />
                            <input 
                                type="text" 
                                value={username}
                                onChange={(e) => setUsername(e.target.value)}
                                placeholder="Username"
                                style={{ 
                                    width: '100%', padding: '14px 16px 14px 48px', background: 'rgba(255,255,255,0.02)',
                                    border: '1px solid rgba(255,255,255,0.1)', borderRadius: '12px', color: 'white',
                                    outline: 'none', transition: 'all 0.3s'
                                }}
                                required
                            />
                        </div>
                    </div>

                    <div style={{ marginBottom: '32px' }}>
                        <label style={{ color: '#94a3b8', fontSize: '12px', fontWeight: 'bold', textTransform: 'uppercase', marginBottom: '8px', display: 'block' }}>Secret Key</label>
                        <div style={{ position: 'relative' }}>
                            <Lock size={18} style={{ position: 'absolute', left: '16px', top: '50%', transform: 'translateY(-50%)', color: '#475569' }} />
                            <input 
                                type="password" 
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                placeholder="••••••••"
                                style={{ 
                                    width: '100%', padding: '14px 16px 14px 48px', background: 'rgba(255,255,255,0.02)',
                                    border: '1px solid rgba(255,255,255,0.1)', borderRadius: '12px', color: 'white',
                                    outline: 'none', transition: 'all 0.3s'
                                }}
                                required
                            />
                        </div>
                    </div>

                    {error && (
                        <div style={{ 
                            display: 'flex', alignItems: 'center', gap: '8px', color: '#f43f5e', 
                            fontSize: '13px', marginBottom: '24px', background: 'rgba(244, 63, 94, 0.1)',
                            padding: '12px', borderRadius: '10px'
                        }}>
                            <AlertCircle size={16} />
                            {error}
                        </div>
                    )}

                    <button 
                        type="submit" 
                        disabled={loading}
                        style={{ 
                            width: '100%', padding: '16px', background: 'linear-gradient(135deg, #00d2ff 0%, #3a7bd5 100%)',
                            color: 'white', border: 'none', borderRadius: '12px', fontWeight: '700',
                            fontSize: '16px', cursor: 'pointer', boxShadow: '0 8px 24px rgba(0, 210, 255, 0.2)',
                            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '10px'
                        }}
                    >
                        {loading ? <Loader2 className="spinner" size={20} /> : 'Login to System'}
                    </button>
                </form>

                <p style={{ color: '#475569', fontSize: '12px', textAlign: 'center', marginTop: '40px' }}>
                    Secured by Quantum-Ready Encryption
                </p>
            </div>
            <style>{`
                .spinner { animation: spin 1s linear infinite; }
                @keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
                input:focus { border-color: #00d2ff !important; box-shadow: 0 0 0 4px rgba(0, 210, 255, 0.1); }
            `}</style>
        </div>
    );
};

export default Login;
