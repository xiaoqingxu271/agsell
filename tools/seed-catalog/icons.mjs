// Iconify SVG -> PNG 批量栅格化（Windows 无 cairo，cairosvg 不可用，改走 sharp 内置 librsvg）
// 用法：node tools/seed-catalog/icons.mjs <jobs.json>   jobs: [{svg, out, size}]
import { readFileSync } from "node:fs";
import sharp from "sharp";

const jobsFile = process.argv[2];
if (!jobsFile) {
  console.error("usage: node icons.mjs <jobs.json>");
  process.exit(2);
}
const jobs = JSON.parse(readFileSync(jobsFile, "utf8"));
let ok = 0;
const failed = [];
for (const j of jobs) {
  try {
    await sharp(j.svg, { density: 300 })
      .resize(j.size, j.size, { fit: "inside", background: { r: 255, g: 255, b: 255, alpha: 0 } })
      .png()
      .toFile(j.out);
    ok++;
  } catch (e) {
    failed.push(`${j.svg}: ${e.message}`);
  }
}
console.log(`[icons.mjs] 栅格化成功 ${ok}/${jobs.length}`);
if (failed.length) {
  console.error(failed.slice(0, 10).join("\n"));
  process.exit(1);
}
