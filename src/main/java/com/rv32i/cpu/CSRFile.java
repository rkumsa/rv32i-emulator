package com.rv32i.cpu;

import java.util.HashMap;
import java.util.Map;

/**
 * CSRFile — machine-mode Control and Status Registers.
 *
 * Implements the M-mode registers required for interrupt handling:
 *   mstatus  (0x300) — machine status (MIE bit)
 *   mie      (0x304) — machine interrupt enable
 *   mtvec    (0x305) — trap-handler base address
 *   mepc     (0x341) — machine exception PC
 *   mcause   (0x342) — trap cause
 *   mtval    (0x343) — bad address / bad instruction
 *   mip      (0x344) — machine interrupt pending
 *   mcycle   (0xC00) — cycle counter (low 32 bits)
 *   mcycleh  (0xC80) — cycle counter (high 32 bits)
 *
 * Interrupt cause codes (mcause bit 31 set = interrupt, clear = exception):
 *   Machine software interrupt : 3  (0x80000003)
 *   Machine timer interrupt    : 7  (0x80000007)
 *   Machine external interrupt : 11 (0x8000000B)
 */
public class CSRFile {

    // CSR addresses
    public static final int MSTATUS  = 0x300;
    public static final int MIE      = 0x304;
    public static final int MTVEC    = 0x305;
    public static final int MEPC     = 0x341;
    public static final int MCAUSE   = 0x342;
    public static final int MTVAL    = 0x343;
    public static final int MIP      = 0x344;
    public static final int MCYCLE   = 0xC00;
    public static final int MCYCLEH  = 0xC80;

    // mstatus bits
    public static final int MSTATUS_MIE  = 1 << 3;  // Machine Interrupt Enable
    public static final int MSTATUS_MPIE = 1 << 7;  // Previous MIE (saved on trap entry)

    // mie / mip bits
    public static final int MIE_MSIE = 1 << 3;   // software interrupt enable
    public static final int MIE_MTIE = 1 << 7;   // timer interrupt enable
    public static final int MIE_MEIE = 1 << 11;  // external interrupt enable

    // mcause values
    public static final int MCAUSE_MACHINE_TIMER_IRQ   = 0x80000007;
    public static final int MCAUSE_EXTERNAL_IRQ        = 0x8000000B;

    private final Map<Integer, Integer> regs = new HashMap<>();

    // Internal 64-bit cycle counter
    private long cycleCount = 0;

    public CSRFile() {
        // Initial state: interrupts disabled, no trap vector
        regs.put(MSTATUS, 0);
        regs.put(MIE,     0);
        regs.put(MTVEC,   0);
        regs.put(MEPC,    0);
        regs.put(MCAUSE,  0);
        regs.put(MTVAL,   0);
        regs.put(MIP,     0);
    }

    /**
     * Read a CSR.  Unknown CSRs return 0 (lenient — real hardware would trap).
     */
    public int read(int addr) {
        return switch (addr) {
            case MCYCLE  -> (int)(cycleCount & 0xFFFFFFFFL);
            case MCYCLEH -> (int)(cycleCount >>> 32);
            default      -> regs.getOrDefault(addr, 0);
        };
    }

    /**
     * Write a CSR.  Ignores writes to read-only counters.
     */
    public void write(int addr, int value) {
        switch (addr) {
            case MCYCLE, MCYCLEH -> { /* read-only in emulation */ }
            case MSTATUS -> regs.put(MSTATUS, value & 0x88);  // only MIE + MPIE bits writable
            default      -> regs.put(addr, value);
        }
    }

    // -------------------------------------------------------------------------
    // Convenience accessors used by the CPU core
    // -------------------------------------------------------------------------

    /** Returns true when MIE (global machine interrupt enable) is set in mstatus. */
    public boolean isMachineInterruptEnabled() {
        return (regs.getOrDefault(MSTATUS, 0) & MSTATUS_MIE) != 0;
    }

    public int getMtvec() { return regs.getOrDefault(MTVEC, 0); }
    public int getMepc()  { return regs.getOrDefault(MEPC,  0); }

    /**
     * Enter a trap: save PC, set mcause/mtval, disable interrupts (clear MIE,
     * save old MIE into MPIE).  Mirrors hardware behaviour exactly.
     */
    public void enterTrap(int pc, int cause, int tval) {
        int mstatus = regs.getOrDefault(MSTATUS, 0);
        // Save current MIE into MPIE, then clear MIE
        int mpie = (mstatus & MSTATUS_MIE) != 0 ? MSTATUS_MPIE : 0;
        regs.put(MSTATUS, (mstatus & ~(MSTATUS_MIE | MSTATUS_MPIE)) | mpie);

        regs.put(MEPC,   pc);
        regs.put(MCAUSE, cause);
        regs.put(MTVAL,  tval);
    }

    /**
     * MRET: restore PC from mepc, restore MIE from MPIE.
     * @return the address to jump back to
     */
    public int exitTrap() {
        int mstatus = regs.getOrDefault(MSTATUS, 0);
        // Restore MIE from MPIE, set MPIE=1 (per spec)
        boolean prevMie = (mstatus & MSTATUS_MPIE) != 0;
        int newMstatus = (mstatus & ~(MSTATUS_MIE | MSTATUS_MPIE))
                       | MSTATUS_MPIE
                       | (prevMie ? MSTATUS_MIE : 0);
        regs.put(MSTATUS, newMstatus);
        return regs.getOrDefault(MEPC, 0);
    }

    /** Called every cycle to advance the hardware performance counter. */
    public void incrementCycle() { cycleCount++; }

    /** Set a pending interrupt bit in mip. */
    public void setPending(int bit)   { regs.merge(MIP, bit, (a, b) -> a | b); }

    /** Clear a pending interrupt bit in mip. */
    public void clearPending(int bit) { regs.merge(MIP, ~bit, (a, b) -> a & b); }

    /** Check whether an interrupt is both enabled (mie) and pending (mip). */
    public boolean isInterruptPending(int bit) {
        int mie = regs.getOrDefault(MIE, 0);
        int mip = regs.getOrDefault(MIP, 0);
        return (mie & bit) != 0 && (mip & bit) != 0;
    }
}
