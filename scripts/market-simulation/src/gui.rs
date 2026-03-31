use std::path::PathBuf;

use eframe::egui;
use egui_plot::{Bar, BarChart, GridMark, Line, Plot, PlotPoints};

use crate::config::SimConfig;
use crate::engine::PriceTrendDirection;
use crate::loan::LoanStatus;
use crate::player::Archetype;
use crate::recorder::DataRecorder;
use crate::simulation::Simulation;

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum ChartTab {
    Prices,
    Spreads,
    Volume,
    Economy,
    Detail,
}

pub struct GuiState {
    pub tab: ChartTab,
    pub selected_items: Vec<bool>,
    pub focused_item: usize,
    pub pending_config: SimConfig,
    pub selected_player: Option<usize>,
    pub recording: bool,
    pub recording_start_tick: u64,
    pub recording_path: Option<String>,
    pub recording_error: Option<String>,
}

impl GuiState {
    pub fn new(config: &SimConfig) -> Self {
        let item_count = config.items.len();
        Self {
            tab: ChartTab::Prices,
            selected_items: vec![true; item_count],
            focused_item: 0,
            pending_config: config.clone(),
            selected_player: None,
            recording: false,
            recording_start_tick: 0,
            recording_path: None,
            recording_error: None,
        }
    }
}

const ITEM_COLORS: &[(u8, u8, u8)] = &[
    (255, 107, 107), // red
    (78, 205, 196),  // teal
    (255, 230, 109), // yellow
    (162, 155, 254), // purple
    (0, 210, 211),   // cyan
    (255, 159, 67),  // orange
    (46, 213, 115),  // green
    (116, 185, 255), // blue
];

fn item_color(idx: usize) -> egui::Color32 {
    let (r, g, b) = ITEM_COLORS[idx % ITEM_COLORS.len()];
    egui::Color32::from_rgb(r, g, b)
}

fn help(ui: &mut egui::Ui, text: &str) {
    ui.label(
        egui::RichText::new("?")
            .small()
            .color(egui::Color32::from_rgb(130, 170, 255)),
    )
    .on_hover_ui(|ui| {
        ui.style_mut().interaction.selectable_labels = false;
        ui.label(egui::RichText::new(text).small());
    });
}

pub fn draw_gui(ctx: &egui::Context, sim: &mut Simulation, gui: &mut GuiState) {
    draw_top_bar(ctx, sim, gui);
    draw_left_sidebar(ctx, sim, gui);
    draw_player_inspector(ctx, sim, gui);
    draw_center_panel(ctx, sim, gui);
}

fn draw_top_bar(ctx: &egui::Context, sim: &mut Simulation, gui: &mut GuiState) {
    egui::TopBottomPanel::top("top_bar").show(ctx, |ui| {
        ui.horizontal(|ui| {
            if sim.paused {
                if ui.button("Play").clicked() {
                    sim.paused = false;
                }
            } else if ui.button("Pause").clicked() {
                sim.paused = true;
            }

            if ui.button("Step").clicked() {
                sim.tick();
            }

            ui.label("Speed:");
            ui.add(
                egui::Slider::new(&mut sim.speed, 0.1..=100.0)
                    .logarithmic(true)
                    .clamping(egui::SliderClamping::Always),
            );

            ui.separator();

            ui.label(format!("Tick: {}", sim.current_tick));
            ui.label(format!("Time: {}", sim.sim_time_string()));

            ui.separator();

            if ui.button("Reset").clicked() {
                if gui.recording {
                    if let Some(recorder) = &mut sim.recorder {
                        let _ = recorder.finalize();
                    }
                    gui.recording = false;
                    gui.recording_path = None;
                    gui.recording_error = None;
                }
                sim.reset();
                gui.pending_config = sim.config.clone();
            }

            ui.separator();

            if gui.recording {
                let elapsed = sim.current_tick.saturating_sub(gui.recording_start_tick);
                ui.colored_label(
                    egui::Color32::from_rgb(255, 70, 70),
                    format!("REC {} ticks", elapsed),
                );
                if ui.button("Stop").clicked() {
                    if let Some(recorder) = &mut sim.recorder
                        && let Err(e) = recorder.finalize()
                    {
                        gui.recording_error = Some(format!("{e}"));
                    }
                    sim.recorder = None;
                    gui.recording = false;
                }
            } else if ui.button("Record").clicked() {
                gui.recording_error = None;
                let filename = format!("sim_dump_{}.db", sim.current_tick);
                let path = PathBuf::from(&filename);
                match serde_json::to_string(&sim.config) {
                    Ok(config_json) => match DataRecorder::new(path, &config_json) {
                        Ok(recorder) => {
                            gui.recording_path = Some(recorder.path.display().to_string());
                            sim.recorder = Some(recorder);
                            gui.recording = true;
                            gui.recording_start_tick = sim.current_tick;
                        }
                        Err(e) => {
                            gui.recording_error = Some(format!("{e}"));
                        }
                    },
                    Err(e) => {
                        gui.recording_error = Some(format!("Config serialize error: {e}"));
                    }
                }
            }

            if let Some(path) = &gui.recording_path {
                ui.colored_label(egui::Color32::GRAY, path);
            }

            if let Some(err) = &gui.recording_error {
                ui.colored_label(egui::Color32::from_rgb(255, 70, 70), err);
            }
        });
    });
}

fn draw_left_sidebar(ctx: &egui::Context, sim: &mut Simulation, gui: &mut GuiState) {
    egui::SidePanel::left("sidebar")
        .default_width(300.0)
        .show(ctx, |ui| {
            egui::ScrollArea::vertical().show(ui, |ui| {
                draw_config_panel(ui, sim, gui);
                ui.separator();
                draw_stress_panel(ui, sim);
                ui.separator();
                draw_item_selection(ui, sim, gui);
            });
        });
}

fn slider_with_help(ui: &mut egui::Ui, slider: egui::Slider<'_>, tooltip: &str) -> bool {
    let changed = ui.horizontal(|ui| {
        let r = ui.add(slider).changed();
        help(ui, tooltip);
        r
    });
    changed.inner
}

