package com.rv32i.peripheral;

/**
 * Memory-mapped timer peripheral — compare-match style, like SiFive's CLINT.
 *
 * Register map (base = 0x60000000):
 *   +0x00  MTIME_LO    (RO) — current tick count, low 32 bits
 *   +0x04  MTIME_HI    (RO) — current tick count, high 32 bits
 *   +0x08  MTIMECMP_LO (RW) — compare value, low 32 bits
 *   +0x0C  MTIMECMP_HI (RW) — compare value, high 32 bits
 *
 * An interrupt fires (and irqPending goes true) whenever mtime >= mtimecmp.
 * The ISR reschedules the next event by writing a new mtimecmp value.
 *
 * DemoPrograms.timerInterruptDemo() relies on this exact register layout.
 */
public class TimerPeripheral extends Peripheral {

    public static final int BASE = 0x60000000;
    private static final int SIZE = 0x100;

    private static final int REG_MTIME_LO    = 0x00;
    private static final int REG_MTIME_HI    = 0x04;
    private static final int REG_MTIMECMP_LO = 0x08;
    private static final int REG_MTIMECMP_HI = 0x0C;

    private long mtime    = 0;
    private long mtimecmp = Long.MAX_VALUE; // no interrupt until programmed

    public TimerPeripheral() {
        super(BASE, SIZE);
    }

    @Override
    public void tick(long cycle) {
        mtime++;
        if (Long.compareUnsigned(mtime, mtimecmp) >= 0) {
            irqPending = true;
        }
    }

    @Override
    public int readByte(int addr) {
        int reg = (addr - base) & ~3;
        int byteOffset = (addr - base) & 3;
        long word64 = switch (reg) {
            case REG_MTIME_LO    -> mtime    & 0xFFFFFFFFL;
            case REG_MTIME_HI    -> (mtime   >>> 32) & 0xFFFFFFFFL;
            case REG_MTIMECMP_LO -> mtimecmp & 0xFFFFFFFFL;
            case REG_MTIMECMP_HI -> (mtimecmp>>> 32) & 0xFFFFFFFFL;
            default              -> 0L;
        };
        return (int)((word64 >>> (byteOffset * 8)) & 0xFF);
    }

    @Override
    public void writeByte(int addr, int value) {
        int reg = (addr - base) & ~3;
        int byteOffset = (addr - base) & 3;
        switch (reg) {
            case REG_MTIMECMP_LO -> {
                long mask = 0xFFL << (byteOffset * 8);
                long newBits = ((long)(value & 0xFF)) << (byteOffset * 8);
                mtimecmp = (mtimecmp & ~mask) | newBits;
                // Re-evaluate: writing cmp clears the interrupt if now in the future
                if (Long.compareUnsigned(mtime, mtimecmp) < 0) irqPending = false;
            }
            case REG_MTIMECMP_HI -> {
                long mask = 0xFFL << (byteOffset * 8 + 32);
                long newBits = ((long)(value & 0xFF)) << (byteOffset * 8 + 32);
                mtimecmp = (mtimecmp & ~mask) | newBits;
                if (Long.compareUnsigned(mtime, mtimecmp) < 0) irqPending = false;
            }
            // mtime is read-only in this implementation
        }
    }

    public long getMtime()    { return mtime; }
    public long getMtimecmp() { return mtimecmp; }
}
