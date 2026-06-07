package dev.gvol.villagertrims.tooltrims;

import java.io.OutputStream;

final class OutputStreamSink extends OutputStream {
    static final OutputStreamSink INSTANCE = new OutputStreamSink();

    private OutputStreamSink() {
    }

    @Override
    public void write(int b) {
    }
}
