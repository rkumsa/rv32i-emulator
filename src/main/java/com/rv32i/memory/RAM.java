package com.rv32i.memory;

/**
 * Random-Access Memory — stack, heap, and data live here.
 *
 * Also tracks stack usage for the emulator diagnostics panel.
 * The stack grows downward from RAM_BASE+RAM_SIZE; we record
 * the lowest SP value seen to estimate high-water-mark usage.
 */
public class RAM extends MemoryRegion {

    private final byte[] data;
    private int lowestSp;
    private boolean stackOverflow = false;

    public RAM(int base, int size) {
        super(base, size);
        this.data    = new byte[size];
        this.lowestSp = base + size; // starts at top
    }

    @Override
    public int readByte(int addr) {
        return data[addr - base] & 0xFF;
    }

    @Override
    public void writeByte(int addr, int value) {
        int offset = addr - base;
        data[offset] = (byte)(value & 0xFF);
    }

    /** Bulk-load initialized data (used by ELF loader for .data section). */
    public void load(int vaddr, byte[] bytes) {
        System.arraycopy(bytes, 0, data, vaddr - base, bytes.length);
    }

    /** Zero-fill all of RAM (reset). */
    public void zero() {
        java.util.Arrays.fill(data, (byte) 0);
        lowestSp = base + size;
        stackOverflow = false;
    }

    /**
     * Called by SystemBus on every SP write so we can track stack depth.
     * SP = x2 in RISC-V ABI.
     */
    public void observeSp(int sp) {
        if (Integer.compareUnsigned(sp, base) < 0
                || Integer.compareUnsigned(sp, base + size) >= 0) {
            stackOverflow = true;
        } else if (Integer.compareUnsigned(sp, lowestSp) < 0) {
            lowestSp = sp;
        }
    }

    /** Stack usage in bytes (high-water mark from top of RAM). */
    public int getStackUsage() {
        return (base + size) - lowestSp;
    }

    public boolean isStackOverflowDetected() { return stackOverflow; }
}