fn draw_config_panel(ui: &mut egui::Ui, sim: &mut Simulation, gui: &mut GuiState) {
    ui.heading("Economy");
    let cfg = &mut gui.pending_config;

    let online = sim.players.iter().filter(|p| p.online).count();
    let fep = cfg.player_scaling.full_effect_players;
    let scaling = (online as f64 * 2.6466524123622457 / fep as f64).tanh();

    let mut changed = false;

    let tip = format!(
        "newPrice = price + price * tradeRatio * playerScaling * (maxChange/100)\n\n\
        tradeRatio = (weightedBuys - weightedSells) / total, range [-1, +1]\n\
        playerScaling = tanh(n * 2.647 / FEP), range [0, 1]\n\n\
        With {online} online, scaling = {scaling:.3}\n\
        Max single-tick move = {:.2}% * {scaling:.3} = {:.3}%",
        cfg.economy.max_price_change_percent,
        cfg.economy.max_price_change_percent * scaling,
    );
    changed |= slider_with_help(
        ui,
        egui::Slider::new(&mut cfg.economy.max_price_change_percent, 0.1..=20.0)
            .text("Max Price Change %"),
        &tip,
    );

    let tip = format!(
        "Trades within this window affect price. Recency-weighted:\n\
        weight = max(0, 1 - age/window)\n\n\
        Window = {} days = {} ticks\n\
        A trade halfway through the window has weight 0.5\n\
        A trade at the edge has weight ~0.0",
        cfg.economy.trade_window_days,
        cfg.economy.trade_window_days as u64 * 288,
    );
    changed |= slider_with_help(
        ui,
        egui::Slider::new(&mut cfg.economy.trade_window_days, 1..=30).text("Trade Window (days)"),
        &tip,
    );

    changed |= slider_with_help(
        ui,
        egui::Slider::new(&mut cfg.economy.slippage_coeff, 0.0..=0.05).text("Slippage Coeff"),
        "Large trades pay more per unit (sqrt scaling).\n\n\
        cost = price * (1 + coeff * sqrt(amount)) * amount\n\
        At 0.01, buying 64 units costs 8% more per unit.\n\
        Buying 16 units costs 4% more per unit.\n\
        Discourages huge single-trade manipulation.",
    );

    let spm = cfg.economy.sell_pressure_multiplier;
    let tip = format!(
        "Negative price changes are amplified by {spm:.2}x.\n\n\
        A raw -1% change becomes {:.2}%.\n\
        A raw +1% change stays at +1%.\n\n\
        Models real-world panic selling.",
        -spm,
    );
    changed |= slider_with_help(
        ui,
        egui::Slider::new(&mut cfg.economy.sell_pressure_multiplier, 1.0..=2.0)
            .text("Sell Pressure"),
        &tip,
    );

    let sc = cfg.economy.sector_correlation;
    let tip = format!(
        "Items in the same section influence each other.\n\n\
        correlation = {sc:.2}\n\
        If ores average +2%, each ore gets an extra\n\
        {sc:.2} * 2% = {:.2}% nudge.\n\n\
        0 = fully independent, 0.5 = strong coupling.",
        sc * 2.0,
    );
    changed |= slider_with_help(
        ui,
        egui::Slider::new(&mut cfg.economy.sector_correlation, 0.0..=0.5)
            .text("Sector Correlation"),
        &tip,
    );

    changed |= slider_with_help(
        ui,
        egui::Slider::new(&mut cfg.economy.max_sector_correlation_group_size, 2..=100)
            .text("Max Sector Group Size"),
        "Sections with more items than this are skipped\n\
        for sector correlation to prevent server-wide cascading.",
    );

    let rl = cfg.economy.player_rate_limit_multiplier;
    let tip = format!(
        "Cap any player's weighted volume at {rl:.1}x the mean.\n\n\
        mean = total_weighted / distinct_traders\n\
        cap = mean * {rl:.1}\n\n\
        Excess volume is proportionally scaled down.\n\
        Prevents a single whale from dominating price.",
    );
    changed |= slider_with_help(
        ui,
        egui::Slider::new(&mut cfg.economy.player_rate_limit_multiplier, 1.0..=10.0)
            .text("Player Rate Limit"),
        &tip,
    );

    let td = cfg.economy.trend_dampening;
    let floor = cfg.economy.trend_dampening_floor;
    let tip = format!(
        "Prolonged trends are progressively dampened.\n\
        Only applied when continuing the streak direction.\n\n\
        dampening = max(1 / (1 + streak * {td:.2}), {floor:.2})\n\
        Streak 0:  100% of max change\n\
        Streak 5:  {:.0}% of max change\n\
        Streak 10: {:.0}% of max change\n\
        Streak 20: {:.0}% of max change\n\n\
        Prevents runaway inflation/deflation.",
        (100.0 / (1.0 + 5.0 * td)).max(floor * 100.0),
        (100.0 / (1.0 + 10.0 * td)).max(floor * 100.0),
        (100.0 / (1.0 + 20.0 * td)).max(floor * 100.0),
    );
    changed |= slider_with_help(
        ui,
        egui::Slider::new(&mut cfg.economy.trend_dampening, 0.0..=1.0).text("Trend Dampening"),
        &tip,
    );

    changed |= slider_with_help(
        ui,
        egui::Slider::new(&mut cfg.economy.trend_streak_threshold_percent, 0.0..=1.0)
            .text("Streak Threshold %"),
        "Minimum % change per tick to count as directional.\n\n\
        Changes smaller than this are STABLE and decay the streak.\n\
        Prevents tiny rounding changes from building long streaks.",
    );

    changed |= slider_with_help(
        ui,
        egui::Slider::new(&mut cfg.economy.trend_dampening_floor, 0.0..=1.0)
            .text("Dampening Floor"),
        "Floor for the dampening factor.\n\n\
        Dampening never reduces price changes below this fraction.\n\
        At 0.25, even a 100-tick streak still allows 25% of max change.",
    );

    ui.horizontal(|ui| {
        if ui
            .checkbox(&mut cfg.economy.adaptive_window, "Adaptive Window")
            .changed()
        {
            changed = true;
        }
        help(
            ui,
            "Automatically scales the trade window based on transaction density.\n\n\
            Low activity = wider window (captures more history).\n\
            High activity = narrower window (more responsive).\n\
            Target density: 100 transactions/day.",
        );
    });

    if cfg.economy.adaptive_window {
        let ew = sim.engine.effective_window_ticks;
        let ew_days = ew as f64 / 288.0;
        let tip = format!(
            "Minimum window when activity is high.\n\n\
            Current effective window: {ew} ticks ({ew_days:.1} days)",
        );
        changed |= slider_with_help(
            ui,
            egui::Slider::new(
                &mut cfg.economy.min_window_days,
                1..=cfg.economy.trade_window_days,
            )
            .text("Min Window (days)"),
            &tip,
        );

        let tip = format!(
            "Maximum window when activity is low.\n\n\
            Current effective window: {ew} ticks ({ew_days:.1} days)",
        );
        changed |= slider_with_help(
            ui,
            egui::Slider::new(
                &mut cfg.economy.max_window_days,
                cfg.economy.trade_window_days..=30,
            )
            .text("Max Window (days)"),
            &tip,
        );
    }

    ui.heading("Spread");

    let half = cfg.spread.base_spread / 2.0;
    let tip = format!(
        "Split evenly into BPD and SPD before adjustments.\n\n\
        halfSpread = baseSpread / 2 = {:.2} / 2 = {half:.2}\n\
        Buy price  = base * (1 + BPD) = base * {:.2}\n\
        Sell price = base * (1 - SPD) = base * {:.2}\n\n\
        Example: $100 item\n\
        Buy @ ${:.2}, Sell @ ${:.2} (before other factors)",
        cfg.spread.base_spread,
        1.0 + half,
        1.0 - half,
        100.0 * (1.0 + half),
        100.0 * (1.0 - half),
    );
    changed |= slider_with_help(
        ui,
        egui::Slider::new(&mut cfg.spread.base_spread, 0.01..=2.0).text("Base Spread"),
        &tip,
    );

    let vi = cfg.spread.volume_impact;
    let tip = format!(
        "Shifts spread toward the dominant trade side.\n\n\
        buyRatio = weightedBuys / totalWeighted\n\
        imbalance = (buyRatio - 0.5) * 2, range [-1, +1]\n\n\
        BPD += max(0, imbalance) * halfSpread * {vi:.1}\n\
        SPD += max(0, -imbalance) * halfSpread * {vi:.1}\n\n\
        At 100% buy pressure with halfSpread {half:.2}:\n\
        BPD goes from {half:.2} to {:.2} (+{:.2})\n\
        SPD stays at {half:.2}",
        half + half * vi,
        half * vi,
    );
    changed |= slider_with_help(
        ui,
        egui::Slider::new(&mut cfg.spread.volume_impact, 0.0..=2.0).text("Volume Impact"),
        &tip,
    );

    let player_scaling = if online > 0 { scaling } else { 0.0 };
    let pi = cfg.spread.player_impact;
    let player_reduction = 1.0 - pi * player_scaling;
    let tip = format!(
        "More players = tighter spreads.\n\n\
        playerScaling = tanh(n * 2.647 / FEP)\n\
        playerReduction = 1 - playerImpact * playerScaling\n\
        spread *= playerReduction\n\n\
        With {online} online (FEP={fep}):\n\
        scaling = {player_scaling:.3}\n\
        reduction = 1 - {pi:.1} * {player_scaling:.3} = {player_reduction:.3}\n\
        Spread multiplied by {:.1}%",
        player_reduction * 100.0,
    );
    changed |= slider_with_help(
        ui,
        egui::Slider::new(&mut cfg.spread.player_impact, 0.0..=1.0).text("Player Impact"),
        &tip,
    );

    let lc = cfg.spread.liquidity_coeff;
    let fet = cfg.spread.liquidity_full_effect_traders.max(1) as f64;
    let tip = format!(
        "High-volume items get tighter spreads, scaled by trader diversity.\n\n\
        effectiveCoeff = (coeff / fullEffectTraders) * min(distinctTraders, fullEffectTraders)\n\
        liquidityReduction = 1 / (1 + totalWeightedVolume * effectiveCoeff)\n\n\
        At coeff={lc:.4}, fullEffectTraders={fet:.0}:\n\
        1 trader,  vol=100: effectiveCoeff={:.5}, reduction={:.3}\n\
        5 traders, vol=100: effectiveCoeff={:.5}, reduction={:.3}\n\
        {fet:.0} traders, vol=100: effectiveCoeff={:.5}, reduction={:.3}",
        lc / fet * 1.0,
        1.0 / (1.0 + 100.0 * lc / fet * 1.0),
        lc / fet * 5.0,
        1.0 / (1.0 + 100.0 * lc / fet * 5.0),
        lc / fet * fet,
        1.0 / (1.0 + 100.0 * lc / fet * fet),
    );
    changed |= slider_with_help(
        ui,
        egui::Slider::new(&mut cfg.spread.liquidity_coeff, 0.0..=0.5).text("Liquidity Coeff"),
        &tip,
    );

    let tip = format!(
        "Number of distinct traders needed for full liquidity benefit.\n\n\
        With fewer distinct traders, spread compression is reduced proportionally.\n\
        Prevents single-player manipulation of spreads.\n\n\
        Current: {}\n\
        1 trader  = {:.0}% of liquidity benefit\n\
        5 traders = {:.0}% of liquidity benefit\n\
        {} traders = 100% of liquidity benefit",
        cfg.spread.liquidity_full_effect_traders,
        100.0 / fet,
        (500.0 / fet).min(100.0),
        cfg.spread.liquidity_full_effect_traders,
    );
    changed |= slider_with_help(
        ui,
        egui::Slider::new(&mut cfg.spread.liquidity_full_effect_traders, 1..=50)
            .text("Liquidity Full Effect Traders"),
        &tip,
    );

    ui.heading("Scaling");

    let fep_f = fep as f64;
    let tip = format!(
        "tanh curve for player count effect.\n\n\
        scaling = tanh(n * atanh(0.99) / FEP)\n\
        scaling = tanh(n * 2.647 / {fep})\n\n\
        1 player:  {:.3}\n\
        5 players: {:.3}\n\
        {fep} players: {:.3} (FEP)\n\
        {} players: {:.3} (2x FEP)\n\n\
        Affects both price changes AND spread player reduction.",
        (1.0 * 2.6466524123622457 / fep_f).tanh(),
        (5.0 * 2.6466524123622457 / fep_f).tanh(),
        (fep_f * 2.6466524123622457 / fep_f).tanh(),
        fep * 2,
        (fep_f * 2.0 * 2.6466524123622457 / fep_f).tanh(),
    );
    changed |= slider_with_help(
        ui,
        egui::Slider::new(&mut cfg.player_scaling.full_effect_players, 1..=100)
            .text("Full Effect Players"),
        &tip,
    );

    ui.heading("Loans");

    let br = cfg.loans.base_interest_rate;
    let ci = cfg.loans.compound_interval_hours;
    let tip = format!(
        "rate = baseRate * (1 + (500 - creditScore) / 1000)\n\n\
        At base = {:.0}%:\n\
        Score 700: {:.0}% * (1 + -0.2) = {:.1}%\n\
        Score 500: {:.0}% * (1 + 0.0)  = {:.1}%\n\
        Score 300: {:.0}% * (1 + 0.2)  = {:.1}%\n\
        Score 100: {:.0}% * (1 + 0.4)  = {:.1}%\n\n\
        Applied every {ci} hours ({} ticks).",
        br * 100.0,
        br * 100.0,
        br * 0.8 * 100.0,
        br * 100.0,
        br * 100.0,
        br * 100.0,
        br * 1.2 * 100.0,
        br * 100.0,
        br * 1.4 * 100.0,
        ci as u64 * 12,
    );
    changed |= slider_with_help(
        ui,
        egui::Slider::new(&mut cfg.loans.base_interest_rate, 0.01..=0.5).text("Interest Rate"),
        &tip,
    );

    let tip = format!(
        "Interest compounds every {ci} hours ({} ticks).\n\n\
        A $1000 loan at {:.0}% base rate:\n\
        After 1 compound:  ${:.2}\n\
        After 7 compounds: ${:.2}\n\
        After 30 compounds: ${:.2}",
        ci as u64 * 12,
        br * 100.0,
        1000.0 * (1.0 + br),
        1000.0 * (1.0 + br).powi(7),
        1000.0 * (1.0 + br).powi(30),
    );
    changed |= slider_with_help(
        ui,
        egui::Slider::new(&mut cfg.loans.compound_interval_hours, 1..=168).text("Compound (hours)"),
        &tip,
    );

    let dd = cfg.loans.default_duration_days;
    let tip = format!(
        "Loan must be repaid within {dd} days ({} ticks).\n\n\
        With compound every {ci}h, that's {:.0} interest applications\n\
        before default.",
        dd as u64 * 288,
        (dd as f64 * 24.0) / ci as f64,
    );
    changed |= slider_with_help(
        ui,
        egui::Slider::new(&mut cfg.loans.default_duration_days, 1..=30).text("Duration (days)"),
        &tip,
    );

    let dp = cfg.loans.default_penalty;
    let tip = format!(
        "creditScore -= {dp} on default.\n\n\
        Score 500 -> {} (can still borrow, min=200)\n\
        Score 300 -> {} {}\n\
        Score 200 -> {} (locked out)",
        500 - dp,
        (300 - dp).max(0),
        if 300 - dp < 200 { "(locked out)" } else { "" },
        (200 - dp).max(0),
    );
    changed |= slider_with_help(
        ui,
        egui::Slider::new(&mut cfg.loans.default_penalty, 10..=200).text("Default Penalty"),
        &tip,
    );

    if changed {
        sim.apply_config(cfg.clone());
    }
}

