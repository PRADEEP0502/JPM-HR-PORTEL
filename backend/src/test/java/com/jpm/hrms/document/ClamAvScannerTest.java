package com.jpm.hrms.document;

import com.jpm.hrms.document.scan.ClamAvScanner;
import com.jpm.hrms.document.scan.MalwareScanner.Outcome;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Talks the clamd INSTREAM protocol to a small fake daemon. */
class ClamAvScannerTest {

    @Test
    void cleanInfectedAndUnreachable() throws Exception {
        AtomicReference<byte[]> received = new AtomicReference<>();
        try (ServerSocket server = fakeClamd("stream: OK", received)) {
            var v = new ClamAvScanner("127.0.0.1", server.getLocalPort(), 3000).scan("%PDF-1.4 hello".getBytes(StandardCharsets.US_ASCII));
            assertThat(v.outcome()).isEqualTo(Outcome.CLEAN);
            assertThat(new String(received.get(), StandardCharsets.US_ASCII)).isEqualTo("%PDF-1.4 hello");
        }
        try (ServerSocket server = fakeClamd("stream: Win.Test.EICAR_HDB-1 FOUND", received)) {
            var v = new ClamAvScanner("127.0.0.1", server.getLocalPort(), 3000).scan(new byte[200_000]);
            assertThat(v.outcome()).isEqualTo(Outcome.INFECTED);
            assertThat(v.detail()).isEqualTo("Win.Test.EICAR_HDB-1");
            assertThat(received.get()).hasSize(200_000); // sent in several chunks, reassembled
        }
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        assertThat(new ClamAvScanner("127.0.0.1", closedPort, 1000).scan(new byte[10]).outcome()).isEqualTo(Outcome.ERROR);
    }

    /** Accepts one connection, reads the INSTREAM command and chunks, answers with {@code reply}. */
    private static ServerSocket fakeClamd(String reply, AtomicReference<byte[]> received) throws IOException {
        ServerSocket server = new ServerSocket(0);
        Thread t = new Thread(() -> {
            try (Socket s = server.accept()) {
                DataInputStream in = new DataInputStream(s.getInputStream());
                byte[] cmd = new byte["zINSTREAM\0".length()];
                in.readFully(cmd);
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                for (int len = in.readInt(); len > 0; len = in.readInt()) {
                    byte[] chunk = new byte[len];
                    in.readFully(chunk);
                    data.write(chunk);
                }
                received.set(data.toByteArray());
                s.getOutputStream().write((reply + "\0").getBytes(StandardCharsets.US_ASCII));
            } catch (IOException ignored) {
                // test ends
            }
        });
        t.setDaemon(true);
        t.start();
        return server;
    }
}
