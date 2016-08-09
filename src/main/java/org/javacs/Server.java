package org.javacs;

import io.typefox.lsapi.services.json.LanguageServerToJsonAdapter;

import java.io.IOException;
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
                final int port = client.getPort();
                LOG.info("New connection at " + port);
                final Connection connection = new Connection(client);
                Thread t = new Thread(() -> {
                    try {
                        connection.run();
                    } catch (IOException e) {
                        // ignore
                    } finally {
                        LOG.info("Closed connection at " + port);
                    }
                });
                t.setName("Connector " + port);
                t.setDaemon(true);
                t.start();
            }
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, t.getMessage(), t);

            System.exit(1);
        }
    }

    private static class Connection implements ShutdownHandler {

        private final Socket socket;
        private final JavaLanguageServer server;
        private final LanguageServerToJsonAdapter jsonServer;

        private Connection(Socket socket) {
            this.socket = socket;
            this.server = new JavaLanguageServer();
            this.jsonServer = new LanguageServerToJsonAdapter(this.server);
        }

        private void run() throws IOException {
            jsonServer.connect(socket.getInputStream(), socket.getOutputStream());
            jsonServer.getProtocol().addErrorListener((message, err) -> {
                if (err instanceof IOException) {
                    this.shutdown(server);
                } else {
                    LOG.log(Level.SEVERE, message, err);
                }
            });

            try {
                jsonServer.join();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            } finally {
                this.socket.close();
            }
        }

        @Override
        public void shutdown(JavaLanguageServer server) {
            jsonServer.getProtocol().getIoHandler().stop();
        }
    }

}
