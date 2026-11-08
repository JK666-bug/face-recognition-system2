import React, { useEffect, useRef, useState } from 'react';

const API_BASE = 'http://localhost:8080/api/detection';

const DetectionTask = () => {
    const [tasks, setTasks] = useState([]);
    const [selectedTaskId, setSelectedTaskId] = useState('');
    const [busy, setBusy] = useState(false);
    const [logs, setLogs] = useState([]);
    const [notice, setNotice] = useState('');

    const [webcamLocation, setWebcamLocation] = useState('meetingroom');
    const [webcamWindow, setWebcamWindow] = useState(true);

    const [streamUrl, setStreamUrl] = useState('');
    const [streamLocation, setStreamLocation] = useState('home');
    const [streamWindow, setStreamWindow] = useState(false);

    const selectedTaskIdRef = useRef('');

    const tokenHeaders = () => ({
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
    });

    const chooseTask = (taskId) => {
        selectedTaskIdRef.current = taskId;
        setSelectedTaskId(taskId);
    };

    const refresh = async () => {
        const tasksResp = await fetch(`${API_BASE}/tasks`, { headers: tokenHeaders() });
        if (!tasksResp.ok) throw new Error(`tasks request failed: ${tasksResp.status}`);
        const taskList = await tasksResp.json();
        const safeList = Array.isArray(taskList) ? taskList : [];
        setTasks(safeList);

        const currentId = selectedTaskIdRef.current;
        const hasSelected = currentId && safeList.some((task) => task.taskId === currentId);
        const nextTaskId = hasSelected ? currentId : (safeList[0]?.taskId || '');
        if (nextTaskId !== currentId) {
            chooseTask(nextTaskId);
        }

        if (nextTaskId) {
            const logsResp = await fetch(`${API_BASE}/tasks/${encodeURIComponent(nextTaskId)}/logs?tail=120`, { headers: tokenHeaders() });
            if (!logsResp.ok) throw new Error(`logs request failed: ${logsResp.status}`);
            const logsData = await logsResp.json();
            setLogs(Array.isArray(logsData.lines) ? logsData.lines : []);
        } else {
            setLogs([]);
        }
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

    const readError = async (response, fallback) => {
        const data = await response.json().catch(() => ({}));
        if (!response.ok || data.state === 'ERROR' || data.error) {
            throw new Error(data.lastError || data.error || fallback);
        }
        return data;
    };

    const startWebcam = async () => {
        setBusy(true);
        setNotice('');
        try {
            const response = await fetch(`${API_BASE}/tasks/webcam/start`, {
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
            const createdTask = await readError(response, `webcam start failed: ${response.status}`);
            if (createdTask?.taskId) chooseTask(createdTask.taskId);
            await refresh();
        } catch (error) {
            setNotice(error.message);
        } finally {
            setBusy(false);
        }
    };

    const startStream = async () => {
        if (!streamUrl.trim()) return;
        setBusy(true);
        setNotice('');
        try {
            const response = await fetch(`${API_BASE}/tasks/stream/start`, {
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
            const createdTask = await readError(response, `stream start failed: ${response.status}`);
            if (createdTask?.taskId) chooseTask(createdTask.taskId);
            await refresh();
        } catch (error) {
            setNotice(error.message);
        } finally {
            setBusy(false);
        }
    };

    const stopTask = async (taskId) => {
        setBusy(true);
        setNotice('');
        try {
            const response = await fetch(`${API_BASE}/tasks/${encodeURIComponent(taskId)}/stop`, {
                method: 'POST',
                headers: tokenHeaders()
            });
            await readError(response, `stop failed: ${response.status}`);
            await refresh();
        } catch (error) {
            setNotice(error.message);
        } finally {
            setBusy(false);
        }
    };

    const deleteTask = async (taskId) => {
        setBusy(true);
        setNotice('');
        try {
            const response = await fetch(`${API_BASE}/tasks/${encodeURIComponent(taskId)}`, {
                method: 'DELETE',
                headers: tokenHeaders()
            });
            await readError(response, `delete failed: ${response.status}`);
            if (selectedTaskIdRef.current === taskId) {
                chooseTask('');
                setLogs([]);
            }
            await refresh();
        } catch (error) {
            setNotice(error.message);
        } finally {
            setBusy(false);
        }
    };

    return (
        <div style={{ padding: '32px' }}>
            <h4 style={{ fontSize: '16px', fontWeight: '600', color: '#94a3b8', marginBottom: '16px', textTransform: 'uppercase', letterSpacing: '1px' }}>
                Detection Task
            </h4>
            {notice ? (
                <div style={{ marginBottom: '16px', color: '#fda4af', fontSize: '13px' }}>{notice}</div>
            ) : null}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: '20px', marginBottom: '24px' }}>
                <div style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: '18px', padding: '18px' }}>
                    <div style={{ fontSize: '15px', fontWeight: 700, marginBottom: '10px' }}>Webcam Source</div>
                    <input value={webcamLocation} onChange={(e) => setWebcamLocation(e.target.value)} disabled={busy} placeholder="meetingroom"
                        style={{ width: '100%', marginBottom: '10px', background: 'rgba(2,6,23,0.7)', color: '#e2e8f0', border: '1px solid rgba(148,163,184,0.35)', borderRadius: '10px', padding: '10px 12px' }} />
                    <label style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#cbd5e1', marginBottom: '12px', fontSize: '13px' }}>
                        <input type="checkbox" checked={webcamWindow} onChange={(e) => setWebcamWindow(e.target.checked)} disabled={busy} />
                        Show preview window on this PC
                    </label>
                    <button onClick={startWebcam} disabled={busy}
                        style={{ background: '#10b981', color: '#fff', border: 'none', borderRadius: '10px', padding: '10px 14px', fontWeight: 600, cursor: busy ? 'not-allowed' : 'pointer' }}>
                        Start Webcam Detection
                    </button>
                </div>

                <div style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: '18px', padding: '18px' }}>
                    <div style={{ fontSize: '15px', fontWeight: 700, marginBottom: '10px' }}>Video Stream Source (RTSP/HTTP)</div>
                    <input value={streamUrl} onChange={(e) => setStreamUrl(e.target.value)} disabled={busy} placeholder="rtsp://127.0.0.1:8554/live"
                        style={{ width: '100%', marginBottom: '10px', background: 'rgba(2,6,23,0.7)', color: '#e2e8f0', border: '1px solid rgba(148,163,184,0.35)', borderRadius: '10px', padding: '10px 12px' }} />
                    <input value={streamLocation} onChange={(e) => setStreamLocation(e.target.value)} disabled={busy} placeholder="home"
                        style={{ width: '100%', marginBottom: '10px', background: 'rgba(2,6,23,0.7)', color: '#e2e8f0', border: '1px solid rgba(148,163,184,0.35)', borderRadius: '10px', padding: '10px 12px' }} />
                    <label style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#cbd5e1', marginBottom: '12px', fontSize: '13px' }}>
                        <input type="checkbox" checked={streamWindow} onChange={(e) => setStreamWindow(e.target.checked)} disabled={busy} />
                        Show preview window on this PC
                    </label>
                    <button onClick={startStream} disabled={busy || !streamUrl.trim()}
                        style={{ background: '#3b82f6', color: '#fff', border: 'none', borderRadius: '10px', padding: '10px 14px', fontWeight: 600, cursor: busy ? 'not-allowed' : 'pointer' }}>
                        Start Stream Detection
                    </button>
                </div>
            </div>

            <div style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: '18px', padding: '18px', marginBottom: '24px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '14px' }}>
                    <div style={{ fontWeight: 700 }}>Task List</div>
                    <div style={{ color: '#94a3b8', fontSize: '12px' }}>
                        Running: {tasks.filter((task) => task.running).length} / Total: {tasks.length}
                    </div>
                </div>
                <div style={{ display: 'grid', gap: '10px' }}>
                    {tasks.length === 0 ? (
                        <div style={{ color: '#94a3b8', fontSize: '13px' }}>No tasks yet. Start webcam or stream detection above.</div>
                    ) : tasks.map((task) => (
                        <div key={task.taskId}
                            onClick={() => {
                                chooseTask(task.taskId);
                                refresh().catch((error) => console.error(error));
                            }}
                            style={{
                                border: selectedTaskId === task.taskId ? '1px solid rgba(59,130,246,0.6)' : '1px solid rgba(148,163,184,0.25)',
                                background: selectedTaskId === task.taskId ? 'rgba(59,130,246,0.12)' : 'rgba(15,23,42,0.4)',
                                borderRadius: '10px',
                                padding: '10px',
                                cursor: 'pointer'
                            }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                                <div style={{ fontWeight: 700, fontSize: '13px' }}>{task.taskId}</div>
                                <span style={{ padding: '4px 8px', borderRadius: '999px', fontSize: '11px', fontWeight: 700, color: task.running ? '#10b981' : task.state === 'ERROR' ? '#f43f5e' : '#94a3b8', background: task.running ? 'rgba(16,185,129,0.12)' : 'rgba(148,163,184,0.12)' }}>
                                    {task.state}
                                </span>
                                <button
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        stopTask(task.taskId);
                                    }}
                                    disabled={!task.running || busy}
                                    style={{ marginLeft: 'auto', background: '#f43f5e', color: '#fff', border: 'none', borderRadius: '8px', padding: '6px 10px', fontSize: '12px', fontWeight: 600, cursor: !task.running || busy ? 'not-allowed' : 'pointer' }}
                                >
                                    Stop
                                </button>
                                <button
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        deleteTask(task.taskId);
                                    }}
                                    disabled={task.running || busy}
                                    style={{ background: 'rgba(148,163,184,0.2)', color: '#e2e8f0', border: '1px solid rgba(148,163,184,0.35)', borderRadius: '8px', padding: '6px 10px', fontSize: '12px', fontWeight: 600, cursor: task.running || busy ? 'not-allowed' : 'pointer' }}
                                >
                                    Delete
                                </button>
                            </div>
                            <div style={{ marginTop: '6px', color: '#cbd5e1', fontSize: '12px', lineHeight: 1.5 }}>
                                Mode: <strong>{task.mode}</strong> | Source: <strong>{task.source}</strong> | Location: <strong>{task.location}</strong> | Uptime: <strong>{task.uptimeSec || 0}s</strong>
                                {task.lastError ? <div style={{ color: '#fda4af' }}>Error: {task.lastError}</div> : null}
                            </div>
                        </div>
                    ))}
                </div>
            </div>

            <div style={{ background: 'rgba(2,6,23,0.7)', border: '1px solid rgba(148,163,184,0.2)', borderRadius: '12px', padding: '12px', minHeight: '180px', maxHeight: '360px', overflowY: 'auto', fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace', fontSize: '12px', lineHeight: 1.45, color: '#cbd5e1', whiteSpace: 'pre-wrap' }}>
                {selectedTaskId ? (logs.length > 0 ? logs.join('\n') : 'No logs for selected task yet.') : 'Select a task to view logs.'}
            </div>
        </div>
    );
};

export default DetectionTask;
