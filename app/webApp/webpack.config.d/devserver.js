// Force webpack dev server on :8081 so it doesn't collide with Ktor on :8080.
// /game WebSocket requests are proxied to the Ktor game server on :8080.
config.devServer = config.devServer || {};
config.devServer.port = 8081;
config.devServer.proxy = [
    {
        context: ["/game"],
        target: "http://localhost:8080",
        ws: true,
        changeOrigin: true,
    },
    {
        context: ["/api"],
        target: "http://localhost:8080",
        changeOrigin: true,
    },
];
