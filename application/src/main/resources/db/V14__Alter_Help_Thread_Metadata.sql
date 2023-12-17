ALTER TABLE help_threads ADD ticket_status INTEGER DEFAULT 0;
ALTER TABLE help_threads ADD tag TEXT DEFAULT 'none';
ALTER TABLE help_threads ADD closed_at TIMESTAMP NULL;