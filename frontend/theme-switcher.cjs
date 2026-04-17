const fs = require('fs');
const path = require('path');

const directoryInfo = 'd:/前后端开发/前端/vs code 代码/face-recognition-system/frontend/src';

const replacements = [
    { from: /#0f172a/gi, to: '#f1f5f9' },
    { from: /#1e293b/gi, to: '#ffffff' },
    { from: /#334155/gi, to: '#e2e8f0' },
    { from: /#f8fafc/gi, to: '#1e293b' },
    { from: /#94a3b8/gi, to: '#64748b' },
    { from: /rgba\(255,\s*255,\s*255,\s*0\.05\)/g, to: 'rgba(0, 0, 0, 0.02)' },
    { from: /rgba\(255,\s*255,\s*255,\s*0\.1\)/g, to: 'rgba(0, 0, 0, 0.08)' },
    { from: /rgba\(255,\s*255,\s*255,\s*0\.2\)/g, to: 'rgba(0, 0, 0, 0.12)' },
    { from: /color:\s*'white'/g, to: 'color: \'#1e293b\'' },
    { from: /color:\s*white;/g, to: 'color: #1e293b;' },
    { from: /background:\s*'rgba\(0, 0, 0, 0.8\)'/g, to: 'background: \'rgba(255, 255, 255, 0.8)\'' },
    { from: /backgroundColor:\s*'rgba\(0, 0, 0, 0.8\)'/g, to: 'backgroundColor: \'rgba(255, 255, 255, 0.8)\'' },
    { from: /background:\s*'#111827'/gi, to: 'background: \'#ffffff\'' }
];

function walk(dir) {
    let results = [];
    const list = fs.readdirSync(dir);
    list.forEach(function(file) {
        file = path.join(dir, file);
        const stat = fs.statSync(file);
        if (stat && stat.isDirectory()) { 
            results = results.concat(walk(file));
        } else { 
            if (file.endsWith('.js') || file.endsWith('.jsx') || file.endsWith('.css')) {
                results.push(file);
            }
        }
    });
    return results;
}

const files = walk(directoryInfo);

files.forEach(file => {
    let content = fs.readFileSync(file, 'utf8');
    let changed = false;
    replacements.forEach(r => {
        if (content.match(r.from)) {
            content = content.replace(r.from, r.to);
            changed = true;
        }
    });
    if (changed) {
        fs.writeFileSync(file, content, 'utf8');
        console.log('Updated', file);
    }
});
