const { BannerPlugin } = require("webpack");
const pad = (n) => String(n).padStart(2, "0");
const d = new Date();
const ts = `${d.getUTCFullYear()}${pad(d.getUTCMonth() + 1)}${pad(d.getUTCDate())}-${pad(d.getUTCHours())}.${pad(d.getUTCMinutes())}.${pad(d.getUTCSeconds())}`;
config.plugins.push(new BannerPlugin({ banner: `console.log("[debug] build ${ts} (webApp)");`, raw: true }));
