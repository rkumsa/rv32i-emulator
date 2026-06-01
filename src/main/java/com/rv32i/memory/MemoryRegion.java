package com.rv32i.memory;

/**
 * Base class for anything that lives on the system bus — ROM, RAM, or MMIO.
 */
public abstract class MemoryRegion {

    protected final int base;
    protected final int size;

    protected MemoryRegion(int base, int size) {
        this.base = base;
        this.size = size;
    }

    public int  getBase() { return base; }
    public int  getSize() { return size; }

    /** Returns true if the given byte address falls within this region. */
    public boolean contains(int addr) {
        return Integer.compareUnsigned(addr - base, size) < 0;
    }

    // Byte-level access — all wider accesses are built from these in subclasses
    public abstract int  readByte(int addr);
    public abstract void writeByte(int addr, int value);
}
