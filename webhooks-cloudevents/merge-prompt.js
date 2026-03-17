const fs = require('fs');
const path = require('path');

// Default to Java config if no argument provided
const configFile = process.argv[2] || 'prompt-config-java.json';
const configPath = path.join(__dirname, configFile);
const templatePath = path.join(__dirname, 'prompt-template.md');
const outputPath = path.join(__dirname, 'ready-prompt.md');

// Validate files exist
if (!fs.existsSync(configPath)) {
  console.error(`Config file not found: ${configPath}`);
  console.error('Usage: node merge-prompt.js [config-file.json]');
  process.exit(1);
}

if (!fs.existsSync(templatePath)) {
  console.error(`Template file not found: ${templatePath}`);
  process.exit(1);
}

// Load config and template
const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
let prompt = fs.readFileSync(templatePath, 'utf8');

// Track which variables were resolved and which were not
const resolved = [];
const unresolved = [];

// Replace all {{variables}} with config values
for (const [key, value] of Object.entries(config)) {
  const regex = new RegExp(`\\{\\{${key}\\}\\}`, 'g');
  const matches = prompt.match(regex);
  if (matches) {
    prompt = prompt.replace(regex, value);
    resolved.push({ key, count: matches.length });
  }
}

// Check for any remaining unresolved {{variables}}
const remaining = prompt.match(/\{\{[a-z_]+\}\}/g);
if (remaining) {
  const unique = [...new Set(remaining)];
  unique.forEach(v => unresolved.push(v));
}

// Write output
fs.writeFileSync(outputPath, prompt, 'utf8');

// Report
console.log(`\n✅ Generated: ${outputPath}`);
console.log(`   Config:    ${configFile}`);
console.log(`   Resolved:  ${resolved.length} variables (${resolved.reduce((sum, r) => sum + r.count, 0)} replacements)`);

if (unresolved.length > 0) {
  console.log(`\n⚠️  UNRESOLVED VARIABLES (${unresolved.length}):`);
  unresolved.forEach(v => console.log(`   - ${v}`));
  console.log('\n   Add these to your config file or the prompt will trigger a STOP at Task 0a.');
} else {
  console.log('   Unresolved: 0 — prompt is fully resolved and ready to use.');
}

console.log('');
