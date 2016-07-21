package org.javacs;

import io.typefox.lsapi.services.json.LanguageServerToJsonAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server {
    private static final Logger LOG = Logger.getLogger("main");

    public static void main(String[] args) throws IOException {
        try {
            LoggingFormat.startLogging();
            ServerSocket socket = new ServerSocket(Integer.parseInt(System.getProperty("javacs.port")));
            Socket client;
            while ((client = socket.accept()) != null) {
                LOG.info("New connection at " + client.getPort());
                final Connection connection = new Connection(client);
                Thread t = new Thread(() -> {
                    run(connection);
                });
                t.setName("Connector " + client.getPort());
                t.setDaemon(true);
                t.start();
            }
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, t.getMessage(), t);

            System.exit(1);
        }
    }

    private static class Connection {
        final InputStream in;
        final OutputStream out;
        final Socket socket;

        private Connection(Socket socket) throws IOException {
            this.socket = socket;
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
        }
    }

    /**
     * Listen for requests
     * Send replies asynchronously.
     * When the request stream is closed, wait for 5s for all outstanding responses to compute, then return.
     */
    static void run(Connection connection) {
        JavaLanguageServer server = new JavaLanguageServer();
        LanguageServerToJsonAdapter jsonServer = new LanguageServerToJsonAdapter(server);

        jsonServer.connect(connection.in, connection.out);
        jsonServer.getProtocol().addErrorListener((message, err) -> {
            LOG.log(Level.SEVERE, message, err);
            if (err instanceof IOException) {
                server.shutdown();
                try {
                    connection.socket.close();
                } catch (IOException ignore) {
                    // NOOP
                }
            }
            //server.onError(message, err);
        });
        
        try {
            jsonServer.join();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
