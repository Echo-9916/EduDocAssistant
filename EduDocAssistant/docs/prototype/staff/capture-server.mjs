import { createReadStream } from "node:fs";
import { mkdir, stat, writeFile } from "node:fs/promises";
import { createServer } from "node:http";
import { extname, join, normalize, resolve, sep } from "node:path";

const root = resolve(process.cwd());
const port = Number(process.argv[2] || process.env.PORT || 4174);
const reportFile = join(root, "figma-staff-v3-report.json");
const inspectionFile = join(root, "figma-staff-v3-inspection.json");
const migrationFile = join(root, "figma-staff-v3-migration.json");
const validationFile = join(root, "figma-staff-v3-validation.json");
const studentViewFile = join(root, "figma-student-view.json");
const studentIconAuditFile = join(root, "figma-student-icon-text-audit.json");
const snapshotDirectory = join(root, "figma-snapshots");
const writeOrigins = new Set([
  "null",
  "http://127.0.0.1:4180",
  "http://localhost:4180",
  "https://www.figma.com",
]);

function writeAllowed(request) {
  const origin = request.headers.origin;
  return !origin || writeOrigins.has(origin);
}

function writeHeaders(request) {
  return {
    "Access-Control-Allow-Origin": request.headers.origin || "null",
    "Access-Control-Allow-Headers": "Content-Type",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
  };
}
const mime = {
  ".css": "text/css; charset=utf-8",
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".svg": "image/svg+xml",
};