fn draw_stress_panel(ui: &mut egui::Ui, sim: &mut Simulation) {
    ui.horizontal(|ui| {
        ui.heading("Stress Tests");
        help(ui, "Presets that push the economy to extremes. Use these to test how the market engine handles adversarial or unusual conditions.");
    });

    ui.horizontal(|ui| {
        if ui.button("Market Crash").clicked() {
            sim.stress_market_crash();
        }
        help(ui, "All existing players become sell-only. Adds 20 farmers with 100 of every item. Simulates a mass sell-off.");
    });
    ui.horizontal(|ui| {
        if ui.button("Exploit Attempt").clicked() {
            sim.stress_exploit();
        }
        help(ui, "Adds 3 exploiters with $100k each, all targeting one item. Tests if spread widens enough to limit manipulation.");
    });
    ui.horizontal(|ui| {
        if ui.button("Low Players").clicked() {
            sim.stress_low_players();
        }
        help(ui, "Removes all but 2 players. Tests tanh scaling: with few players, price changes should be heavily dampened.");
    });
    ui.horizontal(|ui| {
        if ui.button("Hyperinflation").clicked() {
            sim.stress_hyperinflation();
        }
        help(ui, "Sets max price change to 10% and adds 20 aggressive buyers with $500k each. Prices should climb rapidly.");
    });
    ui.horizontal(|ui| {
        if ui.button("Loan Cascade").clicked() {
            sim.stress_loan_cascade();
        }
        help(ui, "All players take max loans and compound interval drops to 1 hour. Tests debt spiral and mass defaults.");
    });
}

