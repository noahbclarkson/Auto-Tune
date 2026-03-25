use std::collections::HashMap;
use std::fmt;
use std::path::PathBuf;

use rusqlite::{Connection, params};

use crate::engine::{ItemState, PriceTrendDirection};
use crate::player::{DecisionLog, PlayerAgent};
use crate::simulation::EconomySnapshot;

#[derive(Debug)]
pub enum RecorderError {
    Sqlite(rusqlite::Error),
    Io(std::io::Error),
    Json(serde_json::Error),
}

impl fmt::Display for RecorderError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Sqlite(e) => write!(f, "SQLite error: {e}"),
            Self::Io(e) => write!(f, "IO error: {e}"),
            Self::Json(e) => write!(f, "JSON error: {e}"),
        }
    }
}

impl From<rusqlite::Error> for RecorderError {
    fn from(e: rusqlite::Error) -> Self {
        Self::Sqlite(e)
    }
}

impl From<std::io::Error> for RecorderError {
    fn from(e: std::io::Error) -> Self {
        Self::Io(e)
    }
}

impl From<serde_json::Error> for RecorderError {
    fn from(e: serde_json::Error) -> Self {
        Self::Json(e)
    }
}

#[derive(Clone, Debug)]
pub struct LoanEventData {
    pub tick: u64,
    pub player_id: usize,
    pub event_type: &'static str,
    pub principal: f64,
    pub balance: f64,
    pub rate: f64,
    pub amount: f64,
}

pub struct TickSnapshot<'a> {
    pub tick: u64,
    pub online_count: i32,
    pub total_players: usize,
    pub global_volume_multiplier: f64,
    pub items: &'a [ItemState],
    pub players: &'a [PlayerAgent],
    pub decisions: &'a [DecisionLog],
}

struct TickRow {
    tick: u64,
    online_players: i32,
    total_players: usize,
    global_volume_multiplier: f64,
}

struct ItemStateRow {
    tick: u64,
    item_index: usize,
    item_name: String,
    base_price: f64,
    price: f64,
    buy_price: f64,
    sell_price: f64,
    bpd: f64,
    spd: f64,
    trend: String,
    trend_pct: f64,
    buy_volume: i32,
    sell_volume: i32,
}

struct PlayerStateRow {
    tick: u64,
    player_id: usize,
    name: String,
    archetype: String,
    balance: f64,
    online: bool,
    credit_score: i32,
    total_traded: f64,
    total_trades: u32,
    inventory_json: String,
}

struct DecisionRow {
    tick: u64,
    player_id: usize,
    item_index: usize,
    action: String,
    amount: i32,
    price_per_unit: f64,
    total_cost: f64,
    perceived_value: f64,
    effective_perceived: f64,
    buy_threshold: f64,
    sell_threshold: f64,
    balance_before: f64,
    inventory_before: i32,
    reasoning: String,
}

struct EconomySnapshotRow {
    tick: u64,
    gdp: f64,
    total_debt: f64,
    avg_price_change: f64,
    online_players: i32,
    total_players: usize,
}

struct LoanEventRow {
    tick: u64,
    player_id: usize,
    event_type: String,
    principal: f64,
    balance: f64,
    rate: f64,
    amount: f64,
}

struct ConfigChangeRow {
    tick: u64,
    config_json: String,
}

struct RecordBatch {
    ticks: Vec<TickRow>,
    item_states: Vec<ItemStateRow>,
    player_states: Vec<PlayerStateRow>,
    decisions: Vec<DecisionRow>,
    economy_snapshots: Vec<EconomySnapshotRow>,
    loan_events: Vec<LoanEventRow>,
    config_changes: Vec<ConfigChangeRow>,
}

impl RecordBatch {
    fn new() -> Self {
        Self {
            ticks: Vec::new(),
            item_states: Vec::new(),
            player_states: Vec::new(),
            decisions: Vec::new(),
            economy_snapshots: Vec::new(),
            loan_events: Vec::new(),
            config_changes: Vec::new(),
        }
    }

    fn clear(&mut self) {
        self.ticks.clear();
        self.item_states.clear();
        self.player_states.clear();
        self.decisions.clear();
        self.economy_snapshots.clear();
        self.loan_events.clear();
        self.config_changes.clear();
    }

    fn is_empty(&self) -> bool {
        self.ticks.is_empty()
    }
}

