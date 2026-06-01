package com.rv32i.peripheral;

import com.rv32i.memory.MemoryRegion;

/**
 * Base class for memory-mapped I/O peripherals.
 *
 * Each peripheral occupies a contiguous range of the address space.
 * The tick() method is called once per CPU cycle so peripherals can
 * model timers, FIFO drains, etc.
 */
public abstract class Peripheral extends MemoryRegion {

    protected boolean irqPending = false;

    protected Peripheral(int base, int size) {
        super(base, size);
    }

    /**
     * Called every CPU cycle.  Peripherals use this to advance internal state
     * (timer counts, UART shift registers, etc.).
     */
    public void tick(long cycle) { /* default: no-op */ }

    public boolean isIrqPending() { return irqPending; }
    public void    clearIrq()     { irqPending = false; }
}