fn draw_item_selection(ui: &mut egui::Ui, sim: &mut Simulation, gui: &mut GuiState) {
    ui.heading("Items");

    for (i, item) in sim.engine.items.iter().enumerate() {
        ui.horizontal(|ui| {
            let color = item_color(i);
            ui.colored_label(color, "  ");

            if i < gui.selected_items.len() {
                ui.checkbox(&mut gui.selected_items[i], "");
            }

            let label = format!("{}: ${:.2}", item.name, item.price);

            if ui.selectable_label(gui.focused_item == i, label).clicked() {
                gui.focused_item = i;
            }

            let trend_str = match item.trend.direction {
                PriceTrendDirection::Up => format!("+{:.1}%", item.trend.percent_change),
                PriceTrendDirection::Down => format!("{:.1}%", item.trend.percent_change),
                PriceTrendDirection::Stable => "~".to_string(),
            };
            let trend_color = match item.trend.direction {
                PriceTrendDirection::Up => egui::Color32::from_rgb(46, 213, 115),
                PriceTrendDirection::Down => egui::Color32::from_rgb(255, 107, 107),
                PriceTrendDirection::Stable => egui::Color32::GRAY,
            };
            ui.colored_label(trend_color, trend_str);
        });
    }

    if gui.focused_item < gui.pending_config.items.len() {
        ui.separator();
        let item_name = gui.pending_config.items[gui.focused_item].name.clone();
        ui.heading(format!("{item_name} Overrides"));

        let item_cfg = &mut gui.pending_config.items[gui.focused_item];
        let mut changed = false;

        let mut has_max_change = item_cfg.max_price_change_override.is_some();
        ui.horizontal(|ui| {
            if ui.checkbox(&mut has_max_change, "").changed() {
                if has_max_change {
                    item_cfg.max_price_change_override =
                        Some(gui.pending_config.economy.max_price_change_percent);
                } else {
                    item_cfg.max_price_change_override = None;
                }
                changed = true;
            }
            if let Some(ref mut val) = item_cfg.max_price_change_override {
                changed |= ui
                    .add(egui::Slider::new(val, 0.1..=20.0).text("Max Price Change %"))
                    .changed();
            } else {
                ui.add_enabled(
                    false,
                    egui::Slider::new(
                        &mut gui.pending_config.economy.max_price_change_percent.clone(),
                        0.1..=20.0,
                    )
                    .text("Max Price Change % (global)"),
                );
            }
            help(
                ui,
                "Override max price change % for this item.\n\
                Uncheck to use the global value.",
            );
        });

        let mut has_spread = item_cfg.base_spread_override.is_some();
        ui.horizontal(|ui| {
            if ui.checkbox(&mut has_spread, "").changed() {
                if has_spread {
                    item_cfg.base_spread_override = Some(gui.pending_config.spread.base_spread);
                } else {
                    item_cfg.base_spread_override = None;
                }
                changed = true;
            }
            if let Some(ref mut val) = item_cfg.base_spread_override {
                changed |= ui
                    .add(egui::Slider::new(val, 0.01..=2.0).text("Base Spread"))
                    .changed();
            } else {
                ui.add_enabled(
                    false,
                    egui::Slider::new(
                        &mut gui.pending_config.spread.base_spread.clone(),
                        0.01..=2.0,
                    )
                    .text("Base Spread (global)"),
                );
            }
            help(
                ui,
                "Override base spread for this item.\n\
                Uncheck to use the global value.",
            );
        });

        if changed {
            sim.apply_config(gui.pending_config.clone());
        }
    }
}

fn draw_center_panel(ctx: &egui::Context, sim: &mut Simulation, gui: &mut GuiState) {
    egui::CentralPanel::default().show(ctx, |ui| {
        ui.horizontal(|ui| {
            ui.selectable_value(&mut gui.tab, ChartTab::Prices, "Prices");
            ui.selectable_value(&mut gui.tab, ChartTab::Spreads, "Spreads");
            ui.selectable_value(&mut gui.tab, ChartTab::Volume, "Volume");
            ui.selectable_value(&mut gui.tab, ChartTab::Economy, "Economy");
            ui.selectable_value(&mut gui.tab, ChartTab::Detail, "Detail");
        });

        ui.separator();

        let available = ui.available_size();
        let chart_height = (available.y - 200.0).max(100.0);

        match gui.tab {
            ChartTab::Prices => draw_prices_chart(ui, sim, gui, chart_height),
            ChartTab::Spreads => draw_spreads_chart(ui, sim, gui, chart_height),
            ChartTab::Volume => draw_volume_chart(ui, sim, gui, chart_height),
            ChartTab::Economy => draw_economy_chart(ui, sim, chart_height),
            ChartTab::Detail => draw_detail_chart(ui, sim, gui, chart_height),
        }

        ui.separator();
        draw_player_panel(ui, sim, gui);
    });
}

fn log_price_formatter(mark: GridMark, _range: &std::ops::RangeInclusive<f64>) -> String {
    let dollar = 10.0_f64.powf(mark.value);
    if dollar >= 1000.0 {
        format!("${:.0}", dollar)
    } else if dollar >= 1.0 {
        format!("${:.2}", dollar)
    } else {
        format!("${:.3}", dollar)
    }
}

fn log_y(price: f64) -> f64 {
    if price > 0.0 { price.log10() } else { -2.0 }
}

fn draw_prices_chart(ui: &mut egui::Ui, sim: &Simulation, gui: &GuiState, height: f32) {
    Plot::new("prices_plot")
        .height(height)
        .allow_zoom(true)
        .allow_drag(true)
        .legend(egui_plot::Legend::default())
        .y_axis_formatter(log_price_formatter)
        .show(ui, |plot_ui| {
            for (i, item) in sim.engine.items.iter().enumerate() {
                if i >= gui.selected_items.len() || !gui.selected_items[i] {
                    continue;
                }
                let points: PlotPoints = item
                    .price_history
                    .iter()
                    .enumerate()
                    .map(|(x, &y)| [x as f64, log_y(y)])
                    .collect();
                plot_ui.line(Line::new(&item.name, points).color(item_color(i)));
            }

            if gui.focused_item < sim.engine.items.len() {
                let item = &sim.engine.items[gui.focused_item];
                if !item.buy_price_history.is_empty() {
                    let buy_points: PlotPoints = item
                        .buy_price_history
                        .iter()
                        .enumerate()
                        .map(|(x, &y)| [x as f64, log_y(y)])
                        .collect();
                    plot_ui.line(
                        Line::new(format!("{} Buy", item.name), buy_points)
                            .color(egui::Color32::from_rgba_premultiplied(46, 213, 115, 128))
                            .style(egui_plot::LineStyle::dashed_dense()),
                    );

                    let sell_points: PlotPoints = item
                        .sell_price_history
                        .iter()
                        .enumerate()
                        .map(|(x, &y)| [x as f64, log_y(y)])
                        .collect();
                    plot_ui.line(
                        Line::new(format!("{} Sell", item.name), sell_points)
                            .color(egui::Color32::from_rgba_premultiplied(255, 107, 107, 128))
                            .style(egui_plot::LineStyle::dashed_dense()),
                    );
                }
            }
        });
}

fn draw_spreads_chart(ui: &mut egui::Ui, sim: &Simulation, gui: &GuiState, height: f32) {
    if gui.focused_item >= sim.engine.items.len() {
        ui.label("No item focused");
        return;
    }

    let item = &sim.engine.items[gui.focused_item];
    ui.label(format!(
        "Spread: {} | BPD: {:.2}% | SPD: {:.2}% | Buy: ${:.2} | Sell: ${:.2}",
        item.name,
        item.spread.bpd * 100.0,
        item.spread.spd * 100.0,
        item.buy_price(),
        item.sell_price(),
    ));

    let half_height = height / 2.0;

    Plot::new("spreads_pct_plot")
        .height(half_height)
        .allow_zoom(true)
        .allow_drag(true)
        .legend(egui_plot::Legend::default())
        .y_axis_formatter(|mark: GridMark, _| format!("{:.1}%", mark.value))
        .show(ui, |plot_ui| {
            let bpd_points: PlotPoints = item
                .bpd_history
                .iter()
                .enumerate()
                .map(|(x, &y)| [x as f64, y * 100.0])
                .collect();
            plot_ui
                .line(Line::new("BPD %", bpd_points).color(egui::Color32::from_rgb(46, 213, 115)));

            let spd_points: PlotPoints = item
                .spd_history
                .iter()
                .enumerate()
                .map(|(x, &y)| [x as f64, y * 100.0])
                .collect();
            plot_ui
                .line(Line::new("SPD %", spd_points).color(egui::Color32::from_rgb(255, 107, 107)));

            let total_spread: PlotPoints = item
                .bpd_history
                .iter()
                .zip(item.spd_history.iter())
                .enumerate()
                .map(|(x, (&b, &s))| [x as f64, (b + s) * 100.0])
                .collect();
            plot_ui.line(
                Line::new("Total Spread %", total_spread)
                    .color(egui::Color32::from_rgb(255, 230, 109))
                    .style(egui_plot::LineStyle::dashed_dense()),
            );
        });

    Plot::new("spreads_dollar_plot")
        .height(half_height)
        .allow_zoom(true)
        .allow_drag(true)
        .legend(egui_plot::Legend::default())
        .y_axis_formatter(|mark: GridMark, _| format!("${:.2}", mark.value))
        .show(ui, |plot_ui| {
            let buy_points: PlotPoints = item
                .price_history
                .iter()
                .zip(item.bpd_history.iter())
                .enumerate()
                .map(|(x, (&p, &b))| [x as f64, p * (1.0 + b)])
                .collect();
            plot_ui.line(
                Line::new("Buy Price", buy_points).color(egui::Color32::from_rgb(46, 213, 115)),
            );

            let base_points: PlotPoints = item
                .price_history
                .iter()
                .enumerate()
                .map(|(x, &y)| [x as f64, y])
                .collect();
            plot_ui.line(Line::new("Base Price", base_points).color(item_color(gui.focused_item)));

            let sell_points: PlotPoints = item
                .price_history
                .iter()
                .zip(item.spd_history.iter())
                .enumerate()
                .map(|(x, (&p, &s))| [x as f64, p * (1.0 - s)])
                .collect();
            plot_ui.line(
                Line::new("Sell Price", sell_points).color(egui::Color32::from_rgb(255, 107, 107)),
            );
        });
}