pub struct DataRecorder {
    conn: Connection,
    session_id: i64,
    batch: RecordBatch,
    flush_interval: u64,
    ticks_since_flush: u64,
    pub path: PathBuf,
}

impl DataRecorder {
    pub fn new(path: PathBuf, config_json: &str) -> Result<Self, RecorderError> {
        let conn = Connection::open(&path)?;

        conn.execute_batch(
            "PRAGMA journal_mode = WAL;
             PRAGMA synchronous = NORMAL;
             PRAGMA cache_size = -8000;
             PRAGMA temp_store = MEMORY;",
        )?;

        Self::create_schema(&conn)?;

        conn.execute(
            "INSERT INTO sessions (started_at, config_json) VALUES (datetime('now'), ?1)",
            params![config_json],
        )?;
        let session_id = conn.last_insert_rowid();

        Ok(Self {
            conn,
            session_id,
            batch: RecordBatch::new(),
            flush_interval: 100,
            ticks_since_flush: 0,
            path,
        })
    }

    fn create_schema(conn: &Connection) -> Result<(), RecorderError> {
        conn.execute_batch(
            "CREATE TABLE IF NOT EXISTS sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                started_at TEXT NOT NULL,
                config_json TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS ticks (
                session_id INTEGER NOT NULL,
                tick INTEGER NOT NULL,
                online_players INTEGER NOT NULL,
                total_players INTEGER NOT NULL,
                global_volume_multiplier REAL NOT NULL,
                PRIMARY KEY (session_id, tick),
                FOREIGN KEY (session_id) REFERENCES sessions(id)
            );

            CREATE TABLE IF NOT EXISTS item_states (
                session_id INTEGER NOT NULL,
                tick INTEGER NOT NULL,
                item_index INTEGER NOT NULL,
                item_name TEXT NOT NULL,
                base_price REAL NOT NULL,
                price REAL NOT NULL,
                buy_price REAL NOT NULL,
                sell_price REAL NOT NULL,
                bpd REAL NOT NULL,
                spd REAL NOT NULL,
                trend TEXT NOT NULL,
                trend_pct REAL NOT NULL,
                buy_volume INTEGER NOT NULL,
                sell_volume INTEGER NOT NULL,
                PRIMARY KEY (session_id, tick, item_index),
                FOREIGN KEY (session_id) REFERENCES sessions(id)
            );

            CREATE TABLE IF NOT EXISTS player_states (
                session_id INTEGER NOT NULL,
                tick INTEGER NOT NULL,
                player_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                archetype TEXT NOT NULL,
                balance REAL NOT NULL,
                online INTEGER NOT NULL,
                credit_score INTEGER NOT NULL,
                total_traded REAL NOT NULL,
                total_trades INTEGER NOT NULL,
                inventory_json TEXT NOT NULL,
                PRIMARY KEY (session_id, tick, player_id),
                FOREIGN KEY (session_id) REFERENCES sessions(id)
            );

            CREATE TABLE IF NOT EXISTS decisions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL,
                tick INTEGER NOT NULL,
                player_id INTEGER NOT NULL,
                item_index INTEGER NOT NULL,
                action TEXT NOT NULL,
                amount INTEGER NOT NULL,
                price_per_unit REAL NOT NULL,
                total_cost REAL NOT NULL,
                perceived_value REAL NOT NULL,
                effective_perceived REAL NOT NULL,
                buy_threshold REAL NOT NULL,
                sell_threshold REAL NOT NULL,
                balance_before REAL NOT NULL,
                inventory_before INTEGER NOT NULL,
                reasoning TEXT NOT NULL,
                FOREIGN KEY (session_id) REFERENCES sessions(id)
            );

            CREATE TABLE IF NOT EXISTS economy_snapshots (
                session_id INTEGER NOT NULL,
                tick INTEGER NOT NULL,
                gdp REAL NOT NULL,
                total_debt REAL NOT NULL,
                avg_price_change REAL NOT NULL,
                online_players INTEGER NOT NULL,
                total_players INTEGER NOT NULL,
                PRIMARY KEY (session_id, tick),
                FOREIGN KEY (session_id) REFERENCES sessions(id)
            );

            CREATE TABLE IF NOT EXISTS loan_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL,
                tick INTEGER NOT NULL,
                player_id INTEGER NOT NULL,
                event_type TEXT NOT NULL,
                principal REAL NOT NULL,
                balance REAL NOT NULL,
                rate REAL NOT NULL,
                amount REAL NOT NULL,
                FOREIGN KEY (session_id) REFERENCES sessions(id)
            );

