package com.cleantv.speed;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * A tiny HTTP/1.1 server with Cloudflare's two speed test endpoints, {@code GET /__down?bytes=N} and
 * {@code POST /__up}, for tests. Plain sockets, because Android unit tests can't see the JDK's own HTTP server.
 */
final class LocalSpeedServer implements AutoCloseable {
    private final ServerSocket socket;

    LocalSpeedServer() throws IOException {
        socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread acceptor = new Thread(this::accept, "local-speed-server");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    String url() {
        return "http://127.0.0.1:" + socket.getLocalPort() + "/";
    }

    private void accept() {
        while (!socket.isClosed()) {
            try {
                Socket client = socket.accept();
                Thread handler = new Thread(() -> serve(client), "local-speed-client");
                handler.setDaemon(true);
                handler.start();
            } catch (IOException e) {
                return;
            }
        }
    }

    /** Answers requests on one connection until the client hangs up (keep-alive). */
    private static void serve(Socket client) {
        try (Socket s = client) {
            InputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();
            byte[] buffer = new byte[64 * 1024];
            while (true) {
                String requestLine = readLine(in);
                if (requestLine == null || requestLine.isEmpty()) return;
                long contentLength = 0;
                String header;
                while ((header = readLine(in)) != null && !header.isEmpty()) {
                    int colon = header.indexOf(':');
                    if (colon > 0 && header.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                        contentLength = Long.parseLong(header.substring(colon + 1).trim());
                    }
                }
                String path = requestLine.split(" ")[1];
                if (requestLine.startsWith("POST") && path.startsWith("/__up")) {
                    for (long left = contentLength; left > 0; ) {
                        int n = in.read(buffer, 0, (int) Math.min(buffer.length, left));
                        if (n < 0) return;
                        left -= n;
                    }
                    out.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                } else if (path.startsWith("/__down?bytes=")) {
                    long bytes = Long.parseLong(path.substring("/__down?bytes=".length()));
                    out.write(("HTTP/1.1 200 OK\r\nContent-Length: " + bytes + "\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
                    java.util.Arrays.fill(buffer, (byte) 0);
                    for (long left = bytes; left > 0; left -= buffer.length) {
                        out.write(buffer, 0, (int) Math.min(buffer.length, left));
                    }
                } else {
                    out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                }
                out.flush();
            }
        } catch (IOException | RuntimeException e) {
            // The client hung up, usually at the end of a phase.
        }
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') return line.toString();
            if (c != '\r') line.append((char) c);
        }
        return line.length() == 0 ? null : line.toString();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
