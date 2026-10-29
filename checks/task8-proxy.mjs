import http from "node:http";

const upstream = new URL(process.env.UPSTREAM_URL || "http://backend:8080");
const port = Number(process.env.PORT || 8081);

http
  .createServer((request, response) => {
    if (request.url === "/__health") {
      response.writeHead(200).end("ok");
      return;
    }
    const target = new URL(request.url, upstream);
    if (/^\/api\/tasks\/[0-9a-f-]{36}$/.test(target.pathname)) {
      console.log(`${request.method} ${target.pathname}`);
    }
    const forwarded = http.request(
      target,
      {
        method: request.method,
        headers: { ...request.headers, host: upstream.host },
      },
      (result) => {
        response.writeHead(result.statusCode, result.headers);
        result.pipe(response);
      },
    );
    forwarded.on("error", () => {
      if (!response.headersSent) response.writeHead(502);
      response.end();
    });
    request.on("aborted", () => forwarded.destroy());
    request.pipe(forwarded);
  })
  .listen(port, "0.0.0.0");