fn draw_volume_chart(ui: &mut egui::Ui, sim: &Simulation, gui: &GuiState, height: f32) {
    if gui.focused_item >= sim.engine.items.len() {
        ui.label("No item focused");
        return;
    }

    let item = &sim.engine.items[gui.focused_item];
    ui.label(format!("Volume: {}", item.name));

    Plot::new("volume_plot")
        .height(height)
        .allow_zoom(true)
        .allow_drag(true)
        .legend(egui_plot::Legend::default())
        .show(ui, |plot_ui| {
            let buy_bars: Vec<Bar> = item
                .buy_volume_history
                .iter()
                .enumerate()
                .map(|(x, &y)| Bar::new(x as f64, y as f64).width(0.8))
                .collect();
            plot_ui.bar_chart(
                BarChart::new("Buy Volume", buy_bars).color(egui::Color32::from_rgb(46, 213, 115)),
            );

            let sell_bars: Vec<Bar> = item
                .sell_volume_history
                .iter()
                .enumerate()
                .map(|(x, &y)| Bar::new(x as f64, -(y as f64)).width(0.8))
                .collect();
            plot_ui.bar_chart(
                BarChart::new("Sell Volume", sell_bars)
                    .color(egui::Color32::from_rgb(255, 107, 107)),
            );
        });
}

fn draw_economy_chart(ui: &mut egui::Ui, sim: &Simulation, height: f32) {
    if let Some(snap) = sim.economy_snapshots.last() {
        ui.horizontal(|ui| {
            ui.label(format!("Tick: {}", snap.tick));
            ui.label(format!("GDP: ${:.0}", snap.gdp));
            ui.label(format!("Debt: ${:.0}", snap.total_debt));
            ui.label(format!("Inflation: {:.2}%", snap.avg_price_change));
            ui.label(format!(
                "Online: {}/{}",
                snap.online_players, snap.total_players
            ));
        });
    }

    Plot::new("economy_plot")
        .height(height)
        .allow_zoom(true)
        .allow_drag(true)
        .legend(egui_plot::Legend::default())
        .show(ui, |plot_ui| {
            let gdp_points: PlotPoints = sim
                .economy_snapshots
                .iter()
                .enumerate()
                .map(|(x, s)| [x as f64, s.gdp])
                .collect();
            plot_ui.line(
                Line::new("GDP (24h buy volume)", gdp_points)
                    .color(egui::Color32::from_rgb(46, 213, 115)),
            );

            let debt_points: PlotPoints = sim
                .economy_snapshots
                .iter()
                .enumerate()
                .map(|(x, s)| [x as f64, s.total_debt])
                .collect();
            plot_ui.line(
                Line::new("Total Debt", debt_points).color(egui::Color32::from_rgb(255, 107, 107)),
            );

            let inflation_points: PlotPoints = sim
                .economy_snapshots
                .iter()
                .enumerate()
                .map(|(x, s)| [x as f64, s.avg_price_change])
                .collect();
            plot_ui.line(
                Line::new("Avg Price Change %", inflation_points)
                    .color(egui::Color32::from_rgb(255, 230, 109)),
            );
        });
}

fn draw_detail_chart(ui: &mut egui::Ui, sim: &Simulation, gui: &GuiState, height: f32) {
    if gui.focused_item >= sim.engine.items.len() {
        ui.label("No item focused");
        return;
    }

    let item = &sim.engine.items[gui.focused_item];
    ui.heading(format!("{} Detail", item.name));

    let chart_h = height / 2.0;

    Plot::new("detail_price_plot")
        .height(chart_h)
        .allow_zoom(true)
        .allow_drag(true)
        .legend(egui_plot::Legend::default())
        .show(ui, |plot_ui| {
            let price_points: PlotPoints = item
                .price_history
                .iter()
                .enumerate()
                .map(|(x, &y)| [x as f64, y])
                .collect();
            plot_ui.line(Line::new("Base Price", price_points).color(item_color(gui.focused_item)));

            if !item.buy_price_history.is_empty() {
                let buy_points: PlotPoints = item
                    .buy_price_history
                    .iter()
                    .enumerate()
                    .map(|(x, &y)| [x as f64, y])
                    .collect();
                plot_ui.line(
                    Line::new("Buy Price", buy_points).color(egui::Color32::from_rgb(46, 213, 115)),
                );

                let sell_points: PlotPoints = item
                    .sell_price_history
                    .iter()
                    .enumerate()
                    .map(|(x, &y)| [x as f64, y])
                    .collect();
                plot_ui.line(
                    Line::new("Sell Price", sell_points)
                        .color(egui::Color32::from_rgb(255, 107, 107)),
                );
            }
        });

    Plot::new("detail_volume_plot")
        .height(chart_h)
        .allow_zoom(true)
        .allow_drag(true)
        .legend(egui_plot::Legend::default())
        .show(ui, |plot_ui| {
            let buy_bars: Vec<Bar> = item
                .buy_volume_history
                .iter()
                .enumerate()
                .map(|(x, &y)| Bar::new(x as f64, y as f64).width(0.8))
                .collect();
            plot_ui.bar_chart(
                BarChart::new("Buy Vol", buy_bars).color(egui::Color32::from_rgb(46, 213, 115)),
            );

            let sell_bars: Vec<Bar> = item
                .sell_volume_history
                .iter()
                .enumerate()
                .map(|(x, &y)| Bar::new(x as f64, -(y as f64)).width(0.8))
                .collect();
            plot_ui.bar_chart(
                BarChart::new("Sell Vol", sell_bars).color(egui::Color32::from_rgb(255, 107, 107)),
            );
        });
}

