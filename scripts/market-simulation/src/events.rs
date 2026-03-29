//! Market Event System — price velocity modifiers for the simulation.
//!
//! Mirrors the Java `MarketEventService` and `MarketEvent` classes.
//! Events amplify or dampen price changes for matching items, making the
//! simulated economy feel dynamic without overriding natural market forces.
//!
//! Event effects are applied to `price_change_percent` in the price update pipeline,
//! after trend dampening but before the price change is applied.

use serde::{Deserialize, Serialize};

/// Market event types — each has distinct price velocity behavior.
#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
pub enum EventType {
    /// Buy prices boosted — amplifies upward price moves, suppresses downward.
    DemandSurge,
    /// Sell prices boosted — amplifies downward moves, suppresses upward.
    SupplyGlut,
    /// Persistent upward drift — always adds upward pressure.
    InflationBoost,
    /// Persistent downward drift — always adds downward pressure.
    DeflationDrop,
    /// Symmetric amplification — amplify whatever direction the market is moving.
    GoldRush,
    /// Custom — admin sets arbitrary multiplier (treated symmetrically).
    Custom,
}

impl EventType {
    pub fn from_str(s: &str) -> Self {
        match s.to_uppercase().as_str() {
            "DEMAND_SURGE" => Self::DemandSurge,
            "SUPPLY_GLUT" => Self::SupplyGlut,
            "INFLATION_BOOST" => Self::InflationBoost,
            "DEFLATION_DROP" => Self::DeflationDrop,
            "GOLD_RUSH" => Self::GoldRush,
            _ => Self::Custom,
        }
    }

    pub fn as_str(&self) -> &'static str {
        match self {
            Self::DemandSurge => "DEMAND_SURGE",
            Self::SupplyGlut => "SUPPLY_GLUT",
            Self::InflationBoost => "INFLATION_BOOST",
            Self::DeflationDrop => "DEFLATION_DROP",
            Self::GoldRush => "GOLD_RUSH",
            Self::Custom => "CUSTOM",
        }
    }
}

/// A market event that modifies price velocity for matching items.
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct MarketEvent {
    /// Human-readable name.
    pub name: String,
    /// Event type — determines direction of price effect.
    pub event_type: EventType,
    /// Material name patterns (exact, "PREFIX_*", "*_SUFFIX", or "*MIDDLE*").
    pub materials: Vec<String>,
    /// Price change multiplier (e.g., 2.0 = 2× price velocity).
    pub multiplier: f64,
    /// Tick at which the event starts (inclusive).
    pub starts_at_tick: u64,
    /// Tick at which the event ends (exclusive).
    pub ends_at_tick: u64,
}

impl MarketEvent {
    /// Returns true if this event is active at the given tick.
    pub fn is_active(&self, tick: u64) -> bool {
        tick >= self.starts_at_tick && tick < self.ends_at_tick
    }

    /// Returns true if the given material name matches any of this event's patterns.
    pub fn matches_material(&self, material: &str) -> bool {
        for pattern in &self.materials {
            if pattern_eq(material, pattern) {
                return true;
            }
        }
        false
    }

    /// Computes the additional price-change-percentage effect from this event.
    ///
    /// The effect is additive — it adds to (or subtracts from) the existing
    /// `price_change_percent` that the natural market forces would produce.
    ///
    /// **DEMAND_SURGE**: amplifies upward (buy pressure): result = extra + suppressed.
    ///   Rising (+5%): extra=+5%, suppressed=-2.5% → net +7.5% (amplified)
    ///   Falling (-5%): extra=-5%, suppressed=+2.5% → net -2.5% (less falling)
    /// **SUPPLY_GLUT**: amplifies downward (sell pressure): result = extra + suppressed.
    ///   Falling (-5%): extra=-5%, suppressed=+2.5% → net -7.5% (amplified)
    ///   Rising (+5%): extra=+5%, suppressed=-2.5% → net +2.5% (less rising)
    /// **INFLATION_BOOST**: always adds upward drift. Rising: extra + suppressed.
    ///   Falling: suppressed only (flip sign of natural fall).
    /// **DEFLATION_DROP**: always adds downward drift. Falling: extra + suppressed.
    ///   Rising: suppressed only (flip sign of natural rise).
    /// **GOLD_RUSH / CUSTOM**: symmetric amplification: result = extra only.
    pub fn compute_effect(&self, price_change_percent: f64) -> f64 {
        let extra = price_change_percent * (self.multiplier - 1.0);
        // suppressed: dampening force toward zero when direction opposes the event type.
        // Java formula: -priceChangePercent * (1 - 1/mult)
        let suppressed = -price_change_percent * (1.0 - 1.0 / self.multiplier);

        match self.event_type {
            EventType::DemandSurge => {
                if price_change_percent >= 0.0 {
                    extra
                } else {
                    suppressed
                }
            }
            EventType::SupplyGlut => {
                if price_change_percent <= 0.0 {
                    extra
                } else {
                    suppressed
                }
            }
            EventType::InflationBoost => {
                // Always add upward drift: abs(pcp) * (mult - 1)
                price_change_percent.abs() * (self.multiplier - 1.0)
            }
            EventType::DeflationDrop => {
                // Always add downward drift: -abs(pcp) * (mult - 1)
                -price_change_percent.abs() * (self.multiplier - 1.0)
            }
            EventType::GoldRush | EventType::Custom => extra,
        }
    }
}

