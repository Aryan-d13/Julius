-- Add execution context metadata column to tasks table
ALTER TABLE tasks ADD COLUMN metadata TEXT;
