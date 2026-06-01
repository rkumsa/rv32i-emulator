package com.rv32i.memory;

/**
 * Read-Only Memory — holds the program (text + rodata).
 *
 * Writes are silently ignored (as on real flash in read mode).
 * The loadBinary() method is used by the loader / assembler to
 * program the ROM before the CPU starts.
 */
public class ROM extends MemoryRegion {

    private final byte[] data;
    private int loadedBytes = 0;

    public ROM(int base, int size) {
        super(base, size);
        this.data = new byte[size];
    }

    /** Load a raw binary blob into ROM starting at offset 0. */
    public void loadBinary(byte[] binary) {
        if (binary.length > size)
            throw new IllegalArgumentException(
                    "Binary (" + binary.length + " B) exceeds ROM size (" + size + " B)");
        System.arraycopy(binary, 0, data, 0, binary.length);
        loadedBytes = binary.length;
    }

    /** Load at a specific byte offset within the ROM. */
    public void loadAt(int offset, byte[] binary) {
        if (offset < 0 || offset + binary.length > size)
            throw new IllegalArgumentException("Binary does not fit at offset 0x"
                    + Integer.toHexString(offset));
        System.arraycopy(binary, 0, data, offset, binary.length);
        loadedBytes = Math.max(loadedBytes, offset + binary.length);
    }

    @Override
    public int readByte(int addr) {
        return data[addr - base] & 0xFF;
    }

    /** ROM is not writable — silently ignore. */
    @Override
    public void writeByte(int addr, int value) {
        // Intentional no-op: write to ROM is ignored (as on real flash)
    }

    public int getLoadedBytes() { return loadedBytes; }
}