/// Returns true if `material` matches the given `pattern`.
///
/// Supports three wildcard forms:
/// - Exact: `"DIAMOND"`
/// - Prefix wildcard: `"GOLD_*"` → matches `"GOLD_INGOT"`, `"GOLD_NUGGET"`
/// - Suffix wildcard: `"*_INGOT"` → matches `"GOLD_INGOT"`, `"IRON_INGOT"`
/// - Contains: `"*RA*"` → matches `"DIAMOND"`, `"GRASS_BLOCK"`
fn pattern_eq(material: &str, pattern: &str) -> bool {
    let m = material.to_uppercase();
    let p = pattern.to_uppercase();

    if m == p {
        return true;
    }
    if p.ends_with("_*")
        && let Some(prefix) = p.strip_suffix("_*")
    {
        let prefix_upper = prefix.to_uppercase();
        return m.starts_with(&prefix_upper) && m.chars().nth(prefix_upper.len()) == Some('_');
    }
    if p.starts_with("*_")
        && let Some(suffix) = p.strip_prefix("*_")
    {
        return m.ends_with(&("_".to_string() + suffix));
    }
    if p.starts_with('*') && p.ends_with('*') && p.len() >= 2 {
        let mid = &p[1..p.len() - 1];
        return m.contains(mid);
    }
    false
}

/// Apply all active market event effects to a price change percentage.
///
/// `events` — active market events for this simulation tick.
/// `material` — the item material being priced.
/// `price_change_percent` — the natural price change from market forces (before events).
///
/// Returns the adjusted price change percentage after all matching event effects.
pub fn apply_event_multiplier(
    events: &[MarketEvent],
    material: &str,
    price_change_percent: f64,
) -> f64 {
    let mut result = price_change_percent;
    for event in events {
        if event.matches_material(material) {
            result += event.compute_effect(price_change_percent);
        }
    }
    result
}

#[cfg(test)]
mod tests {
    use super::*;

    fn diamond_demand_surge() -> MarketEvent {
        MarketEvent {
            name: "Diamond Demand Surge".into(),
            event_type: EventType::DemandSurge,
            materials: vec!["DIAMOND".into()],
            multiplier: 2.0,
            starts_at_tick: 0,
            ends_at_tick: 1000,
        }
    }

    fn gold_supply_glut() -> MarketEvent {
        MarketEvent {
            name: "Gold Supply Glut".into(),
            event_type: EventType::SupplyGlut,
            materials: vec!["GOLD_*".into()],
            multiplier: 2.0,
            starts_at_tick: 0,
            ends_at_tick: 1000,
        }
    }

    #[test]
    fn test_demand_surge_amplifies_rising() {
        let ev = diamond_demand_surge();
        // Java: DEMAND_SURGE rising returns extra = pcp * (mult-1) = 0.05
        let effect = ev.compute_effect(0.05);
        assert!((effect - 0.05).abs() < 1e-10);
    }

    #[test]
    fn test_demand_surge_suppresses_falling() {
        let ev = diamond_demand_surge();
        // Java: DEMAND_SURGE falling returns suppressed = +2.5% (push toward zero)
        // This pushes against the natural fall, reducing its magnitude.
        let effect = ev.compute_effect(-0.05);
        assert!((effect - 0.025).abs() < 1e-10);
    }

    #[test]
    fn test_supply_glut_amplifies_falling() {
        let ev = gold_supply_glut();
        // Java: SUPPLY_GLUT falling returns extra = pcp * (mult-1) = -0.05
        let effect = ev.compute_effect(-0.05);
        assert!((effect - (-0.05)).abs() < 1e-10);
    }

