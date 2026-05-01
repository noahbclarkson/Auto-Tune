-- Add plugin_version column to track which plugin version each server runs
ALTER TABLE servers ADD COLUMN plugin_version TEXT;

-- Index for version queries (admins checking outdated servers)
CREATE INDEX IF NOT EXISTS idx_servers_plugin_version ON servers(plugin_version);