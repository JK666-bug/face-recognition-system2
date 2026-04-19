import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    LayoutGrid,
    Users,
    History,
    Settings,
    Settings as SettingsIcon,
    LogOut,
    Search as SearchIcon,
    Shield,
    Activity,
    MapPin,
    User as UserIcon
} from 'lucide-react';
import Search from './Search';
import SettingsPage from './Settings';
import HistoryManager from './HistoryManager';
import AccessControl from './AccessControl';
import PeopleLibrary from './PeopleLibrary';

const ALERT_RETENTION_MS = 30 * 1000;

const DashboardContent = ({ liveAlerts, onDismissAlert }) => {
    const [stats, setStats] = useState({
        capturesToday: '-',
        totalFaces: '-',
        peopleToday: '-',
        totalPeople: '-',
        milvusStatus: 'Checking...',
        minioStatus: 'Checking...',
        rabbitMQStatus: 'Checking...',
        activeLocations: [],
        recentCaptures: []
    });

    useEffect(() => {
        const fetchStats = async () => {
            const token = localStorage.getItem('token');
            try {
                const response = await fetch('http://localhost:8080/api/stats', {
                    headers: { 'Authorization': `Bearer ${token}` }
                });
                const data = await response.json();
                setStats({
                    capturesToday: (data.capturesToday ?? 0).toLocaleString(),
                    totalFaces: (data.totalFaces ?? 0).toLocaleString(),
                    peopleToday: (data.peopleToday ?? 0).toLocaleString(),
                    totalPeople: (data.totalPeople ?? 0).toLocaleString(),
                    milvusStatus: data.milvusStatus || 'Unknown',
                    minioStatus: data.minioStatus || 'Unknown',
                    rabbitMQStatus: data.rabbitMQStatus || 'Unknown',
                    activeLocations: data.activeLocations || [],
                    recentCaptures: data.recentCaptures || []
                });
            } catch (error) {
                console.error('Failed to fetch stats:', error);
                setStats(prev => ({
                    ...prev,
                    milvusStatus: 'Offline',
                    minioStatus: 'Offline',
                    rabbitMQStatus: 'Offline',
                    recentCaptures: []
                }));
            }
        };

        fetchStats();
        const interval = setInterval(fetchStats, 5000); // Poll every 5 seconds
        return () => clearInterval(interval);
    }, []);

    const getStatusColor = (status) => {
        if (status === 'Active') return '#10b981';
        if (status === 'Checking...') return '#94a3b8';
        return '#f43f5e';
    };

    const mainStats = [
        { label: 'Captures Today', value: stats.capturesToday, color: '#00d2ff', icon: Activity },
        { label: 'Total Faces', value: stats.totalFaces, color: '#10b981', icon: Users },
        { label: 'People Today', value: stats.peopleToday, color: '#f59e0b', icon: UserIcon },
        { label: 'Total People', value: stats.totalPeople, color: '#a78bfa', icon: Users },
    ];

    const healthStats = [
        { label: 'Milvus DB', value: stats.milvusStatus, color: getStatusColor(stats.milvusStatus), icon: Shield },
        { label: 'MinIO Storage', value: stats.minioStatus, color: getStatusColor(stats.minioStatus), icon: Shield },
        { label: 'RabbitMQ', value: stats.rabbitMQStatus, color: getStatusColor(stats.rabbitMQStatus), icon: Activity }
    ];

    return (
        <div style={{ padding: '32px' }}>
            <h4 style={{ fontSize: '16px', fontWeight: '600', color: '#94a3b8', marginBottom: '16px', textTransform: 'uppercase', letterSpacing: '1px' }}>Global Statistics</h4>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '24px', marginBottom: '40px' }}>
                {mainStats.map((stat, i) => (
                    <div key={i} style={{
                        padding: '28px',
                        background: 'rgba(255, 255, 255, 0.03)',
                        borderRadius: '24px',
                        border: '1px solid rgba(255, 255, 255, 0.08)',
                        backdropFilter: 'blur(10px)'
                    }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px' }}>
                            <p style={{ color: '#94a3b8', fontSize: '14px', fontWeight: '500' }}>{stat.label}</p>
                            <stat.icon size={20} color={stat.color} style={{ opacity: 0.8 }} />
                        </div>
                        <h3 style={{ fontSize: '32px', fontWeight: '700', color: stat.color, letterSpacing: '-0.5px' }}>{stat.value}</h3>
                    </div>
                ))}
            </div>

            <h4 style={{ fontSize: '16px', fontWeight: '600', color: '#94a3b8', marginBottom: '16px', textTransform: 'uppercase', letterSpacing: '1px' }}>System Health Pipeline</h4>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '24px', marginBottom: '40px' }}>
                {healthStats.map((stat, i) => (
                    <div key={i} style={{
                        padding: '20px',
                        background: 'rgba(255, 255, 255, 0.02)',
                        borderRadius: '20px',
                        border: '1px solid rgba(255, 255, 255, 0.05)',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '16px'
                    }}>
                        <div style={{
                            width: '40px',
                            height: '40px',
                            borderRadius: '10px',
                            background: `${stat.color}15`,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center'
                        }}>
                            <stat.icon size={20} color={stat.color} />
                        </div>
                        <div>
                            <p style={{ color: '#94a3b8', fontSize: '12px', marginBottom: '4px' }}>{stat.label}</p>
                            <p style={{ fontSize: '16px', fontWeight: '600', color: stat.color }}>{stat.value}</p>
                        </div>
                    </div>
                ))}
            </div>

            <h4 style={{ fontSize: '16px', fontWeight: '600', color: '#fda4af', marginBottom: '16px', textTransform: 'uppercase', letterSpacing: '1px' }}>Live Suspect Alerts</h4>
            <div style={{
                background: 'rgba(244, 63, 94, 0.08)',
                borderRadius: '24px',
                padding: '20px',
                border: '1px solid rgba(244, 63, 94, 0.25)',
                marginBottom: '30px'
            }}>
                {liveAlerts.length === 0 ? (
                    <div style={{ color: '#fda4af', opacity: 0.8 }}>No live suspect alerts yet.</div>
                ) : (
                    <div style={{ display: 'grid', gap: '12px' }}>
                        {liveAlerts.map((alert, idx) => (
                            <div key={`${alert.faceId}-${idx}`} style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: '12px',
                                background: 'rgba(15,23,42,0.6)',
                                border: '1px solid rgba(244,63,94,0.2)',
                                borderRadius: '12px',
                                padding: '10px'
                            }}>
                                {alert.imageBase64 && (
                                    <img
                                        src={`data:image/jpeg;base64,${alert.imageBase64}`}
                                        alt="alert"
                                        style={{ width: '52px', height: '52px', borderRadius: '8px', objectFit: 'cover' }}
                                    />
                                )}
                                <div style={{ fontSize: '12px' }}>
                                    <div style={{ color: '#fda4af', fontWeight: 700 }}>
                                        ALERT: matched suspect {alert.alertPeopleId || alert.peopleId}
                                    </div>
                                    <div style={{ color: '#cbd5e1' }}>
                                        Location: {alert.location || 'Unknown'} | Time: {alert.timestamp || 'Unknown'}
                                    </div>
                                </div>
                                <button
                                    onClick={() => onDismissAlert(alert.receivedAt, alert.faceId)}
                                    style={{
                                        marginLeft: 'auto',
                                        background: 'transparent',
                                        border: '1px solid rgba(244,63,94,0.35)',
                                        color: '#fda4af',
                                        borderRadius: '8px',
                                        padding: '6px 10px',
                                        cursor: 'pointer',
                                        fontSize: '11px',
                                        fontWeight: 600
                                    }}
                                >
                                    Dismiss
                                </button>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            <h4 style={{ fontSize: '16px', fontWeight: '600', color: '#94a3b8', marginBottom: '16px', textTransform: 'uppercase', letterSpacing: '1px', marginTop: '40px' }}>Latest Intelligence Activity</h4>
            <div style={{
                background: 'rgba(255, 255, 255, 0.02)',
                borderRadius: '24px',
                padding: '24px',
                border: '1px solid rgba(255, 255, 255, 0.05)',
                overflow: 'hidden',
                marginBottom: '20px'
            }}>
                <div style={{ 
                    display: 'flex', 
                    gap: '20px', 
                    overflowX: 'auto', 
                    paddingBottom: '12px',
                    scrollbarWidth: 'thin'
                }}>
                    {stats.recentCaptures.length > 0 ? (
                        stats.recentCaptures.map((cap, i) => (
                            <div key={i} style={{
                                minWidth: '160px',
                                background: 'rgba(255, 255, 255, 0.03)',
                                borderRadius: '18px',
                                overflow: 'hidden',
                                border: '1px solid rgba(255, 255, 255, 0.05)',
                                transition: 'transform 0.3s'
                            }}>
                                <img 
                                    src={`data:image/jpeg;base64,${cap.imageBase64}`} 
                                    style={{ width: '100%', height: '160px', objectFit: 'cover' }} 
                                    alt="Capture" 
                                />
                                <div style={{ padding: '12px' }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#00d2ff', fontSize: '11px', fontWeight: 'bold' }}>
                                        <MapPin size={12} />
                                        {cap.location}
                                    </div>
                                    <div style={{ color: '#94a3b8', fontSize: '10px', marginTop: '4px' }}>
                                        {cap.timestamp && cap.timestamp.includes('T') ? cap.timestamp.split('T')[1].substring(0, 8) : cap.timestamp}
                                    </div>
                                </div>
                            </div>
                        ))
                    ) : (
                        <div style={{ width: '100%', textAlign: 'center', padding: '40px', color: '#475569', fontSize: '14px' }}>
                            <div style={{ marginBottom: '10px' }}>No recent activity detected in the last window.</div>
                            <div style={{ fontSize: '12px', opacity: 0.6 }}>Start the recognition stream to see live results.</div>
                        </div>
                    )}
                </div>
            </div>

            <h4 style={{ fontSize: '16px', fontWeight: '600', color: '#94a3b8', marginBottom: '16px', textTransform: 'uppercase', letterSpacing: '1px', marginTop: '40px' }}>Active Capture Nodes</h4>
            <div style={{
                background: 'rgba(255, 255, 255, 0.02)',
                borderRadius: '24px',
                padding: '32px',
                border: '1px solid rgba(255, 255, 255, 0.05)'
            }}>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '16px' }}>
                    {stats.activeLocations.length > 0 ? (
                        stats.activeLocations.map((loc, i) => (
                            <div key={i} style={{
                                padding: '12px 20px',
                                background: 'rgba(0, 210, 255, 0.1)',
                                color: '#00d2ff',
                                borderRadius: '12px',
                                border: '1px solid rgba(0, 210, 255, 0.2)',
                                display: 'flex',
                                alignItems: 'center',
                                gap: '8px',
                                fontSize: '14px',
                                fontWeight: '600'
                            }}>
                                <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#00d2ff', boxShadow: '0 0 8px #00d2ff' }} />
                                {loc}
                            </div>
                        ))
                    ) : (
                        <div style={{ width: '100%', textAlign: 'center', padding: '40px', color: '#94a3b8', border: '1px dashed rgba(255, 255, 255, 0.1)', borderRadius: '16px' }}>
                            No active camera nodes detected. Launch algorithm scripts to begin monitoring.
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

const Dashboard = () => {
    const navigate = useNavigate();
    const [activeTab, setActiveTab] = useState('overview');
    const [liveAlerts, setLiveAlerts] = useState(() => {
        try {
            const rawAlerts = JSON.parse(localStorage.getItem('liveAlerts') || '[]');
            const now = Date.now();
            return Array.isArray(rawAlerts)
                ? rawAlerts.filter(alert => alert?.receivedAt && now - alert.receivedAt <= ALERT_RETENTION_MS)
                : [];
        } catch {
            return [];
        }
    });
    const user = JSON.parse(localStorage.getItem('user') || '{"username":"Guest","role":"GUEST","id":"0"}');

    useEffect(() => {
        const ws = new WebSocket('ws://localhost:8080/ws-alerts');
        ws.onmessage = (event) => {
            try {
                const alert = JSON.parse(event.data);
                if (alert.type === 'SUSPECT_ALERT') {
                    const stampedAlert = { ...alert, receivedAt: Date.now() };
                    setLiveAlerts(prev => [stampedAlert, ...prev]
                        .filter(item => stampedAlert.receivedAt - item.receivedAt <= ALERT_RETENTION_MS)
                        .slice(0, 20));
                }
            } catch (e) {
                console.error('Failed to parse websocket alert:', e);
            }
        };
        ws.onerror = (err) => console.error('Alert websocket error:', err);
        return () => ws.close();
    }, []);

    useEffect(() => {
        const interval = setInterval(() => {
            const now = Date.now();
            setLiveAlerts(prev => prev.filter(alert => now - alert.receivedAt <= ALERT_RETENTION_MS));
        }, 30000);
        return () => clearInterval(interval);
    }, []);

    useEffect(() => {
        localStorage.setItem('liveAlerts', JSON.stringify(liveAlerts));
    }, [liveAlerts]);

    const handleDismissAlert = (receivedAt, faceId) => {
        setLiveAlerts(prev => prev.filter(alert => !(alert.receivedAt === receivedAt && alert.faceId === faceId)));
    };

    const handleLogout = () => {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        localStorage.removeItem('liveAlerts');
        navigate('/login');
    };

    const canPeopleLibrary = user.role === 'SUPER_ADMIN';

    useEffect(() => {
        if (!canPeopleLibrary && activeTab === 'people') {
            setActiveTab('overview');
        }
    }, [canPeopleLibrary, activeTab]);

    const navItems = [
        { id: 'overview', icon: LayoutGrid, label: 'Analytics Overview', visible: true },
        { id: 'search', icon: SearchIcon, label: 'Trajectory Search', visible: true },
        { id: 'people', icon: Users, label: 'People Library', visible: canPeopleLibrary },
        { id: 'history', icon: History, label: 'Dataset', visible: user.role === 'SUPER_ADMIN' },
        { id: 'users', icon: Users, label: 'Access Control', visible: user.role === 'SUPER_ADMIN' },
        { id: 'settings', icon: Settings, label: 'Settings', visible: user.role === 'SUPER_ADMIN' }
    ].filter(item => item.visible);

    return (
        <div style={{ display: 'flex', minHeight: '100vh', background: '#0f172a', color: 'white', fontFamily: 'Inter, system-ui, sans-serif' }}>
            {/* Sidebar */}
            <div style={{
                width: '280px',
                background: 'rgba(15, 23, 42, 0.9)',
                backdropFilter: 'blur(20px)',
                borderRight: '1px solid rgba(255, 255, 255, 0.08)',
                padding: '40px 24px',
                display: 'flex',
                flexDirection: 'column'
            }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '48px', paddingLeft: '8px' }}>
                    <div style={{ width: '40px', height: '40px', background: 'linear-gradient(135deg, #00d2ff 0%, #3a7bd5 100%)', borderRadius: '12px', display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: '0 8px 16px rgba(0, 210, 255, 0.2)' }}>
                        <Shield size={22} color="white" />
                    </div>
                    <div>
                        <h2 style={{ fontSize: '18px', fontWeight: '700', letterSpacing: '0.5px' }}>FaceGuard AI</h2>
                        <span style={{ fontSize: '10px', color: '#00d2ff', fontWeight: '600', textTransform: 'uppercase' }}>Enterprise</span>
                    </div>
                </div>

                <nav style={{ flex: 1 }}>
                    {navItems.map(item => (
                        <button
                            key={item.id}
                            onClick={() => setActiveTab(item.id)}
                            style={{
                                width: '100%',
                                display: 'flex',
                                alignItems: 'center',
                                gap: '14px',
                                padding: '14px 18px',
                                marginBottom: '6px',
                                borderRadius: '14px',
                                background: activeTab === item.id ? 'rgba(0, 210, 255, 0.08)' : 'transparent',
                                color: activeTab === item.id ? '#00d2ff' : '#94a3b8',
                                border: 'none',
                                cursor: 'pointer',
                                fontSize: '15px',
                                fontWeight: activeTab === item.id ? '600' : '500',
                                transition: 'all 0.25s cubic-bezier(0.4, 0, 0.2, 1)'
                            }}
                        >
                            <item.icon size={20} />
                            {item.label}
                        </button>
                    ))}
                </nav>

                <div style={{ marginTop: 'auto' }}>
                    <button
                        onClick={handleLogout}
                        style={{
                            width: '100%',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '12px',
                            padding: '16px',
                            borderRadius: '14px',
                            color: '#f43f5e',
                            border: '1px solid rgba(244, 63, 94, 0.2)',
                            background: 'rgba(244, 63, 94, 0.05)',
                            cursor: 'pointer',
                            fontSize: '15px',
                            fontWeight: '600'
                        }}
                    >
                        <LogOut size={20} />
                        End Session
                    </button>
                </div>
            </div>

            {/* Main Content */}
            <div style={{ flex: 1, height: '100vh', overflowY: 'auto', background: 'radial-gradient(circle at top right, rgba(0, 210, 255, 0.03), transparent)' }}>
                <header style={{
                    padding: '32px 48px',
                    borderBottom: '1px solid rgba(255, 255, 255, 0.05)',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    background: 'rgba(15, 23, 42, 0.5)',
                    backdropFilter: 'blur(8px)',
                    position: 'sticky',
                    top: 0,
                    zIndex: 10
                }}>
                    <div>
                        <h1 style={{ fontSize: '26px', fontWeight: '700', marginBottom: '4px' }}>
                            {activeTab === 'overview' ? 'Command Center' :
                                activeTab === 'search' ? 'Trajectory Analysis' :
                                    activeTab === 'people' ? 'People Library' :
                                    activeTab === 'history' ? 'Dataset Explorer' :
                                        activeTab === 'users' ? 'Identity Management' :
                                            'System Configuration'}
                        </h1>
                        <p style={{ color: '#94a3b8', fontSize: '14px' }}>
                            {activeTab === 'overview' ? 'Real-time intelligence and node health monitoring' :
                                activeTab === 'search' ? 'Cross-referencing face signatures across distributed nodes' :
                                    activeTab === 'people' ? 'De-duplicated identity base and suspect controls' :
                                    activeTab === 'history' ? 'Reviewing the global capture database' :
                                        activeTab === 'users' ? 'Managing administrative access and privileges' :
                                            'Fine-tuning system precision and metadata'}
                        </p>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '10px 20px', background: 'rgba(255,255,255,0.03)', borderRadius: '16px', border: '1px solid rgba(255,255,255,0.08)' }}>
                        <div style={{ textAlign: 'right' }}>
                            <div style={{ fontSize: '14px', fontWeight: 'bold' }}>{user.username}</div>
                            <div style={{ fontSize: '10px', color: '#00d2ff', fontWeight: 'bold' }}>
                                {user.role === 'SUPER_ADMIN' ? '🛡️ SUPER_ADMIN' : '👤 OPERATOR_ADMIN'} (ID: {user.id})
                            </div>
                        </div>
                        <div style={{ width: '40px', height: '40px', borderRadius: '12px', background: 'rgba(0, 210, 255, 0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#00d2ff' }}>
                            <UserIcon size={20} />
                        </div>
                    </div>
                </header>

                <main style={{ padding: '16px' }}>
                    {activeTab === 'overview' ? (
                        <DashboardContent liveAlerts={liveAlerts} onDismissAlert={handleDismissAlert} />
                    ) : activeTab === 'search' ? (
                        <Search />
                    ) : activeTab === 'people' && canPeopleLibrary ? (
                        <PeopleLibrary />
                    ) : activeTab === 'history' ? (
                        <HistoryManager />
                    ) : activeTab === 'users' ? (
                        <AccessControl />
                    ) : activeTab === 'settings' ? (
                        <SettingsPage />
                    ) : (
                        <div style={{ padding: '80px 40px', textAlign: 'center', color: '#475569' }}>
                            <div style={{ marginBottom: '24px', display: 'flex', justifyContent: 'center' }}>
                                <SettingsIcon size={48} style={{ opacity: 0.2 }} />
                            </div>
                            <h2 style={{ fontSize: '20px', fontWeight: '600', color: '#94a3b8' }}>Module in Preparation</h2>
                            <p style={{ marginTop: '8px' }}>This feature is scheduled for the next deployment phase.</p>
                        </div>
                    )}
                </main>
            </div>
        </div>
    );
};

export default Dashboard;
