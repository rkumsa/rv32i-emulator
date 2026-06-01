package com.rv32i.trace;

import java.io.PrintStream;

/**
 * Circular ring-buffer execution tracer.
 *
 * Stores the last N instructions so you can do post-mortem debugging after a
 * fault.  When the buffer fills it wraps, keeping only the most recent entries.
 * This matches how real embedded debug tools (e.g. Cortex-M ETB) work.
 */
public class ExecutionTracer {

    private final TraceRecord[] buf;
    private int   head  = 0;  // next write position
    private long  total = 0;  // total records ever written
    private boolean enabled = true;

    public ExecutionTracer(int capacity) {
        this.buf = new TraceRecord[capacity];
    }

    /** Record one instruction.  Overwrites oldest entry when full. */
    public void record(TraceRecord r) {
        if (!enabled) return;
        buf[head] = r;
        head = (head + 1) % buf.length;
        total++;
    }

    /** Dump the trace to a stream, oldest entry first. */
    public void dump(PrintStream out) {
        long count = Math.min(total, buf.length);
        int  start = (int)((total >= buf.length) ? head : 0);
        out.println("=== Execution Trace (last " + count + " instructions) ===");
        for (long i = 0; i < count; i++) {
            TraceRecord r = buf[(int)((start + i) % buf.length)];
            if (r != null) out.println(r);
        }
    }

    public void    clear()             { head = 0; total = 0; }
    public boolean isEnabled()         { return enabled; }
    public void    setEnabled(boolean e){ this.enabled = e; }
    public long    getCount()          { return total; }
}