fn draw_player_panel(ui: &mut egui::Ui, sim: &mut Simulation, gui: &mut GuiState) {
    ui.horizontal(|ui| {
        if ui.button("+Casual").clicked() {
            sim.add_player(Archetype::Casual);
        }
        if ui.button("+Farmer").clicked() {
            sim.add_player(Archetype::Farmer);
        }
        if ui.button("+Trader").clicked() {
            sim.add_player(Archetype::Trader);
        }
        if ui.button("+Hoarder").clicked() {
            sim.add_player(Archetype::Hoarder);
        }
        if ui.button("+Exploiter").clicked() {
            sim.add_player(Archetype::Exploiter);
        }
        if ui.button("+10 Random").clicked() {
            sim.add_random_players(10);
        }
        if ui.button("Clear All").clicked() {
            sim.clear_players();
            gui.selected_player = None;
        }

        ui.label(format!("Players: {}", sim.players.len()));

        let online_count = sim.players.iter().filter(|p| p.online).count();
        ui.label(format!("Online: {online_count}"));

        let active_loans = sim
            .loans
            .iter()
            .filter(|l| l.status == LoanStatus::Active)
            .count();
        ui.label(format!("Active Loans: {active_loans}"));

        if gui.selected_player.is_some() && ui.button("Deselect Player").clicked() {
            gui.selected_player = None;
        }
    });

    let available_height = ui.available_height().max(80.0);

    egui::ScrollArea::vertical()
        .max_height(available_height)
        .show(ui, |ui| {
            egui::Grid::new("player_table")
                .striped(true)
                .min_col_width(60.0)
                .show(ui, |ui| {
                    ui.strong("Name");
                    ui.strong("Type");
                    ui.strong("Balance");
                    ui.strong("Net Worth");
                    ui.strong("Online");
                    ui.strong("Trades");
                    ui.strong("Credit");
                    ui.end_row();

                    for (idx, player) in sim.players.iter().enumerate() {
                        let is_selected = gui.selected_player == Some(idx);

                        if ui.selectable_label(is_selected, &player.name).clicked() {
                            gui.selected_player = if is_selected { None } else { Some(idx) };
                        }

                        ui.label(player.archetype.label());
                        ui.label(format!("${:.0}", player.balance));

                        let inventory_value: f64 = player
                            .inventory
                            .iter()
                            .map(|(&item_idx, &qty)| {
                                if item_idx < sim.engine.items.len() {
                                    sim.engine.items[item_idx].sell_price() * qty as f64
                                } else {
                                    0.0
                                }
                            })
                            .sum();
                        let net_worth = player.balance + inventory_value;
                        let nw_color = if net_worth >= 0.0 {
                            egui::Color32::from_rgb(46, 213, 115)
                        } else {
                            egui::Color32::from_rgb(255, 107, 107)
                        };
                        ui.colored_label(nw_color, format!("${:.0}", net_worth));

                        let online_str = if player.online { "Yes" } else { "No" };
                        let online_color = if player.online {
                            egui::Color32::from_rgb(46, 213, 115)
                        } else {
                            egui::Color32::GRAY
                        };
                        ui.colored_label(online_color, online_str);

                        ui.label(format!("{}", player.total_trades));
                        ui.label(format!("{}", player.credit_score));
                        ui.end_row();
                    }
                });
        });
}

