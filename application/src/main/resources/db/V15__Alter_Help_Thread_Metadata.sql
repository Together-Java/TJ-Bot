ALTER TABLE help_threads ADD ticket_status INTEGER DEFAULT 0;
ALTER TABLE help_threads ADD tags TEXT DEFAULT 'none';
ALTER TABLE help_threads ADD closed_at TIMESTAMP NULL;
ALTER TABLE help_threads ADD participants INTEGER DEFAULT 1;
ALTER TABLE help_threads ADD message_count INTEGER DEFAULT 0;