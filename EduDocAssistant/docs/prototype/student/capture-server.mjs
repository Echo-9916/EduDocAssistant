import { createReadStream } from "node:fs";
import { stat } from "node:fs/promises";
import { createServer } from "node:http";
import { extname, normalize, resolve, sep } from "node:path";

const root = resolve(process.cwd());
const port = Number(process.env.PORT || 4174);
const mime = {
  ".css": "text/css; charset=utf-8",
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".svg": "image/svg+xml",
};

createServer(async (request, response) => {
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
