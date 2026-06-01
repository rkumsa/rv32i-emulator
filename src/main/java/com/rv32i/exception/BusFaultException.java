package com.rv32i.exception;

/**
 * Thrown when the CPU attempts to read or write an address that has no
 * mapped device on the system bus.
 */
public class BusFaultException extends RuntimeException {

    private final int address;

    public BusFaultException(int address, String op) {
        super(String.format("Bus fault on %s at 0x%08X", op, address));
        this.address = address;
    }

    public int getAddress() { return address; }
}
