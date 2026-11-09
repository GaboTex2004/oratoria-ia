CREATE TABLE media_deletion_jobs (
    id uuid PRIMARY KEY,
    storage_path varchar(500) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now()
);
