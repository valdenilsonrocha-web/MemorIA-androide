package com.memoria.mobile.ui.theme

import androidx.compose.ui.graphics.Color

// MemórIA palette — the web app's `:root` in main.css, standardised on the blue of
// the new logo (commit "padroniza azul com a nova logomarca").
// Primary royal blue family
val BluePrimary = Color(0xFF0060F8)      // Web: #0060f8 (--primary-color)
val BluePrimaryDark = Color(0xFF003B9A)  // Web: #003b9a (--primary-dark)
val BluePrimaryLight = Color(0xFFDBEAFE) // Web: #dbeafe (--primary-light)
val BlueSecondary = Color(0xFF66A9FF)    // Web: #66a9ff (--secondary-color)

// Brand gradient stops used by the loading screen, the premium/upsell cards and
// the onboarding buttons: `linear-gradient(160deg, #0053d6, #0060f8, #2f7dff)`.
val BrandBlueDeep = Color(0xFF0053D6)
val BrandBlueBright = Color(0xFF2F7DFF)
val BrandBlueNavy = Color(0xFF003788)
val BrandBlueSoftBorder = Color(0xFF9AC6FF)

// Feedback & status colors matching web variables (--success-color, --danger-color, --warning-color)
val GreenOk = Color(0xFF10B981) // Web: #10b981 (--success-color)
val RedMiss = Color(0xFFEF4444)  // Web: #ef4444 (--danger-color)
val Snooze = Color(0xFFF59E0B)   // Web: #f59e0b (--warning-color)
val Amber = Color(0xFFF59E0B)    // Web: #f59e0b (--warning-color)

// Neutral surfaces matching web variables (--bg-secondary, --bg-primary)
val SurfaceLight = Color(0xFFF9FAFB) // Web: #f9fafb (--bg-secondary)
val SurfaceTertiary = Color(0xFFF3F4F6) // Web: #f3f4f6 (--bg-tertiary)
val SurfaceDark = Color(0xFF0F172A)  // Web: #0f172a (dark mode / slate-900)
val TextPrimary = Color(0xFF1F2937)  // Web: #1f2937 (--text-primary)
val TextSecondary = Color(0xFF6B7280)// Web: #6b7280 (--text-secondary)
val TextLight = Color(0xFF9CA3AF)    // Web: #9ca3af (--text-light)
val BorderLight = Color(0xFFE5E7EB)  // Web: #e5e7eb (--border-color)
val BorderSubtle = Color(0xFFF3F4F6) // Web: #f3f4f6 (--border-light)

// Backward compatibility aliases
val Teal = BluePrimary
val TealDark = BluePrimaryDark
val TealLight = BluePrimaryLight