    #[test]
    fn test_supply_glut_suppresses_rising() {
        let ev = gold_supply_glut();
        // Java: SUPPLY_GLUT rising returns suppressed = -2.5% (push toward zero)
        let effect = ev.compute_effect(0.05);
        assert!((effect - (-0.025)).abs() < 1e-10);
    }

    #[test]
    fn test_material_wildcard_prefix() {
        let ev = gold_supply_glut();
        assert!(ev.matches_material("GOLD_INGOT"));
        assert!(ev.matches_material("GOLD_NUGGET"));
        assert!(ev.matches_material("gold_nugget")); // case insensitive
        assert!(!ev.matches_material("GOLD"));
        assert!(!ev.matches_material("GOLDSWORD"));
    }

    #[test]
    fn test_material_wildcard_suffix() {
        let ev = MarketEvent {
            name: "Ingot Rush".into(),
            event_type: EventType::GoldRush,
            materials: vec!["*_INGOT".into()],
            multiplier: 1.5,
            starts_at_tick: 0,
            ends_at_tick: 1000,
        };
        assert!(ev.matches_material("GOLD_INGOT"));
        assert!(ev.matches_material("IRON_INGOT"));
        assert!(!ev.matches_material("INGOT"));
    }

    #[test]
    fn test_apply_event_multiplier() {
        let events = vec![diamond_demand_surge()];
        // +5% on diamond with 2× demand surge
        // effect = extra = +5% → result = natural + effect = 0.05 + 0.05 = 0.10
        let result = apply_event_multiplier(&events, "DIAMOND", 0.05);
        assert!((result - 0.10).abs() < 1e-10);
        // Non-matching material → unchanged
        let result2 = apply_event_multiplier(&events, "IRON_INGOT", 0.05);
        assert!((result2 - 0.05).abs() < 1e-10);
    }

    #[test]
    fn test_multiple_events_stack() {
        let ev1 = MarketEvent {
            name: "A".into(),
            event_type: EventType::DemandSurge,
            materials: vec!["DIAMOND".into()],
            multiplier: 2.0,
            starts_at_tick: 0,
            ends_at_tick: 1000,
        };
        let ev2 = MarketEvent {
            name: "B".into(),
            event_type: EventType::GoldRush,
            materials: vec!["DIAMOND".into()],
            multiplier: 1.5,
            starts_at_tick: 0,
            ends_at_tick: 1000,
        };
        let events = vec![ev1, ev2];
        // Both apply to diamond. Natural +5%.
        // ev1 (DemandSurge, 2×): rising → effect = extra = +0.05
        // ev2 (GoldRush, 1.5×): rising → effect = extra = 0.05 × 0.5 = +0.025
        // Total event effect: 0.05 + 0.025 = +0.075
        // Result: natural 0.05 + 0.075 = +0.125
        let result = apply_event_multiplier(&events, "DIAMOND", 0.05);
        assert!((result - 0.125).abs() < 1e-10);
    }

    #[test]
    fn test_inflation_boost_always_up() {
        let ev = MarketEvent {
            name: "Inflate".into(),
            event_type: EventType::InflationBoost,
            materials: vec!["*".into()],
            multiplier: 2.0,
            starts_at_tick: 0,
            ends_at_tick: 1000,
        };
        // Java: return abs(pcp) * (mult-1) — always positive, always upward
        let effect_up = ev.compute_effect(0.05);
        assert!((effect_up - 0.05).abs() < 1e-10);
        let effect_down = ev.compute_effect(-0.05);
        assert!((effect_down - 0.05).abs() < 1e-10);
        // Always positive (always upward)
        assert!(effect_up > 0.0);
        assert!(effect_down > 0.0);
    }

    #[test]
    fn test_deflation_drop_always_down() {
        let ev = MarketEvent {
            name: "Deflate".into(),
            event_type: EventType::DeflationDrop,
            materials: vec!["*".into()],
            multiplier: 2.0,
            starts_at_tick: 0,
            ends_at_tick: 1000,
        };
        // Java: return -abs(pcp) * (mult-1) — always negative, always downward
        let effect_up = ev.compute_effect(0.05);
        assert!((effect_up - (-0.05)).abs() < 1e-10);
        let effect_down = ev.compute_effect(-0.05);
        assert!((effect_down - (-0.05)).abs() < 1e-10);
        // Always negative (always downward)
        assert!(effect_up < 0.0);
        assert!(effect_down < 0.0);
    }
}
