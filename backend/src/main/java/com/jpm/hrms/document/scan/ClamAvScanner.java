package com.jpm.hrms.document.scan;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Scans with a ClamAV daemon (clamd, free and open source) using its INSTREAM command over TCP.
 * Run clamd next to the backend (e.g. the official clamav/clamav Docker image) and set
 * MALWARE_SCAN_MODE=clamav, CLAMAV_HOST and CLAMAV_PORT.
 */
public class ClamAvScanner implements MalwareScanner {
    private static final int CHUNK = 64 * 1024;
    private final String host;
    private final int port;
    private final int timeoutMs;

    public ClamAvScanner(String host, int port, int timeoutMs) {
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public Verdict scan(byte[] content) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            for (int off = 0; off < content.length; off += CHUNK) {
                int len = Math.min(CHUNK, content.length - off);
                out.writeInt(len);
                out.write(content, off, len);
            }
            out.writeInt(0);
            out.flush();
            String reply = readReply(socket.getInputStream());
            // e.g. "stream: OK", "stream: Win.Test.EICAR_HDB-1 FOUND", "INSTREAM size limit exceeded. ERROR"
            if (reply.endsWith("OK")) return new Verdict(Outcome.CLEAN, null);
            if (reply.endsWith("FOUND")) {
                String sig = reply.substring(reply.indexOf(':') + 1, reply.length() - "FOUND".length()).trim();
                return new Verdict(Outcome.INFECTED, sig);
            }
            return new Verdict(Outcome.ERROR, "Scanner replied: " + reply);
        } catch (IOException e) {
            return new Verdict(Outcome.ERROR, "Malware scanner unreachable (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static String readReply(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != 0) buf.write(b);
        return buf.toString(StandardCharsets.US_ASCII).trim();
    }
}
