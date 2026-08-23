package com.example.watchorderengine.util

import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.Modifier

/**
 * Enforces Android's recommended 48dp x 48dp minimum touch target (Material Design
 * Accessibility Guidelines / WCAG 2.5.5 "Target Size") for a small interactive element
 * *without* stretching what's actually drawn.
 *
 * This is a thin, intention-revealing wrapper around Material3's own
 * [minimumInteractiveComponentSize], which reserves invisible extra space around content
 * that measures smaller than 48dp rather than growing the content itself — so a small
 * pill/chip/icon keeps its exact visual size while still getting a comfortably tappable
 * hit area. Apply directly to the element carrying the click/selection modifier (e.g.
 * right after `.clickable(...)` / `.selectable(...)` / `.toggleable(...)`), before any
 * further `.size(...)` modifiers.
 */
fun Modifier.minTouchTarget(): Modifier = this.minimumInteractiveComponentSize()
