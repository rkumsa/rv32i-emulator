package com.rv32i.cpu;

import com.rv32i.peripheral.GpioPeripheral;
import com.rv32i.peripheral.TimerPeripheral;
import com.rv32i.peripheral.UartPeripheral;

/**
 * Platform-Level Interrupt Controller (PLIC-lite).
 *
 * Polls each peripheral for a pending interrupt and returns the highest-
 * priority one.  In a real SoC this would be hardware; here we check each
 * peripheral's irqPending() flag.
 *
 * IRQ numbers (arbitrary — must match DemoPrograms expectations):
 *   IRQ_TIMER    = 7   (machine timer, matches RISC-V mie bit 7)
 *   IRQ_UART_RX  = 11  (external, matches mie bit 11)
 *   IRQ_GPIO     = 12
 */
public class InterruptController {

    public static final int IRQ_TIMER   = 7;
    public static final int IRQ_UART_RX = 11;
    public static final int IRQ_GPIO    = 12;

    private final TimerPeripheral timer;
    private final UartPeripheral  uart;
    private final GpioPeripheral  gpio;

    public InterruptController(TimerPeripheral timer, UartPeripheral uart, GpioPeripheral gpio) {
        this.timer = timer;
        this.uart  = uart;
        this.gpio  = gpio;
    }

    /**
     * Poll for the highest-priority pending interrupt.
     * @return IRQ number, or -1 if nothing pending.
     */
    public int poll() {
        if (timer.isIrqPending()) return IRQ_TIMER;
        if (uart.isIrqPending())  return IRQ_UART_RX;
        if (gpio.isIrqPending())  return IRQ_GPIO;
        return -1;
    }

    /**
     * Acknowledge (clear) an IRQ after the CPU has begun handling it.
     */
    public void acknowledge(int irq) {
        switch (irq) {
            case IRQ_TIMER   -> timer.clearIrq();
            case IRQ_UART_RX -> uart.clearIrq();
            case IRQ_GPIO    -> gpio.clearIrq();
        }
    }
}