createServer(async (request, response) => {
  if (request.method === "POST" && !writeAllowed(request)) {
    response.writeHead(403, { "Content-Type": "text/plain; charset=utf-8" });
    response.end("Forbidden origin");
    return;
  }

  if (request.method === "POST" && request.url === "/__native") {
    try {
      const chunks = [];
      for await (const chunk of request) chunks.push(chunk);
      const report = JSON.parse(Buffer.concat(chunks).toString("utf8"));
      const nativeImportFile = join(
        root,
        report.role === "student"
          ? "figma-native-student.json"
          : report.role === "operations"
            ? "figma-native-operations.json"
            : "figma-native-teacher.json",
      );
      await writeFile(nativeImportFile, `${JSON.stringify(report, null, 2)}\n`, "utf8");
      response.writeHead(204, writeHeaders(request));
      response.end();
    } catch (error) {
      response.writeHead(400, writeHeaders(request));
      response.end(String(error));
    }
    return;
  }

  if (request.method === "POST" && request.url === "/__student-view") {
    try {
      const chunks = [];
      for await (const chunk of request) chunks.push(chunk);
      const report = JSON.parse(Buffer.concat(chunks).toString("utf8"));
      await writeFile(studentViewFile, `${JSON.stringify(report, null, 2)}\n`, "utf8");
      response.writeHead(204, writeHeaders(request));
      response.end();
    } catch (error) {
      response.writeHead(400, writeHeaders(request));
      response.end(String(error));
    }
    return;
  }

  if (request.method === "POST" && request.url === "/__student-icon-audit") {
    try {
      const chunks = [];
      for await (const chunk of request) chunks.push(chunk);
      const report = JSON.parse(Buffer.concat(chunks).toString("utf8"));
      await writeFile(studentIconAuditFile, `${JSON.stringify(report, null, 2)}\n`, "utf8");
      response.writeHead(204, writeHeaders(request));
      response.end();
    } catch (error) {
      response.writeHead(400, writeHeaders(request));
      response.end(String(error));
    }
    return;
  }

  if (request.method === "POST" && request.url === "/__validation") {
    try {
      const chunks = [];
      for await (const chunk of request) chunks.push(chunk);
      const report = JSON.parse(Buffer.concat(chunks).toString("utf8"));
      await writeFile(validationFile, `${JSON.stringify(report, null, 2)}\n`, "utf8");
      response.writeHead(204, writeHeaders(request));
      response.end();
    } catch (error) {
      response.writeHead(400, writeHeaders(request));
      response.end(String(error));
    }
    return;
  }

  if (request.method === "POST" && request.url === "/__snapshot") {
    try {
      const chunks = [];
      for await (const chunk of request) chunks.push(chunk);
      const snapshot = JSON.parse(Buffer.concat(chunks).toString("utf8"));
      const fileName = String(snapshot.name || "").replace(/[^a-zA-Z0-9._-]/g, "");
      if (!fileName.endsWith(".png") || fileName !== snapshot.name) {
        throw new Error("Invalid snapshot file name");
      }
      await mkdir(snapshotDirectory, { recursive: true });
      await writeFile(join(snapshotDirectory, fileName), Buffer.from(snapshot.base64, "base64"));
      response.writeHead(204, writeHeaders(request));
      response.end();
    } catch (error) {
      response.writeHead(400, writeHeaders(request));
      response.end(String(error));
    }
    return;
  }

  if (request.method === "POST" && request.url === "/__migration") {
    try {
      const chunks = [];
      for await (const chunk of request) chunks.push(chunk);
      const report = JSON.parse(Buffer.concat(chunks).toString("utf8"));
      await writeFile(migrationFile, `${JSON.stringify(report, null, 2)}\n`, "utf8");
      response.writeHead(204, writeHeaders(request));
      response.end();
    } catch (error) {
      response.writeHead(400, writeHeaders(request));
      response.end(String(error));
    }
    return;
  }

  if (request.method === "POST" && request.url === "/__inspect") {
    try {
      const chunks = [];
      for await (const chunk of request) chunks.push(chunk);
      const report = JSON.parse(Buffer.concat(chunks).toString("utf8"));
      await writeFile(inspectionFile, `${JSON.stringify(report, null, 2)}\n`, "utf8");
      response.writeHead(204, writeHeaders(request));
      response.end();
    } catch (error) {
      response.writeHead(400, writeHeaders(request));
      response.end(String(error));
    }
    return;
  }

  if (request.method === "POST" && request.url === "/__report") {
    try {
      const chunks = [];
      for await (const chunk of request) chunks.push(chunk);
      const report = JSON.parse(Buffer.concat(chunks).toString("utf8"));
      await writeFile(reportFile, `${JSON.stringify(report, null, 2)}\n`, "utf8");
      response.writeHead(204, writeHeaders(request));
      response.end();
    } catch (error) {
      response.writeHead(400, writeHeaders(request));
      response.end(String(error));
    }
    return;
  }

  if (request.method === "OPTIONS") {
    if (!writeAllowed(request)) {
      response.writeHead(403, { "Content-Type": "text/plain; charset=utf-8" });
      response.end("Forbidden origin");
      return;
    }
    response.writeHead(204, writeHeaders(request));
    response.end();
    return;
  }

  try {
    const pathname = decodeURIComponent(
      new URL(request.url, `http://${request.headers.host}`).pathname,
    );
    if (pathname.includes("\0")) throw new Error("invalid path");
    const relative = normalize(pathname === "/" ? "/index.html" : pathname).replace(
      /^[/\\]+/,
      "",
    );
    const file = resolve(root, relative);
    if (file !== root && !file.startsWith(`${root}${sep}`)) {
      response.writeHead(403, { "Content-Type": "text/plain; charset=utf-8" });
      response.end("Forbidden");
      return;
    }
    const info = await stat(file);
    if (!info.isFile()) throw new Error("not a file");
    response.writeHead(200, {
      "Access-Control-Allow-Origin": "*",
      "Cache-Control": "no-store",
      "Content-Type": mime[extname(file)] || "application/octet-stream",
      "Content-Length": info.size,
    });
    createReadStream(file).pipe(response);
  } catch {
    response.writeHead(404, { "Access-Control-Allow-Origin": "*" });
    response.end("Not found");
  }
}).listen(port, "127.0.0.1", () => {
  console.log(`CORS preview server listening on http://127.0.0.1:${port}`);
});
