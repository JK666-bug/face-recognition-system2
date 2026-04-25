import React, { useEffect, useState } from 'react';

const API_BASE = 'http://localhost:8080/api/detection';

const DetectionTask = () => {
    const [status, setStatus] = useState({
        state: 'IDLE',
        running: false,
        mode: 'IDLE',
        source: '0',
        location: 'meetingroom',
        uptimeSec: 0,
        lastError: null,
        showWindow: true
    });
    const [busy, setBusy] = useState(false);
    const [logs, setLogs] = useState([]);

    const [webcamLocation, setWebcamLocation] = useState('meetingroom');
    const [webcamWindow, setWebcamWindow] = useState(true);

    const [streamUrl, setStreamUrl] = useState('');
    const [streamLocation, setStreamLocation] = useState('home');
    const [streamWindow, setStreamWindow] = useState(false);

    const tokenHeaders = () => ({
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
    });

    const refresh = async () => {
        const [statusResp, logsResp] = await Promise.all([
            fetch(`${API_BASE}/status`, { headers: tokenHeaders() }),
            fetch(`${API_BASE}/logs?tail=120`, { headers: tokenHeaders() })
        ]);
        if (!statusResp.ok) throw new Error(`status request failed: ${statusResp.status}`);
        if (!logsResp.ok) throw new Error(`logs request failed: ${logsResp.status}`);
        const statusData = await statusResp.json();
        const logsData = await logsResp.json();
        setStatus(statusData);
        setLogs(Array.isArray(logsData.lines) ? logsData.lines : []);
    };

    useEffect(() => {
        let stopped = false;
        const loop = async () => {
            if (stopped) return;
            try {
                await refresh();
            } catch (error) {
                console.error('Failed to refresh detection task page:', error);
            }
        };
        loop();
        const timer = setInterval(loop, 3000);
        return () => {
            stopped = true;
            clearInterval(timer);
        };
    }, []);

    const startWebcam = async () => {
        setBusy(true);
        try {
            const response = await fetch(`${API_BASE}/webcam/start`, {
                method: 'POST',
                headers: {
                    ...tokenHeaders(),
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    location: webcamLocation || 'meetingroom',
                    showWindow: webcamWindow
                })
            });
            if (!response.ok) throw new Error(`webcam start failed: ${response.status}`);
            await refresh();
        } catch (error) {
            console.error(error);
        } finally {
            setBusy(false);
        }
    };

    const startStream = async () => {
        if (!streamUrl.trim()) return;
        setBusy(true);
        try {
            const response = await fetch(`${API_BASE}/stream/start`, {
                method: 'POST',
                headers: {
                    ...tokenHeaders(),
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    sourceUrl: streamUrl.trim(),
                    location: streamLocation || 'home',
                    showWindow: streamWindow
                })
            });
            if (!response.ok) throw new Error(`stream start failed: ${response.status}`);
            await refresh();
        } catch (error) {
            console.error(error);
        } finally {
            setBusy(false);
        }
    };

    const stopTask = async () => {
        setBusy(true);
        try {
            const response = await fetch(`${API_BASE}/stop`, {
                method: 'POST',
                headers: tokenHeaders()
            });
            if (!response.ok) throw new Error(`stop failed: ${response.status}`);
            await refresh();
        } catch (error) {
            console.error(error);
        } finally {
            setBusy(false);
        }
    };

    return (
        <div style={{ padding: '32px' }}>
            <h4 style={{ fontSize: '16px', fontWeight: '600', color: '#94a3b8', marginBottom: '16px', textTransform: 'uppercase', letterSpacing: '1px' }}>
                Detection Task
            </h4>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: '20px', marginBottom: '24px' }}>
                <div style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: '18px', padding: '18px' }}>
                    <div style={{ fontSize: '15px', fontWeight: 700, marginBottom: '10px' }}>Webcam Source</div>
                    <input value={webcamLocation} onChange={(e) => setWebcamLocation(e.target.value)} disabled={status.running || busy} placeholder="meetingroom"
                        style={{ width: '100%', marginBottom: '10px', background: 'rgba(2,6,23,0.7)', color: '#e2e8f0', border: '1px solid rgba(148,163,184,0.35)', borderRadius: '10px', padding: '10px 12px' }} />
                    <label style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#cbd5e1', marginBottom: '12px', fontSize: '13px' }}>
                        <input type="checkbox" checked={webcamWindow} onChange={(e) => setWebcamWindow(e.target.checked)} disabled={status.running || busy} />
                        Show preview window on this PC
                    </label>
                    <button onClick={startWebcam} disabled={status.running || busy}
                        style={{ background: '#10b981', color: '#fff', border: 'none', borderRadius: '10px', padding: '10px 14px', fontWeight: 600, cursor: status.running || busy ? 'not-allowed' : 'pointer' }}>
                        Start Webcam Detection
                    </button>
                </div>

                <div style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: '18px', padding: '18px' }}>
                    <div style={{ fontSize: '15px', fontWeight: 700, marginBottom: '10px' }}>Video Stream Source (RTSP/HTTP)</div>
                    <input value={streamUrl} onChange={(e) => setStreamUrl(e.target.value)} disabled={status.running || busy} placeholder="rtsp://127.0.0.1:8554/live"
                        style={{ width: '100%', marginBottom: '10px', background: 'rgba(2,6,23,0.7)', color: '#e2e8f0', border: '1px solid rgba(148,163,184,0.35)', borderRadius: '10px', padding: '10px 12px' }} />
                    <input value={streamLocation} onChange={(e) => setStreamLocation(e.target.value)} disabled={status.running || busy} placeholder="home"
                        style={{ width: '100%', marginBottom: '10px', background: 'rgba(2,6,23,0.7)', color: '#e2e8f0', border: '1px solid rgba(148,163,184,0.35)', borderRadius: '10px', padding: '10px 12px' }} />
                    <label style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#cbd5e1', marginBottom: '12px', fontSize: '13px' }}>
                        <input type="checkbox" checked={streamWindow} onChange={(e) => setStreamWindow(e.target.checked)} disabled={status.running || busy} />
                        Show preview window on this PC
                    </label>
                    <button onClick={startStream} disabled={status.running || busy || !streamUrl.trim()}
                        style={{ background: '#3b82f6', color: '#fff', border: 'none', borderRadius: '10px', padding: '10px 14px', fontWeight: 600, cursor: status.running || busy ? 'not-allowed' : 'pointer' }}>
                        Start Stream Detection
                    </button>
                </div>
            </div>

            <div style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: '18px', padding: '18px', marginBottom: '24px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '10px' }}>
                    <div style={{ fontWeight: 700 }}>Current Task Status</div>
                    <span style={{ padding: '6px 10px', borderRadius: '999px', fontSize: '12px', fontWeight: 700, color: status.running ? '#10b981' : status.state === 'ERROR' ? '#f43f5e' : '#94a3b8', background: status.running ? 'rgba(16,185,129,0.12)' : 'rgba(148,163,184,0.12)' }}>
                        {status.state}
                    </span>
                    <button onClick={stopTask} disabled={!status.running || busy}
                        style={{ marginLeft: 'auto', background: '#f43f5e', color: '#fff', border: 'none', borderRadius: '10px', padding: '8px 12px', fontWeight: 600, cursor: !status.running || busy ? 'not-allowed' : 'pointer' }}>
                        Stop Running Task
                    </button>
                </div>
                <div style={{ color: '#cbd5e1', fontSize: '13px', lineHeight: 1.6 }}>
                    Mode: <strong>{status.mode || 'IDLE'}</strong> | Source: <strong>{status.source || '-'}</strong> | Location: <strong>{status.location || '-'}</strong> | Uptime: <strong>{status.uptimeSec || 0}s</strong>
                    {status.lastError ? <div style={{ color: '#fda4af' }}>Error: {status.lastError}</div> : null}
                </div>
            </div>

            <div style={{ background: 'rgba(2,6,23,0.7)', border: '1px solid rgba(148,163,184,0.2)', borderRadius: '12px', padding: '12px', minHeight: '180px', maxHeight: '360px', overflowY: 'auto', fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace', fontSize: '12px', lineHeight: 1.45, color: '#cbd5e1', whiteSpace: 'pre-wrap' }}>
                {logs.length > 0 ? logs.join('\n') : 'No task logs yet.'}
            </div>
        </div>
    );
};

export default DetectionTask;
