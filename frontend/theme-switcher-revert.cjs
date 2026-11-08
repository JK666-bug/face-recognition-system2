const fs = require('fs');
const path = require('path');

const directoryInfo = path.join(__dirname, 'src');

const replacements = [
    { from: /color:\s*'#1e293b'/g, to: 'color: \'white\'' },
    { from: /color:\s*#1e293b;/g, to: 'color: white;' },
    { from: /#f1f5f9/gi, to: '#0f172a' },
    { from: /#ffffff/gi, to: 'TMP_ffffff' },
    { from: /#1e293b/gi, to: '#f8fafc' },
    { from: /TMP_ffffff/g, to: '#1e293b' },
    { from: /#e2e8f0/gi, to: '#334155' },
    { from: /#64748b/gi, to: '#94a3b8' },
    { from: /rgba\(0,\s*0,\s*0,\s*0\.02\)/g, to: 'rgba(255, 255, 255, 0.05)' },
    { from: /rgba\(0,\s*0,\s*0,\s*0\.08\)/g, to: 'rgba(255, 255, 255, 0.1)' },
    { from: /rgba\(0,\s*0,\s*0,\s*0\.12\)/g, to: 'rgba(255, 255, 255, 0.2)' },
    { from: /background:\s*'rgba\(255,\s*255,\s*255,\s*0\.8\)'/g, to: 'background: \'rgba(0, 0, 0, 0.8)\'' },
    { from: /backgroundColor:\s*'rgba\(255,\s*255,\s*255,\s*0\.8\)'/g, to: 'backgroundColor: \'rgba(0, 0, 0, 0.8)\'' }
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
        console.log('Restored', file);
    }
});