            CREATE TABLE IF NOT EXISTS config_changes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL,
                tick INTEGER NOT NULL,
                config_json TEXT NOT NULL,
                FOREIGN KEY (session_id) REFERENCES sessions(id)
            );

            CREATE INDEX IF NOT EXISTS idx_decisions_player ON decisions(player_id);
            CREATE INDEX IF NOT EXISTS idx_decisions_item ON decisions(item_index);
            CREATE INDEX IF NOT EXISTS idx_loan_events_player ON loan_events(player_id);",
        )?;

        Ok(())
    }

    pub fn record_tick(&mut self, snapshot: TickSnapshot<'_>) -> Result<(), RecorderError> {
        self.batch.ticks.push(TickRow {
            tick: snapshot.tick,
            online_players: snapshot.online_count,
            total_players: snapshot.total_players,
            global_volume_multiplier: snapshot.global_volume_multiplier,
        });

        for (i, item) in snapshot.items.iter().enumerate() {
            let trend_str = match item.trend.direction {
                PriceTrendDirection::Up => "UP",
                PriceTrendDirection::Down => "DOWN",
                PriceTrendDirection::Stable => "STABLE",
            };
            self.batch.item_states.push(ItemStateRow {
                tick: snapshot.tick,
                item_index: i,
                item_name: item.name.clone(),
                base_price: item.base_price,
                price: item.price,
                buy_price: item.buy_price(),
                sell_price: item.sell_price(),
                bpd: item.spread.bpd,
                spd: item.spread.spd,
                trend: trend_str.to_string(),
                trend_pct: item.trend.percent_change,
                buy_volume: item.tick_buy_volume,
                sell_volume: item.tick_sell_volume,
            });
        }

        for player in snapshot.players {
            let inventory_map: HashMap<String, i32> = player
                .inventory
                .iter()
                .map(|(&k, &v)| (k.to_string(), v))
                .collect();
            let inventory_json = serde_json::to_string(&inventory_map)?;

            self.batch.player_states.push(PlayerStateRow {
                tick: snapshot.tick,
                player_id: player.id,
                name: player.name.clone(),
                archetype: player.archetype.label().to_string(),
                balance: player.balance,
                online: player.online,
                credit_score: player.credit_score,
                total_traded: player.total_traded,
                total_trades: player.total_trades,
                inventory_json,
            });
        }

        for log in snapshot.decisions {
            self.batch.decisions.push(DecisionRow {
                tick: snapshot.tick,
                player_id: log.player_id,
                item_index: log.item_index,
                action: if log.is_buy {
                    "Buy".to_string()
                } else {
                    "Sell".to_string()
                },
                amount: log.amount,
                price_per_unit: log.price_per_unit,
                total_cost: log.total_cost,
                perceived_value: log.perceived_value,
                effective_perceived: log.effective_perceived,
                buy_threshold: log.buy_threshold,
                sell_threshold: log.sell_threshold,
                balance_before: log.balance_before,
                inventory_before: log.inventory_before,
                reasoning: log.reasoning.clone(),
            });
        }

        self.ticks_since_flush += 1;
        if self.ticks_since_flush >= self.flush_interval {
            self.flush()?;
        }

        Ok(())
    }

    pub fn record_economy_snapshot(&mut self, snapshot: &EconomySnapshot) {
        self.batch.economy_snapshots.push(EconomySnapshotRow {
            tick: snapshot.tick,
            gdp: snapshot.gdp,
            total_debt: snapshot.total_debt,
            avg_price_change: snapshot.avg_price_change,
            online_players: snapshot.online_players,
            total_players: snapshot.total_players,
        });
    }

    pub fn record_loan_events(&mut self, events: &[LoanEventData]) {
        for event in events {
            self.batch.loan_events.push(LoanEventRow {
                tick: event.tick,
                player_id: event.player_id,
                event_type: event.event_type.to_string(),
                principal: event.principal,
                balance: event.balance,
                rate: event.rate,
                amount: event.amount,
            });
        }
    }

    pub fn record_config_change(&mut self, tick: u64, config_json: &str) {
        self.batch.config_changes.push(ConfigChangeRow {
            tick,
            config_json: config_json.to_string(),
        });
    }

    fn flush(&mut self) -> Result<(), RecorderError> {
        if self.batch.is_empty()
            && self.batch.item_states.is_empty()
            && self.batch.player_states.is_empty()
            && self.batch.decisions.is_empty()
            && self.batch.economy_snapshots.is_empty()
            && self.batch.loan_events.is_empty()
            && self.batch.config_changes.is_empty()
        {
            self.ticks_since_flush = 0;
            return Ok(());
        }

        let tx = self.conn.unchecked_transaction()?;

        {
            let mut stmt = tx.prepare_cached(
                "INSERT INTO ticks (session_id, tick, online_players, total_players, global_volume_multiplier)
                 VALUES (?1, ?2, ?3, ?4, ?5)",
            )?;
            for row in &self.batch.ticks {
                stmt.execute(params![
                    self.session_id,
                    row.tick as i64,
                    row.online_players,
                    row.total_players as i64,
                    row.global_volume_multiplier,
                ])?;
            }
        }

        {
            let mut stmt = tx.prepare_cached(
                "INSERT INTO item_states (session_id, tick, item_index, item_name, base_price, price, buy_price, sell_price, bpd, spd, trend, trend_pct, buy_volume, sell_volume)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)",
            )?;
            for row in &self.batch.item_states {
                stmt.execute(params![
                    self.session_id,
                    row.tick as i64,
                    row.item_index as i64,
                    row.item_name,
                    row.base_price,
                    row.price,
                    row.buy_price,
                    row.sell_price,
                    row.bpd,
                    row.spd,
                    row.trend,
                    row.trend_pct,
                    row.buy_volume,
                    row.sell_volume,
                ])?;
            }
        }

        {
            let mut stmt = tx.prepare_cached(
                "INSERT INTO player_states (session_id, tick, player_id, name, archetype, balance, online, credit_score, total_traded, total_trades, inventory_json)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)",
            )?;
            for row in &self.batch.player_states {
                stmt.execute(params![
                    self.session_id,
                    row.tick as i64,
                    row.player_id as i64,
                    row.name,
                    row.archetype,
                    row.balance,
                    row.online as i32,
                    row.credit_score,
                    row.total_traded,
                    row.total_trades,
                    row.inventory_json,
                ])?;
            }
        }

        {
            let mut stmt = tx.prepare_cached(
                "INSERT INTO decisions (session_id, tick, player_id, item_index, action, amount, price_per_unit, total_cost, perceived_value, effective_perceived, buy_threshold, sell_threshold, balance_before, inventory_before, reasoning)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15)",
            )?;
            for row in &self.batch.decisions {
                stmt.execute(params![
                    self.session_id,
                    row.tick as i64,
                    row.player_id as i64,
                    row.item_index as i64,
                    row.action,
                    row.amount,
                    row.price_per_unit,
                    row.total_cost,
                    row.perceived_value,
                    row.effective_perceived,
                    row.buy_threshold,
                    row.sell_threshold,
                    row.balance_before,
                    row.inventory_before,
                    row.reasoning,
                ])?;
            }
        }

        {
            let mut stmt = tx.prepare_cached(
                "INSERT INTO economy_snapshots (session_id, tick, gdp, total_debt, avg_price_change, online_players, total_players)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
            )?;
            for row in &self.batch.economy_snapshots {
                stmt.execute(params![
                    self.session_id,
                    row.tick as i64,
                    row.gdp,
                    row.total_debt,
                    row.avg_price_change,
                    row.online_players,
                    row.total_players as i64,
                ])?;
            }
        }

        {
            let mut stmt = tx.prepare_cached(
                "INSERT INTO loan_events (session_id, tick, player_id, event_type, principal, balance, rate, amount)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)",
            )?;
            for row in &self.batch.loan_events {
                stmt.execute(params![
                    self.session_id,
                    row.tick as i64,
                    row.player_id as i64,
                    row.event_type,
                    row.principal,
                    row.balance,
                    row.rate,
                    row.amount,
                ])?;
            }
        }

        {
            let mut stmt = tx.prepare_cached(
                "INSERT INTO config_changes (session_id, tick, config_json)
                 VALUES (?1, ?2, ?3)",
            )?;
            for row in &self.batch.config_changes {
                stmt.execute(params![self.session_id, row.tick as i64, row.config_json,])?;
            }
        }

        tx.commit()?;

        self.batch.clear();
        self.ticks_since_flush = 0;

        Ok(())
    }

    pub fn finalize(&mut self) -> Result<(), RecorderError> {
        self.flush()?;
        Ok(())
    }
}