fn draw_player_inspector(ctx: &egui::Context, sim: &Simulation, gui: &mut GuiState) {
    let Some(player_idx) = gui.selected_player else {
        return;
    };

    if player_idx >= sim.players.len() {
        gui.selected_player = None;
        return;
    }

    egui::SidePanel::right("player_inspector")
        .default_width(340.0)
        .show(ctx, |ui| {
            egui::ScrollArea::vertical().show(ui, |ui| {
                let player = &sim.players[player_idx];

                ui.horizontal(|ui| {
                    ui.heading(&player.name);
                    let archetype_color = match player.archetype {
                        Archetype::Casual => egui::Color32::from_rgb(116, 185, 255),
                        Archetype::Farmer => egui::Color32::from_rgb(46, 213, 115),
                        Archetype::Trader => egui::Color32::from_rgb(255, 230, 109),
                        Archetype::Hoarder => egui::Color32::from_rgb(162, 155, 254),
                        Archetype::Exploiter => egui::Color32::from_rgb(255, 107, 107),
                        Archetype::Newbie => egui::Color32::from_rgb(255, 183, 77),
                        Archetype::AFKFarmer => egui::Color32::from_rgb(99, 179, 237),
                        Archetype::GuildBuyer => egui::Color32::from_rgb(255, 118, 117),
                        Archetype::MarketMaker => egui::Color32::from_rgb(0, 206, 201),
                        Archetype::InsiderTrader => egui::Color32::from_rgb(255, 183, 197),
                        Archetype::GuildSeller => egui::Color32::from_rgb(255, 165, 77),
                        Archetype::VolumeTrader => egui::Color32::from_rgb(0, 184, 148),
                    };
                    ui.colored_label(archetype_color, player.archetype.label());
                });

                ui.separator();

                // --- Behavior Settings ---
                ui.heading("Behavior Settings");
                egui::Grid::new("player_settings")
                    .num_columns(3)
                    .striped(true)
                    .show(ui, |ui| {
                        let effective_chance = player.online_probability * player.activity_rate * 100.0;
                        let risk_adj_max = (player.max_trade_amount as f64 * player.risk_tolerance).ceil() as i32;

                        ui.label("Online Probability");
                        ui.label(format!("{:.0}%", player.online_probability * 100.0));
                        help(ui, &format!(
                            "Each tick, roll random < {:.0}% to go online.\n\
                            Offline players skip all decisions.\n\n\
                            Combined with activity rate:\n\
                            effective trade chance = {:.0}% * {:.0}% = {:.1}%\n\
                            On average, trades every {:.0} ticks ({:.1} hours)",
                            player.online_probability * 100.0,
                            player.online_probability * 100.0,
                            player.activity_rate * 100.0,
                            effective_chance,
                            if effective_chance > 0.0 { 100.0 / effective_chance } else { f64::INFINITY },
                            if effective_chance > 0.0 { 100.0 / effective_chance / 12.0 } else { f64::INFINITY },
                        ));
                        ui.end_row();

                        ui.label("Activity Rate");
                        ui.label(format!("{:.0}%", player.activity_rate * 100.0));
                        help(ui, &format!(
                            "If online, roll random < {:.0}% to actually trade.\n\n\
                            Two-stage gating:\n\
                            1. Online? {:.0}% chance\n\
                            2. Active? {:.0}% chance\n\
                            P(trade) = {:.0}% * {:.0}% = {:.1}%",
                            player.activity_rate * 100.0,
                            player.online_probability * 100.0,
                            player.activity_rate * 100.0,
                            player.online_probability * 100.0,
                            player.activity_rate * 100.0,
                            effective_chance,
                        ));
                        ui.end_row();

                        ui.label("Buy Threshold");
                        ui.label(format!("{:.1}%", player.buy_threshold * 100.0));
                        help(ui, &format!(
                            "Buy condition: buy_price < perceived * (1 - {:.3})\n\n\
                            For a $100 perceived item:\n\
                            max buy price = $100 * {:.3} = ${:.2}\n\n\
                            Higher threshold = needs bigger discount.\n\
                            0% = buys at any price below perceived value.",
                            player.buy_threshold,
                            1.0 - player.buy_threshold,
                            100.0 * (1.0 - player.buy_threshold),
                        ));
                        ui.end_row();

                        ui.label("Sell Threshold");
                        ui.label(format!("{:.1}%", player.sell_threshold * 100.0));
                        help(ui, &format!(
                            "Sell condition: sell_price > perceived * (1 + {:.3})\n\n\
                            For a $100 perceived item:\n\
                            min sell price = $100 * {:.3} = ${:.2}\n\n\
                            Higher threshold = needs bigger premium.\n\
                            0% = sells at any price above perceived value.",
                            player.sell_threshold,
                            1.0 + player.sell_threshold,
                            100.0 * (1.0 + player.sell_threshold),
                        ));
                        ui.end_row();

                        ui.label("Max Trade Amount");
                        ui.label(format!("{}", player.max_trade_amount));
                        help(ui, &format!(
                            "Scaled by risk tolerance before use:\n\
                            effective_max = ceil({} * {:.2}) = {}\n\n\
                            Actual amount = random(1..=effective_max)\n\
                            Also capped by:\n\
                            - Affordable: floor(balance / price)\n\
                            - Inventory held (for sells)",
                            player.max_trade_amount,
                            player.risk_tolerance,
                            risk_adj_max,
                        ));
                        ui.end_row();

                        ui.label("Risk Tolerance");
                        ui.label(format!("{:.0}%", player.risk_tolerance * 100.0));
                        help(ui, &format!(
                            "Multiplier on max trade amount:\n\
                            effective_max = ceil(max_trade * risk) = ceil({} * {:.2}) = {}\n\n\
                            At current balance ${:.2}:\n\
                            Could buy up to {} of a $50 item per trade\n\
                            or {} of a $500 item per trade",
                            player.max_trade_amount,
                            player.risk_tolerance,
                            risk_adj_max,
                            player.balance,
                            risk_adj_max.min((player.balance / 50.0).floor() as i32),
                            risk_adj_max.min((player.balance / 500.0).floor() as i32),
                        ));
                        ui.end_row();

                        ui.label("Inv. Saturation");
                        ui.label(format!("{:.1}%/item", player.inventory_saturation * 100.0));
                        help(ui, &format!(
                            "Diminishing marginal utility:\n\
                            effective = base / (1 + qty * {:.3})\n\n\
                            With {:.1}% rate:\n\
                            \u{2022}  1 item: base / {:.2} = {:.1}% of base\n\
                            \u{2022}  5 items: base / {:.2} = {:.1}% of base\n\
                            \u{2022} 10 items: base / {:.2} = {:.1}% of base\n\
                            \u{2022} 20 items: base / {:.2} = {:.1}% of base\n\n\
                            Makes hoarding less attractive over time.",
                            player.inventory_saturation,
                            player.inventory_saturation * 100.0,
                            1.0 + 1.0 * player.inventory_saturation,
                            100.0 / (1.0 + 1.0 * player.inventory_saturation),
                            1.0 + 5.0 * player.inventory_saturation,
                            100.0 / (1.0 + 5.0 * player.inventory_saturation),
                            1.0 + 10.0 * player.inventory_saturation,
                            100.0 / (1.0 + 10.0 * player.inventory_saturation),
                            1.0 + 20.0 * player.inventory_saturation,
                            100.0 / (1.0 + 20.0 * player.inventory_saturation),
                        ));
                        ui.end_row();

                        let item_count = sim.engine.items.len();
                        ui.label("Gather Rate");
                        ui.label(format!("{:.0}%/item", player.gather_rate * 100.0));
                        help(ui, &format!(
                            "Per item per active tick:\n\
                            P(gather) = {:.0}% * (sell_price / perceived)\n\n\
                            Price incentive directs gathering toward\n\
                            profitable items:\n\
                            \u{2022} sell @ 2x perceived: {:.0}% gather chance\n\
                            \u{2022} sell @ 1x perceived: {:.0}% gather chance\n\
                            \u{2022} sell @ 0.5x perceived: {:.0}% gather chance\n\n\
                            Gated by online ({:.0}%) and activity ({:.0}%)",
                            player.gather_rate * 100.0,
                            player.gather_rate * 2.0 * 100.0,
                            player.gather_rate * 100.0,
                            player.gather_rate * 0.5 * 100.0,
                            player.online_probability * 100.0,
                            player.activity_rate * 100.0,
                        ));
                        ui.end_row();

                        let avg_pref: f64 = if player.preferences.is_empty() {
                            0.5
                        } else {
                            player.preferences.values().sum::<f64>() / player.preferences.len() as f64
                        };
                        ui.label("Usage Rate");
                        ui.label(format!("{:.0}%/item", player.usage_rate * 100.0));
                        help(ui, &format!(
                            "Each active tick, per item with qty > 0:\n\
                            P(consume 1) = usage_rate * preference\n\
                            = {:.0}% * pref (avg pref = {:.2})\n\n\
                            Higher-preference items are consumed more.\n\
                            Avg effective rate = {:.0}% * {:.2} = {:.1}%\n\n\
                            Net flow per active tick (gather - usage):\n\
                            gather: +{:.1} items, usage: -{:.1} items\n\
                            net: {:.1} items/active tick",
                            player.usage_rate * 100.0,
                            avg_pref,
                            player.usage_rate * 100.0,
                            avg_pref,
                            player.usage_rate * avg_pref * 100.0,
                            player.gather_rate * item_count as f64,
                            player.usage_rate * avg_pref * item_count as f64,
                            player.gather_rate * item_count as f64
                                - player.usage_rate * avg_pref * item_count as f64,
                        ));
                        ui.end_row();
                    });

                ui.separator();

                // --- Financial State ---
                ui.heading("Financial State");
                egui::Grid::new("player_financials")
                    .num_columns(2)
                    .striped(true)
                    .show(ui, |ui| {
                        ui.label("Balance");
                        ui.strong(format!("${:.2}", player.balance));
                        ui.end_row();

                        ui.label("Total Traded");
                        ui.label(format!("${:.2}", player.total_traded));
                        ui.end_row();

                        ui.label("Total Trades");
                        ui.label(format!("{}", player.total_trades));
                        ui.end_row();

                        ui.label("Credit Score");
                        let credit_color = if player.credit_score >= 600 {
                            egui::Color32::from_rgb(46, 213, 115)
                        } else if player.credit_score >= 400 {
                            egui::Color32::from_rgb(255, 230, 109)
                        } else {
                            egui::Color32::from_rgb(255, 107, 107)
                        };
                        ui.colored_label(credit_color, format!("{}", player.credit_score));
                        ui.end_row();

                        ui.label("Online");
                        if player.online {
                            ui.colored_label(egui::Color32::from_rgb(46, 213, 115), "Yes");
                        } else {
                            ui.colored_label(egui::Color32::GRAY, "No");
                        }
                        ui.end_row();
                    });

                // Show active loan if any
                if let Some(loan) = sim.loans.iter().find(|l| {
                    l.player_index == player_idx && l.status == LoanStatus::Active
                }) {
                    ui.separator();
                    ui.heading("Active Loan");
                    egui::Grid::new("player_loan")
                        .num_columns(2)
                        .striped(true)
                        .show(ui, |ui| {
                            ui.label("Principal");
                            ui.label(format!("${:.2}", loan.principal));
                            ui.end_row();

                            ui.label("Current Balance");
                            ui.strong(format!("${:.2}", loan.current_balance));
                            ui.end_row();

                            ui.label("Interest Rate");
                            ui.label(format!("{:.2}%", loan.interest_rate * 100.0));
                            ui.end_row();

                            ui.label("Due Tick");
                            ui.label(format!("{}", loan.due_tick));
                            ui.end_row();

                            let overdue = loan.is_overdue(sim.current_tick);
                            ui.label("Overdue");
                            if overdue {
                                ui.colored_label(egui::Color32::from_rgb(255, 107, 107), "YES");
                            } else {
                                ui.label("No");
                            }
                            ui.end_row();
                        });
                }

                ui.separator();

                // --- Per-Item Decision Table ---
                ui.horizontal(|ui| {
                    ui.heading("Per-Item Perception");
                    help(ui, "Shows how this player perceives each item. Base Val is their innate belief, Eff. Val accounts for inventory saturation. The Would column shows what they'd decide right now.");
                });
                ui.add_space(4.0);

                egui::Grid::new("player_items")
                    .num_columns(8)
                    .striped(true)
                    .min_col_width(40.0)
                    .show(ui, |ui| {
                        ui.strong("Item");
                        ui.strong("Pref").on_hover_text("Random weight 0-100%. Each tick, items are considered in preference order and skipped with probability (1 - pref).");
                        ui.strong("Base Val").on_hover_text("Innate perceived value. Randomly sampled at creation from a normal distribution around the item's base price (stddev = 15%).");
                        ui.strong("Eff. Val").on_hover_text("Effective perceived value after inventory saturation.\nFormula: base / (1 + qty * saturation_rate)\nDrops as inventory grows, making sells more likely.");
                        ui.strong("Buy@").on_hover_text("Current market buy price = base_price * (1 + BPD). What the player pays to buy.");
                        ui.strong("Sell@").on_hover_text("Current market sell price = base_price * (1 - SPD). What the player receives when selling.");
                        ui.strong("Inv").on_hover_text("How many of this item the player currently holds. Must have >0 to sell.");
                        ui.strong("Would...").on_hover_text("What this player would decide right now.\nBUY: buy_price < effective * (1 - buy_threshold) and can afford\nSELL: sell_price > effective * (1 + sell_threshold) and has inventory\nExploiters follow trends instead.");
                        ui.end_row();

                        for (i, item) in sim.engine.items.iter().enumerate() {
                            let preference =
                                player.preferences.get(&i).copied().unwrap_or(0.5);
                            let base_perceived =
                                player.perceived_values.get(&i).copied().unwrap_or(item.price);
                            let inventory =
                                player.inventory.get(&i).copied().unwrap_or(0);
                            let effective =
                                player.effective_perceived(i, base_perceived);
                            let buy_price = item.buy_price();
                            let sell_price = item.sell_price();

                            let decision = if player.archetype == Archetype::Exploiter {
                                match item.trend.direction {
                                    PriceTrendDirection::Up => {
                                        if player.balance > buy_price {
                                            "BUY (trend)"
                                        } else {
                                            "- (broke)"
                                        }
                                    }
                                    PriceTrendDirection::Down | PriceTrendDirection::Stable => {
                                        if inventory > 0 {
                                            "SELL (dump)"
                                        } else {
                                            "- (no inv)"
                                        }
                                    }
                                }
                            } else {
                                let buy_target = effective * (1.0 - player.buy_threshold);
                                let sell_target = effective * (1.0 + player.sell_threshold);
                                if buy_price < buy_target && player.balance > buy_price {
                                    "BUY"
                                } else if sell_price > sell_target && inventory > 0 {
                                    "SELL"
                                } else if buy_price >= buy_target && sell_price <= sell_target {
                                    "- (spread)"
                                } else if player.balance <= buy_price && inventory == 0 {
                                    "- (nothing)"
                                } else if player.balance <= buy_price {
                                    "- (broke)"
                                } else {
                                    "- (no inv)"
                                }
                            };

                            ui.label(&item.name);

                            let pref_color = egui::Color32::from_rgb(
                                (255.0 * (1.0 - preference)) as u8,
                                (255.0 * preference) as u8,
                                100,
                            );
                            ui.colored_label(pref_color, format!("{:.0}%", preference * 100.0));

                            // Base perceived value
                            ui.label(format!("${:.1}", base_perceived));

                            // Effective perceived value (after saturation)
                            let saturation_pct = if base_perceived > 0.0 {
                                ((effective - base_perceived) / base_perceived) * 100.0
                            } else {
                                0.0
                            };
                            let eff_color = if saturation_pct < -20.0 {
                                egui::Color32::from_rgb(255, 107, 107)
                            } else if saturation_pct < -5.0 {
                                egui::Color32::from_rgb(255, 230, 109)
                            } else {
                                egui::Color32::LIGHT_GRAY
                            };
                            ui.colored_label(
                                eff_color,
                                format!("${:.1} ({:+.0}%)", effective, saturation_pct),
                            );

                            ui.label(format!("${:.1}", buy_price));
                            ui.label(format!("${:.1}", sell_price));

                            if inventory > 0 {
                                ui.label(format!("{inventory}"));
                            } else {
                                ui.colored_label(egui::Color32::DARK_GRAY, "0");
                            }

                            let decision_color = if decision.starts_with("BUY") {
                                egui::Color32::from_rgb(46, 213, 115)
                            } else if decision.starts_with("SELL") {
                                egui::Color32::from_rgb(255, 107, 107)
                            } else {
                                egui::Color32::GRAY
                            };
                            ui.colored_label(decision_color, decision);

                            ui.end_row();
                        }
                    });

                ui.separator();

                // --- Decision Logic Explanation ---
                ui.heading("Decision Logic");
                match player.archetype {
                    Archetype::Exploiter => {
                        ui.label("Trend-follower: Buys when trend is UP, dumps inventory on DOWN/STABLE.");
                        ui.label("Ignores perceived value entirely.");
                    }
                    Archetype::Farmer => {
                        ui.label("Gains 0-2 of every item per tick (resource gathering).");
                        ui.label(format!(
                            "Sells when sell_price > perceived * {:.0}%",
                            (1.0 + player.sell_threshold) * 100.0
                        ));
                        ui.label("Low sell threshold means sells easily.");
                    }
                    Archetype::Hoarder => {
                        ui.label(format!(
                            "Buys when buy_price < perceived * {:.0}%",
                            (1.0 - player.buy_threshold) * 100.0
                        ));
                        ui.label(format!(
                            "Sells when sell_price > perceived * {:.0}%",
                            (1.0 + player.sell_threshold) * 100.0
                        ));
                        ui.label("High sell threshold means rarely sells. Accumulates.");
                    }
                    Archetype::Trader => {
                        ui.label(format!(
                            "Buys when buy_price < perceived * {:.0}%",
                            (1.0 - player.buy_threshold) * 100.0
                        ));
                        ui.label(format!(
                            "Sells when sell_price > perceived * {:.0}%",
                            (1.0 + player.sell_threshold) * 100.0
                        ));
                        ui.label("Tight margins, high frequency. Profits from spread.");
                    }
                    Archetype::Casual => {
                        ui.label(format!(
                            "Buys when buy_price < perceived * {:.0}%",
                            (1.0 - player.buy_threshold) * 100.0
                        ));
                        ui.label(format!(
                            "Sells when sell_price > perceived * {:.0}%",
                            (1.0 + player.sell_threshold) * 100.0
                        ));
                        ui.label("Infrequent, balanced. Low online rate.");
                    }
                    Archetype::Newbie => {
                        ui.label("New player: buys basics even at slight premium.");
                        ui.label("Almost never sells (hoards early gains).");
                        ui.label(format!("Low credit score ({}).", player.credit_score));
                    }
                    Archetype::AFKFarmer => {
                        ui.label("Mostly offline — comes online briefly to dump inventory.");
                        ui.label(format!(
                            "Massive gather_rate ({:.1}), huge max_trade_amount ({})",
                            player.gather_rate, player.max_trade_amount
                        ));
                        ui.label("Sells at near-zero margin. High inventory saturation.");
                    }
                    Archetype::GuildBuyer => {
                        ui.label("Guild-backed buyer: maintains stock for members.");
                        ui.label(format!(
                            "Buy threshold={:.1}%, sell threshold={:.1}% (guild markup)",
                            player.buy_threshold * 100.0,
                            player.sell_threshold * 100.0
                        ));
                        ui.label("Buys heavily to keep guild inventory stocked.");
                    }
                    Archetype::MarketMaker => {
                        ui.label("Posts two-sided orders around fair value, earns from spread.");
                        ui.label(format!(
                            "Buy/sell threshold={:.1}% (MM spread = 2x this)",
                            player.sell_threshold * 100.0
                        ));
                        ui.label(format!(
                            "Inventory target={}, max={} per item",
                            player.mm_target_inventory, player.mm_max_inventory
                        ));
                        ui.label("Trades both directions, reduces systemic underselling.");
                    }
                    Archetype::InsiderTrader => {
                        ui.label("Mean-reversion: buys when price is below rolling average, sells when above.");
                        ui.label(format!(
                            "Threshold={:.0}% deviation, history window={} ticks",
                            player.buy_threshold * 100.0,
                            player.insider_history_window
                        ));
                        ui.label("Contrarian — fades momentum by buying dips and selling spikes.");
                    }
                    Archetype::GuildSeller => {
                        ui.label("Guild-backed seller: liquidates stock for members at fair prices.");
                        ui.label(format!(
                            "Price-spike threshold={:.1}%, sell threshold={:.1}%",
                            player.guild_sell_threshold * 100.0,
                            player.sell_threshold * 100.0
                        ));
                        ui.label("Sells proactively when price spikes above perceived (anti-bubble).");
                        ui.label("Liquidates excess inventory when > 2x guild target.");
                    }
                    Archetype::VolumeTrader => {
                        ui.label("Contrarian volume trader: buys when spreads widen AND prices drop.");
                        ui.label(format!(
                            "Spread threshold={:.0}% deviation, spread window={}, price window={}",
                            player.buy_threshold * 100.0,
                            player.volume_spread_window,
                            player.volume_price_window
                        ));
                        ui.label("Signal: wide spread + low price = buy (volume drought).");
                        ui.label("Signal: tight spread + high price = sell (volume surge).");
                        ui.label("5-tick cooldown between decisions per item.");
                    }
                }

                ui.add_space(4.0);
                ui.label(format!(
                    "Each tick: {:.0}% chance online, then {:.0}% chance active.",
                    player.online_probability * 100.0,
                    player.activity_rate * 100.0,
                ));
                ui.label(format!(
                    "Effective trade chance per tick: {:.1}%",
                    player.online_probability * player.activity_rate * 100.0,
                ));

                // --- Total Inventory Value ---
                ui.separator();
                ui.heading("Inventory Value");
                let mut total_inv_value = 0.0;
                let mut total_items = 0;
                for (i, item) in sim.engine.items.iter().enumerate() {
                    let qty = player.inventory.get(&i).copied().unwrap_or(0);
                    if qty > 0 {
                        total_inv_value += item.sell_price() * qty as f64;
                        total_items += qty;
                    }
                }
                ui.label(format!(
                    "{} items worth ${:.2} (at sell prices)",
                    total_items, total_inv_value
                ));
                ui.label(format!(
                    "Net worth: ${:.2}",
                    player.balance + total_inv_value
                ));
            });
        });
}
