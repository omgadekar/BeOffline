package com.beoffline.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Allowance — a temporary permission to USE an open-blocked app while its rule
 * stays active (distinct from TIMER rules, which block for a duration).
 *
 * Enforcement is evaluated per foreground event, so expiry needs no alarms:
 * once [grantedUntil] passes, the next open is blocked again. Expired rows are
 * lazily cleaned up.
 *
 * M2 source is always the solo teaser; M3 adds partner/group grants.
 */
@Entity(tableName = "allowances")
data class Allowance(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /** The open-blocked package this allowance unlocks. */
    val packageName: String,

    /** The rule this allowance was granted against. */
    val ruleId: Int,

    /** Epoch millis when this allowance expires. */
    val grantedUntil: Long,

    /** What granted it: TEASER (M2); PARTNER/GROUP arrive with M3. */
    val source: AllowanceSource,

    val createdAt: Long = System.currentTimeMillis()
)

enum class AllowanceSource {
    TEASER,
    PARTNER,
    GROUP
}
