package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val category: String,
    val dateMillis: Long = System.currentTimeMillis(),
    val isExpense: Boolean = true, // false for Income
    val note: String = ""
)
