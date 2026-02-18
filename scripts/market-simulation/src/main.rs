mod config;
mod engine;
mod gui;
mod loan;
mod player;
mod recorder;
mod simulation;

use eframe::egui;

use crate::config::SimConfig;
use crate::gui::GuiState;
use crate::simulation::Simulation;

struct SimApp {
    sim: Simulation,
    gui: GuiState,
}

impl SimApp {
    fn new() -> Self {
        let config = SimConfig::default();
        let gui = GuiState::new(&config);
        let sim = Simulation::new(config);
        Self { sim, gui }
    }
}

impl eframe::App for SimApp {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        if !self.sim.paused {
            self.sim.tick_accumulator += self.sim.speed;
            while self.sim.tick_accumulator >= 1.0 {
                self.sim.tick();
                self.sim.tick_accumulator -= 1.0;
            }
            ctx.request_repaint();
        }

        gui::draw_gui(ctx, &mut self.sim, &mut self.gui);
    }
}

fn main() -> eframe::Result {
    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default()
            .with_inner_size([1400.0, 900.0])
            .with_title("Auto-Tune Market Simulation"),
        ..Default::default()
    };

    eframe::run_native(
        "Auto-Tune Market Simulation",
        options,
        Box::new(|_cc| Ok(Box::new(SimApp::new()))),
    )
}
