import React, { useState, useEffect } from 'react';
import { UserPlus, ShieldCheck, UserCog, UserCheck, ShieldAlert, Loader2, Key, User as UserIcon } from 'lucide-react';

const AccessControl = () => {
    const [users, setUsers] = useState([]);
    const [loading, setLoading] = useState(false);
    const [regUsername, setRegUsername] = useState('');
    const [regPassword, setRegPassword] = useState('');
    const [regLoading, setRegLoading] = useState(false);
    const currentUser = JSON.parse(localStorage.getItem('user') || '{}');

    useEffect(() => {
        fetchUsers();
    }, []);

    const fetchUsers = async () => {
        setLoading(true);
        const token = localStorage.getItem('token');
        try {
            const response = await fetch('http://localhost:8080/api/management/admin/users', {
                headers: { 'Authorization': `Bearer ${token}` }
            });
            if (response.ok) {
                const data = await response.json();
                setUsers(data);
            }
        } catch (error) {
            console.error('Failed to fetch users:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleRegister = async (e) => {
        e.preventDefault();
        setRegLoading(true);
        const token = localStorage.getItem('token');
        try {
            const response = await fetch('http://localhost:8080/api/management/admin/register', {
                method: 'POST',
                headers: { 
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${token}`
                },
                body: JSON.stringify({ username: regUsername, password: regPassword })
            });

            if (response.ok) {
                alert('Operator registered successfully!');
                setRegUsername('');
                setRegPassword('');
                fetchUsers();
            } else {
                const msg = await response.text();
                alert(msg);
            }
        } catch (error) {
            alert('Registration failed.');
        } finally {
            setRegLoading(false);
        }
    };

    const handlePromote = async (id) => {
        if (!window.confirm('Are you sure you want to promote this user to SUPER ADMIN?')) return;
        const token = localStorage.getItem('token');
        try {
            const response = await fetch(`http://localhost:8080/api/management/admin/promote/${id}`, {
                method: 'POST',
                headers: { 'Authorization': `Bearer ${token}` }
            });
            if (response.ok) {
                fetchUsers();
            }
        } catch (error) {
            alert('Promotion failed.');
        }
    };

    if (currentUser.role !== 'SUPER_ADMIN') {
        return (
            <div style={{ padding: '80px', textAlign: 'center' }}>
                <ShieldAlert size={64} color="#f43f5e" style={{ margin: '0 auto 24px' }} />
                <h2 style={{ color: 'white', fontSize: '24px', fontWeight: 'bold' }}>Access Denied</h2>
                <p style={{ color: '#64748b', marginTop: '12px' }}>Only Super Administrators can access the identity management console.</p>
            </div>
        );
    }

    return (
        <div style={{ padding: '30px' }}>
            <div style={{ marginBottom: '40px' }}>
                <h2 style={{ fontSize: '28px', fontWeight: '800', marginBottom: '8px' }}>Access Control Center</h2>
                <p style={{ color: '#64748b' }}>Manage system administrators, provision new accounts, and adjust privilege levels.</p>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.5fr', gap: '32px', alignItems: 'start' }}>
                {/* Registration Form */}
                <div style={{ 
                    background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.08)',
                    borderRadius: '24px', padding: '32px'
                }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '24px' }}>
                        <div style={{ padding: '10px', background: 'rgba(0, 210, 255, 0.1)', borderRadius: '12px', color: '#00d2ff' }}>
                            <UserPlus size={20} />
                        </div>
                        <h3 style={{ fontSize: '18px', fontWeight: '700' }}>Provision New Admin</h3>
                    </div>

                    <form onSubmit={handleRegister}>
                        <div style={{ marginBottom: '20px' }}>
                            <label style={{ color: '#94a3b8', fontSize: '12px', fontWeight: 'bold', marginBottom: '8px', display: 'block' }}>USER IDENTITY</label>
                            <div style={{ position: 'relative' }}>
                                <UserIcon size={16} style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', color: '#475569' }} />
                                <input 
                                    type="text" 
                                    value={regUsername}
                                    onChange={(e) => setRegUsername(e.target.value)}
                                    placeholder="Username" 
                                    required
                                    style={{ 
                                        width: '100%', padding: '12px 12px 12px 38px', background: 'rgba(0,0,0,0.2)',
                                        border: '1px solid rgba(255,255,255,0.1)', borderRadius: '10px', color: 'white'
                                    }}
                                />
                            </div>
                        </div>

                        <div style={{ marginBottom: '24px' }}>
                            <label style={{ color: '#94a3b8', fontSize: '12px', fontWeight: 'bold', marginBottom: '8px', display: 'block' }}>TEMPORARY SECRET</label>
                            <div style={{ position: 'relative' }}>
                                <Key size={16} style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', color: '#475569' }} />
                                <input 
                                    type="password" 
                                    value={regPassword}
                                    onChange={(e) => setRegPassword(e.target.value)}
                                    placeholder="••••••••" 
                                    required
                                    style={{ 
                                        width: '100%', padding: '12px 12px 12px 38px', background: 'rgba(0,0,0,0.2)',
                                        border: '1px solid rgba(255,255,255,0.1)', borderRadius: '10px', color: 'white'
                                    }}
                                />
                            </div>
                        </div>

                        <button 
                            type="submit" 
                            disabled={regLoading}
                            style={{ 
                                width: '100%', padding: '14px', background: '#00d2ff', color: '#0f172a',
                                border: 'none', borderRadius: '12px', fontWeight: '700', cursor: 'pointer',
                                display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px'
                            }}
                        >
                            {regLoading ? <Loader2 className="spinner" size={18} /> : <ShieldCheck size={18} />}
                            Finalize Provisioning
                        </button>
                    </form>
                </div>

                {/* User List */}
                <div style={{ 
                    background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.08)',
                    borderRadius: '24px', padding: '32px'
                }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '24px' }}>
                        <div style={{ padding: '10px', background: 'rgba(168, 85, 247, 0.1)', borderRadius: '12px', color: '#a855f7' }}>
                            <UserCog size={20} />
                        </div>
                        <h3 style={{ fontSize: '18px', fontWeight: '700' }}>Active Registry</h3>
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                        {users.map(user => (
                            <div key={user.id} style={{ 
                                padding: '16px', background: 'rgba(255,255,255,0.02)', 
                                border: '1px solid rgba(255,255,255,0.05)', borderRadius: '16px',
                                display: 'flex', alignItems: 'center', justifyContent: 'space-between'
                            }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                                    <div style={{ 
                                        width: '40px', height: '40px', borderRadius: '50%', 
                                        background: user.role === 'SUPER_ADMIN' ? '#a855f7' : '#475569',
                                        display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'white'
                                    }}>
                                        <UserIcon size={20} />
                                    </div>
                                    <div>
                                        <div style={{ fontWeight: '700' }}>{user.username}</div>
                                        <div style={{ fontSize: '12px', color: '#64748b' }}>Account ID: {user.id}</div>
                                    </div>
                                </div>

                                <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                                    <span style={{ 
                                        padding: '4px 10px', borderRadius: '6px', fontSize: '10px', fontWeight: 'bold',
                                        background: user.role === 'SUPER_ADMIN' ? 'rgba(168, 85, 247, 0.1)' : 'rgba(100, 116, 139, 0.1)',
                                        color: user.role === 'SUPER_ADMIN' ? '#a855f7' : '#94a3b8',
                                        border: `1px solid ${user.role === 'SUPER_ADMIN' ? 'rgba(168, 85, 247, 0.2)' : 'rgba(100, 116, 139, 0.2)'}`
                                    }}>
                                        {user.role}
                                    </span>
                                    
                                    {user.role === 'ADMIN' && (
                                        <button 
                                            onClick={() => handlePromote(user.id)}
                                            style={{ 
                                                padding: '8px 12px', background: 'rgba(0, 210, 255, 0.1)',
                                                border: '1px solid rgba(0, 210, 255, 0.2)', borderRadius: '8px',
                                                color: '#00d2ff', cursor: 'pointer', fontSize: '12px',
                                                display: 'flex', alignItems: 'center', gap: '6px'
                                            }}
                                        >
                                            <UserCheck size={14} />
                                            Promote
                                        </button>
                                    )}
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            </div>
        </div>
    );
};

export default AccessControl;
