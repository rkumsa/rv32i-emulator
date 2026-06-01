package com.rv32i.trace;

/**
 * A single instruction trace entry.  Captures enough context to reconstruct
 * exactly what happened at each step — useful for post-mortem debugging.
 */
public record TraceRecord(
        long   cycle,
        int    pc,
        int    rawInstr,
        int    rd,
        int    rdValue,
        int    rs1,
        int    rs1Value,
        int    rs2,
        int    rs2Value,
        String mnemonic
) {
    @Override
    public String toString() {
        return String.format("[%6d] PC=%08X  %-6s  rd=x%-2d(%08X)  rs1=x%-2d(%08X)  rs2=x%-2d(%08X)  raw=%08X",
                cycle, pc, mnemonic,
                rd, rdValue,
                rs1, rs1Value,
                rs2, rs2Value,
                rawInstr);
    }
}
