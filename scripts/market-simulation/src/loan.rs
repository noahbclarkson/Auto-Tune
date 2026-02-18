use crate::config::SimConfig;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum LoanStatus {
    Active,
    Paid,
    Defaulted,
}

#[derive(Clone, Debug)]
pub struct Loan {
    pub player_index: usize,
    pub principal: f64,
    pub current_balance: f64,
    pub interest_rate: f64,
    pub due_tick: u64,
    pub last_interest_tick: u64,
    pub status: LoanStatus,
}

impl Loan {
    pub fn new(
        player_index: usize,
        principal: f64,
        interest_rate: f64,
        current_tick: u64,
        config: &SimConfig,
    ) -> Self {
        Self {
            player_index,
            principal,
            current_balance: principal,
            interest_rate,
            due_tick: current_tick + config.loan_duration_ticks(),
            last_interest_tick: current_tick,
            status: LoanStatus::Active,
        }
    }

    pub fn is_overdue(&self, current_tick: u64) -> bool {
        self.status == LoanStatus::Active && current_tick > self.due_tick
    }

    pub fn apply_interest(&mut self) {
        let interest = self.current_balance * self.interest_rate;
        self.current_balance += interest;
    }

    pub fn make_payment(&mut self, amount: f64) {
        self.current_balance -= amount;
        if self.current_balance <= 0.0 {
            self.current_balance = 0.0;
            self.status = LoanStatus::Paid;
        }
    }

    pub fn mark_defaulted(&mut self) {
        self.status = LoanStatus::Defaulted;
    }
}

pub fn calculate_interest_rate(credit_score: i32, config: &SimConfig) -> f64 {
    let base_rate = config.loans.base_interest_rate;
    if !config.loans.credit_score_modifier {
        return base_rate;
    }
    let modifier = 1.0 + (500.0 - credit_score as f64) / 1000.0;
    base_rate * modifier
}
