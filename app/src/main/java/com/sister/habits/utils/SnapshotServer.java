package com.sister.habits.utils;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import fi.iki.elonen.NanoHTTPD;

/**
 * Snapshot reporting service (port 18082).
 *
 * GET /snapshot.png -> triggers a PixelCopy self-capture and returns the PNG
 * bytes. External tools can read the app's live UI (including on a virtual
 * display) via: adb forward tcp:18082 tcp:18082 + curl http://127.0.0.1:18082/snapshot.png
 */
public class SnapshotServer extends NanoHTTPD {

    public static final int PORT = 18082;

    public SnapshotServer(Context context) {
        super(PORT);
    }

    @Override
    public Response serve(IHTTPSession session) {
        if ("GET".equals(session.getMethod().name()) && "/snapshot.png".equals(session.getUri())) {
            return handleSnapshot();
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found");
    }

    private Response handleSnapshot() {
        File f = SnapshotHelper.capture();
        if (f == null || !f.exists() || f.length() == 0) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "no snapshot");
        }
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] data = new byte[(int) f.length()];
            int off = 0;
            while (off < data.length) {
                int r = fis.read(data, off, data.length - off);
                if (r < 0) break;
                off += r;
            }
            return newFixedLengthResponse(Response.Status.OK, "image/png",
                    new java.io.ByteArrayInputStream(data));
        } catch (IOException e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "io error");
        }
    }
}
