package com.rv32i.exception;

/**
 * Represents a synchronous CPU exception (trap) such as an illegal instruction,
 * misaligned access, or ECALL.  These are converted into trap entries in the
 * CSR file rather than propagating as Java exceptions to user code — the catch
 * sites in RV32ICore call trap() and then return.
 */
public class CpuException extends RuntimeException {

    /** Standard RV32I exception cause codes (mcause values). */
    public enum Cause {
        INSTRUCTION_ADDRESS_MISALIGNED(0),
        INSTRUCTION_ACCESS_FAULT      (1),
        ILLEGAL_INSTRUCTION           (2),
        BREAKPOINT                    (3),
        LOAD_ADDRESS_MISALIGNED       (4),
        LOAD_ACCESS_FAULT             (5),
        STORE_ADDRESS_MISALIGNED      (6),
        STORE_ACCESS_FAULT            (7),
        ECALL                         (11);

        public final int code;
        Cause(int code) { this.code = code; }
    }

    private final Cause cause;
    private final int   trapPc;
    private final int   badAddress;

    public CpuException(Cause cause, int trapPc, String detail) {
        super(cause + " at PC=0x" + Integer.toHexString(trapPc) + ": " + detail);
        this.cause      = cause;
        this.trapPc     = trapPc;
        this.badAddress = 0;
    }

    public CpuException(Cause cause, int trapPc, int badAddress, String detail) {
        super(cause + " at PC=0x" + Integer.toHexString(trapPc)
              + " addr=0x" + Integer.toHexString(badAddress) + ": " + detail);
        this.cause      = cause;
        this.trapPc     = trapPc;
        this.badAddress = badAddress;
    }

    public Cause getExceptionCause() { return cause; }
    public int   getPC()         { return trapPc; }
    public int   getBadAddress() { return badAddress; }
}
