package com.example.inventory.stock;

/**
 * Direction of a stock movement. The ledger stores a signed delta; this type records intent
 * and constrains the sign (IN positive, OUT negative, ADJUST either).
 */
public enum MovementType {
    IN,
    OUT,
    ADJUST
}
