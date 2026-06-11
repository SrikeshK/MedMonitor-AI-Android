package com.medmonitor.data.model

data class InventoryItem(
    val name: String,
    val type: String,
    val total: Double,
    val remaining: Double
)