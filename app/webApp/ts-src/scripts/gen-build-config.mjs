import { writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const now = new Date();
const pad = (n) => String(n).padStart(2, "0");
const timestamp =
  `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}` +
  `-${pad(now.getHours())}.${pad(now.getMinutes())}.${pad(now.getSeconds())}`;

const outPath = fileURLToPath(new URL("../buildConfig.ts", import.meta.url));
writeFileSync(outPath, `// Auto-generated — do not edit\nexport const MC_BUILD_TIMESTAMP = "${timestamp}";\n`);
